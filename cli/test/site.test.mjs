/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH591 — a site that was never meant to be kept.
 *
 * <p>Twelve replication rounds left thirteen sites on one instance, every one made to answer a
 * question that has since been answered. Removing them meant thirteen `site.delete` ops, each
 * answering 428 and each needing its own confirm token.</p>
 *
 * <p>The assertions that matter are the <b>refusals</b>, not the sweep. A bulk delete is the most
 * dangerous verb this CLI has, and the two things keeping it safe are that a site without the flag
 * is untouched at any count, and that the flag can only be set by the run that created the site.
 * Both are asserted directly, because both are the kind of guarantee that is true until somebody
 * adds a convenience.</p>
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import * as site from "../src/commands/site.mjs";
import * as apply from "../src/commands/apply.mjs";
import { buildConversion } from "../src/convert.mjs";

function sink() {
  const out = { text: "" };
  out.write = (s) => {
    out.text += s;
  };
  return out;
}

/** An instance holding two kept sites and three an agent left behind. */
const SITES = [
  { name: "Acme", url: "https://acme.test", posts: 42 },
  { name: "R11", url: "", posts: 25, scratch: true },
  { name: "R12", url: "", posts: 25, scratch: true },
  { name: "Blog", url: "", posts: 9 },
  { name: "R10Ssr", url: "", posts: 3, scratch: true },
];

/** A scripted instance: the context pack, then whatever the batch calls receive. */
function instance({ sites = SITES } = {}) {
  const calls = [];
  const fetch = async (url, init) => {
    const at = String(url);
    if (at.includes("/agent/context")) {
      return json({ sites });
    }
    const body = init?.body ? JSON.parse(init.body) : {};
    calls.push({ url: at, body });
    // The plan answers with the digest of what it would destroy; the run answers with the result.
    return json(at.includes("dryRun=true") || body.dryRun
      ? { dryRun: true, ops: body.ops.length, confirm: "d1g3st" }
      : { dryRun: false, ops: body.ops.length, changed: body.ops.length });
  };
  return { calls, ctx: { stdout: sink(), stderr: sink(), env: {}, fetch } };
}

test("SH591: the sweep deletes every scratch site and no other, in one confirmation", async () => {
  const { calls, ctx } = instance();

  const result = await site.run({ scratch: true, url: "http://shio.test" }, ["rm"], ctx);

  assert.deepEqual(result.deleted, ["R10Ssr", "R11", "R12"]);
  // Two calls: the plan, then the run carrying its digest. That is SH108 unchanged — the saving
  // was never in the protocol, it was that nothing could compose the document.
  assert.equal(calls.length, 2, "one plan and one apply, not one pair per site");
  assert.equal(calls[1].body.confirm, "d1g3st");
  for (const call of calls) {
    assert.deepEqual(
      call.body.ops.map((op) => op.address),
      ["site:R10Ssr", "site:R11", "site:R12"],
      "Acme and Blog are not in the document at all",
    );
    assert.ok(call.body.ops.every((op) => op.op === "site.delete"));
  }
  assert.match(ctx.stdout.text, /deleted 3 scratch site\(s\)/);
  assert.match(ctx.stdout.text, /purged and not trashed/);
});

test("SH591: rm without --scratch refuses, and there is no --all to reach for", async () => {
  // The guarantee, stated as a refusal: the only set this will ever act on is the one its members'
  // creators declared disposable. A widenable filter would make this a fast way to delete a site
  // somebody chose to keep, which is the exact opposite of the task.
  const { calls, ctx } = instance();

  await assert.rejects(
    () => site.run({ url: "http://shio.test" }, ["rm"], ctx),
    /--scratch is required/,
  );
  assert.deepEqual(calls, [], "nothing was planned, let alone deleted");
});

test("SH591: an instance with nothing marked says so and sends no document", async () => {
  const { calls, ctx } = instance({ sites: [{ name: "Acme", posts: 42 }] });

  const result = await site.run({ scratch: true, url: "http://shio.test" }, ["rm"], ctx);

  assert.deepEqual(result.deleted, []);
  assert.deepEqual(calls, []);
  assert.match(ctx.stdout.text, /nothing to sweep/);
});

test("SH591: --dry-run names what would go and deletes nothing", async () => {
  const { calls, ctx } = instance();

  const result = await site.run(
    { scratch: true, dryRun: true, url: "http://shio.test" }, ["rm"], ctx);

  assert.deepEqual(result.planned, ["R10Ssr", "R11", "R12"]);
  assert.deepEqual(result.deleted, []);
  assert.equal(calls.length, 1, "the plan and nothing after it");
  assert.match(ctx.stdout.text, /would delete 3 scratch site/);
});

test("SH591: the listing marks which is which — a human reads this list", async () => {
  // The second half of the task, and worth as much as the first: `shio context` and the console
  // showed thirteen sites and no difference between the two a human curates and the eleven an
  // agent left behind.
  const { ctx } = instance();

  const result = await site.run({ url: "http://shio.test" }, [], ctx);

  assert.equal(result.scratch, 3);
  assert.match(ctx.stdout.text, /5 site\(s\), 3 declared disposable/);
  assert.match(ctx.stdout.text, /R11 {2}SCRATCH/);
  assert.doesNotMatch(ctx.stdout.text, /Acme {2}SCRATCH/);
});

test("SH591: the flag rides the create, and a document that creates nothing cannot carry it", () => {
  // The whole safety argument in one assertion. `scratch` is written only alongside `createSite`,
  // so every site the sweep can remove was declared disposable in a document a human could read
  // before there was anything in it to lose — P7 satisfied, not waived.
  const proposal = { pages: [], theme: null, scripts: [] };

  const made = buildConversion({ proposal, site: "R13", createSite: true, scratch: true });
  assert.equal(made.document.scratch, true);
  assert.equal(made.document.createSite, true);

  const notMade = buildConversion({ proposal, site: "Acme", createSite: false, scratch: true });
  assert.equal(notMade.document.scratch, undefined,
    "a run that creates no site may not declare somebody else's site disposable");
});

test("SH591: apply --scratch is refused on a document that creates no site", async () => {
  const document = { site: "Acme", folders: [{ path: "/" }], posts: [] };
  const seen = [];
  const ctx = {
    stdout: sink(),
    stderr: sink(),
    env: {},
    cwd: ".",
    fs: { async readFile() { return JSON.stringify(document); } },
    fetch: async (url, init) => {
      seen.push(init?.body ? JSON.parse(init.body) : {});
      return json({ dryRun: true, ops: 1, changed: 0, results: [] });
    },
  };

  await apply.run({ scratch: true, dryRun: true, url: "http://shio.test" }, ["doc.json"], ctx);

  assert.equal(seen[0].scratch, undefined,
    "--scratch without --create-site is a caller asking to mark somebody else's site disposable");
});

function json(body) {
  return {
    ok: true,
    status: 200,
    async json() {
      return body;
    },
    async text() {
      return JSON.stringify(body);
    },
  };
}
