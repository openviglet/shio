/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * Command-level tests with fake IO (scripted fetch + in-memory fs). Requires the
 * @viglet/shio-client build (root `test` script builds it first).
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import * as types from "../src/commands/types.mjs";
import * as push from "../src/commands/push.mjs";

function sink() {
  const out = { text: "" };
  out.write = (s) => {
    out.text += s;
  };
  return out;
}

/** Separator-agnostic so it works whether path.join yields "/" or "\". */
const norm = (p) => String(p).replace(/\\/g, "/");

/** Minimal in-memory fs capturing writes; seedable with files for reads. */
function memFs(seed = {}) {
  const files = new Map(Object.entries(seed).map(([k, v]) => [norm(k), v]));
  return {
    files,
    get: (p) => files.get(norm(p)),
    async mkdir() {},
    async writeFile(path, data) {
      files.set(norm(path), data);
    },
    async readFile(path) {
      const key = norm(path);
      if (!files.has(key)) throw new Error(`ENOENT: ${key}`);
      return files.get(key);
    },
    async readdir(dir) {
      const prefix = `${norm(dir).replace(/\/$/, "")}/`;
      return [...files.keys()]
        .filter((p) => p.startsWith(prefix))
        .map((p) => p.slice(prefix.length))
        .filter((p) => !p.includes("/"));
    },
  };
}

function fetchScript(handler) {
  const calls = [];
  const fn = async (url, init = {}) => {
    calls.push({ url, method: init.method ?? "GET", body: init.body });
    const { status = 200, body = {} } = handler(url, init.method ?? "GET") ?? {};
    return new Response(status === 204 ? null : JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/json" },
    });
  };
  fn.calls = calls;
  return fn;
}

test("types generates a .d.ts with a typed Post and writes it", async () => {
  const schema = {
    name: "news",
    title: "News",
    namePlural: "News",
    description: null,
    fields: [
      { name: "title", label: "Title", widget: "Text", required: true, multiple: false, isTitle: true, isSummary: false, ordinal: 0, fields: [] },
    ],
  };
  const fs = memFs();
  const stdout = sink();
  const ctx = {
    env: {},
    cwd: "/proj",
    stdout,
    stderr: sink(),
    fetch: fetchScript(() => ({ body: [schema] })),
    fs,
  };

  await types.run({ token: "t" }, [], ctx);

  const written = fs.get("/proj/lib/shio-types.d.ts");
  assert.ok(written, "should write the default output path");
  assert.match(written, /Post/);
  assert.match(stdout.text, /wrote 1 post-type/);
});

test("push --dry-run reports the plan and writes nothing remotely", async () => {
  const local = JSON.stringify({ name: "News", title: "News", fields: [] });
  const fs = memFs({ "/proj/shio/post-types/News.json": local });
  const fetchFn = fetchScript((url, method) => {
    if (method === "GET") return { body: [] }; // remote empty → News is "added"
    return { body: { name: "News" } };
  });
  const stdout = sink();
  const ctx = { env: {}, cwd: "/proj", stdout, stderr: sink(), fetch: fetchFn, fs };

  const result = await push.run({ dryRun: true, user: "u", password: "p" }, [], ctx);

  assert.equal(result.dryRun, true);
  assert.deepEqual(result.toPut.map((d) => d.name), ["News"]);
  assert.match(stdout.text, /1 added/);
  // Only the GET (remote fetch) happened — no PUT in dry-run.
  assert.equal(fetchFn.calls.filter((c) => c.method === "PUT").length, 0);
});

test("push applies upserts (PUT) when not a dry run", async () => {
  const local = JSON.stringify({ name: "News", title: "News", fields: [] });
  const fs = memFs({ "/proj/shio/post-types/News.json": local });
  const fetchFn = fetchScript((url, method) => (method === "GET" ? { body: [] } : { body: { name: "News" } }));
  const ctx = { env: {}, cwd: "/proj", stdout: sink(), stderr: sink(), fetch: fetchFn, fs };

  await push.run({ user: "u", password: "p" }, [], ctx);

  const puts = fetchFn.calls.filter((c) => c.method === "PUT");
  assert.equal(puts.length, 1);
  assert.equal(puts[0].url, "http://localhost:2710/api/v2/post-type/News");
});
