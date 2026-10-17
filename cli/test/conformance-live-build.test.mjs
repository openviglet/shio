/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH460 — the conformance harness's staleness gate, tested where it costs nothing to run.
 *
 * ## Why this lives in `test/` and not in `conformance/`
 *
 * The gate is the first thing `requireInstance` does, so nothing under `conformance/` can reach
 * it without an instance — and the case worth asserting is the one where the instance is *wrong*,
 * which no real instance will volunteer. A `node:http` stub answering a manifest of our choosing
 * is the whole fixture, so it belongs in the fast suite: `pnpm test` runs with no instance, no
 * browser and no registry, and this needs none of the three.
 *
 * ## What it asserts, and the reason it asserts the negative case too
 *
 * A gate that fails on everything is not a gate. The incident SH460 comes from was a *reachable,
 * authenticated, correctly-configured* instance running old code, so the interesting pair is:
 * a build newer than HEAD passes silently, and a build older than HEAD stops the run before a
 * single scenario builds anything — naming the timestamp, HEAD, and the command that fixes it.
 *
 * SH500 is that sentence's other half, and it is why `staleReason` is asserted directly as well.
 * A gate that fails on everything is not a gate, and one asserted in a single direction becomes
 * one: consulting the clock after the commit already matched cancelled four tests on an instance
 * running HEAD, because a build precedes its own commit by seconds. The four cases are commit ×
 * tree and only two of them can be produced by whatever state this repository is in when the
 * suite runs, so the decision is exercised as a function over a synthetic HEAD.
 */
import assert from "node:assert/strict";
import { after, before, describe, test } from "node:test";

import { createServer } from "node:http";

import { changedPaths, compiles, requireInstance, staleReason } from "../conformance/support/instance.mjs";

/** A minimal instance: it answers the identity probe with whatever manifest the test sets. */
let server;
let baseUrl;
let manifest;

function env(extra = {}) {
  return { SHIO_URL: baseUrl, SHIO_USER: "admin", SHIO_PASSWORD: "admin", ...extra };
}

describe("SH460: the conformance harness refuses an instance older than the code under test", () => {
  before(async () => {
    server = createServer((req, res) => {
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify(manifest));
    });
    await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
    baseUrl = `http://127.0.0.1:${server.address().port}`;
  });

  after(() => server?.close());

  test("a build newer than HEAD is simply reachable, as before", async () => {
    manifest = {
      product: "Viglet Shio",
      version: "dev",
      commit: "deadbee",
      builtAt: new Date(Date.now() + 60_000).toISOString(),
    };

    const conn = await requireInstance(env());

    assert.equal(conn.baseUrl, baseUrl);
    assert.equal(conn.manifest.commit, "deadbee");
  });

  test("a build older than HEAD fails the run, naming both sides and the fix", async () => {
    manifest = { product: "Viglet Shio", version: "dev", commit: "0ldbu1d", builtAt: "2001-01-01T00:00:00Z" };

    await assert.rejects(() => requireInstance(env()), (error) => {
      assert.match(error.message, /older than the code under test/);
      assert.match(error.message, /2001-01-01T00:00:00Z/, "it must date the instance");
      assert.match(error.message, /0ldbu1d/, "and name the build, when the build names itself");
      assert.match(error.message, /HEAD {2}\w+/, "and date HEAD, so the reader compares two facts");
      assert.match(error.message, /spring-boot:run/, "and carry the command that fixes it");
      return true;
    });
  });

  test("an instance with no build identity is stale by definition — the field ships with this gate", async () => {
    manifest = { product: "Viglet Shio", version: "dev" };

    await assert.rejects(() => requireInstance(env()), /reports no build identity at all/);
  });

  test("SHIO_CONFORMANCE_ALLOW_STALE=1 is the escape hatch for an instance you did not build", async () => {
    manifest = { product: "Viglet Shio", version: "dev", builtAt: "2001-01-01T00:00:00Z" };

    const conn = await requireInstance(env({ SHIO_CONFORMANCE_ALLOW_STALE: "1" }));

    assert.equal(conn.baseUrl, baseUrl);
  });

  /**
   * SH500 against the repository this is running in, which is the state the gate got wrong.
   *
   * The manifest names the real HEAD and is dated a second *before* HEAD's commit — the ordinary
   * order, compile then commit. Both branches are asserted rather than one being skipped: on a
   * clean tree the hash is proof and the suite runs, and on a dirty one the message says which
   * signal it fell back to. The gate has no third answer, so this cannot pass vacuously.
   */
  test("an instance built from HEAD a second before the commit is not stale on a clean tree", async () => {
    const head = await liveHead();
    manifest = {
      product: "Viglet Shio",
      version: "dev",
      commit: head.sha.slice(0, 7),
      builtAt: new Date(head.at - 1000).toISOString(),
    };

    if (head.dirty) {
      await assert.rejects(() => requireInstance(env()), (error) => {
        assert.match(error.message, /tracked changes/, "a dirty tree must say why the hash was not enough");
        return true;
      });
      return;
    }

    const conn = await requireInstance(env());

    assert.equal(conn.manifest.commit, head.sha.slice(0, 7));
  });
});

describe("SH500: the four commit × tree combinations, over a HEAD the test controls", () => {
  const head = { sha: "8b1b7711", iso: "2026-08-06T10:32:38Z", at: Date.parse("2026-08-06T10:32:38Z"), dirty: false };
  const dirty = { ...head, dirty: true };

  /** The reported incident, to the second: same bytes, thirty-three seconds of clock. */
  const atHead = { commit: "8b1b771", builtAt: "2026-08-06T10:32:05Z" };

  test("a matching commit on a clean tree ends the question, whatever the clocks say", () => {
    assert.equal(staleReason(atHead, head), null);
  });

  test("a matching commit on a dirty tree falls back to the clock, and says so", () => {
    // SH764 reworded this: `dirty` now means dirty *under paths that compile*, and the message says
    // so — a reader who has just edited only docs must not read it as being about their edit.
    assert.match(
      staleReason(atHead, dirty),
      /tracked changes under paths that compile, so a commit that matches HEAD/,
    );
  });

  test("a matching commit on a dirty tree still passes when the build is the newer of the two", () => {
    assert.equal(staleReason({ ...atHead, builtAt: "2026-08-06T10:33:00Z" }, dirty), null);
  });

  test("a commit that is not HEAD is judged on the timestamp — SH460's incident, still caught", () => {
    assert.match(
      staleReason({ commit: "0ldbu1d", builtAt: "2026-08-05T09:00:00Z" }, head),
      /before HEAD was committed/,
    );
  });

  test("a build with no identity at all is stale, and a matching commit does not need one", () => {
    assert.match(staleReason({ version: "dev" }, head), /no build identity at all/);
    assert.equal(staleReason({ commit: "8b1b7711" }, head), null);
  });

  test("an abbreviation too short to identify a commit does not match HEAD", () => {
    assert.match(
      staleReason({ commit: "8b1b7", builtAt: "2026-08-06T10:32:05Z" }, head),
      /before HEAD was committed/,
    );
  });
});

/**
 * SH764 — the gate that a roadmap edit trips, and the two narrowings that stop it.
 *
 * The assertion that would have caught it, at the boundary it crossed (SH527): `staleReason` told
 * a run to stop because two files under `docs/` had been edited, on an instance built from the
 * exact commit under test. Findings are filed as roadkeep tasks and a red suite is a stop, so
 * recording a finding disabled the gate that would have confirmed it.
 */
describe("SH764: the staleness gate is about the JVM, not about the working tree", () => {
  const head = { sha: "4b04b20e", iso: "2026-08-16T21:09:00Z", at: Date.parse("2026-08-16T21:09:00Z"), dirty: false };

  /** The incident, to the field: same commit, built three minutes earlier, two docs files edited. */
  const atHead = { commit: "4b04b20", builtAt: "2026-08-16T21:06:45Z" };

  test("a tree dirty only under docs/ is not a fact about the running classes", () => {
    // `dirty` is now computed over compiling paths, so roadkeep's two files never set it. This is
    // the run that was cancelled: instance at HEAD, docs/ROADMAP.md and docs/IMPROVEMENTS.md open.
    assert.equal(gitDirty("M  docs/ROADMAP.md\nM  docs/IMPROVEMENTS.md\n"), false);
    assert.equal(staleReason(atHead, { ...head, dirty: false }), null);
  });

  test("a tree dirty under a path that compiles still falls back to the clock", () => {
    assert.equal(gitDirty("M  shio-app/src/main/java/com/viglet/shio/render/ShRenderModel.java\n"), true);
    assert.match(staleReason(atHead, { ...head, dirty: true }), /paths that compile/);
  });

  test("the build's commit is diffed against HEAD before the clock is consulted", () => {
    // A merge commit that moved only documentation: the build predates it and the classes are
    // identical, which `builtAt >= head.at` calls stale and a content comparison does not.
    const older = { commit: "0ldbu1d", builtAt: "2026-08-16T21:06:45Z" };
    assert.match(staleReason(older, head), /before HEAD was committed/);
    assert.equal(staleReason(older, { ...head, unchangedSince: true }), null);
  });

  test("unchanged-since cannot excuse a tree that is dirty where it compiles", () => {
    assert.match(
      staleReason({ commit: "0ldbu1d", builtAt: "2026-08-16T21:06:45Z" },
        { ...head, unchangedSince: true, dirty: true }),
      /before HEAD was committed/,
    );
  });

  test("a packaged README is not documentation — the false green an extension rule would open", () => {
    // Seven of these ship inside the jar as blueprint carried files (ShBlueprintCarriedFilesTest).
    // `*.md` is the obvious spelling of this rule and it would exempt a compiled-in resource.
    assert.equal(compiles("shio-app/src/main/resources/blueprints/blog/files/README.md"), true);
    assert.equal(compiles("shio-app/src/main/resources/js/server-side/README.md"), true);
    assert.equal(compiles("docs/ROADMAP.md"), false);
    assert.equal(compiles("docs/agents/build.md"), false);
    assert.equal(compiles("README.md"), false, "root markdown is documentation");
    assert.equal(compiles("agents.md"), false);
    assert.equal(compiles("shio-app/src/main/java/com/viglet/shio/ShioApplication.java"), true);
    assert.equal(compiles("cli/src/convert.mjs"), true, "unknown is compiling — the safe direction");
  });

  test("porcelain is parsed the way git writes it: renames, quotes and both sides", () => {
    assert.deepEqual(changedPaths("M  docs/ROADMAP.md\n?? scratch\n"), ["docs/ROADMAP.md", "scratch"]);
    assert.deepEqual(
      changedPaths('R  docs/OLD.md -> shio-app/src/main/java/Sh.java\n'),
      ["docs/OLD.md", "shio-app/src/main/java/Sh.java"],
      "a rename yields both sides — moving a .java out of a module changes what compiles too",
    );
    assert.deepEqual(changedPaths('M  "docs/a b.md"\n'), ["docs/a b.md"]);
    assert.deepEqual(changedPaths(""), []);
  });

  /** `gitDirty`'s decision without the subprocess: the parse and the predicate, composed. */
  function gitDirty(porcelain) {
    return changedPaths(porcelain).some(compiles);
  }
});

/**
 * HEAD as the gate itself reads it — the same three facts, asked of the repo under test.
 *
 * <p>`dirty` is computed with {@link compiles}, and it has to be: this copy read the whole porcelain
 * and SH764 narrowed the real one to paths that compile, so on a tree carrying only documentation
 * edits the two disagreed — the test took its dirty branch and expected a refusal the gate correctly
 * no longer makes. Caught by the suite on a tree dirty with exactly the roadmap files this task is
 * about, which is the case the whole change exists for. A second copy of a predicate is the drift
 * SH245 is about, so this composes the exported one rather than restating it.</p>
 */
async function liveHead() {
  const { execFile } = await import("node:child_process");
  const { promisify } = await import("node:util");
  const run = promisify(execFile);
  const repo = new URL("../..", import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1");

  const { stdout } = await run("git", ["-C", repo, "log", "-1", "--format=%h %cI"], { timeout: 10_000 });
  const [sha, iso] = stdout.trim().split(/\s+/);
  const { stdout: status } = await run("git", ["-C", repo, "status", "--porcelain", "-uno"], { timeout: 10_000 });

  return { sha, iso, at: Date.parse(iso), dirty: changedPaths(status).some(compiles) };
}
