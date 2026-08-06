/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * Runs against the built bundle with an injected fake `fetch` — no network.
 *   npm run build && npm test
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { ShioClient, ShioHttpError, createShioClient } from "../dist/shio-client.js";

const BASE = "https://cms.example.test";

/** Build a fake fetch that records calls and returns a scripted Response. */
function fakeFetch(scripted) {
  const calls = [];
  const fn = async (url, init) => {
    calls.push({ url, init });
    const { status = 200, body = {} } =
      typeof scripted === "function" ? scripted(url, init) : scripted;
    return new Response(status === 204 ? null : JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/json" },
    });
  };
  fn.calls = calls;
  return fn;
}

test("getSites builds GET /api/v2/cda/site and sends the Key header", async () => {
  const fetchFn = fakeFetch({ body: [{ id: "s1", name: "Site 1" }] });
  const client = new ShioClient({ baseUrl: BASE, apiToken: "tok-123", fetch: fetchFn });

  const sites = await client.getSites();

  assert.equal(fetchFn.calls.length, 1);
  assert.equal(fetchFn.calls[0].url, `${BASE}/api/v2/cda/site`);
  assert.equal(fetchFn.calls[0].init.method, "GET");
  assert.equal(fetchFn.calls[0].init.headers["Key"], "tok-123");
  assert.deepEqual(sites, [{ id: "s1", name: "Site 1" }]);
});

test("trailing slashes on baseUrl are normalized", async () => {
  const fetchFn = fakeFetch({ body: [] });
  const client = new ShioClient({ baseUrl: `${BASE}///`, fetch: fetchFn });
  await client.getSites();
  assert.equal(fetchFn.calls[0].url, `${BASE}/api/v2/cda/site`);
});

test("getPost returns the parsed post with attrs", async () => {
  const post = { id: "p1", title: "Hello", attrs: { title: "Hello", n: 3 } };
  const client = new ShioClient({ baseUrl: BASE, fetch: fakeFetch({ body: post }) });
  const result = await client.getPost("p1");
  assert.equal(result.id, "p1");
  assert.deepEqual(result.attrs, { title: "Hello", n: 3 });
});

test("single-resource getters translate 404 to null", async () => {
  const client = new ShioClient({ baseUrl: BASE, fetch: fakeFetch({ status: 404 }) });
  assert.equal(await client.getPost("missing"), null);
  assert.equal(await client.getSite("missing"), null);
  assert.equal(await client.getPath("missing"), null);
  assert.equal(await client.listChildren("missing"), null);
  assert.equal(await client.getPostByUrl("s1", "/missing"), null);
});

test("non-404 errors throw ShioHttpError with status", async () => {
  const client = new ShioClient({ baseUrl: BASE, fetch: fakeFetch({ status: 500, body: { error: "boom" } }) });
  await assert.rejects(
    () => client.getSites(),
    (err) => {
      assert.ok(err instanceof ShioHttpError);
      assert.equal(err.status, 500);
      return true;
    }
  );
});

test("getPostByUrl encodes siteId + url into the query string", async () => {
  const fetchFn = fakeFetch({ body: { id: "p1" } });
  const client = new ShioClient({ baseUrl: BASE, fetch: fetchFn });
  await client.getPostByUrl("site 1", "/blog/olá");
  const url = new URL(fetchFn.calls[0].url);
  assert.equal(url.pathname, "/api/v2/cda/post/by-url");
  assert.equal(url.searchParams.get("siteId"), "site 1");
  assert.equal(url.searchParams.get("url"), "/blog/olá");
});

test("query omits undefined/empty params", async () => {
  const fetchFn = fakeFetch({ body: { posts: [] } });
  const client = new ShioClient({ baseUrl: BASE, fetch: fetchFn });
  await client.query({ siteId: "s1", postType: "", page: 0, size: 20 });
  const url = new URL(fetchFn.calls[0].url);
  assert.equal(url.searchParams.get("siteId"), "s1");
  assert.equal(url.searchParams.has("postType"), false, "empty postType should be omitted");
  assert.equal(url.searchParams.get("size"), "20");
});

test("listChildren forwards paging params", async () => {
  const fetchFn = fakeFetch({ body: { folders: [], posts: [] } });
  const client = new ShioClient({ baseUrl: BASE, fetch: fetchFn });
  await client.listChildren("f1", { page: 2, size: 10 });
  const url = new URL(fetchFn.calls[0].url);
  assert.equal(url.pathname, "/api/v2/cda/object/f1/list");
  assert.equal(url.searchParams.get("page"), "2");
  assert.equal(url.searchParams.get("size"), "10");
});

test("request forwards the abort signal", async () => {
  const fetchFn = fakeFetch({ body: [] });
  const client = new ShioClient({ baseUrl: BASE, fetch: fetchFn });
  const controller = new AbortController();
  await client.getSites({ signal: controller.signal });
  assert.equal(fetchFn.calls[0].init.signal, controller.signal);
});

test("credentials are omitted unless configured", async () => {
  const f1 = fakeFetch({ body: [] });
  await new ShioClient({ baseUrl: BASE, fetch: f1 }).getSites();
  assert.equal("credentials" in f1.calls[0].init, false);

  const f2 = fakeFetch({ body: [] });
  await new ShioClient({ baseUrl: BASE, fetch: f2, credentials: "include" }).getSites();
  assert.equal(f2.calls[0].init.credentials, "include");
});

test("createShioClient factory works and requires baseUrl", async () => {
  const client = createShioClient({ baseUrl: BASE, fetch: fakeFetch({ body: [] }) });
  assert.ok(client instanceof ShioClient);
  assert.throws(() => new ShioClient({}), /baseUrl/);
});

test("getPostTypes builds GET /post-type (SH11)", async () => {
  const fetchFn = fakeFetch({ body: [{ name: "News", fields: [] }] });
  const client = new ShioClient({ baseUrl: BASE, fetch: fetchFn });
  const schemas = await client.getPostTypes();
  assert.equal(fetchFn.calls[0].url, `${BASE}/api/v2/cda/post-type`);
  assert.deepEqual(schemas, [{ name: "News", fields: [] }]);
});

test("getPostType encodes the name and translates 404 to null (SH11)", async () => {
  const found = fakeFetch({ body: { name: "News", fields: [] } });
  const client = new ShioClient({ baseUrl: BASE, fetch: found });
  await client.getPostType("News Article");
  assert.equal(new URL(found.calls[0].url).pathname, "/api/v2/cda/post-type/News%20Article");

  const missing = new ShioClient({ baseUrl: BASE, fetch: fakeFetch({ status: 404 }) });
  assert.equal(await missing.getPostType("Nope"), null);
});
