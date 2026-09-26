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
import { dirname, join, relative } from "node:path";
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
  //
  // SH1119 — and then it asserted nothing at all for its whole life. The pattern held two literal
  // backspaces, U+0008, where `\b` was written: a shell heredoc resolved the escape before the file
  // was written, and no source file contains a backspace, so `match` returned null every run. The
  // three published files carry no id today, checked when this was repaired, so nothing drifted
  // while it was blind — but nothing here could have said so.
  for (const file of ["README.md", "AGENTS.fragment.md", "package.json"]) {
    const text = await readFile(join(plugin, file), "utf8");
    const leaked = text.match(/\bSH\d{2,}\b/g) ?? [];
    assert.deepEqual(leaked, [], `${file}: internal task ids do not ship — found ${leaked.join(" ")}`);
  }
});

/**
 * SH604 — the published artefacts do not enumerate the tool surface.
 *
 * The README listed ten tools and four resources against a server carrying eleven and seven, so
 * `shio_remember` and `shio://diagnostics` had both shipped and the plugin had never learned
 * either. Both are the ones that cost most to miss: `remember` is where a convention survives a
 * session, and `diagnostics` is what makes a 5xx readable to somebody whose only surface is MCP.
 *
 * **The rule is not "regenerate the list".** Naming a tool to teach a rule is teaching; enumerating
 * tools is a copy — and a copy in the one artefact whose reader cannot check it against the source.
 * So the fragment naming `shio_apply` while stating what apply guarantees stays, and an inventory
 * is refused by count: three or more distinct tool names in one line is a list, whatever it calls
 * itself.
 */
test("SH604: no published artefact carries an inventory of the tool surface", async () => {
  // Everything that ships, not the three the id-leak guard happened to name. The skills and
  // commands are the artefacts a reader meets *after* installing, and they are where teaching
  // lives — each names one tool while stating a rule, which is why they pass a rule that refuses
  // three. Walking them is what makes the distinction a property of the plugin rather than of the
  // file somebody remembered to check.
  for (const file of await published()) {
    const text = await readFile(file, "utf8");
    for (const [index, line] of text.split("\n").entries()) {
      const tools = new Set(line.match(/\bshio_[a-z]+\b/g) ?? []);
      const resources = new Set(line.match(/shio:\/\/[a-z]+/g) ?? []);
      assert.ok(
        tools.size < 3 && resources.size < 3,
        `${relative(plugin, file)}:${index + 1} enumerates the surface (${[...tools, ...resources].join(", ")}). `
          + "An inventory here is a copy of something the reader can ask the instance for, and it "
          + "is wrong the first time a tool ships — it listed ten of eleven for two releases. "
          + "Name one to teach a rule; route to `shio://manifest` for the list.",
      );
    }
  }
});

/**
 * SH604 — and the artefacts route to the thing that cannot be stale.
 *
 * Deleting the list is only half: a reader who came for the surface has to leave with something.
 * `shio://manifest` is the instance describing itself, which is the one answer that is right by
 * construction.
 */
test("SH604: the README sends a reader to the manifest instead", async () => {
  const readme = await readFile(join(plugin, "README.md"), "utf8");
  assert.match(readme, /shio:\/\/manifest/,
    "the README must point at the resource that answers what this plugin exposes");
});

/**
 * SH949 — this repository loads the plugin it publishes, and the two skill sets stay apart.
 *
 * <p>The marketplace shipped four skills and an MCP server to strangers while
 * `.claude/settings.json` enabled only `roadkeep@alegauss`. So the skills that teach an agent how
 * to build a site in Shio were published by, and never loaded in, the repository that wrote them —
 * which means every breakage in them arrives as somebody else's report rather than as a session
 * here behaving oddly.</p>
 *
 * <p>Enabling it forces a decision worth forcing, and the second assertion is what holds it. There
 * are now two sets of skills on one subject, and two authorities on one subject is the drift
 * `agents.md` already names. The split: <b>a plugin skill serves a session driving an instance; a
 * project skill serves a session changing this source tree.</b> A shared name would make the
 * routing undecidable at the only moment it matters — when a description matches and Claude has to
 * pick one — so a collision is a failure rather than a note.</p>
 */
test("SH949: the marketplace this repository publishes is enabled in this repository", async () => {
  const marketplace = await json(join(repo, ".claude-plugin/marketplace.json"));
  const settings = await json(join(repo, ".claude/settings.json"));

  const known = settings.extraKnownMarketplaces ?? {};
  assert.ok(
    Object.hasOwn(known, marketplace.name),
    `SH949: .claude-plugin/marketplace.json publishes \`${marketplace.name}\` and`
      + ` .claude/settings.json does not know it. Known: ${Object.keys(known).join(", ") || "none"}.`
      + " A plugin this repository ships and never loads is one whose breakages are reported by"
      + " strangers.",
  );

  const enabled = settings.enabledPlugins ?? {};
  for (const entry of marketplace.plugins) {
    const key = `${entry.name}@${marketplace.name}`;
    assert.equal(
      enabled[key], true,
      `SH949: ${key} is published by this repository and not enabled here. Add it to`
        + " enabledPlugins in .claude/settings.json, so a session in this tree loads exactly what"
        + " somebody installing the marketplace gets.",
    );
  }
});

test("SH949: no skill name is both a plugin skill and a project skill", async () => {
  const directories = async (dir) =>
    (await readdir(dir, { withFileTypes: true }))
      .filter((entry) => entry.isDirectory())
      .map((entry) => entry.name);

  const ofPlugin = await directories(join(plugin, "skills"));
  const ofProject = await directories(join(repo, ".claude/skills"));

  // Neither side may be empty, or the intersection below is empty for the wrong reason — the
  // failure mode of every comparison of two walks (SH322).
  assert.ok(ofPlugin.length >= 4, `SH949: found ${ofPlugin.length} plugin skills; there were 4`);
  assert.ok(ofProject.length >= 9, `SH949: found ${ofProject.length} project skills; there were 11`);

  const shared = ofPlugin.filter((name) => ofProject.includes(name)).sort();
  assert.deepEqual(
    shared,
    [],
    `SH949: ${shared.join(", ")} is both a plugin skill and a project skill.\n`
      + "  A plugin skill serves a session DRIVING AN INSTANCE; a project skill serves a session"
      + " CHANGING THIS SOURCE TREE. Both sets are loaded in this repository, so a shared name"
      + " makes the routing undecidable exactly when the descriptions overlap.\n"
      + "  Rename one to say which side it is on — `shio-replicate` (drive) against"
      + " `shio-replication-test` (change) is the pair this rule was written from.",
  );
});

/** Every file the plugin ships that a reader can read: markdown and the manifest. */
async function published() {
  const out = [];
  const walk = async (dir) => {
    for (const entry of await readdir(dir, { withFileTypes: true })) {
      const path = join(dir, entry.name);
      if (entry.isDirectory()) {
        if (entry.name !== "test" && entry.name !== "node_modules") await walk(path);
      } else if (/\.(md|json)$/.test(entry.name)) {
        out.push(path);
      }
    }
  };
  await walk(plugin);
  return out.sort();
}
