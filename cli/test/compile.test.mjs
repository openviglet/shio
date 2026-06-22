/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH64 — the CLI compile step: `readLocalDefs` over a mixed `.ts`/`.json` dir
 * (fake module loader + in-memory fs), the duplicate-name guard, and the `build`
 * command emitting canonical JSON. No live server, no disk, no real TS loader.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { collectDefsFromModule, readLocalDefs } from "../src/post-types-compile.mjs";
import * as build from "../src/commands/build.mjs";
import * as push from "../src/commands/push.mjs";

function sink() {
  const out = { text: "" };
  out.write = (s) => {
    out.text += s;
  };
  return out;
}

const norm = (p) => String(p).replace(/\\/g, "/");

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

/** Fake module loader keyed by basename (DSL files never hit disk in tests). */
function fakeImporter(modules) {
  return async (url) => {
    const base = String(url).split("/").pop();
    if (!(base in modules)) throw new Error(`fake import: no module "${base}"`);
    return modules[base];
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

const def = (name, fields = []) => ({ name, title: name, fields });

test("collectDefsFromModule gathers default, named, and array exports", () => {
  const fromDefault = collectDefsFromModule({ default: def("A") });
  assert.deepEqual(fromDefault.map((d) => d.name), ["A"]);

  const fromNamed = collectDefsFromModule({ news: def("News"), page: def("Page") });
  assert.deepEqual(fromNamed.map((d) => d.name).sort(), ["News", "Page"]);

  const fromArray = collectDefsFromModule({ default: [def("X"), def("Y")] });
  assert.deepEqual(fromArray.map((d) => d.name), ["X", "Y"]);

  // Non-def exports (helpers, constants) are ignored.
  const mixed = collectDefsFromModule({ default: def("Z"), WIDGET: { TEXT: "Text" }, n: 42 });
  assert.deepEqual(mixed.map((d) => d.name), ["Z"]);
});

test("readLocalDefs compiles a mixed .ts/.json directory", async () => {
  const fs = memFs({
    "/proj/shio/post-types/Page.json": JSON.stringify(def("Page")),
    "/proj/shio/post-types/news.ts": "// DSL source",
  });
  const ctx = {
    cwd: "/proj",
    fs,
    import: fakeImporter({ "news.ts": { default: def("News") } }),
  };

  const defs = await readLocalDefs(ctx, "shio/post-types");

  assert.deepEqual(defs.map((d) => d.name).sort(), ["News", "Page"]);
});

test("readLocalDefs errors when a name is defined twice across sources", async () => {
  const fs = memFs({
    "/proj/shio/post-types/News.json": JSON.stringify(def("News")),
    "/proj/shio/post-types/news.ts": "// DSL source",
  });
  const ctx = {
    cwd: "/proj",
    fs,
    import: fakeImporter({ "news.ts": { default: def("News") } }),
  };

  await assert.rejects(() => readLocalDefs(ctx, "shio/post-types"), /defined twice/);
});

test("readLocalDefs errors when a DSL module exports no post-type", async () => {
  const fs = memFs({ "/proj/shio/post-types/empty.mjs": "// nothing" });
  const ctx = { cwd: "/proj", fs, import: fakeImporter({ "empty.mjs": { foo: 1 } }) };

  await assert.rejects(() => readLocalDefs(ctx, "shio/post-types"), /exports no post-type/);
});

test("build compiles DSL + JSON to canonical JSON under shio/.out", async () => {
  const fs = memFs({
    "/proj/shio/post-types/Page.json": JSON.stringify(def("Page")),
    "/proj/shio/post-types/news.ts": "// DSL source",
  });
  const stdout = sink();
  const ctx = {
    cwd: "/proj",
    fs,
    stdout,
    stderr: sink(),
    import: fakeImporter({ "news.ts": { default: def("News", [
      { name: "title", widget: "Text", required: true, isTitle: true, ordinal: 1 },
    ]) } }),
  };

  const defs = await build.run({}, [], ctx);

  assert.equal(defs.length, 2);
  const newsOut = fs.get("/proj/shio/.out/News.json");
  assert.ok(newsOut, "writes News.json to the default out dir");
  // Canonical output: pretty JSON, trailing newline, normalized field keys.
  assert.match(newsOut, /"name": "News"/);
  assert.match(newsOut, /"isTitle": true/);
  assert.ok(newsOut.endsWith("}\n"), "trailing newline");
  assert.match(stdout.text, /compiled 2 post-type/);
});

test("push applies a .ts-authored model exactly like JSON", async () => {
  const fs = memFs({ "/proj/shio/post-types/news.ts": "// DSL source" });
  const fetchFn = fetchScript((url, method) => (method === "GET" ? { body: [] } : { body: { name: "News" } }));
  const ctx = {
    env: {},
    cwd: "/proj",
    stdout: sink(),
    stderr: sink(),
    fetch: fetchFn,
    fs,
    import: fakeImporter({ "news.ts": { default: def("News") } }),
  };

  const result = await push.run({ user: "u", password: "p" }, [], ctx);

  assert.deepEqual(result.toPut.map((d) => d.name), ["News"]);
  const puts = fetchFn.calls.filter((c) => c.method === "PUT");
  assert.equal(puts.length, 1);
  assert.equal(puts[0].url, "http://localhost:2710/api/v2/post-type/News");
});
