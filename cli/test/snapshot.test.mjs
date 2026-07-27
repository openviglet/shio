/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH102 — the visual snapshot. Browser-free: the capture is injected, because a test that
 * launched Chromium would be testing Playwright, and what is worth pinning here is everything
 * around it — the baseline rotation, the two comparison resolutions, and the exit code.
 *
 * The exit code is the one to watch. §IX.4 frames a PNG as a human artifact rather than an agent
 * input, and that decision only survives if a visual change does *not* fail the command by
 * default: the moment it does, the command that produces the evidence is the command people
 * wrap in `|| true`.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { compareSnapshots, describe as describeResult, resolveUrl, slugFor } from "../src/snapshot.mjs";
import * as snapshot from "../src/commands/snapshot.mjs";

function sink() {
  const out = { text: "" };
  out.write = (s) => {
    out.text += s;
  };
  return out;
}

const CONTEXT = { sites: [{ name: "Blog", url: "https://blog.example.com/" }, { name: "Docs", url: "" }] };

function ctxWith({ files = {}, capture, compare } = {}) {
  const written = {};
  return {
    env: { SHIO_URL: "http://shio.test", SHIO_USER: "admin", SHIO_PASSWORD: "secret" },
    cwd: "/repo",
    stdout: sink(),
    stderr: sink(),
    written,
    capture: capture ?? (async () => Buffer.from("AFTER")),
    compare,
    async fetch() {
      return {
        ok: true,
        status: 200,
        headers: { get: () => null },
        async text() {
          return JSON.stringify(CONTEXT);
        },
        async json() {
          return CONTEXT;
        },
      };
    },
    fs: {
      async readFile(path) {
        const key = Object.keys(files).find((each) => path.replace(/\\/g, "/").endsWith(each));
        if (key) return files[key];
        const error = new Error(`ENOENT: ${path}`);
        error.code = "ENOENT";
        throw error;
      },
      async mkdir() {},
      async writeFile(path, body) {
        written[path.replace(/\\/g, "/")] = body;
      },
    },
  };
}

// --- the pure half ---

test("a slug is stable per page and cannot collide two pages onto one baseline", () => {
  assert.equal(slugFor("post:Blog/blog/hello-world"), "blog-blog-hello-world");
  assert.equal(slugFor("https://blog.example.com/blog/hello"), "blog.example.com-blog-hello");
  assert.notEqual(slugFor("post:Blog/a"), slugFor("post:Blog/b"));
  assert.equal(slugFor(""), "page");
});

test("no baseline is 'new', not 'changed'", () => {
  const result = compareSnapshots(null, Buffer.from("X"));
  assert.equal(result.status, "new");
  assert.equal(result.ratio, null);
});

test("without a pixel comparator the answer is byte-level, and says so", () => {
  const same = compareSnapshots(Buffer.from("X"), Buffer.from("X"));
  assert.equal(same.status, "same");
  assert.equal(same.resolution, "bytes");
  // Deliberately null: a byte comparison cannot measure *how much* changed, and a fabricated
  // magnitude is exactly the number a caller would build a threshold on.
  assert.equal(same.ratio, null);

  const changed = compareSnapshots(Buffer.from("X"), Buffer.from("Y"));
  assert.equal(changed.status, "changed");
  assert.equal(changed.ratio, null);
});

test("with a pixel comparator the ratio and the diff image come through", () => {
  const result = compareSnapshots(Buffer.from("X"), Buffer.from("Y"), {
    compare: () => ({ ratio: 0.0125, diff: Buffer.from("DIFF") }),
  });
  assert.equal(result.status, "changed");
  assert.equal(result.resolution, "pixel");
  assert.equal(result.ratio, 0.0125);
  assert.equal(String(result.diff), "DIFF");
});

test("a pixel comparison finding zero differing pixels is 'same'", () => {
  const result = compareSnapshots(Buffer.from("X"), Buffer.from("Y"), {
    compare: () => ({ ratio: 0, diff: Buffer.from("EMPTY") }),
  });
  assert.equal(result.status, "same");
});

test("the summary line names the resolution of the claim it is making", () => {
  assert.match(
    describeResult({ status: "same", ratio: null, resolution: "bytes" }, { slug: "p" }),
    /unchanged \(bytes\)/,
  );
  assert.match(
    describeResult({ status: "changed", ratio: 0.25, resolution: "pixel" }, { slug: "p" }),
    /CHANGED — 25\.00% of pixels \(pixel\)/,
  );
  assert.match(
    describeResult({ status: "new", ratio: null, resolution: "bytes" }, { slug: "p" }),
    /no baseline to compare/,
  );
});

test("an address resolves against the site's configured URL; a URL is taken as given", () => {
  const sites = new Map([["Blog", "https://blog.example.com/"], ["Docs", ""]]);

  assert.equal(
    resolveUrl("post:Blog/blog/hello", sites).url,
    "https://blog.example.com/blog/hello",
  );
  assert.equal(resolveUrl("https://x.test/y", sites).url, "https://x.test/y");
  // Never a guessed origin: a screenshot of the wrong host is a plausible-looking artifact.
  assert.match(resolveUrl("post:Docs/guide", sites).error, /no front-end URL configured/);
  assert.match(resolveUrl("post:Other/x", sites).error, /not one this instance reports/);
  assert.match(resolveUrl("folder:Blog/blog", sites).error, /is not a page/);
});

// --- the command ---

test("a first capture writes the baseline and reports no comparison", async () => {
  const ctx = ctxWith();
  const result = await snapshot.run({}, ["post:Blog/blog/hello"], ctx);

  assert.equal(result.url, "https://blog.example.com/blog/hello");
  assert.equal(result.status, "new");
  assert.deepEqual(Object.keys(ctx.written), ["shio/snapshots/blog-blog-hello.png"]);
  assert.match(ctx.stdout.text, /no baseline to compare/);
});

test("a change keeps the displaced baseline as .prev.png and still exits 0", async () => {
  const ctx = ctxWith({ files: { "shio/snapshots/blog-blog-hello.png": Buffer.from("BEFORE") } });
  const result = await snapshot.run({}, ["post:Blog/blog/hello"], ctx);

  assert.equal(result.status, "changed");
  // A page looking different is usually the change you just made (§IX.4).
  assert.equal(result.exitCode, 0);
  assert.equal(String(ctx.written["shio/snapshots/blog-blog-hello.prev.png"]), "BEFORE");
  assert.equal(String(ctx.written["shio/snapshots/blog-blog-hello.png"]), "AFTER");
  assert.match(ctx.stdout.text, /CHANGED/);
  assert.match(ctx.stdout.text, /previous: /);
});

test("--fail-on-change is the opt-in gate", async () => {
  const ctx = ctxWith({ files: { "shio/snapshots/blog-blog-hello.png": Buffer.from("BEFORE") } });
  const result = await snapshot.run({ failOnChange: true }, ["post:Blog/blog/hello"], ctx);

  assert.equal(result.exitCode, 1);
});

test("--accept moves the baseline without keeping a previous copy", async () => {
  const ctx = ctxWith({ files: { "shio/snapshots/blog-blog-hello.png": Buffer.from("BEFORE") } });
  await snapshot.run({ accept: true }, ["post:Blog/blog/hello"], ctx);

  assert.equal(ctx.written["shio/snapshots/blog-blog-hello.prev.png"], undefined);
  assert.equal(String(ctx.written["shio/snapshots/blog-blog-hello.png"]), "AFTER");
});

test("an unchanged page rewrites nothing surprising and reports 'unchanged'", async () => {
  const ctx = ctxWith({
    files: { "shio/snapshots/blog-blog-hello.png": Buffer.from("AFTER") },
  });
  const result = await snapshot.run({}, ["post:Blog/blog/hello"], ctx);

  assert.equal(result.status, "same");
  assert.equal(ctx.written["shio/snapshots/blog-blog-hello.prev.png"], undefined);
  assert.match(ctx.stdout.text, /unchanged/);
});

test("a diff image is written when a pixel comparator is available", async () => {
  const ctx = ctxWith({
    files: { "shio/snapshots/blog-blog-hello.png": Buffer.from("BEFORE") },
    compare: () => ({ ratio: 0.5, diff: Buffer.from("DIFF") }),
  });
  await snapshot.run({}, ["post:Blog/blog/hello"], ctx);

  assert.equal(String(ctx.written["shio/snapshots/blog-blog-hello.diff.png"]), "DIFF");
  assert.match(ctx.stdout.text, /diff → /);
});

test("--out redirects the whole set", async () => {
  const ctx = ctxWith();
  await snapshot.run({ out: "artifacts/shots" }, ["https://x.test/page"], ctx);

  assert.deepEqual(Object.keys(ctx.written), ["artifacts/shots/x.test-page.png"]);
});

test("no page named explains what to pass", async () => {
  await assert.rejects(() => snapshot.run({}, [], ctxWith()), /pass a page/);
});
