/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH97 — the plugin's manifests are valid and its parts exist.
 *
 * Why a test for four JSON files and some Markdown. A plugin is loaded by *another* program
 * on somebody else's machine: a missing `description` on a skill means the skill never
 * triggers, a mistyped path in the marketplace means the install fails, and neither shows up
 * anywhere in this repository's own build. This is the cheapest possible substitute for
 * installing the plugin in CI, and it catches the whole class of failure that matters —
 * a manifest that refers to something that is not there.
 */
import assert from "node:assert/strict";
import { readFile, readdir, stat } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

const here = dirname(fileURLToPath(import.meta.url));
const plugin = join(here, "..");
const repo = join(plugin, "..");

const json = async (path) => JSON.parse(await readFile(path, "utf8"));

/** The `---` frontmatter block at the top of a skill or command file, as key → value. */
function frontmatter(text) {
  assert.match(text, /^---\n/, "the file must open with a frontmatter block");
  const end = text.indexOf("\n---", 4);
  assert.ok(end > 0, "the frontmatter block must be closed");
  const fields = {};
  for (const line of text.slice(4, end).split("\n")) {
    const cut = line.indexOf(":");
    if (cut > 0) fields[line.slice(0, cut).trim()] = line.slice(cut + 1).trim();
  }
  return fields;
}

test("the plugin manifest identifies the plugin", async () => {
  const manifest = await json(join(plugin, ".claude-plugin/plugin.json"));

  assert.equal(manifest.name, "shio");
  assert.match(manifest.version, /^\d+\.\d+\.\d+$/);
  // The description is what a user reads in `claude plugin list`. One clause is a name
  // repeated, not a description.
  assert.ok(manifest.description.length > 80, "the description must say what it gives you");
  assert.equal(manifest.license, "Apache-2.0");
});

test("the marketplace points at this plugin, by a path that exists", async () => {
  const marketplace = await json(join(repo, ".claude-plugin/marketplace.json"));

  assert.equal(marketplace.plugins.length, 1);
  const entry = marketplace.plugins[0];
  assert.equal(entry.name, "shio");
  const source = join(repo, entry.source);
  assert.ok((await stat(source)).isDirectory(), `${entry.source} must be a directory`);
  assert.ok(
    (await stat(join(source, ".claude-plugin/plugin.json"))).isFile(),
    "the source directory must hold a plugin manifest",
  );
});

test("the MCP server is the CLI's stdio bridge, with the environment it needs", async () => {
  const mcp = await json(join(plugin, ".mcp.json"));
  const server = mcp.mcpServers.shio;

  assert.ok(server, "the server must be named `shio` — the tool names assume it");
  // `npx -y @viglet/shio mcp`: nothing to install first, and the same bridge a developer
  // would register by hand with `claude mcp add shio -- shio mcp`.
  assert.equal(server.command, "npx");
  assert.deepEqual(server.args, ["-y", "@viglet/shio", "mcp"]);
  assert.match(server.env.SHIO_URL, /\$\{SHIO_URL/);
  assert.ok("SHIO_PASSWORD" in server.env, "the bridge authenticates as a console user");
  // A default password would be a credential shipped in a manifest.
  assert.equal(server.env.SHIO_PASSWORD, "${SHIO_PASSWORD}");
});

test("every skill declares the name and the description that make it trigger", async () => {
  const dir = join(plugin, "skills");
  const names = (await readdir(dir, { withFileTypes: true }))
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name);

  assert.deepEqual(names.sort(),
    ["shio-content-model", "shio-replicate", "shio-site", "shio-verify"]);

  for (const name of names) {
    const fields = frontmatter(await readFile(join(dir, name, "SKILL.md"), "utf8"));
    assert.equal(fields.name, name, `${name}: the frontmatter name must match the directory`);
    // The description is the *only* thing that decides whether a skill is loaded. A vague
    // one is a skill that never runs.
    assert.ok(
      fields.description && fields.description.length > 80,
      `${name}: the description must say when to use the skill`,
    );
    assert.match(
      fields.description,
      /use when/i,
      `${name}: the description must state the trigger`,
    );
  }
});

test("every command declares a description, and none of them writes without asking", async () => {
  const dir = join(plugin, "commands");
  const files = (await readdir(dir)).filter((name) => name.endsWith(".md"));

  assert.deepEqual(files.sort(), ["shio-blueprint.md", "shio-check.md", "shio-start.md"]);

  for (const file of files) {
    const text = await readFile(join(dir, file), "utf8");
    const fields = frontmatter(text);
    assert.ok(fields.description, `${file}: needs a description`);
    // The house rule for every procedure this repository ships (P7): plan before writing,
    // and publishing is never implied.
    if (/shio_apply|shio_write/.test(text)) {
      assert.match(text, /dryRun/, `${file}: a procedure that writes must plan first`);
      assert.match(
        text,
        /DRAFT|do not publish/i,
        `${file}: a procedure that writes must say publishing is separate`,
      );
    }
  }
});

/**
 * SH595 — the onboarding command asks what the instance can *do* before anything else.
 *
 * <p>`/shio-start` called `shio_context`, `shio://blueprints` and `shio_verify` — the model, the
 * packages, the lint state — and never `shio://manifest`, the one document carrying what differs
 * between one instance and the next. The cost is on record: a run diagnosed a 500 by grepping the
 * server's log while `diagnostics: true` sat in that list, and twice proposed building a capability
 * the same list already flagged shipped.</p>
 *
 * <p><b>The assertion is about order, because the defect was.</b> Nothing was missing from the
 * plugin — the resource was published and the command was free to read it. Asserting only that the
 * string appears would pass on a file that reads the manifest last, which is the same dead end
 * reached more slowly. So the manifest's position is compared against every other call the command
 * makes, and `shio_remember` is required outright: whoever worked on that instance last is the
 * other thing no amount of re-derivation recovers.</p>
 */
test("SH595: the onboarding command reads the manifest, and reads it first", async () => {
  const text = await readFile(join(plugin, "commands/shio-start.md"), "utf8");

  const manifest = text.indexOf("shio://manifest");
  assert.ok(manifest > 0, "shio-start must read shio://manifest — it is the capability list");

  for (const later of ["shio_context", "shio://blueprints", "shio_verify"]) {
    const at = text.indexOf(later);
    assert.ok(at > 0, `shio-start must still call ${later}`);
    assert.ok(
      manifest < at,
      `shio://manifest must be read before ${later}: a capability check costs one call and ` +
        "the alternative costs a session",
    );
  }

  // The two fields an assumption gets wrong, named rather than left to the reader.
  assert.match(text, /\bfeatures\b/, "it must say to report the features that are false");
  assert.match(text, /\blimits\b/, "it must say to report the limits");

  // The instance's own memory. A note is the only fact that survives a session, and it is free.
  assert.ok(
    text.includes("shio_remember"),
    "shio-start must read the instance's notes — whoever worked here last wrote down why",
  );
});

test("the AGENTS.md fragment states the rules that are expensive to learn by mistake", async () => {
  const fragment = await readFile(join(plugin, "AGENTS.fragment.md"), "utf8");

  for (const rule of [
    "shio_context",
    "shio://blueprints",
    "post:<site>",
    "dryRun",
    "DRAFT",
    "post.move",
    "shio_verify",
  ]) {
    assert.ok(fragment.includes(rule), `the fragment must mention ${rule}`);
  }
  // It is hand-written and therefore rots: it has to say so, and point at what replaces it.
  assert.match(fragment, /shio context --as agents-md/);

  // And it must point at it by the COMMAND, not by a task id. Everything under
  // `claude-plugin/` ships to installations that have no access to this repository's
  // roadmap, so an `SH<n>` there reads as a term of art the reader is missing rather than
  // as the bookkeeping it is. This assertion used to require one, which is how five of them
  // survived in the published README, package description, fragment and a skill.
  for (const file of ["README.md", "AGENTS.fragment.md", "package.json"]) {
    const text = await readFile(join(plugin, file), "utf8");
    const leaked = text.match(/SH\d{2,}/g) ?? [];
    assert.deepEqual(leaked, [], `${file}: internal task ids do not ship — found ${leaked.join(" ")}`);
  }
});
