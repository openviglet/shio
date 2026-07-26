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
import { test } from "node:test";

import * as changes from "../src/commands/changes.mjs";
import * as contentPull from "../src/commands/content-pull.mjs";
import * as contentPush from "../src/commands/content-push.mjs";
import * as watch from "../src/commands/content-watch.mjs";

const DEFS = [
  {
    name: "Article",
    fields: [
      { name: "headline", widget: "Text", ordinal: 1 },
      { name: "body", widget: "HTML Editor", ordinal: 2 },
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

function norm(path) {
  return String(path).replace(/\\/g, "/").replace(/\/+/g, "/");
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
function server({ remote = {}, truncated = false, changed = 2 } = {}) {
  const calls = [];
  const fetchImpl = async (url, init = {}) => {
    const target = String(url);
    calls.push({ url: target, init, body: init.body ? JSON.parse(init.body) : null });
    if (target.includes("/agent/find")) {
      return json({
        count: Object.keys(remote).length,
        truncated,
        results: Object.keys(remote).map((address) => ({ address, type: remote[address].type })),
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
  return fetchImpl;
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
  // would delete every post it never saw (SH138's rule).
  const ctx = ctxWith(server({ remote: { "post:Blog/blog/hello": REMOTE_DOC }, truncated: true }));

  await assert.rejects(() => contentPull.run({}, [], ctx), /truncated post list/);

  const allowed = ctxWith(server({ remote: { "post:Blog/blog/hello": REMOTE_DOC }, truncated: true }));
  await contentPull.run({ allowPartial: true }, [], allowed);
  assert.match(allowed.fs.files.get(BASE_PATH), /"partial": true/);
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

test("the watch loop is stoppable and triggers a sync on demand", async () => {
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

  assert.equal(watchers.length, 1);
  assert.match(watchers[0].path, /shio\/content$/);
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
