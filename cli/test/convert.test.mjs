/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
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
  SECTIONS_FOLDER, THEME_FOLDER, buildConversion, withResolvedFileFields,
} from "../src/convert.mjs";
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
