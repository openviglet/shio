/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH377 — `shio clone`, the capture pass.
 *
 * Network-free: the crawl's fetch is injected, so a fixture site is a map from path to markup. What
 * that buys is the ability to pin the things a real crawl makes hard to observe — that the walk
 * stops at the origin, that a ceiling cuts the *same* pages twice, that robots.txt is honoured, and
 * above all that **no content is written**, which is the promise every later phase is built on.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import {
  INVENTORY_FILE,
  MARKUP_DIR,
  buildInventory,
  describeInventory,
  localeShape,
  parseRobots,
  pathOf,
  rebaseOwnOrigin,
  resolveHref,
  robotsAllows,
  scanPage,
  settleRendered,
  serializeInventory,
  stripNonMarkup,
} from "../src/clone.mjs";
import { BEHAVIOUR, DOWNLOADABLE } from "../src/clone-assets.mjs";
import { PROGRESS_LOG, STATUS_FILE } from "../src/clone-progress.mjs";
import * as clone from "../src/commands/clone.mjs";

function sink() {
  const out = { text: "" };
  out.write = (chunk) => {
    out.text += chunk;
  };
  return out;
}

// --- the scanner ---

const PAGE = `<!doctype html>
<html lang="en">
<head>
  <title>  Home  page </title>
  <meta name="description" content="What we do">
  <link rel="canonical" href="https://x.test/">
  <link rel="alternate" hreflang="pt-BR" href="https://x.test/pt-br/">
  <link rel="alternate" hreflang="de" href="https://de.x.test/">
  <link rel="stylesheet" href="/css/site.css">
  <link rel="icon" href="/favicon.ico">
  <script src="/js/app.js" defer></script>
  <script src="https://www.googletagmanager.com/gtm.js?id=GTM-1"></script>
  <script>document.write("<a href='/injected'>not a link</a>")</script>
  <style>.a::after { content: "<img src=x>" }</style>
  <!-- <a href="/commented-out">nor this</a> -->
</head>
<body>
  <nav aria-label="Primary">
    <a href="/solutions">Solutions</a>
    <a href="/pricing"> Pricing </a>
    <a href="https://partner.test/">Partners</a>
  </nav>
  <img src="/img/hero.png" srcset="/img/hero@1x.png 1x, /img/hero@2x.png 2x" alt="Hero">
  <p>Body copy that is long enough to look like a page a person wrote, rather than a shell.</p>
  <a href="/about#team">About the team</a>
  <a href="mailto:hi@x.test">Email</a>
  <a href="/whitepaper.pdf">Whitepaper</a>
  <footer><a href="/privacy">Privacy</a> <a href="/admin">Admin</a></footer>
  <form action="/subscribe" method="POST">
    <input type="email" name="email" required>
    <input type="hidden" name="listId" value="7">
    <textarea name="note"></textarea>
    <select name="plan"><option>a</option></select>
    <input type="submit" value="Go">
  </form>
</body>
</html>`;

test("the scanner reads what one page declares, and nothing a comment or a script pretends", () => {
  const doc = scanPage(PAGE, "https://x.test/");

  assert.equal(doc.lang, "en");
  assert.equal(doc.title, "Home page");
  assert.equal(doc.description, "What we do");
  assert.equal(doc.canonical, "https://x.test/");
  // The three ways markup lies to a tag scanner: a comment, a string inside <script>, and CSS
  // content. All three name an href or a src, and none of them is one.
  assert.ok(!doc.links.some((link) => link.href.includes("commented-out")));
  assert.ok(!doc.links.some((link) => link.href.includes("injected")));
  assert.ok(!doc.assets.some((asset) => asset.url.endsWith("/x")));
});

test("navigation is <nav> only, in source order, with the label the source gives it", () => {
  const doc = scanPage(PAGE, "https://x.test/");

  assert.deepEqual(
    doc.nav.map((item) => `${item.label}→${item.href}`),
    ["Solutions→/solutions", "Pricing→/pricing", "Partners→https://partner.test/"],
  );
  // A footer's links are links. Calling them navigation would put a privacy policy in the site's
  // primary menu, and the whole value of this section is that it is the menu as the source spells it.
  assert.ok(!doc.nav.some((item) => item.label === "Privacy"));
  assert.ok(doc.links.some((link) => link.href === "/privacy"));
});

/**
 * SH468's measured case, re-measured for SH472: a nav whose anchors hold a brand tile.
 *
 * <p>The tile is the real one this time, rendered from `www.viglet.org` — a `<div>` carrying the
 * mark, and the mark is **two `<span>`s**, one letter each, because each letter has its own glow
 * animation delay. That is the detail SH468's reduction dropped and the reason its fix looked
 * right: with `<span>Du</span>` an unconditional separator yields `Du Dumont DEP`, and with the
 * markup the site actually serves it yields `D u Dumont DEP`.</p>
 */
const BRANDED_NAV = `<!doctype html>
<html><head><title>Products</title></head><body>
<nav>
  <a href="/dumont"><div class="tile" aria-label="Du"><span><span>D</span><span>u</span></span></div>Dumont DEP</a>
  <a href="/shio"><div class="tile" aria-label="Sh"><span><span>S</span><span>h</span></span></div>Viglet Shio</a>
  <a href="/turing"><div class="tile"><span><span>T</span><span>u</span></span></div>Turing ES</a>
  <a href="/oss"><em>Open</em>Source</a>
  <a href="/plain">  Plain  label  </a>
  <a href="/card"><p>Line</p><p>Two</p></a>
</nav>
<p>Body copy long enough that this reads like a page a person wrote rather than a shell of one.</p>
</body></html>`;

test("SH472: the separator lands at an element boundary the eye sees, and nowhere else", () => {
  const doc = scanPage(BRANDED_NAV, "https://x.test/");
  const labels = new Map(doc.nav.map((item) => [item.href, item.label]));

  assert.equal(
    labels.get("/turing"),
    "Tu Turing ES",
    "the mark's two letters are two sibling `<span>`s inside one element, so they are one word; "
      + "the `</div>` before the label is the only boundary in this anchor that separates. This "
      + "tile carries no `aria-label`, which is what keeps SH472's own case observable after SH477 "
      + "stopped the labelled ones from reaching a label at all",
  );
  assert.equal(
    labels.get("/oss"),
    "OpenSource",
    "and this is the concession SH472 makes and SH468 refused: an inline element is one word to "
      + "a reader, one word to `innerText`, and now one word here",
  );
  assert.equal(
    labels.get("/plain"),
    "Plain label",
    "a label with no elements in it is untouched — `collapse()` still squeezes, so the separator "
      + "never shows up as a doubled or leading space",
  );
  assert.equal(
    labels.get("/card"),
    "Line Two",
    "`<p>` is not inline, so a two-paragraph link is still two words — the SH468 case survives",
  );
});

/**
 * SH477 — the mark that names itself, and the three things that bound the rule.
 *
 * <p>SH472 spaced the tile correctly and left it in the label, so `Du Dumont DEP` is what a
 * conversion writes and `Du` is a menu entry a curator deletes by hand. The signal needs no
 * heuristic: the tile carries `aria-label="Du"` over visible text that is also `Du`, which is the
 * author saying the graphic is decorative.
 *
 * <p>What this test is really for is the two cases the rule must **not** eat. `DEP` sits in the same
 * string as `Du` — same length, same shape, and real content — so "drop short runs" and "drop
 * uppercase runs" are both refuted by the fixture rather than by an argument. And an anchor whose
 * only text is its self-labelling element keeps that text: there the visible words *are* the label.
 */
const SELF_LABELLED = `<!doctype html>
<html><head><title>Marks</title></head><body>
<nav>
  <a href="/beside"><span aria-label="Go">Go</span>Search the docs</a>
  <a href="/alone"><span aria-label="Contact">Contact</span></a>
  <a href="/nested"><div aria-label="Ab"><span aria-label="Ab"><span>A</span><span>b</span></span></div>About us</a>
  <a href="/different"><span aria-label="Open the menu">Menu</span>Products</a>
</nav>
<p>Body copy long enough that this reads like a page a person wrote rather than a shell of one.</p>
</body></html>`;

/**
 * SH670 — a link's label is content, so it is decoded once, like every other field that is.
 *
 * <p>Found by SH658's archetype on its first run: one document gave two answers for the same bytes.
 * `<title>Northwind &amp; Co</title>` reached the inventory as `Northwind & Co` — decoded since
 * SH565 — and the brand link's identical text reached it as `Northwind &amp; Co`, decoded not at
 * all. The label is what `shio report` prints and what a curator is asked to keep, which is SH476's
 * definition of the boundary: decode where markup becomes content.</p>
 *
 * <p><b>The second case is the one that could not be seen from the outside.</b> Attribute values
 * have been decoded at the tokenizer since SH550, so SH477's test — does this frame's text equal
 * its own `aria-label` — was comparing a decoded attribute against raw text. A mark spelled
 * `aria-label="A&amp;B"` over the same drawn letters could therefore never match, and the rule
 * silently did not apply to any mark carrying an entity. Nothing was wrong at either end; the two
 * sides were simply not the same kind of string.</p>
 */
test("SH670: a link's label is decoded, and a mark carrying an entity can name itself", () => {
  const doc = scanPage(
    `<!doctype html>
<html><head><title>Northwind &amp; Co</title></head><body>
<nav>
  <a href="/home"><span aria-label="A&amp;B"><span>A</span><span>&amp;</span><span>B</span></span>Northwind &amp; Co</a>
  <a href="/plain">Tea &#38; Sympathy &mdash; &thetasym;</a>
</nav>
<p>Body copy long enough that this reads like a page a person wrote rather than a shell of one.</p>
</body></html>`,
    "https://x.test/",
  );
  const labels = new Map(doc.nav.map((item) => [item.href, item.label]));

  assert.equal(
    labels.get("/home"),
    "Northwind & Co",
    "SH670: the label is content and is decoded once — the same answer this page's <title> has "
      + "given since SH565. Without it a curator is shown `Northwind &amp; Co` and the entry they "
      + "keep carries the five characters rather than the one.",
  );
  assert.equal(
    labels.get("/plain"),
    "Tea & Sympathy — &thetasym;",
    "SH670: the label inherits `decodeEntities` whole rather than a second spelling of it — the "
      + "numeric form and the named ones this decoder carries, and an unknown name left **verbatim** "
      + "because a reader can report `&thetasym;` and cannot report content that vanished. That "
      + "boundary is the decoder's, deliberately, and this asserts the label is on the same side of "
      + "it as every other captured field.",
  );
  assert.equal(
    doc.selfLabelled,
    1,
    "SH670: and the mark named itself. Its `aria-label` was decoded at the tokenizer and its text "
      + "was not, so this frame could never match and SH477's rule quietly skipped every mark "
      + "carrying an entity — a rule that does not fire looks exactly like a page that has none.",
  );
});

test("SH477: a mark whose aria-label repeats its own text is not part of the link's label", () => {
  const branded = scanPage(BRANDED_NAV, "https://x.test/");
  const labels = new Map(branded.nav.map((item) => [item.href, item.label]));

  assert.equal(
    labels.get("/dumont"),
    "Dumont DEP",
    "`Du` is drawn, not written — the tile says so with an aria-label equal to its own text — and "
      + "`DEP`, which is the same shape and is content, is untouched in the same string",
  );
  assert.equal(labels.get("/shio"), "Viglet Shio");
  assert.equal(branded.selfLabelled, 2, "and the page counts what it dropped, which is the measurement");
});

test("SH477: the rule stops at the two cases that would cost content", () => {
  const doc = scanPage(SELF_LABELLED, "https://x.test/");
  const labels = new Map(doc.nav.map((item) => [item.href, item.label]));

  assert.equal(labels.get("/beside"), "Search the docs");
  assert.equal(
    labels.get("/alone"),
    "Contact",
    "an anchor whose only text names itself keeps it: the author spelled the accessible name the "
      + "way the link reads, and an empty label is indistinguishable from a link that never had one",
  );
  assert.equal(
    labels.get("/nested"),
    "About us",
    "an outer frame that matches covers the inner ones, so the same characters are dropped once",
  );
  assert.equal(
    labels.get("/different"),
    "MenuProducts",
    "an aria-label that says something the text does not is a real accessible name, not a mark, so "
      + "`Menu` survives — run together with `Products` because `<span>` is inline, which is SH472's "
      + "concession and not this rule's business",
  );
  assert.equal(doc.selfLabelled, 2, "two labels dropped a mark; `/alone` and `/different` did not");
});

/**
 * The independent oracle, and the reason this is a check rather than five assertions (SH468).
 *
 * <p>A fabricated token is invisible to everything downstream precisely because it looks like
 * content: `verify` cannot see it, a digest cannot, and a reviewer skimming a menu cannot. What
 * *can* see it is the source's own bytes — a word the capture emits that the page never spelled.
 * Stripping tags is a different mechanism from accumulating text tokens, so this fails on the next
 * fusion whatever introduces it.</p>
 *
 * <p>SH472 moved the line the oracle draws, and it had to: "a word of the page" now means a run
 * between two boundaries a reader sees, so an inline tag is deleted rather than replaced. `a` is
 * the exception, replaced like a block — the accumulator never carries text across `</a>`, so two
 * adjacent anchors are two labels however the source spaces them.</p>
 */
const ORACLE_INLINE = /<\/?(?:span|em|strong|b|i|code|sup|sub|small)\b[^>]*>/gi;

function wordsOf(html) {
  return new Set(
    html
      .replace(ORACLE_INLINE, "")
      .replace(/<[^>]*>/g, " ")
      .replace(/&[a-z]+;/gi, " ")
      .split(/\s+/)
      .filter(Boolean),
  );
}

test("SH468: no extracted label contains a word the source never spelled", () => {
  for (const [name, html] of [["branded nav", BRANDED_NAV], ["the main fixture", PAGE]]) {
    const doc = scanPage(html, "https://x.test/");
    const source = wordsOf(html);
    for (const { text, href } of doc.links) {
      for (const word of text.split(/\s+/).filter(Boolean)) {
        assert.ok(
          source.has(word),
          `${name}: ${href} is labelled "${text}", and "${word}" is not a word of the page`,
        );
      }
    }
  }
});

test("assets come from every attribute that names bytes, srcset included", () => {
  const doc = scanPage(PAGE, "https://x.test/");
  const kinds = new Map(doc.assets.map((asset) => [asset.url.replace("https://x.test", ""), asset.kind]));

  assert.equal(kinds.get("/css/site.css"), "stylesheet");
  assert.equal(kinds.get("/favicon.ico"), "icon");
  assert.equal(kinds.get("/img/hero.png"), "image");
  // One <img> can name three files, and a capture that took only `src` would leave the phone and
  // the retina copies out of the tree.
  assert.equal(kinds.get("/img/hero@2x.png"), "image");
});

/**
 * SH473 — the site's own bundle is an asset, and until now it was an asset nobody produced.
 *
 * <p>`doc.scripts` is the *third-party* list: `buildInventory` drops every same-origin row from it,
 * because SH389 models the hosts a site borrows behaviour from. So `--scripts`, `BEHAVIOUR`,
 * `wantedKind` and the `withDownloads` note all shipped with SH469 and every one of them was
 * unreachable — `planAssets` walks `assets`, and no row in it ever carried `kind: "script"`.</p>
 */
test("SH473: a <script src> is an asset row, and the third-party list stays third-party", () => {
  const doc = scanPage(PAGE, "https://x.test/");
  const kinds = new Map(doc.assets.map((asset) => [asset.url, asset.kind]));

  assert.equal(kinds.get("https://x.test/js/app.js"), "script");
  assert.equal(kinds.get("https://www.googletagmanager.com/gtm.js?id=GTM-1"), "script");

  const inventory = buildInventory({
    origin: "https://x.test",
    seed: "https://x.test",
    visited: [{ url: "https://x.test/", status: 200, depth: 0, doc }],
    robots: parseRobots("User-agent: *\nDisallow:\n"),
    options: {},
  });
  const own = inventory.assets.find((asset) => asset.path === "/js/app.js");
  assert.deepEqual(
    { kind: own?.kind, external: own?.external },
    { kind: "script", external: false },
    "the fetch reads `assets`, so this row is the whole of what --scripts had been missing",
  );
  assert.deepEqual(
    inventory.scripts.map((script) => script.host),
    ["www.googletagmanager.com"],
    "and SH389's section is unchanged — a site's own bundle is not a host it borrows from",
  );
});

/**
 * The instrument, and the reason this task is the third of its shape (SH473).
 *
 * <p>SH452's note became honest while the download stayed unwritten, SH455 had to be filed to finish
 * it, and SH469 shipped the reporting without the fetch. Each time a phase that *described* an
 * omission read as done. What none of them had was a check that the two halves meet: `planAssets`
 * declares the kinds it will fetch, `scanPage` decides which kinds exist, and nothing asserted that
 * every kind in the first is a kind the second can produce — SH469's own tests hand-wrote an
 * inventory holding a `script` row precisely because no capture could make one.</p>
 *
 * <p>So: one page referencing one of everything, and the claim is coverage rather than shape. A kind
 * added to `DOWNLOADABLE` or `BEHAVIOUR` with no path through the scanner fails here, naming itself.
 * Extending the fixture to make it pass is the point — that is the proof the scanner can emit it.</p>
 */
test("SH473: every kind the downloader will fetch is a kind the scanner can produce", () => {
  const one = `<html lang="en"><head>
    <link rel="stylesheet" href="/css/site.css">
    <link rel="icon" href="/favicon.ico">
    <link rel="manifest" href="/site.webmanifest">
    <link rel="preload" href="/fonts/inter.woff2" as="font">
    <script src="/assets/index.js" type="module"></script>
    </head><body>
    <img src="/img/hero.png" alt="">
    <video src="/media/clip.mp4"></video>
    <script>fetch("/content/insights.json")</script>
    <p>${"text ".repeat(60)}</p>
    </body></html>`;
  const produced = new Set(scanPage(one, "https://x.test/").assets.map((asset) => asset.kind));
  const fetched = [...DOWNLOADABLE, ...BEHAVIOUR].sort();

  assert.deepEqual(
    fetched.filter((kind) => !produced.has(kind)),
    [],
    `these kinds are in the fetch lists and no markup in this fixture yields them, which is how a `
      + `flag ships with nothing to act on. Produced: ${[...produced].sort().join(" ")}`,
  );
});

/**
 * SH515 — the collector's half of the one list.
 *
 * <p>Four tasks added a place to look (SH452, SH455, SH473, and this one for style attributes and
 * `fetch()`), and each was found by a replica that was already broken: three 404s and a crash,
 * `insightsData.find is not a function`, because the JSON the page expected was a 404 body. The
 * asymmetry was the defect rather than any single missing pattern — **the rewriter had already
 * found all three**, and the pass that knew was the one that could not fetch.</p>
 *
 * <p>So the expectation is not written here. It is declared in the fixture, beside the page, and
 * `ShReferenceParityTest` reads the same file: what this asserts is that the collector produces the
 * declared set, and what that asserts is that the rewriter resolves it too.</p>
 */
test("SH515: the collector finds every reference the shared fixture declares", async () => {
  const { readFile } = await import("node:fs/promises");
  const { fileURLToPath } = await import("node:url");
  const path = fileURLToPath(new URL("./fixtures/references-page.html", import.meta.url));
  const html = await readFile(path, "utf8");

  const doc = scanPage(html, "https://x.test/en/");

  assert.deepEqual(
    [...new Set(doc.assets.map((asset) => new URL(asset.url).pathname))].sort(),
    expectedIn(html, "asset"),
    "every reference shape on that page, resolved against /en/ — a style attribute's url() and a "
      + "fetch() literal included, which are the two the capture used to miss",
  );
  assert.equal(
    doc.assets.find((asset) => asset.url.endsWith("/content/insights.json"))?.kind,
    "data",
    "and the fetched file is `data`, which is what puts it behind --scripts: without the bundle "
      + "nothing on the replica would ever ask for it",
  );
});

/**
 * SH550 — `&amp;` in a query string, which is the form that will bite a same-origin asset.
 *
 * <p>Not in the shared fixture's EXPECTED block, and the reason is worth stating: that block is
 * compared against `pathname` on this side and against the rewriter's site path on the Java side,
 * and neither carries a query — so a case whose whole content is the query would be asserted by
 * neither. It gets its own test rather than a widened fixture, because widening the comparison to
 * include queries is a change to what parity *means*.
 *
 * <p>A URL joined with a literal `&amp;` is a **different request** — `?w=100&amp;h=50` asks for one
 * parameter named `w` whose value ends in `;h=50` — and nothing about it looks wrong in a listing.
 * That is why this is the dangerous half and the missing image is the visible one.
 */
test("SH550: an entity in a query string decodes, so the request is the one the page makes", () => {
  const page = '<html><body><img src="../img/sized.png?w=100&amp;h=50" alt="Sized">'
    + '<link rel="stylesheet" href="/css/f.css?family=Inter&amp;display=swap"></body></html>';

  const urls = scanPage(page, "https://x.test/en/").assets.map((asset) => asset.url);

  assert.ok(
    urls.includes("https://x.test/img/sized.png?w=100&h=50"),
    `an image's query string is one the source would recognise, got ${JSON.stringify(urls)}`,
  );
  assert.ok(
    urls.includes("https://x.test/css/f.css?family=Inter&display=swap"),
    "and a font URL's, which is the pair the capture that found this actually carried",
  );
  assert.ok(
    !urls.some((url) => url.includes("&amp;")),
    "nothing reaches the downloader still escaped",
  );
});

/**
 * SH550 — and a `<style>` block is NOT decoded, which is the half that is easy to over-fix.
 *
 * <p>Entities are not recognised inside raw-text elements, so `&quot;` there is four characters of a
 * CSS string and not a delimiter. Decoding it would invent a reference the browser never resolves —
 * the mirror image of the defect, and it would pass a naive "no entities anywhere" assertion.
 */
test("SH550: a raw-text body keeps its bytes, because entities are not markup there", () => {
  const page = '<html><head><style>body{background:url(&quot;/img/never.png&quot;)}</style>'
    + '</head><body><p>Copy.</p></body></html>';

  const paths = scanPage(page, "https://x.test/en/").assets.map((asset) =>
    new URL(asset.url).pathname);

  assert.ok(
    !paths.includes("/img/never.png"),
    "a browser would not fetch that either — the decode belongs to attributes only",
  );
});

/** The fixture's own EXPECTED block — the one place the list lives, read rather than restated. */
function expectedIn(html, kind) {
  return [...html.matchAll(new RegExp(`^${kind} (\\S+)$`, "gm"))].map((match) => match[1]).sort();
}

test("a url() inside an inline <style> is an asset the inventory names (SH455)", () => {
  // The measured case: a page with critical CSS and no stylesheet file. SH452 made a *downloaded*
  // stylesheet's references discoverable, so an external sheet's webfonts arrive; an inline block's
  // did not, and `--assets` wrote one file while `propose` reported the fonts as 404-on-the-replica
  // and told the reader to re-run the capture that could not fetch them.
  const page = `<html><head><style>
    @font-face { font-family: Inter; src: url("/fonts/inter.woff2") format("woff2"); }
    body { background: url(/img/bg.jpg); }
    .sprite { background-image: url('../sprite'); }
    .noise { background: url(data:image/gif;base64,AA==); }
    .off { background: url(https://cdn.test/x.png); }
  </style></head><body><p>Copy.</p></body></html>`;
  const kinds = new Map(
    scanPage(page, "https://x.test/a/b").assets.map((asset) => [asset.url, asset.kind]),
  );

  assert.equal(kinds.get("https://x.test/fonts/inter.woff2"), "font");
  assert.equal(kinds.get("https://x.test/img/bg.jpg"), "image");
  // Resolved against the page, and `image` because a url() carries no tag and no rel — the
  // extension is the only evidence, and the default has to be the one that downloads.
  assert.equal(kinds.get("https://x.test/sprite"), "image");
  assert.equal(kinds.get("https://cdn.test/x.png"), "image");
  // A data: URI is already portable; recording it would put base64 in the inventory.
  assert.ok(![...kinds.keys()].some((url) => url.startsWith("data:")));
});

test("a form's fields are what it submits, and an unnamed control is not a field", () => {
  const [form] = scanPage(PAGE, "https://x.test/").forms;

  assert.equal(form.action, "https://x.test/subscribe");
  assert.equal(form.method, "post");
  assert.deepEqual(
    form.fields.map((field) => `${field.name ?? "-"}:${field.type}${field.required ? "!" : ""}`),
    ["email:email!", "listId:hidden", "note:textarea", "plan:select", "-:submit"],
  );
});

test("hrefs that are not pages or bytes are dropped, and a fragment is not a second page", () => {
  assert.equal(resolveHref("mailto:x@y.test", "https://x.test/"), null);
  assert.equal(resolveHref("tel:+15551234", "https://x.test/"), null);
  assert.equal(resolveHref("javascript:void(0)", "https://x.test/"), null);
  assert.equal(resolveHref("#top", "https://x.test/a"), null);
  assert.equal(resolveHref("/about#team", "https://x.test/").href, "https://x.test/about");
  assert.equal(resolveHref("../b?q=1", "https://x.test/a/c").href, "https://x.test/b?q=1");
  // A query is part of the identity: an archive's page two is not page one.
  assert.equal(pathOf(new URL("https://x.test/blog?page=2")), "/blog?page=2");
});

/**
 * The one hazard this module has that no other module in the repository has: it parses bytes fetched
 * from a **stranger's server**. The first draft's tag pattern repeated a group of `name(=value)?`
 * over overlapping character classes — exponential backtracking, which on a hostile page is not a
 * slow crawl but a **hung** one, and a hang leaves no report to diagnose.
 *
 * The timeout is the assertion. It is 5s for work that takes microseconds, so it is not the flaky
 * timing ceiling SH250 warns about — it distinguishes "returns" from "never returns", which is the
 * only distinction that matters here.
 */
test("pathological markup terminates: the scanner is linear, not backtracking", { timeout: 5000 }, () => {
  const attrs = `${" a=".repeat(2000)}`;
  const unclosedQuote = `<a href="${"x".repeat(5000)}`;
  const nested = `<div ${"data-x ".repeat(3000)}`;

  for (const hostile of [`<a${attrs}>`, unclosedQuote, nested, `<${"a".repeat(20000)}`]) {
    const doc = scanPage(hostile, "https://x.test/");
    assert.ok(doc, "the scanner returned");
  }
});

test("stripNonMarkup keeps the open tag and drops only the body", () => {
  const out = stripNonMarkup('<script src="/a.js">var x = "<b>"</script>');

  assert.match(out, /<script src="\/a\.js">/);
  assert.ok(!out.includes("var x"));
});

// --- robots.txt ---

const ROBOTS = `# comment
User-agent: *
Disallow: /admin
Disallow: /*?sort=
Allow: /admin/public
Crawl-delay: 2

User-agent: shio-clone
User-agent: another-bot
Disallow: /private
Sitemap: https://x.test/sitemap.xml
`;

test("an exact agent match wins outright and does not merge with the star group", () => {
  const robots = parseRobots(ROBOTS, "shio-clone");

  assert.equal(robots.matched, "shio-clone");
  assert.equal(robotsAllows(robots, "/private/x"), false);
  // The star group's rules are not this crawler's, so /admin is allowed here — merging the two is
  // the mistake, and it would make this crawler quietly stricter than the site asked.
  assert.equal(robotsAllows(robots, "/admin"), true);
  assert.deepEqual(robots.sitemaps, ["https://x.test/sitemap.xml"]);
});

test("the star group applies to an agent nobody named, with the longest rule winning", () => {
  const robots = parseRobots(ROBOTS, "somebody-else");

  assert.equal(robots.matched, "*");
  assert.equal(robotsAllows(robots, "/admin"), false);
  // Longest match wins and Allow wins a tie — which is what opens one path inside a closed subtree.
  assert.equal(robotsAllows(robots, "/admin/public/x"), true);
  assert.equal(robotsAllows(robots, "/blog?sort=asc"), false, "a wildcard rule is honoured");
  assert.equal(robots.crawlDelay, 2);
});

test("no robots.txt allows everything, and an empty Disallow does not close the site", () => {
  const absent = parseRobots("", "shio-clone");
  assert.equal(absent.present, false);
  assert.equal(robotsAllows(absent, "/anything"), true);

  const permissive = parseRobots("User-agent: *\nDisallow:\n", "shio-clone");
  assert.equal(robotsAllows(permissive, "/anything"), true);
});

// --- locales ---

test("a locale's URL shape is derived, and 'unknown' is a real answer", () => {
  assert.equal(localeShape("pt-BR", "https://x.test/pt-br/sobre", "https://x.test"), "prefix");
  assert.equal(localeShape("pt", "https://x.test/pt/sobre", "https://x.test"), "prefix");
  assert.equal(localeShape("de", "https://de.x.test/", "https://x.test"), "subdomain");
  assert.equal(localeShape("fr", "https://example.fr/", "https://x.test"), "host");
  assert.equal(localeShape("es", "https://x.test/sobre?lang=es", "https://x.test"), "query");
  assert.equal(localeShape("x-default", "https://x.test/", "https://x.test"), "default");
  // A site may key the locale off a cookie. Guessing a prefix here would be wrong in a way nothing
  // downstream could detect.
  assert.equal(localeShape("ja", "https://x.test/about", "https://x.test"), "unknown");
});

// --- the crawl ---

const SITE = {
  "/robots.txt": { type: "text/plain", body: "User-agent: *\nDisallow: /admin\n" },
  "/": { body: PAGE },
  "/solutions": { body: page("Solutions", ['<a href="/solutions/one">One</a>', '<a href="/">Home</a>']) },
  "/solutions/one": { body: page("One", ['<a href="/solutions/two">Two</a>']) },
  "/solutions/two": { body: page("Two", []) },
  "/pricing": { body: page("Pricing", []) },
  "/about": { body: page("About", []) },
  "/privacy": { body: page("Privacy", []) },
  "/admin": { body: page("Admin", []) },
  "/whitepaper.pdf": { type: "application/pdf", body: "%PDF-1.4" },
};

function page(title, body) {
  return `<html lang="en"><head><title>${title}</title></head><body><p>${"text ".repeat(60)}</p>${body.join("")}</body></html>`;
}

function ctxWith(site = SITE, { onFetch, render } = {}) {
  const written = {};
  const requested = [];
  return {
    env: {},
    cwd: "/repo",
    stdout: sink(),
    stderr: sink(),
    written,
    requested,
    render,
    async fetch(url, init = {}) {
      requested.push(url);
      onFetch?.(url, init);
      const parsed = new URL(url);
      const entry = site[`${parsed.pathname}${parsed.search}`] ?? site[parsed.pathname];
      if (!entry) {
        return response({ status: 404, body: "not found" });
      }
      return response({ ...entry, url });
    },
    fs: {
      async mkdir() {},
      async writeFile(path, body) {
        written[path.replace(/\\/g, "/")] = body;
      },
      async appendFile(path, body) {
        const key = path.replace(/\\/g, "/");
        written[key] = (written[key] ?? "") + body;
      },
      async readFile(path) {
        const error = new Error(`ENOENT: ${path}`);
        error.code = "ENOENT";
        throw error;
      },
    },
  };
}

function response({ status = 200, body = "", type = "text/html; charset=utf-8", location } = {}) {
  const headers = new Map();
  if (type) headers.set("content-type", type);
  if (location) headers.set("location", location);
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (name) => headers.get(String(name).toLowerCase()) ?? null },
    async text() {
      return body;
    },
  };
}

test("the capture writes one artefact and no content, whatever else it reports", async () => {
  const ctx = ctxWith();
  const result = await clone.run({}, ["https://x.test"], ctx);

  // The promise every later phase is built on. A capture that wrote a post would make the
  // conversion unreviewable — the thing it was supposed to let you review would already exist.
  //
  // SH543 loosened the letter of this and not the substance. The capture used to write exactly one
  // file; it now also reports where it is, to two files under the same `.shio/` metadata directory.
  // The invariant that matters was never the count — it is that nothing lands where content lives,
  // which is why it is asserted as a prefix below rather than left implied by a list of three.
  assert.deepEqual(Object.keys(ctx.written).sort(), [
    `/repo/${PROGRESS_LOG}`,
    `/repo/${STATUS_FILE}`,
    `/repo/${INVENTORY_FILE}`,
  ].map((path) => path.replace(/\\/g, "/")).sort());
  for (const path of Object.keys(ctx.written)) {
    assert.ok(path.startsWith("/repo/shio/.shio/"),
      `${path} is outside the metadata directory, so the capture wrote something it promised not to`);
  }
  assert.equal(result.exitCode, 0);
  assert.ok(result.inventory.pages.length > 3);
});

test("SH543 — the status says where a crawl is, and its size does not grow with the crawl", async () => {
  const ctx = ctxWith();
  await clone.run({ maxPages: "3" }, ["https://x.test"], ctx);

  const status = JSON.parse(ctx.written[`/repo/${STATUS_FILE}`]);
  // Terminal, and that is the whole reason a poller can stop: a status still reading `running`
  // after the process is gone is the ambiguity the channel exists to remove.
  assert.equal(status.state, "done");
  assert.equal(status.phase, "crawl");
  assert.equal(status.done, 3);
  assert.equal(status.total, 3);
  assert.equal(status.remaining, 0);
  // The ceilings travel with it, because they are what a caller decides with — 13 of 200 at 30 s
  // each is a decision to make now rather than in ninety minutes.
  assert.equal(status.ceiling.maxPages, 3);
  assert.equal(status.counts.ok, 3);
  assert.ok(status.pid > 0);

  // The heartbeat, which is the only field that separates a slow page from a wedged one. A count
  // cannot: it reads the same at rest.
  assert.ok(Date.parse(status.updatedAt) >= Date.parse(status.startedAt));

  // Constant size is the P3 claim, and it is the reason there are two files rather than one: this
  // is the one a caller re-reads, so nothing in it may accumulate.
  assert.ok(!JSON.stringify(status).includes("["),
    "the status carries no list, or re-reading it costs more each time");
});

test("SH543 — --depth 0 reports the total it knows, not the ceiling it will never reach", async () => {
  const ctx = ctxWith();
  await clone.run({ depth: 0 }, ["https://x.test"], ctx);

  const status = JSON.parse(ctx.written[`/repo/${STATUS_FILE}`]);
  // The seed and nothing else. Carrying the 200-page default here printed `1 of 200, 199 remaining`
  // for a crawl that had already finished — an upper bound is the cautious choice everywhere the
  // frontier is unknown, and simply the wrong number in the one place it is not.
  assert.equal(status.total, 1);
  assert.equal(status.done, 1);
  assert.equal(status.remaining, 0);
  assert.equal(status.ceiling.maxPages, 200);
});

test("SH543 — the log keeps the cost of a page, which the inventory does not", async () => {
  const ctx = ctxWith({ ...SITE, "/about": { status: 500, body: "boom" } });
  await clone.run({}, ["https://x.test"], ctx);

  const all = ctx.written[`/repo/${PROGRESS_LOG}`].trim().split("\n").map((l) => JSON.parse(l));
  // The last line is the terminal one and carries no `n`, which is how a reader tells a log whose
  // run finished from one whose process was killed mid-crawl.
  assert.equal(all.at(-1).state, "done");
  assert.equal(all.at(-1).n, undefined);
  const lines = all.filter((line) => line.n !== undefined);
  assert.ok(lines.length > 3);
  for (const line of lines) {
    assert.equal(line.phase, "crawl");
    assert.ok(typeof line.ms === "number", "a page's cost is the finding that ends a doomed run early");
    assert.ok(line.path.startsWith("/"));
    // `at` is the timestamp, as it is in `shio dev --content`'s event log. It once collided with the
    // page's own path and the clock silently lost — a heartbeat that reads as a URL is not one.
    assert.ok(Number.isFinite(Date.parse(line.at)), `at is not a timestamp: ${line.at}`);
  }
  // Greppable by outcome — the question the log answers that the status cannot, since the status
  // keeps a count and not which page produced it.
  const failed = lines.filter((line) => line.outcome === "failed");
  assert.equal(failed.length, 1);
  assert.equal(failed[0].path, "/about");
  assert.match(failed[0].error, /HTTP 500/);
});

test("SH543 — a failure written for a terminal is rewritten for grep", async () => {
  // Verbatim from Playwright, which is the failure this whole channel was built to surface: ANSI
  // colour and a four-line call log, in a format whose premise is one line per unit. Found by
  // reading the first real run's own log.
  const render = () => ({
    async goto() {
      throw new Error(
        "page.goto: Timeout 8000ms exceeded.\nCall log:\n\u001b[2m  - navigating to \"https://x.test/\", "
          + "waiting until \"networkidle\"\u001b[22m\n",
      );
    },
    async content() {
      return "";
    },
    async close() {},
  });
  const ctx = ctxWith(SITE, { render });
  await clone.run({ render: true }, ["https://x.test"], ctx);

  const log = ctx.written[`/repo/${PROGRESS_LOG}`];
  const line = JSON.parse(log.trim().split("\n")[0]);
  assert.equal(log.trim().split("\n").length, 2, "one line per unit, plus the terminal row");
  assert.ok(!line.error.includes("\u001b"), `an escape sequence survived: ${JSON.stringify(line.error)}`);
  assert.ok(!line.error.includes("\n"));
  assert.match(line.error, /Timeout 8000ms exceeded\. Call log: - navigating to/);
});

test("SH543 — --no-progress writes the artefact and nothing else", async () => {
  const ctx = ctxWith();
  await clone.run({ noProgress: true }, ["https://x.test"], ctx);

  assert.deepEqual(Object.keys(ctx.written), [`/repo/${INVENTORY_FILE}`.replace(/\\/g, "/")]);
});

test("SH543 — a crawl that throws does not leave a status reading `running`", async () => {
  // The seed redirects off-origin, which `run` refuses by throwing — the one failure that happens
  // after the channel is open and before a single page is walked.
  const ctx = ctxWith({ "/": { status: 301, location: "https://elsewhere.test/" } });
  await assert.rejects(() => clone.run({}, ["https://x.test"], ctx));

  const status = JSON.parse(ctx.written[`/repo/${STATUS_FILE}`]);
  assert.equal(status.state, "failed");
  assert.match(status.error ?? "", /different host/);
});

test("SH543 — losing the channel does not lose the crawl", async () => {
  const ctx = ctxWith();
  ctx.fs.writeFile = async (path, body) => {
    if (path.replace(/\\/g, "/").includes("clone-status")) throw new Error("disk full");
    ctx.written[path.replace(/\\/g, "/")] = body;
  };
  const result = await clone.run({}, ["https://x.test"], ctx);

  // The thing meant to make a run legible must not be the thing that ends it.
  assert.equal(result.exitCode, 0);
  assert.ok(result.inventory.pages.length > 3);
  assert.match(ctx.stderr.text, /progress could not be written/);
});

test("--markup caches the HTML beside the inventory, and still writes no content (SH379)", async () => {
  const ctx = ctxWith();
  const { inventory } = await clone.run({ markup: true }, ["https://x.test"], ctx);

  const paths = Object.keys(ctx.written);
  assert.ok(paths.includes(`/repo/${INVENTORY_FILE}`.replace(/\\/g, "/")));
  // Every other file is under `.shio/`, which is the derived-snapshot root — not `shio/content/`,
  // not `shio/assets/`. That is what keeps SH377's promise true with the flag on: the conversion is
  // still reviewable before anything reaches somebody's instance.
  // SH543's two files are under the same root and are not the markup cache, so they are named out
  // rather than swept into the `cached` list this asserts a page-per-file over.
  const reported = [`/repo/${STATUS_FILE}`, `/repo/${PROGRESS_LOG}`, `/repo/${INVENTORY_FILE}`]
    .map((path) => path.replace(/\\/g, "/"));
  const cached = paths.filter((path) => !reported.includes(path));
  assert.ok(cached.length > 3, `expected a file per page, got ${cached.length}`);
  for (const path of cached) assert.ok(path.startsWith(`/repo/${MARKUP_DIR}/`), path);

  // The row names its own file, so `shio propose` never has to re-derive a slug the crawl chose.
  const home = inventory.pages.find((page) => page.path === "/");
  assert.match(home.markup, /\.html$/);
  // SH586 narrowed "verbatim": the cache is the source's markup with URLs on the source's OWN
  // origin written as the paths they already mean, and nothing else touched. So this is no longer
  // byte-identity — it is byte-identity after that one substitution, which `rebaseOwnOrigin` is.
  assert.equal(
    ctx.written[`/repo/${MARKUP_DIR}/${home.markup}`],
    rebaseOwnOrigin(SITE["/"].body, "https://x.test"),
  );
  assert.equal(inventory.crawl.markup, true);
});

test("without --markup the crawl carries no HTML at all, and the report says how to get it", async () => {
  const ctx = ctxWith();
  const { inventory } = await clone.run({}, ["https://x.test"], ctx);

  assert.equal(inventory.crawl.markup, false);
  assert.ok(inventory.pages.every((page) => page.markup === undefined));
  assert.match(ctx.stdout.text, /markup:\s+not cached — add --markup/);
});

test("the walk stays on one origin, and records what it did not follow", async () => {
  const ctx = ctxWith();
  const { inventory } = await clone.run({}, ["https://x.test"], ctx);

  for (const url of ctx.requested) {
    assert.equal(new URL(url).host, "x.test", `the crawl left the origin for ${url}`);
  }
  // Off-origin is recorded rather than dropped: a conversion has to decide what to do about a
  // partner link, and it cannot decide about one nothing wrote down.
  assert.deepEqual(inventory.outbound, [{ host: "partner.test", refs: 1 }]);
  assert.deepEqual(inventory.scripts.map((each) => each.host), ["www.googletagmanager.com"]);
});

test("robots.txt bounds the crawl, and the report says which path it skipped", async () => {
  const ctx = ctxWith();
  const { inventory } = await clone.run({}, ["https://x.test"], ctx);

  assert.ok(!inventory.pages.some((each) => each.path === "/admin"));
  assert.ok(inventory.notes.some((note) => /robots\.txt disallows \/admin/.test(note)));
  assert.equal(inventory.crawl.robots.present, true);
});

test("--ignore-robots is recorded, so a capture cannot later look permitted", async () => {
  const ctx = ctxWith();
  const { inventory } = await clone.run({ ignoreRobots: true }, ["https://x.test"], ctx);

  assert.ok(inventory.pages.some((each) => each.path === "/admin"));
  assert.ok(inventory.notes.some((note) => /--ignore-robots was passed/.test(note)));
  assert.equal(ctx.requested.some((url) => url.endsWith("/robots.txt")), false);
});

test("--depth bounds the levels, and --max-pages cuts the same pages twice", async () => {
  const shallow = ctxWith();
  const deep = ctxWith();
  const { inventory: atOne } = await clone.run({ depth: 1 }, ["https://x.test"], shallow);
  const { inventory: atThree } = await clone.run({ depth: 3 }, ["https://x.test"], deep);

  assert.ok(!atOne.pages.some((each) => each.path === "/solutions/one"));
  assert.ok(atThree.pages.some((each) => each.path === "/solutions/one"));

  // The determinism that makes the artefact worth keeping: a bounded crawl of an unchanged site
  // must produce the same bytes, or the diff against the next capture is noise (P5).
  const first = await clone.run({ maxPages: 3 }, ["https://x.test"], ctxWith());
  const second = await clone.run({ maxPages: 3 }, ["https://x.test"], ctxWith());
  assert.equal(serializeInventory(first.inventory), serializeInventory(second.inventory));
  assert.equal(first.inventory.crawl.truncated, true);
  assert.ok(first.inventory.notes.some((note) => /every count in it is a lower bound/.test(note)));
});

test("SH431: a served non-HTML response is recorded as not a page, not as unreadable", async () => {
  // The distinction this asserts is the whole of SH431. Capturing openviglet.github.io reported
  // "could not be read (24)", every row a 200 with `not html (application/json)`: the site's data
  // surface, linked on purpose and served correctly. The coverage gate reads `problems.length` as
  // "source page(s) could not be read at all" and downgrades every count in its own report on the
  // strength of it, so a complete capture came back with two dozen phantom gaps.
  const ctx = ctxWith();
  const { inventory } = await clone.run({}, ["https://x.test"], ctx);

  const pdf = inventory.data.find((each) => each.path === "/whitepaper.pdf");
  assert.ok(pdf, "the PDF is recorded — a reader may well decide the replica needs it");
  assert.equal(pdf.type, "application/pdf", "with what it actually was, as a field rather than "
    + "as a content type buried in a message");
  assert.equal(pdf.status, 200, "and with the status that says the crawl did not fail on it");
  assert.ok(
    !inventory.problems.some((each) => each.path === "/whitepaper.pdf"),
    "`problems` means the crawl could not read this, which is a different claim with a different fix",
  );
  // It is a *link* target — which is why the crawl fetched it — so it stays in the visited set and
  // the SH387 unfollowed list does not report a link to it as unresolved either. That is the second
  // way this could have been got wrong: moving the row out of `problems` and out of `visited` would
  // have turned every reference to it into a phantom gap of a different kind.
  assert.ok(!inventory.unfollowed.some((each) => each.path === "/whitepaper.pdf"));
});

test("SH431: a fetch that really failed is still a problem, and the report keeps them apart", async () => {
  const ctx = ctxWith({ ...SITE, "/about": { status: 500, body: "boom" } });
  const { inventory } = await clone.run({}, ["https://x.test"], ctx);

  const broken = inventory.problems.find((each) => each.path === "/about");
  assert.ok(broken, "a 500 is the honest case the unreadable list exists for");
  assert.match(broken.reason, /HTTP 500/);
  assert.match(ctx.stdout.text, /could not be read \(1\)/);
  assert.match(
    ctx.stdout.text,
    /not a page \(served, not HTML\)/,
    "two headings, because the reader's next question differs: one asks what is broken on the "
      + "source, the other whether the replica needs a data surface too",
  );
});

test("SH432: a menu repeated in a footer is on one page, and says how often it appears", async () => {
  // The measured case: a capture of ONE page reported `navigation (3)` with `on 2 page(s)` against
  // all three items, because the source repeats its links in a topbar <nav> and again in a footer
  // <nav>. What was counted was occurrences and what was printed was pages — and a count above the
  // pages captured cannot be read as a lower bound the way a truncated one can. It is just wrong.
  const twice = `<html lang="en"><head><title>Home</title></head><body>
    <nav><a href="/solutions">Solutions</a><a href="/pricing">Pricing</a></nav>
    <p>${"text ".repeat(60)}</p>
    <form action="/subscribe" method="POST"><input type="email" name="email"></form>
    <footer><nav><a href="/solutions">Solutions</a><a href="/pricing">Pricing</a></nav></footer>
    <form action="/subscribe" method="POST"><input type="email" name="email"></form>
  </body></html>`;
  const ctx = ctxWith({ "/": { body: twice } });
  const { inventory } = await clone.run({ depth: 0 }, ["https://x.test"], ctx);

  assert.equal(inventory.pages.length, 1, "one page captured, which is what makes the defect visible");
  for (const item of inventory.navigation) {
    assert.equal(item.on, 1, `${item.href} is on one page — the label promises distinct pages`);
    assert.equal(item.occurrences, 2, "and the repetition is kept: a link that appears twice on "
      + "every page is a fact about the menu, not noise to drop");
  }
  assert.match(
    ctx.stdout.text,
    /on 1 page\(s\), 2 occurrence\(s\)/,
    "the report prints both, so a reviewer can tell a menu from a repetition",
  );

  // The same arithmetic, one field along — `on` was a per-sighting counter there too, and the
  // comment beside it already said "how many pages carry it".
  assert.equal(inventory.forms.length, 1, "one form, deduped");
  assert.equal(inventory.forms[0].on, 1);
  assert.equal(inventory.forms[0].occurrences, 2);
});

test("SH432: reach across pages is still counted, which is the number the field exists for", async () => {
  const menu = (title) => `<html lang="en"><head><title>${title}</title></head><body>
    <nav><a href="/solutions">Solutions</a><a href="/about">About</a></nav>
    <p>${"text ".repeat(60)}</p></body></html>`;
  const ctx = ctxWith({ "/": { body: menu("Home") }, "/about": { body: menu("About") } });
  const { inventory } = await clone.run({ depth: 1 }, ["https://x.test"], ctx);

  const solutions = inventory.navigation.find((item) => item.href === "/solutions");
  assert.equal(solutions.on, 2, "two pages carry it, and that is the reach a reviewer reads");
  assert.equal(solutions.occurrences, 2);
  assert.ok(
    !/occurrence\(s\)/.test(ctx.stdout.text),
    "and nothing is printed about occurrences when they equal the pages — a second number that "
      + "always says nothing is one a reader stops reading",
  );
});

test("a seed that redirects off-host is refused, naming the host it actually got", async () => {
  const site = { ...SITE, "/": { status: 301, location: "https://www.x.test/", body: "" } };
  const ctx = ctxWith(site);

  await assert.rejects(
    () => clone.run({}, ["https://x.test"], ctx),
    (error) => {
      assert.match(error.message, /redirects to https:\/\/www\.x\.test, which is a different host/);
      // The fix is a copy-paste, which is what a teaching error owes its reader (P6).
      assert.match(error.message, /shio clone https:\/\/www\.x\.test/);
      return true;
    },
  );
});

test("a page that redirects on-host is followed once; off-host mid-crawl is just a link", async () => {
  const site = {
    ...SITE,
    "/about": { status: 302, location: "/about-us", body: "" },
    "/about-us": { body: page("About us", []) },
    "/pricing": { status: 302, location: "https://elsewhere.test/pricing", body: "" },
  };
  const { inventory } = await clone.run({}, ["https://x.test"], ctxWith(site));

  assert.ok(inventory.pages.some((each) => each.title === "About us"));
  const pricing = inventory.problems.find((each) => each.path === "/pricing");
  assert.match(pricing.reason, /redirects off-origin to https:\/\/elsewhere\.test\/pricing/);
});

test("locales, forms and navigation are aggregated across the whole crawl", async () => {
  const { inventory } = await clone.run({}, ["https://x.test"], ctxWith());

  assert.deepEqual(
    inventory.locales.map((each) => `${each.locale}/${each.shape}`),
    ["de/subdomain", "en/declared", "pt-BR/prefix"],
  );
  assert.equal(inventory.forms.length, 1);
  assert.equal(inventory.forms[0].fields.length, 5);
  // One nav on every page is one navigation, with the page count beside it.
  assert.deepEqual(inventory.navigation.map((each) => each.label), ["Solutions", "Pricing", "Partners"]);
});

test("--render uses the browser's DOM through the same scanner", async () => {
  const seen = [];
  // The markup a fetch would never see: the shell's <div id="root"> is empty on the wire, and this
  // is what the page looks like once its own JavaScript has run.
  const hydrated = page("Hydrated", ['<a href="/only-after-hydration">Deep</a>']);
  const browserPage = {
    async goto(url) {
      seen.push(url);
    },
    async content() {
      return hydrated;
    },
    async close() {},
  };
  const ctx = ctxWith(SITE, { render: () => browserPage });

  // depth 0 is the seed and nothing else — the cheapest possible look at a site.
  const { inventory } = await clone.run({ render: true, depth: 0 }, ["https://x.test"], ctx);

  assert.equal(inventory.crawl.rendered, true);
  assert.equal(inventory.pages[0].title, "Hydrated");
  assert.equal(inventory.pages[0].rendered, true);
  assert.deepEqual(seen, ["https://x.test/"]);
});

test("SH517: --render gives back the machinery it baked, and keeps the content", () => {
  // The measurement: the source has `<div id="particles"></div>` — empty, filled at runtime by a
  // particles library — and the replica had `<div id="particles" style="margin-left: -740px">
  // <canvas width="1480" height="770">`, a dead canvas sized for the capture's viewport with
  // nothing painting on it. Baking the DOM a script produced is right when that DOM is CONTENT and
  // wrong when it is MACHINERY, and the same mechanism does both.
  const source = '<html><body><div id="particles"></div>'
    + '<section class="uk-section uk-active"><h1>Real</h1></section></body></html>';
  const baked = '<html><body><div id="particles" style="margin-left: -740px; margin-top: -385px;">'
    + '<canvas width="1480" height="770"></canvas></div>'
    + '<section class="uk-section uk-active uk-scrollspy-inview" style="">'
    + '<h1>Real</h1><p style="color:red">Kept.</p></section></body></html>';

  const { html, froze } = settleRendered(baked, source);

  assert.ok(!/<canvas/i.test(html), "a canvas the source did not have was made by a script, and a "
    + "canvas is meaningless without the loop that draws on it — baking one also prevents the "
    + "library from creating its own");
  assert.ok(!/class="[^"]*uk-active/.test(html), "and a framework's state class is a scroll "
    + "position caught mid-flight: the replica opened with its first slide already chosen");
  assert.match(html, /class="uk-section"/, "while the classes an author wrote are untouched");
  assert.ok(!/style=""/.test(html), "an empty style attribute is residue — no author writes one");
  assert.match(html, /style="color:red"/, "but a style with a declaration in it stays: there is no "
    + "way to tell one a script wrote from one an author did, and SH515 made its url() reachable");
  assert.match(html, /<h1>Real<\/h1>/, "and the content --render exists to capture is all still here");
  assert.deepEqual(froze, { canvas: 1, style: 1, classes: 2 },
    "counted, because the note that says what was frozen is the whole of the reporting half");
});

test("SH517: a canvas the source itself declares is authored markup and is kept", () => {
  // The evidence is the response body, which costs no request — it is the bytes the navigation
  // already transferred. An authored `<canvas>` is markup somebody wrote and its children are
  // fallback content; only the surplus goes.
  const source = '<html><body><canvas id="chart">Your browser cannot draw.</canvas></body></html>';
  const { html, froze } = settleRendered(
    '<html><body><canvas id="chart" width="800" height="600"></canvas></body></html>', source);

  assert.match(html, /<canvas id="chart"/);
  assert.equal(froze.canvas, 0);
});

test("SH517: the crawl says what it froze, because an undeclared cut reads as a clean capture", async () => {
  const source = '<html lang="en"><head><title>App</title></head><body><div id="particles"></div>'
    + `<p>${"text ".repeat(60)}</p></body></html>`;
  const baked = source.replace('<div id="particles"></div>',
    '<div id="particles"><canvas width="1480" height="770"></canvas></div>');
  const browserPage = {
    async goto() {
      return { text: async () => source };
    },
    async content() {
      return baked;
    },
    async close() {},
  };
  const ctx = ctxWith(SITE, { render: () => browserPage });

  const { inventory } = await clone.run({ render: true, depth: 0 }, ["https://x.test"], ctx);

  assert.ok(
    inventory.notes.some((note) => /given back/.test(note) && /canvas/.test(note)),
    `a capture that freezes behaviour has to say what it froze. Notes: ${JSON.stringify(inventory.notes)}`,
  );
});

test("a thin page is a note pointing at --render, not a silent miss", () => {
  const shell = '<html lang="en"><head><title>App</title></head><body><div id="root"></div><script src="/app.js"></script></body></html>';
  const inventory = buildInventory({
    origin: "https://x.test",
    seed: "https://x.test",
    visited: [{ url: "https://x.test/", status: 200, depth: 0, doc: scanPage(shell, "https://x.test/") }],
    robots: parseRobots("User-agent: *\nDisallow:\n"),
    options: {},
  });

  // The failure mode a fetch-only crawl has and cannot detect from the outside: a complete-looking
  // capture of a page whose content never arrived.
  assert.ok(inventory.notes.some((note) => /re-run with --render/.test(note)));
});

test("a sitemap named in robots is reported as NOT walked", () => {
  const inventory = buildInventory({
    origin: "https://x.test",
    seed: "https://x.test",
    visited: [],
    robots: parseRobots("User-agent: *\nSitemap: https://x.test/sitemap.xml\n"),
    options: {},
  });

  // No silent caps: a page reachable only from a sitemap is missing, and a report that did not say
  // so would read as complete coverage.
  assert.ok(inventory.notes.some((note) => /did NOT walk/.test(note)));
});

test("the report is bounded per section and the inventory keeps everything", async () => {
  const { inventory } = await clone.run({}, ["https://x.test"], ctxWith());
  const short = describeInventory(inventory, { limit: 1 });

  assert.match(short, /pages \(\d+\)/);
  assert.match(short, /… \d+ more in the inventory/);
  assert.match(short, /no content was written/);
});

test("a crawl that reached nothing exits non-zero", async () => {
  const ctx = ctxWith({ "/robots.txt": { type: "text/plain", body: "" } });
  const result = await clone.run({}, ["https://x.test"], ctx);

  // "0 pages" from a broken seed and "a site with no pages" are different claims, and a CI step
  // that wrapped this would read the first as the second.
  assert.equal(result.exitCode, 1);
  assert.equal(result.inventory.pages.length, 0);
});

test("bad input explains itself", async () => {
  await assert.rejects(() => clone.run({}, [], ctxWith()), /pass the site to capture/);
  await assert.rejects(() => clone.run({}, ["not a url"], ctxWith()), /is not a URL/);
  await assert.rejects(() => clone.run({}, ["ftp://x.test"], ctxWith()), /not http\(s\)/);
  await assert.rejects(() => clone.run({ depth: "deep" }, ["https://x.test"], ctxWith()), /--depth takes a whole number/);
});

/**
 * SH586 — a replica whose links go home.
 *
 * `openviglet.github.io/model-catalog/models/azure/` writes three of its own links as absolute URLs
 * on its own origin. A fidelity replica serves them verbatim, so clicking one leaves for the
 * original. Measured across the whole replica: twenty-two pages, exactly three each,
 * `internal links -3` beside `external links +3` on every one. The total is conserved, which is
 * what made it invisible — and `shio verify` exits 0, because *no href carries the source's base*
 * is check 2 of a checklist a person runs, not a rule the gate holds.
 */

test("SH586: a link on the source's own origin is cached as the path it already means", () => {
  const html = rebaseOwnOrigin(
    '<a href="https://openviglet.github.io/model-catalog/">catalog</a>'
      + '<a href="//openviglet.github.io/models/azure/">azure</a>'
      + '<a href="https://openviglet.github.io">home</a>',
    "https://openviglet.github.io",
  );

  assert.match(html, /href="\/model-catalog\/"/);
  assert.match(html, /href="\/models\/azure\/"/, "protocol-relative is the same claim");
  assert.match(html, /href="\/"/, "a bare origin means the root — a naive strip leaves href=\"\"");
});

test("SH586: a genuinely off-site link is left exactly alone", () => {
  // The distinction SH466 could not make and this one can: an absolute URL belongs to whoever it
  // names, and the ONE case that is wrong for is the origin the capture itself crawled. A rule that
  // rebased more than that would break every real outbound link on the replica.
  const html = '<a href="https://partner.test/x">p</a><a href="//de.x.test/">de</a>'
    + '<a href="mailto:hi@x.test">m</a>';

  assert.equal(rebaseOwnOrigin(html, "https://x.test"), html);
});

test("SH586: the substitution is exact — a path, a query and a fragment all survive it", () => {
  assert.equal(
    rebaseOwnOrigin('<a href="https://x.test/a/b?p=2#top">x</a>', "https://x.test"),
    '<a href="/a/b?p=2#top">x</a>',
  );
  // srcset is comma-and-space separated, so the terminator set has to include both or the
  // descriptor would be swallowed into the path.
  assert.equal(
    rebaseOwnOrigin('<img srcset="https://x.test/a.png 1x, /b.png 2x">', "https://x.test"),
    '<img srcset="/a.png 1x, /b.png 2x">',
  );
});

test("SH586: no origin and a malformed one leave the markup untouched", () => {
  // A capture from before this, replayed: `planMarkup` is called with no origin by every existing
  // caller in a test, and a cache that came back empty would be worse than one that came back old.
  const html = '<a href="https://x.test/a">a</a>';
  assert.equal(rebaseOwnOrigin(html, null), html);
  assert.equal(rebaseOwnOrigin(html, "not a url"), html);
  assert.equal(rebaseOwnOrigin(null, "https://x.test"), null);
});


test("SH565: a <title> is decoded as it is captured, on both conversion paths", () => {
  // Measured before the fix: `scanPage` yielded the five characters `&amp;` while `decodeEntities`
  // on the same string yielded `&`. The decoder was right and nothing called it here — a `<title>`
  // is RCDATA, which SH476 (text becoming a field) and SH550 (attribute values) both routed around.
  assert.equal(
    scanPage("<html><head><title>Northwind &amp; Co</title></head><body></body></html>",
      "https://x.test/").title,
    "Northwind & Co",
    "the title does not travel through `propose`, so this was wrong on the fidelity path too — "
      + "where SH476's defect was authorable only",
  );
});

test("SH565: one pass, so a doubly-escaped title keeps the entity its author wrote", () => {
  // The `awkward` archetype's own title. `&amp;amp;` is an author escaping an ampersand-entity on
  // purpose: decoding once yields the literal text `&amp;`, and a second pass would decode that to
  // `&` and lose the distinction. `decodeEntities` is single-pass for exactly this, and the fix
  // must not smuggle in a second one.
  assert.equal(
    scanPage("<html><head><title>Awkward &amp;amp; company</title></head><body></body></html>",
      "https://x.test/").title,
    "Awkward &amp; company",
  );
});

test("SH565: a title with no entity is untouched, and an absent one stays null", () => {
  assert.equal(
    scanPage("<html><head><title>Plain title</title></head><body></body></html>", "https://x.test/").title,
    "Plain title",
  );
  assert.equal(
    scanPage("<html><head></head><body></body></html>", "https://x.test/").title,
    null,
    "no <title> is a fact about the page, and an empty string would read as one that has a blank",
  );
});


// --- SH570: markup a browser repairs, and a scanner does not -------------------------------------

test("SH570: an unclosed block does not swallow what follows it", () => {
  // Every fixture before this was typed by somebody who closed their tags, so `tokenize` had never
  // met an unclosed `<div>`. A browser repairs one silently; a scanner that treated the rest of the
  // document as inside it would give every later block the wrong extent.
  const doc = scanPage(
    "<html><head><title>T</title></head><body><main>"
      + "<section><h1>First</h1><p>One"
      + "<div><h2>Second</h2><p>Two"
      + "</main></body></html>",
    "https://x.test/",
  );

  assert.equal(doc.title, "T");
  assert.ok(doc.textChars > 0, "the text after the unclosed tag is still read, not discarded");
});

test("SH570: a page with no <title> records null, not an empty string", () => {
  // `Page` requires a title and SH544 proved it load-bearing. `null` says the source had none;
  // `""` would read as a source that had a blank one, and only the first is true.
  assert.equal(
    scanPage("<html><head></head><body><main><h1>No title</h1></main></body></html>",
      "https://x.test/").title,
    null,
  );
});

test("SH570: an absurdly long title is captured whole, and stays the source's problem", () => {
  // SH544 as a fixture rather than a story: 265 characters ended a 201-page conversion after 403
  // assets had been uploaded. The scanner's job is to report what the source served — truncating
  // here would hide the very fact the conversion needs to refuse on.
  const long = "x".repeat(265);
  assert.equal(
    scanPage(`<html><head><title>${long}</title></head><body></body></html>`, "https://x.test/").title,
    long,
    "a scanner that shortened it would make the defect invisible to the phase that can act on it",
  );
});

test("SH570: a nested form is not counted as two", () => {
  // HTML forbids it and a browser drops the inner one. Counting both would tell a conversion there
  // are two forms to model where a reader sees one.
  const doc = scanPage(
    "<html><head><title>T</title></head><body>"
      + "<form action=\"/a\"><input name=\"x\">"
      + "<form action=\"/b\"><input name=\"y\"></form>"
      + "</form></body></html>",
    "https://x.test/",
  );

  assert.equal(doc.forms.length, 1,
    "a browser keeps the outer form and discards the inner; two rows would model a page nobody sees");
});
