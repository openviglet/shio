/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH435 — **a command's flags must not cost a source read.**
 *
 * `shio clone --help` printed the same forty-line global help as `shio --help`, and so did every
 * other command: `main` answered `flags.help` before `loader()` ran, so no command was ever consulted
 * about its own flags. The cost landed on the agent, which is what makes this a P3 item rather than a
 * polish one — learning that `clone` takes `--assets`, `--markup`, `--depth`, `--max-pages`,
 * `--render`, `--ignore-robots`, `--max-assets` and `--max-asset-bytes` meant grepping
 * `cli/src/commands/clone.mjs` for `flags.`, reading an implementation to discover an interface,
 * having already paid for a help text that answered something else. Repeat it per command and a
 * six-command pipeline costs six greps plus six wasted help outputs.
 *
 * <h2>The flag list is derived, never listed here</h2>
 *
 * The interesting test is not "does every command have a help" — it is **"does that help name every
 * flag the command reads"**, and the only honest source for the second is the module itself. So each
 * `flags.<name>` in a command's source is kebab-cased and looked for in its `HELP`. A new flag with no
 * documentation fails the build the moment it is read, which is the property a hand-kept list cannot
 * have (SH260's rule for helpers, SH300's for DOM attributes, applied to an interface).
 *
 * Two limits, stated rather than papered over:
 *
 * - **Only the command's own module is scanned.** `pull --content` and `push --content` delegate to
 *   `content-pull.mjs`/`content-push.mjs`, whose flags are documented in the command's help but not
 *   derived from it. The lint under-reports there; it never over-reports.
 * - **`help` is exempt**, because `index.mjs` answers it before the command runs.
 */
import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";
import { test } from "node:test";

const run = promisify(execFile);
const ENTRY = fileURLToPath(new URL("../src/index.mjs", import.meta.url));

/** Answered by `index.mjs` before any command loads, so no command documents it. */
const NOT_A_COMMAND_FLAG = new Set(["help"]);

/** `maxPages` → `max-pages`, which is what a caller types and what the help has to say. */
const kebab = (name) => name.replace(/[A-Z]/g, (upper) => `-${upper.toLowerCase()}`);

async function registry() {
  const source = await readFile(ENTRY, "utf8");
  const block = source.slice(source.indexOf("const COMMANDS = {"), source.indexOf("const HELP ="));
  const names = [...block.matchAll(/^\s{2}(\w+):/gm)].map(([, name]) => name);
  assert.ok(names.length > 5, `expected a command registry, found ${names.length} entries`);
  return names;
}

const commands = await registry();

test("SH435: every registered command prints its own help, not the global one", async () => {
  const missing = [];
  for (const name of commands) {
    const mod = await import(`../src/commands/${name}.mjs`);
    if (typeof mod.HELP !== "string" || !mod.HELP.trim()) {
      missing.push(name);
      continue;
    }
    const first = mod.HELP.trim().split("\n")[0];
    assert.ok(
      first.startsWith(`shio ${name}`),
      `\`${name}\`'s help opens with "${first}" — it has to name the command it is about, since it `
        + "is printed on its own",
    );
  }

  assert.deepEqual(
    missing,
    [],
    "a command with no HELP would fall back to nothing — index.mjs prints `mod.HELP` and does not "
      + "reach for the global text, deliberately: the global text is the wrong answer to a question "
      + "about one command, and a silent fallback would hide exactly this",
  );
});

test("SH435: every flag a command reads is named in that command's help", async () => {
  const undocumented = [];
  let flags = 0;

  for (const name of commands) {
    const [mod, source] = await Promise.all([
      import(`../src/commands/${name}.mjs`),
      readFile(new URL(`../src/commands/${name}.mjs`, import.meta.url), "utf8"),
    ]);
    const read = [...new Set([...source.matchAll(/flags\.([A-Za-z][A-Za-z0-9]*)/g)]
      .map(([, flag]) => flag))]
      .filter((flag) => !NOT_A_COMMAND_FLAG.has(flag))
      .sort();
    flags += read.length;
    for (const flag of read) {
      if (!mod.HELP.includes(`--${kebab(flag)}`)) {
        undocumented.push(`${name}: --${kebab(flag)}`);
      }
    }
  }

  console.log(`\n[SH435] ${flags} flags read across ${commands.length} commands, all documented\n`);

  assert.ok(flags > 80, `derived from the sources — ${flags} is too few to be the real set`);
  assert.deepEqual(
    undocumented,
    [],
    "a flag the command reads and its help does not mention is a flag a caller can only find by "
      + "reading the source, which is the whole defect. `clone --url` is the case worth naming: it "
      + "is the one command where --url means the target rather than the instance, and that was "
      + "declared in a test rather than anywhere a caller looks",
  );
});

/**
 * SH208 retired the backtick half of this, and the reason is the point.
 *
 * <p>It used to assert that no command's help contained a raw backtick, because a backtick ends a
 * template literal: a lone one broke the parse and a *pair* silently truncated the text. Both
 * failures were properties of the help being **JavaScript**. It is a `.txt` now, where a backtick
 * is a character — so the assertion had stopped protecting anything and started costing something,
 * which is the state a guard is hardest to notice from the inside. It surfaced the ordinary way: a
 * new command's help quoted a command name, the way help about a CLI wants to, and the build went
 * red for prose that could not hurt anyone.</p>
 *
 * <p>What remains is real and is asserted here rather than deleted with it. A help that
 * interpolates cannot live in a flat file at all, so `${` in a `.txt` is printed verbatim to the
 * caller — a `${flags.site}` that renders as itself is worse than one that fails, because it looks
 * like the command is telling you something. `help-source.test.mjs` owns the other half now: it
 * refuses the template literal itself, which is the mechanism rather than one of its symptoms.</p>
 */
test("SH208: no help text interpolates, because a flat file prints ${…} verbatim", async () => {
  for (const name of commands) {
    const { HELP } = await import(`../src/commands/${name}.mjs`);
    assert.ok(
      !HELP.includes("${"),
      `\`${name}\`'s help contains \${…}, which a .txt prints literally — a caller would read it `
        + "as output rather than as a placeholder nobody filled in",
    );
  }
});

test("SH435: the binary answers --help per command, and the global list stopped describing flags",
  async () => {
    const printed = await Promise.all(commands.map(async (name) => {
      const { stdout } = await run(process.execPath, [ENTRY, name, "--help"]);
      return [name, stdout];
    }));

    for (const [name, stdout] of printed) {
      assert.match(stdout, new RegExp(`^shio ${name}\\b`), `\`shio ${name} --help\` printed something else`);
      assert.ok(
        !stdout.includes("\nCommands:\n"),
        `\`shio ${name} --help\` still printed the global command list`,
      );
    }

    const { stdout: global } = await run(process.execPath, [ENTRY, "--help"]);
    const lines = global.split("\n").length;
    console.log(`[SH435] the global help is ${lines} lines (it was 357)\n`);
    assert.ok(
      lines < 120,
      `the global help is ${lines} lines. It names the commands and points at their own help; `
        + "describing their flags here too would be two copies of one interface, which is the "
        + "shape that drifts",
    );
    assert.match(global, /shio <command> --help/, "and it has to say where a flag list lives");
  });

test("SH435: `shio help <command>` is the same answer as `shio <command> --help`", async () => {
  const [{ stdout: viaHelp }, { stdout: viaFlag }] = await Promise.all([
    run(process.execPath, [ENTRY, "help", "clone"]),
    run(process.execPath, [ENTRY, "clone", "--help"]),
  ]);

  assert.equal(viaHelp, viaFlag, "two spellings of one question must not have two answers");
});
