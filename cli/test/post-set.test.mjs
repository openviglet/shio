/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1091 — `shio post set` merges through the CDA and sends the tag it read as If-Match.
 *
 * The live half is `cli-surface.conformance.mjs`, against an instance; this is the fast half, over a
 * fake fetch, for what the command itself decides: which token it sends where, that the tag it read
 * is the precondition it sends, what a 412 says, and that a field the type does not declare is
 * refused before anything is written.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { parseAssignments, run } from "../src/commands/post.mjs";

function fakeInstance({ patchStatus = 200, fields = ["title", "summary", "featured"] } = {}) {
  const calls = [];
  const fetch = async (url, init = {}) => {
    const path = new URL(url).pathname + new URL(url).search;
    calls.push({ path, method: init.method ?? "GET", headers: init.headers ?? {}, body: init.body });
    const json = (status, body, headers = {}) => new Response(JSON.stringify(body),
      { status, headers: { "Content-Type": "application/json", ...headers } });
    if (path.startsWith("/api/v2/agent/resolve")) {
      return json(200, { kind: "post", id: "p1", address: "post:Blog/hello" });
    }
    if (path === "/api/v2/cda/post/p1/editable") {
      return json(200, { fields: fields.map((name) => ({ name })) }, { ETag: '"read-tag"' });
    }
    if (path === "/api/v2/cda/post/p1" && init.method === "PATCH") {
      return patchStatus === 409
        ? json(409, { type: "https://shio.viglet.com/errors/stale-expectation", title: "stale",
          detail: "post:Blog/hello moved since read-tag" })
        : json(200, { id: "p1" }, { ETag: '"new-tag"' });
    }
    return json(404, {});
  };
  return { fetch, calls };
}

function context(fetch, env = { SHIO_WRITE_TOKEN: "write-token" }) {
  let out = "";
  return { ctx: { env, fetch, stdout: { write: (text) => { out += text; } } }, output: () => out };
}

test("SH1091: the tag read from /editable is the If-Match the merge carries, with the WRITE token", async () => {
  const { fetch, calls } = fakeInstance();
  const { ctx, output } = context(fetch);

  await run({}, ["set", "post:Blog/hello", "title=Hello again", "featured:=true"], ctx);

  const patch = calls.find((call) => call.method === "PATCH");
  assert.equal(patch.headers["If-Match"], '"read-tag"',
    "SH1091: the merge was sent without the tag it read, so a raced write would be applied");
  assert.equal(patch.headers.Key, "write-token", "SH1091: the CDA merge must carry the WRITE token");
  assert.deepEqual(JSON.parse(patch.body).attrs, { title: "Hello again", featured: true });
  assert.match(output(), /etag="new-tag"/, "SH1091: the new tag is not printed, so the next change cannot pin it");
});

test("SH1091: --if-match pins the check to a tag read earlier", async () => {
  const { fetch, calls } = fakeInstance();
  const { ctx } = context(fetch);
  await run({ ifMatch: '"older-tag"' }, ["set", "post:Blog/hello", "title=x"], ctx);
  assert.equal(calls.find((call) => call.method === "PATCH").headers["If-Match"], '"older-tag"');
});

test("SH1091: a stale-expectation 409 is an error that says nothing was written, not a success", async () => {
  const { fetch } = fakeInstance({ patchStatus: 409 });
  const { ctx } = context(fetch);
  await assert.rejects(() => run({}, ["set", "post:Blog/hello", "title=x"], ctx),
    /changed since it was read, so nothing was written[\s\S]*moved since read-tag/u);
});

test("SH1091: a field the post's type does not declare is refused before anything is written", async () => {
  const { fetch, calls } = fakeInstance();
  const { ctx } = context(fetch);
  await assert.rejects(() => run({}, ["set", "post:Blog/hello", "titel=x"], ctx), /titel is not a field[\s\S]*allowed: featured, summary, title/u);
  assert.equal(calls.filter((call) => call.method === "PATCH").length, 0);
});

test("SH1091: without a WRITE token it says which token and why an AGENT one will not do", async () => {
  const { fetch } = fakeInstance();
  const { ctx } = context(fetch, {});
  await assert.rejects(() => run({}, ["set", "post:Blog/hello", "title=x"], ctx), /WRITE-scope API token[\s\S]*AGENT token/u);
});

test("SH1091: field=value is a string and field:=json a value", () => {
  assert.deepEqual(parseAssignments(["title=a=b", "order:=3", "tags:=[\"x\"]"]),
    { title: "a=b", order: 3, tags: ["x"] });
  assert.throws(() => parseAssignments(["nothing"]), /not field=value/u);
  assert.throws(() => parseAssignments(["n:=not json"]), /not JSON/u);
});

test("SH1091: the flags reach the command as the parser spells them, from a real argv", async () => {
  // The first live run refused nothing: the command read flags["if-match"] and the parser hands it
  // over as ifMatch, and this file's own tests passed the wrong spelling too. So this goes through
  // parseArgs with the argv a person types.
  const { parseArgs } = await import("../src/args.mjs");
  const { flags, positionals } = parseArgs(
    ["set", "post:Blog/hello", "--publish", "title=x", "--if-match", '"older-tag"'], { booleans: ["publish"] });
  const { fetch, calls } = fakeInstance();
  const { ctx } = context(fetch);
  await run(flags, positionals, ctx);
  const patch = calls.find((call) => call.method === "PATCH");
  assert.equal(patch.headers["If-Match"], '"older-tag"', "SH1091: --if-match did not reach the request");
  assert.deepEqual(JSON.parse(patch.body), { attrs: { title: "x" }, publish: true },
    "SH1091: --publish swallowed the field after it, or was not read as a boolean");
});
