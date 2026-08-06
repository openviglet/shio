/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH378 — the bytes, and the four rules that make downloading them not quite mechanical.
 *
 * The name rules get the most tests here on purpose. Every other input to a path in this tree comes
 * from Shio's own database; this one comes from **a stranger's markup**, so it is the only producer
 * where a hostile value is the expected case rather than the pathological one. `path-safety.test.mjs`
 * asks the SH403/SH408 questions of it alongside every other producer; this file asks the ones that
 * are specific to a URL.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import {
  BEHAVIOUR,
  DOWNLOADABLE,
  assetsReferencedBy,
  capturedAssetPathFor,
  describeDownloads,
  kindForUrl,
  placeAsset,
  planAssets,
  sha256Of,
  withDownloads,
} from "../src/clone-assets.mjs";
import * as clone from "../src/commands/clone.mjs";

function sink() {
  const out = { text: "" };
  out.write = (chunk) => {
    out.text += chunk;
  };
  return out;
}

// --- the path ---

test("the folder chain is the source's own path, so the tree reads like the site", () => {
  assert.equal(capturedAssetPathFor("https://x.test/img/hero.png", "Blog"), "Blog/img/hero.png");
  assert.equal(capturedAssetPathFor("https://x.test/logo.png", "Blog"), "Blog/logo.png");
  assert.equal(
    capturedAssetPathFor("https://x.test/assets/fonts/inter/Inter.woff2", "Acme"),
    "Acme/assets/fonts/inter/Inter.woff2",
  );
  // A query is not part of a file name — `site.css?v=3` is one stylesheet, and two versions of it
  // are answered by the digest, not by two paths.
  assert.equal(capturedAssetPathFor("https://x.test/css/site.css?v=3", "Blog"), "Blog/css/site.css");
});

test("percent-encoding is decoded for readability, before it is judged", () => {
  // The tree should hold the name a person would type, not the wire form.
  assert.equal(capturedAssetPathFor("https://x.test/img/h%C3%A9ro.png", "Blog"), "Blog/img/héro.png");
  assert.equal(capturedAssetPathFor("https://x.test/img/a%20b.png", "Blog"), "Blog/img/a b.png");
  // And an escape that decodes to a separator is refused, which is the whole reason the order is
  // decode-then-validate. A parser preserves %2f deliberately; decoding it re-creates the structure.
  assert.equal(capturedAssetPathFor("https://x.test/img/a%2fb.png", "Blog"), null);
  assert.equal(capturedAssetPathFor("https://x.test/img/a%5cb.png", "Blog"), null);
  // A malformed escape has no one reading, and guessing between two readings would put two names on
  // one asset.
  assert.equal(capturedAssetPathFor("https://x.test/img/%zz.png", "Blog"), null);
});

test("a name a path cannot hold is refused, never trimmed (SH256/SH257)", () => {
  const refused = [
    ["https://x.test/img/%20hero.png", "leading space — trimming would write a name that is not the name"],
    ["https://x.test/img/hero.png%20", "trailing space"],
    ["https://x.test/img/hero.png.", "a trailing dot, which Windows drops silently"],
    ["https://x.test/img/%2e", "a single dot is a directory, not a name"],
    ["https://x.test/img/a%3Ab.png", "a colon — legal on POSIX, not on Windows, and the tree must move"],
    ["https://x.test/img/a%3Cb.png", "an angle bracket"],
    ["https://x.test/img/a%7Cb.png", "a pipe"],
    ["https://x.test/img/nul.png", "a Windows device name, which a file cannot be called"],
    ["https://x.test/img/COM1", "another, uppercase"],
    ["https://x.test/img/", "a directory URL names no bytes"],
    ["https://x.test/", "and neither does the origin root"],
    [`https://x.test/img/${"a".repeat(300)}.png`, "past what any filesystem holds"],
  ];
  for (const [url, why] of refused) {
    assert.equal(capturedAssetPathFor(url, "Blog"), null, `should refuse ${url} — ${why}`);
  }
  // The site name goes through the same rules, because it is the first segment of the same path.
  assert.equal(capturedAssetPathFor("https://x.test/img/hero.png", ".."), null);
  assert.equal(capturedAssetPathFor("https://x.test/img/hero.png", "Blog/x"), null);
  assert.equal(capturedAssetPathFor("https://x.test/img/hero.png", ""), null);
});

// --- the plan ---

const INVENTORY = {
  version: 1,
  source: { origin: "https://x.test", seed: "https://x.test" },
  crawl: { depth: 3, maxPages: 200, rendered: false, pages: 2, truncated: false, robots: {} },
  pages: [{ path: "/", title: "Home" }],
  assets: [
    { path: "/img/hero.png", kind: "image", external: false, refs: 2 },
    { path: "/img/hero-copy.png", kind: "image", external: false, refs: 1 },
    { path: "/css/site.css", kind: "stylesheet", external: false, refs: 2 },
    { path: "/fonts/Inter.woff2", kind: "font", external: false, refs: 2 },
    { path: "/img/a%2fb.png", kind: "image", external: false, refs: 1 },
    { path: "/docs/paper.pdf", kind: "document", external: false, refs: 1 },
    { path: "https://cdn.other.test/img/shared.png", kind: "image", external: true, refs: 4 },
    { path: "https://fonts.gstatic.com/s/inter.woff2", kind: "font", external: true, refs: 1 },
  ],
  locales: [],
  forms: [],
  navigation: [],
  scripts: [],
  outbound: [],
  problems: [],
  notes: [],
};

test("the plan is same-origin, renderable kinds only, and names what it left behind", () => {
  const { plan, skipped, external } = planAssets(INVENTORY, { site: "Blog" });

  // Most-referenced first, ties by path — the order decides which of two identical files becomes the
  // canonical one, so it is asserted rather than left to the inventory's own sort.
  assert.deepEqual(plan.map((each) => each.file), [
    "Blog/css/site.css",
    "Blog/fonts/Inter.woff2",
    "Blog/img/hero.png",
    "Blog/img/hero-copy.png",
  ]);
  const reasons = new Map(skipped.map((each) => [each.source, each.reason]));
  // Off-origin is a licensing decision this tool is not entitled to make.
  assert.equal(reasons.get("https://cdn.other.test/img/shared.png"), "off-origin");
  assert.equal(reasons.get("https://fonts.gstatic.com/s/inter.woff2"), "off-origin");
  assert.equal(reasons.get("/docs/paper.pdf"), "not a renderable asset");
  assert.equal(reasons.get("/img/a%2fb.png"), "no safe path for this name");
  // The hosts are named, so somebody can make the decision the tool declined to.
  assert.deepEqual(external, [
    { host: "cdn.other.test", refs: 1 },
    { host: "fonts.gstatic.com", refs: 1 },
  ]);
});

test("--max-assets is a declared cut, not a silent one", () => {
  const { plan, skipped } = planAssets(INVENTORY, { site: "Blog", maxAssets: 2 });

  assert.equal(plan.length, 2);
  assert.equal(skipped.filter((each) => each.reason === "past --max-assets").length, 2);
});

test("the downloadable kinds are the ones a replica needs to render", () => {
  // Asserted rather than described. `script` is deliberately absent: rendering and working are two
  // lists, and SH469's `BEHAVIOUR` is the second one — opt-in, and reported when it is not taken.
  assert.deepEqual([...DOWNLOADABLE].sort(), ["font", "icon", "image", "manifest", "media", "stylesheet"]);
  assert.ok(!DOWNLOADABLE.has("script"));
  // SH515 — `data` is a file a `fetch()` names, and it is on the behaviour side for the reason the
  // split already states: a script requests it and nothing else does, so without --scripts there is
  // no bundle to ask for it and downloading it would fetch bytes nobody will ever serve. With the
  // flag it is exactly as load-bearing as the bundle — the replica that produced the task crashed
  // on a 404 body rather than merely missing an image.
  assert.deepEqual([...BEHAVIOUR].sort(), ["data", "script"]);
});

// --- SH469: the replica keeps the appearance and drops the behaviour ---

/** A site whose page is a bundle plus a third-party tag — the shape `www.viglet.org` has. */
const REACT_SITE = {
  version: 1,
  source: { origin: "https://x.test", seed: "https://x.test" },
  crawl: { depth: 3, maxPages: 200, rendered: true, pages: 1, truncated: false, robots: {} },
  pages: [{ path: "/", title: "Home" }],
  assets: [
    { path: "/css/site.css", kind: "stylesheet", external: false, refs: 1 },
    { path: "/assets/index-LNwgBwpz.js", kind: "script", external: false, refs: 1 },
    { path: "https://forms.cognitoforms.com/f/x.js", kind: "script", external: true, refs: 1 },
  ],
  locales: [], forms: [], navigation: [], scripts: [], outbound: [], problems: [], notes: [],
};

test("SH469: the site's own bundle is skipped as behaviour, not as an unrenderable file", () => {
  const { plan, skipped } = planAssets(REACT_SITE, { site: "Viglet" });

  assert.deepEqual(plan.map((each) => each.source), ["/css/site.css"]);
  const reasons = new Map(skipped.map((each) => [each.source, each.reason]));
  assert.equal(
    reasons.get("/assets/index-LNwgBwpz.js"),
    "behaviour, not captured without --scripts",
    "`not a renderable asset` is true of a script and says the wrong thing about it: a .docx the "
      + "crawl saw is not part of the page, and the site's own bundle IS the page's behaviour",
  );
  assert.equal(reasons.get("https://forms.cognitoforms.com/f/x.js"), "off-origin");
});

test("SH469: --scripts takes the site's own bundle and still refuses somebody else's", () => {
  const { plan, skipped } = planAssets(REACT_SITE, { site: "Viglet", scripts: true });

  assert.deepEqual(plan.map((each) => each.source).sort(),
    ["/assets/index-LNwgBwpz.js", "/css/site.css"]);
  assert.equal(
    skipped.find((each) => each.source.startsWith("https://")).reason,
    "off-origin",
    "SH378's decision is untouched by this one — a flag about behaviour is not a flag about "
      + "mirroring another host's bytes",
  );
});

test("SH469: a capture that discards behaviour says so, in behaviour's terms", () => {
  const { skipped, external } = planAssets(REACT_SITE, { site: "Viglet" });
  const folded = withDownloads(REACT_SITE, { site: "Viglet", results: [], skipped, external });

  // The whole finding: every instrument agreed the replica was faithful — the clone reported
  // `rendered`, the proposal was silent, `verify` was silent, and the snapshot said 0.33%. All of
  // them measure the photograph. The counting was already here; the sentence was not.
  const note = folded.notes.find((each) => each.includes("script(s) were NOT captured"));
  assert.ok(note, `no note named the discarded behaviour. Notes: ${JSON.stringify(folded.notes)}`);
  assert.match(note, /render and not respond/);
  assert.match(note, /1 of them belong to this site/);
  assert.match(note, /--scripts/);
  assert.match(note, /index-LNwgBwpz\.js/);
});

test("SH469: with --scripts the note is only about what is genuinely not ours", () => {
  const { skipped, external } = planAssets(REACT_SITE, { site: "Viglet", scripts: true });
  const folded = withDownloads(REACT_SITE, { site: "Viglet", results: [], skipped, external });

  const note = folded.notes.find((each) => each.includes("script(s) were NOT captured"));
  assert.match(note, /All of them are off-origin/);
  assert.ok(!note.includes("belong to this site"));
});

// --- placement: dedupe, collision, soft-404 ---

function state() {
  return { byDigest: new Map(), byPath: new Map() };
}

test("one logo served from three URLs becomes one asset (SH210)", () => {
  const at = state();
  const bytes = new Uint8Array([1, 2, 3]);

  const first = placeAsset(at, { source: "/img/logo.png", file: "Blog/img/logo.png", bytes, kind: "image", contentType: "image/png" });
  const second = placeAsset(at, { source: "/assets/logo.png", file: "Blog/assets/logo.png", bytes, kind: "image", contentType: "image/png" });
  const third = placeAsset(at, { source: "/logo.png?v=2", file: "Blog/logo.png", bytes, kind: "image", contentType: "image/png" });

  assert.equal(first.action, "wrote");
  assert.equal(second.action, "deduped");
  assert.equal(third.action, "deduped");
  // Both later references point at the first file: three paths for one logo would mean a curator
  // replacing "the logo" replaces a third of it.
  assert.equal(second.file, "Blog/img/logo.png");
  assert.equal(third.sameAs, "Blog/img/logo.png");
  assert.equal(at.byPath.size, 1);
});

test("one path wanted by two different files keeps the first and names both", () => {
  const at = state();
  placeAsset(at, { source: "/logo.png?v=1", file: "Blog/logo.png", bytes: new Uint8Array([1]), kind: "image", contentType: "image/png" });
  const clash = placeAsset(at, {
    source: "/logo.png?v=2",
    file: "Blog/logo.png",
    bytes: new Uint8Array([2]),
    kind: "image",
    contentType: "image/png",
  });

  // There is no honest way to keep both under a name the source chose, and overwriting silently is
  // the one outcome P7 rules out.
  assert.equal(clash.action, "collision");
  assert.equal(at.byPath.get("Blog/logo.png"), sha256Of(new Uint8Array([1])));
});

test("an HTML page where an image was expected is a soft 404, not an asset", () => {
  const at = state();
  const result = placeAsset(at, {
    source: "/img/missing.png",
    file: "Blog/img/missing.png",
    bytes: "<!doctype html><title>404</title>",
    kind: "image",
    contentType: "text/html; charset=utf-8",
  });

  // Writing it would put a 404 page in the media library under a .png name, which every later check
  // reads as a present asset. It is the one failure this pass can detect and the server cannot.
  assert.equal(result.action, "soft-404");
  assert.equal(at.byPath.size, 0);
  // A manifest legitimately is a document, so the check does not apply to it.
  assert.equal(
    placeAsset(state(), { source: "/m.json", file: "Blog/m.json", bytes: "{}", kind: "manifest", contentType: "text/html" }).action,
    "wrote",
  );
});

// --- the inventory write-back ---

test("the outcome folds back into the one inventory, so the URL→path map has one home", () => {
  const results = [
    { action: "wrote", source: "/img/hero.png", file: "Blog/img/hero.png", digest: "aaa", bytes: 12 },
    { action: "deduped", source: "/img/hero-copy.png", file: "Blog/img/hero.png", digest: "aaa", sameAs: "Blog/img/hero.png" },
    { action: "soft-404", source: "/css/site.css", file: "Blog/css/site.css", detail: "served text/html, expected a stylesheet" },
  ];
  const skipped = [{ source: "https://cdn.other.test/img/shared.png", reason: "off-origin" }];
  const external = [{ host: "cdn.other.test", refs: 4 }];

  const updated = withDownloads(INVENTORY, { site: "Blog", results, skipped, external });
  const byPath = new Map(updated.assets.map((each) => [each.path, each]));

  assert.equal(byPath.get("/img/hero.png").file, "Blog/img/hero.png");
  assert.equal(byPath.get("/img/hero.png").sha256, "aaa");
  assert.equal(byPath.get("/img/hero-copy.png").dedupedWith, "Blog/img/hero.png");
  assert.match(byPath.get("/css/site.css").unavailable, /served text\/html/);
  assert.equal(byPath.get("https://cdn.other.test/img/shared.png").unavailable, "off-origin");
  assert.deepEqual(updated.download, { site: "Blog", wrote: 1, deduped: 1, skipped: 1, failed: 1, bytes: 12 });
  // A licensing decision somebody has to make is a note, not a silence.
  assert.ok(updated.notes.some((note) => /licensing decision/.test(note)));
  assert.ok(updated.notes.some((note) => /soft 404/.test(note)));
});

test("the report names what landed where, bounded", () => {
  const updated = withDownloads(INVENTORY, {
    site: "Blog",
    results: [{ action: "wrote", source: "/img/hero.png", file: "Blog/img/hero.png", digest: "a", bytes: 2048 }],
    skipped: [],
    external: [],
  });

  const text = describeDownloads(updated, { limit: 5 });
  assert.match(text, /assets: wrote 1, deduped 0, skipped 0, unavailable 0 \(2\.0 KB into shio\/assets\/Blog\/\)/);
  assert.match(text, /\/img\/hero\.png → Blog\/img\/hero\.png/);
  // No download pass, no section — rather than a zeroed one that reads as a pass.
  assert.equal(describeDownloads(INVENTORY), "");
});

// --- the command ---

const PAGE = `<html lang="en"><head><title>Home</title>
<link rel="stylesheet" href="/css/site.css"></head>
<body><p>${"word ".repeat(60)}</p>
<img src="/img/hero.png"><img src="/img/hero.png">
<img src="/img/hero-again.png">
<img src="https://cdn.other.test/x.png">
<img src="/img/broken.png">
</body></html>`;

function ctxWith({ bodies = {} } = {}) {
  const written = {};
  const requested = [];
  const site = {
    "/robots.txt": { type: "text/plain", body: "User-agent: *\nDisallow:\n" },
    "/": { body: PAGE },
    "/css/site.css": { type: "text/css", body: "body{color:red}" },
    "/img/hero.png": { type: "image/png", body: "PNGBYTES" },
    // The same bytes under a second URL — the dedupe case, and the common one in a real site.
    "/img/hero-again.png": { type: "image/png", body: "PNGBYTES" },
    "/img/broken.png": { type: "text/html", body: "<!doctype html><title>404</title>" },
    ...bodies,
  };
  return {
    env: {},
    cwd: "/repo",
    stdout: sink(),
    stderr: sink(),
    written,
    requested,
    async fetch(url) {
      requested.push(url);
      const parsed = new URL(url);
      const entry = site[`${parsed.pathname}${parsed.search}`] ?? site[parsed.pathname];
      if (!entry) return answer({ status: 404, body: "gone" });
      return answer(entry);
    },
    fs: {
      async mkdir() {},
      async writeFile(path, body) {
        written[path.replace(/\\/g, "/")] = body;
      },
      async readFile(path) {
        const error = new Error(`ENOENT: ${path}`);
        error.code = "ENOENT";
        throw error;
      },
    },
  };
}

function answer({ status = 200, body = "", type = "text/html; charset=utf-8" } = {}) {
  const bytes = Buffer.from(body);
  const headers = new Map([["content-type", type], ["content-length", String(bytes.length)]]);
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (name) => headers.get(String(name).toLowerCase()) ?? null },
    async text() {
      return body;
    },
    async arrayBuffer() {
      return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength);
    },
  };
}

test("--assets writes the bytes into the tree beside the inventory", async () => {
  const ctx = ctxWith();
  const { inventory } = await clone.run({ assets: true, site: "Blog", depth: 0 }, ["https://x.test"], ctx);

  const files = Object.keys(ctx.written).sort();
  assert.deepEqual(files, [
    "/repo/shio/.shio/source-inventory.json",
    "/repo/shio/assets/Blog/css/site.css",
    "/repo/shio/assets/Blog/img/hero.png",
  ]);
  // The path a `File` field will hold, which is what makes `push --content` upload it.
  const hero = inventory.assets.find((each) => each.path === "/img/hero.png");
  assert.equal(hero.file, "Blog/img/hero.png");
  assert.equal(hero.sha256, sha256Of("PNGBYTES"));
});

test("the deduped asset keeps the name the site actually uses, not the alphabetical one", async () => {
  const ctx = ctxWith();
  const { inventory } = await clone.run({ assets: true, site: "Blog", depth: 0 }, ["https://x.test"], ctx);

  // `/img/hero.png` is referenced twice and `/img/hero-again.png` once. Sorted by path — which is
  // the inventory's own order — the incidental copy sorts first (`-` < `.`) and became the canonical
  // file, so the tree named a logo after the page that mentioned it least. Found by this test.
  assert.equal(ctx.written["/repo/shio/assets/Blog/img/hero-again.png"], undefined);
  assert.ok(ctx.written["/repo/shio/assets/Blog/img/hero.png"]);
  const again = inventory.assets.find((each) => each.path === "/img/hero-again.png");
  assert.equal(again.file, "Blog/img/hero.png");
  assert.equal(again.dedupedWith, "Blog/img/hero.png");
  assert.equal(inventory.download.wrote, 2);
  assert.equal(inventory.download.deduped, 1);
});

test("--assets never fetches off-origin, and says whose bytes it left", async () => {
  const ctx = ctxWith();
  const { inventory } = await clone.run({ assets: true, site: "Blog", depth: 0 }, ["https://x.test"], ctx);

  for (const url of ctx.requested) {
    assert.equal(new URL(url).host, "x.test", `left the origin for ${url}`);
  }
  assert.ok(inventory.notes.some((note) => /cdn\.other\.test/.test(note)));
});

test("a soft 404 does not become a file", async () => {
  const ctx = ctxWith();
  const { inventory } = await clone.run({ assets: true, site: "Blog", depth: 0 }, ["https://x.test"], ctx);

  assert.equal(ctx.written["/repo/shio/assets/Blog/img/broken.png"], undefined);
  const broken = inventory.assets.find((each) => each.path === "/img/broken.png");
  assert.match(broken.unavailable, /served text\/html, expected a image/);
});

test("--max-asset-bytes is checked against the header and against what arrived", async () => {
  const ctx = ctxWith();
  const { inventory } = await clone.run(
    { assets: true, site: "Blog", depth: 0, maxAssetBytes: 4 },
    ["https://x.test"],
    ctx,
  );

  // A server that under-declares or omits content-length would otherwise walk past a ceiling whose
  // whole job is to stop a crawl filling a disk.
  const hero = inventory.assets.find((each) => each.path === "/img/hero.png");
  assert.match(hero.unavailable, /past --max-asset-bytes/);
  assert.equal(ctx.written["/repo/shio/assets/Blog/img/hero.png"], undefined);
});

test("--assets refuses to start without --site, before a single request", async () => {
  const ctx = ctxWith();
  await assert.rejects(
    () => clone.run({ assets: true }, ["https://x.test"], ctx),
    (error) => {
      assert.match(error.message, /--assets needs --site <name>/);
      assert.match(error.message, /shio clone https:\/\/example\.com --assets --site Acme/);
      return true;
    },
  );
  // Nothing was crawled: a run that has nowhere to put the bytes should cost one second, not 400
  // requests to somebody else's server.
  assert.deepEqual(ctx.requested, []);
});

test("a site name a path cannot hold is refused up front, not by the 200th write", async () => {
  await assert.rejects(
    () => clone.run({ assets: true, site: "Blog/x" }, ["https://x.test"], ctxWith()),
    /shio content:/,
  );
  await assert.rejects(
    () => clone.run({ assets: true, site: ".." }, ["https://x.test"], ctxWith()),
    /shio content:/,
  );
});

test("without --assets the capture still writes exactly one file", async () => {
  const ctx = ctxWith();
  await clone.run({ depth: 0 }, ["https://x.test"], ctx);

  // SH377's promise, which SH378 is opt-in in order to keep.
  assert.deepEqual(Object.keys(ctx.written), ["/repo/shio/.shio/source-inventory.json"]);
});

// --- SH452: what a stylesheet references is not markup ---

test("SH452: a url() is resolved against the stylesheet, never against the page", () => {
  // The most common single defect in anything that touches CSS urls, and the reason `base` is
  // required rather than optional: `../fonts/x.woff2` in /assets/css/site.css is
  // /assets/fonts/x.woff2, and resolving it against the page names a file that does not exist.
  const rows = assetsReferencedBy(
    `@font-face{src:url("../fonts/inter.woff2")}
     .hero{background:url(/img/bg.jpg)}
     .icon{background:url('sprite.svg')}`,
    "https://x.test/assets/css/site.css",
    "https://x.test",
  );

  assert.deepEqual(rows.map((row) => row.path).sort(), [
    "/assets/css/sprite.svg",
    "/assets/fonts/inter.woff2",
    "/img/bg.jpg",
  ]);
  assert.equal(rows.find((row) => row.path.endsWith("inter.woff2")).kind, "font",
    "a url() carries no evidence of its kind, so the extension is all there is");
});

test("SH452: data:, off-origin and a repeated reference each land where they should", () => {
  const rows = assetsReferencedBy(
    `a{background:url(data:image/gif;base64,R0lGOD)}
     b{background:url(https://cdn.other.test/x.png)}
     c{background:url(/img/bg.jpg)}
     d{background:url("/img/bg.jpg")}`,
    "https://x.test/site.css",
    "https://x.test",
  );

  assert.equal(rows.length, 2, "data: is already portable and is not a reference to fetch");
  assert.equal(rows.find((row) => row.external).path, "https://cdn.other.test/x.png",
    "an off-origin row is a URL, a same-origin one is a path — the inventory's own currency, which "
      + "is what lets these go through planAssets unchanged");
  assert.equal(rows.find((row) => !row.external).refs, 2, "counted, so the dedupe ordering holds");
});

test("SH452: the extension classifier only has to decide download-or-not", () => {
  assert.equal(kindForUrl("/fonts/x.woff2"), "font");
  assert.equal(kindForUrl("/a/b.css?v=2"), "stylesheet");
  assert.equal(kindForUrl("/favicon.ico"), "icon");
  assert.equal(
    kindForUrl("/img/sprite"),
    "image",
    "unrecognised is `image`, which downloads: refusing what it cannot classify would drop the "
      + "sprite sheet nobody gave an extension, and a url() in CSS is overwhelmingly an image",
  );
});

test("SH452: --assets follows a stylesheet's url() calls, which is where the fonts are", async () => {
  // The defect, measured on a real run: `--assets` wrote exactly one file (a <link rel="icon">
  // logo) and the replica rendered in a serif fallback, because `clone` builds its asset list from
  // the markup and a stylesheet's url() is not markup. For a fidelity replica — whose whole claim
  // is that it renders identically — the stylesheet is where most of the remaining bytes live.
  const ctx = ctxWith({
    bodies: {
      "/css/site.css": {
        type: "text/css",
        body: "@font-face{src:url(../fonts/inter.woff2)}.hero{background:url(/img/bg.jpg)}",
      },
      "/fonts/inter.woff2": { type: "font/woff2", body: "WOFF2BYTES" },
      "/img/bg.jpg": { type: "image/jpeg", body: "JPEGBYTES" },
    },
  });

  const { inventory } = await clone.run({ assets: true, site: "Blog", depth: 0 }, ["https://x.test"], ctx);

  assert.ok(
    Object.keys(ctx.written).includes("/repo/shio/assets/Blog/fonts/inter.woff2"),
    `the webfont is in the tree. Written: ${Object.keys(ctx.written).sort().join(" ")}`,
  );
  assert.ok(Object.keys(ctx.written).includes("/repo/shio/assets/Blog/img/bg.jpg"));

  // And in the inventory, which is the map `theme.mjs`' rewrite reads: a font downloaded but not
  // recorded is a file nothing points at.
  const font = inventory.assets.find((asset) => asset.path === "/fonts/inter.woff2");
  assert.equal(font.file, "Blog/fonts/inter.woff2");
  assert.equal(font.kind, "font");
  assert.equal(font.sha256, sha256Of("WOFF2BYTES"));
});

test("SH452: a stylesheet that a stylesheet references is followed too", async () => {
  // The loop walks the plan by index and appends to it, so an @import chain is followed by the
  // same pass rather than needing a second one.
  const ctx = ctxWith({
    bodies: {
      "/css/site.css": { type: "text/css", body: "@import url(/css/fonts.css);" },
      "/css/fonts.css": { type: "text/css", body: "@font-face{src:url(/fonts/inter.woff2)}" },
      "/fonts/inter.woff2": { type: "font/woff2", body: "WOFF2BYTES" },
    },
  });

  await clone.run({ assets: true, site: "Blog", depth: 0 }, ["https://x.test"], ctx);

  assert.ok(Object.keys(ctx.written).includes("/repo/shio/assets/Blog/css/fonts.css"));
  assert.ok(
    Object.keys(ctx.written).includes("/repo/shio/assets/Blog/fonts/inter.woff2"),
    "two hops from the markup, which is where a font actually lives on a real site",
  );
});

test("SH455: an inline <style>'s url() is followed too, which is where a critical-CSS site keeps them", async () => {
  // SH452 closed the *external* stylesheet. A page with critical CSS has no stylesheet file, so its
  // webfonts stayed invisible to the downloader while `theme.mjs` — which reads the same <style>
  // blocks — named them in a note whose remedy was to re-run this command. Measured on
  // openviglet.github.io: `wrote 1 asset`, twice, and the replica in a serif fallback.
  const ctx = ctxWith({
    bodies: {
      "/": {
        body: "<html><head><style>@font-face{font-family:Inter;src:url(/fonts/inter.woff2)}"
          + "body{background:url(\"/img/bg.jpg\")}</style></head><body><p>Copy.</p></body></html>",
      },
      "/fonts/inter.woff2": { type: "font/woff2", body: "WOFF2BYTES" },
      "/img/bg.jpg": { type: "image/jpeg", body: "JPEGBYTES" },
    },
  });

  const { inventory } = await clone.run({ assets: true, site: "Blog", depth: 0 }, ["https://x.test"], ctx);

  assert.ok(
    Object.keys(ctx.written).includes("/repo/shio/assets/Blog/fonts/inter.woff2"),
    `the webfont is in the tree. Written: ${Object.keys(ctx.written).sort().join(" ")}`,
  );
  assert.ok(Object.keys(ctx.written).includes("/repo/shio/assets/Blog/img/bg.jpg"));
  assert.equal(inventory.assets.find((asset) => asset.path === "/fonts/inter.woff2").kind, "font");
});

test("SH452: an off-origin url() is left behind and its host is named, like any other", async () => {
  // SH378's rule reaches here unchanged: mirroring somebody else's bytes is a licensing decision
  // this tool does not make. What matters is that it lands in the *same* external report, so the
  // hosts a run left behind are one list rather than two.
  const ctx = ctxWith({
    bodies: {
      "/css/site.css": { type: "text/css", body: "@font-face{src:url(https://fonts.other.test/i.woff2)}" },
    },
  });

  const { inventory } = await clone.run({ assets: true, site: "Blog", depth: 0 }, ["https://x.test"], ctx);

  assert.ok(!ctx.requested.some((url) => url.includes("fonts.other.test")), "never fetched");
  assert.ok(
    inventory.notes.some((note) => note.includes("fonts.other.test")),
    `the host is named once, with the rest. Notes: ${inventory.notes.join(" | ")}`,
  );
});
