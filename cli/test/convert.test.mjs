/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH417 — `shio convert`, the verb the pipeline was one short of.
 *
 * SH379 stopped at the proposal on purpose: `--accept` records a review decision and applies nothing,
 * because review and mutation in one command is the surprise P7 forbids. That left the plan a file
 * whose only reader was the report that produced it. This is the other half, and what is worth pinning
 * is not the translation — a section's fields are already in the proposal — but the three things a
 * write owes: it refuses a **stale** acceptance, it lands in **DRAFT**, and it refuses when the target
 * cannot hold what it carries (SH436).
 *
 * Network-free: the desired-state document is built by a pure function, and the command's own IO is a
 * scripted `fetch`.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import {
  CAPTURED_STYLE_HTML, CAPTURED_THEME_URL, LAYOUT_ROLES, SECTIONS_FOLDER, THEME_FOLDER,
  VOCABULARY_CLASSES, WRAPPER_ROLES, buildConversion, describeConversion, withResolvedFileFields,
} from "../src/convert.mjs";
import {
  VOCABULARY_THEME_CSS, VOCABULARY_THEME_TOKENS, vocabularyThemeTitle,
} from "../src/vocabulary-theme.mjs";
import * as convert from "../src/commands/convert.mjs";
import { INVENTORY_FILE, MARKUP_DIR, markupFileFor } from "../src/clone.mjs";
import { PLAN_FILE } from "../src/propose.mjs";
import { norm } from "./support/paths.mjs";

// --- the translation ---

const candidate = (id, extra = {}) => ({
  id,
  type: "SectionHero",
  mode: "authorable",
  fields: { heading: "Home", body: "<p>Words.</p>" },
  pages: ["/"],
  ...extra,
});

test("SH417: an accepted candidate becomes one section post, in DRAFT", () => {
  // One post per candidate, not per page: a candidate IS a signature, so the hero that recurs on
  // twelve pages is one section reused — which is the whole reason the proposal groups by structure.
  const { document, sections } = buildConversion({
    proposal: { candidates: [candidate("b1a2"), candidate("c3d4", { mode: "fidelity" })] },
    plan: { accepted: [{ block: "b1a2", type: "SectionHero" }] },
    site: "Acme",
  });

  assert.equal(sections.length, 1, "the unaccepted candidate stays verbatim markup");
  assert.deepEqual(document.posts[0], {
    url: `${SECTIONS_FOLDER}/b1a2`,
    type: "SectionHero",
    folder: SECTIONS_FOLDER,
    data: { heading: "Home", body: "<p>Words.</p>" },
  });
  assert.ok(
    !("publish" in document.posts[0]),
    "absent, not false: absent means leave the publish state alone, so re-running cannot unpublish a "
      + "page a curator has since published — and P7's draft-default is why a human is in this loop",
  );
  assert.deepEqual(document.folders, [{ path: SECTIONS_FOLDER }]);
});

test("SH417: a stale acceptance refuses the whole document rather than writing part of it", () => {
  // The rule the structural id exists to make checkable. An acceptance from a run taken before the
  // source changed names a block that is not there now, and writing anything for it would apply a
  // decision nobody made about this markup.
  const { document, stale } = buildConversion({
    proposal: { candidates: [candidate("b1a2")] },
    plan: { accepted: [{ block: "b1a2", type: "SectionHero" }, { block: "gone99", type: "SectionCta" }] },
    site: "Acme",
  });

  assert.deepEqual(stale, ["gone99"]);
  assert.equal(document, null, "all or nothing: a half-applied conversion is the worst outcome here");
});

test("SH417: the theme and the scripts ride the same document, with the render vocabulary's names", () => {
  const { document, extras } = buildConversion({
    proposal: {
      candidates: [candidate("b1a2")],
      theme: { rows: [{ path: "brand", source: "declared" }], css: ":root{}", tokens: { brand: { $value: "#0b5533" } } },
      scripts: [{ src: "https://x.test/a.js", position: "head" }],
    },
    plan: { accepted: [{ block: "b1a2", type: "SectionHero" }] },
    site: "Acme",
  });

  assert.deepEqual(extras, ["Theme", "SiteScripts"]);
  const theme = document.posts.find((post) => post.type === "Theme");
  assert.equal(theme.folder, THEME_FOLDER);
  assert.deepEqual(Object.keys(theme.data).sort(), ["CSS", "TITLE", "TOKENS"],
    "uppercase: a section's fields are the blueprint's vocabulary and a Theme's are the renderer's");
  assert.deepEqual(JSON.parse(theme.data.TOKENS), { brand: { $value: "#0b5533" } },
    "the TOKENS field is the DTCG tree ShThemeTokens.parse reads, serialized");
  const scripts = document.posts.find((post) => post.type === "SiteScripts");
  assert.deepEqual(JSON.parse(scripts.data.SCRIPTS)[0].src, "https://x.test/a.js");
});

test("SH417: a proposal with no theme declares no theme folder", () => {
  const { document, extras } = buildConversion({
    proposal: { candidates: [candidate("b1a2")] },
    plan: { accepted: [{ block: "b1a2", type: "SectionHero" }] },
    site: "Acme",
  });

  assert.deepEqual(extras, []);
  assert.deepEqual(document.folders, [{ path: SECTIONS_FOLDER }]);
});

// --- SH471: a File field names the post that holds the bytes ---

test("SH471: a section's File fields point at the uploaded post, and nothing else moves", () => {
  const ids = new Map([
    ["/img/hero.png", "file-hero"],
    ["/img/acme.svg", "file-acme"],
    ["/img/face.jpg", "file-face"],
  ]);
  const document = {
    site: "Acme",
    posts: [
      { url: "/sections/a", type: "SectionHero", data: { heading: "Hi", image: "/img/hero.png" } },
      {
        url: "/sections/b",
        type: "SectionLogoWall",
        // The case that rules out matching any string equal to an uploaded path: this row carries
        // an `image` and an `href`, and a logo linking to its own artwork would have had its LINK
        // turned into a post id.
        data: { logos: [{ name: "Acme", image: "/img/acme.svg", href: "/img/acme.svg" }] },
      },
      {
        url: "/sections/c",
        type: "SectionTestimonial",
        data: { quotes: [{ quote: "Good", avatar: "/img/face.jpg" }] },
      },
      { url: "/", type: "Page", data: { content: '<img src="/img/hero.png">' } },
    ],
  };

  const { document: out, resolved, unresolved } = withResolvedFileFields(document, ids);

  assert.equal(out.posts[0].data.image, "file-hero");
  assert.equal(out.posts[1].data.logos[0].image, "file-acme");
  assert.equal(out.posts[1].data.logos[0].href, "/img/acme.svg",
    "only the fields the model declares as File — an href is a link, whatever it points at");
  assert.equal(out.posts[2].data.quotes[0].avatar, "file-face");
  assert.equal(out.posts[3].data.content, '<img src="/img/hero.png">',
    "SH429's option A is untouched: a fidelity page's markup is verbatim, and `ShPreviewLinks` "
      + "points that src at the site's copy at render time. An edited one is a difference "
      + "`snapshot --against` structurally cannot see");
  assert.deepEqual(resolved.sort(),
    ["/sections/a.image", "/sections/b.logos[0].image", "/sections/c.quotes[0].avatar"]);
  assert.deepEqual(unresolved, []);
});

test("SH471: a reference the capture never fetched is left as written and counted", () => {
  const { document, resolved, unresolved } = withResolvedFileFields(
    {
      site: "Acme",
      posts: [{
        url: "/sections/a",
        type: "SectionHero",
        data: { image: "https://cdn.other.test/hero.png" },
      }],
    },
    new Map([["/img/hero.png", "file-hero"]]),
  );

  assert.equal(document.posts[0].data.image, "https://cdn.other.test/hero.png",
    "off-origin bytes are SH378's decision, and blanking the field would destroy the evidence "
      + "the capture is for");
  assert.deepEqual(resolved, []);
  assert.deepEqual(unresolved, ["/sections/a.image = https://cdn.other.test/hero.png"]);
});

test("SH471: with no uploads there is nothing to point at, and the document is the same object", () => {
  const document = { site: "Acme", posts: [{ url: "/s", type: "SectionHero", data: { image: "/x.png" } }] };

  assert.equal(withResolvedFileFields(document, new Map()).document, document);
  assert.equal(withResolvedFileFields(document, null).document, document);
});

// --- the command ---

const PAGE = "<html><body><main>"
  + "<section class=hero><h1>Home</h1><p>Welcome to the site.</p><a href=/go>Start</a></section>"
  + "</main></body></html>";

/**
 * @param {object} [options]
 * @param {Record<string, string|null>} [options.assets] source path → bytes, or `null` for an
 *   asset the capture named and never downloaded (SH437's "missing" row)
 * @param {object[]} [options.staticFiles] what the instance already holds in the target folder
 */
function ctxWith({ plan = null, site = { "/": PAGE }, assets = {}, staticFiles = [], fetchImpl } = {}) {
  const files = new Map();
  const assetRows = Object.keys(assets).map((path) => ({
    path,
    file: `x.test${path}`,
    kind: "image",
    contentType: "image/png",
  }));
  files.set(norm(`/repo/${INVENTORY_FILE}`), JSON.stringify({
    version: 3,
    source: { origin: "https://x.test", seed: "https://x.test" },
    crawl: { pages: 1, truncated: false, markup: true },
    pages: Object.keys(site).map((path) => ({ path, title: path, markup: markupFileFor(path) })),
    assets: assetRows,
  }));
  for (const [path, html] of Object.entries(site)) {
    files.set(norm(`/repo/${MARKUP_DIR}/${markupFileFor(path)}`), html);
  }
  for (const [path, bytes] of Object.entries(assets)) {
    if (bytes !== null) files.set(norm(`/repo/shio/assets/x.test${path}`), bytes);
  }
  if (plan) files.set(norm(`/repo/${PLAN_FILE}`), JSON.stringify(plan));
  const written = {};
  const calls = [];
  return {
    env: {},
    cwd: "/repo",
    stdout: sink(),
    stderr: sink(),
    written,
    calls,
    async fetch(url, init = {}) {
      const at = String(url);
      calls.push({ url: at, method: init.method ?? "GET", body: init.body });
      if (fetchImpl) return fetchImpl(at, init);
      // SH437's three reads, in the order `sendAssets` makes them.
      //
      // SH456 — and the resolve refuses a malformed address, the way the instance does. A stub that
      // answers `{ id }` to anything cannot disagree with its caller, so `folder:Acme` (the root
      // form with its slash stripped) read as a resolved folder here and threw `Malformed address`
      // against a server. That is the SH448 shape inside a unit test: the harness decided the
      // answer, so the one boundary it existed to stand in for was the one it could not model.
      // `ShAddress.parse` is the rule — a `folder:` target with no `/` after the site is a 400.
      if (at.includes("/agent/resolve")) {
        const address = decodeURIComponent(new URL(at, "http://x").searchParams.get("address") ?? "");
        if (/^folder:[^/]*$/.test(address)) {
          return json({ error: "Malformed address", detail: `Malformed address '${address}'` }, 400);
        }
        return json({ id: "folder-1", kind: "folder" });
      }
      if (at.includes("/api/v2/staticfile?")) return json(staticFiles);
      if (at.includes("/api/v2/staticfile/upload")) return json({ id: "file-1" });
      return json({ changed: 3 });
    },
    fs: {
      async mkdir() {},
      async writeFile(path, body) {
        written[norm(path)] = body;
        files.set(norm(path), body);
      },
      async readFile(path) {
        const body = files.get(norm(path));
        if (body === undefined) {
          const error = new Error(`ENOENT: ${path}`);
          error.code = "ENOENT";
          throw error;
        }
        return body;
      },
    },
  };
}

const json = (body, status = 200) => ({
  ok: status >= 200 && status < 300,
  status,
  headers: { get: () => "application/json" },
  async json() {
    return body;
  },
  async text() {
    return JSON.stringify(body);
  },
});

function sink() {
  const out = { text: "" };
  out.write = (chunk) => {
    out.text += chunk;
  };
  return out;
}

/** The id the current markup produces, read the way a reviewer would. */
async function idOf(ctx) {
  const { proposalFor } = await import("../src/commands/propose.mjs");
  const { proposal } = await proposalFor({}, ctx, "propose");
  return proposal.candidates[0].id;
}

test("SH417: --out writes the document and applies nothing", async () => {
  const probe = ctxWith();
  const id = await idOf(probe);
  const ctx = ctxWith({ plan: { accepted: [{ block: id, type: "SectionHero" }] } });

  const { applied } = await convert.run({ site: "Acme", out: "conversion.json" }, [], ctx);

  assert.equal(applied, false);
  assert.deepEqual(ctx.calls, [], "no instance was touched, which is what makes this reviewable");
  const document = JSON.parse(ctx.written[norm("/repo/conversion.json")]);
  assert.equal(document.site, "Acme");
  assert.equal(document.posts[0].type, "SectionHero");
  assert.match(ctx.stdout.text, /every post lands in DRAFT/);
});

test("SH417: --dry-run plans and writes nothing; a bare run applies", async () => {
  const probe = ctxWith();
  const id = await idOf(probe);
  const plan = { accepted: [{ block: id, type: "SectionHero" }] };

  const dry = ctxWith({ plan });
  await convert.run({ site: "Acme", dryRun: true }, [], dry);
  assert.ok(dry.calls.every((call) => call.url.includes("/agent/plan")), "plan, never apply");
  assert.match(dry.stdout.text, /nothing was written/);

  const wet = ctxWith({ plan });
  await convert.run({ site: "Acme" }, [], wet);
  assert.ok(wet.calls.some((call) => call.url.includes("/agent/apply")));
  assert.match(wet.stdout.text, /in DRAFT/);
});

test("SH449: nothing accepted writes the page verbatim, which is what fidelity mode always meant", async () => {
  // SH417 refused here, and SH438 wrote no Page for a wholly-unaccepted capture — so the two
  // documented modes were one mode and a label. Measured before this: convert emitted the Theme
  // alone, the site 404'd at every captured path, and `verify --checks replication` reported
  // `source-page-missing` for all of them. Accepting nothing is a decision, not an omission.
  const ctx = ctxWith();

  await convert.run({ site: "Acme", out: "conversion.json" }, [], ctx);
  const document = JSON.parse(ctx.written[norm("/repo/conversion.json")]);

  const page = document.posts.find((post) => post.type === "Page");
  assert.equal(page.url, "/", "SH385: the replica keeps the source's URL space");
  assert.ok(
    !("sections" in page.data),
    "nothing was accepted, so there is no section to compose — the markup is the content",
  );
  assert.match(page.data.content, /<section class=hero>/,
    "the source's own markup, and its class attributes with it");
  assert.ok(
    !/<body/i.test(page.data.content),
    "the body's *inner* HTML: the layout owns the document, so a second <body> here would nest one",
  );

  const layout = document.posts.find((post) => post.type === "PageLayout");
  assert.match(layout.data.HTML, /\{\{\{content\}\}\}/,
    "the shell interpolates the page's markup — triple-stash, or the replica renders as escaped text");
  assert.match(layout.data.HTML, /<style>\{\{theme\.css\}\}<\/style>/,
    "and inlines the captured theme, which is the one thing a replica must not take from the source");
  assert.deepEqual(
    document.postTypeLayout,
    { Page: "Acme (fidelity)" },
    "SH328/SH449: the binding rides the document. A layout nobody bound renders nothing, and this "
      + "is the one edge no post.upsert can express — without it --out would be un-appliable",
  );
  assert.match(ctx.stdout.text, /1 of 1 page\(s\) in fidelity mode/);
});

test("SH449: a capture with no markup is refused by the read, not by the writer", async () => {
  // What replaced SH417's "nothing has been accepted". That refusal asked the wrong question — an
  // unaccepted block is now written — and the state it was standing in for is a capture with no
  // markup, which `proposalFor` already refuses one step earlier and with the better sentence,
  // because it knows the origin and can print the exact `clone` to re-run. The writer's own
  // "nothing to write" survives as the residue for a capture that has styles and no pages.
  const ctx = ctxWith({ site: {} });

  await assert.rejects(
    () => convert.run({ site: "Acme" }, [], ctx),
    /neither their markup[\s\S]*shio clone https:\/\/x\.test --markup/,
  );
  assert.deepEqual(ctx.calls, []);
});

test("SH437: a fidelity conversion uploads the captured bytes at the source's own path", async () => {
  // SH145's rule is that an asset reaches the instance because a File *field* names it, and a page
  // kept in fidelity mode has no fields at all — which is what `source-asset-stranded` has been
  // reporting since SH429. The reference is in the markup, at the source's path, so the upload has
  // to use that path as the folder chain plus file name: the pair the renderer walks back.
  const withImage = { "/": PAGE.replace("<main>", '<main><img src="/img/hero.png" alt="Hero">') };
  const ctx = ctxWith({ site: withImage, assets: { "/img/hero.png": "PNG" } });

  const { assets } = await convert.run({ site: "Acme" }, [], ctx);

  assert.deepEqual(
    { uploaded: assets.uploaded, present: assets.present, missing: assets.missing },
    { uploaded: 1, present: 0, missing: 0 });
  const folder = ctx.calls.find((call) => call.url.includes("/agent/apply") && /"\/img"/.test(call.body ?? ""));
  assert.ok(folder, "the folder chain the source implied is created through the one write path (P10)");
  const upload = ctx.calls.find((call) => call.url.includes("/api/v2/staticfile/upload"));
  assert.ok(upload, "and the bytes go up the endpoint the console uses");

  // The markup is untouched — SH429's option A, and the reason it needed a route rather than a
  // rewrite: `snapshot --against` compares a replica to its source, and an edited `src` is a
  // difference the comparison structurally cannot see.
  const out = ctxWith({ site: withImage, assets: { "/img/hero.png": "PNG" } });
  const page = (await convert.run({ site: "Acme", out: "c.json" }, [], out))
    .document.posts.find((post) => post.type === "Page");
  assert.match(page.data.content, /src="\/img\/hero\.png"/);
});

test("SH437: a second conversion uploads nothing, and a missing byte is counted rather than raised", async () => {
  // Idempotent, because a second `convert` must not re-upload a site's images; and an asset the
  // capture named but never downloaded is `source-asset-missing`, which the gate already reports
  // with the `clone` that fixes it — failing a whole conversion over an image would be worse.
  const already = ctxWith({
    assets: { "/img/hero.png": "PNG" },
    staticFiles: [{ id: "file-already", fileName: "hero.png" }],
  });
  const second = (await convert.run({ site: "Acme" }, [], already)).assets;
  assert.deepEqual({ uploaded: second.uploaded, present: second.present, missing: second.missing },
    { uploaded: 0, present: 1, missing: 0 });
  assert.deepEqual(
    [...second.ids],
    [["/img/hero.png", "file-already"]],
    "SH471: the re-run path records the id too. It is the case an agent hits most, and a map that "
      + "only filled on a first upload would leave every second conversion holding paths",
  );

  const gone = ctxWith({ assets: { "/img/hero.png": null } });
  const missing = (await convert.run({ site: "Acme" }, [], gone)).assets;
  assert.deepEqual({ uploaded: missing.uploaded, present: missing.present, missing: missing.missing },
    { uploaded: 0, present: 0, missing: 1 });
});

test("SH456: an asset at the root of the URL space gets an address the resolver accepts", async () => {
  // `/favicon.ico` has no folder segment, so its folder is `/` — and the branch written for that
  // case was the only one that produced an address the resolver rejects. Measured:
  // `shio convert --site Publico --create-site` printed `0 section(s) in 1 page(s) + Theme +
  // PageLayout`, `changed: 7 object(s) — in DRAFT`, and then failed with `Malformed address
  // 'folder:Publico'` and exited **1**. The three posts were on the instance: the work succeeded
  // and the command said it did not.
  //
  // It is the main path, not an edge: a single-page capture converts into the site's root folder,
  // so every fidelity conversion of a home page hit it. And the exit code is the damage — `convert`
  // is idempotent, so a caller who trusts the 1 re-runs it and sees the same 1 forever.
  const withIcon = { "/": PAGE.replace("<main>", '<main><link rel="icon" href="/favicon.ico">') };
  const ctx = ctxWith({ site: withIcon, assets: { "/favicon.ico": "ICO" } });

  const { assets } = await convert.run({ site: "Acme" }, [], ctx);

  assert.deepEqual(
    { uploaded: assets.uploaded, present: assets.present, missing: assets.missing },
    { uploaded: 1, present: 0, missing: 0 });
  const resolve = ctx.calls.find((call) => call.url.includes("/agent/resolve"));
  assert.match(
    decodeURIComponent(resolve.url),
    /address=folder:Acme\/(&|$)/,
    "`folder:<site>/` — the root form, which is the one the grammar spells and the one the refusal "
      + "itself printed. Stripping the slash leaves `folder:Acme`, a target with no path at all",
  );
});

test("SH449: an accepted page composes sections and carries no verbatim copy of itself", async () => {
  // The two modes are per page, and a page that composes must not also ship the markup it replaced:
  // that would render the block twice and make the acceptance invisible in the output.
  const probe = ctxWith();
  const id = await idOf(probe);
  const ctx = ctxWith({ plan: { accepted: [{ block: id, type: "SectionHero" }] } });

  await convert.run({ site: "Acme", out: "conversion.json" }, [], ctx);
  const document = JSON.parse(ctx.written[norm("/repo/conversion.json")]);

  const page = document.posts.find((post) => post.type === "Page");
  assert.deepEqual(page.data.sections, [`${SECTIONS_FOLDER}/${id}`]);
  assert.ok(!("content" in page.data), "composed, so there is nothing verbatim to keep");
  // SH464 inverts what SH449 asserted here, and the inversion is the task. "No layout when nothing
  // is verbatim" was written as a saving and measured as the reason `/preview/<site>/` answered
  // **400 - binds no layout for post-type 'Page'** on every authorable replica: the renderer resolves
  // one layout per post-type, so a Page with no layout is a Page that does not render, and the block
  // named "an existing site becomes an authorable Shio site" shipped neither half of that.
  const layout = document.posts.find((post) => post.type === "PageLayout");
  assert.ok(layout, "an authorable conversion needs a layout too - a Page renders through one");
  assert.match(
    layout.data.HTML,
    /\{\{#getRelation sections\}\}/,
    "and the layout walks Page.sections, which is the half that makes the sections visible",
  );
  assert.match(
    layout.data.HTML,
    /sh-post="\{\{id\}\}"[\s\S]*sh-field="heading"/,
    "SH464: sh-post binds the block to the SECTION post and sh-field is what the renderer turns "
      + "into data-shio-prop - fidelity mode renders with zero of them, so the editor loads over a "
      + "page with nothing to offer",
  );
  assert.equal(document.postTypeLayout.Page, layout.data.TITLE,
    "and the binding rides the document, because a layout nobody bound renders nothing");
  // SH465 — the four collection fields of the SH95 vocabulary, which SH464's union left out. Four of
  // the seven section types are collection-shaped, so they rendered a heading and stopped, and
  // SectionLogoWall — heading and logos, nothing else — rendered as a bare <h2>. `{{#each}}` and not
  // `{{#getRelation}}`: an SH95 Relator declares nested fields, so its value is an array of row
  // objects in the section's own data rather than a list of references to other posts.
  for (const collection of ["features", "questions", "logos", "quotes"]) {
    assert.match(
      layout.data.HTML,
      new RegExp(`\\{\\{#each ${collection}\\}\\}`),
      `SH465: the layout draws ${collection}. Whether it draws them *correctly* is asserted where `
        + "the renderer is — ShConvertedLayoutRenderTest reads these bytes and runs them, and the "
        + "coverage half of it reads the vocabulary rather than a list, so a section type or a field "
        + "the model gains and this template never draws fails there by name",
    );
  }
  // SH503 — `sh-field` declares what an element's *content* is, so one element declared one field,
  // and a link is two: its text and its target. The destination was captured, stored on the section
  // and rendered into the href with no declaration at all, so a curator could rename the button and
  // not repoint it. Which fields still lack one is counted where the renderer is, for the same
  // reason the collections are.
  assert.match(
    layout.data.HTML,
    /sh-field="ctaLabel" sh-field-attr="ctaHref:href"/,
    "SH503: the call to action declares both of its fields - the label it draws as text and the "
      + "destination it draws into the href",
  );
});

/**
 * A page with two blocks, which is the normal page and the one SH520 is about — every real page
 * mixes what the vocabulary matched with what it did not.
 */
const MIXED_PAGE = "<html><body><main>"
  + "<section class=hero><h1>Home</h1><p>Welcome to the site.</p><a href=/go>Start</a></section>"
  + "<section class=notes><h2>Notes</h2><p>Kept exactly as the source wrote it.</p></section>"
  + "</main></body></html>";

/** The page's block ids in source order, read the way a reviewer would. */
async function blocksOfPage(ctx) {
  const { proposalFor } = await import("../src/commands/propose.mjs");
  const { proposal } = await proposalFor({}, ctx, "propose");
  return proposal.pages[0].blocks;
}

/**
 * The two-page fixture SH552 needs: a banner whose text belongs to neither page.
 *
 * <p>The same shape the real capture had — one string carried by candidates that share no page — and
 * it has to be *two* pages, because that is the whole predicate: a value repeated inside one page is
 * a duplication the reviewer can see, and a value on candidates sharing no page is chrome.</p>
 */
const CONSENT = "<h2>Before you continue</h2><p>We respect your privacy and use cookies here.</p>";
const CONSENT_SITE = {
  "/": `<html><body><main><section class=hero><h1>Home</h1><p>Welcome to it.</p>`
    + `<a href=/go>Start</a></section><section class=consent>${CONSENT}</section></main></body></html>`,
  "/about": `<html><body><main><section class=about><h1>About</h1><p>Who we are here.</p>`
    + `<a href=/x>Go</a></section><div class=consent2>${CONSENT}</div></main></body></html>`,
};

/**
 * SH552 — the seventh time a finding shipped ahead of the behaviour it reports on.
 *
 * <p>SH505 taught `propose` to exclude an overlay and to report any field value carried by candidates
 * that share no page. Both halves ran, and the note named the string exactly — `intro "We respect your
 * privacy"`, across five candidate ids. Then `convert` copied `row.fields` verbatim and the replica's
 * home page introduced itself with a cookie banner. The report was not wrong and the exclusion was not
 * wrong; what was missing is that the second command reads the first one's finding.</p>
 *
 * <p>So this is an end-to-end assertion on purpose. A unit test of the drop would pass with the two
 * commands still disconnected — which is the exact state SH452, SH469 and SH429 shipped in, each time
 * as a sentence somebody had to act on, and the somebody was a command.</p>
 */
test("SH552: a value the proposal marked as chrome is not written into the section", async () => {
  const ctx0 = ctxWith({ site: CONSENT_SITE });
  const { proposalFor } = await import("../src/commands/propose.mjs");
  const { proposal } = await proposalFor({}, ctx0, "propose");

  assert.ok(
    proposal.adopted?.length,
    `propose has to publish the finding as data, not only as prose: ${JSON.stringify(proposal.notes)}`,
  );
  const banner = proposal.adopted.find((row) => row.value.includes("respect your privacy"));
  assert.ok(banner, `the banner's value is one of the rows: ${JSON.stringify(proposal.adopted)}`);
  assert.ok(
    banner.value.length > 40,
    "the full value, never `preview`'s truncation — the consumer matches on it",
  );
  assert.ok(banner.candidates.length >= 2, "with the carriers that made it chrome");

  // Accept the candidate that carries it, which is what the real run did.
  const carrier = proposal.candidates.find((row) => row.id === banner.candidates[0]);
  assert.ok(
    JSON.stringify(carrier.fields).includes("respect your privacy"),
    "the candidate still carries the value in the proposal — `propose` reports and does not edit, so "
      + "without this the assertion below would pass on a fixture that never held the banner",
  );
  const ctx = ctxWith({
    site: CONSENT_SITE,
    plan: { accepted: [{ block: carrier.id, type: "SectionRichText" }] },
  });

  await convert.run({ site: "Acme", out: "conversion.json" }, [], ctx);
  const document = JSON.parse(ctx.written[norm("/repo/conversion.json")]);
  const section = document.posts.find((post) => post.url.startsWith(`${SECTIONS_FOLDER}/`));

  assert.ok(section, "the accepted block is still written as a section");
  assert.ok(
    !JSON.stringify(section.data).includes("respect your privacy"),
    `the chrome value is not in the section's fields: ${JSON.stringify(section.data)}`,
  );
  assert.match(
    ctx.stdout.text,
    /field value\(s\) dropped as chrome/,
    "and the count is printed by the command that performed the cut, not only by the one that found "
      + "it — a drop nobody states reads as a capture that never held the thing",
  );
});

test("SH552: the drop is keyed on the pair, so a section's own field survives", () => {
  // A `heading` that is chrome on one value is ordinary content on another. Dropping the whole field
  // would take the section's own heading with it, which is a second defect wearing the fix's clothes.
  const { document, dropped } = buildConversion({
    proposal: {
      version: 3,
      candidates: [{
        id: "b1a2",
        type: "SectionHero",
        pages: ["/"],
        fields: { heading: "Ship faster than before", intro: "We respect your privacy here" },
      }],
      pages: [{ path: "/", title: "Home", blocks: ["b1a2"] }],
      adopted: [{ field: "intro", value: "We respect your privacy here", candidates: ["b1a2", "c3d4"] }],
    },
    plan: { accepted: [{ block: "b1a2", type: "SectionHero" }] },
    site: "Acme",
  });

  const section = document.posts.find((post) => post.type === "SectionHero");
  assert.equal(section.data.heading, "Ship faster than before", "the section's own field is kept");
  assert.ok(!("intro" in section.data), "and the chrome one is absent rather than empty — an absent "
    + "field renders nothing, and a field holding \"\" is one a curator has to notice and delete");
  assert.deepEqual(dropped, [{ section: "b1a2", field: "intro", value: "We respect your privacy here" }]);
});

test("SH552: a proposal with no adopted rows changes nothing, and prints no line about it", () => {
  const { document, dropped } = buildConversion({
    proposal: {
      version: 3,
      candidates: [{ id: "b1a2", type: "SectionHero", pages: ["/"], fields: { heading: "Ship faster" } }],
      pages: [{ path: "/", title: "Home", blocks: ["b1a2"] }],
    },
    plan: { accepted: [{ block: "b1a2", type: "SectionHero" }] },
    site: "Acme",
  });

  assert.deepEqual(dropped, []);
  assert.equal(document.posts.find((post) => post.type === "SectionHero").data.heading, "Ship faster");
  assert.ok(
    !describeConversion({ document, sections: [], extras: [], dropped }).includes("dropped as chrome"),
    "the report gains nothing on a conversion that dropped nothing (P5: same bytes as before)",
  );
});

test("SH520: accepting one block keeps every other block on the page, verbatim", async () => {
  // The promise `propose` prints in the same run — "an unaccepted block stays verbatim markup" — and
  // the whole reason accepting one block is an incremental decision rather than an all-or-nothing
  // rewrite. Measured on viglet.org before this: accepting the FAQ left a Page holding a 6-byte
  // title and one section where fidelity holds 42,371 bytes, so the hero, the product grid and the
  // footer were gone and a megabyte of captured CSS was still served for markup that did not exist.
  const site = { "/": MIXED_PAGE };
  const [hero, notes] = await blocksOfPage(ctxWith({ site }));
  const ctx = ctxWith({ site, plan: { accepted: [{ block: hero, type: "SectionHero" }] } });

  await convert.run({ site: "Acme", out: "conversion.json" }, [], ctx);
  const document = JSON.parse(ctx.written[norm("/repo/conversion.json")]);
  const page = document.posts.find((post) => post.type === "Page");

  assert.notEqual(hero, notes, "the fixture is two blocks, or this test asserts nothing");
  assert.deepEqual(page.data.sections, [`${SECTIONS_FOLDER}/${hero}`],
    "the accepted block is a section, exactly as before");
  assert.equal(
    page.data.content,
    "<main><section class=notes><h2>Notes</h2><p>Kept exactly as the source wrote it.</p></section></main>",
    "and the block nobody accepted is still on the page, as the source's own bytes with the source's "
      + "own class attribute — the remainder, cut out of the captured file rather than re-serialized",
  );
  assert.ok(
    !page.data.content.includes("Welcome to the site."),
    "the accepted one is gone from the markup: a page that ships both renders it twice, and the "
      + "acceptance becomes invisible in the output",
  );
  assert.match(ctx.stdout.text, /1 of 1 page\(s\) mixed/,
    "and the reviewer is told, in the run that writes it, that the rest of the page was kept");
});

test("SH549: a mixed page gets the vocabulary theme, and its verbatim half is not what decides", async () => {
  // SH520 asserted the opposite here, and SH510's reasoning was why: the half of a mixed page nobody
  // accepted carries the source's class names, so naming the vocabulary theme would leave that half
  // as unstyled text. True — and it made the *other* half unstyled instead, for every page of the
  // site, which is what SH549 measured. This fixture has no captured stylesheet at all, so there is
  // nothing the verbatim half loses and everything the authored half gains.
  const site = { "/": MIXED_PAGE };
  const [hero] = await blocksOfPage(ctxWith({ site }));
  const ctx = ctxWith({ site, plan: { accepted: [{ block: hero, type: "SectionHero" }] } });

  await convert.run({ site: "Acme", out: "conversion.json" }, [], ctx);
  const document = JSON.parse(ctx.written[norm("/repo/conversion.json")]);
  const layout = document.posts.find((post) => post.type === "PageLayout");

  assert.equal(
    layout.data.THEME,
    vocabularyThemeTitle("Acme"),
    "the page composes a section, so it draws shio-* names and needs the theme that defines them",
  );
  assert.ok(
    document.posts.some((post) => post.data?.TITLE === vocabularyThemeTitle("Acme")),
    "and the theme post is written, because the layout names it",
  );
  assert.ok(
    !layout.data.HTML.includes(CAPTURED_REFERENCE),
    "no captured block on a source with no stylesheet: the reference would resolve to nothing, and "
      + "a template naming a post the document does not hold is SH297's marked failure",
  );
});

// --- SH508: the layout contract a theme lays out with ---

/**
 * Every start tag in the layout, with its classes — enough to ask what an element *declares*.
 *
 * A regex rather than a parser: the questions below are all "does this tag carry that attribute",
 * the template is a sibling file's own bytes, and no attribute value in it contains a `>`. A closing
 * tag never matches, because `</` is not `<[a-z]`.
 */
function layoutElements(html) {
  return [...html.matchAll(/<([a-z][a-z0-9]*)\b([^>]*)>/g)].map(([, tag, attrs]) => ({
    tag,
    attrs,
    classes: (/class="([^"]*)"/.exec(attrs)?.[1] ?? "").split(/\s+/).filter(Boolean),
  }));
}

test("SH508: the layout carries a structural contract, named by role and owned by nobody", () => {
  // SH507 decided a converted page is presented by a Shio theme. This is the tree that theme lays
  // out: the layout emitted `section > h2 > ul > li` and eight names, with no container to bound a
  // measure, no stack to space siblings and no grid to put cards beside each other.
  const { document } = buildConversion({
    proposal: {
      candidates: [candidate("b1a2")],
      pages: [{ path: "/", title: "Home", blocks: ["b1a2"] }],
    },
    plan: { accepted: [{ block: "b1a2", type: "SectionHero" }] },
    site: "Acme",
  });
  const html = document.posts.find((post) => post.type === "PageLayout").data.HTML;
  const elements = layoutElements(html);
  const used = new Set(elements.flatMap((element) => element.classes));

  assert.deepEqual(
    LAYOUT_ROLES.filter((role) => !used.has(role)), [],
    "every role the contract declares has to be somewhere in the layout - a role a theme can style "
      + "and the template never emits is a rule that matches nothing",
  );
  const body = elements.find((element) => element.tag === "body");
  assert.ok(
    body?.classes.includes("shio-page"),
    "the rhythm *between* sections needs no wrapper at all: the sections are body's own children, "
      + "and body is the one element both modes already have",
  );
  const section = elements.find((element) => element.classes.includes("shio-section"));
  assert.match(
    section.attrs, /data-shio-columns="\{\{columns\}\}"/,
    "the grid's column count is SectionFeatureGrid.columns and not a role: a theme reads "
      + "[data-shio-columns=\"3\"] .shio-grid, so the contract has both ends and the template writes "
      + "the number once",
  );

  // The two properties SH185 imposes, and the reason the rule names three roles rather than eight.
  const wrappers = elements.filter((element) =>
    element.classes.some((name) => WRAPPER_ROLES.includes(name)));
  assert.ok(
    wrappers.length >= 4,
    "a filter that matched nothing would make both rules below pass over nothing",
  );
  for (const wrapper of wrappers) {
    assert.ok(
      !/\bsh-post=/.test(wrapper.attrs),
      `<${wrapper.tag} class="${wrapper.classes.join(" ")}"> declares an owner. Ownership resolves `
        + "to the nearest annotated ancestor, so a wrapper that declared one would silently reassign "
        + "every field under it to the wrong post",
    );
    assert.ok(
      !/\bsh-field(-attr)?=/.test(wrapper.attrs),
      `<${wrapper.tag} class="${wrapper.classes.join(" ")}"> is editable. A wrapper is presentation, `
        + "and a curator has nothing to edit in it",
    );
  }
  assert.ok(
    elements.some((element) =>
      element.classes.some((name) => LAYOUT_ROLES.includes(name) && !WRAPPER_ROLES.includes(name))
      && /\bsh-field(-attr)?=/.test(element.attrs)),
    "and the premise of that scoping: a role outside WRAPPER_ROLES does land on an element that "
      + "declares a field - a feature row is a card and draws its icon into an attribute - so a rule "
      + "written over all eight would be false rather than strict",
  );

  assert.deepEqual(
    [...used].filter((name) => name.includes("Section")), [],
    "no class names a type at all (SH556): the per-type hook is data-shio-section, which is on the "
      + "same element, so a class spelling it too would make the theme a list of types and break on "
      + "the eighth one that ships",
  );
});

test("SH556: the layout draws no class the vocabulary does not declare", () => {
  // The instrument, and the direction nobody was checking. SH509's test asserts every *role* is
  // styled; nothing asserted the converse, so the templates grew sixteen names the theme never had
  // — `shio-section-<Type>`, `shio-features`, `shio-feature`, `shio-feature-title`,
  // `shio-quote-avatar` and the rest — and SH521's lint reported the gap on all eleven authorable
  // pages of a converted site, accurately, every run. Reading the layout constant rather than a
  // rendered page is what makes this total: every `{{#if}}` branch is in the string whether or not
  // a fixture has that field, so a class only a testimonial draws is still counted.
  const { document } = buildConversion({
    proposal: {
      candidates: [candidate("b1a2")],
      pages: [{ path: "/", title: "Home", blocks: ["b1a2"] }],
    },
    plan: { accepted: [{ block: "b1a2", type: "SectionHero" }] },
    site: "Acme",
  });
  const html = document.posts.find((post) => post.type === "PageLayout").data.HTML;
  const drawn = new Set(layoutElements(html).flatMap((element) => element.classes));

  assert.ok(drawn.size > 0, "a scanner that found nothing would make this pass over nothing");
  assert.deepEqual(
    [...drawn].filter((name) => !VOCABULARY_CLASSES.includes(name)).sort(), [],
    "a class the templates draw and the vocabulary does not declare is `page-unstyled` on every "
      + "page that renders it — the lint's premise is provable and it was right",
  );
  assert.deepEqual(
    VOCABULARY_CLASSES.filter((name) => !VOCABULARY_THEME_CSS.includes(`.${name}`)), [],
    "and the other end: a declared class the theme never styles is the same gap one file over",
  );
});

test("SH556: what the removed classes addressed is still addressable", () => {
  // The objection that made the choice look hard — "emit only roles and you lose the per-type hook
  // a curator's own CSS would want" — and the fact that answers it. Nothing was lost, because the
  // hook was already on the same element: the template was spelling it twice, which is exactly what
  // the `data-shio-columns` paragraph in convert.mjs forbids.
  const { document } = buildConversion({
    proposal: {
      candidates: [candidate("b1a2")],
      pages: [{ path: "/", title: "Home", blocks: ["b1a2"] }],
    },
    plan: { accepted: [{ block: "b1a2", type: "SectionHero" }] },
    site: "Acme",
  });
  const html = document.posts.find((post) => post.type === "PageLayout").data.HTML;

  assert.match(html, /<section class="shio-section"[^>]*data-shio-section="\{\{type\}\}"/,
    "`[data-shio-section=\"SectionHero\"]` is what `.shio-section-SectionHero` was");
  for (const row of ["features", "questions", "logos", "quotes"]) {
    assert.ok(html.includes(`data-shio-row="${row}"`),
      `\`[data-shio-row="${row}"]\` is what \`.shio-${row}\` was, and it reaches the leaves too: `
        + `\`[data-shio-row="${row}"] h3\` is the title the class named`);
  }
});

// --- SH509: the theme the vocabulary never had ---

/** The DTCG tree flattened the way `ShThemeTokens` flattens it: `color.ink` → `color-ink`. */
function declaredCustomProperties(tree, prefix = "", into = new Set()) {
  for (const [name, node] of Object.entries(tree)) {
    if (name.startsWith("$") || node === null || typeof node !== "object") continue;
    const path = prefix ? `${prefix}.${name}` : name;
    if ("$value" in node) into.add(path.replaceAll(/[^A-Za-z0-9-]+/g, "-"));
    else declaredCustomProperties(node, path, into);
  }
  return into;
}

test("SH509: an authorable conversion carries the theme it is presented with, as content", () => {
  const { document, extras } = buildConversion({
    proposal: {
      candidates: [candidate("b1a2")],
      pages: [{ path: "/", title: "Home", blocks: ["b1a2"] }],
    },
    plan: { accepted: [{ block: "b1a2", type: "SectionHero" }] },
    site: "Acme",
  });

  assert.ok(extras.includes("Theme (Shio)"), "and the report says so, because a post nobody expected "
    + "is the kind of write P7 makes a run announce");
  const theme = document.posts.find((post) => post.data.TITLE === vocabularyThemeTitle("Acme"));
  assert.ok(theme, "SH507 decided the mode presents with a Shio theme; this is the theme");
  assert.equal(theme.type, "Theme");
  assert.deepEqual(Object.keys(theme.data).sort(), ["CSS", "TITLE", "TOKENS"],
    "a Theme post and not a file: a stylesheet in the jar would be the one part of a converted site "
      + "nobody can change, which is the shape SH183 rejected when it made the render model content");
  assert.deepEqual(JSON.parse(theme.data.TOKENS), VOCABULARY_THEME_TOKENS,
    "and the tokens ride it, so changing a colour is a field edit rather than a stylesheet rewrite");
});

test("SH509: a fidelity conversion carries no theme it would not use", () => {
  // The roles are SH508's, and a verbatim page has none of them. Writing it anyway would be a post
  // no page renders — and on the one path that already reaches 0.00% against its source.
  const { document, extras } = buildConversion({
    proposal: {
      candidates: [candidate("b1a2", { mode: "fidelity" })],
      pages: [{ path: "/", title: "Home", blocks: ["b1a2"] }],
    },
    plan: {},
    site: "Acme",
    markup: new Map([["/", "<html><body><p>Words.</p></body></html>"]]),
  });

  assert.ok(!extras.includes("Theme (Shio)"));
  assert.equal(document.posts.filter((post) => post.type === "Theme").length, 0);
});

test("SH509: the theme styles every role SH508 declares, and assumes no section type", () => {
  // The contract has two ends and this is the assertion that they meet. A role the layout emits and
  // the theme never styles is a wrapper that does nothing; a rule for a role the layout never emits
  // is a rule that matches nothing.
  assert.deepEqual(
    LAYOUT_ROLES.filter((role) => !VOCABULARY_THEME_CSS.includes(`.${role}`)), [],
    "every role in the contract is styled - a container that bounds nothing is a byte of markup with "
      + "no reader",
  );
  assert.ok(
    !VOCABULARY_THEME_CSS.includes("Section"),
    "and none of it names a section type: SH508's names are roles, and a theme that styled "
      + ".shio-section-SectionHero specially would break the eighth type that ships",
  );

  // A `var()` naming a property nothing declares is the same defect one level down, and it fails the
  // way an unstyled page fails - silently, with the browser dropping the declaration.
  const declared = declaredCustomProperties(VOCABULARY_THEME_TOKENS);
  const used = new Set([...VOCABULARY_THEME_CSS.matchAll(/var\(--([A-Za-z0-9-]+)\)/g)]
    .map(([, name]) => name));
  assert.ok(used.size > 0, "a scanner that found nothing would make both directions pass over nothing");
  assert.deepEqual(
    [...used].filter((name) => !declared.has(name)).sort(), [],
    "every custom property the stylesheet reads is a token the theme declares - ShThemeTokens "
      + "compiles color.ink into --color-ink and prepends the :root block, so an undeclared one is a "
      + "declaration the browser drops",
  );
  assert.deepEqual(
    [...declared].filter((name) => !used.has(name)).sort(), [],
    "and the other way: a token nothing reads is a field a curator can edit to no effect, which is "
      + "worse than not offering it",
  );
});

// --- SH510: one stylesheet on the page, and it is the one that can match ---

/** ~1.04 MB, the order of magnitude the task was filed on rather than a token stand-in. */
const CAPTURED_CSS = ".a{color:red}".repeat(80_000);

const withCapturedTheme = (extra = {}) => ({
  candidates: [candidate("b1a2"), candidate("c3d4")],
  theme: { rows: [{ path: "brand", source: "declared" }], css: CAPTURED_CSS, tokens: {} },
  ...extra,
});

test("SH510: an authorable conversion is presented by the theme it can apply", () => {
  // The measurement SH507 was filed on: 1,062,141 of an authorable page's 1,066,436 bytes were a
  // captured stylesheet whose ~2,894 rules met 8 shio-* names. So the assertion is a ratio, not a
  // claim about a name.
  const { document } = buildConversion({
    proposal: withCapturedTheme({ pages: [{ path: "/", title: "Home", blocks: ["b1a2"] }] }),
    plan: { accepted: [{ block: "b1a2", type: "SectionHero" }] },
    site: "Acme",
  });

  const layout = document.posts.find((post) => post.type === "PageLayout");
  assert.equal(layout.data.THEME, vocabularyThemeTitle("Acme"));
  const named = document.posts.find((post) => post.data.TITLE === layout.data.THEME);
  assert.ok(
    named.data.CSS.length < CAPTURED_CSS.length / 100,
    `the page carries ${named.data.CSS.length} bytes of stylesheet instead of ${CAPTURED_CSS.length}`
      + " - a megabyte on every page of a converted site, served with max-age=300, for rules that"
      + " match nothing",
  );
  assert.equal(
    document.posts.find((post) => post.data.TITLE === "Acme (captured)").data.CSS, CAPTURED_CSS,
    "and the capture keeps every byte: fidelity inlines it and reaches 0.00% with it, so the same "
      + "site converted the other way needs all of them",
  );
  assert.equal(
    layout.data.HTML.split("{{theme.css}}").length - 1, 1,
    "one handle, not two. Two stylesheets on one page would make the cascade depend on which came "
      + "first, which is the accident SH424 spent a task removing - reintroduced by inclusion",
  );
  assert.equal(layout.data.HTML.split("<style>").length - 1, 1);
});

/** The per-page block SH549 puts in the layout's head — read from the module, never restated. */
const CAPTURED_REFERENCE = CAPTURED_STYLE_HTML;

test("SH549: the block's hardcoded URL is the one the captured theme post is written at", () => {
  // `CAPTURED_STYLE_HTML` interpolates nothing, because the JVM half of this seam reads it as a
  // string (SH464). That is worth exactly one assertion: a literal that drifted from
  // `CAPTURED_THEME_URL` would name a post the document does not hold, which renders as SH297's
  // `data-shio-theme-missing` and styles nothing.
  assert.ok(
    CAPTURED_STYLE_HTML.includes(`"${CAPTURED_THEME_URL}"`),
    `${CAPTURED_STYLE_HTML} must reference ${CAPTURED_THEME_URL}`,
  );
});

/**
 * SH549 — the one block the vocabulary cannot name, and the eleven pages it used to unstyle.
 *
 * <p>This is SH510's own fixture with the assertion turned around, and the turn is the task. Under
 * `authorable = verbatim === 0` one page left verbatim named the captured theme for the **whole
 * site**: measured on viglet.org, twelve pages each carrying 1,062,146 characters of CSS defining
 * none of the `shio-*` classes the section templates draw, with `page-unstyled` on ten of them. A
 * real site always has a block the vocabulary cannot name — this was the first attempt on the first
 * site — so that was not the rare case.</p>
 *
 * <p>Both claims now hold, which is why this is not the trade SH510 refused: the layout names the
 * vocabulary theme, and the captured CSS reaches the verbatim page through a **per-page** condition
 * on the page's own `content`. By reference, so the captured `Theme` post is still the single copy
 * and SH424's token edits still reach it.</p>
 */
test("SH549: one verbatim page no longer decides the theme for the whole site", () => {
  const { document, verbatim } = buildConversion({
    proposal: withCapturedTheme({
      pages: [
        { path: "/", title: "Home", blocks: ["b1a2"] },
        { path: "/about", title: "About", blocks: ["c3d4"] },
      ],
    }),
    plan: { accepted: [{ block: "b1a2", type: "SectionHero" }] },
    site: "Acme",
    markup: new Map([["/about", "<html><body><p>Words.</p></body></html>"]]),
  });

  assert.equal(verbatim, 1);
  const layout = document.posts.find((post) => post.type === "PageLayout");
  assert.equal(
    layout.data.THEME,
    vocabularyThemeTitle("Acme"),
    "the authorable page draws shio-* names, and eleven of twelve pages losing their styling to one "
      + "that did not is the regression this task is",
  );
  assert.ok(
    document.posts.some((post) => post.data?.TITLE === vocabularyThemeTitle("Acme")),
    "so the vocabulary theme post is in the document",
  );
  assert.ok(
    document.posts.some((post) => post.data?.TITLE === "Acme (captured)"),
    "and the captured one still is too — nothing about the capture's bytes changed",
  );
  assert.ok(
    layout.data.HTML.includes(CAPTURED_REFERENCE),
    "the verbatim page still gets the source's CSS, guarded by its own `content` — so an authorable "
      + "page receives exactly one stylesheet and is byte-identical to what SH510 produced",
  );
  assert.ok(
    layout.data.HTML.indexOf("{{theme.css}}") < layout.data.HTML.indexOf("getRelation"),
    "captured second, so the source's rules win the collisions a reset would otherwise decide "
      + "against the page's own markup",
  );
});

test("SH549: a fidelity conversion never emits the captured block twice", () => {
  const { document, verbatim } = buildConversion({
    proposal: withCapturedTheme({ pages: [{ path: "/", title: "Home", blocks: ["b1a2"] }] }),
    plan: {},
    site: "Acme",
    markup: new Map([["/", "<html><body><p>Words.</p></body></html>"]]),
  });

  assert.equal(verbatim, 1);
  const layout = document.posts.find((post) => post.type === "PageLayout");
  assert.equal(layout.data.THEME, "Acme (captured)", "unchanged: nothing composes sections");
  assert.ok(
    !layout.data.HTML.includes(CAPTURED_REFERENCE),
    "and the layout already names it, so a second reference would put a megabyte on the page twice",
  );
});

test("SH510: a fidelity conversion is untouched, and one with no theme at all names none", () => {
  const fidelity = buildConversion({
    proposal: withCapturedTheme({ pages: [{ path: "/", title: "Home", blocks: ["b1a2"] }] }),
    plan: {},
    site: "Acme",
    markup: new Map([["/", "<html><body><p>Words.</p></body></html>"]]),
  });
  assert.equal(
    fidelity.document.posts.find((post) => post.type === "PageLayout").data.THEME, "Acme (captured)");

  const bare = buildConversion({
    proposal: { candidates: [candidate("b1a2")], pages: [{ path: "/", title: "Home", blocks: ["b1a2"] }] },
    plan: {},
    site: "Acme",
    markup: new Map([["/", "<html><body><p>Words.</p></body></html>"]]),
  });
  assert.ok(
    !("THEME" in bare.document.posts.find((post) => post.type === "PageLayout").data),
    "a THEME naming a post the site does not have is a marked failure at render time, so a document "
      + "with no theme in it names none",
  );
});

test("SH417: no --site is refused before anything is read", async () => {
  const ctx = ctxWith();

  await assert.rejects(() => convert.run({}, [], ctx), /pass the site to write into/);
});

test("SH436: a target missing Theme.TOKENS is refused with the reconcile call, before the write", async () => {
  // The precondition SH436 put in the artefact, checked at the one moment there is an instance to ask.
  // A theme written into a Theme with no TOKENS either fails somewhere unhelpful or writes the theme
  // without its tokens, after a capture, a conversion and a review have all been paid for.
  const withStyles = {
    "/": PAGE.replace("<body>", "<body><style>:root{--brand:#0b5533}</style>"),
  };
  const probe = ctxWith({ site: withStyles });
  const id = await idOf(probe);
  const ctx = ctxWith({
    site: withStyles,
    plan: { accepted: [{ block: id, type: "SectionHero" }] },
    fetchImpl: (url) => (url.includes("/render/post-types/drift")
      ? json([{ postType: "Theme", missingFields: ["TOKENS"] }])
      : json({ changed: 1 })),
  });

  await assert.rejects(
    () => convert.run({ site: "Acme" }, [], ctx),
    /missing Theme\.TOKENS[\s\S]*render\/post-types\/reconcile/,
  );
  assert.ok(
    !ctx.calls.some((call) => call.url.includes("/agent/apply")),
    "and it refused BEFORE writing, which is the whole point of asking first",
  );
});

test("SH436: a sound instance is not asked twice, and a dry run does not ask at all", async () => {
  const withStyles = { "/": PAGE.replace("<body>", "<body><style>:root{--brand:#0b5533}</style>") };
  const probe = ctxWith({ site: withStyles });
  const id = await idOf(probe);
  const plan = { accepted: [{ block: id, type: "SectionHero" }] };

  const sound = ctxWith({
    site: withStyles,
    plan,
    fetchImpl: (url) => (url.includes("drift") ? json([]) : json({ changed: 2 })),
  });
  await convert.run({ site: "Acme" }, [], sound);
  assert.equal(sound.calls.filter((call) => call.url.includes("drift")).length, 1);

  const dry = ctxWith({ site: withStyles, plan });
  await convert.run({ site: "Acme", dryRun: true }, [], dry);
  assert.ok(
    !dry.calls.some((call) => call.url.includes("drift")),
    "a dry run writes nothing, so a precondition on writing has nothing to guard",
  );
});

// --- SH439: a refusal that names a remedy this surface can express ---
//
// `shio convert --site Viglet2` against an absent site printed the plan and then refused with the
// write resolver's fix: "add a `site.upsert` op for it earlier in the same batch." Un-followable
// here — `convert` composes its own document and takes no ops — and it landed at the worst moment,
// because converting into a **new** site is the normal shape of a replication. P6 makes an
// instruction naming something that does not exist worse than none. So the flag, and the message.

test("SH439: --create-site rides the document, and its absence leaves the document as it was", () => {
  const proposal = { candidates: [candidate("b1a2")] };
  const plan = { accepted: [{ block: "b1a2", type: "SectionHero" }] };

  const asked = buildConversion({ proposal, plan, site: "Acme", createSite: true });
  assert.equal(asked.document.createSite, true);

  const not = buildConversion({ proposal, plan, site: "Acme" });
  assert.ok(
    !("createSite" in not.document),
    "absent rather than false: every document written before SH439 is the same bytes, and creating "
      + "a site nobody asked for is the surprise P7 forbids (P5)",
  );
});

test("SH439: `convert --create-site` sends the flag, and `--out` writes it into the file", async () => {
  const probe = ctxWith();
  const id = await idOf(probe);
  const plan = { accepted: [{ block: id, type: "SectionHero" }] };

  const wet = ctxWith({ plan });
  await convert.run({ site: "Acme", createSite: true }, [], wet);
  const sent = JSON.parse(wet.calls.find((call) => call.url.includes("/agent/apply")).body);
  assert.equal(sent.createSite, true, "the flag is the document's, so plan and apply agree by construction");

  const out = ctxWith({ plan });
  await convert.run({ site: "Acme", createSite: true, out: "conversion.json" }, [], out);
  const document = JSON.parse(out.written[norm("/repo/conversion.json")]);
  assert.equal(
    document.createSite,
    true,
    "an --out document must mean what the run meant, or `shio apply conversion.json` is a different act",
  );
});

test("SH439: an absent site is refused with --create-site, not with an op convert cannot take", async () => {
  const probe = ctxWith();
  const id = await idOf(probe);
  const ctx = ctxWith({
    plan: { accepted: [{ block: id, type: "SectionHero" }] },
    // The write resolver's own body, verbatim in shape: correct on the batch surface, and the
    // reason this task exists on this one.
    fetchImpl: (url) => (url.includes("/agent/apply") || url.includes("/agent/plan")
      ? json({
        type: "https://shio.viglet.com/errors/unknown-site",
        title: "No readable site 'Viglet2'",
        detail: "No site with that id or name is visible to this credential.",
        fix: "Address a site that exists, or add a `site.upsert` op for it earlier in the same batch.",
        example: "{\"op\":\"site.upsert\",\"address\":\"site:Viglet2\"}",
      }, 404)
      : json([])),
  });

  const failure = await convert.run({ site: "Viglet2" }, [], ctx).then(() => null, (error) => error);

  assert.ok(failure, "an absent site is still a refusal — the flag is the remedy, not the default");
  const problem = JSON.parse(failure.body);
  assert.match(problem.fix, /--create-site/);
  assert.ok(
    !problem.fix.includes("site.upsert"),
    "the op is what this surface cannot express; naming it is the defect, not the message's length",
  );
  assert.equal(problem.example, "shio convert --site Viglet2 --create-site");
  assert.equal(
    problem.title,
    "No readable site 'Viglet2'",
    "and the resolver's own title, detail and didYouMean stay: they are about the site, not the surface",
  );
});

// --- SH438: a section no page composes is not a replica ---
//
// SH417's scope was honest — sections, a theme, scripts — and a section nothing orders is not
// deliverable by anything. Measured on a real run: three posts created, `/preview/viglet2/` answered
// 404, and `verify --checks replication` reported `source-page-missing` for `/`. The replica of a
// one-page site ended as two orphans, and the step that turns them into a site was the one step
// still done by hand — the manual work this phase existed to remove, moved up from block to page.

/** A proposal that also records what each page is made of, which is what SH438 added to it. */
const composed = (pages, candidates) => ({ candidates, pages });

test("SH438: each captured page becomes a Page composing its sections, in source order", () => {
  const { document, pages } = buildConversion({
    proposal: composed(
      [{ path: "/", title: "Home", blocks: ["hero1", "feat2", "cta3"] }],
      [candidate("hero1"), candidate("feat2"), candidate("cta3")],
    ),
    // Accepted in an order that is neither the source's nor the sorted one, so the assertion below
    // can only pass by reading the page's own block order.
    plan: { accepted: [{ block: "cta3" }, { block: "hero1" }, { block: "feat2" }].map(
      (entry) => ({ ...entry, type: "SectionHero" })) },
    site: "Acme",
  });

  assert.equal(pages.length, 1);
  assert.deepEqual(pages[0], {
    url: "/",
    type: "Page",
    folder: "/",
    data: {
      title: "Home",
      sections: [
        `${SECTIONS_FOLDER}/hero1`,
        `${SECTIONS_FOLDER}/feat2`,
        `${SECTIONS_FOLDER}/cta3`,
      ],
    },
  });
  assert.ok(
    !("publish" in pages[0]),
    "DRAFT like every other row: a page appearing published because a converter ran is the surprise "
      + "P7 forbids",
  );
  assert.deepEqual(
    document.folders,
    [{ path: SECTIONS_FOLDER }, { path: "/" }, { path: "/theme" }],
    "the site root is declared, because that is where a page lives — and since SH464 /theme is "
      + "too, because every conversion with a page now carries the layout that renders it",
  );
});

test("SH438: the home page's URL is the source's `/`, which is what post:<site>/ names", () => {
  // SH385's rule — a replica keeps the original's URL space — and SH433's form for the one path the
  // grammar does not spell. A page at any other URL is a replica that 404s where the source served.
  const { pages } = buildConversion({
    proposal: composed(
      [{ path: "/", title: "Home", blocks: ["hero1"] }, { path: "/pricing", title: "Pricing", blocks: ["hero1"] }],
      [candidate("hero1")],
    ),
    plan: { accepted: [{ block: "hero1", type: "SectionHero" }] },
    site: "Acme",
  });

  assert.deepEqual(pages.map((page) => page.url), ["/", "/pricing"]);
  assert.ok(
    pages.every((page) => page.data.sections.length === 1),
    "a block accepted on several pages belongs to each of them — one section post, composed twice",
  );
});

test("SH438: only accepted blocks reach the array, and a page composing none gets no Page", () => {
  const { pages } = buildConversion({
    proposal: composed(
      [
        { path: "/", title: "Home", blocks: ["hero1", "verbatim9"] },
        { path: "/legal", title: "Legal", blocks: ["verbatim9"] },
      ],
      [candidate("hero1"), candidate("verbatim9")],
    ),
    plan: { accepted: [{ block: "hero1", type: "SectionHero" }] },
    site: "Acme",
  });

  assert.deepEqual(pages.map((page) => page.url), ["/"]);
  assert.deepEqual(pages[0].data.sections, [`${SECTIONS_FOLDER}/hero1`]);
  // A candidate left in fidelity mode stays verbatim markup, so it is not a section and cannot be
  // in the array. A page made only of those composes nothing, and an empty Page would report success
  // for a page that serves nothing — this same defect one level down, which is why the gate's
  // `source-page-missing` is right to keep asking about /legal.
});

test("SH438: an untitled page falls back to its path, because Page.title is required", () => {
  const { pages } = buildConversion({
    proposal: composed([{ path: "/quiet", title: "  ", blocks: ["hero1"] }], [candidate("hero1")]),
    plan: { accepted: [{ block: "hero1", type: "SectionHero" }] },
    site: "Acme",
  });

  assert.equal(pages[0].data.title, "/quiet", "the path, not the site name: two untitled pages must "
    + "still be two rows a human can tell apart");
});

test("SH438: a repeated signature on one page is listed once", () => {
  // One post, one set of fields, so listing it twice asks the page to render the same content twice.
  // That is a decision for the reviewer, not something to infer from a structural collision.
  const { pages } = buildConversion({
    proposal: composed([{ path: "/", title: "Home", blocks: ["cta1", "hero2", "cta1"] }],
      [candidate("cta1"), candidate("hero2")]),
    plan: { accepted: [{ block: "cta1", type: "SectionCta" }, { block: "hero2", type: "SectionHero" }] },
    site: "Acme",
  });

  assert.deepEqual(pages[0].data.sections, [`${SECTIONS_FOLDER}/cta1`, `${SECTIONS_FOLDER}/hero2`]);
});

test("SH520: a proposal from before the spans keeps its sections rather than duplicating them", () => {
  // The shape a proposal file already on disk has. Without a span there is no way to cut the
  // accepted block out, and writing the body anyway would render it twice — once as markup and once
  // as the section it became. So the remainder is refused, which is exactly what the previous
  // release wrote; re-running `shio propose` is what produces it, and that is a cheaper thing to say
  // than a page whose hero appears twice.
  const { pages } = buildConversion({
    proposal: composed([{ path: "/", title: "Home", blocks: ["hero1", "kept2"] }],
      [candidate("hero1"), candidate("kept2")]),
    plan: { accepted: [{ block: "hero1", type: "SectionHero" }] },
    site: "Acme",
    markup: new Map([["/", "<html><body><main><section>one</section><section>two</section></main></body></html>"]]),
  });

  assert.deepEqual(pages[0].data.sections, [`${SECTIONS_FOLDER}/hero1`]);
  assert.ok(!("content" in pages[0].data), "no span, no remainder — and no second copy of the hero");
});

test("SH438: a proposal from before this carried no pages, and still converts", () => {
  // The pre-SH438 shape, which is what a proposal file already on disk looks like. It composes
  // nothing rather than throwing: re-running `shio propose` is what produces the page order, and a
  // crash would be a worse way to say so.
  const { document, pages } = buildConversion({
    proposal: { candidates: [candidate("hero1")] },
    plan: { accepted: [{ block: "hero1", type: "SectionHero" }] },
    site: "Acme",
  });

  assert.deepEqual(pages, []);
  assert.deepEqual(document.posts.map((post) => post.url), [`${SECTIONS_FOLDER}/hero1`]);
});

/*
 * SH575 — a site of pages with no way between them.
 *
 * An authorable conversion keeps what the vocabulary matched and drops the rest, which is the
 * design; the source's navigation lives in the rest, and nothing put one back. Measured on
 * viglet.org converted with `--accept all`: `shio diff` reported `internal links: 16 → 0` on all
 * twelve pages, `audit` reported `no-landmark`, and `verify` exited 0. Twelve pages, none of which
 * you can leave, and every gate green.
 */

/** The one `{{#unless content}}` region the nav has to be inside, and the reason it must be. */
function unlessContent(html) {
  const open = "{{#unless content}}";
  const start = html.indexOf(open);
  assert.ok(start >= 0, "the layout has no `{{#unless content}}` block at all");
  const end = html.indexOf("{{/unless}}", start);
  assert.ok(end > start, "unterminated `{{#unless content}}`");
  return html.slice(start + open.length, end);
}

function layoutOf() {
  const { document } = buildConversion({
    proposal: {
      candidates: [candidate("b1a2")],
      pages: [{ path: "/", title: "Home", blocks: ["b1a2"] }],
    },
    plan: { accepted: [{ block: "b1a2", type: "SectionHero" }] },
    site: "Acme",
  });
  return document.posts.find((post) => post.type === "PageLayout").data.HTML;
}

test("SH575: the generated layout emits a navigation, over the site's own pages", () => {
  const html = layoutOf();

  const nav = /<nav class="shio-nav"[^>]*>([\s\S]*?)<\/nav>/.exec(html);
  assert.ok(nav, `the layout carries no <nav class="shio-nav">. HTML: ${html.slice(0, 400)}`);
  // `{{#query}}` and not `{{#navigation}}`, which is the choice XVII.80 left open and the corpus
  // settled: `{{#navigation}}` yields the site's top-level FOLDERS, and a conversion writes its
  // pages into the root (PAGES_FOLDER is "/") with `sections` and `theme` beside them. A nav built
  // from it would list "sections" and "theme" — and link to neither, since a folder's row `url` is
  // its index post and these have none.
  assert.match(nav[1], /\{\{#query type="Page"/,
    "the rows are the site's Page posts, which is what a reader means by the site's navigation");
  assert.match(nav[1], /href="\{\{link\}\}"/,
    "`link` is the post's friendly URL — the address a delivered front end would use, not a "
      + "preview path, so the markup the preview proves is the markup the site serves");
  assert.match(nav[1], /sort="title"/,
    "alphabetical and not `newest`: a menu whose items move when somebody edits a page is a "
      + "control people misclick — the same argument `{{#translations}}` makes for ordering by "
      + "locale rather than by date");
  assert.match(nav[0], /aria-label="Site"/,
    "and the landmark `audit` reported missing, named — two navs on one page is the case an "
      + "accessible name exists for");
});

test("SH575: and only on a page with no verbatim markup, or fidelity would carry two navs", () => {
  const html = layoutOf();

  assert.match(
    unlessContent(html),
    /<nav class="shio-nav"/,
    "SH549's precedent, and the reason this is not a site-wide switch: `content` holds the source's "
      + "own markup, so `{{#unless content}}` is the per-page condition that separates a page which "
      + "LOST its navigation from one that still has the source's. A fidelity replica is judged at "
      + "0.00% against its source (`snapshot --against`), and a Shio nav above the source's own "
      + "would put that bar out of reach for a mode that had nothing wrong with it.",
  );

  const nav = /<nav class="shio-nav"[^>]*>[\s\S]*?<\/nav>/.exec(html)[0];
  assert.ok(
    !/\bsh-post=/.test(nav) && !/\bsh-field(-attr)?=/.test(nav),
    "the nav declares no owner and nothing editable: it is derived from the site's own tree, so a "
      + "curator edits it by moving or renaming a page, and an `sh-field` here would offer a text "
      + "box that writes to nothing",
  );
});

test("SH575: the class it draws is in the vocabulary and the theme styles it", () => {
  // The two directions SH556's instrument already checks, asserted here by name because this is the
  // task that added a role: a class the layout draws and the theme does not style is `page-unstyled`
  // on every page, which is precisely the finding SH521's lint reported on all eleven pages of a
  // converted site.
  assert.ok(VOCABULARY_CLASSES.includes("shio-nav"));
  assert.match(VOCABULARY_THEME_CSS, /\.shio-nav\{/);
  assert.match(VOCABULARY_THEME_CSS, /\.shio-nav a\{/,
    "the links are styled by element, the way `.shio-card h3` is — a second class per leaf would be "
      + "two more names for the theme to define and the diff to carry");
});
