/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * Command-level tests with fake IO (scripted fetch + in-memory fs). Requires the
 * @viglet/shio-client build (root `test` script builds it first).
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import * as types from "../src/commands/types.mjs";
import * as push from "../src/commands/push.mjs";
import * as importCmd from "../src/commands/import.mjs";
import { formatError } from "../src/format-error.mjs";

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

test("push renames a moved post-type instead of creating and deleting it (SH235)", async () => {
  // `git mv shio/post-types/Article.json Note.json`: same body, new file name.
  const body = { title: "Story", namePlural: "Stories", system: false,
    fields: [{ name: "headline", label: "Headline", widget: "Text", ordinal: 1 }] };
  const fs = memFs({ "/proj/shio/post-types/Note.json": JSON.stringify({ name: "Note", ...body }) });
  const fetchFn = fetchScript((url, method) => {
    if (method === "GET") return { body: [{ name: "Article", ...body }] };
    return { body: { from: "Article", to: "Note", posts: 3, referencesUpdated: [], notes: [] } };
  });
  const stdout = sink();
  const ctx = { env: {}, cwd: "/proj", stdout, stderr: sink(), fetch: fetchFn, fs };

  const result = await push.run({ prune: true, user: "u", password: "p" }, [], ctx);

  assert.deepEqual(result.renames, [{ from: "Article", to: "Note", explicit: false }]);
  const posts = fetchFn.calls.filter((c) => c.method === "POST");
  assert.equal(posts.length, 1);
  assert.match(posts[0].url, /\/post-type\/Article\/rename$/);
  assert.deepEqual(JSON.parse(posts[0].body), { to: "Note" });
  // The two calls this replaces: a PUT that would have created a second type, and a DELETE
  // aimed at the one that still holds every post written against it.
  assert.equal(fetchFn.calls.filter((c) => c.method === "PUT").length, 0);
  assert.equal(fetchFn.calls.filter((c) => c.method === "DELETE").length, 0);
  assert.match(stdout.text, /Article renamed to Note/);
  // ...and the half the server cannot do is said out loud.
  assert.match(stdout.text, /shio\/content/);
});

test("push refuses an ambiguous rename before writing anything (SH235)", async () => {
  const body = { title: "Story", system: false,
    fields: [{ name: "headline", label: "Headline", widget: "Text", ordinal: 1 }] };
  const fs = memFs({ "/proj/shio/post-types/Note.json": JSON.stringify({ name: "Note", ...body }) });
  const fetchFn = fetchScript((url, method) => {
    if (method === "GET") return { body: [{ name: "Article", ...body }, { name: "Story", ...body }] };
    return { body: {} };
  });
  const stderr = sink();
  const ctx = { env: {}, cwd: "/proj", stdout: sink(), stderr, fetch: fetchFn, fs };

  const result = await push.run({ prune: true, user: "u", password: "p" }, [], ctx);

  assert.equal(result.exitCode, 1);
  assert.match(stderr.text, /cannot tell what "Note" is/);
  assert.match(stderr.text, /movedFrom/);
  // Nothing written: a coin flip here moves a content model's posts to the wrong type.
  assert.equal(fetchFn.calls.filter((c) => c.method !== "GET").length, 0);
});

test("push --check exits non-zero on drift and writes nothing", async () => {
  const local = JSON.stringify({ name: "News", title: "News", fields: [] });
  const fs = memFs({ "/proj/shio/post-types/News.json": local });
  const fetchFn = fetchScript((url, method) => (method === "GET" ? { body: [] } : { body: { name: "News" } }));
  const stdout = sink();
  const ctx = { env: {}, cwd: "/proj", stdout, stderr: sink(), fetch: fetchFn, fs };

  const result = await push.run({ check: true, user: "u", password: "p" }, [], ctx);

  assert.equal(result.check, true);
  assert.equal(result.drift, true);
  assert.equal(result.exitCode, 1);
  assert.match(stdout.text, /drift detected/);
  // No writes: only the GET (remote fetch), no PUT/DELETE.
  assert.equal(fetchFn.calls.filter((c) => c.method !== "GET").length, 0);
});

test("push --check exits zero when git matches server", async () => {
  const local = JSON.stringify({ name: "News", title: "News", fields: [] });
  const fs = memFs({ "/proj/shio/post-types/News.json": local });
  // Remote returns the same def → unchanged, no drift.
  const remote = { name: "News", title: "News", fields: [] };
  const fetchFn = fetchScript((url, method) => (method === "GET" ? { body: [remote] } : { body: { name: "News" } }));
  const stdout = sink();
  const ctx = { env: {}, cwd: "/proj", stdout, stderr: sink(), fetch: fetchFn, fs };

  const result = await push.run({ check: true, user: "u", password: "p" }, [], ctx);

  assert.equal(result.drift, false);
  assert.equal(result.exitCode, 0);
  assert.match(stdout.text, /in sync/);
  assert.equal(fetchFn.calls.filter((c) => c.method !== "GET").length, 0);
});

test("push --check --prune counts prunable deletes as drift (skips system types)", async () => {
  const fs = memFs(); // no local defs
  const remote = [
    { name: "News", title: "News", fields: [] }, // not local → prunable delete
    { name: "Folder", title: "Folder", system: true, fields: [] }, // system → never deleted
  ];
  const fetchFn = fetchScript((url, method) => (method === "GET" ? { body: remote } : { body: {} }));
  const stdout = sink();
  const ctx = { env: {}, cwd: "/proj", stdout, stderr: sink(), fetch: fetchFn, fs };

  const result = await push.run({ check: true, prune: true, user: "u", password: "p" }, [], ctx);

  assert.equal(result.drift, true);
  assert.equal(result.exitCode, 1);
  assert.deepEqual(result.toDelete, ["News"]); // system type excluded
  assert.equal(fetchFn.calls.filter((c) => c.method !== "GET").length, 0);
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

test("SH268: import posts the package as multipart and prints the site's address", async () => {
  const stdout = sink();
  const fetch = fetchScript(() => ({ status: 201, body: { id: "site-1", name: "Bootstrap" } }));
  const ctx = {
    env: { SHIO_URL: "http://shio.test", SHIO_USER: "u", SHIO_PASSWORD: "p" },
    cwd: "/proj",
    stdout,
    stderr: sink(),
    fetch,
    // The bytes come from disk, so the command takes its reader from ctx like every other one.
    readFile: async (path) => {
      assert.equal(norm(path), "/packages/bootstrap-site.zip");
      return new Uint8Array([0x50, 0x4b, 0x03, 0x04]);
    },
  };

  const site = await importCmd.run({}, ["/packages/bootstrap-site.zip"], ctx);

  assert.equal(site.name, "Bootstrap");
  assert.equal(stdout.text, "imported site:Bootstrap" + String.fromCharCode(10),
    "the name is what the next command addresses it by (SH79), not the id the response leads with");
  const [call] = fetch.calls;
  assert.equal(call.method, "POST");
  assert.equal(call.url, "http://shio.test/api/v2/import");
  assert.ok(call.body instanceof FormData, "multipart, because that is the endpoint the console uses");
  assert.equal(call.body.get("file").name, "bootstrap-site.zip",
    "the file name travels: SH189's errors echo it, because a caller who uploaded the wrong file "
      + "learns more from its name than from any diagnosis of the contents");
});

test("SH268: import with no file says what to pass, and never calls the instance", async () => {
  const fetch = fetchScript(() => ({ body: {} }));
  const ctx = { env: {}, cwd: "/proj", stdout: sink(), stderr: sink(), fetch };

  await assert.rejects(() => importCmd.run({}, [], ctx), /a package file is required/);
  assert.equal(fetch.calls.length, 0);
});

test("SH268: a teaching refusal reaches the operator instead of being swallowed", async () => {
  // SH189 and SH267 made this body say which of four things went wrong, with a fix. A CLI that
  // reported "import failed" would put the operator back where the console was.
  const fetch = fetchScript(() => ({
    status: 409,
    body: { type: "https://shio.viglet.com/errors/import-no-site", title: "The package holds no site.",
      fix: "apply post-types with PUT /api/v2/post-type/{name}" },
  }));
  const ctx = {
    env: { SHIO_URL: "http://shio.test", SHIO_USER: "u", SHIO_PASSWORD: "p" },
    cwd: "/proj",
    stdout: sink(),
    stderr: sink(),
    fetch,
    readFile: async () => new Uint8Array([1, 2, 3]),
  };

  const err = await importCmd.run({}, ["types-only.zip"], ctx).then(
    () => null,
    (thrown) => thrown,
  );
  assert.ok(err, "a refusal must not resolve");
  assert.equal(err.status, 409);
  // The body is what carries SH189's slug and fix, and `formatError` is what the operator reads:
  // asserting the rendering rather than the exception is the difference between "the teaching error
  // exists" and "the teaching error arrives".
  const shown = formatError(err);
  assert.match(shown, /holds no site/);
  assert.match(shown, /post-type/);
});
