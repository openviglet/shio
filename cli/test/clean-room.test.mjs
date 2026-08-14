import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { readFileSync, readdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join } from "node:path";
import { test } from "node:test";

// @scans prose reason: it asks what a published package CONTAINS, so a file's presence and its
// text both count and neither half can be stripped away (SH740).

/**
 * SH605 — what is actually in the box.
 *
 * Every measurement this project has was taken with the source tree on disk. The reader had
 * `agents.md`, `docs/agents/**`, `docs/specs/**` and a repo-local skill, and used them without
 * noticing — **that is what using them looks like**. A stranger who runs `npm i -g @viglet/shio` and
 * adds the plugin has none of it, so "is Shio discoverable" has never actually been asked. What was
 * measured is "is Shio discoverable to a reader holding its repository".
 *
 * This is the enforcement half of SH603. SH603 says *follow only what the instance and the plugin
 * say*; a reader with the repo mounted cannot honour that and cannot tell that it did not, because
 * the cheapest read is the one you do not remember making.
 *
 * <h2>The box, taken from the packer rather than from a list</h2>
 *
 * `npm pack --dry-run --json` is what `npm publish` would upload, so this cannot drift from the
 * `files` field, from `.npmignore`, or from a directory somebody added. The plugin ships as a
 * directory rather than a tarball, so its box is the directory minus its own tests.
 */

const ROOT = fileURLToPath(new URL("../../", import.meta.url));

/** Paths that exist only in this repository — a published file naming one sends a stranger away. */
const REPO_ONLY = [
  /\bagents\.md\b/,
  /\bdocs\/agents\b/,
  /\bdocs\/specs\b/,
  /\bdocs\/(ROADMAP|CHANGELOG|IMPROVEMENTS)\.md\b/,
  /\.claude\/skills\b/,
  /\bshio-app\/src\b/,
  /\bcli\/conformance\b/,
];

/** What `npm publish` would put in the tarball. */
function packed(pkg) {
  const out = execFileSync("npm", ["pack", "--dry-run", "--json"], {
    cwd: join(ROOT, pkg),
    encoding: "utf8",
    stdio: ["ignore", "pipe", "ignore"],
    shell: process.platform === "win32",
  });
  return JSON.parse(out)[0].files.map((file) => file.path);
}

function walk(dir, base = dir) {
  const out = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === "node_modules" || entry.name === "test") continue;
    const path = join(dir, entry.name);
    if (entry.isDirectory()) out.push(...walk(path, base));
    else out.push(path.slice(base.length + 1).split("\\").join("/"));
  }
  return out;
}

const boxes = () => [
  { name: "@viglet/shio", root: "cli", files: packed("cli") },
  { name: "claude-plugin", root: "claude-plugin", files: walk(join(ROOT, "claude-plugin")) },
];

const read = (root, rel) => readFileSync(join(ROOT, root, rel), "utf8");
const lines = (root, rel) => read(root, rel).split(/\r?\n/u);

test("SH605: nothing a reader reads points at a file that stays in the repository", () => {
  // Scoped to what a READER opens — markdown and the help texts — rather than to every source
  // file. A javadoc naming the test that guards a rule is a note to whoever maintains the code,
  // and it ships because the code ships; sending a stranger to `docs/agents/renderer.md` is a
  // different act, and it is the one this refuses.
  const stranded = [];
  for (const box of boxes()) {
    for (const rel of box.files.filter((f) => /\.(md|txt)$/.test(f))) {
      for (const [index, line] of read(box.root, rel).split("\n").entries()) {
        for (const pattern of REPO_ONLY) {
          const hit = pattern.exec(line);
          if (hit) stranded.push(`${box.name}/${rel}:${index + 1} → ${hit[0]}`);
        }
      }
    }
  }

  assert.deepEqual(stranded, [],
    "A published file naming a repo-only path sends a stranger to a file they do not have. "
      + "Twelve rounds of measurement never noticed because the repo was always mounted — which "
      + "is what using it looks like (SH605).");
});

test("SH605: every link a published document makes resolves inside the box", () => {
  // The interesting half, and the reason this is a walk rather than a grep: what leaves the box is
  // a decision nobody has made deliberately. A relative link is a promise that the thing is there.
  const dangling = [];
  for (const box of boxes()) {
    const present = new Set(box.files);
    for (const rel of box.files.filter((f) => f.endsWith(".md"))) {
      for (const match of read(box.root, rel).matchAll(/\]\(([^)#\s]+)(?:#[^)]*)?\)/g)) {
        const target = match[1].trim();
        if (/^(https?:|mailto:|#)/.test(target)) continue;
        if (!present.has(target.replace(/^\.\//, ""))) {
          dangling.push(`${box.name}/${rel} → ${target}`);
        }
      }
    }
  }

  assert.deepEqual(dangling, [],
    "A relative link in a published document is a promise the file is in the box. One that is "
      + "not is a reader following a path out of the product and into a repository they were "
      + "never given (SH605).");
});

test("SH605: the box names a first move, so a stranger has somewhere to start", () => {
  // The clean room is not only about what is missing. A box that stranded nobody and taught
  // nothing would pass both assertions above and still leave a reader with no way in.
  //
  // Three entry points, one per surface, because a stranger arrives through exactly one of them:
  // the CLI, the plugin's MCP server, or the instance's own manifest.
  const text = boxes()
    .flatMap((box) => box.files.filter((f) => /\.(md|txt)$/.test(f))
      .map((rel) => read(box.root, rel)))
    .join("\n");

  for (const entry of ["shio context", "shio_context", "shio://manifest"]) {
    assert.ok(text.includes(entry),
      `the published artefacts never mention \`${entry}\`, so a reader holding only them has no `
        + "first call to make — which is the whole of what SH603 asks a stranger to do (SH605)");
  }
});

test("SH605: the box is not empty, so the assertions above are about something", () => {
  // Every source lint in this repository is written against this failure: a walk that found
  // nothing passes perfectly and reports a guarantee nobody has. `npm pack` answering with an
  // empty file list would do exactly that here.
  for (const box of boxes()) {
    assert.ok(box.files.length > 5, `${box.name} packed ${box.files.length} file(s)`);
    assert.ok(box.files.some((f) => f.endsWith(".md")),
      `${box.name} ships no prose at all, so the reader assertions above are vacuous`);
  }
});

test("SH605: nothing in the box tells a reader to run a command only a checkout has", () => {
  // The other way the repo leaks, and the one a path check cannot see: a build command reads as
  // ordinary advice. `./mvnw`, a workspace filter, the commit script, roadkeep — every one of them
  // is a sentence that makes sense to whoever wrote it and is unrunnable for the reader it reaches.
  const REPO_COMMANDS = [/\.\/mvnw/, /pnpm --filter/, /run-commit/, /python -m roadkeep/];
  const leaked = [];
  for (const box of boxes()) {
    for (const rel of box.files.filter((f) => /\.(md|txt)$/.test(f))) {
      for (const [index, line] of lines(box.root, rel).entries()) {
        for (const pattern of REPO_COMMANDS) {
          const hit = pattern.exec(line);
          if (hit) leaked.push(`${box.name}/${rel}:${index + 1} → ${hit[0]}`);
        }
      }
    }
  }

  assert.deepEqual(leaked, [],
    "A published document naming a command that only works inside this checkout is advice the "
      + "reader cannot take, and it reads as advice rather than as a mistake (SH605).");
});
