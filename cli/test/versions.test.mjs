/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH987 — `shio versions` and `shio rollback`, the two halves of an undo somebody can run.
 *
 * What is asserted here is what only these readers can get wrong. `versions` has to **ask** for the
 * history: an ordinary read carries none, by design, so a command that forgot the include would
 * print "none captured" about a page with fifty revisions and read like good news. And `rollback`
 * has to plan by default: it is the one write whose subject the caller cannot see, a number naming
 * bytes that are not on screen, so writing on the first invocation is the defect.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import * as rollback from "../src/commands/rollback.mjs";
import * as versions from "../src/commands/versions.mjs";

const REVISIONS = [
  {
    version: 2, state: "PUBLISHED", reason: "PUBLISH", actor: "ana",
    capturedAt: "2026-09-10T12:00:00Z", digest: "bbb", changed: ["TITLE", "body"],
  },
  {
    version: 1, state: "PUBLISHED", reason: "PUBLISH", actor: "agent-7",
    capturedAt: "2026-09-09T09:00:00Z", digest: "aaa", changed: ["TITLE"],
  },
];

function ctxWith(fetchImpl) {
  const out = [];
  const err = [];
  return {
    env: { SHIO_URL: "http://shio.test", SHIO_USER: "admin", SHIO_PASSWORD: "secret" },
    stdout: { write: (text) => out.push(text) },
    stderr: { write: (text) => err.push(text) },
    fetch: fetchImpl,
    text: () => out.join(""),
    errors: () => err.join(""),
  };
}

test("SH987: shio versions asks for the history, and prints one line per revision", async () => {
  const urls = [];
  const ctx = ctxWith(async (url) => {
    urls.push(String(url));
    return {
      ok: true,
      status: 200,
      json: async () => [{ address: "post:Blog/hello", revisions: REVISIONS }],
    };
  });

  const code = await versions.run({}, ["post:Blog/hello"], ctx);

  assert.equal(code, 0);
  assert.match(urls[0], /include=revisions/,
    "SH987: the history is opt-in, so a read that does not ask for it comes back without one — and "
      + "this command would then report a page with fifty revisions as having none");
  const printed = ctx.text();
  assert.match(printed, /shio versions post:Blog\/hello: 2/);
  assert.match(printed, /2\s+PUBLISH\s+2026-09-10T12:00:00Z\s+ana/);
  assert.match(printed, /changed: TITLE, body/,
    "SH987: the field names are what makes a list worth reading — they say which revision to fetch");
});

test("SH987: a page with no captured revision says so, and exits non-zero", async () => {
  const ctx = ctxWith(async () => ({
    ok: true, status: 200, json: async () => [{ address: "post:Blog/new", revisions: [] }],
  }));

  const code = await versions.run({}, ["post:Blog/new"], ctx);

  assert.equal(code, 1,
    "SH987: a script that reads this and finds nothing should stop rather than guess a number");
  assert.match(ctx.text(), /none captured/);
  assert.match(ctx.text(), /has never been published/,
    "SH987: and it says why there are none, which is the one thing the caller cannot see");
});

test("SH987: shio versions without an address says what it needs", async () => {
  const ctx = ctxWith(async () => {
    throw new Error("SH987: it must not call the instance without an address");
  });

  assert.equal(await versions.run({}, [], ctx), 2);
  assert.match(ctx.errors(), /needs an address/);
});

test("SH987: shio rollback plans by default and writes only when told", async () => {
  const sent = [];
  const ctx = ctxWith(async (url, init) => {
    sent.push(JSON.parse(String(init?.body ?? "{}")));
    return {
      ok: true,
      status: 200,
      json: async () => ({
        dryRun: sent.at(-1).dryRun,
        results: [{ op: "post.rollback", action: "update", detail: "restore revision 1 into the draft" }],
      }),
    };
  });

  await rollback.run({ to: "1" }, ["post:Blog/hello"], ctx);

  assert.equal(sent[0].dryRun, true,
    "SH987: a rollback names bytes the caller cannot see, so the first invocation plans");
  assert.deepEqual(sent[0].ops, [{ op: "post.rollback", address: "post:Blog/hello", revision: 1 }]);
  assert.match(ctx.text(), /would: restore revision 1/);
  assert.match(ctx.text(), /--apply/,
    "SH987: and it names the second word, so the plan is not a dead end");

  await rollback.run({ to: "1", apply: true }, ["post:Blog/hello"], ctx);
  assert.equal(sent[1].dryRun, false);
  assert.match(ctx.text(), /It landed in the draft/,
    "SH987: it restores into the draft, and says so — publishing is the separate act (P7)");
});

test("SH987: shio rollback without a revision says where the numbers come from", async () => {
  const ctx = ctxWith(async () => {
    throw new Error("SH987: it must not call the instance without a revision");
  });

  assert.equal(await rollback.run({}, ["post:Blog/hello"], ctx), 2);
  assert.match(ctx.errors(), /shio versions/,
    "SH987: the refusal names the command that answers it, which is what P6 asks of a 4xx and this "
      + "is the same refusal one layer out");
});
