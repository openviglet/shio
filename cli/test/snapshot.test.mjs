/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
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

import {
  compareShapes,
  compareSnapshots,
  describeAgainst,
  describeGeometry,
  describeShapes,
  describe as describeResult,
  pageTarget,
  resolveUrl,
  slugFor,
} from "../src/snapshot.mjs";
import * as snapshot from "../src/commands/snapshot.mjs";

function sink() {
  const out = { text: "" };
  out.write = (s) => {
    out.text += s;
  };
  return out;
}

const CONTEXT = { sites: [{ name: "Blog", url: "https://blog.example.com/" }, { name: "Docs", url: "" }] };

/**
 * SH444 — `compare: null` by default, which is what these tests were always getting.
 *
 * <p>The captures below are strings, not PNGs, and until `pixelmatch`/`pngjs` were declared at the
 * root they were never parsed: no comparator resolved, so every one of these ran the byte-only path
 * *by absence*. Declaring them turned eleven of these red with a pngjs stack trace. Saying `null` is
 * the same behaviour those assertions were written against, now stated rather than inherited — and a
 * test that wants the pixel path passes a `compare` function, as two below do.</p>
 */
function ctxWith({ files = {}, capture, compare = null } = {}) {
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

/** The measured pair: a replica 4091px tall against a source 6076px, identical where they overlap. */
const SHORTER = {
  before: { width: 1280, height: 6076 },
  after: { width: 1280, height: 4091 },
  compared: { width: 1280, height: 4091 },
};

test("SH866: two pages of different heights are scored over what they have in common", () => {
  // The comparator used to answer `ratio: 1` for any size mismatch and stop, so the one page a
  // reader most needs scored — a replica a third shorter than its source — read `100.00% of pixels`:
  // nothing matched, of a page whose first screen is identical. A fabricated magnitude is the number
  // a caller builds a threshold on, which is the objection `compareSnapshots` already makes of the
  // byte path two branches down.
  const result = compareSnapshots(Buffer.from("X"), Buffer.from("Y"), {
    compare: () => ({ ratio: 0, diff: Buffer.from("EMPTY"), geometry: SHORTER }),
  });

  assert.equal(result.ratio, 0, "the overlap is what was compared, and it matched");
  assert.equal(result.status, "changed",
    "and the two pages are NOT the same, which has to be said here because the ratio no longer says "
      + "it — a replica two thousand pixels short answering `same` is the defect this fix would "
      + "otherwise have introduced at the other end");
  assert.deepEqual(result.geometry, SHORTER);
});

test("SH866: the clause says what was compared and which side is short", () => {
  assert.equal(describeGeometry(SHORTER),
    "compared over 1280×4091 — the replica is 1,985px shorter",
    "two numbers a reader can act on, against one that says only `different`");
  assert.equal(describeGeometry(SHORTER, { source: "the baseline", replica: "this run" }),
    "compared over 1280×4091 — this run is 1,985px shorter",
    "each label carries its own article: a template prepending `the` produced `the this run is` on "
      + "this function's first measurement");

  const taller = { ...SHORTER, after: { width: 1280, height: 8000 } };
  assert.match(describeGeometry(taller), /the source is 1,924px shorter/,
    "and the reading is one-directional the way `compareShapes` is: a replica TALLER than its "
      + "source has gained something — a Shio layout composing a nav the source built in script — "
      + "which sends a reader somewhere else entirely");

  assert.equal(describeGeometry({ ...SHORTER, after: SHORTER.before }), null,
    "and two pages of one size have nothing to say, so the ordinary case gains no clause");
  assert.equal(describeGeometry(undefined), null, "nor does the byte path, which knows none of it");
});

test("SH866: the row a reader takes away carries the region the percentage is of", () => {
  const line = describeAgainst([{
    width: 1280,
    status: "changed",
    ratio: 0.0676,
    resolution: "pixel",
    geometry: SHORTER,
    sourcePath: "a.png",
    replicaPath: "b.png",
  }], { source: "https://x.test/", page: "post:VigR7/" });

  assert.match(line, /6\.76% of pixels \(pixel\)\s+compared over 1280×4091 — the replica is 1,985px shorter/,
    "a percentage without its region is a claim about a whole page that was not compared — which is "
      + "the same objection SH443 made of `worst 100.00%` on the line above it");
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

test("SH442: a slug in the URL field is not a front end, so it is photographed through the preview", () => {
  // The third state, and the one the fixtures did not have. `Docs` has no origin and `Blog` has an
  // absolute one — two states, while a site's URL field holds three: the slug is what any site
  // created without a public URL gets. It is truthy, so `if (options.preview || !origin)` took the
  // front-end branch and handed `cafeteria` to the browser as an absolute address. Measured:
  // playwright's own `Cannot navigate to invalid URL`, call log `navigating to "viglet2/"`.
  const sites = new Map([
    ["Blog", "https://blog.example.com/"],
    ["Docs", ""],
    ["Cafeteria", "cafeteria"],
  ]);
  const at = { previewBase: "http://localhost:2710" };

  const slug = resolveUrl("post:Cafeteria/menu", sites, at);
  assert.equal(slug.url, "http://localhost:2710/preview/Cafeteria/menu");
  assert.equal(slug.preview, true, "SH265's answer, which this predicate was the one place not applying");

  // The home page is the address the defect was reported on, and `post:<site>/` is its form (SH433).
  assert.equal(
    resolveUrl("post:Cafeteria/", sites, at).url,
    "http://localhost:2710/preview/Cafeteria/",
  );

  // The other two states are unchanged: this is a predicate correction, not a new policy.
  assert.equal(resolveUrl("post:Blog/blog/hello", sites, at).url, "https://blog.example.com/blog/hello");
  assert.equal(resolveUrl("post:Docs/guide", sites, at).url, "http://localhost:2710/preview/Docs/guide");
});

test("SH442: with no preview to fall back to, the refusal names the slug rather than claiming no URL", () => {
  // A non-absolute site URL deserves a teaching refusal, not a protocol error — and not a refusal
  // that misreports the cause either: "has no front-end URL configured" is simply false for a site
  // whose field holds `cafeteria`, and a reader who believes it goes and sets the field it already has.
  const sites = new Map([["Cafeteria", "cafeteria"], ["Docs", ""]]);

  const slug = resolveUrl("post:Cafeteria/menu", sites, {}).error;
  assert.match(slug, /`cafeteria` in its URL field, which is not an absolute http\(s\) URL/);
  assert.match(slug, /cannot fall back to its preview/);

  assert.match(
    resolveUrl("post:Docs/guide", sites, {}).error,
    /has no front-end URL configured/,
    "and the empty case keeps the sentence that is true of it",
  );
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
  //
  // SH450 — and this assertion is the reason that defect shipped, so it is worth saying what it
  // does *not* cover. It asserts the credentials were handed to the **injected capture**: a fact
  // about this command's plumbing, and none at all about whether a browser got in. It did not, for
  // a reason nothing here can see (Playwright answers a challenge the preview was not sending), and
  // the gate photographed the 401 and scored it 100% against the real site. The two ends of that
  // are `ShPreviewChallengeTest` and `refuseNon2xx` below.
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

// --- SH365: one flag, one meaning ---

test("SH365: --page names the page and --url stays the instance", () => {
  const warnings = [];
  const warn = (message) => warnings.push(message);

  assert.equal(pageTarget({ page: "post:Blog/x" }, [], warn), "post:Blog/x");
  assert.equal(pageTarget({ address: "post:Blog/x" }, [], warn), "post:Blog/x");
  assert.equal(pageTarget({}, ["post:Blog/x"], warn), "post:Blog/x");
  // The case the defect was about: both given, which is the *correct* usage. `--url` must be read
  // only as the instance, and since SH265 it also decides which server a preview comes from.
  assert.equal(pageTarget({ page: "post:Blog/x", url: "http://localhost:2710" }, [], warn), "post:Blog/x");
  assert.deepEqual(warnings, [], "nothing was deprecated — no caller named the page with --url");
});

test("SH365: --url as the page still works, and says which flag to use", () => {
  const warnings = [];
  const target = pageTarget({ url: "https://site.test/page" }, [], (message) => warnings.push(message));

  // A rename with a deprecation, not a break: `snapshot --url <page>` was documented and is
  // somebody's script.
  assert.equal(target, "https://site.test/page");
  assert.equal(warnings.length, 1);
  assert.match(warnings[0], /--page https:\/\/site\.test\/page/);
  assert.match(warnings[0], /means the instance in every other/);
});

test("SH365: a command with no legacy reading refuses --url as the page", () => {
  // No `warn` is how the helper is told there is nothing to deprecate. Accepting it in `audit` —
  // which never documented it — would mean `--url http://localhost:2710` audits the instance root
  // instead of refusing, which is the plausible-artifact failure the task is about.
  assert.equal(pageTarget({ url: "http://localhost:2710" }, []), undefined);
  assert.equal(pageTarget({}, []), undefined);
});

test("SH365: snapshot warns on stderr and still photographs the page", async () => {
  const ctx = ctxWith();
  const result = await snapshot.run({ url: "https://x.test/page" }, [], ctx);

  assert.equal(result.url, "https://x.test/page");
  assert.match(ctx.stderr.text, /shio snapshot: --url named the page here, which is deprecated/);
  // stdout stays the artifact's own report — a deprecation is not part of what the command produces.
  assert.doesNotMatch(ctx.stdout.text, /deprecated/);
});

// --- SH386: the baseline a replication needs is somebody else's site ---

/** A capture keyed on origin, so a comparison can be made to match or to differ deliberately. */
function twoSites({ same = false } = {}) {
  const seen = [];
  return {
    seen,
    capture: async (url, flags, credentials) => {
      seen.push({ url, width: flags.width, credentials });
      if (same) return Buffer.from("IDENTICAL");
      return Buffer.from(url.includes("acme.example.com") ? "SOURCE" : "REPLICA");
    },
  };
}

test("SH386: --against captures both sides in one run, at each width", async () => {
  const { seen, capture } = twoSites();
  const ctx = ctxWith({ capture });

  const result = await snapshot.run(
    { against: "https://acme.example.com/", width: "1280,375" },
    ["post:Blog/"],
    ctx,
  );

  // Both sides, both widths, and the *original first* at each — so a failure fetching somebody
  // else's site is reported before the replica is photographed.
  assert.deepEqual(seen.map((each) => `${each.width}:${new URL(each.url).host}`), [
    "375:acme.example.com", "375:blog.example.com",
    "1280:acme.example.com", "1280:blog.example.com",
  ]);
  assert.equal(result.widths.length, 2);
});

test("SH386: the source capture is kept in the tree, which is what makes the number reviewable", async () => {
  const { capture } = twoSites();
  const ctx = ctxWith({ capture });

  await snapshot.run({ against: "https://acme.example.com/", width: "1280" }, ["post:Blog/"], ctx);

  const paths = Object.keys(ctx.written).map((each) => each.replace(/\\/g, "/"));
  // A pull request shows what the replica was compared against. Without the source image the number
  // is unreproducible *and* unreviewable; with it, at least a human can look.
  assert.ok(paths.some((each) => each.endsWith("shio/snapshots/blog@1280.source.png")), paths.join(" "));
  assert.ok(paths.some((each) => each.endsWith("shio/snapshots/blog@1280.replica.png")));
});

test("SH386: a difference exits 1 — the one place SH102's rule inverts", async () => {
  const { capture } = twoSites();
  const ctx = ctxWith({ capture });

  const result = await snapshot.run({ against: "https://acme.example.com/" }, ["post:Blog/"], ctx);

  // No --fail-on-change to remember: nobody runs this command except to find out whether the
  // replica matches, so a mismatch is the defect rather than the change you just made.
  assert.equal(result.exitCode, 1);
  assert.match(ctx.stdout.text, /1 width\(s\), 1 differing/);
});

test("SH386: a matching replica exits 0", async () => {
  const { capture } = twoSites({ same: true });
  const ctx = ctxWith({ capture });

  const result = await snapshot.run({ against: "https://acme.example.com/" }, ["post:Blog/"], ctx);

  assert.equal(result.exitCode, 0);
  assert.match(ctx.stdout.text, /0 differing/);
});

test("SH386: a per-pixel comparator gives a magnitude per width, and the worst one is in the header", async () => {
  const { capture } = twoSites();
  const ctx = ctxWith({
    capture,
    compare: (before, after) => ({
      // Wider viewport, bigger difference — so the header has to name which width was worst.
      ratio: after.length > 0 && before.length > 0 ? 0.25 : 0,
      diff: Buffer.from("DIFF"),
    }),
  });

  const result = await snapshot.run(
    { against: "https://acme.example.com/", width: "375,1280" },
    ["post:Blog/"],
    ctx,
  );

  assert.deepEqual(result.widths.map((row) => row.ratio), [0.25, 0.25]);
  assert.match(ctx.stdout.text, /worst 25\.00% at \d+px/);
  assert.match(ctx.stdout.text, /diff → /);
});

test("SH386: with no comparator the run says the byte answer is nearly meaningless here", async () => {
  const { capture } = twoSites();
  const ctx = ctxWith({ capture });

  await snapshot.run({ against: "https://acme.example.com/" }, ["post:Blog/"], ctx);

  // Two browsers photographing two origins almost never produce identical bytes, so a difference at
  // byte resolution is unmeasured rather than a magnitude — and a reader has to be told.
  assert.match(ctx.stdout.text, /no per-pixel comparator/);
  assert.match(ctx.stdout.text, /treat a difference here as unmeasured/);

  // SH443 — and the summary line has to agree with the row under it. It used to read
  // `1 differing, worst 100.00% at 1280px` four lines above `differs (bytes only — no magnitude)`:
  // a percentage nothing computed, because the reducer read a null ratio as 1 for anything not
  // `same`. It is the line a reader takes away and an agent greps, and "worst 100.00%" about a
  // replica that may be all but identical is the most misleading thing this command could say.
  assert.match(ctx.stdout.text, /1 width\(s\), 1 differing \(unmeasured\)/);
  assert.doesNotMatch(ctx.stdout.text, /worst/,
    "no percentage at all, rather than a smaller wrong one: there is no comparator to produce it");
});

test("SH443: a measured run still names the worst width, and a match claims nothing", async () => {
  // The other two cases the header has to tell apart. "Nothing measured it" and "measured, and
  // zero" are different claims, and only the first is unmeasured.
  const measured = ctxWith({
    capture: twoSites().capture,
    compare: () => ({ ratio: 0.25, diff: Buffer.from("DIFF") }),
  });
  await snapshot.run({ against: "https://acme.example.com/", width: "375,1280" }, ["post:Blog/"], measured);
  assert.match(measured.stdout.text, /2 differing, worst 25\.00% at \d+px/);
  assert.doesNotMatch(measured.stdout.text, /unmeasured\)/);

  const same = ctxWith({ capture: twoSites({ same: true }).capture });
  await snapshot.run({ against: "https://acme.example.com/" }, ["post:Blog/"], same);
  assert.match(same.stdout.text, /0 differing$/m,
    "nothing differs, so there is nothing to qualify — neither a percentage nor `(unmeasured)`");
});

test("SH386: --against wants an absolute URL, because the instance never fetches the original", async () => {
  const ctx = ctxWith({ capture: twoSites().capture });

  await assert.rejects(
    () => snapshot.run({ against: "/about" }, ["post:Blog/"], ctx),
    /--against takes an absolute URL[\s\S]*never fetches \(SH268\)/,
  );
});

test("SH386: several widths in the baseline mode are refused, naming --against", async () => {
  const ctx = ctxWith();

  // Refused rather than silently using the first: `<slug>.png` is a file every project holds, so a
  // second width here would orphan it or need a conditional name (SH379's rule).
  await assert.rejects(
    () => snapshot.run({ width: "1280,375" }, ["post:Blog/"], ctx),
    /--width takes one viewport here, not 2[\s\S]*--against/,
  );
});

test("SH386: the baseline mode is unchanged by all of this", async () => {
  const ctx = ctxWith({ files: { "shio/snapshots/blog.png": Buffer.from("AFTER") } });

  const result = await snapshot.run({}, ["post:Blog/"], ctx);

  assert.equal(result.status, "same");
  assert.equal(result.exitCode, 0);
  assert.ok(Object.keys(ctx.written).some((each) => each.replace(/\\/g, "/")
    .endsWith("shio/snapshots/blog.png")));
});

test("SH444: the real pixelmatch/pngjs comparator runs, which the checkout could not do before", async () => {
  // Every other test here injects `compare`, so the wiring between this command and the two optional
  // helpers had no coverage at all — and could not have: they were in no lockfile. Declaring them at
  // the root is what makes this test possible, so it is the one that would notice them going away
  // again in a way `browser-refusal.test.mjs`'s resolution guard cannot: by using them.
  const { PNG } = await import("pngjs");
  const png = (shade) => {
    const image = new PNG({ width: 4, height: 4 });
    for (let i = 0; i < image.data.length; i += 4) {
      // One pixel of sixteen differs, so the ratio is a number this test can name exactly.
      const value = i === 0 ? shade : 0;
      image.data[i] = value;
      image.data[i + 1] = value;
      image.data[i + 2] = value;
      image.data[i + 3] = 255;
    }
    return PNG.sync.write(image);
  };

  const ctx = ctxWith({
    files: { "shio/snapshots/blog.png": png(0) },
    capture: async () => png(255),
  });
  // Not `compare: undefined` — that is what a default parameter *is*, so it would take the `null`.
  // Deleting the key is how this context says "resolve for real", which is what `defaultContext`
  // does by never setting it.
  delete ctx.compare;

  const result = await snapshot.run({}, ["post:Blog/"], ctx);

  assert.equal(result.resolution, "pixel", "not `bytes` — the helpers resolved and did the work");
  assert.equal(result.ratio, 1 / 16, "one pixel of sixteen, measured rather than assumed");
  assert.match(ctx.stdout.text, /6\.25% of pixels/);
});

// --- SH450: a screenshot of an error is not a comparison ---

test("SH450: a capture refuses a page the browser could not open, and the 401 names why", () => {
  // The defect, in one number: `--against` reported `100.00% of pixels` on a replica whose markup
  // is the source's, verbatim. The PNG was a white page reading `{"error":"Unauthorized"}`, and
  // 100% is exactly what any unrelated image scores. A failure to *capture* must read as a failure
  // and never as a difference — the two have nothing in common but the digit.
  assert.throws(
    () => snapshot.refuseNon2xx({ status: () => 401 }, "http://shio.test/preview/Docs/", {
      user: "admin",
      password: "secret",
    }),
    (error) => {
      assert.match(error.message, /answered HTTP 401 \(as admin\)/);
      // The sentence a caller can act on, and the reason it is specific to 401: `httpCredentials`
      // answers a *challenge*, so credentials in hand plus a 401 means the server did not send one.
      // That is a fact about the instance, and "fix your password" would send a reader nowhere.
      assert.match(error.message, /WWW-Authenticate/);
      assert.match(error.message, /SH450/, "and names the version that emits it");
      return true;
    },
  );
});

test("SH450: a 2xx passes, and a 500 is refused without the credentials sentence", () => {
  assert.doesNotThrow(() => snapshot.refuseNon2xx({ status: () => 200 }, "http://x.test/", null));
  assert.doesNotThrow(
    () => snapshot.refuseNon2xx(null, "http://x.test/", null),
    "a null response is about:blank and same-document navigation — neither reaches this",
  );

  assert.throws(
    () => snapshot.refuseNon2xx({ status: () => 500 }, "http://x.test/", null),
    (error) => {
      assert.match(error.message, /answered HTTP 500 \(with no credentials\)/);
      assert.ok(
        !/WWW-Authenticate/.test(error.message),
        "the authentication sentence is for the status it explains — on a 500 it is noise pointing "
          + "at the wrong subsystem",
      );
      return true;
    },
  );
});

// --- SH526: the shape beside the pixels ---

/**
 * Four instruments passed a page that had lost its hero, its grid and its footer — and this command
 * was one of them, for a reason its own header states: it had **no baseline to compare**.
 *
 * <p>A pixel diff answers "identical to a baseline", which is exactly right for fidelity (0.00% is a
 * real gate) and structurally useless for authoring, where a page built from a blueprint has no
 * baseline at all. It also cannot tell a reader *which* large difference happened: an emptied page
 * and a re-cropped hero both move a lot of pixels. The tree can, in a sentence — and it survives
 * what pixels cannot: a font substitution, an animation caught mid-frame, a different viewport.</p>
 */
test("SH526: a replica missing a landmark has lost a region, whatever the percentage says", () => {
  const source = {
    landmarks: { main: 1, navigation: 1, contentinfo: 1 },
    headings: [{ level: 1, text: "Acme" }, { level: 2, text: "Products" }, { level: 2, text: "Contact" }],
  };
  const replica = { landmarks: { main: 1 }, headings: [{ level: 2, text: "Acme" }] };

  const comparison = compareShapes(source, replica);

  assert.deepEqual(comparison.lost, ["contentinfo", "navigation"]);
  assert.deepEqual(comparison.headings, { source: 3, replica: 1 });
  assert.equal(comparison.ok, false);
  assert.match(describeShapes(comparison), /lost contentinfo, navigation/);
  assert.match(describeShapes(comparison), /3 heading\(s\) became 1/);
});

test("SH526: a landmark the replica GAINED is not a defect", () => {
  // A Shio layout composes a `nav` the source built in script, so the comparison is one-directional
  // — the same rule `shio diff` makes for counts, and for the same reason: a gate that called any
  // difference damage would fire on every working conversion.
  const comparison = compareShapes(
    { landmarks: { main: 1 }, headings: [{ level: 1, text: "Acme" }] },
    { landmarks: { main: 1, navigation: 1 }, headings: [{ level: 1, text: "Acme" }, { level: 2, text: "More" }] },
  );

  assert.deepEqual(comparison.lost, []);
  assert.equal(comparison.ok, true);
  assert.match(describeShapes(comparison), /every landmark the source has/);
});

test("SH526: --against prints the shape beside the percentage", async () => {
  const { capture } = twoSites();
  const ctx = ctxWith({
    // The seam hands back `{bytes, shape}` the way the real capture now does; a fixture that hands
    // back a bare Buffer is the pre-SH526 contract and still works, which is what the other tests
    // in this file are.
    capture: async (url, flags, credentials) => ({
      bytes: await capture(url, flags, credentials),
      shape: url.includes("acme.example.com")
        ? { landmarks: { main: 1, contentinfo: 1 }, headings: [{ level: 1, text: "Acme" }] }
        : { landmarks: { main: 1 }, headings: [{ level: 1, text: "Acme" }] },
    }),
  });

  await snapshot.run({ against: "https://acme.example.com/" }, ["post:Blog/"], ctx);

  assert.match(ctx.stdout.text, /% of pixels|differing/,
    "the pixel answer is still there — the shape is beside it, never instead of it");
  assert.match(ctx.stdout.text, /shape: lost contentinfo/);
});

// --- SH209: the set, not the page ---

/**
 * A context whose instance answers both reads the sweep makes: the SH77 pack for the site's
 * public URL, and `/agent/find` for its pages.
 *
 * `capture` hands back `{bytes, shape}` — the shape the real capture returns since SH526, and
 * deliberately not the bare Buffer the older fixtures use. That difference is the whole reason
 * the single-page path could hand a plain object to `writeFile` while every test stayed green.
 */
function siteCtx({ pages, files = {}, capture, truncated = false } = {}) {
  const written = {};
  return {
    env: { SHIO_URL: "http://shio.test", SHIO_USER: "admin", SHIO_PASSWORD: "secret" },
    cwd: "/repo",
    stdout: sink(),
    stderr: sink(),
    written,
    compare: null,
    capture: capture ?? (async (url) => ({ bytes: Buffer.from(`SHOT ${url}`), shape: null })),
    async fetch(url) {
      const body = String(url).includes("/agent/find")
        ? { count: pages.length, truncated, results: pages }
        : CONTEXT;
      return {
        ok: true,
        status: 200,
        headers: { get: () => null },
        async text() {
          return JSON.stringify(body);
        },
        async json() {
          return body;
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
        if (!Buffer.isBuffer(body) && typeof body !== "string") {
          // The real `fs.writeFile` throws ERR_INVALID_ARG_TYPE here. The fixtures that accept
          // anything are why a plain object reached this call for two tasks without a red test.
          throw new TypeError(
            `ERR_INVALID_ARG_TYPE: "data" was ${Object.prototype.toString.call(body)}`,
          );
        }
        written[path.replace(/\\/g, "/")] = body;
      },
    },
  };
}

const PUBLISHED = [
  { address: "post:Blog/", type: "Page", published: true },
  { address: "post:Blog/about", type: "Page", published: true },
  { address: "post:Blog/draft-only", type: "Page" },
];

test("SH209: --site photographs every published page and leaves the drafts alone", async () => {
  const ctx = siteCtx({ pages: PUBLISHED });

  const result = await snapshot.run({ site: "Blog" }, [], ctx);

  assert.equal(result.pages, 2, "the draft has no URL a visitor could open, so it is not a page");
  assert.deepEqual(Object.keys(ctx.written).sort(), [
    "shio/snapshots/blog-about.png",
    "shio/snapshots/blog.png",
  ]);
  assert.match(ctx.stdout.text, /2 page\(s\) of 'Blog'/);
  assert.equal(result.exitCode, 0, "new baselines are not a failure");
});

test("SH209: an unreachable page is its own outcome, never an unchanged one", async () => {
  const ctx = siteCtx({
    pages: PUBLISHED,
    capture: async (url) => {
      if (url.includes("/about")) throw new Error("Timeout 30000ms exceeded");
      return { bytes: Buffer.from("SHOT"), shape: null };
    },
  });

  const result = await snapshot.run({ site: "Blog" }, [], ctx);

  assert.equal(result.unreachable, 1);
  assert.match(ctx.stdout.text, /UNREACHABLE {2}post:Blog\/about/);
  assert.match(ctx.stdout.text, /Timeout 30000ms exceeded/,
    "the reason travels: 'unreachable' with no cause is a row a reader has to reproduce by hand");
  assert.match(ctx.stdout.text, /1 unreachable/);
  assert.equal(result.exitCode, 1,
    "a page the browser could not open is not a change — it is the run failing to do what it " +
      "was asked, so it exits non-zero without --fail-on-change");
});

test("SH209: one bad page does not end the sweep", async () => {
  const ctx = siteCtx({
    pages: PUBLISHED,
    // The FIRST page in sorted order fails, so a sweep that stopped at the first error would
    // photograph nothing at all.
    capture: async (url) => {
      if (url.endsWith("/about")) throw new Error("net::ERR_CONNECTION_REFUSED");
      return { bytes: Buffer.from("SHOT"), shape: null };
    },
  });

  const result = await snapshot.run({ site: "Blog" }, [], ctx);

  assert.equal(result.pages, 2);
  assert.ok(
    Object.keys(ctx.written).includes("shio/snapshots/blog.png"),
    "the page after the failure was still photographed — losing every result after the first bad " +
      "page is the failure mode a set is supposed to remove",
  );
});

test("SH209: a changed page is named and an unchanged one is only counted", async () => {
  const ctx = siteCtx({
    pages: PUBLISHED,
    files: {
      "shio/snapshots/blog.png": Buffer.from("SHOT http://shio.test/x"),
      "shio/snapshots/blog-about.png": Buffer.from("OLD"),
    },
    capture: async () => ({ bytes: Buffer.from("OLD"), shape: null }),
  });

  await snapshot.run({ site: "Blog" }, [], ctx);

  assert.match(ctx.stdout.text, /changed {2}post:Blog\//);
  assert.doesNotMatch(ctx.stdout.text, /changed {2}post:Blog\/about/,
    "an unchanged page is a count, not a line — a list where everything is named is one nobody reads");
});

test("SH616: the single-page path writes bytes, not the capture envelope", async () => {
  // `capture` returns {bytes, shape} in real mode since SH526, and this path passed the whole
  // object to writeFile — so `shio snapshot <page>` threw ERR_INVALID_ARG_TYPE against a real
  // browser. Every existing fixture returns a bare Buffer, so nothing was red. The fixture above
  // returns the production envelope and its writeFile refuses a non-Buffer the way the real one
  // does, which is what makes this red on the code as it stood.
  const ctx = siteCtx({ pages: [] });

  await snapshot.run({}, ["post:Blog/blog/hello"], ctx);

  const written = ctx.written["shio/snapshots/blog-blog-hello.png"];
  assert.ok(Buffer.isBuffer(written), `expected bytes, got ${Object.prototype.toString.call(written)}`);
});

test("SH209: a page and --site together are refused rather than resolved by precedence", async () => {
  const ctx = siteCtx({ pages: PUBLISHED });

  await assert.rejects(
    () => snapshot.run({ site: "Blog" }, ["post:Blog/about"], ctx),
    /cannot be combined/,
  );
});
