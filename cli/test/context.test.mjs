/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH126 — `shio context`. Network-free: a scripted fetch records the request the
 * command builds and returns a canned pack.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import * as context from "../src/commands/context.mjs";

const TERSE_PACK = `shio-context 1 manifest=/api/v2/agent/manifest
instance "Viglet Shio" dev locale=en
site Blog id=s1 root=f1 posts=3
type Article managed posts=3 fields="title:Text!,body:HTML Editor"
node folder / id=f1 folders=1
node post /blog id=p1 type=Article state=DRAFT url=/blog/hello title="Hello"
convention "Writes land in DRAFT."
digest c:abc123def456
budget est=142 limit=none
`;

function sink() {
  const out = { text: "" };
  out.write = (s) => {
    out.text += s;
  };
  return out;
}

const norm = (p) => String(p).replace(/\\/g, "/");

function memFs() {
  const files = new Map();
  return {
    get: (p) => files.get(norm(p)),
    async mkdir() {},
    async writeFile(path, data) {
      files.set(norm(path), data);
    },
  };
}

/** Records every request; replies with `body` and an est-tokens header. */
function scriptedFetch(body = TERSE_PACK, { status = 200, estTokens = "142" } = {}) {
  const calls = [];
  const fetchImpl = async (url, init) => {
    calls.push({ url: String(url), init });
    return {
      ok: status >= 200 && status < 300,
      status,
      headers: { get: (name) => (name.toLowerCase() === "x-shio-est-tokens" ? estTokens : null) },
      async text() {
        return body;
      },
      async json() {
        return JSON.parse(body);
      },
    };
  };
  fetchImpl.calls = calls;
  return fetchImpl;
}

function ctxWith(fetchImpl, overrides = {}) {
  return {
    env: { SHIO_URL: "http://shio.test", SHIO_USER: "admin", SHIO_PASSWORD: "secret" },
    cwd: "/repo",
    stdout: sink(),
    stderr: sink(),
    fs: memFs(),
    fetch: fetchImpl,
    ...overrides,
  };
}

test("context defaults to terse and prints the pack unchanged", async () => {
  const fetchImpl = scriptedFetch();
  const ctx = ctxWith(fetchImpl);

  const result = await context.run({}, [], ctx);

  assert.equal(result.format, "terse");
  assert.match(fetchImpl.calls[0].url, /\/api\/v2\/agent\/context\?format=terse$/);
  assert.equal(fetchImpl.calls[0].init.headers.Accept, "text/plain");
  // Byte-for-byte: the pack's digest is computed over exactly these bytes, so the
  // CLI must not reformat them.
  assert.equal(ctx.stdout.text, TERSE_PACK);
});

test("context sends console Basic auth", async () => {
  const fetchImpl = scriptedFetch();

  await context.run({}, [], ctxWith(fetchImpl));

  const expected = `Basic ${Buffer.from("admin:secret", "utf8").toString("base64")}`;
  assert.equal(fetchImpl.calls[0].init.headers.Authorization, expected);
});

test("context forwards every scoping flag and drops the empty ones", async () => {
  const fetchImpl = scriptedFetch();

  await context.run(
    { site: "Blog", depth: 2, budget: 3000, include: "model,sitemap", from: "p1", format: "json" },
    [],
    ctxWith(fetchImpl),
  );

  const url = new URL(fetchImpl.calls[0].url);
  assert.equal(url.searchParams.get("site"), "Blog");
  assert.equal(url.searchParams.get("depth"), "2");
  assert.equal(url.searchParams.get("budget"), "3000");
  assert.equal(url.searchParams.get("include"), "model,sitemap");
  assert.equal(url.searchParams.get("from"), "p1");
  assert.equal(url.searchParams.get("format"), "json");
});

test("context omits absent flags rather than sending blanks", async () => {
  const fetchImpl = scriptedFetch();

  await context.run({ site: "" }, [], ctxWith(fetchImpl));

  const url = new URL(fetchImpl.calls[0].url);
  assert.equal(url.searchParams.has("site"), false);
  assert.equal(url.searchParams.has("depth"), false);
});

test("context --out writes a committable artifact instead of printing", async () => {
  const fetchImpl = scriptedFetch();
  const ctx = ctxWith(fetchImpl);

  await context.run({ out: "shio/context.txt" }, [], ctx);

  assert.equal(ctx.fs.get("/repo/shio/context.txt"), TERSE_PACK);
  assert.match(ctx.stdout.text, /wrote shio\/context\.txt \(~142 tokens\)/);
  assert.doesNotMatch(ctx.stdout.text, /shio-context 1/);
});

test("context rejects an unknown --format with the allowed set", async () => {
  // Same contract as the server's teaching errors: say what is allowed, in the
  // message, so a typo costs zero round trips.
  await assert.rejects(
    () => context.run({ format: "yaml" }, [], ctxWith(scriptedFetch())),
    /unknown --format "yaml"\. Allowed: terse, json/,
  );
});

test("context accepts json and asks for JSON", async () => {
  const fetchImpl = scriptedFetch('{"protocol":1}');

  const result = await context.run({ format: "JSON" }, [], ctxWith(fetchImpl));

  assert.equal(result.format, "json");
  assert.equal(fetchImpl.calls[0].init.headers.Accept, "application/json");
});

test("context surfaces an HTTP failure instead of writing a broken artifact", async () => {
  const fetchImpl = scriptedFetch("nope", { status: 403 });
  const ctx = ctxWith(fetchImpl);

  await assert.rejects(() => context.run({ out: "shio/context.txt" }, [], ctx));
  assert.equal(ctx.fs.get("/repo/shio/context.txt"), undefined);
});
