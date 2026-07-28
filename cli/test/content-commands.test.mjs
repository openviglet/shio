/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
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
import * as contentPush from "../src/commands/content-push.mjs";
import * as watch from "../src/commands/content-watch.mjs";
import { norm } from "./support/paths.mjs";

const DEFS = [
  {
    name: "Article",
    fields: [
      { name: "headline", widget: "Text", ordinal: 1 },
      { name: "body", widget: "HTML Editor", ordinal: 2 },
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
  // SH145 — the media inventory: `[{ id, fileName, folderId, siteName }]`, plus the bytes.
  serverAssets = [],
} = {}) {
  const calls = [];
  const uploads = [];
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
        budget: { estTokens: 10, limitTokens: 0, truncated: false },
      });
    }
    if (target.includes("/api/v2/staticfile/upload")) {
      const created = { id: `uploaded-${uploads.length + 1}` };
      uploads.push({ body: init.body, created });
      return json(created);
    }
    if (/\/api\/v2\/staticfile\/[^/?]+$/.test(target)) {
      const id = target.split("/").pop();
      return bytesResponse(new TextEncoder().encode(`bytes-of-${id}`));
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
    if (target.includes("/api/v2/post-type")) return json(DEFS);
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
  assert.ok(applies.every((call) => call.body.posts.length + call.body.folders.length <= 5));
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

function assetServer({ remote = {}, serverAssets = [], serverFolders = {} } = {}) {
  const base = server({ remote, serverAssets, serverFolders });
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
  // A `File` post's bytes are immutable — an upload always creates a new id-keyed post — so the
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
    serverAssets: [{ id: "file-1", fileName: "logo.png", folderId: "Blog:/media", siteName: "Blog" }],
  });
  const ctx = ctxWith(fetchImpl, {
    [FILE]: `---\nsite: Blog\nurl: /blog/hello\ntype: Article\nfolder: /blog\nfields:\n  headline: Hello\n  hero: Blog/media/logo.png\n---\n`,
    "/repo/shio/assets/Blog/media/logo.png": "the bytes",
  });

  await contentPush.run({}, [], ctx);

  assert.equal(fetchImpl.uploads.length, 0);
  const applied = fetchImpl.calls.find((call) => call.url.includes("/agent/apply"));
  assert.equal(applied.body.posts[0].data.hero, "file-1");
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
  assert.equal(base.version, 2);
  const entry = base.posts["post:Blog/blog/hello"];
  // The identity stays verbatim, because it is *used* rather than compared: a pull deletes a
  // file by the address the base remembers, and a digest would leave nothing to delete.
  assert.deepEqual(Object.keys(entry).sort(), ["digest", "site", "url"]);
  // `.field` for a data key, `@name` for metadata — the same flattening the merge compares on.
  assert.deepEqual(Object.keys(entry.digest).sort(), [".body", ".headline", "@folder", "@type"]);
  assert.ok(Object.values(entry.digest).every((hash) => /^[0-9a-f]{16}$/.test(hash)));
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
  assert.equal(JSON.parse(ctx.fs.files.get(BASE_PATH)).version, 2);
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
  );
  assert.deepEqual(applied.body.posts, []);
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

  assert.deepEqual(result.folders, { written: [], deleted: [], kept: [] });
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
  assert.equal(ctx.fs.files.get(REGION_HTML_FILE), REGION_HTML);
  assert.equal(ctx.fs.files.get(REGION_JS_FILE), REGION_JS);
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
  assert.equal(watchers.length, 0);
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
