/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH87 / SH89 / SH90 — `pull --content`, `push --content` and the dev sync loop, against a
 * fake filesystem and a scripted server. Network-free and disk-free on purpose: what is
 * being tested is the *decisions* (what gets written, what gets refused, what reaches the
 * write endpoint), and those should not depend on a live instance to observe.
 */
import assert from "node:assert/strict";
import { join } from "node:path";
import { test } from "node:test";

import * as changes from "../src/commands/changes.mjs";
import * as contentPull from "../src/commands/content-pull.mjs";
import * as pull from "../src/commands/pull.mjs";
import * as contentPush from "../src/commands/content-push.mjs";
import * as watch from "../src/commands/content-watch.mjs";
import { digestsOf } from "../src/content-sync.mjs";
import { sha256Of } from "../src/content-assets.mjs";
import { norm } from "./support/paths.mjs";

const DEFS = [
  {
    name: "Article",
    fields: [
      { name: "headline", widget: "Text", ordinal: 1 },
      { name: "body", widget: "HTML Editor", ordinal: 2 },
      // SH406 — a File field, so a fixture can show an id becoming an asset path (SH145). Additive:
      // a post that does not set it serializes exactly as before.
      { name: "hero", widget: "File", ordinal: 3 },
    ],
  },
  // SH183's render model, as SH191 needs to see it: two code fields, no body field at all.
  {
    name: "Region",
    fields: [
      { name: "HTML", widget: "Ace Editor - HTML", ordinal: 1 },
      { name: "JAVASCRIPT", widget: "Ace Editor - Javascript", ordinal: 2 },
    ],
  },
];

/** An in-memory fs with the handful of calls the content commands make. */
function fakeFs(seed = {}) {
  const files = new Map(Object.entries(seed));
  return {
    files,
    async readFile(path) {
      const key = norm(path);
      if (!files.has(key)) {
        const error = new Error(`ENOENT: ${key}`);
        error.code = "ENOENT";
        throw error;
      }
      return files.get(key);
    },
    async writeFile(path, body) {
      files.set(norm(path), body);
    },
    async appendFile(path, body) {
      files.set(norm(path), (files.get(norm(path)) ?? "") + body);
    },
    async mkdir() {
      /* directories are implicit in a flat map */
    },
    async unlink(path) {
      const key = norm(path);
      if (!files.delete(key)) {
        const error = new Error(`ENOENT: ${key}`);
        error.code = "ENOENT";
        throw error;
      }
    },
    async readdir(path, options) {
      const prefix = `${norm(path)}/`;
      const names = new Set();
      for (const key of files.keys()) {
        if (!key.startsWith(prefix)) continue;
        const rest = key.slice(prefix.length);
        const cut = rest.indexOf("/");
        names.add(cut < 0 ? rest : `${rest.slice(0, cut)}/`);
      }
      if (!names.size) {
        const error = new Error(`ENOENT: ${prefix}`);
        error.code = "ENOENT";
        throw error;
      }
      return [...names].sort().map((name) => ({
        name: name.replace(/\/$/, ""),
        isDirectory: () => name.endsWith("/"),
      }));
    },
  };
}

function sink() {
  const out = { text: "" };
  out.write = (chunk) => {
    out.text += chunk;
  };
  return out;
}

/**
 * A scripted instance: `find`, `read`, `post-type`, `plan`/`apply` and `changes`.
 * `remote` is a map of address → agent doc.
 */
function server({
  remote = {},
  truncated = false,
  changed = 2,
  pageSize = 0,
  // SH149 — the folders each site holds, as the sitemap reports them: `{ Blog: ["/", "/news"] }`.
  serverFolders = null,
  // SH145 — the media inventory: `[{ id, fileName, folderId, siteName, size }]`, plus the bytes.
  serverAssets = [],
  // SH210 — what the server holds for an id, so a test can make the bytes match or differ.
  serverBytes = {},
  // SH405 — the sitemap walk reports it could not finish. Nothing fits after the first page, so
  // there is no `truncatedAfter` to resume from, which is the shape that ends the walk short.
  sitemapTruncated = false,
  // SH408 — so a test can put two post-type names differing only in case on the instance.
  defs = DEFS,
  // SH231 — what `GET /agent/memory` reports: `[{ scope, key, note }]`, scope absent =
  // instance-wide. Empty by default, so every test written before this reads unchanged.
  serverNotes = [],
} = {}) {
  const calls = [];
  const uploads = [];
  const replacements = [];
  const fetchImpl = async (url, init = {}) => {
    const target = String(url);
    const jsonBody = init.body && typeof init.body === "string" ? JSON.parse(init.body) : null;
    calls.push({ url: target, init, body: jsonBody });
    if (target.includes("/agent/context")) {
      const site = new URL(target).searchParams.get("site");
      const paths = serverFolders?.[site] ?? [];
      const wantIds = new URL(target).searchParams.get("ids") === "true";
      return json({
        // A folder's id is `<site>:<chain>` here — enough to be joined against an asset's
        // `folderId`, and readable in a failure message.
        sitemap: paths.map((path) => ({
          kind: "folder",
          path,
          ...(wantIds ? { id: `${site}:${path}` } : {}),
        })),
        budget: { estTokens: 10, limitTokens: 0, truncated: sitemapTruncated },
      });
    }
    if (target.includes("/api/v2/staticfile/upload")) {
      const created = { id: `uploaded-${uploads.length + 1}` };
      uploads.push({ body: init.body, created });
      return json(created);
    }
    if (/\/api\/v2\/staticfile\/[^/?]+\/digest$/.test(target)) {
      const id = target.split("/").at(-2);
      const bytes = serverBytes[id] ?? `bytes-of-${id}`;
      return json({ id, size: Buffer.byteLength(bytes), sha256: sha256Of(bytes) });
    }
    if (/\/api\/v2\/staticfile\/[^/?]+\/replace$/.test(target)) {
      const id = target.split("/").at(-2);
      replacements.push({ id, body: init.body });
      return json({ id });
    }
    if (/\/api\/v2\/staticfile\/[^/?]+$/.test(target)) {
      const id = target.split("/").pop();
      return bytesResponse(new TextEncoder().encode(serverBytes[id] ?? `bytes-of-${id}`));
    }
    if (target.includes("/api/v2/staticfile")) {
      return json(serverAssets);
    }
    if (target.includes("/agent/find")) {
      // SH146 — the real endpoint sorts by address and pages on `after`, so the fake does
      // too: `pageSize` makes it hand out cursors, `truncated` (with no cursor) is the
      // pre-SH146 dead end the projection must still refuse.
      const after = new URL(target).searchParams.get("after");
      const all = Object.keys(remote).sort();
      const rest = after ? all.filter((address) => address > after) : all;
      const page = pageSize ? rest.slice(0, pageSize) : rest;
      const more = page.length < rest.length;
      return json({
        count: page.length,
        truncated: truncated || more,
        cursor: more ? page[page.length - 1] : undefined,
        results: page.map((address) => ({ address, type: remote[address].type })),
      });
    }
    if (target.includes("/agent/read")) {
      const wanted = [...new URL(target).searchParams.getAll("address")];
      return json(wanted.map((address) => remote[address]).filter(Boolean));
    }
    if (target.includes("/api/v2/post-type")) return json(defs);
    if (target.includes("/agent/memory")) {
      // The real endpoint answers a scoped read with that site's notes PLUS the instance-wide
      // ones, and an unscoped read with everything — which is the shape SH231's two readers
      // filter in opposite directions, so the fake has to reproduce it or neither is tested.
      const scope = new URL(target).searchParams.get("site");
      return json({
        notes: serverNotes.filter((note) => !scope || !note.scope || note.scope === scope),
      });
    }
    if (target.includes("/agent/changes")) {
      return json({ found: 0, window: true, cursor: "2026-07-26T10:00:00Z|abc", more: false, changes: [], notes: [] });
    }
    if (target.includes("/agent/plan") || target.includes("/agent/apply")) {
      return json({ dryRun: target.includes("plan"), ops: 3, changed, results: [] });
    }
    return json({});
  };
  fetchImpl.calls = calls;
  fetchImpl.uploads = uploads;
  fetchImpl.replacements = replacements;
  return fetchImpl;
}

/** A bytes response, for the asset download path (SH145). */
function bytesResponse(bytes) {
  return {
    ok: true,
    status: 200,
    headers: { get: () => null },
    async arrayBuffer() {
      return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength);
    },
    async text() {
      return new TextDecoder().decode(bytes);
    },
  };
}

function json(body, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: () => null },
    async text() {
      return JSON.stringify(body);
    },
    async json() {
      return body;
    },
  };
}

function ctxWith(fetchImpl, seed = {}) {
  return {
    env: { SHIO_URL: "http://shio.test", SHIO_USER: "admin", SHIO_PASSWORD: "secret" },
    cwd: "/repo",
    stdout: sink(),
    stderr: sink(),
    fetch: fetchImpl,
    fs: fakeFs(seed),
  };
}

const REMOTE_DOC = {
  address: "post:Blog/blog/hello",
  type: "Article",
  state: "DRAFT",
  folder: "/blog",
  data: { headline: "Hello", body: "<p>First</p>" },
};

const FILE = "/repo/shio/content/Blog/blog/hello.md";
const BASE_PATH = "/repo/shio/.shio/content-base.json";
/** A folder marker's path and contents (SH149) — a root of its own, beside the content tree. */
const markerPath = (site, chain) =>
  `/repo/shio/folders/${site}${chain === "/" ? "" : chain}/_folder.md`;
const markerFile = (site, folder) => `---\nsite: ${site}\nfolder: ${folder}\n---\n`;

function baseSnapshot(data, { partial = false } = {}) {
  return `${JSON.stringify({
    version: 1,
    cursor: "2026-07-26T09:00:00Z|aaa",
    partial,
    posts: {
      "post:Blog/blog/hello": {
        site: "Blog",
        url: "/blog/hello",
        type: "Article",
        folder: "/blog",
        data,
      },
    },
  })}\n`;
}

// --- pull ---

test("pull --content writes a file per post and records the cursor", async () => {
  const ctx = ctxWith(server({ remote: { "post:Blog/blog/hello": REMOTE_DOC } }));

  await contentPull.run({}, [], ctx);

  const file = ctx.fs.files.get(FILE);
  assert.match(file, /^---\nsite: Blog\nurl: \/blog\/hello\ntype: Article\nfolder: \/blog\n/);
  // The rich-text field became the body — that is what makes a later Edit cheap.
  assert.match(file, /\nbody: body\nfields:\n  headline: Hello\n---\n<p>First<\/p>\n$/);
  assert.match(ctx.fs.files.get(BASE_PATH), /"cursor": "2026-07-26T10:00:00Z\|abc"/);
});

test("pull --content leaves a locally-changed post alone", async () => {
  // A pull is not a download: the file tree is ahead here, and `push --content` is the
  // other half of the loop.
  const local = `---\nsite: Blog\nurl: /blog/hello\ntype: Article\nfolder: /blog\nbody: body\nfields:\n  headline: Hello\n---\n<p>Rewritten locally</p>\n`;
  const ctx = ctxWith(server({ remote: { "post:Blog/blog/hello": REMOTE_DOC } }), {
    [FILE]: local,
    [BASE_PATH]: baseSnapshot({ headline: "Hello", body: "<p>First</p>" }),
  });

  const result = await contentPull.run({}, [], ctx);

  assert.equal(ctx.fs.files.get(FILE), local);
  assert.deepEqual(result.kept, ["post:Blog/blog/hello"]);
});

test("pull --content merges edits to different fields", async () => {
  const local = `---\nsite: Blog\nurl: /blog/hello\ntype: Article\nfolder: /blog\nbody: body\nfields:\n  headline: Hello\n---\n<p>Rewritten locally</p>\n`;
  const remote = {
    "post:Blog/blog/hello": { ...REMOTE_DOC, data: { headline: "Hello there", body: "<p>First</p>" } },
  };
  const ctx = ctxWith(server({ remote }), {
    [FILE]: local,
    [BASE_PATH]: baseSnapshot({ headline: "Hello", body: "<p>First</p>" }),
  });

  const result = await contentPull.run({}, [], ctx);

  assert.deepEqual(result.written, ["post:Blog/blog/hello (merged)"]);
  const merged = ctx.fs.files.get(FILE);
  assert.match(merged, /headline: Hello there/);
  assert.match(merged, /<p>Rewritten locally<\/p>/);
});

test("pull --content writes nothing for a conflict and exits non-zero", async () => {
  const local = `---\nsite: Blog\nurl: /blog/hello\ntype: Article\nfolder: /blog\nbody: body\nfields:\n  headline: Ours\n---\n<p>First</p>\n`;
  const remote = {
    "post:Blog/blog/hello": { ...REMOTE_DOC, data: { headline: "Theirs", body: "<p>First</p>" } },
  };
  const ctx = ctxWith(server({ remote }), {
    [FILE]: local,
    [BASE_PATH]: baseSnapshot({ headline: "Hello", body: "<p>First</p>" }),
  });

  const result = await contentPull.run({ showRemote: true }, [], ctx);

  assert.equal(result.exitCode, 1);
  assert.equal(ctx.fs.files.get(FILE), local);
  // No conflict markers anywhere: they would be content, and the next push would send them.
  assert.doesNotMatch(ctx.fs.files.get(FILE), /<<<</);
  // The server's copy sits beside it so a diff is one command away.
  assert.match(ctx.fs.files.get(`${FILE}.remote`), /headline: Theirs/);
  assert.match(ctx.stderr.text, /--theirs|--ours/);
});

test("--theirs resolves a conflict towards the server", async () => {
  const local = `---\nsite: Blog\nurl: /blog/hello\ntype: Article\nfolder: /blog\nbody: body\nfields:\n  headline: Ours\n---\n<p>First</p>\n`;
  const remote = {
    "post:Blog/blog/hello": { ...REMOTE_DOC, data: { headline: "Theirs", body: "<p>First</p>" } },
  };
  const ctx = ctxWith(server({ remote }), {
    [FILE]: local,
    [BASE_PATH]: baseSnapshot({ headline: "Hello", body: "<p>First</p>" }),
  });

  const result = await contentPull.run({ theirs: true }, [], ctx);

  assert.equal(result.exitCode, undefined);
  assert.match(ctx.fs.files.get(FILE), /headline: Theirs/);
});

test("a post deleted on the server deletes its file when nobody touched it", async () => {
  const local = `---\nsite: Blog\nurl: /blog/hello\ntype: Article\nfolder: /blog\nbody: body\nfields:\n  headline: Hello\n---\n<p>First</p>\n`;
  const ctx = ctxWith(server({ remote: {} }), {
    [FILE]: local,
    [BASE_PATH]: baseSnapshot({ headline: "Hello", body: "<p>First</p>" }),
  });

  const result = await contentPull.run({}, [], ctx);

  assert.deepEqual(result.deleted, ["post:Blog/blog/hello"]);
  assert.equal(ctx.fs.files.has(FILE), false);
});

test("a truncated post list refuses to write a partial projection", async () => {
  // The dangerous case: a partial projection that a later `push --prune` reads as complete
  // would delete every post it never saw (SH138's rule). Since SH146 this is specifically an
  // instance that says "short" and offers no cursor — the projection cannot finish the walk,
  // so it refuses rather than writing holes.
  const ctx = ctxWith(server({ remote: { "post:Blog/blog/hello": REMOTE_DOC }, truncated: true }));

  await assert.rejects(() => contentPull.run({}, [], ctx), /truncated post list/);

  const allowed = ctxWith(server({ remote: { "post:Blog/blog/hello": REMOTE_DOC }, truncated: true }));
  await contentPull.run({ allowPartial: true }, [], allowed);
  assert.match(allowed.fs.files.get(BASE_PATH), /"partial": true/);
});

test("a post list past the page size is walked to the end, not refused (SH146)", async () => {
  // Three posts, one per page: the projection is complete and the snapshot is *not* partial,
  // which is the whole point — before SH146 this site could not be projected at all.
  const remote = {};
  for (const slug of ["alpha", "beta", "gamma"]) {
    remote[`post:Blog/blog/${slug}`] = { ...REMOTE_DOC, address: `post:Blog/blog/${slug}` };
  }
  const fetchImpl = server({ remote, pageSize: 1 });
  const ctx = ctxWith(fetchImpl);

  await contentPull.run({}, [], ctx);

  for (const slug of ["alpha", "beta", "gamma"]) {
    assert.ok(ctx.fs.files.has(`/repo/shio/content/Blog/blog/${slug}.md`), `${slug} projected`);
  }
  assert.doesNotMatch(ctx.fs.files.get(BASE_PATH), /"partial": true/);
  // Three find calls for three pages — the last one reports itself complete, so there is no
  // extra empty read to discover that. Each after the first carries the previous page's
  // cursor: a keyset, never an offset.
  const finds = fetchImpl.calls.filter((call) => call.url.includes("/agent/find"));
  assert.equal(finds.length, 3);
  assert.match(finds[1].url, /after=post%3ABlog%2Fblog%2Falpha/);
  assert.match(finds[2].url, /after=post%3ABlog%2Fblog%2Fbeta/);
});

test("a cursor that does not advance is a stall, not an endless walk (SH146)", async () => {
  // A server that repeats a cursor would page forever over the same rows. The client stops and
  // reports the result short, which routes into the same refusal a real cut gets.
  let calls = 0;
  const fetchImpl = async (url) => {
    const target = String(url);
    if (target.includes("/agent/find")) {
      calls += 1;
      return json({
        count: 1,
        truncated: true,
        cursor: "post:Blog/blog/stuck",
        results: [{ address: "post:Blog/blog/stuck", type: "Article" }],
      });
    }
    if (target.includes("/agent/read")) return json([]);
    if (target.includes("/api/v2/post-type")) return json(DEFS);
    return json({});
  };
  fetchImpl.calls = [];
  const ctx = ctxWith(fetchImpl);

  await assert.rejects(() => contentPull.run({}, [], ctx), /truncated post list/);
  assert.equal(calls, 2, "one page, then the repeat that ends the walk");
});

// --- push ---

test("push --content applies one desired-state document per site", async () => {
  const fetchImpl = server({ remote: {} });
  const ctx = ctxWith(fetchImpl, {
    [FILE]: `---\nsite: Blog\nurl: /blog/hello\ntype: Article\nfolder: /blog\nbody: body\nfields:\n  headline: Hello\n---\n<p>First</p>\n`,
  });

  await contentPush.run({}, [], ctx);

  const applied = fetchImpl.calls.find((call) => call.url.includes("/agent/apply"));
  assert.ok(applied, "the file tree should reach /agent/apply");
  assert.equal(applied.body.site, "Blog");
  assert.deepEqual(
    applied.body.folders.map((folder) => folder.path),
    ["/", "/blog"],
  );
  assert.deepEqual(applied.body.posts[0], {
    url: "/blog/hello",
    type: "Article",
    folder: "/blog",
    data: { headline: "Hello", body: "<p>First</p>" },
  });
});

test("push --content --check plans only and exits non-zero on drift", async () => {
  const fetchImpl = server({ remote: {}, changed: 2 });
  const ctx = ctxWith(fetchImpl, {
    [FILE]: `---\nsite: Blog\nurl: /blog/hello\ntype: Article\nfolder: /blog\nfields:\n  headline: Hello\n---\n`,
  });

  const result = await contentPush.run({ check: true }, [], ctx);

  assert.equal(result.exitCode, 1);
  assert.ok(fetchImpl.calls.every((call) => !call.url.includes("/agent/apply")));
  assert.match(ctx.stderr.text, /drift detected/);
});

test("push --content refuses to overwrite a post that changed on both sides", async () => {
  const fetchImpl = server({
    remote: {
      "post:Blog/blog/hello": { ...REMOTE_DOC, data: { headline: "Theirs", body: "<p>First</p>" } },
    },
  });
  const ctx = ctxWith(fetchImpl, {
    [FILE]: `---\nsite: Blog\nurl: /blog/hello\ntype: Article\nfolder: /blog\nbody: body\nfields:\n  headline: Ours\n---\n<p>First</p>\n`,
    [BASE_PATH]: baseSnapshot({ headline: "Hello", body: "<p>First</p>" }),
  });

  await assert.rejects(() => contentPush.run({}, [], ctx), /changed both here and on the server/);
  assert.ok(fetchImpl.calls.every((call) => !call.url.includes("/agent/apply")));
});

test("push --content declares the server's values for a post it did not change", async () => {
  // The no-revert rule: the console edit stays, and the post stays inside the document's
  // declared set so a --prune cannot delete it.
  const fetchImpl = server({
    remote: {
      "post:Blog/blog/hello": {
        ...REMOTE_DOC,
        data: { headline: "Edited in console", body: "<p>First</p>" },
      },
    },
  });
  const ctx = ctxWith(fetchImpl, {
    [FILE]: `---\nsite: Blog\nurl: /blog/hello\ntype: Article\nfolder: /blog\nbody: body\nfields:\n  headline: Hello\n---\n<p>First</p>\n`,
    [BASE_PATH]: baseSnapshot({ headline: "Hello", body: "<p>First</p>" }),
  });

  const result = await contentPush.run({}, [], ctx);

  const applied = fetchImpl.calls.find((call) => call.url.includes("/agent/apply"));
  assert.equal(applied.body.posts[0].data.headline, "Edited in console");
  assert.deepEqual(result.behind, ["post:Blog/blog/hello"]);
  assert.match(ctx.stdout.text, /server is ahead/);
});

test("push --content --prune refuses a partial snapshot", async () => {
  const ctx = ctxWith(server({ remote: {} }), {
    [FILE]: `---\nsite: Blog\nurl: /blog/hello\ntype: Article\nfolder: /blog\nfields:\n  headline: Hello\n---\n`,
    [BASE_PATH]: baseSnapshot({ headline: "Hello" }, { partial: true }),
  });

  await assert.rejects(() => contentPush.run({ prune: true }, [], ctx), /marked partial/);
});

test("push --content says so instead of pushing an empty tree", async () => {
  const ctx = ctxWith(server({ remote: {} }));

  await assert.rejects(() => contentPush.run({}, [], ctx), /no content files under/);
});

// --- a large tree splits (SH199) ---

/** `n` content files in one site, enough to push a document past a small ceiling. */
function manyFiles(n) {
  const seed = {};
  for (let index = 0; index < n; index += 1) {
    seed[`/repo/shio/content/Blog/blog/p${index}.md`] =
      `---\nsite: Blog\nurl: /blog/p${index}\ntype: Article\nfolder: /blog\nfields:\n  headline: p${index}\n---\n`;
  }
  return seed;
}

test("push --content splits a tree past the op ceiling into several applies", async () => {
  const fetchImpl = server({ remote: {} });
  const ctx = ctxWith(fetchImpl, manyFiles(9));

  const result = await contentPush.run({ maxOps: 5 }, [], ctx);

  const applies = fetchImpl.calls.filter((call) => call.url.includes("/agent/apply"));
  assert.ok(applies.length > 1, `expected several applies, got ${applies.length}`);
  // Every part fits, and between them they declare every post exactly once — the union is what
  // one document would have declared, because an upsert is independent and idempotent.
  const urls = applies.flatMap((call) => call.body.posts.map((post) => post.url)).sort();
  assert.equal(urls.length, 9);
  assert.equal(new Set(urls).size, 9);
  assert.ok(applies.every((call) => call.body.posts.length + call.body.folders.length <= 5),
    "SH199: every part is under the ceiling. A split that produced one oversized document has "
      + "done the work and still fails at the wall it was splitting to stay under");
  assert.ok(applies.every((call) => call.body.prune === false));
  assert.equal(result.documents.length, applies.length);
});

test("push --content --prune refuses a tree too large to push whole", async () => {
  // Prune deletes what a document does not declare, so each part of a split would delete what
  // the other parts declare — and every document declares the site root, so no subtree split
  // narrows it either. A refusal that says why beats a split that means something else.
  const ctx = ctxWith(server({ remote: {} }), manyFiles(9));

  await assert.rejects(
    () => contentPush.run({ prune: true, maxOps: 5 }, [], ctx),
    /a pruning push cannot be split/,
  );
});

// --- assets project too (SH145) ---

/** A post-type with a File field, and a doc that references one. */
const ASSET_DEFS = [
  {
    name: "Article",
    fields: [
      { name: "headline", widget: "Text", ordinal: 1 },
      { name: "body", widget: "HTML Editor", ordinal: 2 },
      { name: "hero", widget: "File", ordinal: 3 },
    ],
  },
];

function assetServer({ remote = {}, serverAssets = [], serverFolders = {}, serverBytes = {} } = {}) {
  const base = server({ remote, serverAssets, serverFolders, serverBytes });
  const wrapped = async (url, init) => {
    // The post-type list has to carry the File field for this group, and `server()`'s DEFS
    // deliberately does not — every other test would then pay for a field it never uses.
    if (String(url).includes("/api/v2/post-type")) {
      return {
        ok: true,
        status: 200,
        headers: { get: () => null },
        async text() {
          return JSON.stringify(ASSET_DEFS);
        },
        async json() {
          return ASSET_DEFS;
        },
      };
    }
    return base(url, init);
  };
  wrapped.calls = base.calls;
  wrapped.uploads = base.uploads;
  wrapped.replacements = base.replacements;
  return wrapped;
}

test("pull --content downloads the bytes and projects a File field as a path", async () => {
  // The gap SH145 closes: a projected `File` field read as a bare UUID, so the tree could not be
  // moved to another instance, reviewed in a diff, or read as a complete description of a site.
  const fetchImpl = assetServer({
    remote: {
      "post:Blog/blog/hello": { ...REMOTE_DOC, data: { headline: "Hello", hero: "file-1" } },
    },
    serverFolders: { Blog: ["/", "/blog", "/media"] },
    serverAssets: [
      { id: "file-1", fileName: "logo.png", folderId: "Blog:/media", siteName: "Blog" },
    ],
  });
  const ctx = ctxWith(fetchImpl, {
    [FILE]: `---\nsite: Blog\nurl: /blog/hello\ntype: Article\nfolder: /blog\nfields:\n  headline: Hello\n  hero: seed\n---\n`,
  });

  const result = await contentPull.run({ theirs: true }, [], ctx);

  assert.deepEqual(result.assets, ["Blog/media/logo.png"]);
  assert.equal(
    new TextDecoder().decode(ctx.fs.files.get("/repo/shio/assets/Blog/media/logo.png")),
    "bytes-of-file-1",
  );
  // The path, not the id — the whole point.
  assert.match(ctx.fs.files.get(FILE), /hero: Blog\/media\/logo\.png/);
  assert.doesNotMatch(ctx.fs.files.get(FILE), /file-1/);
});

test("an asset already on disk is not downloaded again", async () => {
  // Still true of `pull`, and since SH210 a gap rather than a rule: bytes used to be immutable
  // because an upload always created a new id-keyed post, and a replace operation exists now. It
  // stays skipped until SH373 teaches the merge about bytes — fetching on a digest mismatch cannot
  // tell a server edit from a local one and would overwrite the local file. Until then the
  // same path is the same asset, and re-fetching would spend a request per image on every pull.
  const fetchImpl = assetServer({
    remote: { "post:Blog/blog/hello": { ...REMOTE_DOC, data: { headline: "Hello", hero: "file-1" } } },
    serverFolders: { Blog: ["/", "/media"] },
    serverAssets: [{ id: "file-1", fileName: "logo.png", folderId: "Blog:/media", siteName: "Blog" }],
  });
  const ctx = ctxWith(fetchImpl, {
    "/repo/shio/assets/Blog/media/logo.png": "already here",
  });

  const result = await contentPull.run({}, [], ctx);

  assert.deepEqual(result.assets, []);
  assert.equal(ctx.fs.files.get("/repo/shio/assets/Blog/media/logo.png"), "already here");
  assert.ok(fetchImpl.calls.every((call) => !/staticfile\/file-1$/.test(call.url)));
});

test("push --content uploads an asset the server does not have, and sends its id", async () => {
  const fetchImpl = assetServer({
    remote: {},
    serverFolders: { Blog: ["/", "/blog", "/media"] },
    serverAssets: [],
  });
  const ctx = ctxWith(fetchImpl, {
    [FILE]: `---\nsite: Blog\nurl: /blog/hello\ntype: Article\nfolder: /blog\nfields:\n  headline: Hello\n  hero: Blog/media/logo.png\n---\n`,
    "/repo/shio/assets/Blog/media/logo.png": "the bytes",
  });

  await contentPush.run({}, [], ctx);

  assert.equal(fetchImpl.uploads.length, 1);
  const applied = fetchImpl.calls.find((call) => call.url.includes("/agent/apply"));
  // The path became an id at the last moment before the wire, and nothing upstream saw one.
  assert.equal(applied.body.posts[0].data.hero, "uploaded-1");
  assert.match(ctx.stdout.text, /1 asset\(s\) uploaded/);
});

test("push --content reuses an asset the server already has, so a second push uploads nothing", async () => {
  // Idempotent by (site, folder, file name) against the instance's own inventory — not against
  // a remembered id — so a fresh clone with no base snapshot resolves exactly as an old one.
  const fetchImpl = assetServer({
    remote: {},
    serverFolders: { Blog: ["/", "/blog", "/media"] },
    serverAssets: [
      { id: "file-1", fileName: "logo.png", folderId: "Blog:/media", siteName: "Blog", size: 9 },
    ],
    serverBytes: { "file-1": "the bytes" },
  });
  const ctx = ctxWith(fetchImpl, {
    [FILE]: `---\nsite: Blog\nurl: /blog/hello\ntype: Article\nfolder: /blog\nfields:\n  headline: Hello\n  hero: Blog/media/logo.png\n---\n`,
    "/repo/shio/assets/Blog/media/logo.png": "the bytes",
  });

  await contentPush.run({}, [], ctx);

  assert.equal(fetchImpl.uploads.length, 0);
  // SH210: unchanged is now a *comparison* that came out equal, not a path that was skipped. It
  // cost one digest and no bytes — which is the point, because an unchanged asset is exactly the
  // one whose length matches, so it is exactly the one a byte comparison would have downloaded.
  assert.equal(fetchImpl.replacements.length, 0);
  assert.ok(fetchImpl.calls.every((call) => !/staticfile\/file-1$/.test(call.url)));
  const applied = fetchImpl.calls.find((call) => call.url.includes("/agent/apply"));
  assert.equal(applied.body.posts[0].data.hero, "file-1");
});

// --- editing an asset's bytes in place (SH210) ---

test("push --content replaces the bytes of an asset whose length changed", async () => {
  // The projection's one silent no-op: idempotence by path used to mean *skip*, so editing the
  // bytes at a path the server already had uploaded nothing and reported success.
  const fetchImpl = assetServer({
    remote: {},
    serverFolders: { Blog: ["/", "/blog", "/media"] },
    serverAssets: [
      { id: "file-1", fileName: "logo.png", folderId: "Blog:/media", siteName: "Blog", size: 4 },
    ],
  });
  const ctx = ctxWith(fetchImpl, {
    [FILE]: `---\nsite: Blog\nurl: /blog/hello\ntype: Article\nfolder: /blog\nfields:\n  headline: Hello\n  hero: Blog/media/logo.png\n---\n`,
    "/repo/shio/assets/Blog/media/logo.png": "the edited bytes",
  });

  await contentPush.run({}, [], ctx);

  assert.deepEqual(
    fetchImpl.replacements.map((row) => row.id),
    ["file-1"],
  );
  // Replaced, not re-uploaded: a second post at the same (folder, file name) would make the
  // asset path stop naming one asset, and the field still resolves to the same id.
  assert.equal(fetchImpl.uploads.length, 0);
  const applied = fetchImpl.calls.find((call) => call.url.includes("/agent/apply"));
  assert.equal(applied.body.posts[0].data.hero, "file-1");
  assert.match(ctx.stdout.text, /1 asset\(s\) replaced/);
  // The cheap branch decided it: the length differed, so no digest was needed to find out.
  assert.ok(fetchImpl.calls.every((call) => !/staticfile\/file-1\/digest$/.test(call.url)));
});

test("push --content asks for a digest when the length is unchanged", async () => {
  // The case size alone cannot see, and the one that made a detector built on size alone
  // unacceptable: re-saving a text asset without changing its length is ordinary.
  const fetchImpl = assetServer({
    remote: {},
    serverFolders: { Blog: ["/", "/blog", "/media"] },
    serverAssets: [
      { id: "file-1", fileName: "logo.svg", folderId: "Blog:/media", siteName: "Blog", size: 5 },
    ],
    serverBytes: { "file-1": "aaaaa" },
  });
  const ctx = ctxWith(fetchImpl, {
    [FILE]: `---\nsite: Blog\nurl: /blog/hello\ntype: Article\nfolder: /blog\nfields:\n  headline: Hello\n  hero: Blog/media/logo.svg\n---\n`,
    "/repo/shio/assets/Blog/media/logo.svg": "aaaab",
  });

  await contentPush.run({}, [], ctx);

  assert.deepEqual(
    fetchImpl.replacements.map((row) => row.id),
    ["file-1"],
  );
  // It asked for exactly one digest, and downloaded nothing.
  assert.equal(
    fetchImpl.calls.filter((call) => /staticfile\/file-1\/digest$/.test(call.url)).length,
    1,
  );
  assert.ok(fetchImpl.calls.every((call) => !/staticfile\/file-1$/.test(call.url)));
});

test("push --content leaves an asset alone when the same-length bytes are identical", async () => {
  const fetchImpl = assetServer({
    remote: {},
    serverFolders: { Blog: ["/", "/blog", "/media"] },
    serverAssets: [
      { id: "file-1", fileName: "logo.svg", folderId: "Blog:/media", siteName: "Blog", size: 5 },
    ],
    serverBytes: { "file-1": "aaaaa" },
  });
  const ctx = ctxWith(fetchImpl, {
    [FILE]: `---\nsite: Blog\nurl: /blog/hello\ntype: Article\nfolder: /blog\nfields:\n  headline: Hello\n  hero: Blog/media/logo.svg\n---\n`,
    "/repo/shio/assets/Blog/media/logo.svg": "aaaaa",
  });

  await contentPush.run({}, [], ctx);

  assert.equal(fetchImpl.replacements.length, 0);
  assert.doesNotMatch(ctx.stdout.text, /replaced/);
});

test("a plan reports a replacement and performs none", async () => {
  const fetchImpl = assetServer({
    remote: {},
    serverFolders: { Blog: ["/", "/blog", "/media"] },
    serverAssets: [
      { id: "file-1", fileName: "logo.png", folderId: "Blog:/media", siteName: "Blog", size: 4 },
    ],
  });
  const ctx = ctxWith(fetchImpl, {
    [FILE]: `---\nsite: Blog\nurl: /blog/hello\ntype: Article\nfolder: /blog\nfields:\n  headline: Hello\n  hero: Blog/media/logo.png\n---\n`,
    "/repo/shio/assets/Blog/media/logo.png": "the edited bytes",
  });

  await contentPush.run({ dryRun: true }, [], ctx);

  assert.equal(fetchImpl.replacements.length, 0);
  assert.match(ctx.stdout.text, /1 asset\(s\) to replace/);
  assert.match(ctx.stdout.text, /Blog\/media\/logo\.png \(would replace\)/);
});

test("an asset the server has and the tree does not is not a refusal", async () => {
  // The missing-bytes refusal exists so a push cannot publish a File field pointing at nothing,
  // and a path the server already holds points at something. A hand-written document naming an
  // existing asset it never pulled is legitimate, so ENOENT here means unchanged.
  const fetchImpl = assetServer({
    remote: {},
    serverFolders: { Blog: ["/", "/blog", "/media"] },
    serverAssets: [
      { id: "file-1", fileName: "logo.png", folderId: "Blog:/media", siteName: "Blog", size: 4 },
    ],
  });
  const ctx = ctxWith(fetchImpl, {
    [FILE]: `---\nsite: Blog\nurl: /blog/hello\ntype: Article\nfolder: /blog\nfields:\n  headline: Hello\n  hero: Blog/media/logo.png\n---\n`,
  });

  await contentPush.run({}, [], ctx);

  assert.equal(fetchImpl.replacements.length, 0);
  const applied = fetchImpl.calls.find((call) => call.url.includes("/agent/apply"));
  assert.equal(applied.body.posts[0].data.hero, "file-1");
});

// --- asset bytes join the three-way merge (SH373) ---

/** A base snapshot whose only content is the asset agreements. */
const assetBase = (assets) => JSON.stringify(
  { version: 3, cursor: null, partial: false, posts: {}, folders: [], notes: {}, assets },
);

/** sha-256 of "bytes", which is what these fixtures put on disk. */
const SHA_BYTES = "277089d91c0bdf4f2e6862ba7e4a07605119431f5d13f726dd352b06f1b206a9";

const ASSET_ITEM = {
  id: "file-1", fileName: "logo.png", folderId: "Blog:/media", siteName: "Blog", size: 5,
};

const POST_WITH_HERO = `---\nsite: Blog\nurl: /blog/hello\ntype: Article\nfolder: /blog\nfields:\n  headline: Hello\n  hero: Blog/media/logo.png\n---\n`;

test("SH373: a server-side asset edit reaches the tree", async () => {
  // The symptom: `pull` skipped a path already on disk, because a File post's bytes used to be
  // immutable. SH210 removed that premise and a curator swapping an image produced a change no
  // pull would ever project.
  const fetchImpl = assetServer({
    remote: { "post:Blog/blog/hello": { ...REMOTE_DOC, data: { headline: "Hello", hero: "file-1" } } },
    serverFolders: { Blog: ["/", "/media"] },
    serverAssets: [ASSET_ITEM],
    serverBytes: { "file-1": "newer" },
  });
  const ctx = ctxWith(fetchImpl, {
    [FILE]: POST_WITH_HERO,
    "/repo/shio/assets/Blog/media/logo.png": "bytes",
    [BASE_PATH]: assetBase({ "Blog/media/logo.png": SHA_BYTES }),
  });

  const result = await contentPull.run({ theirs: true }, [], ctx);

  assert.deepEqual(result.assets, ["Blog/media/logo.png"]);
  assert.equal(
    new TextDecoder().decode(ctx.fs.files.get("/repo/shio/assets/Blog/media/logo.png")),
    "newer",
  );
});

test("SH373: a locally-edited asset is not overwritten by a pull", async () => {
  // The reason this waited for a merge instead of shipping with SH210: downloading whenever the
  // hashes differ cannot tell a server edit from a local one, so it overwrites the tree's copy.
  const fetchImpl = assetServer({
    remote: { "post:Blog/blog/hello": { ...REMOTE_DOC, data: { headline: "Hello", hero: "file-1" } } },
    serverFolders: { Blog: ["/", "/media"] },
    serverAssets: [ASSET_ITEM],
    serverBytes: { "file-1": "bytes" },
  });
  const ctx = ctxWith(fetchImpl, {
    [FILE]: POST_WITH_HERO,
    "/repo/shio/assets/Blog/media/logo.png": "edited here",
    [BASE_PATH]: assetBase({ "Blog/media/logo.png": SHA_BYTES }),
  });

  const result = await contentPull.run({ theirs: true }, [], ctx);

  assert.deepEqual(result.assets, []);
  assert.equal(ctx.fs.files.get("/repo/shio/assets/Blog/media/logo.png"), "edited here");
});

test("SH373: both sides editing one asset is a conflict, and the file is left alone", async () => {
  const fetchImpl = assetServer({
    remote: { "post:Blog/blog/hello": { ...REMOTE_DOC, data: { headline: "Hello", hero: "file-1" } } },
    serverFolders: { Blog: ["/", "/media"] },
    serverAssets: [ASSET_ITEM],
    serverBytes: { "file-1": "newer" },
  });
  const ctx = ctxWith(fetchImpl, {
    [FILE]: POST_WITH_HERO,
    "/repo/shio/assets/Blog/media/logo.png": "edited here",
    [BASE_PATH]: assetBase({ "Blog/media/logo.png": SHA_BYTES }),
  });

  const result = await contentPull.run({ theirs: true }, [], ctx);

  assert.deepEqual(result.assetConflicts, ["Blog/media/logo.png"]);
  assert.equal(ctx.fs.files.get("/repo/shio/assets/Blog/media/logo.png"), "edited here");
  assert.match(ctx.stderr.text, /asset\(s\) changed both here and on the server/);
});

test("SH373: a push does not revert a server-side asset edit", async () => {
  // The defect SH210 introduced and this closes: replacing whenever the bytes differ reverts a
  // curator's edit whenever the tree did not touch it, and "a push never silently reverts a
  // console edit" is the rule SH89 exists for.
  const fetchImpl = assetServer({
    remote: {},
    serverFolders: { Blog: ["/", "/blog", "/media"] },
    serverAssets: [ASSET_ITEM],
    serverBytes: { "file-1": "newer" },
  });
  const ctx = ctxWith(fetchImpl, {
    [FILE]: POST_WITH_HERO,
    "/repo/shio/assets/Blog/media/logo.png": "bytes",
    [BASE_PATH]: assetBase({ "Blog/media/logo.png": SHA_BYTES }),
  });

  const result = await contentPush.run({}, [], ctx);

  assert.deepEqual(fetchImpl.replacements, []);
  assert.deepEqual(result.behindAssets, ["Blog/media/logo.png"]);
  assert.match(ctx.stdout.text, /changed on the server and not here/);
});

test("SH373: a push still replaces the asset the tree edited", async () => {
  const fetchImpl = assetServer({
    remote: {},
    serverFolders: { Blog: ["/", "/blog", "/media"] },
    serverAssets: [ASSET_ITEM],
    serverBytes: { "file-1": "bytes" },
  });
  const ctx = ctxWith(fetchImpl, {
    [FILE]: POST_WITH_HERO,
    "/repo/shio/assets/Blog/media/logo.png": "edited here",
    [BASE_PATH]: assetBase({ "Blog/media/logo.png": SHA_BYTES }),
  });

  await contentPush.run({}, [], ctx);

  assert.deepEqual(fetchImpl.replacements.map((row) => row.id), ["file-1"]);
});

test("SH373: a push refuses an asset both sides edited", async () => {
  const fetchImpl = assetServer({
    remote: {},
    serverFolders: { Blog: ["/", "/blog", "/media"] },
    serverAssets: [ASSET_ITEM],
    serverBytes: { "file-1": "newer" },
  });
  const ctx = ctxWith(fetchImpl, {
    [FILE]: POST_WITH_HERO,
    "/repo/shio/assets/Blog/media/logo.png": "edited here too",
    [BASE_PATH]: assetBase({ "Blog/media/logo.png": SHA_BYTES }),
  });

  await assert.rejects(
    () => contentPush.run({}, [], ctx),
    /asset\(s\) changed both here and on the server/,
  );
  assert.deepEqual(fetchImpl.replacements, []);
});

test("SH373: with no agreed hash the tree is authoritative, both branches alike", async () => {
  // A push and a pull are not symmetrical about missing evidence, and the two size branches must
  // not disagree either: a differing *length* and differing *bytes of the same length* both mean
  // "the tree is what the server should hold" when nothing has ever been agreed.
  const fetchImpl = assetServer({
    remote: {},
    serverFolders: { Blog: ["/", "/blog", "/media"] },
    serverAssets: [ASSET_ITEM],
    serverBytes: { "file-1": "other" },
  });
  const ctx = ctxWith(fetchImpl, {
    [FILE]: POST_WITH_HERO,
    "/repo/shio/assets/Blog/media/logo.png": "bytes",
  });

  await contentPush.run({}, [], ctx);

  assert.deepEqual(fetchImpl.replacements.map((row) => row.id), ["file-1"]);
});

test("SH373: the agreed hash is recorded, so the next server edit is a change not a conflict", async () => {
  const fetchImpl = assetServer({
    remote: { "post:Blog/blog/hello": { ...REMOTE_DOC, data: { headline: "Hello", hero: "file-1" } } },
    serverFolders: { Blog: ["/", "/media"] },
    serverAssets: [ASSET_ITEM],
    serverBytes: { "file-1": "bytes" },
  });
  const ctx = ctxWith(fetchImpl, {
    [FILE]: POST_WITH_HERO,
    "/repo/shio/assets/Blog/media/logo.png": "bytes",
  });

  await contentPull.run({ theirs: true }, [], ctx);

  assert.deepEqual(JSON.parse(ctx.fs.files.get(BASE_PATH)).assets, {
    "Blog/media/logo.png": SHA_BYTES,
  });
});

test("push --content refuses a File reference whose bytes are not in the tree", async () => {
  // The alternative is a post published with a File field pointing at nothing.
  const fetchImpl = assetServer({
    remote: {},
    serverFolders: { Blog: ["/", "/blog", "/media"] },
    serverAssets: [],
  });
  const ctx = ctxWith(fetchImpl, {
    [FILE]: `---\nsite: Blog\nurl: /blog/hello\ntype: Article\nfolder: /blog\nfields:\n  headline: Hello\n  hero: Blog/media/missing.png\n---\n`,
  });

  await assert.rejects(
    () => contentPush.run({}, [], ctx),
    /name an asset that is not in shio\/assets/,
  );
  assert.equal(fetchImpl.uploads.length, 0);
  assert.ok(fetchImpl.calls.every((call) => !call.url.includes("/agent/apply")));
});

test("a File value that is not a path is an id already, and is left alone", async () => {
  // A reference this tree never projected — the File post lives in a site the run did not read.
  // Rewriting it would be inventing, and it is already the value the server wants.
  const fetchImpl = assetServer({
    remote: {},
    serverFolders: { Blog: ["/", "/blog"] },
    serverAssets: [],
  });
  const ctx = ctxWith(fetchImpl, {
    [FILE]: `---\nsite: Blog\nurl: /blog/hello\ntype: Article\nfolder: /blog\nfields:\n  headline: Hello\n  hero: 11111111-2222-3333-4444-555555555555\n---\n`,
  });

  await contentPush.run({}, [], ctx);

  const applied = fetchImpl.calls.find((call) => call.url.includes("/agent/apply"));
  assert.equal(applied.body.posts[0].data.hero, "11111111-2222-3333-4444-555555555555");
  assert.equal(fetchImpl.uploads.length, 0);
});

// --- the merge base is fingerprints (SH148) ---

test("the base snapshot keeps no copy of any field value", async () => {
  // The whole point: the base was a second copy of the site on disk — and in every clone,
  // once committed to share it — stored so that it could be compared and then discarded.
  const ctx = ctxWith(server({ remote: { "post:Blog/blog/hello": REMOTE_DOC } }));

  await contentPull.run({}, [], ctx);

  const raw = ctx.fs.files.get(BASE_PATH);
  assert.doesNotMatch(raw, /First/, "the body must not be in the snapshot");
  assert.doesNotMatch(raw, /Hello/, "nor any other field value");
  const base = JSON.parse(raw);
  // 3 since SH215: a fingerprint carries the canonical form's byte length beside its hash, which
  // is what turns a collision from a silently-dropped edit into a reported one.
  assert.equal(base.version, 3);
  const entry = base.posts["post:Blog/blog/hello"];
  // The identity stays verbatim, because it is *used* rather than compared: a pull deletes a
  // file by the address the base remembers, and a digest would leave nothing to delete.
  assert.deepEqual(Object.keys(entry).sort(), ["digest", "site", "url"]);
  // `.field` for a data key, `@name` for metadata — the same flattening the merge compares on.
  assert.deepEqual(Object.keys(entry.digest).sort(), [".body", ".headline", "@folder", "@type"]);
  // SH215 — `<16 hex>.<byte length>`. The length is not there to make a collision rarer: compared
  // part by part it *proves* one, which is what turns a dropped edit into a reported conflict.
  assert.ok(Object.values(entry.digest).every((hash) => /^[0-9a-f]{16}\.\d+$/.test(hash)));
  // And still no value anywhere: a length is not a copy of the content.
  assert.doesNotMatch(raw, /Server|First|Hello/,
    "SH148: the merge base is fingerprints, so it holds no field value at all — the version it "
      + "replaced was a second copy of the site on disk, in every clone that committed it");
});

test("SH215: a base-fingerprint collision is reported by name, not dropped", async () => {
  // Forged rather than found: the fixture is the observable state a real 64-bit collision
  // produces — the base's hash for `headline` matches the local value and its length cannot. The
  // old comparison read that as "unchanged" and took the server's value, losing the local edit
  // with nothing said, which is the one outcome a sync must never have.
  const local = {
    site: "Blog",
    url: "/blog/hello",
    type: "Article",
    folder: "/blog",
    data: { headline: "Mine", body: "<p>First</p>" },
  };
  const digest = digestsOf(local);
  const forged = {
    version: 3,
    cursor: null,
    partial: false,
    posts: {
      "post:Blog/blog/hello": {
        site: "Blog",
        url: "/blog/hello",
        digest: {
          ...digest,
          ".headline": `${String(digest[".headline"]).split(".")[0]}.9999`,
        },
      },
    },
    folders: [],
    notes: {},
  };
  const ctx = ctxWith(
    server({ remote: { "post:Blog/blog/hello": { ...REMOTE_DOC, data: { headline: "Theirs", body: "<p>First</p>" } } } }),
    {
      [FILE]: `---
site: Blog
url: /blog/hello
type: Article
folder: /blog
body: body
fields:
  headline: Mine
---
<p>First</p>
`,
      [BASE_PATH]: JSON.stringify(forged),
    },
  );

  const result = await contentPull.run({}, [], ctx);

  assert.equal(result.exitCode, 1);
  assert.match(ctx.stderr.text, /base-fingerprint collision/);
  assert.match(ctx.stderr.text, /post:Blog\/blog\/hello · fields\.headline/);
  // The remedy is not "pick a side": the base cannot describe the value it indexes, so the base
  // is what has to be rebuilt.
  assert.match(ctx.stderr.text, /content-base\.json/);
  // And the local edit is still there, because nothing was written.
  assert.match(ctx.fs.files.get(FILE), /headline: Mine/);
});

test("a v1 snapshot full of values is read forward, and still merges", async () => {
  // Every other test in this file seeds a v1 fixture, so the whole suite exercises the read
  // path; this one states the property. An existing project needs no re-pull and loses nothing.
  const ctx = ctxWith(
    server({
      remote: {
        "post:Blog/blog/hello": { ...REMOTE_DOC, data: { headline: "Hello", body: "<p>Server</p>" } },
      },
    }),
    {
      [FILE]: `---\nsite: Blog\nurl: /blog/hello\ntype: Article\nfolder: /blog\nbody: body\nfields:\n  headline: Mine\n---\n<p>First</p>\n`,
      [BASE_PATH]: baseSnapshot({ headline: "Hello", body: "<p>First</p>" }),
    },
  );

  const result = await contentPull.run({}, [], ctx);

  // Different fields on each side: the merge needed the v1 base to tell which side moved, and
  // got there through the digests derived from it.
  assert.deepEqual(result.written, ["post:Blog/blog/hello (merged)"]);
  assert.match(ctx.fs.files.get(FILE), /headline: Mine/);
  assert.match(ctx.fs.files.get(FILE), /<p>Server<\/p>/);
  assert.equal(JSON.parse(ctx.fs.files.get(BASE_PATH)).version, 3);
});

// --- a rename is a move (SH150) ---

test("push --content sends a renamed post as a move, not a delete plus a create", async () => {
  // The defect: a rename is two file edits, and to a state document that reads as one
  // undeclared post at the old URL plus a new one at the new. The post that came out the
  // other side had a new id and none of the old one's history.
  const fetchImpl = server({ remote: { "post:Blog/blog/hello": REMOTE_DOC } });
  const ctx = ctxWith(fetchImpl, {
    "/repo/shio/content/Blog/blog/greeting.md":
      `---\nsite: Blog\nurl: /blog/greeting\ntype: Article\nfolder: /blog\nbody: body\nfields:\n  headline: Hello\n---\n<p>First</p>\n`,
    [BASE_PATH]: baseSnapshot({ headline: "Hello", body: "<p>First</p>" }),
  });

  const result = await contentPush.run({ prune: true }, [], ctx);

  const applied = fetchImpl.calls.find((call) => call.url.includes("/agent/apply"));
  assert.deepEqual(applied.body.moves, [{ from: "/blog/hello", to: "/blog/greeting" }]);
  assert.deepEqual(result.moves.map((move) => move.from), ["/blog/hello"]);
  // And it is no longer a deletion, which is what stops --prune destroying the post that the
  // move just preserved.
  assert.deepEqual(result.removed, []);
  assert.match(ctx.stdout.text, /move post:Blog\/blog\/hello → \/blog\/greeting \(matched by content\)/);
});

test("push --content refuses when a rename is ambiguous", async () => {
  const fetchImpl = server({ remote: { "post:Blog/blog/hello": REMOTE_DOC } });
  const body = `type: Article\nfolder: /blog\nbody: body\nfields:\n  headline: Hello\n---\n<p>First</p>\n`;
  const ctx = ctxWith(fetchImpl, {
    "/repo/shio/content/Blog/blog/a.md": `---\nsite: Blog\nurl: /blog/a\n${body}`,
    "/repo/shio/content/Blog/blog/b.md": `---\nsite: Blog\nurl: /blog/b\n${body}`,
    [BASE_PATH]: baseSnapshot({ headline: "Hello", body: "<p>First</p>" }),
  });

  await assert.rejects(() => contentPush.run({}, [], ctx), /could be a rename of more than one/);
  assert.ok(fetchImpl.calls.every((call) => !call.url.includes("/agent/apply")));
});

test("push --content warns about a rename it cannot prove", async () => {
  const fetchImpl = server({ remote: { "post:Blog/blog/hello": REMOTE_DOC } });
  const ctx = ctxWith(fetchImpl, {
    "/repo/shio/content/Blog/blog/greeting.md":
      `---\nsite: Blog\nurl: /blog/greeting\ntype: Article\nfolder: /blog\nbody: body\nfields:\n  headline: Rewritten\n---\n<p>All new</p>\n`,
    [BASE_PATH]: baseSnapshot({ headline: "Hello", body: "<p>First</p>" }),
  });

  const result = await contentPush.run({}, [], ctx);

  assert.deepEqual(result.moves, []);
  // Unprovable, so it is not performed — but it *is* said, because the alternative is a
  // delete plus a create that destroys the post's history without mentioning it.
  assert.match(ctx.stdout.text, /looks like it could be post:Blog\/blog\/hello renamed/);
  assert.match(ctx.stdout.text, /movedFrom: \/blog\/hello/);
});

// --- accepting a delete-plus-create (SH214) ---

/** The tree that produces one unprovable rename: a vanished post and a rewritten new one. */
const UNPROVABLE = {
  "/repo/shio/content/Blog/blog/greeting.md":
    `---
site: Blog
url: /blog/greeting
type: Article
folder: /blog
body: body
fields:
  headline: Rewritten
---
<p>All new</p>
`,
  [BASE_PATH]: baseSnapshot({ headline: "Hello", body: "<p>First</p>" }),
};

const BASELINE_PATH = "/repo/shio/verify-baseline.json";

const acceptedMoves = (moves) =>
  JSON.stringify({ version: 1, accepted: [], acceptedMoves: moves });

test("--accept records the unprovable rename and pushes nothing", async () => {
  // Accept-and-stop, the way `verify --accept` works: accepting is a judgement about a
  // destructive operation, and folding it into the run that performs it would mean the judgement
  // and its consequence were never separately reviewable.
  const fetchImpl = server({ remote: { "post:Blog/blog/hello": REMOTE_DOC } });
  const ctx = ctxWith(fetchImpl, { ...UNPROVABLE });

  const result = await contentPush.run({ accept: true, reason: "the old page was retired" }, [], ctx);

  assert.equal(result.written, 1);
  assert.ok(fetchImpl.calls.every((call) => !call.url.includes("/agent/apply")));
  const written = JSON.parse(ctx.fs.files.get(BASELINE_PATH));
  assert.deepEqual(written.acceptedMoves, [
    { from: "post:Blog/blog/hello", to: "post:Blog/blog/greeting", reason: "the old page was retired" },
  ], "SH214: the acceptance is recorded WITH its reason and the push does not happen. Folding the "
    + "judgement into the run that performs it is what makes a destructive operation and the "
    + "decision to allow it un-reviewable separately");
  // `verify`'s own list is untouched — one file, two producers, neither erasing the other.
  assert.deepEqual(written.accepted, []);
  assert.match(ctx.stdout.text, /Nothing was pushed/);
});

test("an accepted delete-plus-create is not warned about, and is counted", async () => {
  const fetchImpl = server({ remote: { "post:Blog/blog/hello": REMOTE_DOC } });
  const ctx = ctxWith(fetchImpl, {
    ...UNPROVABLE,
    [BASELINE_PATH]: acceptedMoves([
      { from: "post:Blog/blog/hello", to: "post:Blog/blog/greeting", reason: "retired" },
    ]),
  });

  await contentPush.run({}, [], ctx);

  assert.doesNotMatch(ctx.stdout.text, /looks like it could be/);
  // Counted, never silent: "no warnings" and "warnings somebody forgave" must not read the same.
  assert.match(ctx.stdout.text, /1 delete-plus-create\(s\) accepted/);
  assert.match(ctx.stdout.text, /retired/);
});

test("an acceptance is spent by the push that uses it", async () => {
  // Not SH142's staleness rule, and the difference is the whole design: a probable move is
  // transient, so an entry would be stale immediately after the push that honoured it and the
  // next run would fail for doing exactly what was asked. Consuming it is the stronger form —
  // nothing can outlive its event, so there is nothing left to rot.
  const fetchImpl = server({ remote: { "post:Blog/blog/hello": REMOTE_DOC } });
  const ctx = ctxWith(fetchImpl, {
    ...UNPROVABLE,
    [BASELINE_PATH]: acceptedMoves([
      { from: "post:Blog/blog/hello", to: "post:Blog/blog/greeting" },
      { from: "post:Blog/other", to: "post:Blog/another" },
    ]),
  });

  await contentPush.run({}, [], ctx);

  const written = JSON.parse(ctx.fs.files.get(BASELINE_PATH));
  assert.deepEqual(written.acceptedMoves, [{ from: "post:Blog/other", to: "post:Blog/another" }]);
  assert.match(ctx.stdout.text, /1 acceptance\(s\) spent and removed/);
});

test("a plan spends nothing, because a plan writes nothing", async () => {
  const fetchImpl = server({ remote: { "post:Blog/blog/hello": REMOTE_DOC } });
  const before = acceptedMoves([
    { from: "post:Blog/blog/hello", to: "post:Blog/blog/greeting" },
  ]);
  const ctx = ctxWith(fetchImpl, { ...UNPROVABLE, [BASELINE_PATH]: before });

  await contentPush.run({ check: true }, [], ctx);

  assert.equal(ctx.fs.files.get(BASELINE_PATH), before);
  assert.doesNotMatch(ctx.stdout.text, /spent and removed/);
});

test("an acceptance is keyed on both addresses, so a different arrival is still warned about", async () => {
  // The one thing §X.17 said to get right: accepting "this deletion is fine" alone would silence
  // the warning for a different new post arriving later, which is the case the warning exists for.
  const fetchImpl = server({ remote: { "post:Blog/blog/hello": REMOTE_DOC } });
  const ctx = ctxWith(fetchImpl, {
    ...UNPROVABLE,
    [BASELINE_PATH]: acceptedMoves([
      { from: "post:Blog/blog/hello", to: "post:Blog/blog/something-else" },
    ]),
  });

  await contentPush.run({}, [], ctx);

  assert.match(ctx.stdout.text, /looks like it could be post:Blog\/blog\/hello renamed/);
  assert.doesNotMatch(ctx.stdout.text, /accepted in/);
});

test("an explicit --baseline that is not there is a mistake, not an empty acceptance", async () => {
  const fetchImpl = server({ remote: { "post:Blog/blog/hello": REMOTE_DOC } });
  const ctx = ctxWith(fetchImpl, { ...UNPROVABLE });

  await assert.rejects(
    () => contentPush.run({ baseline: "shio/nope.json" }, [], ctx),
    /no baseline at shio\/nope\.json/,
  );
});

test("push --content moves a post whose folder changed, which used to do nothing", async () => {
  const fetchImpl = server({ remote: { "post:Blog/blog/hello": REMOTE_DOC } });
  const ctx = ctxWith(fetchImpl, {
    [FILE]: `---\nsite: Blog\nurl: /blog/hello\ntype: Article\nfolder: /archive\nbody: body\nfields:\n  headline: Hello\n---\n<p>First</p>\n`,
    [BASE_PATH]: baseSnapshot({ headline: "Hello", body: "<p>First</p>" }),
  });

  await contentPush.run({}, [], ctx);

  const applied = fetchImpl.calls.find((call) => call.url.includes("/agent/apply"));
  assert.deepEqual(applied.body.moves, [{ from: "/blog/hello", folder: "/archive" }]);
});

// --- folders as files (SH149) ---

test("push --content declares an empty folder from its marker alone", async () => {
  // The gap SH149 closes: before this a folder existed in the projection only because a post
  // named it, so "scaffold the structure, then fill it" had to leave the file tree entirely.
  const fetchImpl = server({ remote: {} });
  const ctx = ctxWith(fetchImpl, {
    [markerPath("Blog", "/archive")]: markerFile("Blog", "/archive"),
  });

  await contentPush.run({}, [], ctx);

  const applied = fetchImpl.calls.find((call) => call.url.includes("/agent/apply"));
  assert.ok(applied, "a markers-only tree is a real push, not an empty one");
  assert.equal(applied.body.site, "Blog");
  assert.deepEqual(
    applied.body.folders.map((folder) => folder.path),
    ["/", "/archive"],
    "SH149: a folder exists because its marker does, not because a post named it — before this, "
      + "scaffolding a structure and then filling it had to leave the file tree entirely",
  );
  assert.deepEqual(applied.body.posts, []);
});

// --- renaming a folder's directory is a move, not a delete plus a create (SH211) ---

/** A base snapshot holding folders and no posts — enough for the folder half of the merge. */
const folderBase = (folders) =>
  JSON.stringify({ version: 1, cursor: null, partial: false, posts: {}, folders, notes: {} });

test("push --content sends a renamed folder as a move, not a delete plus a create", async () => {
  // The defect: `--prune` deleted the old folder, and a folder delete takes every post beneath
  // it — SH150's failure one level up, where the loss is a section rather than one post.
  const fetchImpl = server({ remote: {} });
  const ctx = ctxWith(fetchImpl, {
    [BASE_PATH]: folderBase([{ site: "Blog", folder: "/news" }]),
    [markerPath("Blog", "/updates")]: markerFile("Blog", "/updates"),
  });

  await contentPush.run({ prune: true }, [], ctx);

  const applied = fetchImpl.calls.find((call) => call.url.includes("/agent/apply"));
  assert.deepEqual(applied.body.folders, [
    { path: "/" },
    { path: "/updates", movedFrom: "/news" },
  ]);
  assert.match(ctx.stdout.text, /Blog\/news moves to \/updates/);
  assert.match(ctx.stdout.text, /the pages inside keep their URLs/);
});

test("a folder rename that cannot be told apart stops a pruning push", async () => {
  // Two sections gone and two arrived: either pairing is plausible and moving the wrong one
  // moves the wrong pages. With --prune the folders that left are deleted and a folder delete
  // takes every post beneath it, so this is the case worth stopping.
  const fetchImpl = server({ remote: {} });
  const ctx = ctxWith(fetchImpl, {
    [BASE_PATH]: folderBase([
      { site: "Blog", folder: "/news" },
      { site: "Blog", folder: "/press" },
    ]),
    [markerPath("Blog", "/updates")]: markerFile("Blog", "/updates"),
    [markerPath("Blog", "/releases")]: markerFile("Blog", "/releases"),
  });

  await assert.rejects(
    () => contentPush.run({ prune: true }, [], ctx),
    /folder rename\(s\) cannot be told apart/,
  );
  assert.ok(fetchImpl.calls.every((call) => !call.url.includes("/agent/apply")));
});

test("the same ambiguity without --prune is said out loud and pushed anyway", async () => {
  // Nothing is being deleted, so there is nothing to protect the author from — and refusing
  // would block the legitimate case (a section removed, two added) with no way to declare that
  // neither is a rename.
  const fetchImpl = server({ remote: {} });
  const ctx = ctxWith(fetchImpl, {
    [BASE_PATH]: folderBase([
      { site: "Blog", folder: "/news" },
      { site: "Blog", folder: "/press" },
    ]),
    [markerPath("Blog", "/updates")]: markerFile("Blog", "/updates"),
    [markerPath("Blog", "/releases")]: markerFile("Blog", "/releases"),
  });

  await contentPush.run({}, [], ctx);

  assert.match(ctx.stdout.text, /could be a rename and cannot be proved/);
  assert.match(ctx.stdout.text, /movedFrom: <the old folder path>/);
  const applied = fetchImpl.calls.find((call) => call.url.includes("/agent/apply"));
  assert.deepEqual(applied.body.folders.filter((folder) => folder.movedFrom), []);
});

test("a marker naming a folder the tree still declares is refused whatever the flags", async () => {
  // A contradiction rather than an unproved guess: two markers, one claiming to be the new home
  // of a folder the other still declares. No reading of the pair is safe, so --force is not an
  // answer and --prune is not the trigger.
  const fetchImpl = server({ remote: {} });
  const ctx = ctxWith(fetchImpl, {
    [BASE_PATH]: folderBase([{ site: "Blog", folder: "/news" }]),
    [markerPath("Blog", "/news")]: markerFile("Blog", "/news"),
    [markerPath("Blog", "/updates")]:
      "---\nsite: Blog\nfolder: /updates\nmovedFrom: /news\n---\n",
  });

  await assert.rejects(
    () => contentPush.run({ force: true }, [], ctx),
    /still declares/,
  );
});

test("`movedFrom:` in a marker decides a rename the tree cannot prove", async () => {
  const fetchImpl = server({ remote: {} });
  const ctx = ctxWith(fetchImpl, {
    [BASE_PATH]: folderBase([
      { site: "Blog", folder: "/news" },
      { site: "Blog", folder: "/press" },
    ]),
    [markerPath("Blog", "/updates")]:
      "---\nsite: Blog\nfolder: /updates\nmovedFrom: /news\n---\n",
    [markerPath("Blog", "/releases")]:
      "---\nsite: Blog\nfolder: /releases\nmovedFrom: /press\n---\n",
  });

  await contentPush.run({}, [], ctx);

  const applied = fetchImpl.calls.find((call) => call.url.includes("/agent/apply"));
  assert.deepEqual(applied.body.folders, [
    { path: "/" },
    { path: "/releases", movedFrom: "/press" },
    { path: "/updates", movedFrom: "/news" },
  ]);
});

test("a folder that only left the tree is still a deletion", async () => {
  // The rename inference must not turn every removal into a move: with nothing arriving there
  // is no pair at all, so --prune does what it has always done and nothing is reported.
  const fetchImpl = server({ remote: {} });
  const ctx = ctxWith(fetchImpl, {
    [BASE_PATH]: folderBase([{ site: "Blog", folder: "/gone" }]),
    [markerPath("Blog", "/gone")]: markerFile("Blog", "/gone"),
  });

  await contentPush.run({ prune: true }, [], ctx);

  const applied = fetchImpl.calls.find((call) => call.url.includes("/agent/apply"));
  assert.deepEqual(applied.body.folders, [{ path: "/" }, { path: "/gone" }]);
  assert.doesNotMatch(ctx.stdout.text, /moves to/);
});

test("--force pushes past an unprovable folder rename, and moves nothing", async () => {
  const fetchImpl = server({ remote: {} });
  const ctx = ctxWith(fetchImpl, {
    [BASE_PATH]: folderBase([
      { site: "Blog", folder: "/news" },
      { site: "Blog", folder: "/press" },
    ]),
    [markerPath("Blog", "/updates")]: markerFile("Blog", "/updates"),
    [markerPath("Blog", "/releases")]: markerFile("Blog", "/releases"),
  });

  await contentPush.run({ prune: true, force: true }, [], ctx);

  const applied = fetchImpl.calls.find((call) => call.url.includes("/agent/apply"));
  assert.ok(applied, "the push went through");
  assert.deepEqual(applied.body.folders.filter((folder) => folder.movedFrom), []);
});

test("push --content refuses a tree with neither posts nor markers", async () => {
  // The refusal is unchanged; what changed is what counts as content. An empty directory is
  // far more often the wrong --content-dir than a deliberate wipe, and with --prune it would
  // be a request to delete a section.
  const ctx = ctxWith(server({ remote: {} }), {});

  await assert.rejects(() => contentPush.run({}, [], ctx), /no content files under/);
});

test("pull --content writes a marker for a folder no post declares", async () => {
  const ctx = ctxWith(
    server({
      remote: { "post:Blog/blog/hello": REMOTE_DOC },
      serverFolders: { Blog: ["/", "/blog", "/empty"] },
    }),
    { [BASE_PATH]: baseSnapshot({ headline: "Hello", body: "<p>First</p>" }) },
  );

  const result = await contentPull.run({}, [], ctx);

  // /empty gets a file; / and /blog do not, because the post's own `folder:` already says so.
  assert.deepEqual(result.folders.written, ["folder:Blog/empty"]);
  assert.equal(ctx.fs.files.get(markerPath("Blog", "/empty")), markerFile("Blog", "/empty"));
  assert.equal(ctx.fs.files.has(markerPath("Blog", "/blog")), false);
  assert.equal(ctx.fs.files.has(markerPath("Blog", "/")), false);
  // Both are still *agreed*, so the next pull cannot read the unwritten ones as deletions.
  const base = JSON.parse(ctx.fs.files.get(BASE_PATH));
  assert.deepEqual(base.folders.map((row) => row.folder), ["/", "/blog", "/empty"]);
});

test("pull --content deletes a marker whose folder is gone, and keeps one never pushed", async () => {
  const ctx = ctxWith(
    server({ remote: {}, serverFolders: { Blog: [] } }),
    {
      [markerPath("Blog", "/gone")]: markerFile("Blog", "/gone"),
      [markerPath("Blog", "/brand-new")]: markerFile("Blog", "/brand-new"),
      [BASE_PATH]: `${JSON.stringify({
        version: 1,
        cursor: "2026-07-26T09:00:00Z|aaa",
        partial: false,
        posts: {},
        folders: [{ site: "Blog", folder: "/gone" }],
      }, null, 2)}\n`,
    },
  );

  const result = await contentPull.run({}, [], ctx);

  // The only thing that tells these two apart is the base: /gone was agreed on and is now
  // absent (a deletion), /brand-new never was (a local scaffold the push has yet to make real).
  assert.deepEqual(result.folders.deleted, ["folder:Blog/gone"]);
  assert.deepEqual(result.folders.kept, ["folder:Blog/brand-new"]);
  assert.equal(ctx.fs.files.has(markerPath("Blog", "/gone")), false);
  assert.equal(ctx.fs.files.has(markerPath("Blog", "/brand-new")), true);
});

test("pull --content leaves an agreed marker byte-identical", async () => {
  // Nothing to compare means nothing to write. Rewriting it would make every pull report a
  // change nobody made, which is the property the whole format is built around (P5).
  const ctx = ctxWith(
    server({ remote: {}, serverFolders: { Blog: ["/news"] } }),
    {
      [markerPath("Blog", "/news")]: markerFile("Blog", "/news"),
      [BASE_PATH]: `${JSON.stringify({
        version: 1,
        cursor: "2026-07-26T09:00:00Z|aaa",
        partial: false,
        posts: {},
        folders: [{ site: "Blog", folder: "/news" }],
      }, null, 2)}\n`,
    },
  );

  const result = await contentPull.run({}, [], ctx);

  // `conflicts` since SH407 — a pair of chains a case-insensitive disk cannot tell apart.
  assert.deepEqual(result.folders, { written: [], deleted: [], kept: [], conflicts: [] });
  assert.equal(ctx.fs.files.get(markerPath("Blog", "/news")), markerFile("Blog", "/news"));
});

// --- a template projects as files (SH191) ---

const REGION_HTML = '<div class="cards">\n  {{#each this.posts}}<b>{{title}}</b>{{/each}}\n</div>\n';
const REGION_JS = "document.querySelectorAll('.cards').forEach((n) => n.classList.add('ready'));\n";
const REGION_REMOTE = {
  address: "post:Blog/region/content-list",
  type: "Region",
  state: "DRAFT",
  folder: "/Regions",
  data: { HTML: REGION_HTML, JAVASCRIPT: REGION_JS },
};
const REGION_MD = "/repo/shio/content/Blog/region/content-list.md";
const REGION_HTML_FILE = "/repo/shio/content/Blog/region/content-list.HTML.html";
const REGION_JS_FILE = "/repo/shio/content/Blog/region/content-list.JAVASCRIPT.js";

test("pull --content writes a template as a real .html beside the post file", async () => {
  const ctx = ctxWith(server({ remote: { "post:Blog/region/content-list": REGION_REMOTE } }));

  await contentPull.run({}, [], ctx);

  // Verbatim, so the file an editor opens is the program and `Grep` reaches it — which is the
  // whole of law P4's claim, and what escaped JSON in YAML frontmatter defeated.
  assert.equal(ctx.fs.files.get(REGION_HTML_FILE), REGION_HTML,
    "SH191: a template projects as a real .html, byte for byte. Escaped JSON inside YAML "
      + "frontmatter is what an editor cannot open and `Grep` cannot reach, which is P4's whole "
      + "claim");
  assert.equal(ctx.fs.files.get(REGION_JS_FILE), REGION_JS,
    "SH191: and its JavaScript beside it, for the same reason");
  const md = ctx.fs.files.get(REGION_MD);
  assert.match(md, /^files:\n {2}HTML: content-list\.HTML\.html\n {2}JAVASCRIPT: content-list\.JAVASCRIPT\.js$/m);
  assert.doesNotMatch(md, /\{\{#each/);
});

test("a pull straight after a pull reports nothing changed — the round trip is exact", async () => {
  const fetchImpl = server({ remote: { "post:Blog/region/content-list": REGION_REMOTE } });
  const ctx = ctxWith(fetchImpl);

  await contentPull.run({}, [], ctx);
  // Same tree, same server, same base: a second pull that reported a change would mean the
  // format normalized something on the way out, and every CI drift gate would go red on its own.
  const again = await contentPull.run({}, [], { ...ctx, stdout: sink(), stderr: sink() });

  assert.deepEqual(again.written, []);
  assert.deepEqual(again.kept, []);
  assert.deepEqual(again.unchanged, ["post:Blog/region/content-list"]);
});

test("push --content sends the sidecar's bytes as the field's value", async () => {
  const fetchImpl = server({ remote: {} });
  const ctx = ctxWith(fetchImpl, {
    [REGION_MD]:
      "---\nsite: Blog\nurl: /region/content-list\ntype: Region\nfolder: /Regions\n" +
      "files:\n  HTML: content-list.HTML.html\n  JAVASCRIPT: content-list.JAVASCRIPT.js\n---\n",
    [REGION_HTML_FILE]: REGION_HTML,
    [REGION_JS_FILE]: REGION_JS,
  });

  await contentPush.run({}, [], ctx);

  const applied = fetchImpl.calls.find((call) => call.url.includes("/agent/apply"));
  const post = applied.body.posts.find((row) => row.url === "/region/content-list");
  assert.equal(post.data.HTML, REGION_HTML);
  assert.equal(post.data.JAVASCRIPT, REGION_JS);
});

test("a files: entry whose file is gone is refused, never pushed as empty", async () => {
  // The two silent readings are both destructive: empty publishes a blank layout, absent makes
  // `post.upsert` — which declares whole state — clear the server's copy.
  const ctx = ctxWith(server({ remote: {} }), {
    [REGION_MD]:
      "---\nsite: Blog\nurl: /region/content-list\ntype: Region\nfolder: /Regions\n" +
      "files:\n  HTML: content-list.HTML.html\n---\n",
  });

  await assert.rejects(() => contentPush.run({}, [], ctx), /names a file that is not there/);
});

test("a sidecar the post no longer needs is removed, not left orphaned", async () => {
  const emptied = { ...REGION_REMOTE, data: { HTML: REGION_HTML, JAVASCRIPT: "" } };
  const ctx = ctxWith(server({ remote: { "post:Blog/region/content-list": REGION_REMOTE } }));

  await contentPull.run({}, [], ctx);
  assert.equal(ctx.fs.files.has(REGION_JS_FILE), true);

  // The server's JavaScript was cleared. The `.js` must go with it — a file nothing references
  // is one a later reader edits expecting it to matter.
  const next = ctxWith(server({ remote: { "post:Blog/region/content-list": emptied } }), {
    ...Object.fromEntries(ctx.fs.files),
  });
  await contentPull.run({}, [], next);

  assert.equal(next.fs.files.has(REGION_JS_FILE), false);
  assert.equal(next.fs.files.get(REGION_HTML_FILE), REGION_HTML);
  assert.doesNotMatch(next.fs.files.get(REGION_MD), /JAVASCRIPT: content-list/);
});

// --- a value the projection normalizes is not a change (SH253) ---

/**
 * A server body holding CRLF — the fixture the whole suite was missing.
 *
 * `serializePost` rewrites it to LF, so the projection cannot represent it. What the format did
 * with that was record the *server's* form in the base and the *file's* form on disk, which made
 * the two disagree forever: every later pull reported a local change nobody made, and the CI
 * drift gate went red on content nobody had touched. The normalization existed to prevent
 * exactly that.
 */
const CRLF_REMOTE = {
  address: "post:Blog/blog/hello",
  type: "Article",
  state: "DRAFT",
  folder: "/blog",
  data: { headline: "Hello", body: "<p>First</p>\r\n<p>Second</p>" },
};

test("a body the projection rewrites is agreed, not reported changed on every pull", async () => {
  const ctx = ctxWith(server({ remote: { "post:Blog/blog/hello": CRLF_REMOTE } }));

  const first = await contentPull.run({}, [], ctx);
  assert.deepEqual(first.written, ["post:Blog/blog/hello"]);
  assert.match(ctx.fs.files.get(FILE), /<p>First<\/p>\n<p>Second<\/p>/);

  // The whole defect, as one assertion: same tree, same server, second pull. Before SH253 this
  // was `kept: ["post:Blog/blog/hello"]` — a local change nobody made — and it never converged.
  const again = await contentPull.run({}, [], { ...ctx, stdout: sink(), stderr: sink() });
  assert.deepEqual(again.unchanged, ["post:Blog/blog/hello"]);
  assert.deepEqual(again.kept, []);
  assert.deepEqual(again.written, []);

  // And a third, because the failure mode this replaced was *permanent*: a fix that merely
  // swapped which side looked changed would pass the second pull and fail here.
  const third = await contentPull.run({}, [], { ...ctx, stdout: sink(), stderr: sink() });
  assert.deepEqual(third.unchanged, ["post:Blog/blog/hello"]);
});

test("the base records what the file holds, not what the server sent", async () => {
  const ctx = ctxWith(server({ remote: { "post:Blog/blog/hello": CRLF_REMOTE } }));

  await contentPull.run({}, [], ctx);

  // Fingerprints, so the assertion is that the base agrees with the *file* — which is the only
  // document the next pull will read. Digesting the server's copy is what broke.
  const base = JSON.parse(ctx.fs.files.get(BASE_PATH));
  const { baseEntryFor } = await import("../src/content-sync.mjs");
  const { parsePost } = await import("../src/content-files.mjs");
  const onDisk = parsePost("Blog/blog/hello.md", ctx.fs.files.get(FILE));
  assert.deepEqual(
    base.posts["post:Blog/blog/hello"].digest,
    baseEntryFor(onDisk).digest,
  );
});

test("a CRLF body is not a conflict, and push --content --check is clean", async () => {
  const fetchImpl = server({ remote: { "post:Blog/blog/hello": CRLF_REMOTE }, changed: 0 });
  const ctx = ctxWith(fetchImpl);
  await contentPull.run({}, [], ctx);

  // The push side asks the same question of the server's copy and got the same wrong answer:
  // a body differing only in line endings read as "changed on the server", so the gate that is
  // supposed to prove the tree and the instance agree reported drift on its own normalization.
  const stdout = sink();
  const result = await contentPush.run({ check: true }, [], {
    ...ctx,
    fetch: server({ remote: { "post:Blog/blog/hello": CRLF_REMOTE }, changed: 0 }),
    stdout,
    stderr: sink(),
  });

  // A conflict throws, so getting here at all proves the CRLF difference is not read as one.
  assert.equal(result.drift, false);
  assert.equal(result.exitCode, 0);
  assert.doesNotMatch(stdout.text, /server is ahead/);
  assert.match(stdout.text, /in sync/);
});

// --- the dev loop (SH90) ---

test("one sync pulls, pushes, and records both as JSONL events", async () => {
  const fetchImpl = server({ remote: { "post:Blog/blog/hello": REMOTE_DOC } });
  const ctx = ctxWith(fetchImpl);

  const result = await watch.syncOnce({}, ctx, { reason: "startup" });

  assert.equal(result.ok, true);
  const log = ctx.fs.files.get(`/repo/${watch.EVENT_LOG}`);
  const events = log.trim().split("\n").map((line) => JSON.parse(line));
  assert.deepEqual(
    events.map((event) => event.event),
    ["pull", "push"],
    "SH90: one sync is both halves and records both. A loop that pulled and did not push, or "
      + "did either without an event, is a dev loop whose state nothing can be read back from",
  );
  assert.equal(events[0].written, 1);
  assert.equal(events[0].reason, "startup");
});

test("a conflict blocks the push half of the loop and says why", async () => {
  const fetchImpl = server({
    remote: {
      "post:Blog/blog/hello": { ...REMOTE_DOC, data: { headline: "Theirs", body: "<p>First</p>" } },
    },
  });
  const ctx = ctxWith(fetchImpl, {
    [FILE]: `---\nsite: Blog\nurl: /blog/hello\ntype: Article\nfolder: /blog\nbody: body\nfields:\n  headline: Ours\n---\n<p>First</p>\n`,
    [BASE_PATH]: baseSnapshot({ headline: "Hello", body: "<p>First</p>" }),
  });

  const result = await watch.syncOnce({}, ctx, { reason: "file-change" });

  assert.equal(result.ok, false);
  const events = ctx.fs.files
    .get(`/repo/${watch.EVENT_LOG}`)
    .trim()
    .split("\n")
    .map((line) => JSON.parse(line));
  assert.deepEqual(
    events.map((event) => event.event),
    ["pull", "blocked"],
  );
  assert.deepEqual(events[1].addresses, ["post:Blog/blog/hello"]);
  assert.ok(fetchImpl.calls.every((call) => !call.url.includes("/agent/apply")));
});

test("the watch loop is stoppable and triggers a sync on demand", async (t) => {
  const fetchImpl = server({ remote: {} });
  const ctx = ctxWith(fetchImpl, {
    [FILE]: `---\nsite: Blog\nurl: /blog/hello\ntype: Article\nfolder: /blog\nfields:\n  headline: Hello\n---\n`,
  });
  const watchers = [];
  ctx.watch = (path, options, onChange) => {
    watchers.push({ path, onChange });
    return { close: () => watchers.pop() };
  };

  const handle = await watch.run({}, [], ctx);
  // `stop()` unconditionally, *before* the first assertion: the loop arms a real timer, so
  // a failing assertion that skipped the cleanup left it holding the event loop open and
  // `node --test` never exited — one wrong expectation read as "the suite hangs" (SH198).
  t.after(() => handle.stop());

  assert.equal(watchers.length, 1);
  assert.equal(norm(watchers[0].path), norm(join(ctx.cwd, "shio", "content")));
  handle.stop();
  assert.equal(watchers.length, 0,
    "SH198: stopping releases the watcher. A loop that armed a timer and never gave it back is "
      + "what made `node --test` print every result and then not exit — read for a while as `the "
      + "suite hangs`, which is a sentence about the runner and was a sentence about this handle. "
      + "The general case is SH807's watchdog; this is the local half it can name");
});

// --- the change feed's CLI surface (SH88) ---

test("shio changes prints the entries and the cursor", async () => {
  const feed = {
    found: 1,
    window: false,
    cursor: "2026-07-26T10:00:00Z|abc",
    more: false,
    changes: [
      {
        action: "UPDATE",
        address: "post:Blog/blog/hello",
        at: "2026-07-26T09:59:00Z",
        by: "admin",
        what: 'Updated "Hello"',
      },
    ],
    notes: [],
  };
  const ctx = ctxWith(async () => json(feed));

  await changes.run({ since: "2026-07-26T09:00:00Z|aaa" }, [], ctx);

  assert.match(ctx.stdout.text, /UPDATE post:Blog\/blog\/hello — Updated "Hello"/);
  assert.match(ctx.stdout.text, /cursor: 2026-07-26T10:00:00Z\|abc/);
});

test("SH989: --fields asks for include=fields and prints the names, and says when none changed", async () => {
  const seen = [];
  const feed = {
    found: 3,
    window: false,
    cursor: "2026-07-26T10:00:00Z|abc",
    more: false,
    changes: [
      { action: "CREATE", address: "post:Blog/blog/hello", at: "t0", by: "agent", what: 'Created "Hello"' },
      { action: "UPDATE", address: "post:Blog/blog/hello", at: "t1", by: "agent", what: 'Updated "Hello"', fields: ["body", "title"] },
      { action: "PUBLISH", address: "post:Blog/blog/hello", at: "t2", by: "curator", what: 'Published "Hello"', fields: [] },
    ],
    notes: [],
  };
  const ctx = ctxWith(async (url) => {
    seen.push(String(url));
    return json(feed);
  });

  await changes.run({ fields: true }, [], ctx);

  assert.match(seen[0], /include=fields/, "SH989: the flag is the REST opt-in, not a local filter");
  assert.match(ctx.stdout.text, /UPDATE post:Blog\/blog\/hello — Updated "Hello" \[agent at t1\]\n {6}fields: body, title\n/);
  assert.match(ctx.stdout.text, /PUBLISH .*\n {6}fields: \(none changed\)\n/,
    "SH989: an empty list is a write that changed nothing, which is not the same as not recorded");
  assert.equal((ctx.stdout.text.match(/fields:/g) ?? []).length, 2,
    "SH989: a create records no names, so it prints none");
});

test("SH989: --fields with --follow is refused rather than silently dropped", async () => {
  let called = false;
  const ctx = ctxWith(async () => {
    called = true;
    return json({});
  });

  await assert.rejects(() => changes.run({ follow: true, fields: true }, [], ctx), /--fields.*--follow/s);
  assert.equal(called, false, "SH989: refused before connecting");
});

test("SH989: without --fields the feed is asked for nothing extra", async () => {
  const seen = [];
  const ctx = ctxWith(async (url) => {
    seen.push(String(url));
    return json({ found: 0, window: true, cursor: "2026-07-26T10:00:00Z|abc", changes: [], notes: [] });
  });

  await changes.run({}, [], ctx);

  assert.doesNotMatch(seen[0], /include=/);
});

test("--cursor prints only the position, for a pipeline", async () => {
  const ctx = ctxWith(async () =>
    json({ found: 0, window: true, cursor: "2026-07-26T10:00:00Z|abc", changes: [], notes: [] }),
  );

  await changes.run({ cursor: true }, [], ctx);

  assert.equal(ctx.stdout.text, "2026-07-26T10:00:00Z|abc\n");
});

test("--since last resumes from the position the last content pull reached", async () => {
  const seen = [];
  const ctx = ctxWith(
    async (url) => {
      seen.push(String(url));
      return json({ found: 0, window: false, cursor: "2026-07-26T10:00:00Z|abc", changes: [], notes: [] });
    },
    { [BASE_PATH]: baseSnapshot({ headline: "Hello" }) },
  );

  await changes.run({ since: "last" }, [], ctx);

  assert.match(seen[0], /since=2026-07-26T09%3A00%3A00Z%7Caaa/);
});

// --- SH967: the feed, followed ---

/** A response whose body is an SSE stream of these chunks, then the end of the connection. */
function eventStream(chunks, status = 200) {
  const encoder = new TextEncoder();
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: () => null },
    body: (async function* body() {
      for (const chunk of chunks) yield encoder.encode(chunk);
    })(),
    async text() {
      return "";
    },
  };
}

const sseEvent = (id, data) => `id: ${id}\nevent: changes\ndata: ${JSON.stringify(data)}\n\n`;

test("SH967: parseEventStream reads ids, names and data across chunk boundaries, and skips heartbeats", async () => {
  const { parseEventStream } = await import("../src/agent-client.mjs");
  const text = `: heartbeat\n\n${sseEvent("c1", { changes: [{ action: "CREATE" }] })}`;
  // Split mid-field, the way a network delivers it.
  const chunks = [text.slice(0, 7), text.slice(7, 40), text.slice(40)];
  const events = [];
  for await (const event of parseEventStream((async function* () { yield* chunks; })())) {
    events.push(event);
  }
  assert.deepEqual(events, [{ id: "c1", event: "changes", data: { changes: [{ action: "CREATE" }] } }],
    "SH967: a heartbeat is a comment, not an event, and a field split between two chunks is one field");
});

test("SH967: --follow prints each pushed page, and reconnects from the last event id without loss", async () => {
  const requests = [];
  const controller = new AbortController();
  const streams = [
    eventStream([sseEvent("2026-09-14T10:00:00Z|a", {
      changes: [{ action: "CREATE", address: "folder:Blog/news", by: "agent", at: "t1", what: "Created" }],
    })]),
    eventStream([sseEvent("2026-09-14T10:05:00Z|b", {
      changes: [{ action: "UPDATE", address: "post:Blog/news/hello", by: "agent", at: "t2", what: "Updated" }],
    })]),
  ];
  const ctx = ctxWith(async (url, init) => {
    requests.push({ url: String(url), headers: init.headers });
    return streams.shift();
  });
  ctx.signal = controller.signal;
  let slept = 0;
  ctx.sleep = async () => {
    slept += 1;
    if (requests.length >= 2) controller.abort();
  };

  const result = await changes.run({ follow: true, site: "Blog" }, [], ctx);

  assert.equal(requests.length, 2, "one connection, dropped, and one reconnect");
  assert.match(requests[0].url, /\/api\/v2\/events\?site=Blog$/);
  assert.equal(requests[0].headers.Accept, "text/event-stream");
  assert.equal(requests[0].headers["Last-Event-ID"], undefined,
    "SH967: a first open with no --since starts at the edge of the feed");
  assert.equal(requests[1].headers["Last-Event-ID"], "2026-09-14T10:00:00Z|a",
    "SH967: a reconnect must name the last event it received, or whatever was written while the"
      + " connection was down is lost");
  assert.match(ctx.stdout.text, /CREATE folder:Blog\/news — Created/);
  assert.match(ctx.stdout.text, /UPDATE post:Blog\/news\/hello — Updated/);
  assert.equal(result.cursor, "2026-09-14T10:05:00Z|b");
  assert.ok(slept >= 1, "a dropped connection waits before reconnecting");
});

test("SH967: --follow ends on a refusal instead of retrying it", async () => {
  let calls = 0;
  const ctx = ctxWith(async () => {
    calls += 1;
    return eventStream([], 400);
  });
  ctx.sleep = async () => {
    throw new Error("a refusal must not be retried");
  };

  await assert.rejects(() => changes.run({ follow: true, since: "not-a-cursor" }, [], ctx),
    (error) => error.status === 400);
  assert.equal(calls, 1, "SH967: a 4xx is an answer that will not change on a retry");
});

test("SH967: --follow --json writes one line per event, for a pipeline", async () => {
  const controller = new AbortController();
  const ctx = ctxWith(async () =>
    eventStream([sseEvent("c9", { changes: [{ action: "DELETE", address: null }] })]));
  ctx.signal = controller.signal;
  ctx.sleep = async () => controller.abort();

  await changes.run({ follow: true, json: true }, [], ctx);

  const lines = ctx.stdout.text.trim().split("\n");
  assert.equal(lines.length, 1);
  assert.deepEqual(JSON.parse(lines[0]), { cursor: "c9", changes: [{ action: "DELETE", address: null }] });
});

test("SH969: --follow prints a failed schedule's retry as the command that performs it", async () => {
  const controller = new AbortController();
  const ctx = ctxWith(async () => eventStream([sseEvent("c10", {
    incidents: [{
      kind: "schedule", subject: "id:7f1c", message: "scheduled publish failed: address taken",
      count: 1, at: "t3", retry: { op: "post.publish", address: "post:Blog/launch" },
    }],
  })]));
  ctx.signal = controller.signal;
  ctx.sleep = async () => controller.abort();

  await changes.run({ follow: true }, [], ctx);

  assert.match(ctx.stdout.text, /! schedule id:7f1c — scheduled publish failed: address taken/);
  assert.match(ctx.stdout.text, /retry: shio publish post:Blog\/launch/,
    "SH969: a curator following the feed is told what to type, not only that it broke");
});

test("SH970: a form submission's arrival is said as one, with its summary", async () => {
  const controller = new AbortController();
  const ctx = ctxWith(async () => eventStream([sseEvent("c11", {
    changes: [
      {
        action: "CREATE", address: "post:Acme/enquiries/jane-doe", by: "form-token:t1", at: "t4",
        what: 'Created "Jane Doe"', submission: { title: "Jane Doe", summary: "Acme, pricing" },
      },
      { action: "CREATE", address: "post:Acme/about", by: "curator", at: "t5", what: 'Created "About"' },
    ],
  })]));
  ctx.signal = controller.signal;
  ctx.sleep = async () => controller.abort();

  await changes.run({ follow: true }, [], ctx);

  assert.match(ctx.stdout.text, /CREATE post:Acme\/enquiries\/jane-doe — Created "Jane Doe"[^\n]*\n {6}form submission: Acme, pricing\n/,
    "SH970: the arrival is marked on the line under it, not left to the actor string");
  assert.equal(ctx.stdout.text.match(/form submission/g).length, 1,
    "SH970: and a post a curator made is not marked");
});

// --- SH401: two addresses, one file path ---

/**
 * `fakeFs` with the one behaviour Windows and macOS have and Linux does not: a path is stored
 * and found case-insensitively. The first write's spelling is the one that survives, which is
 * what makes the loss silent — the second write reports success and overwrites.
 */
function caseInsensitiveCtx(fetchImpl, seed = {}) {
  const ctx = ctxWith(fetchImpl, seed);
  const real = ctx.fs.files;
  const keys = new Map([...real.keys()].map((key) => [key.toLowerCase(), key]));
  const resolve = (key) => keys.get(key.toLowerCase()) ?? key;
  ctx.fs.files = new Proxy(real, {
    get(target, prop) {
      if (prop === "get" || prop === "has" || prop === "delete") {
        return (key) => target[prop](resolve(key));
      }
      if (prop === "set") {
        return (key, value) => {
          const existing = resolve(key);
          keys.set(key.toLowerCase(), existing);
          return target.set(existing, value);
        };
      }
      const value = Reflect.get(target, prop);
      return typeof value === "function" ? value.bind(target) : value;
    },
  });
  return ctx;
}

const CASED_DOCS = {
  "post:Blog/blog/hello": REMOTE_DOC,
  "post:Blog/blog/Hello": {
    address: "post:Blog/blog/Hello",
    type: "Article",
    state: "DRAFT",
    folder: "/blog",
    data: { headline: "The other one", body: "<p>Second</p>" },
  },
};

test("SH401: two URLs differing only in case are two posts and one file", async () => {
  // The measurement, before the fix. Both addresses are legitimate — the furls differ, so the
  // server's `duplicate-url` never fires and `ShAddressResolver` resolves each to one post —
  // and `filePathFor` still sends them to paths that differ only in case.
  const ctx = caseInsensitiveCtx(server({ remote: CASED_DOCS }));

  const result = await contentPull.run({}, [], ctx);

  // Neither is written, and the collision is named rather than resolved by the filesystem.
  assert.equal(ctx.fs.files.get(FILE), undefined);
  assert.deepEqual(result.written, []);
  const collision = result.conflicts.find((entry) => entry.paths);
  assert.ok(collision, "the pull reports the pair");
  // Ordered, so two runs over one instance produce the same message (P5).
  assert.deepEqual(collision.addresses, ["post:Blog/blog/hello", "post:Blog/blog/Hello"]);
  assert.match(ctx.stdout.text, /Blog\/blog\/hello\.md/);
});

test("SH401: the snapshot a collision leaves is partial, so --prune cannot act on it", async () => {
  // The consequence is what makes this destructive rather than merely invisible: the base would
  // record both, the tree hold one, and the next `push --prune` read the difference as a local
  // deletion. `partial` is the flag that already exists to refuse exactly that.
  const ctx = caseInsensitiveCtx(server({ remote: CASED_DOCS }));

  await contentPull.run({}, [], ctx);

  assert.match(ctx.fs.files.get(BASE_PATH), /"partial": true/);
});

test("SH401: a case difference between different sites is not a collision", async () => {
  // The paths have to collide, not the URLs — `Blog` and `blog` are separate directories only
  // when the filesystem says so, and two sites that differ by case are one directory too.
  const ctx = caseInsensitiveCtx(server({
    remote: {
      "post:Blog/blog/hello": REMOTE_DOC,
      "post:News/blog/other": {
        address: "post:News/blog/other",
        type: "Article",
        state: "DRAFT",
        folder: "/blog",
        data: { headline: "Elsewhere", body: "<p>Other</p>" },
      },
    },
  }));

  const result = await contentPull.run({}, [], ctx);

  assert.deepEqual(result.conflicts, []);
  assert.equal(result.written.length, 2);
});

test("SH402: a post whose URL navigates is skipped and named, not written and not fatal", async () => {
  // The pull keeps going: the projection is short by exactly this post, and the rest is fine.
  const ctx = ctxWith(server({
    remote: {
      "post:Blog/blog/hello": REMOTE_DOC,
      "post:Blog/../../escaped": {
        address: "post:Blog/../../escaped",
        type: "Article",
        state: "DRAFT",
        folder: "/blog",
        data: { headline: "Escaped", body: "<p>Nope</p>" },
      },
    },
  }));

  const result = await contentPull.run({}, [], ctx);

  // The good post still lands.
  assert.match(ctx.fs.files.get(FILE), /url: \/blog\/hello/);
  assert.deepEqual(result.written, ["post:Blog/blog/hello"]);
  // Nothing was written anywhere near the escape.
  const written = [...ctx.fs.files.keys()].filter((key) => key.endsWith("escaped.md"));
  assert.deepEqual(written, [], "no file outside the content root");
  assert.match(ctx.stdout.text, /navigates the filesystem instead of naming a post/);
});

test("SH402: a skipped post makes the snapshot partial, so --prune cannot read it as a deletion", async () => {
  // True of SH173's rows as much as SH402's: a post missing from the tree because it could not be
  // read looks exactly like one a curator deleted, and `--prune` acted on the difference.
  const ctx = ctxWith(server({
    remote: {
      "post:Blog/blog/hello": REMOTE_DOC,
      "post:Blog/../../escaped": {
        address: "post:Blog/../../escaped",
        type: "Article",
        state: "DRAFT",
        folder: "/blog",
        data: { headline: "Escaped" },
      },
    },
  }));

  await contentPull.run({}, [], ctx);

  assert.match(ctx.fs.files.get(BASE_PATH), /"partial": true/);
});

test("SH403: a folder chain that navigates is skipped and named, and the pull continues", async () => {
  // The sitemap's `node.path` is server data, exactly like a post's furl, and it becomes a marker
  // path the same way. One bad row must not take the run down.
  const ctx = ctxWith(server({
    remote: { "post:Blog/blog/hello": REMOTE_DOC },
    serverFolders: { Blog: ["/", "/news", "/../../escaped"] },
  }));

  const result = await contentPull.run({}, [], ctx);

  const escaped = [...ctx.fs.files.keys()].filter((key) => key.includes("escaped"));
  assert.deepEqual(escaped, [], "no marker outside the folders root");
  assert.match(ctx.stdout.text, /navigates the filesystem instead of naming a folder/);
  // The good folders still land, and so does the post.
  assert.ok(result.folders.written.some((entry) => entry.endsWith("/news")));
  assert.deepEqual(result.written, ["post:Blog/blog/hello"]);
});

test("SH404: a clean pull clears partial, so the remedy the push names actually works", async () => {
  // The push refuses `--prune` on a partial base and says "Take a complete projection first
  // (`shio pull --content`)". Measured before the fix: `partial: base.partial || ...` never goes
  // back to false, so that instruction could not resolve the condition it named — one transient
  // skipped row disabled `--prune` for the life of the project.
  const bad = ctxWith(server({
    remote: {
      "post:Blog/blog/hello": REMOTE_DOC,
      "post:Blog/../../escaped": {
        address: "post:Blog/../../escaped",
        type: "Article",
        state: "DRAFT",
        folder: "/blog",
        data: { headline: "Escaped" },
      },
    },
  }));
  await contentPull.run({}, [], bad);
  assert.match(bad.fs.files.get(BASE_PATH), /"partial": true/, "the skip marks it");

  // The offending post is gone from the server; the tree and base are carried over verbatim.
  const seed = {};
  for (const [key, value] of bad.fs.files) seed[key] = value;
  const good = ctxWith(server({ remote: { "post:Blog/blog/hello": REMOTE_DOC } }), seed);

  await contentPull.run({}, [], good);

  assert.match(good.fs.files.get(BASE_PATH), /"partial": false/);
});

test("SH404: a scoped pull still inherits partial, because it proves nothing about the rest", async () => {
  // The other half, and the reason the fix is not simply "recompute". A `--site` run reads one
  // site; clearing the flag there would tell `--prune` the tree describes an instance it never
  // looked at, which is the deletion the flag exists to prevent.
  const seed = {
    [BASE_PATH]: `${JSON.stringify({
      version: 3,
      cursor: "2026-07-26T09:00:00Z|aaa",
      partial: true,
      posts: {},
      folders: [],
      notes: {},
      assets: {},
    })}
`,
  };
  const ctx = ctxWith(server({ remote: { "post:Blog/blog/hello": REMOTE_DOC } }), seed);

  await contentPull.run({ site: "Blog" }, [], ctx);

  assert.match(ctx.fs.files.get(BASE_PATH), /"partial": true/);
});

test("SH404: an unscoped pull over a clean instance clears an inherited partial", async () => {
  // The pair: same seed, no --site. The run covers every site the tree holds, which is the scope
  // `--prune` acts on, so it is entitled to answer for the snapshot.
  const seed = {
    [BASE_PATH]: `${JSON.stringify({
      version: 3,
      cursor: "2026-07-26T09:00:00Z|aaa",
      partial: true,
      posts: {},
      folders: [],
      notes: {},
      assets: {},
    })}
`,
  };
  const ctx = ctxWith(server({ remote: { "post:Blog/blog/hello": REMOTE_DOC } }), seed);

  await contentPull.run({}, [], ctx);

  assert.match(ctx.fs.files.get(BASE_PATH), /"partial": false/);
});

test("SH405: a truncated sitemap says so, instead of leaving the symptom unexplained", async () => {
  // Measured before the fix: `readRemoteAssets` gets `truncated` from the same sitemap walk the
  // post list treats as fatal, returns it, and no command reads it. The run then says a File
  // reference names "no asset this run could see" and never says why it could not see it.
  // Seeded, because the asset inventory reads the sites the *tree* names: an empty tree reads no
  // sites and so cannot truncate, which is a separate defect (SH406) and not this one.
  const ctx = ctxWith(server({
    remote: { "post:Blog/blog/hello": REMOTE_DOC },
    serverFolders: { Blog: ["/", "/media"] },
    sitemapTruncated: true,
  }), {
    [FILE]: "---\nsite: Blog\nurl: /blog/hello\ntype: Article\nfolder: /blog\nfields:\n  headline: Hello\n---\n",
  });

  await contentPull.run({}, [], ctx);

  assert.match(ctx.stdout.text, /asset inventory/);
  // The cause and the remedy, not just the symptom (P9, P6).
  assert.match(ctx.stdout.text, /truncated/);
  assert.match(ctx.stdout.text, /max-limit/);
});

test("SH406: a first pull into an empty tree fetches the assets too", async () => {
  // The measurement. The post read falls back to the whole instance when the tree names no sites;
  // the asset read passed the empty list through and walked nothing, so a clone got posts, no
  // bytes, and File fields still holding ids — from a run that reported success.
  const ctx = ctxWith(server({
    remote: {
      "post:Blog/blog/hello": {
        ...REMOTE_DOC,
        data: { headline: "Hello", body: "<p>First</p>", hero: "a1" },
      },
    },
    serverFolders: { Blog: ["/", "/media"] },
    serverAssets: [
      { id: "a1", fileName: "logo.png", folderId: "Blog:/media", siteName: "Blog", size: 4 },
    ],
    serverBytes: { a1: "AAAA" },
  }));

  const result = await contentPull.run({}, [], ctx);

  assert.deepEqual(result.assets, ["Blog/media/logo.png"], "the bytes came down");
  assert.ok(ctx.fs.files.has("/repo/shio/assets/Blog/media/logo.png"));
  // And the reference is a path, not the id it arrived as (SH145).
  assert.match(ctx.fs.files.get(FILE), /hero: Blog\/media\/logo\.png/);
});

test("SH407: two folder chains differing only in case are two folders and one marker", async () => {
  // The measurement. SH401 proved this for posts and fixed the post root; markers are the same
  // kind of path from the same server data, one directory over.
  const ctx = caseInsensitiveCtx(server({
    remote: { "post:Blog/blog/hello": REMOTE_DOC },
    serverFolders: { Blog: ["/", "/news", "/News"] },
  }));

  const result = await contentPull.run({}, [], ctx);

  const markers = [...ctx.fs.files.keys()].filter((key) => key.endsWith("_folder.md"));
  // Neither is written, and the pair is named rather than settled by the filesystem.
  assert.equal(markers.filter((key) => key.toLowerCase().includes("/news/")).length, 0);
  const collision = result.folders.conflicts?.find((entry) => entry.paths);
  assert.ok(collision, "the pull reports the pair");
  assert.deepEqual(collision.addresses, ["folder:Blog/news", "folder:Blog/News"]);
  assert.match(ctx.stdout.text, /one file/);
});

test("SH408: two assets differing only in case are named, and neither is written", async () => {
  const ctx = ctxWith(server({
    remote: { "post:Blog/blog/hello": REMOTE_DOC },
    serverFolders: { Blog: ["/", "/media"] },
    serverAssets: [
      { id: "a1", fileName: "Logo.png", folderId: "Blog:/media", siteName: "Blog", size: 4 },
      { id: "a2", fileName: "logo.png", folderId: "Blog:/media", siteName: "Blog", size: 4 },
    ],
    serverBytes: { a1: "AAAA", a2: "BBBB" },
  }));

  const result = await contentPull.run({}, [], ctx);

  const written = [...ctx.fs.files.keys()].filter((key) => key.includes("/assets/"));
  assert.deepEqual(written, [], "the byte merge never compares a survivor against both rows");
  assert.deepEqual(result.assets, []);
});

test("SH409: two post types differing only in case are named by the command that writes them", async () => {
  // The severe one, and it moved: SH408 wired this into `pull --content`, which reads defs to map
  // File fields and writes none. `shio pull` is the loop that writes `<name>.json`, and it folded
  // the pair in silence — `diffDefs` then reports the loser as removed and `push --prune` deletes
  // the post type every post of it is validated against.
  const ctx = ctxWith(server({
    defs: [...DEFS, { name: "article", fields: [{ name: "headline", widget: "Text", ordinal: 1 }] }],
  }));

  const written = await pull.run({}, [], ctx);

  // Ordered by `localeCompare`, which puts the lowercase name first (P5, as in SH401).
  assert.match(ctx.stdout.text, /post-type article and Article project to one file/);
  assert.match(ctx.stdout.text, /would then delete the other's type/);
  // Neither is on disk: there is no merging two models into one file.
  const files = [...ctx.fs.files.keys()].filter((key) => /[Aa]rticle\.json$/.test(key));
  assert.deepEqual(files, []);
  assert.deepEqual(written.map((def) => def.name), ["Region"]);
});

// --- SH231: the instance's own notes ---

test("SH231: a pull projects the instance-wide notes, which used to travel nowhere", async () => {
  // SH161 made a site's notes ride its export and deliberately left these behind. That holds for
  // a site import — another installation's house rules beside somebody's blog is the P7 surprise
  // — and not for moving a whole instance, which is the case they matter most in and the only
  // one with no export path at all.
  const ctx = ctxWith(server({
    remote: { "post:Blog/blog/hello": REMOTE_DOC },
    serverNotes: [
      { scope: "Blog", key: "voice", note: "Second person, present tense." },
      { key: "authoring", note: "Copy is authored in files here.", author: "ana" },
      { key: "publishing", note: "Publishing happens on Fridays." },
    ],
  }));

  const result = await contentPull.run({}, [], ctx);

  assert.equal(result.instanceNotes, 2, "the two scope-less notes, and not the site's");
  const file = ctx.fs.files.get("/repo/shio/.memory.yaml");
  assert.match(file, /key: authoring/);
  assert.match(file, /key: publishing/);
  assert.doesNotMatch(file, /voice/, "a site's note belongs in that site's own file (SH161)");
  // Beside content/, not inside it: this describes the installation, not any site.
  assert.ok(!ctx.fs.files.has("/repo/shio/content/.memory.yaml"));
  assert.match(ctx.stdout.text, /instance-wide note\(s\)/);
});

test("SH231: an instance with no house rules gets no file, rather than an empty one", async () => {
  const ctx = ctxWith(server({ remote: { "post:Blog/blog/hello": REMOTE_DOC } }));

  const result = await contentPull.run({}, [], ctx);

  assert.equal(result.instanceNotes, 0);
  // An empty file would read as "there are none" when it means "we never asked" (SH138).
  assert.ok(!ctx.fs.files.has("/repo/shio/.memory.yaml"));
});

test("SH231: a push reports the file and refuses to apply it — that is SH161's rule kept", async () => {
  const fetchImpl = server({ remote: {} });
  const ctx = ctxWith(fetchImpl, {
    [FILE]: `---\nsite: Blog\nurl: /blog/hello\ntype: Article\nfolder: /blog\nbody: body\nfields:\n  headline: Hello\n---\n<p>First</p>\n`,
    "/repo/shio/.memory.yaml": "notes:\n  - key: authoring\n    note: Copy is authored in files here.\n",
  });

  await contentPush.run({}, [], ctx);

  const remembered = fetchImpl.calls.filter((call) => call.url.includes("/agent/memory")
    && call.init?.method === "PUT");
  assert.equal(remembered.length, 0, "nothing was adopted without being asked");
  // Said out loud even though nothing happened: a file the push silently ignores is the SH138
  // failure, and "I edited it and nothing happened" is how an operator would otherwise find out.
  assert.match(ctx.stdout.text, /were NOT applied/);
  assert.match(ctx.stdout.text, /--adopt-memory/);
});

test("SH231: --adopt-memory writes them, scope-less, so a moved instance keeps its rules", async () => {
  const fetchImpl = server({ remote: {} });
  const ctx = ctxWith(fetchImpl, {
    [FILE]: `---\nsite: Blog\nurl: /blog/hello\ntype: Article\nfolder: /blog\nbody: body\nfields:\n  headline: Hello\n---\n<p>First</p>\n`,
    "/repo/shio/.memory.yaml":
      "notes:\n  - key: authoring\n    note: Copy is authored in files here.\n"
      + "  - key: publishing\n    note: Publishing happens on Fridays.\n",
  });

  await contentPush.run({ adoptMemory: true }, [], ctx);

  const remembered = fetchImpl.calls.filter((call) => call.url.includes("/agent/memory")
    && call.init?.method === "PUT");
  assert.equal(remembered.length, 2);
  // No `scope` is what makes it instance-wide — the same shape `shio remember` sends with no
  // --site. A scope here would file the house rules under a site, which is not what they are.
  assert.ok(remembered.every((call) => call.body.scope === undefined));
  assert.deepEqual(remembered.map((call) => call.body.key), ["authoring", "publishing"]);
  assert.match(ctx.stdout.text, /2 instance-wide note\(s\) adopted/);
});

test("SH231: --dry-run adopts nothing but says what it would", async () => {
  const fetchImpl = server({ remote: {} });
  const ctx = ctxWith(fetchImpl, {
    [FILE]: `---\nsite: Blog\nurl: /blog/hello\ntype: Article\nfolder: /blog\nbody: body\nfields:\n  headline: Hello\n---\n<p>First</p>\n`,
    "/repo/shio/.memory.yaml": "notes:\n  - key: authoring\n    note: Copy is authored in files here.\n",
  });

  await contentPush.run({ adoptMemory: true, dryRun: true }, [], ctx);

  assert.equal(
    fetchImpl.calls.filter((call) => call.url.includes("/agent/memory")
      && call.init?.method === "PUT").length,
    0,
  );
  assert.match(ctx.stdout.text, /would be adopted/);
});
