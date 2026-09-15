/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH991 — `shio schedule`, the agenda a curator did not have.
 *
 * What only this reader can get wrong: it has to ask for scheduled posts rather than list every post,
 * it has to put entries in time order where the listing puts them in address order (its cursor), and
 * a post due both to go live and to come down is two moments, not one row.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import * as schedule from "../src/commands/schedule.mjs";

function ctxWith(fetchImpl) {
  const out = [];
  return {
    env: { SHIO_URL: "http://shio.test", SHIO_USER: "admin", SHIO_PASSWORD: "secret" },
    stdout: { write: (text) => out.push(text) },
    stderr: { write: () => {} },
    fetch: fetchImpl,
    text: () => out.join(""),
  };
}

const ROWS = [
  { address: "post:Blog/a", title: "Launch", publishAt: "2031-05-03T09:00:00Z" },
  {
    address: "post:Blog/b", title: "Promo",
    publishAt: "2031-05-01T09:00:00Z", unpublishAt: "2031-05-10T00:00:00Z",
  },
];

function reply(body) {
  return { ok: true, status: 200, json: async () => body };
}

test("SH991: shio schedule asks for scheduled posts and prints them in time order, in UTC", async () => {
  const urls = [];
  const ctx = ctxWith(async (url) => {
    urls.push(String(url));
    return reply({ count: 2, truncated: false, results: ROWS });
  });

  const code = await schedule.run({}, ["Blog"], ctx);

  assert.equal(code, 0);
  const url = new URL(urls[0]);
  assert.equal(url.searchParams.get("scheduled"), "any",
    "SH991: without the filter the listing is every post, and none of its rows carries an instant");
  assert.equal(url.searchParams.get("site"), "Blog");
  const lines = ctx.text().trim().split("\n");
  assert.match(lines[0], /3 \(times are UTC\)/);
  assert.deepEqual(lines.slice(1).map((line) => line.trim().split(/\s+/).slice(0, 3)), [
    ["2031-05-01T09:00:00Z", "publish", "post:Blog/b"],
    ["2031-05-03T09:00:00Z", "publish", "post:Blog/a"],
    ["2031-05-10T00:00:00Z", "unpublish", "post:Blog/b"],
  ], "SH991: time order, and a post due to go live and come down is two entries");
});

test("SH991: --from and --to become the server's window, and bound the entries shown", async () => {
  const urls = [];
  const ctx = ctxWith(async (url) => {
    urls.push(String(url));
    return reply({ count: 1, truncated: false, results: [ROWS[1]] });
  });

  await schedule.run({ from: "2031-05-01T00:00:00Z", to: "2031-05-02T00:00:00Z", json: true }, [], ctx);

  assert.equal(new URL(urls[0]).searchParams.get("scheduled"),
    "2031-05-01T00:00:00Z..2031-05-02T00:00:00Z");
  assert.deepEqual(JSON.parse(ctx.text()).map((entry) => entry.action), ["publish"],
    "SH991: the unpublish on the 10th is outside the window even though its post is inside it");
});

test("SH991: an agenda that stopped short exits non-zero and says so", async () => {
  const ctx = ctxWith(async () => reply({ count: 1, truncated: true, results: [ROWS[0]] }));

  const code = await schedule.run({}, [], ctx);

  assert.equal(code, 1);
  assert.match(ctx.text(), /may be missing entries/);
});
