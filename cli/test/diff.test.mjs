/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH522 — `shio diff`, the join nobody had walked.
 *
 * Shio holds both sides of every comparison it does not make: the capture declares the source, the
 * proposal declares the intent, the document declares the write, and the artefact each produces is
 * compared with none of them. So "is this what it was supposed to be?" was answered by hand every
 * round, and that hand was wrong twice — *zero broken references* on a page with a missing logo, and
 * *106 editable fields* on a page that had lost most of its content.
 *
 * Network-free: the replica's bytes arrive through the injected `fetch`, which is what lets a page
 * that lost its hero be a fixture rather than a story.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { buildInventory, countLinks, INVENTORY_FILE, scanPage } from "../src/clone.mjs";
import { compareCapture, describeDiff } from "../src/diff.mjs";
import * as diff from "../src/commands/diff.mjs";
import { norm } from "./support/paths.mjs";

/** The source, as it was captured: a hero, a nav, a grid and a footer. */
const SOURCE = `<html lang="en"><head><title>Home</title></head><body>
  <nav><a href="/about">About</a><a href="/pricing">Pricing</a></nav>
  <section class="hero"><h1>Acme</h1><img src="/img/hero.png" alt="Hero">
    <p>${"Body copy that reads like a page a person wrote. ".repeat(6)}</p></section>
  <footer><a href="/privacy">Privacy</a></footer>
  </body></html>`;

/** The same page after a conversion that kept one section and lost the rest (the SH520 shape). */
const LOST = `<html lang="en"><head><title>Home</title></head><body>
  <section class="shio-section"><h2>Acme</h2></section>
  </body></html>`;

function inventoryOf(html) {
  const doc = scanPage(html, "https://x.test/");
  return {
    version: 3,
    source: { origin: "https://x.test", seed: "https://x.test" },
    crawl: { pages: 1, truncated: false, markup: true },
    pages: [{
      path: "/",
      title: doc.title,
      // SH554 — `countLinks`, not a `startsWith("/")` of this fixture's own. A hand-rolled split
      // here is the very defect under test, one layer up: it would call an absolute same-origin
      // link external and agree with nothing.
      links: countLinks(doc.links, "https://x.test/"),
      assets: doc.assets.length,
      forms: doc.forms.length,
      textChars: doc.textChars,
    }],
  };
}

/** The produced side as `shio diff` builds it: scanned, then split by the rule the capture used. */
function scannedAt(html, url) {
  const doc = scanPage(html, url);
  return { ...doc, links: countLinks(doc.links, url) };
}

function ctxWith({ served = {}, inventory = inventoryOf(SOURCE) } = {}) {
  const out = { text: "" };
  const files = new Map([[norm(`/repo/${INVENTORY_FILE}`), JSON.stringify(inventory)]]);
  return {
    env: { SHIO_URL: "http://shio.test", SHIO_USER: "admin", SHIO_PASSWORD: "secret" },
    cwd: norm("/repo"),
    stdout: { write: (chunk) => { out.text += chunk; } },
    stderr: { write: () => {} },
    out,
    fs: {
      async readFile(at) {
        if (files.has(norm(at))) return files.get(norm(at));
        const error = new Error("ENOENT");
        error.code = "ENOENT";
        throw error;
      },
    },
    async fetch(url) {
      const path = new URL(url).pathname.replace(/^\/preview\/[^/]+/, "") || "/";
      const html = served[path];
      return html === undefined
        ? { ok: false, status: 404, async text() { return ""; } }
        : { ok: true, status: 200, async text() { return html; } };
    },
  };
}

test("SH522: a page that lost its content is named fact by fact", async () => {
  const ctx = ctxWith({ served: { "/": LOST } });

  const result = await diff.run({ site: "Acme" }, [], ctx);

  assert.equal(result.exitCode, 1, "a lost fact fails the gate — that is the whole point of a verb "
    + "whose answer was being assembled by hand");
  const [page] = result.pages;
  assert.deepEqual(
    page.rows.filter((row) => row.delta < 0).map((row) => row.fact).sort(),
    ["assets", "internal links"],
    "the hero image and every link the nav and footer carried",
  );
  assert.equal(page.text.lost, true, "and the prose, which is the fact SH520 removed and every "
    + "instrument called a success");
  assert.match(ctx.out.text, /internal links: 3 → 0/);
  assert.match(ctx.out.text, /assets: 1 → 0/);
});

test("SH522: a replica that matches its capture says so in one line and exits zero", async () => {
  const ctx = ctxWith({ served: { "/": SOURCE } });

  const result = await diff.run({ site: "Acme" }, [], ctx);

  assert.equal(result.exitCode, 0);
  assert.equal(result.lost, 0);
  assert.match(ctx.out.text, /nothing lost/);
  assert.match(ctx.out.text, /\/ {2}as declared/,
    "a clean page prints one line: a report whose matching rows are as long as its broken ones is "
      + "one nobody reads to the end");
});

test("SH522: a fact the replica gained is not a loss", () => {
  // A Shio layout adds links — a skip link, a nav the theme composes — so a gate that called any
  // difference damage would fire on every working conversion. Counted apart and never failing.
  const declared = [{ path: "/", title: "Home", links: { internal: 2, external: 0 }, assets: 1, forms: 0, textChars: 400 }];
  const produced = new Map([["/", scannedAt(
    '<html><head><title>Home</title></head><body><a href="/a">a</a><a href="/b">b</a>'
      + '<a href="/c">c</a><img src="/img/hero.png" alt=""><p>'
      + "Body copy that reads like a page a person wrote. ".repeat(6) + "</p></body></html>",
    "https://x.test/")]]);

  const comparison = compareCapture(declared, produced);

  assert.equal(comparison.lost, 0);
  assert.equal(comparison.pages[0].gained, 1);
  assert.equal(comparison.pages[0].rows.find((row) => row.fact === "internal links").delta, 1);
});

test("SH522: a path the replica does not serve is one absence, not four lost facts", () => {
  const declared = [{ path: "/gone", title: "Gone", links: { internal: 3, external: 1 }, assets: 2, forms: 1, textChars: 900 }];

  const comparison = compareCapture(declared, new Map());

  assert.deepEqual(comparison.missing, ["/gone"]);
  assert.equal(comparison.lost, 0, "`source-page-missing` is already the finding for this, and "
    + "counting it again as four would be one defect reported twice");
  assert.match(describeDiff(comparison), /the replica serves nothing here/);
});

test("SH522: --against names the three declarations that are not built yet", async () => {
  await assert.rejects(
    () => diff.run({ site: "Acme", against: "proposal" }, [], ctxWith()),
    /not built[\s\S]*allowed: capture[\s\S]*same address\n *at an earlier moment/,
    "a flag that accepted the value and answered approximately would be worse than one that says "
      + "what it does not do",
  );
});

/** Every href shape that has to survive the join: relative, root-relative, absolute-same-host, off-host. */
const MIXED = `<html lang="en"><head><title>Mixed</title></head><body>
  <nav><a href="/about">About</a><a href="https://x.test/pricing">Pricing</a><a href="contact">Contact</a></nav>
  <p><a href="https://linkedin.test/company/acme">LinkedIn</a><a href="https://github.test/acme">GitHub</a></p>
  <img src="/img/hero.png" alt="">
  <p>${"Body copy that reads like a page a person wrote. ".repeat(6)}</p>
  </body></html>`;

test("SH554: one page scanned both ways compares equal, fact for fact", () => {
  // The invariant `diff`'s own header states and did not hold. The capture's counts come out of
  // `buildInventory`; the replica's out of the produced path. Same bytes, same URL, so every delta
  // must be zero — and before SH554 they were `internal 3 → 5` (the total, via a `?? links.length`
  // fallback) and `external 2 → 0` (a field the raw scan shape does not have).
  const at = "https://x.test/";
  const inventory = buildInventory({
    origin: "https://x.test",
    seed: at,
    visited: [{ url: at, doc: scanPage(MIXED, at), status: 200, depth: 0 }],
    robots: { present: true },
  });
  const declared = inventory.pages;
  assert.deepEqual(declared[0].links, { internal: 3, external: 2 },
    "root-relative, absolute-same-host and bare-relative are all internal; two off-host are not");

  const comparison = compareCapture(declared, new Map([["/", scannedAt(MIXED, at)]]));

  assert.deepEqual(
    comparison.pages[0].rows.filter((row) => row.delta !== 0),
    [],
    "a page compared with itself has lost and gained nothing",
  );
  assert.equal(comparison.lost, 0);
});

/**
 * The same page as a replica serves it: own-origin links rebased to root-relative by the rewriter,
 * off-host ones left absolute.
 *
 * <p>The rebase is why `https://x.test/pricing` is spelled `/pricing` here, and it is load-bearing:
 * a replica that kept the source's absolute spelling really would be serving a link that leaves the
 * site, and `diff` counting that as one internal link lost is the correct reading, not a false
 * alarm. What SH554 fixes is the reading of the links that did *not* move.</p>
 */
const REPLICA = MIXED.replace('href="https://x.test/pricing"', 'href="/pricing"');

test("SH554: a replica that keeps its off-host links is not reported as having lost them", async () => {
  // The measured symptom on a fidelity replica of a real site: nine absolute external links served,
  // nine in the source, and `diff` printing `external links: 9 → 0` on that page and every other —
  // with the command in SH548's CI gate, so the suite was red at HEAD on its own fixture.
  const ctx = ctxWith({ inventory: inventoryOf(MIXED), served: { "/": REPLICA } });

  const result = await diff.run({ site: "Acme" }, [], ctx);

  assert.equal(result.exitCode, 0, "nothing was lost, so the gate must not fire");
  assert.equal(result.lost, 0);
  assert.doesNotMatch(ctx.out.text, /external links: \d+ → 0/,
    "an instrument that cries wolf in the gate is worse than one outside it");
});

test("SH554: an unclassified side is refused rather than scored", () => {
  // The boundary the defect crossed. The wrong shape did not throw — it produced four believable
  // numbers, which is why nothing caught it for a whole release.
  const declared = [{ path: "/", title: "Home", links: { internal: 3, external: 2 }, assets: 1, forms: 0, textChars: 400 }];
  const raw = scanPage(MIXED, "https://x.test/");

  assert.throws(
    () => compareCapture(declared, new Map([["/", raw]])),
    /produced side carries links as a raw array[\s\S]*SH554[\s\S]*countLinks/,
  );
});

test("SH557: a path no friendly URL can hold is not reported as a page the replica lost", async () => {
  // The measured run: *107 page(s) compared, 91 the replica does not serve* — which reads as a
  // catastrophic replication failure and is not one. `/en/case-study?p=2`…`?p=24` in five locales
  // were never addressable: `post:<site>/<friendly-url>` has no room for a `?`. Reporting a source
  // trait the target cannot represent in the vocabulary of damage is the defect here, not the
  // number.
  const inventory = inventoryOf(SOURCE);
  inventory.pages.push(
    { path: "/list?page=2", title: "Page 2", links: { internal: 3, external: 0 }, assets: 1, forms: 0, textChars: 400 },
    { path: "/list?page=3", title: "Page 3", links: { internal: 3, external: 0 }, assets: 1, forms: 0, textChars: 400 },
    { path: "/gone", title: "Gone", links: { internal: 3, external: 0 }, assets: 1, forms: 0, textChars: 400 },
  );
  const ctx = ctxWith({ inventory, served: { "/": SOURCE } });

  const result = await diff.run({ site: "Acme" }, [], ctx);

  assert.deepEqual(result.unaddressable, ["/list?page=2", "/list?page=3"]);
  assert.deepEqual(result.missing, ["/gone"],
    "`/gone` is the real absence and stays one — separating the two is the whole task, not "
      + "reclassifying every absence as somebody else's problem");
  assert.equal(result.exitCode, 0,
    "and the gate does not fire on a trait of the source: nothing was lost, so nothing failed");
  assert.match(ctx.out.text, /2 no friendly URL can address/);
  assert.match(ctx.out.text, /never addressable, not lost/);
});

test("SH557: an unaddressable path is not even requested", async () => {
  // 91 round trips to be told 404 ninety-one times, on a run whose limit was decidable from the
  // inventory before it started.
  const asked = [];
  const inventory = inventoryOf(SOURCE);
  inventory.pages.push({ path: "/list?page=2", title: "Page 2", links: { internal: 0, external: 0 }, assets: 0, forms: 0, textChars: 400 });
  const ctx = ctxWith({ inventory, served: { "/": SOURCE } });
  const fetched = ctx.fetch;
  ctx.fetch = (url, init) => { asked.push(url); return fetched(url, init); };

  await diff.run({ site: "Acme" }, [], ctx);

  assert.equal(asked.length, 1, `asked for ${asked.join(" ")}`);
  assert.ok(!asked.some((url) => url.includes("page=2")));
});

test("SH557: the capture names the limit at the phase that can still stop the run", () => {
  // The half that matters most for cost. `clone` reported 198 pages and six notes, none of them
  // this; `propose` then counted candidates over pages that would never exist, and `convert` wrote
  // and published 204 objects. Everything between the capture and the diff was wasted and looked
  // fine, and the inventory held the paths the whole time.
  const origin = "https://x.test";
  const page = (path) => ({
    url: `${origin}${path}`,
    doc: scanPage(`<html><head><title>T</title></head><body><p>Words here.</p></body></html>`, `${origin}${path}`),
    status: 200,
    depth: 1,
  });

  const inventory = buildInventory({
    origin,
    seed: `${origin}/`,
    visited: [page("/"), page("/list?page=2"), page("/list?page=3")],
    robots: { present: true },
  });

  const said = (inventory.notes ?? []).find((note) => /query string/i.test(note));
  assert.ok(said, `no note names the limit: ${JSON.stringify(inventory.notes)}`);
  assert.match(said, /2 of 3/, "the proportion is the decision — 91 of 107 is a source that cannot "
    + "be replicated, and 2 of 107 is a footnote");
  assert.match(said, /"\?"/);
});

test("SH557: a capture no query string touches gains no note", () => {
  const origin = "https://x.test";
  const inventory = buildInventory({
    origin,
    seed: `${origin}/`,
    visited: [{
      url: `${origin}/`,
      doc: scanPage("<html><head><title>T</title></head><body><p>Words.</p></body></html>", `${origin}/`),
      status: 200,
      depth: 0,
    }],
    robots: { present: true },
  });

  assert.ok(!(inventory.notes ?? []).some((note) => /query string/i.test(note)),
    "a note that fires on every capture is one a reader learns to skip, and this one has to be "
      + "read: it is the only one that says the run should not continue");
});

test("SH522: no capture is a refusal naming the command that writes one", async () => {
  const ctx = ctxWith();
  ctx.fs.readFile = async () => {
    const error = new Error("ENOENT");
    error.code = "ENOENT";
    throw error;
  };

  await assert.rejects(() => diff.run({ site: "Acme" }, [], ctx),
    /no capture at[\s\S]*shio clone/);
});
