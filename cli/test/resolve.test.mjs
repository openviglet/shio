/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH79 — `shio resolve`. Network-free: a scripted fetch records the request.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import * as resolve from "../src/commands/resolve.mjs";

const RESOLVED = {
  kind: "post",
  id: "9f3c-abcd",
  address: "post:Blog/blog/hello-world",
  title: "Hello World",
  parent: "f1",
  type: "Article",
  state: "DRAFT",
};

function sink() {
  const out = { text: "" };
  out.write = (s) => {
    out.text += s;
  };
  return out;
}

function scriptedFetch(body = RESOLVED, { status = 200 } = {}) {
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

function ctxWith(fetchImpl) {
  return {
    env: { SHIO_URL: "http://shio.test", SHIO_USER: "admin", SHIO_PASSWORD: "secret" },
    cwd: "/repo",
    stdout: sink(),
    stderr: sink(),
    fetch: fetchImpl,
  };
}

test("resolve takes the address positionally and prints one terse line", async () => {
  const fetchImpl = scriptedFetch();
  const ctx = ctxWith(fetchImpl);

  await resolve.run({}, ["post:Blog/blog/hello-world"], ctx);

  assert.equal(
    ctx.stdout.text,
    "post 9f3c-abcd post:Blog/blog/hello-world type=Article state=DRAFT\n",
  );
});

test("resolve url-encodes the address", async () => {
  // Site names carry spaces in the wild (SH72 provisions "Blog (acme)").
  const fetchImpl = scriptedFetch();

  await resolve.run({}, ["post:Blog (acme)/blog/hello"], ctxWith(fetchImpl));

  // Note: encodeURIComponent leaves ( ) alone — they are legal in a query value.
  assert.match(fetchImpl.calls[0].url, /address=post%3ABlog%20\(acme\)%2Fblog%2Fhello$/);
});

test("resolve --id prints only the id so it composes in a pipeline", async () => {
  const ctx = ctxWith(scriptedFetch());

  await resolve.run({ id: true }, ["post:Blog/blog/hello-world"], ctx);

  assert.equal(ctx.stdout.text, "9f3c-abcd\n");
});

test("resolve omits fields the object does not have", async () => {
  const ctx = ctxWith(scriptedFetch({ kind: "folder", id: "f1", address: "folder:Blog/blog" }));

  await resolve.run({}, ["folder:Blog/blog"], ctx);

  assert.equal(ctx.stdout.text, "folder f1 folder:Blog/blog\n");
});

test("resolve without an address says how to call it", async () => {
  await assert.rejects(
    () => resolve.run({}, [], ctxWith(scriptedFetch())),
    /an address is required.*shio resolve post:Blog\/blog\/hello-world/s,
  );
});

test("resolve passes the server's teaching problem through", async () => {
  // The 404 body names the sibling paths and the nearest match; swallowing it would
  // turn a one-shot fix into a guessing game.
  const problem = JSON.stringify({
    title: "Nothing at 'folder:Blog/blg'",
    didYouMean: "/blog",
    allowed: ["/blog"],
  });
  const fetchImpl = scriptedFetch(problem, { status: 404 });

  await assert.rejects(
    () => resolve.run({}, ["folder:Blog/blg"], ctxWith(fetchImpl)),
    (error) => {
      // ShioHttpError keeps the summary in `message` and the payload in `body`; the
      // structured problem must survive on `body` or the fix it names is lost.
      assert.equal(error.status, 404);
      assert.match(error.body, /didYouMean/);
      return true;
    },
  );
});
