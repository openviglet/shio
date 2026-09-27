/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { ShioAgentClient, readCache, useReadCache } from "../src/agent-client.mjs";
import {
  READ_CACHE_FILE,
  ShReadCache,
  ShReadCacheCorrupt,
  addressOf,
  digestOf,
  emptyCache,
  evict,
  instanceOf,
  parseCache,
  serializeCache,
} from "../src/read-cache.mjs";

/**
 * SH1117 — a second identical read costing no body, counted rather than claimed.
 *
 * <p>SH1048 made the console's reads answer 304 and its design said MCP and the CLI got it free. A
 * browser does; Node has no cache, so `cli/src` sent `If-None-Match` in exactly one place — `clone`
 * probing external assets — and never to Shio's own API. Every `shio pull`, `verify`, `digest` and
 * `context` paid the full body against an unchanged instance, which is the case a CLI is in most of
 * the time.</p>
 *
 * <p>The two halves worth asserting are the two the design names: the saving has to be <b>counted</b>
 * — without a count a store that stopped matching reads exactly like a server that never answers 304
 * — and a <b>stale store must fail loudly</b> rather than hand back bytes a 304 would bless.</p>
 */

/** A fetch that answers 200 with an ETag once, then 304 to a matching `If-None-Match`. */
function conditionalServer({ etag = '"v1"', body = '{"ok":true}' } = {}) {
  const calls = [];
  const impl = async (url, init) => {
    const sent = init?.headers?.["If-None-Match"] ?? null;
    calls.push({ url, ifNoneMatch: sent });
    if (sent === etag) {
      return {
        ok: false,
        status: 304,
        headers: { get: (name) => (name.toLowerCase() === "etag" ? etag : null) },
        text: async () => "",
        json: async () => {
          throw new Error("a 304 carries no body — reading one is the defect this test is about");
        },
      };
    }
    return {
      ok: true,
      status: 200,
      headers: { get: (name) => (name.toLowerCase() === "etag" ? etag : null) },
      text: async () => body,
      json: async () => JSON.parse(body),
    };
  };
  return { impl, calls };
}

/** A cache over a string, so the whole of this drives with no disk. */
function memoryCache(initial = null) {
  const held = { text: initial };
  const cache = new ShReadCache({
    load: () => held.text,
    save: (text) => { held.text = text; },
    now: () => 1_700_000_000_000,
  });
  return { cache, held };
}

test("SH1117: the second identical read sends the tag and is answered without a body", async () => {
  const { impl, calls } = conditionalServer();
  const { cache } = memoryCache();
  useReadCache(cache);
  try {
    const client = new ShioAgentClient({
      baseUrl: "http://localhost:2710/", user: "a", password: "b", fetch: impl,
    });

    const first = await client.find({ site: "Blog" });
    const second = await client.find({ site: "Blog" });

    assert.deepEqual(first, { ok: true }, "the first read is the server's own answer");
    assert.deepEqual(second, { ok: true },
      "SH1117: a 304 has to produce the same value as the 200 it stands in for. If this differs, the "
      + "cache is handing back something the server never sent.");
    assert.equal(calls.length, 2, "both reads still reach the instance — this is revalidation, not a TTL");
    assert.equal(calls[0].ifNoneMatch, null, "nothing is known on the first read, so it is unconditional");
    assert.equal(calls[1].ifNoneMatch, '"v1"',
      "SH1117: the second read sends no `If-None-Match`, which is the whole defect — the CLI sent one "
      + "only to external assets `clone` was copying and never to Shio's own API");

    // Counted, not claimed. Without this a store that stopped matching is indistinguishable from an
    // instance that never answers 304: every command still works and nothing is ever saved.
    assert.equal(cache.ledger.conditional, 1);
    assert.equal(cache.ledger.revalidated, 1);
    assert.equal(cache.ledger.full, 1);
    assert.equal(cache.ledger.savedBytes, '{"ok":true}'.length,
      "SH1117: the bytes a 304 did not send are the saving, and they are the number to report");
    assert.match(cache.report(), /1 conditional, 1 revalidated/);
  } finally {
    useReadCache(null);
  }
});

test("SH1117: a run that learns nothing writes no file, and one that learns something writes once",
  async () => {
    const { impl } = conditionalServer();
    const { cache, held } = memoryCache();
    useReadCache(cache);
    try {
      assert.equal(cache.flush(), false, "SH1117: an untouched cache must not rewrite its own file — a "
        + "store that is written on every invocation is a diff on every invocation");
      const client = new ShioAgentClient({
        baseUrl: "http://localhost:2710", user: "a", password: "b", fetch: impl,
      });
      await client.find({ site: "Blog" });
      assert.equal(cache.flush(), true);
      assert.ok(held.text.endsWith("\n"), "SH1117: byte-stable output, one trailing newline");
      assert.deepEqual(Object.keys(parseCache(held.text).instances), ["http://localhost:2710"],
        "SH1117: keyed by instance, so two instances cannot answer for each other");
    } finally {
      useReadCache(null);
    }
  });

test("SH1117: a body that is not the body its digest names is refused, not used", () => {
  const tampered = serializeCache({
    format: 1,
    instances: {
      "http://localhost:2710": {
        "/api/v2/agent/find": { at: 1, digest: digestOf('{"ok":true}'), etag: '"v1"', body: '{"ok":false}' },
      },
    },
  });
  const { cache } = memoryCache(tampered);

  assert.throws(() => cache.hit("http://localhost:2710", "http://localhost:2710/api/v2/agent/find"),
    (error) => {
      assert.ok(error instanceof ShReadCacheCorrupt,
        "SH1117: a stale store has to fail loudly. Re-fetching quietly is the tempting answer and the "
        + "wrong one — the entry claims a body it does not hold, and the next 304 would bless bytes of "
        + "unknown provenance as current.");
      assert.match(error.message, /fix:/,
        "P6: the refusal carries what to do about it, because nobody knows this file exists");
      assert.ok(error.message.includes(READ_CACHE_FILE), "and where the file is");
      return true;
    });
});

test("SH1117: an absent, unreadable or out-of-format store is nothing known, not a failure", () => {
  for (const text of [null, "", "   ", "{not json", "[]", '{"format":99,"instances":{}}']) {
    assert.deepEqual(parseCache(text), emptyCache(),
      `SH1117: ${JSON.stringify(text)} must read as an empty store. None of these is a claim about `
      + "content, so the honest answer is that nothing is known and the next read is unconditional — "
      + "a refusal here would make an old file break a command for no reason.");
  }
});

test("SH1117: one request has one key however its query is spelled", () => {
  assert.equal(addressOf("http://x/api/v2/agent/find?depth=2&site=a"),
    addressOf("http://x/api/v2/agent/find?site=a&depth=2"),
    "SH1117: two callers spelling one request in two orders would otherwise keep two entries and "
    + "revalidate neither");
  assert.equal(addressOf("http://x/api/v2/agent/find"), "/api/v2/agent/find",
    "SH1117: the origin is the other half of the key, so an entry filed under it twice is two "
    + "spellings that can disagree");
  assert.equal(addressOf("http://x/api/v2/agent/find?site=a"), "/api/v2/agent/find?site=a");
  assert.equal(instanceOf("http://localhost:2710/"), instanceOf("http://localhost:2710"),
    "SH1117: a trailing slash is not a second server");
});

test("SH1117: the store is bounded, oldest-first, so it cannot become a leak", () => {
  const entries = {
    old: { at: 1, body: "x".repeat(60), etag: '"a"', digest: digestOf("x".repeat(60)) },
    mid: { at: 2, body: "y".repeat(60), etag: '"b"', digest: digestOf("y".repeat(60)) },
    fresh: { at: 3, body: "z".repeat(60), etag: '"c"', digest: digestOf("z".repeat(60)) },
  };
  assert.deepEqual(Object.keys(evict(entries, 130)).sort(), ["fresh", "mid"],
    "SH1117: `find` and `read` answer per address, so an unbounded store accumulates an entry per "
    + "post ever asked for. The read nobody repeats is the one worth dropping.");
  assert.deepEqual(evict(entries, 10), {},
    "SH1117: and a limit nothing fits under leaves nothing rather than one entry over it");
});

test("SH1117: with no cache in force the client behaves exactly as it did", async () => {
  const { impl, calls } = conditionalServer();
  useReadCache(null);
  assert.equal(readCache(), null);
  const client = new ShioAgentClient({
    baseUrl: "http://localhost:2710", user: "a", password: "b", fetch: impl,
  });
  await client.find({ site: "Blog" });
  await client.find({ site: "Blog" });
  assert.deepEqual(calls.map((each) => each.ifNoneMatch), [null, null],
    "SH1117: `--no-read-cache` has to mean what it says — a run that must be seen to read the "
    + "instance sends no validator at all, rather than one the server might honour");
});

test("SH1117: a 200 stores the bytes the tag names, not a re-serialization of them", async () => {
  // The defect this closes is invisible until the next 304: `res.json()` then `JSON.stringify` puts a
  // *different* string under the instance's ETag — different key order, no whitespace — and the
  // revalidated answer is something the server never sent.
  const body = '{\n  "b": 1,\n  "a": 2\n}';
  const { impl } = conditionalServer({ body });
  const { cache } = memoryCache();
  useReadCache(cache);
  try {
    const client = new ShioAgentClient({
      baseUrl: "http://localhost:2710", user: "a", password: "b", fetch: impl,
    });
    await client.find({});
    const held = cache.hit("http://localhost:2710", "http://localhost:2710/api/v2/agent/find");
    assert.equal(held.body, body,
      "SH1117: the stored body is not the bytes the server sent, so the ETag it is filed under names "
      + "something else");
    assert.equal(held.digest, digestOf(body));
  } finally {
    useReadCache(null);
  }
});

test("SH1117: context revalidates too, and it is the read this was written for", async () => {
  const body = "# agents.md\n";
  const { impl, calls } = conditionalServer({ etag: '"ctx"', body });
  const { cache } = memoryCache();
  useReadCache(cache);
  try {
    const client = new ShioAgentClient({
      baseUrl: "http://localhost:2710", user: "a", password: "b", fetch: impl,
    });
    const first = await client.getContext({ format: "agents-md" });
    const second = await client.getContext({ format: "agents-md" });
    assert.equal(first.body, body);
    assert.equal(second.body, body,
      "SH1117: `context` is the one call that replaces a session (P2) and the largest body on the "
      + "surface; a run against an unchanged instance repeats it verbatim");
    assert.equal(calls[1].ifNoneMatch, '"ctx"');
    assert.equal(cache.ledger.savedBytes, body.length);
  } finally {
    useReadCache(null);
  }
});
