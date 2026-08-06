/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH114 — `shio remember`, and SH115's `--as agents-md`. Network-free: a scripted
 * fetch records the request each command builds and returns a canned reply.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import * as context from "../src/commands/context.mjs";
import * as remember from "../src/commands/remember.mjs";

const AGENTS_MD = `# Working with this Shio instance

## Start here
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

/** Records every request; replies with `body`. */
function scriptedFetch(body = "{}", { status = 200 } = {}) {
  const calls = [];
  const fetchImpl = async (url, init) => {
    calls.push({ url: String(url), init, method: init?.method ?? "GET" });
    return {
      ok: status >= 200 && status < 300,
      status,
      headers: { get: () => null },
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

function ctxWith(fetchImpl) {
  return {
    env: { SHIO_URL: "http://shio.test", SHIO_USER: "admin", SHIO_PASSWORD: "secret" },
    cwd: "/repo",
    stdout: sink(),
    stderr: sink(),
    fs: memFs(),
    fetch: fetchImpl,
  };
}

test("remember with a key and a note PUTs it", async () => {
  const saved = JSON.stringify({
    key: "blog.post-type",
    note: "This site's blog uses Article, not Post.",
    scope: "Blog",
    author: "admin",
  });
  const fetchImpl = scriptedFetch(saved);
  const ctx = ctxWith(fetchImpl);

  await remember.run(
    { site: "Blog" },
    ["blog.post-type", "This", "site's", "blog", "uses", "Article,", "not", "Post."],
    ctx,
  );

  const call = fetchImpl.calls[0];
  assert.equal(call.method, "PUT");
  assert.match(call.url, /\/api\/v2\/agent\/memory$/);
  const body = JSON.parse(call.init.body);
  assert.equal(body.key, "blog.post-type");
  assert.equal(body.scope, "Blog");
  assert.equal(body.note, "This site's blog uses Article, not Post.");
  // The author is the server's to assign — a caller that could set it could make its
  // own inference look like a human's instruction.
  assert.equal(body.author, undefined);
});

test("remember with no positionals lists what is already known", async () => {
  const fetchImpl = scriptedFetch(
    JSON.stringify({
      found: 2,
      notes: [
        { key: "copy.source", note: "Copy is authored in files.", author: "human" },
        { key: "blog.post-type", note: "Use Article.", scope: "Blog", author: "claude" },
      ],
    }),
  );
  const ctx = ctxWith(fetchImpl);

  await remember.run({}, [], ctx);

  assert.equal(fetchImpl.calls[0].method, "GET");
  assert.match(fetchImpl.calls[0].url, /\/api\/v2\/agent\/memory/);
  assert.match(ctx.stdout.text, /copy\.source\tCopy is authored in files\./);
  // A scoped note prints its scope, because "everywhere" and "on Blog" are different
  // claims and a reader acting on the wrong one edits the wrong site.
  assert.match(ctx.stdout.text, /blog\.post-type@Blog\tUse Article\./);
});

test("a truncated listing says so rather than reading as the whole set", async () => {
  const fetchImpl = scriptedFetch(
    JSON.stringify({ found: 40, notes: [{ key: "one", note: "First." }] }),
  );
  const ctx = ctxWith(fetchImpl);

  await remember.run({ limit: 1 }, [], ctx);

  assert.match(ctx.stdout.text, /showing 1 of 40/);
});

test("remember --forget DELETEs the note", async () => {
  const fetchImpl = scriptedFetch("", { status: 204 });
  const ctx = ctxWith(fetchImpl);

  await remember.run({ forget: true, site: "Blog" }, ["stale.note"], ctx);

  const call = fetchImpl.calls[0];
  assert.equal(call.method, "DELETE");
  const url = new URL(call.url);
  assert.equal(url.searchParams.get("key"), "stale.note");
  assert.equal(url.searchParams.get("scope"), "Blog");
});

test("remember with a key but no note explains both ways out", async () => {
  await assert.rejects(
    () => remember.run({}, ["orphan"], ctxWith(scriptedFetch())),
    /nothing to remember for "orphan".*--forget/s,
  );
});

test("an empty store says how to add the first note", async () => {
  const fetchImpl = scriptedFetch(JSON.stringify({ found: 0, notes: [] }));
  const ctx = ctxWith(fetchImpl);

  await remember.run({}, [], ctx);

  assert.match(ctx.stdout.text, /nothing remembered yet/);
  assert.match(ctx.stdout.text, /shio remember <key>/);
});

test("context --as agents-md asks the server for the markdown rendering", async () => {
  const fetchImpl = scriptedFetch(AGENTS_MD);
  const ctx = ctxWith(fetchImpl);

  const result = await context.run({ as: "agents-md" }, [], ctx);

  const call = fetchImpl.calls[0];
  assert.match(call.url, /format=agents-md/);
  assert.equal(call.init.headers.Accept, "text/markdown");
  assert.equal(result.format, "agents-md");
});

test("context --as agents-md --out writes a committable file", async () => {
  const fetchImpl = scriptedFetch(AGENTS_MD);
  const ctx = ctxWith(fetchImpl);

  await context.run({ as: "agents-md", out: "AGENTS.md" }, [], ctx);

  // The whole point of SH115 is a file that cannot rot: it has to land on disk, or
  // "regenerate it and the diff is the drift" is not a workflow anyone can run.
  assert.match(ctx.fs.get("/repo/AGENTS.md"), /^# Working with this Shio instance/);
});

test("context rejects an unknown --as with the allowed set", async () => {
  await assert.rejects(
    () => context.run({ as: "agent-md" }, [], ctxWith(scriptedFetch())),
    /unknown --format "agent-md"\. Allowed: terse, json, agents-md/,
  );
});
