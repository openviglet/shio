/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * Vendor the roadkeep engine into `.roadkeep/`, the way `npm install` fills `node_modules`.
 *
 * ## Why the engine lives in the repository
 *
 * roadkeep owns `docs/ROADMAP.md`, `docs/CHANGELOG.md` and `docs/IMPROVEMENTS.md`, so a session
 * runs it constantly. Running it from wherever it happened to be found has two costs a project
 * feels every day:
 *
 * - **Every distinct command path is a new authorization prompt.** A copy under the user cache or
 *   a scratch directory changes path per version and per machine, so the allowance never sticks.
 *   `.roadkeep/scripts/roadkeep.py` is one stable path, allow-listed once in
 *   `.claude/settings.json`.
 * - **Which copy answers is not decidable by looking.** This machine has two plugin caches with
 *   different versions and a marketplace clone beside them, and the live checkout is sometimes
 *   mid-edit — a session lost an hour to `ImportError: cannot import name '_print_claim'` from a
 *   half-refactored working tree. A vendored copy is a decision recorded on disk instead.
 *
 * `ROADKEEP_HOME` points at it (`.claude/settings.json`), which is the launcher's own first
 * resolution step — so the guard hook, the MCP server and a hand-run command all reach the same
 * engine. `.roadkeep/` is git-ignored, like `node_modules`: reproduced by this script, never
 * committed.
 *
 * ## How the source is chosen
 *
 * By **version**, never by position. Candidates are asked what they are (`--version`) and the
 * highest wins, because "the plugin's copy" is ambiguous here — two caches, a marketplace clone,
 * and a working checkout that may be ahead of all of them.
 *
 * Usage: `node scripts/install-roadkeep.mjs [--check]`
 */
import { execFileSync } from "node:child_process";
import { cpSync, existsSync, mkdirSync, readdirSync, rmSync, statSync } from "node:fs";
import { homedir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const TARGET = join(ROOT, ".roadkeep");
const ENGINE = join("scripts", "roadkeep.py");

/** Every plugin/marketplace tree that could hold an engine, both config dirs included. */
function pluginRoots() {
  const configs = new Set(
    [process.env.CLAUDE_CONFIG_DIR, join(homedir(), ".claude"), join(homedir(), ".claude-pessoal")]
      .filter(Boolean),
  );
  const found = [];
  for (const config of configs) {
    for (const sub of ["plugins/cache/alegauss/roadkeep", "plugins/marketplaces/alegauss"]) {
      const base = join(config, ...sub.split("/"));
      if (!existsSync(base)) continue;
      if (existsSync(join(base, ENGINE))) found.push(base);
      // The cache nests one directory per installed version.
      for (const entry of safeList(base)) {
        const nested = join(base, entry);
        if (existsSync(join(nested, ENGINE))) found.push(nested);
      }
    }
  }
  return found;
}

function safeList(dir) {
  try {
    return readdirSync(dir).filter((e) => statSync(join(dir, e)).isDirectory());
  } catch {
    return [];
  }
}

/** What a candidate says it is, or null when it cannot answer — a broken tree is not a source. */
function versionOf(root) {
  try {
    const out = execFileSync("python", [join(root, ENGINE), "--version"], {
      encoding: "utf8", stdio: ["ignore", "pipe", "ignore"], timeout: 20000,
    });
    const match = /roadkeep\s+(\d+(?:\.\d+)*)/.exec(out);
    return match ? match[1] : null;
  } catch {
    return null;
  }
}

const compare = (a, b) => {
  const left = a.split(".").map(Number);
  const right = b.split(".").map(Number);
  for (let at = 0; at < Math.max(left.length, right.length); at++) {
    const diff = (left[at] ?? 0) - (right[at] ?? 0);
    if (diff) return diff;
  }
  return 0;
};

// ROADKEEP_SRC first, then the launcher's own sibling rule, then the plugin caches. A working
// CHECKOUT is deliberately not searched for beyond that sibling: it is the one source that can be
// mid-refactor, and a session already lost time to an `ImportError` from a half-edited tree. Point
// ROADKEEP_SRC at a checkout when that is what you want, so it is a decision rather than a default.
const candidates = [
  process.env.ROADKEEP_SRC,
  join(ROOT, "..", "roadkeep"),
  ...pluginRoots(),
].filter((root) => root && existsSync(join(root, ENGINE)));

const rated = candidates
  .map((root) => ({ root, version: versionOf(root) }))
  .filter((row) => row.version)
  .sort((a, b) => compare(b.version, a.version));

if (!rated.length) {
  console.error(
    "install-roadkeep: no roadkeep engine found.\n"
      + "  Looked for scripts/roadkeep.py under $ROADKEEP_SRC, a sibling checkout, and the\n"
      + "  alegauss plugin caches. Install the plugin (update-roadkeep.cmd) or set ROADKEEP_SRC.",
  );
  process.exit(1);
}

const installed = existsSync(join(TARGET, ENGINE)) ? versionOf(TARGET) : null;
const best = rated[0];

console.log(`install-roadkeep: ${rated.length} candidate(s); newest is ${best.version}`);
for (const row of rated) console.log(`  ${row.version.padEnd(10)} ${row.root}`);
console.log(`  vendored   ${installed ?? "(none)"} -> ${TARGET}`);

if (process.argv.includes("--check")) {
  process.exit(installed === best.version ? 0 : 1);
}

if (installed === best.version) {
  console.log(`install-roadkeep: .roadkeep is already ${installed}, nothing to do.`);
  process.exit(0);
}

rmSync(TARGET, { recursive: true, force: true });
mkdirSync(TARGET, { recursive: true });
// `.git` is excluded deliberately: this is a vendored artefact, not a second checkout, and a
// nested repository is what makes `git status` in the parent confusing.
cpSync(best.root, TARGET, {
  recursive: true,
  filter: (src) => !src.split(/[\\/]/).includes(".git"),
});

const now = versionOf(TARGET);
if (now !== best.version) {
  console.error(`install-roadkeep: copied ${best.version} but .roadkeep answers ${now}`);
  process.exit(1);
}
console.log(`install-roadkeep: .roadkeep is now ${now} (from ${best.root})`);
