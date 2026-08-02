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
  assert.match(resolveUrl("post:Other/x", sites).error, /not one this instance reports/);
  assert.match(resolveUrl("folder:Blog/blog", sites).error, /is not a page/);
});

test("SH265: a site with no front end is photographed through the instance's own preview", () => {
  const sites = new Map([["Blog", "https://blog.example.com/"], ["Docs", ""]]);
  const at = { previewBase: "http://localhost:2710" };

  // This used to be an error — "there is no page to photograph" — which Block P made false:
  // SH183 renders the page out of content the instance already holds, and SH184 stopped the
  // *server* saying the same thing. The refusal was that premise left standing one process on.
  const docs = resolveUrl("post:Docs/guide", sites, at);
  assert.equal(docs.url, "http://localhost:2710/preview/Docs/guide");
  assert.equal(docs.preview, true);

  // A site that has a front end still resolves against it, because the live page is what a
  // reader means by "what the site looks like".
  assert.equal(resolveUrl("post:Blog/blog/hello", sites, at).url, "https://blog.example.com/blog/hello");

  // …unless asked for the draft, which is the only thing a live URL cannot serve.
  const draft = resolveUrl("post:Blog/blog/hello", sites, { ...at, preview: true });
  assert.equal(draft.url, "http://localhost:2710/preview/Blog/blog/hello");
  assert.equal(draft.preview, true);

  // A site name is a path segment here, so it is encoded — a name with a slash would otherwise
  // photograph a different page and say nothing about it.
  const spaced = resolveUrl("post:My Site/x", new Map([["My Site", ""]]), at);
  assert.equal(spaced.url, "http://localhost:2710/preview/My%20Site/x");

  // And with nowhere to fall back to, it still refuses rather than inventing an origin.
  assert.match(resolveUrl("post:Docs/guide", sites, {}).error, /cannot fall back to its preview/);
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

test("SH265: a front-endless site is captured from the preview, with credentials and a note", async () => {
  const seen = [];
  const ctx = ctxWith({
    capture: async (url, _flags, credentials) => {
      seen.push({ url, credentials });
      return Buffer.from("AFTER");
    },
  });
  const result = await snapshot.run({}, ["post:Docs/guide"], ctx);

  assert.equal(result.url, "http://shio.test/preview/Docs/guide");
  assert.equal(seen[0].url, "http://shio.test/preview/Docs/guide");
  // SH359 closed the preview, so the browser has to authenticate like everything else the CLI
  // does. Without this the capture would be a photograph of the login page — which compares
  // stably against itself and would read as "unchanged" forever.
  assert.deepEqual(seen[0].credentials, { user: "admin", password: "secret" });
  // The reader is told the picture is a DRAFT with a banner on it, because the image cannot say
  // so and a reviewer diffing it against production would otherwise blame the stripe on the site.
  assert.match(ctx.stdout.text, /preview: http:\/\/shio\.test\/preview\/Docs\/guide \(DRAFT/);
});

test("SH265: a site with a front end is captured from it, and gets no credentials", async () => {
  const seen = [];
  const ctx = ctxWith({
    capture: async (url, _flags, credentials) => {
      seen.push({ url, credentials });
      return Buffer.from("AFTER");
    },
  });
  await snapshot.run({}, ["post:Blog/blog/hello"], ctx);

  assert.equal(seen[0].url, "https://blog.example.com/blog/hello");
  // The one that matters: a public host must never be handed the instance's admin password
  // because one command happens to photograph both.
  assert.equal(seen[0].credentials, null);
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
