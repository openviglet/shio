/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1001 — the review queue from a terminal.
 *
 * The properties that matter are the ones a pipeline depends on: a run still waiting is a non-zero
 * exit, a rejection without a reason is refused before it costs a round trip, and a dry run prints
 * what approving would do without writing. The rest is rendering.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import * as review from "../src/commands/review.mjs";

const SESSION = "7f3c9a2e";

const DETAIL = {
  session: SESSION,
  actor: "ana",
  entries: [
    {
      action: "UPDATE", postId: "p-about", address: "post:Blog/about", title: "About",
      draft: true, published: false, trashed: false, revertable: true,
    },
    {
      action: "PUBLISH", postId: "p-news", address: "post:Blog/news", title: "News",
      draft: false, published: true, trashed: false, revertable: true,
    },
  ],
  decisions: [{ postId: "p-news", decision: "HOLD", note: "waiting on legal", actor: "ana" }],
  appearance: { moved: ["post:Blog/about"], checked: 2 },
};

function ctxWith(handler) {
  const calls = [];
  const out = { text: "" };
  return {
    env: { SHIO_URL: "http://shio.test", SHIO_USER: "admin", SHIO_PASSWORD: "secret" },
    cwd: "/repo",
    calls,
    stdout: { write: (text) => { out.text += text; } },
    stderr: { write: () => {} },
    text: () => out.text,
    fetch: async (url, init = {}) => {
      calls.push({ url: String(url), method: init.method ?? "GET", body: init.body });
      const payload = handler(String(url), init) ?? {};
      return {
        ok: true,
        status: 200,
        headers: { get: () => null },
        async text() { return JSON.stringify(payload); },
        async json() { return payload; },
      };
    },
  };
}

test("SH1001: list exits non-zero while anything is waiting, which is the CI gate", async () => {
  const ctx = ctxWith(() => [
    { session: SESSION, intent: "replace the launch banner", agentName: "claude-code",
      actor: "ana", changes: 2, pending: 1, byAction: { UPDATE: 2 }, sites: [] },
  ]);

  const result = await review.run({ strict: true }, ["review", "list"], ctx);

  assert.equal(result.exitCode, 1,
    "SH1001: 'fail the build if anything is still waiting' is one exit code, and until now the "
      + "only way to ask was to open a browser");
  assert.match(ctx.text(), /replace the launch banner/,
    "SH1000: the premise leads, because it decides whether the counts underneath are the right ones");
  assert.match(ctx.calls[0].url, /\/api\/v2\/review\/sessions/,
    "SH1001: not under /agent — this is the surface that reviews an agent");
});

test("SH1001: a rejection with no reason is refused before it costs a round trip", async () => {
  const ctx = ctxWith(() => DETAIL);

  await assert.rejects(
    () => review.run({ reject: "post:Blog/about" }, ["review", "decide", SESSION], ctx),
    /--reject needs --note/,
  );
  assert.equal(ctx.calls.length, 0,
    "SH1001: the server refuses this too, and a CI job that learns the rule from a 400 has already "
      + "spent a round trip on a run it is gating");
});

test("SH1001: a dry run says what approving would publish and what it would skip", async () => {
  const ctx = ctxWith(() => DETAIL);

  await review.run(
    { approve: "post:Blog/about,post:Blog/news", dryRun: true },
    ["review", "decide", SESSION],
    ctx,
  );

  assert.match(ctx.text(), /p-about — would publish/);
  assert.match(ctx.text(), /p-news — would skip — no draft to publish/,
    "SH1001: read off the `draft` flag the entry already carries — the facts are the server's");
  assert.ok(
    ctx.calls.every((call) => call.method === "GET"),
    "SH1001: a dry run writes nothing, which is the whole of what makes it one",
  );
});

test("SH1001: decide sends one call for every change, by address or by id", async () => {
  const ctx = ctxWith((url) =>
    url.endsWith("/decisions")
      ? { session: SESSION, action: "decide", changed: 2, posts: ["p-about", "p-news"], skipped: [] }
      : DETAIL);

  await review.run(
    { approve: "post:Blog/about", hold: "p-news" },
    ["review", "decide", SESSION],
    ctx,
  );

  const post = ctx.calls.find((call) => call.method === "POST");
  assert.deepEqual(JSON.parse(post.body), [
    { postId: "p-about", decision: "APPROVE" },
    { postId: "p-news", decision: "HOLD" },
  ], "SH1001: one call and one transaction — sent one at a time, a refusal halfway leaves the run "
    + "in a state nobody chose");
});

test("SH1001: a change this run never touched is named, not sent", async () => {
  const ctx = ctxWith(() => DETAIL);

  await assert.rejects(
    () => review.run({ hold: "post:Blog/nothing" }, ["review", "decide", SESSION], ctx),
    /this run changed nothing about post:Blog\/nothing/,
  );
});

test("SH1001: show prints the standing decision beside each change", async () => {
  const ctx = ctxWith(() => DETAIL);

  await review.run({}, ["review", "show", SESSION], ctx);

  assert.match(ctx.text(), /PUBLISH post:Blog\/news — HOLD: waiting on legal/,
    "SH1001: a decision whose only record is its side effect cannot carry a reason, and HOLD has "
      + "no side effect at all");
  assert.match(ctx.text(), /looks different \(2 compared\): post:Blog\/about/,
    "SH1000: which the action column above cannot say");
});
