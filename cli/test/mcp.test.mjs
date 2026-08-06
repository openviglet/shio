/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH75 — `shio mcp`, the stdio bridge. Network-free: a scripted fetch records what
 * the bridge sent and returns canned responses.
 *
 * What is worth asserting about a bridge is not that it works on the happy path but
 * that it never desynchronises the client: one line out per request, nothing on
 * stdout for a notification, an id-carrying JSON-RPC error when the network fails,
 * and the session header echoed once minted.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import * as mcp from "../src/commands/mcp.mjs";

function sink() {
  const out = { text: "" };
  out.write = (s) => {
    out.text += s;
  };
  return out;
}

/** A readable-stream stand-in yielding the given lines. */
function stdinOf(...lines) {
  return {
    setEncoding() {},
    async *[Symbol.asyncIterator]() {
      yield lines.join("\n");
    },
  };
}

function ctxWith(stdin, fetchImpl) {
  return {
    env: { SHIO_URL: "http://shio.test", SHIO_USER: "u", SHIO_PASSWORD: "p" },
    cwd: "/tmp",
    stdin,
    stdout: sink(),
    stderr: sink(),
    fetch: fetchImpl,
  };
}

function response(body, { status = 200, headers = {} } = {}) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (name) => headers[name.toLowerCase()] ?? null },
    text: async () => body,
  };
}

test("forwards each stdin line to POST /mcp and writes one line back", async () => {
  const calls = [];
  const ctx = ctxWith(
    stdinOf('{"jsonrpc":"2.0","id":1,"method":"ping"}'),
    async (url, init) => {
      calls.push({ url, init });
      return response('{"jsonrpc":"2.0","id":1,"result":{}}');
    },
  );

  const result = await mcp.run({}, [], ctx);

  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, "http://shio.test/mcp");
  assert.equal(calls[0].init.method, "POST");
  assert.match(calls[0].init.headers.Authorization, /^Basic /);
  assert.equal(calls[0].init.body, '{"jsonrpc":"2.0","id":1,"method":"ping"}');
  assert.equal(ctx.stdout.text, '{"jsonrpc":"2.0","id":1,"result":{}}\n');
  assert.equal(result.messages, 1);
});

test("mints and then echoes the session id", async () => {
  const seen = [];
  const ctx = ctxWith(
    stdinOf(
      '{"jsonrpc":"2.0","id":1,"method":"initialize"}',
      '{"jsonrpc":"2.0","id":2,"method":"tools/list"}',
    ),
    async (_url, init) => {
      seen.push(init.headers["Mcp-Session-Id"] ?? null);
      return response('{"jsonrpc":"2.0","id":1,"result":{}}', {
        headers: { "mcp-session-id": "sess-1" },
      });
    },
  );

  const result = await mcp.run({}, [], ctx);

  // Nothing to send on the first request; the second carries what the server minted.
  assert.deepEqual(seen, [null, "sess-1"]);
  assert.equal(result.sessionId, "sess-1");
});

test("writes nothing for a 202 — a notification must not be answered", async () => {
  const ctx = ctxWith(
    stdinOf('{"jsonrpc":"2.0","method":"notifications/initialized"}'),
    async () => response("", { status: 202 }),
  );

  await mcp.run({}, [], ctx);

  assert.equal(ctx.stdout.text, "");
});

test("a network failure still answers, carrying the request id", async () => {
  // Otherwise the client waits forever on a promise it believes is in flight.
  const ctx = ctxWith(
    stdinOf('{"jsonrpc":"2.0","id":7,"method":"tools/list"}'),
    async () => {
      throw new Error("ECONNREFUSED");
    },
  );

  await mcp.run({}, [], ctx);

  const answer = JSON.parse(ctx.stdout.text.trim());
  assert.equal(answer.id, 7);
  assert.equal(answer.error.code, -32603);
  assert.match(answer.error.message, /ECONNREFUSED/);
});

test("an HTTP error that is not JSON-RPC is translated into one", async () => {
  const ctx = ctxWith(
    stdinOf('{"jsonrpc":"2.0","id":3,"method":"tools/list"}'),
    async () => response("<html>401</html>", { status: 401 }),
  );

  await mcp.run({}, [], ctx);

  const answer = JSON.parse(ctx.stdout.text.trim());
  assert.equal(answer.id, 3);
  assert.match(answer.error.message, /HTTP 401/);
});

test("a JSON-RPC error body is passed through untouched", async () => {
  const body = '{"jsonrpc":"2.0","id":4,"error":{"code":-32601,"message":"Unknown method"}}';
  const ctx = ctxWith(
    stdinOf('{"jsonrpc":"2.0","id":4,"method":"nope"}'),
    async () => response(body, { status: 400 }),
  );

  await mcp.run({}, [], ctx);

  assert.equal(ctx.stdout.text, `${body}\n`);
});

test("never emits an embedded newline, which would split one message into two", async () => {
  const ctx = ctxWith(
    stdinOf('{"jsonrpc":"2.0","id":5,"method":"ping"}'),
    async () => response('{\n  "jsonrpc": "2.0",\n  "id": 5,\n  "result": {}\n}'),
  );

  await mcp.run({}, [], ctx);

  assert.equal(ctx.stdout.text.trimEnd().includes("\n"), false);
  assert.deepEqual(JSON.parse(ctx.stdout.text), { jsonrpc: "2.0", id: 5, result: {} });
});

test("human output goes to stderr — stdout is the protocol channel", async () => {
  const ctx = ctxWith(stdinOf(""), async () => response(""));

  await mcp.run({}, [], ctx);

  assert.equal(ctx.stdout.text, "");
  assert.match(ctx.stderr.text, /bridging stdio/);
});
