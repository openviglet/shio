/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * Requires the @viglet/shio-client build (for ShioHttpError). The root `test`
 * script builds it first; standalone, run `pnpm --filter @viglet/shio-client build`.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { ShioHttpError } from "@viglet/shio-client";

import { ShioAdminClient } from "../src/admin-client.mjs";

const BASE = "https://cms.example.test";

function fakeFetch(scripted) {
  const calls = [];
  const fn = async (url, init) => {
    calls.push({ url, init });
    const { status = 200, body = {} } = typeof scripted === "function" ? scripted(url, init) : scripted;
    return new Response(status === 204 ? null : JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/json" },
    });
  };
  fn.calls = calls;
  return fn;
}

test("getPostTypeDefs sends a Basic auth header to the admin namespace", async () => {
  const fetchFn = fakeFetch({ body: [{ name: "News" }] });
  const client = new ShioAdminClient({ baseUrl: BASE, user: "admin", password: "secret", fetch: fetchFn });

  const defs = await client.getPostTypeDefs();

  assert.equal(fetchFn.calls[0].url, `${BASE}/api/v2/post-type`);
  assert.equal(fetchFn.calls[0].init.method, "GET");
  const expected = `Basic ${Buffer.from("admin:secret").toString("base64")}`;
  assert.equal(fetchFn.calls[0].init.headers.Authorization, expected);
  assert.deepEqual(defs, [{ name: "News" }]);
});

test("putPostTypeDef PUTs JSON to /{name}", async () => {
  const fetchFn = fakeFetch({ body: { name: "News" } });
  const client = new ShioAdminClient({ baseUrl: BASE, user: "u", password: "p", fetch: fetchFn });

  await client.putPostTypeDef("News", { name: "News", fields: [] });

  assert.equal(fetchFn.calls[0].url, `${BASE}/api/v2/post-type/News`);
  assert.equal(fetchFn.calls[0].init.method, "PUT");
  assert.equal(JSON.parse(fetchFn.calls[0].init.body).name, "News");
});

test("404 → null, 204 → null", async () => {
  const c404 = new ShioAdminClient({ baseUrl: BASE, fetch: fakeFetch({ status: 404 }) });
  assert.equal(await c404.getPostTypeDef("missing"), null);
  const c204 = new ShioAdminClient({ baseUrl: BASE, fetch: fakeFetch({ status: 204 }) });
  assert.equal(await c204.deletePostTypeDef("gone"), null);
});

test("non-2xx throws ShioHttpError carrying the status", async () => {
  const client = new ShioAdminClient({ baseUrl: BASE, fetch: fakeFetch({ status: 409, body: { error: "system" } }) });
  await assert.rejects(() => client.deletePostTypeDef("Folder"), (err) => {
    assert.ok(err instanceof ShioHttpError);
    assert.equal(err.status, 409);
    return true;
  });
});
