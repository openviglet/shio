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

import { buildInventory, countAssets, countLinks, INVENTORY_FILE, scanPage } from "../src/clone.mjs";
import {
  compareCapture, comparePage, comparePublished, describeDiff, describePublishedDiff,
} from "../src/diff.mjs";
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
      // SH578 — `countAssets`, for the same reason `countLinks` is here rather than a hand-rolled
      // split: a fixture that builds the row its own way is a fixture that cannot see the defect.
      assets: countAssets(doc.assets),
      forms: doc.forms.length,
      textChars: doc.textChars,
      // SH867 — from `scanPage` for the reason the two above are: a fixture that builds the row its
      // own way cannot see the defect the row exists to catch.
      elements: doc.elements,
    }],
  };
}

/** SH867's measured viewport: a badge, a hero, two calls to action and a four-cell stats bar. */
const VIEWPORT = `<html lang="en"><head><title>Home</title></head><body>
  <span class="eyebrow">Open Source Platform</span>
  <h1>Enterprise Intelligence</h1>
  <p>${"Copy that reads like a page a person wrote. ".repeat(4)}</p>
  <a class="btn" href="/products">Explore Products</a><a class="btn" href="/docs">Read the Docs</a>
  <ul class="stats"><li>3</li><li>100%</li><li>Apache 2.0</li><li>Self-hosted</li></ul>
  </body></html>`;

/** What the replica served: one call to action, no stats bar, no badge. */
const VIEWPORT_LOST = `<html lang="en"><head><title>Home</title></head><body>
  <h1>Enterprise Intelligence</h1>
  <p>${"Copy that reads like a page a person wrote. ".repeat(4)}</p>
  <a class="btn" href="/products">Explore Products</a>
  </body></html>`;

test("SH867: a stats bar that went is named as list items, not absorbed into a link count", () => {
  // The measurement: `verify` 0 errors, `diff` `external links: 15 → 9`, `judge` eleven findings all
  // of one kind — for a viewport that lost a badge, a secondary CTA and a four-cell statistics band.
  // A stats bar is list items and a call to action is an anchor, so counted as links both are six of
  // one number, and a reviewer told six links are missing looks for six links.
  const declared = inventoryOf(VIEWPORT).pages[0];
  const produced = { ...inventoryOf(VIEWPORT_LOST).pages[0] };
  const compared = comparePage(declared, produced, "fidelity");
  const moved = compared.rows.filter((row) => row.delta !== 0).map((row) => row.fact);

  assert.ok(moved.includes("list items"),
    `the stats bar has to be nameable, or the reviewer is told about links: ${moved.join(", ")}`);
  assert.deepEqual(
    compared.rows.find((row) => row.fact === "list items"),
    { fact: "list items", declared: 4, produced: 0, delta: -4, claimed: true },
    "and a fidelity page claims it: the markup is what that mode keeps");
});

test("SH867: the census names what markup can decide, and does not guess at the rest", () => {
  const source = scanPage(VIEWPORT, "https://x.test/");

  assert.deepEqual(source.elements, { heading: 1, listItem: 4 },
    "a heading is a heading in any stylesheet and an `<li>` is one of a set. An eyebrow badge is a "
      + "`<span>` some CSS makes a pill and half a type scale is a computed font size — both need "
      + "the rendered pair, and a census that guessed at them would report a `<span>` count that "
      + "moves with every wrapper anybody adds");
  assert.equal(source.elements.button, undefined,
    "and `<a class=btn>` is an anchor: this deliberately does not read a class name as a role, "
      + "which is the unverifiable label SH689 spent a task removing");
});

test("SH867: a capture with no census is not scored on those facts, and never as zero", () => {
  // A version-5 row. Reading a missing census as `0` would say the source had no headings at all,
  // so a replica that kept every one of them would report a gain — SH578 and SH581's failure shape.
  const declared = { ...inventoryOf(VIEWPORT).pages[0] };
  const produced = { ...inventoryOf(VIEWPORT_LOST).pages[0] };
  delete declared.elements;
  delete produced.elements;
  const compared = comparePage(declared, produced, "fidelity");

  assert.deepEqual(compared.rows.filter((row) => row.fact === "list items"), [],
    "omitted rather than zeroed — and `migrateInventory` brings such a row forward from the markup "
      + "it already stored, which is why this is not a refusal (SH592)");
  assert.ok(compared.rows.some((row) => row.fact === "internal links"),
    "while every fact the row CAN answer is still scored");
});

/** The produced side as `shio diff` builds it: scanned, then split by the rule the capture used. */
function scannedAt(html, url) {
  const doc = scanPage(html, url);
  return { ...doc, links: countLinks(doc.links, url), assets: countAssets(doc.assets) };
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
    ["body assets", "images", "internal links"],
    "the hero image and every link the nav and footer carried — and SH867's census names the image "
      + "as an image rather than only as one of the page's references, which is the whole point of "
      + "that fact: `body assets` is a count of URLs and `images` is a count of pictures",
  );
  assert.equal(page.text.lost, true, "and the prose, which is the fact SH520 removed and every "
    + "instrument called a success");
  assert.match(ctx.out.text, /internal links: 3 → 0/);
  assert.match(ctx.out.text, /body assets: 1 → 0/);
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
  const declared = [{ path: "/", title: "Home", links: { internal: 2, external: 0 }, assets: { own: 1, head: 0, style: 0 }, forms: 0, textChars: 400 }];
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
  const declared = [{ path: "/gone", title: "Gone", links: { internal: 3, external: 1 }, assets: { own: 2, head: 0, style: 0 }, forms: 1, textChars: 900 }];

  const comparison = compareCapture(declared, new Map());

  assert.deepEqual(comparison.missing, ["/gone"]);
  assert.equal(comparison.lost, 0, "`source-page-missing` is already the finding for this, and "
    + "counting it again as four would be one defect reported twice");
  assert.match(describeDiff(comparison), /the replica serves nothing here/);
});

test("SH522: the refusal sends a reader somewhere, and never to a verb that now exists", async () => {
  await assert.rejects(
    () => diff.run({ site: "Acme", against: "document" }, [], ctxWith()),
    (error) => {
      assert.match(error.message, /allowed: capture, proposal, published/);
      assert.match(error.message, /shio apply --check/,
        "the document comparison is not unbuilt — it is `apply --check` under another name, and a "
          + "refusal that called it 'not implemented' would send somebody to build a second reader "
          + "for a question already answered");
      assert.ok(!/DRAFT-preferred/.test(error.message),
        "SH640 built the published comparison, so the refusal must stop explaining why it cannot "
          + "exist — this test is named for exactly that, and the sentence it now removes was "
          + "true for one commit");
      // The list shrinks as the chain is walked. This is the half a refusal usually gets wrong:
      // naming something as missing after it ships sends a reader away from a verb they have.
      assert.ok(!/The proposal \(what was intended\)/.test(error.message),
        "the refusal must stop offering an excuse for something that now exists");
      return true;
    },
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
  const declared = [{ path: "/", title: "Home", links: { internal: 3, external: 2 }, assets: { own: 1, head: 0, style: 0 }, forms: 0, textChars: 400 }];
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

/*
 * SH577 — one standard, three modes.
 *
 * The gate asserted `lost === 0` on a chain that runs `propose --accept all`, so it held an
 * authorable page to the fidelity claim and was red at HEAD for two rounds over chrome the section
 * vocabulary exists to drop. The standard is unchanged — an artefact is judged against what its
 * mode claims — and the mode is read off what the chain already wrote down.
 */

/** The two pages of the fixture shape: a nav, a hero and a footer, minus what a conversion keeps. */
const AUTHORABLE = `<html lang="en"><head><title>Home</title></head><body>
  <section class="shio-section"><h1>Acme</h1>
    <p>${"Body copy that reads like a page a person wrote. ".repeat(6)}</p></section>
  </body></html>`;

/** A ctx that also carries the two artefacts the chain writes: the plan and the proposal. */
function ctxWithPlan({ served, accepted = [], composition = [], proposal = true }) {
  const ctx = ctxWith({ served });
  const inner = ctx.fs.readFile;
  const files = new Map([
    [norm("/repo/shio/conversion-plan.json"), JSON.stringify({ accepted })],
    ...(proposal
      ? [[norm("/repo/shio/.shio/conversion-proposal.json"), JSON.stringify({ pages: composition })]]
      : []),
  ]);
  ctx.fs.readFile = async (at, encoding) => files.get(norm(at)) ?? inner(at, encoding);
  return ctx;
}

test("SH577: an authorable page's chrome is not a loss, and the count is still on the report",
  async () => {
    const ctx = ctxWithPlan({
      served: { "/": AUTHORABLE },
      accepted: [{ block: "b1", type: "SectionHero" }],
      composition: [{ path: "/", blocks: ["b1"] }],
    });

    const result = await diff.run({ site: "Acme" }, [], ctx);

    assert.equal(result.exitCode, 0, "this is the red the gate carried for two rounds: the source's "
      + "nav and footer links and its hero image, on a page composed from the vocabulary that "
      + "exists to drop them");
    assert.equal(result.lost, 0);
    assert.equal(result.byDesign, 3,
      "internal links, assets and — since SH867 — the image, as a picture rather than as a URL. "
        + "Counted, not discarded: an authorable page composes its own structure, so none of the "
        + "three is a promise it makes, and all three stay on the report");
    assert.equal(result.pages[0].mode, "authorable");
    // The number stays visible, because it is the number SH575's missing navigation was read off. A
    // fact removed from the report is a fact nobody can find.
    assert.match(ctx.out.text, /internal links: 3 → 0.*by design/);
    assert.match(ctx.out.text, /the mode does not claim/);
  });

test("SH577: and its prose is still a loss, because the content is what every mode claims",
  async () => {
    const ctx = ctxWithPlan({
      served: { "/": '<html><head><title>Home</title></head><body><h1>Acme</h1></body></html>' },
      accepted: [{ block: "b1", type: "SectionHero" }],
      composition: [{ path: "/", blocks: ["b1"] }],
    });

    const result = await diff.run({ site: "Acme" }, [], ctx);

    assert.equal(result.exitCode, 1, "an authorable page that dropped its body copy is damage in "
      + "every mode — narrowing the claim to the content is not narrowing it to nothing");
    assert.equal(result.pages[0].text.lost, true);
  });

test("SH577: the mode is per page, because a page is not one mode (SH520)", async () => {
  const ctx = ctxWithPlan({
    served: { "/": AUTHORABLE, "/about": AUTHORABLE },
    accepted: [{ block: "b1", type: "SectionHero" }],
    // `/about` holds a block nobody accepted, so `convert` keeps its body verbatim — which means
    // the capture's counts ARE a claim about it, and a whole-run mode would have excused them.
    composition: [{ path: "/", blocks: ["b1"] }, { path: "/about", blocks: ["b9"] }],
    // A second declared page, so the inventory has one to compare.
  });
  ctx.fs.readFile = ((inner) => async (at, encoding) => {
    if (norm(at).endsWith(norm(INVENTORY_FILE))) {
      const one = JSON.parse(await inner(at, encoding));
      return JSON.stringify({ ...one, pages: [one.pages[0], { ...one.pages[0], path: "/about" }] });
    }
    return inner(at, encoding);
  })(ctx.fs.readFile);

  const result = await diff.run({ site: "Acme" }, [], ctx);

  assert.deepEqual(result.modes, { fidelity: 1, mixed: 0, authorable: 1 });
  assert.equal(result.lost, 3, "the fidelity page is still judged on every fact — its links and "
    + "its image really are gone, and nothing in the plan says they were meant to be. Three since "
    + "SH867, which counts the picture as a picture as well as as a reference");
  assert.equal(result.exitCode, 1);
});

/**
 * SH692 — `mixed` was a label with no standard, and it is where ten of twelve pages land.
 *
 * <p>`MODES.mixed` copied `authorable`'s rule verbatim — `claimsVerbatimFacts: false` — so a page
 * that kept most of the source's markup was judged by the standard of one that kept none, and its
 * whole answer was **20 shortfalls the mode does not claim**: a number that is neither damage nor
 * silence.</p>
 *
 * <p>The corollary the design section names is that the judgeable unit is the region. A region whose
 * markup the conversion kept is judged against the source; one an editor owns is not. So a page
 * still serving verbatim landmarks still claims the source's chrome, because that is where the nav,
 * the external links and the forms live.</p>
 */

/** A mixed page as `convert` writes one: chrome kept, one block authored. */
const MIXED_KEPT_CHROME = `<html lang="en"><head><title>Home</title></head><body>
  <header><nav><a href="/about">About</a></nav></header>
  <section class="shio-section" sh-post="b1"><h1>Acme</h1>
    <p>${"Body copy that reads like a page a person wrote. ".repeat(6)}</p></section>
  <footer><a href="https://elsewhere.example">Out</a></footer>
  </body></html>`;

/** The same acceptance, but the conversion kept nothing — every landmark is authored. */
const MIXED_NO_CHROME = `<html lang="en"><head><title>Home</title></head><body>
  <section class="shio-section" sh-post="b1"><h1>Acme</h1>
    <p>${"Body copy that reads like a page a person wrote. ".repeat(6)}</p></section>
  </body></html>`;

test("SH692: a mixed page that kept chrome still claims the source's verbatim facts", async () => {
  const ctx = ctxWithPlan({
    served: { "/": MIXED_KEPT_CHROME },
    accepted: [{ block: "b1", type: "SectionHero" }],
    // Two blocks declared, one accepted — which is what `resolveModes` calls `mixed`.
    composition: [{ path: "/", blocks: ["b1", "b2"] }],
  });

  const result = await diff.run({ site: "Acme" }, [], ctx);

  assert.equal(result.modes.mixed, 1, "the fixture is the case under test");
  assert.equal(
    result.byDesign,
    0,
    `every shortfall on this page is now damage or nothing — the chrome is still being served, so `
      + `the source's nav and links are still a claim about it. \`byDesign\` was where the whole `
      + `answer used to go (SH692). rows: ${JSON.stringify(result.pages[0].rows)}`,
  );
});

test("SH692: a mixed page that kept no chrome claims none of them, which is what it always meant",
  async () => {
    // The other half, and the one that keeps this from being "mixed is now fidelity". The label is
    // the same; what differs is what the replica actually serves, which is the point of scoring the
    // page rather than the word.
    const ctx = ctxWithPlan({
      served: { "/": MIXED_NO_CHROME },
      accepted: [{ block: "b1", type: "SectionHero" }],
      composition: [{ path: "/", blocks: ["b1", "b2"] }],
    });

    const result = await diff.run({ site: "Acme" }, [], ctx);

    assert.equal(result.modes.mixed, 1, "same label");
    assert.ok(
      result.byDesign > 0,
      "and a different answer: with no verbatim region left, the source's chrome is gone by design "
        + "exactly as it is on an authorable page",
    );
  });

test("SH692: fidelity and authorable are decided by the label, not re-derived from the markup",
  async () => {
    // Deliberately narrow. For those two the label IS the answer — fidelity claims every fact by
    // definition and authorable claims none by design — and re-deriving it from the served page
    // could only introduce a disagreement with the conversion's own record. `mixed` is the one that
    // had no standard, and `mixed` is the only one the regions decide.
    const ctx = ctxWithPlan({
      // An authorable page whose layout happens to wrap its sections in a plain <main>: a verbatim
      // landmark on a page that accepted everything. Under a rule that read the markup for all three
      // modes, this would start claiming the source's chrome and report it as lost.
      served: {
        "/": `<html lang="en"><head><title>Home</title></head><body><main>
          <section class="shio-section" sh-post="b1"><h1>Acme</h1>
            <p>${"Body copy that reads like a page a person wrote. ".repeat(6)}</p></section>
          </main></body></html>`,
      },
      accepted: [{ block: "b1", type: "SectionHero" }],
      composition: [{ path: "/", blocks: ["b1"] }],
    });

    const result = await diff.run({ site: "Acme" }, [], ctx);

    assert.equal(result.modes.authorable, 1);
    assert.ok(
      result.byDesign > 0,
      "the <main> is a verbatim landmark and changes nothing: this page accepted every block, so "
        + "the source's chrome is gone by design and the label says so",
    );
  });

test("SH577: --mode wins over the plan, for a replica whose plan is not in this directory",
  async () => {
    const ctx = ctxWithPlan({
      served: { "/": AUTHORABLE },
      accepted: [{ block: "b1", type: "SectionHero" }],
      composition: [{ path: "/", blocks: ["b1"] }],
    });

    const result = await diff.run({ site: "Acme", mode: "fidelity" }, [], ctx);

    assert.equal(result.exitCode, 1, "the escape hatch is the strict direction, so it can never "
      + "hide a loss");
    assert.equal(result.byDesign, 0);
  });

test("SH577: a mode this cannot judge is refused by name, headless included", async () => {
  await assert.rejects(
    () => diff.run({ site: "Acme", mode: "headless" }, [], ctxWith()),
    /not a conversion mode[\s\S]*allowed: fidelity, mixed, authorable[\s\S]*another origin/,
    "the Next.js front end is a real scenario and a real thing this command cannot reach, so it is "
      + "named rather than left out of the allowed list with no explanation (P6)",
  );
  await assert.rejects(
    () => diff.run({ site: "Acme", mode: "strict" }, [], ctxWith()),
    /allowed: fidelity, mixed, authorable/,
  );
});

test("SH577: no plan on disk is fidelity, which is what this command was before it could tell",
  async () => {
    const ctx = ctxWith({ served: { "/": AUTHORABLE } });

    const result = await diff.run({ site: "Acme" }, [], ctx);

    assert.equal(result.exitCode, 1, "an empty plan means nothing was accepted, so every captured "
      + "fact is a claim — read from the plan rather than assumed, and identical to the old rule");
    assert.equal(result.pages[0].mode, "fidelity");
    assert.match(ctx.out.text, /internal links: 3 → 0 {2}\(-3\)$/m,
      "and no by-design note, because there is nothing to explain away");
  });

/*
 * SH578 — the one fact where a loss can hide inside a gain.
 *
 * SH554 made both sides come out of one function; this fact still asked a different question at each
 * end. The declared side is a source page's own references; the produced side is a replica page plus
 * the theme inlined into it, whose every url() this scanner correctly finds (SH455) and then counted
 * as if the page had referenced it. Measured on a fidelity replica of vilt-group.com: +30 on every
 * page, 32 own references against 60 url() in its <style>, from a captured theme holding 59.
 */

/** A source page with three of its own images and no inline stylesheet. */
const OWN_ASSETS = `<html lang="en"><head><title>Home</title></head><body>
  <img src="/img/a.png" alt="a"><img src="/img/b.png" alt="b"><img src="/img/c.png" alt="c">
  <p>${"Body copy that reads like a page a person wrote. ".repeat(6)}</p>
  </body></html>`;

/** The replica: it lost all three, and picked up a theme with four `url()` inlined into it. */
const THEME_INLINED = `<html lang="en"><head><title>Home</title>
  <style>.a{background:url("/t/1.png")}.b{background:url("/t/2.png")}
  .c{background:url("/t/3.png")}@font-face{src:url("/t/4.woff2")}</style></head><body>
  <p>${"Body copy that reads like a page a person wrote. ".repeat(6)}</p>
  </body></html>`;

test("SH578: a page whose own references shrink while its theme's grow is a LOSS", async () => {
  const ctx = ctxWith({ served: { "/": THEME_INLINED }, inventory: inventoryOf(OWN_ASSETS) });

  const result = await diff.run({ site: "Acme" }, [], ctx);

  // Before the split this page reported `assets: 3 → 4`, a gain — and a gain is never a failure,
  // which is right and is exactly what made this the fact where a real loss disappears.
  assert.equal(result.exitCode, 1, "three images gone. The old reading netted them off against four "
    + "theme url() and called the page fine");
  const assets = result.pages[0].rows.find((row) => row.fact === "body assets");
  assert.deepEqual(
    { declared: assets.declared, produced: assets.produced, delta: assets.delta },
    { declared: 3, produced: 0, delta: -3 },
    "the page's OWN references, at both ends — the question the declared side was always asking",
  );
  assert.match(ctx.out.text, /body assets: 3 → 0 {2}\(-3\)/);
});

test("SH578: and the theme is one artefact, reported once at the site level and never scored",
  async () => {
    const ctx = ctxWith({ served: { "/": THEME_INLINED }, inventory: inventoryOf(OWN_ASSETS) });

    const result = await diff.run({ site: "Acme" }, [], ctx);

    assert.deepEqual(
      result.theme,
      { declared: 0, produced: 4, uniform: true, pages: 1 },
      "counted, named and outside the arithmetic: on a thirteen-page site the same number thirteen "
        + "times said nothing thirteen times, and each copy could hide a page's own loss",
    );
    assert.equal(result.pages[0].gained, 0, "the theme is not a gain either — it is not this page's");
    assert.match(ctx.out.text, /theme: 0 → 4 url\(\) inside a stylesheet, across 1 page\(s\)/);
  });

test("SH578: pages that disagree about the theme are said so, because one theme cannot explain it",
  async () => {
    const other = THEME_INLINED.replace('.c{background:url("/t/3.png")}', "");
    const inventory = inventoryOf(OWN_ASSETS);
    inventory.pages.push({ ...inventory.pages[0], path: "/about" });
    const ctx = ctxWith({ served: { "/": THEME_INLINED, "/about": other }, inventory });

    const result = await diff.run({ site: "Acme" }, [], ctx);

    assert.equal(result.theme.uniform, false);
    assert.equal(result.theme.produced, 4, "the max: a sum would multiply one artefact by the page "
      + "count and an average would invent a number no page has");
    assert.match(ctx.out.text, /the pages disagree about this number/);
  });

test("SH578: a capture written before the split is refused, not scored", () => {
  // The failure mode this rule exists for is not a crash. A version-3 row read as `own` compares a
  // page's markup against a replica's markup-plus-theme, which is the +30 that started this.
  const declared = [{ path: "/", title: "Home", links: { internal: 0, external: 0 }, assets: 32, forms: 0, textChars: 400 }];

  assert.throws(
    () => compareCapture(declared, new Map([["/", scannedAt(THEME_INLINED, "https://x.test/")]])),
    /declared side carries assets as a number[\s\S]*SH578[\s\S]*countAssets[\s\S]*re-run `shio clone`/,
  );
});

// --- SH581: the head one side counts and the other does not have ---

/**
 * The source as captured: a head that declares nine references, and a body that declares two.
 *
 * <p>The measured shape. `/partner/` on a real conversion declared `assets: {own: 33}` and the
 * replica served five — a `-28` that exits the gate — while the captured markup was 155,881
 * characters against a stored body of 60,322. The head was never the page's to lose.</p>
 */
const HEAVY_HEAD = `<html lang="en"><head><title>Home</title>
  <link rel="stylesheet" href="/css/site.css">
  <link rel="icon" href="/favicon.ico">
  <link rel="shortcut icon" href="/favicon-16.png">
  <link rel="apple-touch-icon" href="/touch-120.png">
  <link rel="apple-touch-icon" href="/touch-180.png">
  <link rel="manifest" href="/site.webmanifest">
  <link rel="preload" href="/fonts/a.woff2">
  <link rel="preload" href="/fonts/b.woff2">
  <link rel="preload" href="/fonts/c.woff2"></head><body>
  <img src="/img/hero.png" alt="Hero"><img src="/img/logo.png" alt="Logo">
  <p>${"Body copy that reads like a page a person wrote. ".repeat(6)}</p>
  </body></html>`;

/** The replica: the same body, verbatim, under a Shio layout whose head declares one thing. */
const OWN_HEAD = `<html lang="en"><head><title>Home</title>
  <link rel="stylesheet" href="/theme.css"></head><body>
  <img src="/img/hero.png" alt="Hero"><img src="/img/logo.png" alt="Logo">
  <p>${"Body copy that reads like a page a person wrote. ".repeat(6)}</p>
  </body></html>`;

test("SH581: a fidelity page whose body is unchanged and whose head is the layout's loses nothing",
  async () => {
    const ctx = ctxWith({ served: { "/": OWN_HEAD }, inventory: inventoryOf(HEAVY_HEAD) });

    const result = await diff.run({ site: "Acme" }, [], ctx);

    // The old reading: 11 declared against 3 produced, `-8`, exit 1 — on a page that lost nothing.
    assert.equal(result.exitCode, 0, "the head belongs to the layout on every replica of every mode, "
      + "so counting it into the page's own total scores a shortfall the replica never suffered");
    const assets = result.pages[0].rows.find((row) => row.fact === "body assets");
    assert.deepEqual(
      { declared: assets.declared, produced: assets.produced, delta: assets.delta },
      { declared: 2, produced: 2, delta: 0 },
      "the body at both ends, which is the span a Page stores",
    );
  });

test("SH581: and a body-side loss no longer hides behind a head-side count", async () => {
  const lostBody = OWN_HEAD.replace('<img src="/img/logo.png" alt="Logo">', "");
  const ctx = ctxWith({ served: { "/": lostBody }, inventory: inventoryOf(HEAVY_HEAD) });

  const result = await diff.run({ site: "Acme" }, [], ctx);

  // The other direction of the same defect, and the one that matters more: under the old reading
  // this page reported `assets: 11 → 2`, which is the same kind of number a healthy page reported,
  // so the one image it actually lost was indistinguishable from the eight it never had.
  assert.equal(result.exitCode, 1);
  const assets = result.pages[0].rows.find((row) => row.fact === "body assets");
  assert.deepEqual({ declared: assets.declared, produced: assets.produced }, { declared: 2, produced: 1 });
});

test("SH581: the head is one layout's, reported once at the site level and never scored", async () => {
  const ctx = ctxWith({ served: { "/": OWN_HEAD }, inventory: inventoryOf(HEAVY_HEAD) });

  const result = await diff.run({ site: "Acme" }, [], ctx);

  assert.deepEqual(
    result.head,
    { declared: 9, produced: 1, uniform: true, pages: 1 },
    "counted, named and outside the arithmetic — the same terms SH578 gave the theme",
  );
  assert.equal(result.pages[0].gained, 0);
  assert.match(ctx.out.text, /head: 9 → 1 reference\(s\) in the document head, across 1 page\(s\)/);
});

test("SH581: a capture written before the head split is refused, not scored", () => {
  // The failure mode is worse than SH578's, which is why it gets its own refusal rather than being
  // folded into that one: a version-4 row passes the `own` check, and `own - undefined` is NaN, so
  // every comparison is false and a page that lost every image reports nothing at all.
  const declared = [{
    path: "/", title: "Home", links: { internal: 0, external: 0 },
    assets: { own: 33, style: 0 }, forms: 0, textChars: 400,
  }];

  assert.throws(
    () => compareCapture(declared, new Map([["/", scannedAt(OWN_HEAD, "https://x.test/")]])),
    /declared side carries no head asset count[\s\S]*SH581[\s\S]*countAssets[\s\S]*re-run `shio clone`/,
  );
});

test("SH581: the scanner records which half of the document a reference is in", () => {
  // The boundary the fact crosses, asserted where it is produced rather than only where it is
  // compared: `where` is what `countAssets` reads, and a rule that stopped marking the head would
  // make every test above pass with the numbers silently back to the source's whole document.
  const doc = scanPage(HEAVY_HEAD, "https://x.test/");

  assert.deepEqual(countAssets(doc.assets), { own: 11, head: 9, style: 0 });
  // No `<head>` at all is a fragment, and calling all of it body is the strict reading — the one
  // that keeps a reference scored rather than excused into a fact nothing judges.
  assert.deepEqual(
    countAssets(scanPage('<img src="/a.png"><link rel="icon" href="/f.ico">', "https://x.test/").assets),
    { own: 2, head: 0, style: 0 },
  );
  // And `<body>` closes the head even when the source never wrote `</head>`, which real markup
  // routinely does not.
  assert.deepEqual(
    countAssets(scanPage('<head><link rel="icon" href="/f.ico"><body><img src="/a.png">', "https://x.test/").assets),
    { own: 2, head: 1, style: 0 },
  );
});

/**
 * SH586 — links that changed side rather than went missing.
 *
 * On a real replica this was twenty-two pages carrying the identical pair, `internal links -3`
 * beside `external links +3`, with `shio verify` exiting 0 and eleven review rounds not seeing it.
 * The total is conserved, so nothing is lost, so no shortfall is damage and a gain is never a
 * failure — the two numbers read as unrelated. Naming the join is the half that makes the other
 * half findable.
 */

const withLinks = (internal, external) => ({
  path: "/", title: "Home", links: { internal, external },
  assets: { own: 0, head: 0, style: 0 }, forms: 0, textChars: 400,
});

test("SH586: internal down and external up by the same count is named as one fact", () => {
  const comparison = compareCapture(
    [withLinks(9, 3)],
    new Map([["/", { ...withLinks(6, 6), title: "Home" }]]),
  );

  assert.equal(comparison.wentHome, 3);
  assert.equal(comparison.pages[0].wentHome, 3);

  const report = describeDiff(comparison);
  assert.match(report, /3 link\(s\) now point at the source/, "on the headline, where a reader who "
    + "stops after `nothing lost` would otherwise be told only the true half");
  assert.match(report, /3 link\(s\) now point at the SOURCE/);
  assert.match(report, /internal links: 9 → 6/, "both rows still print — a fact removed from the "
    + "report is a fact nobody can find");
  assert.match(report, /external links: 3 → 6/);
});

test("SH586: equal and opposite is the whole rule — a page that really lost links is not this", () => {
  // The narrowness is the point. A page whose chrome went one way while its layout added links the
  // other is two true facts, and calling it a flip would put a name on a coincidence.
  assert.equal(compareCapture([withLinks(9, 3)],
    new Map([["/", withLinks(6, 4)]])).wentHome, 0, "lost 3, gained 1");
  assert.equal(compareCapture([withLinks(9, 3)],
    new Map([["/", withLinks(9, 6)]])).wentHome, 0, "gained 3, lost none");
  assert.equal(compareCapture([withLinks(9, 3)],
    new Map([["/", withLinks(6, 3)]])).wentHome, 0, "lost 3 outright — that is a real shortfall");
});

test("SH586: a page whose only difference is the flip is not reported `as declared`", () => {
  // The shortcut that hid it: nothing lost, nothing by design, nothing gained on a fidelity page
  // whose every link changed side, and the line read `as declared`.
  const report = describeDiff(compareCapture(
    [withLinks(9, 3)],
    new Map([["/", withLinks(6, 6)]]),
  ));

  assert.doesNotMatch(report, /as declared/);
});

/**
 * SH590 — a constant is not a coincidence.
 *
 * `diff` on a 25-page replica printed `internal links -3` beside `external links +3` on twenty-two
 * of them. Exactly three, on every page, in both directions: one cause, read as forty-four ordinary
 * shortfalls for four rounds. The same shape produced SH578 (`assets +30` on every page of a
 * fidelity replica, uniform to the unit). Both times the give-away was the **invariance**, not the
 * magnitude — a real page-by-page loss varies with the page.
 */

/** N pages, each declaring `was` links and serving `now`, so the delta is identical on every one. */
function replicaOf(count, { was, now, from = 0 }) {
  const declared = [];
  const produced = new Map();
  for (let index = from; index < from + count; index += 1) {
    const path = `/page-${index}`;
    declared.push({ ...withLinks(was, 3), path });
    produced.set(path, { ...withLinks(now, 3), path, title: "Home" });
  }
  return { declared, produced };
}

test("SH590: a delta identical across pages is one candidate cause, not one finding per page", () => {
  const { declared, produced } = replicaOf(22, { was: 9, now: 6 });
  const comparison = compareCapture(declared, produced);

  const group = comparison.invariant.find((entry) => entry.fact === "internal links");
  assert.ok(group, "the repeated delta must be reported");
  assert.equal(group.delta, -3);
  assert.equal(group.pages, 22);
  assert.equal(group.of, 22);

  const report = describeDiff(comparison, { limit: 2 });
  assert.match(report, /delta\(s\) identical across pages/);
  assert.match(report, /internal links: -3\s+on 22 of 22 page\(s\)/);
  // Folded behind the count, with enough paths to go and look at one.
  // Lexical, which is `compareCapture`'s own page order — deterministic, not numeric.
  assert.match(report, /e\.g\. \/page-0, \/page-1, \/page-10 … \+19/);
  // The per-page rows still print. A candidate cause is not a verdict, and this file's standing
  // rule is that a fact removed from the report is a fact nobody can find.
  assert.match(report, /internal links: 9 → 6/);
});

test("SH590: a loss that varies with the page is exactly what this must NOT fold", () => {
  // The discriminator, stated as a fixture: three pages each losing a different number of links is
  // three findings, and calling them one cause would be the mirror of the defect.
  const declared = [];
  const produced = new Map();
  for (const [index, now] of [6, 5, 4].entries()) {
    const path = `/page-${index}`;
    declared.push({ ...withLinks(9, 3), path });
    produced.set(path, { ...withLinks(now, 3), path, title: "Home" });
  }

  assert.deepEqual(compareCapture(declared, produced).invariant, []);
});

test("SH590: two pages agreeing is a coincidence; the floor is three", () => {
  assert.deepEqual(compareCapture(...Object.values(replicaOf(2, { was: 9, now: 6 })))
    .invariant, []);
  assert.equal(compareCapture(...Object.values(replicaOf(3, { was: 9, now: 6 })))
    .invariant.length, 1);
});

test("SH590: the fold is sorted, so two runs over one replica print the same bytes", () => {
  // `internal links` on all six and `body assets` on three of them: most-shared first, and the
  // paths inside a group in the order `compareCapture` compared them.
  const { declared, produced } = replicaOf(6, { was: 9, now: 6 });
  for (const path of ["/page-0", "/page-1", "/page-2"]) {
    produced.set(path, { ...produced.get(path), assets: { own: 2, head: 0, style: 0 } });
  }

  const { invariant } = compareCapture(declared, produced);
  assert.deepEqual(invariant.map((group) => [group.fact, group.delta, group.pages]), [
    ["internal links", -3, 6],
    ["body assets", 2, 3],
  ]);
  assert.deepEqual(invariant[1].paths, ["/page-0", "/page-1", "/page-2"]);
});

test("SH590: a by-design invariant is labelled, not filtered — saying it once is the point", () => {
  // An authorable replica drops the source's chrome on every page, so its shortfall is invariant by
  // construction. That is an argument for saying it ONCE, not for dropping it.
  const { declared, produced } = replicaOf(5, { was: 9, now: 6 });
  const comparison = compareCapture(declared, produced, { mode: "authorable" });

  const group = comparison.invariant.find((entry) => entry.fact === "internal links");
  assert.equal(group.claimed, false);
  assert.match(describeDiff(comparison), /by design — said once rather than per page/);
});


// --- SH640: the draft against what is live ---

/** A read's shape, reduced to what the comparison touches. */
const doc = (address, data) => ({ address, data });

test("SH640: only the fields that differ are reported, and by how much", () => {
  const comparison = comparePublished([
    {
      address: "post:Acme/blog/hello",
      draft: doc("post:Acme/blog/hello", { title: "Hello, revised", body: "<p>Longer now.</p>" }),
      live: doc("post:Acme/blog/hello", { title: "Hello", body: "<p>Short.</p>" }),
    },
    {
      address: "post:Acme/blog/settled",
      draft: doc("post:Acme/blog/settled", { title: "Settled", body: "<p>Same.</p>" }),
      live: doc("post:Acme/blog/settled", { title: "Settled", body: "<p>Same.</p>" }),
    },
  ]);

  assert.equal(comparison.changed, 1);
  assert.equal(comparison.unchanged, 1);
  const changed = comparison.posts.find((post) => post.status === "changed");
  assert.deepEqual(changed.fields.map((field) => field.field), ["body", "title"]);
  assert.ok(changed.fields.every((field) => field.change === "changed"));
  assert.ok(changed.fields.some((field) => field.size > 0),
    "the size delta is what travels, not the text: a rich-text body is the most expensive thing "
      + "this could print, and the values are one `shio read` away");
});

test("SH640: a missing field and an empty one are different findings", () => {
  // The second of the three decisions the read deliberately left to this verb. Deleting a field's
  // value and never declaring the field are different acts, and a diff that folded them would tell
  // a curator something is different and nothing about which act produced it.
  const comparison = comparePublished([{
    address: "post:Acme/blog/fields",
    draft: doc("post:Acme/blog/fields", { kept: "same", emptied: "", appeared: "new" }),
    live: doc("post:Acme/blog/fields", { kept: "same", emptied: "had text", vanished: "gone" }),
  }]);

  const changes = Object.fromEntries(
    comparison.posts[0].fields.map((field) => [field.field, field.change]),
  );
  assert.deepEqual(changes, { appeared: "added", emptied: "changed", vanished: "removed" },
    "an empty string is a VALUE: `\"\"` against absent is added or removed, and `\"\"` against "
      + "text is changed");
  assert.ok(!Object.hasOwn(changes, "kept"), "and a field nobody touched is not a finding");
});

test("SH640: whitespace is a change, because publishing copies the value verbatim", () => {
  // The first decision. A normalising comparison would hide an edit the CDA then serves
  // differently — and a semantic HTML comparison is the first step toward an instance that runs a
  // browser, which is a non-goal.
  const comparison = comparePublished([{
    address: "post:Acme/blog/space",
    draft: doc("post:Acme/blog/space", { body: "<p>One.</p>\n<p>Two.</p>" }),
    live: doc("post:Acme/blog/space", { body: "<p>One.</p><p>Two.</p>" }),
  }]);

  assert.equal(comparison.changed, 1);
  assert.equal(comparison.posts[0].fields[0].field, "body");
});

test("SH640: a post with no live row is new, not a page of added fields", () => {
  // Every field of a page that has never been published would otherwise read as "added" — true,
  // and useless: it buries the three fields that changed on a page somebody is revising under two
  // hundred that did not.
  const comparison = comparePublished([{
    address: "post:Acme/blog/fresh",
    draft: doc("post:Acme/blog/fresh", { title: "Fresh", body: "<p>New.</p>", summary: "s" }),
    live: null,
  }]);

  assert.equal(comparison.fresh, 1);
  assert.equal(comparison.changed, 0);
  assert.deepEqual(comparison.posts[0].fields, []);
  assert.deepEqual(comparison.posts[0].names, ["body", "summary", "title"]);
});

test("SH640: a site that matches what is live says so in one line", () => {
  const report = describePublishedDiff(comparePublished([{
    address: "post:Acme/blog/settled",
    draft: doc("post:Acme/blog/settled", { title: "Settled" }),
    live: doc("post:Acme/blog/settled", { title: "Settled" }),
  }]));

  assert.match(report, /nothing would change/);
  assert.equal(report.trim().split("\n").length, 1,
    "an unchanged post prints nothing beyond the headline: the question is what WILL change, and "
      + "a report listing the pages that will not is one nobody reads to the end");
});

test("SH640: the order is the address, so two runs of one instance print one report", () => {
  // P5. A comparison a reader cannot diff against yesterday's is one they stop running.
  const rows = ["post:Acme/z", "post:Acme/a", "post:Acme/m"].map((address) => ({
    address,
    draft: doc(address, { title: "x" }),
    live: null,
  }));
  assert.deepEqual(
    comparePublished(rows).posts.map((post) => post.address),
    ["post:Acme/a", "post:Acme/m", "post:Acme/z"],
  );
});

test("SH640: the verb asks for the live row only where one exists, and exits on the answer", async () => {
  // The wiring, which the pure comparison above cannot see: `find` reports `published` per row
  // (SH545), so a post that has never been live is known to be new WITHOUT a second read. On a
  // site being built most posts have no live row, and reading each twice would double the most
  // expensive call in the CLI to learn something the first call already said.
  const asked = [];
  const ctx = {
    env: { SHIO_URL: "http://shio.test", SHIO_USER: "admin", SHIO_PASSWORD: "secret" },
    cwd: norm("/repo"),
    stdout: { write: (chunk) => { ctx.out.text += chunk; } },
    stderr: { write: () => {} },
    out: { text: "" },
    fs: { async readFile() { const e = new Error("ENOENT"); e.code = "ENOENT"; throw e; } },
    async fetch(url) {
      const at = new URL(url);
      asked.push(`${at.pathname}?${at.searchParams.toString()}`);
      if (at.pathname.endsWith("/find")) {
        return json({
          results: [
            { address: "post:Acme/live", published: true },
            { address: "post:Acme/never", published: false },
          ],
        });
      }
      const addresses = at.searchParams.getAll("address");
      const state = at.searchParams.get("state");
      return json(addresses.map((address) => ({
        address,
        data: { title: state === "published" ? "As published" : `Draft of ${address}` },
      })));
    },
  };

  const code = await diff.run({ site: "Acme", against: "published" }, [], ctx);

  const live = asked.filter((call) => call.includes("state=published"));
  assert.equal(live.length, 1, "exactly one read asked for the live row");
  assert.ok(live[0].includes("post%3AAcme%2Flive"),
    "and it asked for the post that HAS one — the row `find` already said was never published "
      + "must not be read a second time to be told so again");
  assert.equal(code, 1, "a difference exits non-zero, so a caller can gate a publish on it "
    + "without parsing anything — the same contract `apply --check` carries");
  assert.match(ctx.out.text, /publishing would change 1 post/);
  assert.match(ctx.out.text, /NEW\s+post:Acme\/never/);
});

/** A fetch response carrying JSON, which is all the agent client reads. */
function json(body) {
  return {
    ok: true,
    status: 200,
    async json() { return body; },
    async text() { return JSON.stringify(body); },
  };
}
