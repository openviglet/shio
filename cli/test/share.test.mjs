/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1111 — `shio share`: the link an agent hands a person, and the reading and stopping beside it.
 * Network-free: a routed fake fetch answers the three routes and records what was asked.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import * as share from "../src/commands/share.mjs";
import { parseArgs } from "../src/args.mjs";

function sink() {
  const chunks = [];
  return { write: (text) => chunks.push(text), text: () => chunks.join("") };
}

function ctxWith(routes) {
  const asked = [];
  return {
    asked,
    env: { SHIO_URL: "http://shio.test/", SHIO_USER: "admin", SHIO_PASSWORD: "secret" },
    cwd: "/repo",
    stdout: sink(),
    stderr: sink(),
    fetch: async (url, init = {}) => {
      const method = init.method ?? "GET";
      asked.push({ method, url: String(url), body: init.body });
      const route = routes.find(([m, pattern]) => m === method && pattern.test(String(url)));
      const [, , status, body] = route ?? [null, null, 404, null];
      return {
        ok: status < 400, status, headers: { get: () => null },
        async json() { return body; }, async text() { return JSON.stringify(body); },
      };
    },
  };
}

const run = (line, ctx) => {
  const { flags, positionals } = parseArgs(line.split(" ").filter(Boolean), {});
  return share.run(flags, positionals, ctx);
};

test("SH1111: share prints an absolute link the person can open, from the console's mint", async () => {
  const ctx = ctxWith([
    ["GET", /\/api\/v2\/agent\/resolve\?address=post%3AAcme%2Fpricing$/, 200, { id: "p1" }],
    ["POST", /\/api\/v2\/preview-token$/, 200, {
      token: "t", previewUrl: "/api/v2/preview-share/t?to=%2FAcme%2Fpricing", expiresAt: "2026-09-26T18:00:00Z",
    }],
  ]);

  await run("post:Acme/pricing", ctx);

  assert.equal(ctx.stdout.text(), "http://shio.test/api/v2/preview-share/t?to=%2FAcme%2Fpricing\n",
    "SH1111: the mint answers a relative link, and the caller that reached the instance knows its origin");
  assert.deepEqual(JSON.parse(ctx.asked.find((call) => call.method === "POST").body), { postId: "p1" },
    "SH1111: the same mint the console calls, by the post's id");
});

test("SH1111: --list prints the live links by id, and --revoke stops one", async () => {
  const ctx = ctxWith([
    ["GET", /\/api\/v2\/preview-token$/, 200, [
      { id: "r1", site: "Acme", page: "/pricing", expiresAt: "2026-09-26T18:00:00Z", owner: "ana" },
    ]],
    ["DELETE", /\/api\/v2\/preview-token\/r1$/, 200,
      { id: "r1", site: "Acme", page: "/pricing", expiresAt: "2026-09-26T18:00:00Z", owner: "ana" }],
  ]);

  await run("--list", ctx);
  assert.match(ctx.stdout.text(), /^r1 {2}Acme\/pricing {2}expires 2026-09-26T18:00:00Z {2}by ana\n/);

  await run("--revoke r1", ctx);
  assert.match(ctx.stdout.text(), /Revoked r1 \(Acme\/pricing\)/);
  assert.ok(ctx.asked.some((call) => call.method === "DELETE"), "SH1111: revoking is the DELETE");
});

test("SH1111: revoking a link that is not live says how to find the ones that are", async () => {
  const ctx = ctxWith([]);
  await assert.rejects(() => run("--revoke nope", ctx), /shio share --list/);
});
