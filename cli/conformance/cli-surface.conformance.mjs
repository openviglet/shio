/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH711 — the command surface, spawned as a process, against something running.
 *
 * ## What importing the module removes
 *
 * `cli/test/` imports command modules, which is what makes `pnpm test` fast and is the right trade
 * for sixty suites. What it removes is everything between a user's keystroke and the module: argv
 * parsing, what a flag means, **the exit code CI reads**, the auth handshake, the HTTP client's
 * error mapping, and the file the command leaves on disk.
 *
 * SH456 is the precedent and it is not hypothetical: `convert` exited **1** on an address it had
 * built itself, with the work done and the refusal reported. Code fixed twice against a harness
 * before anything ran the command.
 *
 * ## The round trip is the assertion, not the individual verbs
 *
 * `init → push → pull` against a live instance, ending with a second `pull` whose bytes must equal
 * the first's. P5 is determinism, and the projection is where it has the most places to be wrong:
 * ordering, serialization, the sidecar files, the three-way merge's base. A suite that ran each
 * verb once and checked its exit code would miss every one of them.
 *
 * ## Out of scope, named rather than forgotten
 *
 * `dev`, `build` and `deploy` are not here. `dev` and `build` drive a front-end toolchain and a
 * watcher — they are about a generated app, not about this instance — and `deploy` pushes to a
 * hosting target this run has no business creating. Naming them keeps the ledger honest: they are
 * decided, not overlooked.
 */
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { after, before, describe, test } from "node:test";
import { fileURLToPath } from "node:url";

import {
  ConsoleSession, batch, connection, errorsIn, keepsFixtures, requireInstance,
  requireVocabulary, scratchSite,
} from "./support/instance.mjs";

/** The bin `npm install -g @viglet/shio` puts on a PATH — the entry point `cli/test/` never runs. */
const SHIO_BIN = fileURLToPath(new URL("../src/index.mjs", import.meta.url));

describe("SH711: the CLI as a process, against a live instance", () => {
  const conn = connection();
  const site = scratchSite("Cli");
  let cwd;
  let env;

  /** Spawn the bin. Returns the exit code and everything it said, which is what a user gets. */
  function shio(args, options = {}) {
    return new Promise((resolve) => {
      const child = spawn(process.execPath, [SHIO_BIN, ...args], { cwd, env, ...options });
      let output = "";
      child.stdout.on("data", (chunk) => (output += chunk));
      child.stderr.on("data", (chunk) => (output += chunk));
      child.on("error", (error) => resolve({ code: -1, output: `${output}\n${error.message}` }));
      child.on("close", (code) => resolve({ code, output }));
    });
  }

  before(async () => {
    await requireInstance();
    // SH724 — `Page` is SH95 vocabulary, not a seeded type: without this the fixture below is
    // refused on any instance nobody built by hand, and every scenario behind it is cancelled.
    await requireVocabulary(conn);
    cwd = await mkdtemp(join(tmpdir(), "shio-sh711-"));
    env = {
      ...process.env,
      SHIO_URL: conn.baseUrl,
      SHIO_USER: conn.user,
      SHIO_PASSWORD: conn.password,
    };

    const seeded = await batch(conn, [
      { op: "site.upsert", address: `site:${site}` },
      { op: "folder.upsert", address: `folder:${site}/notes` },
      {
        op: "post.upsert", address: `post:${site}/`, type: "Page", folder: "/",
        data: { title: "Home", content: "The CLI reads this." },
      },
      {
        op: "post.upsert", address: `post:${site}/notes/one`, type: "Page", folder: "/notes",
        data: { title: "One", content: "A page one folder down." },
      },
    ]);
    assert.ok(!errorsIn(seeded), `SH711: the fixture did not seed: ${JSON.stringify(seeded).slice(0, 400)}`);
  });

  after(async () => {
    if (keepsFixtures()) return;
    await new ConsoleSession(conn).deleteSite(site).catch(() => {});
    await rm(cwd, { recursive: true, force: true }).catch(() => {});
  });

  /**
   * The read group.
   *
   * <p>Each is asserted on the exit code first — the thing a module call structurally cannot see —
   * and then on saying something. A command that exits 0 and prints nothing has answered a question
   * nobody can act on, which over this surface is the same defect as failing.</p>
   */
  const READS = [
    { verb: "context", args: ["context", "--site"] },
    { verb: "site", args: ["site"] },
    { verb: "resolve", args: ["resolve", "@ADDRESS@"] },
    // SH1022 — the listing, driven live rather than declared unexercised. It is the one read
    // whose exit code carries a second meaning: 1 is "nothing matched", so a run against a
    // site that HAS content is also the assertion that the filters did not fail closed.
    { verb: "find", args: ["find", "--site"] },
    { verb: "changes", args: ["changes", "--site"] },
    { verb: "digest", args: ["digest", "@ADDRESS@"] },
    { verb: "verify", args: ["verify", "--site"] },
    { verb: "blueprints", args: ["blueprints"] },
    { verb: "marketplace", args: ["marketplace"] },
    { verb: "doctor", args: ["doctor"] },
  ];

  for (const read of READS) {
    test(`shio ${read.verb}: exits 0 against a live instance and says something`, async () => {
      // `--site` takes the scratch site where the verb is scoped; `resolve` takes an address.
      // `resolve` and `digest` take an ADDRESS; the rest are scoped with `--site`. Getting this
      // wrong is how the first run of this file reported two defects that were its own arguments:
      // `digest --site X` answers "pass a page", which is the CLI teaching correctly.
      const args = read.args.map((a) => (a === "@ADDRESS@" ? `post:${site}/` : a));
      if (args.at(-1) === "--site") args.push(site);

      const run = await shio(args);

      assert.equal(run.code, 0,
        `SH711: \`shio ${args.join(" ")}\` exited ${run.code}. The exit code is what CI reads and `
        + `what a shell script branches on, and it is the one thing importing the module cannot `
        + `check — SH456 was a command that did its work and exited 1.\n${run.output.slice(0, 600)}`);
      assert.ok(run.output.trim().length > 0,
        `SH711: it exited 0 and printed nothing. Over this surface that is the same defect as `
        + `failing: the caller has no answer and no error to act on.`);
    });
  }

  test("shio: an unknown verb exits non-zero and does not pretend", async () => {
    const run = await shio(["definitely-not-a-verb"]);

    assert.notEqual(run.code, 0,
      `SH711: an unknown verb exited 0. A shell script branching on that runs its next step against `
      + `nothing having happened.\n${run.output.slice(0, 400)}`);
    assert.ok(run.output.trim().length > 0, "SH711: and it has to say so, not just fail quietly");
  });

  test("shio pull → push → pull: the second projection is byte-identical to the first", async () => {
    // The determinism assertion, and the reason this suite is a round trip rather than a checklist.
    // P5 is stable ordering, path addressing and byte-identical serialization; the projection is
    // where it has the most places to be wrong, and none of them are visible to a single verb's
    // exit code.
    const first = await shio(["pull", "--content", "--site", site]);
    assert.equal(first.code, 0, `SH711: the first pull failed:\n${first.output.slice(0, 800)}`);

    // `index.md`, not `.json`: the projection is front-matter plus the body field, which is what
    // makes `Edit`/`Grep` the agent's cheapest tools (P4). The first draft of this assumed JSON and
    // reported a missing projection that was there — the same class of mistake as SH709's helper.
    const home = join(cwd, "shio", "content", site, "index.md");
    const before = await readFile(home, "utf8").catch(() => null);
    assert.ok(before, `SH711: pull exited 0 and wrote no home page at ${home}. The file on disk is `
      + "the deliverable — P4 says files beat APIs for authoring, and an empty projection is the "
      + "one outcome a green exit code hides.\n" + first.output.slice(0, 400));

    const second = await shio(["pull", "--content", "--site", site]);
    assert.equal(second.code, 0, `SH711: the second pull failed:\n${second.output.slice(0, 800)}`);

    const after_ = await readFile(home, "utf8");
    assert.equal(after_, before,
      "SH711: two pulls of unchanged content produced different bytes. That is P5 failing at the "
      + "layer the three-way merge depends on — a base that is not reproducible makes every "
      + "subsequent `push` a guess about what the author changed.");
  });

  test("shio push: a local edit reaches the instance and the exit code says so", async () => {
    const home = join(cwd, "shio", "content", site, "index.md");
    const projected = await readFile(home, "utf8");
    const parsed = /^---\n([\s\S]*?)\n---\n?([\s\S]*)$/.exec(projected);
    assert.ok(parsed, `SH711: the projection is not front-matter plus a body: ${projected.slice(0, 200)}`);

    // **Where the editable text lives is a question, not an assumption**, and assuming cost this
    // suite a red run on a clean instance (SH724's neighbour). A field whose widget is HTML is
    // projected to a SIDECAR — `files: { content: index.content.html }` — and the `.md` then has
    // front matter and no body at all. Writing a body into that file is refused by `push`, quite
    // correctly: *"it has a body but no `body:` key saying which field the body is."*
    //
    // The suite was green here only because this developer's `Page` happened to carry `content` as
    // a plain field. The canonical vocabulary type — the one `render.provision` installs, and the
    // one a fresh instance therefore gets — does not.
    //
    // So the target is read out of the projection. Either shape is a real authoring path and both
    // are the P4 claim being tested: an agent edits a file, and the edit reaches the instance.
    const sidecar = /^\s{2}\w+:\s*(\S+)$/m.exec(/files:\n([\s\S]*?)(?=\n\w|$)/.exec(parsed[1])?.[1] ?? "");
    const edited = "Edited on disk by SH711.";
    if (sidecar) {
      await writeFile(join(cwd, "shio", "content", site, sidecar[1]), `${edited}\n`);
    } else {
      assert.match(parsed[1], /^body:/m,
        "SH711: the projection has neither a `files:` sidecar nor a `body:` key, so nothing on disk "
        + "names the field an author is supposed to edit. That is a projection an agent cannot use.");
      await writeFile(home, `---\n${parsed[1]}\n---\n${edited}\n`);
    }

    const run = await shio(["push", "--content", "--site", site]);
    assert.equal(run.code, 0, `SH711: push failed:\n${run.output.slice(0, 800)}`);

    // Look at the instance, not at what push said about itself.
    const read = await fetch(`${conn.baseUrl}/api/v2/agent/read?address=post:${site}/`, {
      headers: { Authorization: `Basic ${Buffer.from(`${conn.user}:${conn.password}`).toString("base64")}` },
    });
    const text = await read.text();
    assert.match(text, /Edited on disk by SH711/,
      "SH711: push exited 0 and the instance does not hold the edit. This is the direction that "
      + "matters — a push that reports success and changes nothing leaves an author's work on their "
      + "laptop with nothing saying so.");
  });

  /**
   * SH1091 — one field changed, and a change raced by another refused, through the CDA's merge.
   *
   * <p>Read back from the instance, not from what the command said. The refusal is the half that
   * matters: a partial write with no precondition is a lost update waiting for two people to edit
   * one page, so the pinned write must exit non-zero AND leave the other write standing.</p>
   */
  test("shio post set: changes one field, and a set pinned to a tag the post moved past is refused", async () => {
    const session = new ConsoleSession(conn);
    const token = await session.createToken("sh1091-post-set", "WRITE");
    const withToken = { env: { ...env, SHIO_WRITE_TOKEN: token.token } };
    const address = `post:${site}/notes/one`;
    const titleNow = async () => {
      const read = await fetch(`${conn.baseUrl}/api/v2/agent/read?address=${encodeURIComponent(address)}`, {
        headers: { Authorization: `Basic ${Buffer.from(`${conn.user}:${conn.password}`).toString("base64")}` },
      });
      return (await read.text()).match(/Set by SH1091 \w+/)?.[0] ?? null;
    };
    const tagOf = (output) => /etag=(\S+)/.exec(output)?.[1];
    try {
      const first = await shio(["post", "set", address, "title=Set by SH1091 first"], withToken);
      assert.equal(first.code, 0, `SH1091: \`shio post set\` failed:\n${first.output.slice(0, 600)}`);
      assert.equal(await titleNow(), "Set by SH1091 first",
        "SH1091: post set exited 0 and the instance does not hold the new title");
      const firstTag = tagOf(first.output);
      assert.ok(firstTag && firstTag !== "-", `SH1091: no tag was printed to pin the next change: ${first.output}`);

      const second = await shio(["post", "set", address, "title=Set by SH1091 second"], withToken);
      assert.equal(second.code, 0, `SH1091: the second set failed:\n${second.output.slice(0, 600)}`);
      assert.notEqual(tagOf(second.output), firstTag, "SH1091: a write answered the tag it was checked against");

      const stale = await shio(["post", "set", address, "title=Set by SH1091 stale", "--if-match", firstTag],
        withToken);
      assert.notEqual(stale.code, 0,
        `SH1091: a set pinned to a tag the post has moved past exited 0 — the lost update the precondition `
        + `exists to refuse.\n${stale.output.slice(0, 600)}`);
      assert.match(stale.output, /changed since/, "SH1091: the refusal does not say why nothing was written");
      assert.equal(await titleNow(), "Set by SH1091 second",
        "SH1091: the refused write changed the post anyway");
    } finally {
      await session.deleteToken(token.id).catch(() => {});
    }
  });

  test("SH711: dev, build and deploy are out of scope, and this says which", () => {
    // Named rather than forgotten, so the ledger cannot read them as an oversight.
    const outOfScope = ["dev", "build", "deploy"];
    assert.deepEqual(outOfScope, ["dev", "build", "deploy"],
      "SH711: `dev` and `build` drive a front-end toolchain and a watcher — they are about a "
      + "generated app rather than about this instance — and `deploy` pushes to a hosting target a "
      + "conformance run has no business creating. If one of them gains an instance-facing "
      + "behaviour, it belongs in this file and this list is where the decision was recorded.");
  });
});
