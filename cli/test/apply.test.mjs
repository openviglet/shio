/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH129 — `shio apply`. Network-free: a scripted fetch records the request.
 *
 * The properties under test are the ones that make the command usable in CI rather
 * than the output prose: --dry-run and --check never reach the write endpoint,
 * --check exits non-zero exactly when the plan is non-empty, and a malformed
 * document fails with the shape of a working one rather than a parser message.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import * as apply from "../src/commands/apply.mjs";

const DOCUMENT = { site: "Blog", folders: [{ path: "/blog" }] };

const CLEAN = { dryRun: true, ops: 1, changed: 0, results: [{ op: "folder.upsert", address: "folder:Blog/blog", action: "noop" }] };
const DRIFTED = {
  dryRun: true,
  ops: 2,
  changed: 2,
  results: [
    { op: "folder.upsert", address: "folder:Blog/blog", action: "create" },
    { op: "post.delete", address: "post:Blog/blog/old", action: "delete", detail: "to trash (restorable)" },
  ],
};

function sink() {
  const out = { text: "" };
  out.write = (s) => {
    out.text += s;
  };
  return out;
}

function scriptedFetch(body, { status = 200 } = {}) {
  const calls = [];
  const fetchImpl = async (url, init) => {
    calls.push({ url: String(url), init });
    return {
      ok: status >= 200 && status < 300,
      status,
      headers: { get: () => null },
      async text() {
        return typeof body === "string" ? body : JSON.stringify(body);
      },
      async json() {
        return body;
      },
    };
  };
  fetchImpl.calls = calls;
  return fetchImpl;
}

function ctxWith(fetchImpl, { file = JSON.stringify(DOCUMENT) } = {}) {
  return {
    env: { SHIO_URL: "http://shio.test", SHIO_USER: "admin", SHIO_PASSWORD: "secret" },
    cwd: "/repo",
    stdout: sink(),
    stderr: sink(),
    fetch: fetchImpl,
    fs: {
      async readFile(path) {
        if (file === null) {
          const error = new Error("not found");
          error.code = "ENOENT";
          throw error;
        }
        assert.match(String(path), /site\.json$/);
        return file;
      },
    },
  };
}

test("apply posts the document to /agent/apply", async () => {
  const fetchImpl = scriptedFetch({ dryRun: false, ops: 1, changed: 1, results: [] });
  const ctx = ctxWith(fetchImpl);

  await apply.run({}, ["site.json"], ctx);

  const call = fetchImpl.calls[0];
  assert.match(call.url, /\/api\/v2\/agent\/apply$/);
  assert.equal(call.init.method, "POST");
  assert.deepEqual(JSON.parse(call.init.body), DOCUMENT);
});

test("--dry-run goes to /agent/plan and never to /agent/apply", async () => {
  const fetchImpl = scriptedFetch(CLEAN);
  const ctx = ctxWith(fetchImpl);

  await apply.run({ dryRun: true }, ["site.json"], ctx);

  assert.equal(fetchImpl.calls.length, 1);
  assert.match(fetchImpl.calls[0].url, /\/api\/v2\/agent\/plan$/);
});

test("--check is a plan, and exits 0 when nothing has drifted", async () => {
  const fetchImpl = scriptedFetch(CLEAN);
  const ctx = ctxWith(fetchImpl);

  const result = await apply.run({ check: true }, ["site.json"], ctx);

  assert.match(fetchImpl.calls[0].url, /\/agent\/plan$/);
  assert.equal(result.exitCode, undefined);
  assert.match(ctx.stdout.text, /0 change\(s\) of 1 op\(s\) — nothing written/);
});

test("--check exits non-zero on drift and prints what drifted", async () => {
  const ctx = ctxWith(scriptedFetch(DRIFTED));

  const result = await apply.run({ check: true }, ["site.json"], ctx);

  assert.equal(result.exitCode, 1);
  // The plan is on stdout, so CI shows *what* drifted rather than only that it did.
  assert.match(ctx.stdout.text, /create folder:Blog\/blog/);
  assert.match(ctx.stdout.text, /delete post:Blog\/blog\/old \(to trash \(restorable\)\)/);
  assert.match(ctx.stderr.text, /2 change\(s\) pending/);
});

test("no-ops are counted but not listed — on a converged document they are every line", async () => {
  const ctx = ctxWith(scriptedFetch(CLEAN));

  await apply.run({ dryRun: true }, ["site.json"], ctx);

  assert.equal(ctx.stdout.text, "shio plan: 0 change(s) of 1 op(s) — nothing written\n");
});

test("a move's broken inbound links are printed under it (SH140)", async () => {
  // A row nobody prints is a row that does not exist. The server reports which pages a move
  // broke on the move's own row; a caller in a terminal has to be told the same thing.
  const moved = {
    dryRun: false,
    ops: 1,
    changed: 1,
    results: [{
      op: "post.move",
      address: "post:Blog/blog/new",
      action: "move",
      detail: "the live URL changes immediately",
      inboundLinks: {
        from: "/blog/old",
        addresses: ["post:Blog/blog/a", "post:Blog/blog/b"],
        found: 5,
        fix: "Repoint those links at post:Blog/blog/new.",
      },
    }],
  };
  const ctx = ctxWith(scriptedFetch(moved));

  await apply.run({}, ["site.json"], ctx);

  assert.match(ctx.stdout.text, /links to \/blog\/old still in 2 of 5 page\(s\):/);
  assert.match(ctx.stdout.text, /post:Blog\/blog\/a/);
  assert.match(ctx.stdout.text, /post:Blog\/blog\/b/);
});

test("--ids asks for object ids and prints them", async () => {
  const withIds = {
    dryRun: false,
    ops: 1,
    changed: 1,
    results: [{ op: "post.upsert", address: "post:Blog/blog/a", action: "create", id: "abc" }],
  };
  const fetchImpl = scriptedFetch(withIds);
  const ctx = ctxWith(fetchImpl);

  await apply.run({ ids: true }, ["site.json"], ctx);

  assert.match(fetchImpl.calls[0].url, /\?ids=true$/);
  assert.match(ctx.stdout.text, /create post:Blog\/blog\/a abc/);
});

test("a document may arrive on stdin so it can be generated by the step before", async () => {
  const fetchImpl = scriptedFetch({ dryRun: true, ops: 0, changed: 0, results: [] });
  const ctx = ctxWith(fetchImpl);
  ctx.stdin = (async function* () {
    yield JSON.stringify({ site: "Piped" });
  })();

  await apply.run({ dryRun: true }, ["-"], ctx);

  assert.deepEqual(JSON.parse(fetchImpl.calls[0].init.body), { site: "Piped" });
});

test("invalid JSON fails with the shape of a working document", async () => {
  const ctx = ctxWith(scriptedFetch(CLEAN), { file: "{ not json" });

  await assert.rejects(
    () => apply.run({}, ["site.json"], ctx),
    /is not valid JSON.*"site":"Blog"/s,
  );
});

test("a missing file says so instead of throwing ENOENT", async () => {
  const ctx = ctxWith(scriptedFetch(CLEAN), { file: null });

  await assert.rejects(() => apply.run({}, ["site.json"], ctx), /no such file "site\.json"/);
});

test("an empty document says how to pass one", async () => {
  const ctx = ctxWith(scriptedFetch(CLEAN), { file: "   " });

  await assert.rejects(() => apply.run({}, ["site.json"], ctx), /the document is empty/);
});

test("a teaching problem from the server survives intact", async () => {
  // The batch names the failing op in `at`; collapsing the body to a status would
  // turn a one-line fix into a bisect.
  const problem = JSON.stringify({
    title: "No post-type named 'Artcle'",
    didYouMean: "Article",
    at: "ops[3]",
  });
  const ctx = ctxWith(scriptedFetch(problem, { status: 400 }));

  await assert.rejects(
    () => apply.run({}, ["site.json"], ctx),
    (error) => {
      assert.equal(error.status, 400);
      assert.match(error.body, /ops\[3\]/);
      return true;
    },
  );
});

/**
 * SH108 — a document that prunes takes two calls: plan for the token, then apply with it.
 * Scripted per-call rather than with one body, because the whole point is that the two
 * responses differ.
 */
function twoStepFetch(planBody, applyBody) {
  const calls = [];
  const fetchImpl = async (url, init) => {
    calls.push({ url: String(url), init });
    const body = String(url).endsWith("/plan") ? planBody : applyBody;
    return {
      ok: true,
      status: 200,
      headers: { get: () => null },
      async text() {
        return JSON.stringify(body);
      },
      async json() {
        return body;
      },
    };
  };
  fetchImpl.calls = calls;
  return fetchImpl;
}

test("a pruning apply plans first and sends back the confirm token (SH108)", async () => {
  const plan = {
    dryRun: true,
    ops: 2,
    changed: 1,
    confirm: "9f2c1ab4d0e77a51",
    results: [
      { op: "folder.upsert", address: "folder:Blog/blog", action: "noop" },
      { op: "post.delete", address: "post:Blog/blog/old", action: "delete" },
    ],
  };
  const fetchImpl = twoStepFetch(plan, { dryRun: false, ops: 2, changed: 1, results: [] });
  const ctx = ctxWith(fetchImpl, { file: JSON.stringify({ ...DOCUMENT, prune: true }) });

  await apply.run({}, ["site.json"], ctx);

  assert.equal(fetchImpl.calls.length, 2);
  assert.match(fetchImpl.calls[0].url, /\/agent\/plan$/);
  assert.match(fetchImpl.calls[1].url, /\/agent\/apply$/);
  assert.equal(JSON.parse(fetchImpl.calls[1].init.body).confirm, plan.confirm);
  // The deletions are printed *before* the apply: the token's promise is that a human saw them.
  assert.match(ctx.stdout.text, /deleting 1 object\(s\)/);
  assert.match(ctx.stdout.text, /post:Blog\/blog\/old/);
});

test("a document that cannot delete stays one call", async () => {
  const fetchImpl = twoStepFetch({}, { dryRun: false, ops: 1, changed: 1, results: [] });
  const ctx = ctxWith(fetchImpl);

  await apply.run({}, ["site.json"], ctx);

  assert.equal(fetchImpl.calls.length, 1);
  assert.match(fetchImpl.calls[0].url, /\/agent\/apply$/);
});

test("a prune that would delete nothing needs no confirm and no second thought", async () => {
  // The server hands back no token when there is nothing to destroy, so the apply goes
  // ahead without one — the flag alone must not be the test.
  const fetchImpl = twoStepFetch({ dryRun: true, ops: 1, changed: 0, results: [] },
    { dryRun: false, ops: 1, changed: 0, results: [] });
  const ctx = ctxWith(fetchImpl, { file: JSON.stringify({ ...DOCUMENT, prune: true }) });

  await apply.run({}, ["site.json"], ctx);

  assert.equal(fetchImpl.calls.length, 2);
  assert.equal(JSON.parse(fetchImpl.calls[1].init.body).confirm, undefined);
  assert.doesNotMatch(ctx.stdout.text, /deleting/);
});

// --- SH439: one meaning for --create-site ---

test("SH439: --create-site on the document mode, so the flag means one thing in both", async () => {
  // It was a blueprint-only flag, and the document half's absent-site refusal named a `site.upsert`
  // op instead — the same message `shio convert` could not act on. One flag, one compiled op.
  const fetchImpl = scriptedFetch({ dryRun: false, ops: 2, changed: 2, results: [] });
  const ctx = ctxWith(fetchImpl);

  await apply.run({ createSite: true }, ["site.json"], ctx);

  assert.deepEqual(JSON.parse(fetchImpl.calls[0].init.body), { ...DOCUMENT, createSite: true });
});

test("SH439: without the flag the document is untouched", async () => {
  const fetchImpl = scriptedFetch({ dryRun: false, ops: 1, changed: 1, results: [] });
  const ctx = ctxWith(fetchImpl);

  await apply.run({}, ["site.json"], ctx);

  assert.ok(!("createSite" in JSON.parse(fetchImpl.calls[0].init.body)));
});

test("SH439: an absent site names the flag, and any other 404 is left exactly as it came", async () => {
  const unknownSite = {
    type: "https://shio.viglet.com/errors/unknown-site",
    title: "No readable site 'Ghost'",
    fix: "Address a site that exists, or add a `site.upsert` op for it earlier in the same batch.",
  };
  const retold = await apply.run({}, ["site.json"], ctxWith(scriptedFetch(unknownSite, { status: 404 })))
    .then(() => null, (error) => error);
  assert.match(JSON.parse(retold.body).fix, /--create-site/);
  assert.equal(JSON.parse(retold.body).example, "shio apply --site Blog --create-site");

  // The narrowness is the point: `--create-site` offered at a post-type problem is advice that
  // cannot work, which is the defect this task is about, pointed the other way.
  const elsewhere = { type: "https://shio.viglet.com/errors/unknown-post-type", title: "No post-type 'Hero'", fix: "Create it first." };
  const untouched = await apply.run({}, ["site.json"], ctxWith(scriptedFetch(elsewhere, { status: 404 })))
    .then(() => null, (error) => error);
  assert.deepEqual(JSON.parse(untouched.body), elsewhere);
});
