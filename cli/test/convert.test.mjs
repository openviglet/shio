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
// @scans prose reason: SH763's check reads the shipped vocabulary catalogue, which is JSON — so it
// is `JSON.parse` rather than a match, and the subject is a `fields[].name` the parser produced.
// Declared `prose` rather than `code` because the shared stripper is a JavaScript comment stripper
// and there is nothing here for it to strip: JSON carries no comments, so the confusion `code`
// exists to prevent — a sentence about a field read as a field — cannot arise in this subject.
import assert from "node:assert/strict";
import { readFileSync, readdirSync } from "node:fs";
import { test } from "node:test";

import {
  CAPTURED_STYLE_HTML, CAPTURED_THEME_URL, LAYOUT_ROLES, SECTIONS_FOLDER, THEME_FOLDER,
  MENU_TITLE, PAGE_BODY_HTML,
  VOCABULARY_CLASSES, WRAPPER_ROLES, buildConversion, describeConversion, pageBodyFor,
  sectionFieldsOf, withResolvedFileFields,
} from "../src/convert.mjs";
import {
  VOCABULARY_THEME_CSS, VOCABULARY_THEME_TOKENS, vocabularyThemeTitle,
} from "../src/vocabulary-theme.mjs";
import * as convert from "../src/commands/convert.mjs";
import {
  INVENTORY_FILE, MARKUP_DIR, markupFileFor, parseAttrs, stripNonMarkup, tokenize,
} from "../src/clone.mjs";
import {
  PLAN_FILE, SECTION_REQUIRED, SECTION_TYPES, buildProposal as proposeBuild, missingRequired,
} from "../src/propose.mjs";
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
  assert.deepEqual(
    document.postTypes,
    ["SectionHero"],
    "SH607: the type that section is written as, declared so the run installs it. There is no `Page` "
      + "here because this proposal composes none, which is the same rule the folders follow — a "
      + "document declares what it writes and nothing else (P5)",
  );
});

test("SH857: a proposal that promises an empty row is not written as one", () => {
  // A proposal is a file, and this one was written before the guard in `propose` existed — which is
  // the state every replica already converted is in, and re-running `convert` is what a curator
  // looking at two empty cards will reach for. The measured post, verbatim.
  const rows = (features) => candidate("b1a2", {
    type: "SectionFeatureGrid",
    fields: { heading: "Ask a question, get an answer", features },
  });
  const built = buildConversion({
    proposal: {
      candidates: [
        rows([{}, {}]),
        { ...rows([{ title: "Fast" }, {}, { title: "Cheap", body: "Really" }]), id: "c3d4" },
      ],
    },
    plan: {
      accepted: [
        { block: "b1a2", type: "SectionFeatureGrid" },
        { block: "c3d4", type: "SectionFeatureGrid" },
      ],
    },
    site: "Acme",
  });
  const data = Object.fromEntries(built.document.posts.map((post) => [post.url, post.data]));

  assert.deepEqual(data[`${SECTIONS_FOLDER}/b1a2`], { heading: "Ask a question, get an answer" },
    "the layout draws one `<li class=\"shio-card\">` per row, so two empty rows are two grey "
      + "rectangles under a heading");
  assert.deepEqual(data[`${SECTIONS_FOLDER}/c3d4`].features,
    [{ title: "Fast" }, { title: "Cheap", body: "Really" }],
    "and the drop is per row: a collection with one empty row among three keeps the other two. It "
      + "renumbers them, which is exactly why this happens where the array is WRITTEN — filtering at "
      + "render would renumber against the stored array instead, and `sh-field=\"features.N.title\"` "
      + "resolves into `data.features[N]`, so a curator editing the third card would write the second");
  assert.deepEqual(built.emptied, [
    { section: "b1a2", field: "features", rows: 2, type: "SectionFeatureGrid" },
    { section: "c3d4", field: "features", rows: 1, type: "SectionFeatureGrid" },
  ]);
});

test("SH857: the rows it refused to write are in the report, not only in the return", () => {
  const built = buildConversion({
    proposal: {
      candidates: [candidate("b1a2", {
        type: "SectionFeatureGrid",
        fields: { heading: "Ask a question, get an answer", features: [{}, {}] },
      })],
    },
    plan: { accepted: [{ block: "b1a2", type: "SectionFeatureGrid" }] },
    site: "Acme",
  });
  const report = describeConversion({ ...built, requires: [] }, {});

  assert.match(report, /2 collection row\(s\) not written/,
    "SH849's rule, and this is the fourth outcome of the same pass: a field the builder returns "
      + "that no call site names reaches no reader at all");
  assert.match(report, /b1a2\.features \(SectionFeatureGrid\) — 2 row\(s\)/);
});

/**
 * SH858's measured shape: `www.viglet.org`'s consent banner, which sits at the bottom of every page in
 * `<div aria-live="polite" class="fixed inset-x-0 bottom-0 z-50 …">` with Decline and Accept buttons
 * and a close cross, and which the replica served painted, fixed, and inert.
 */
const BANNER = '<div aria-live="polite" class="fixed inset-x-0 bottom-0 z-50">'
  + "<p>We respect your privacy</p><button>Decline</button><button>Accept analytics</button></div>";

const overlayPage = (head = "") => `<html><head><title>Home</title>${head}</head><body><main>`
  + "<section><h1>Shio</h1><p>An agent-native CMS for people who would rather not click.</p></section>"
  + `${BANNER}</main></body></html>`;

/** The proposal `propose` writes for it, so the spans are the ones a real run records. */
const overlayProposal = (html) => proposeBuild({
  inventory: { source: "https://x.test/", pages: [{ path: "/", title: "Home" }] },
  markup: new Map([["/", html]]),
  html: { tokenize, parseAttrs, stripNonMarkup },
});

test("SH858: an overlay on a page that captured no script is cut out of the kept markup", () => {
  const html = overlayPage();
  const proposal = overlayProposal(html);
  const built = buildConversion({
    proposal, plan: { accepted: [] }, site: "Acme", markup: new Map([["/", html]]),
  });
  const page = built.document.posts.find((post) => post.type === "Page");

  assert.deepEqual(proposal.pages[0].overlays?.length, 1,
    "`propose` already refuses it as a candidate — *an overlay is painted over the page rather than "
      + "part of it* — and now records WHERE, because the exclusion left the bytes in the page");
  assert.equal(page.data.content.includes("Accept analytics"), false,
    "measured on the replica: zero handlers in that region on a page with no `<script src>` at all, "
      + "so the banner is a permanent obstruction of the page's own content and every reader meets it");
  assert.ok(page.data.content.includes("agent-native CMS"),
    "and the rest of the page is untouched — this is a cut of one region, not of the chrome");
  assert.deepEqual(built.obstructions, [{ path: "/", overlays: 1 }]);
});

test("SH858: an overlay on a page that captured a script is left exactly as it was", () => {
  const html = overlayPage('<script src="/consent.js"></script>');
  const built = buildConversion({
    proposal: overlayProposal(html),
    plan: { accepted: [] },
    site: "Acme",
    markup: new Map([["/", html]]),
  });
  const page = built.document.posts.find((post) => post.type === "Page");

  assert.ok(page.data.content.includes("Accept analytics"),
    "whether THIS overlay's dismiss handler was captured is not decidable from markup — the code is "
      + "in a file, minified, and the instance does not run a browser. What is decidable is whether "
      + "the page carries any behaviour at all, and dropping content from a page that does would be "
      + "the over-reach. `--scripts` remains how a caller asks for the working one");
  assert.equal(built.obstructions, undefined);
});

test("SH858: the cut is one walk with the section marks, so the interleaving still lands", () => {
  // Both kinds of span address the ORIGINAL offsets, so cutting the overlay in a second pass would
  // leave every section span past it wrong by the size of the hole — "the footer moved a bit".
  const html = "<html><head><title>Home</title></head><body><main>"
    + "<section><h1>Shio</h1><p>An agent-native CMS for people who would rather not click.</p></section>"
    + BANNER
    + "<section><h2>Why</h2><p>Because a console is a poor protocol for an agent to drive.</p></section>"
    + "</main></body></html>";
  const proposal = overlayProposal(html);
  const first = proposal.candidates.find((row) => row.type);
  const built = buildConversion({
    proposal,
    plan: { accepted: [{ block: first.id, type: first.type }] },
    site: "Acme",
    markup: new Map([["/", html]]),
  });
  const page = built.document.posts.find((post) => post.type === "Page");
  const kept = [page.data.content ?? "", ...built.markup.map((post) => post.data.html ?? "")].join("");

  assert.deepEqual(built.obstructions, [{ path: "/", overlays: 1 }]);
  assert.equal(kept.includes("Accept analytics"), false, "the banner is gone from every kept part");
  assert.ok(page.data.sections?.length,
    "and the page still composes what it accepted — the fallback for an unplaceable split is the "
      + "remainder in one part, which would have swallowed the drop as well");
});

/** A page a reviewer accepts nothing on — wholly verbatim, which is what a fidelity round makes. */
const VERBATIM_PAGE = "<html><head><title>Home</title></head><body><main>"
  + "<section><h1>Enterprise Intelligence</h1>"
  + "<p>An agent-native CMS for people who would rather not click at all.</p>"
  + "<a href=\"/products\">Explore Products</a></section>"
  + "<section><h2>Why</h2>"
  + "<p>Because a console is a poor protocol for a coding agent to drive around.</p></section>"
  + "</main></body></html>";

const verbatimConversion = (html = VERBATIM_PAGE) => buildConversion({
  proposal: proposeBuild({
    inventory: { source: "https://x.test/", pages: [{ path: "/", title: "Home" }] },
    markup: new Map([["/", html]]),
    html: { tokenize, parseAttrs, stripNonMarkup },
  }),
  plan: { accepted: [] },
  site: "Acme",
  markup: new Map([["/", html]]),
  annotate: true,
});

test("SH868: the mapping propose already computed is stamped onto the markup it kept", () => {
  // Spent one way today — read the values out, write them into a new post, render that post through
  // a Shio template — and everything hard about replication follows from the last step. Spent the
  // other way, the markup is the source's and the fields are still there.
  const built = verbatimConversion();
  const page = built.document.posts.find((post) => post.type === "Page");

  assert.match(page.data.content, /<h1 sh-field="[a-z0-9]+Heading">Enterprise Intelligence<\/h1>/,
    "the node `propose` derived `heading` from, annotated in place");
  assert.match(page.data.content, /<a href="\/products" sh-field="[a-z0-9]+CtaLabel">/,
    "and the attribute goes inside the open tag it belongs to, after the ones already there");
  assert.deepEqual(built.annotations, [{ path: "/", fields: 4 }]);
});

test("SH868: strip the annotations and the bytes are the source's, which is what keeps it free", () => {
  // SH864's measurement is that annotating costs nothing in pixels. That holds only if the markup is
  // otherwise untouched — one inserted attribute per annotated element and nothing else moved.
  const built = verbatimConversion();
  const page = built.document.posts.find((post) => post.type === "Page");
  const bare = page.data.content.replace(/ sh-field="[^"]*"/g, "");

  assert.ok(VERBATIM_PAGE.includes(bare.trim()),
    `the kept markup must be the source's own bytes with attributes added:\n${bare}`);
});

test("SH868: each field is named for the block it came from, because one post holds many", () => {
  const built = verbatimConversion();
  const page = built.document.posts.find((post) => post.type === "Page");
  const stamped = built.annotationFields;

  assert.equal(stamped.length, 4);
  assert.equal(new Set(stamped).size, 4,
    "a wholly verbatim page is ONE post holding many blocks, so two heroes both deriving `heading` "
      + "would collide — the name carries the block, which is unique by construction and traceable "
      + "back to the candidate a reviewer read (SH380)");
  // SH872 — the name is in the MARKUP, and the value with it. This used to read the value back off
  // a key on `page.data`, which is the storage that put 44 fields on the type every page shares.
  const subheading = stamped.find((name) => name.endsWith("Subheading"));
  assert.match(page.data.content, new RegExp(`sh-field="${subheading}"`));
  assert.match(page.data.content,
    /An agent-native CMS for people who would rather not click at all\./,
    "and the element's own text is where it always was, so nothing renders differently until it is "
      + "edited — `ShRenderModel` substitutes only where a stored value DIFFERS from the markup "
      + "(SH870), and there is no stored value until a curator makes one");
});

test("SH868: a page that accepted a section keeps exactly what it kept before", () => {
  // The boundary, stated: a mixed page splits its kept markup into `SectionMarkup` runs (SH763) and
  // the fields have to live on the post holding the bytes they annotate, so an offset would first
  // have to be mapped to a run. Not an oversight — the mode is about the wholly verbatim page.
  const proposal = proposeBuild({
    inventory: { source: "https://x.test/", pages: [{ path: "/", title: "Home" }] },
    markup: new Map([["/", VERBATIM_PAGE]]),
    html: { tokenize, parseAttrs, stripNonMarkup },
  });
  const first = proposal.candidates.find((row) => row.type);
  const built = buildConversion({
    proposal,
    plan: { accepted: [{ block: first.id, type: first.type }] },
    site: "Acme",
    markup: new Map([["/", VERBATIM_PAGE]]),
    annotate: true,
  });
  const page = built.document.posts.find((post) => post.type === "Page");

  assert.equal(built.annotations, undefined);
  assert.equal((page.data.content ?? "").includes("sh-field="), false);
});

test("SH868: the third mode is asked for, so plain fidelity is byte-for-byte what it was", () => {
  // `source-corpus.test.mjs` holds that *fidelity is defined by having no editable field*, and it
  // caught this on the run that added it: annotating by default would move the mode boundary
  // silently and falsify what the skill file says about `data-shio-prop`. A third mode is a flag.
  const built = buildConversion({
    proposal: proposeBuild({
      inventory: { source: "https://x.test/", pages: [{ path: "/", title: "Home" }] },
      markup: new Map([["/", VERBATIM_PAGE]]),
      html: { tokenize, parseAttrs, stripNonMarkup },
    }),
    plan: { accepted: [] },
    site: "Acme",
    markup: new Map([["/", VERBATIM_PAGE]]),
  });
  const page = built.document.posts.find((post) => post.type === "Page");

  assert.equal(page.data.content.includes("sh-field="), false);
  assert.equal(built.annotations, undefined);
  assert.deepEqual(Object.keys(page.data).sort(), ["content", "title"],
    "and no invented field: a fidelity page's data is what it always was");
});

test("SH872: an annotated page adds no field to the type every page in the instance shares", () => {
  // The measured defect: `Page` went from 4 fields to 44 on an eleven-page replica, because SH868
  // wrote each annotated block's text into a key on it. `shio annotations post:VigAnn/` then
  // answered `14 of 44 declared field(s) annotated, 30 NOT editable` — 27 of the thirty being
  // other pages' blocks. Thirty findings on a page with nothing wrong with it.
  const built = verbatimConversion();
  const page = built.document.posts.find((post) => post.type === "Page");

  assert.deepEqual(Object.keys(page.data).sort(), ["content", "title"],
    "the page's data is a fidelity page's data — the annotations are in the markup, which is the "
      + "storage SH868 proposed and then did not use");
  assert.equal(built.annotationFields.length, 4);
  assert.equal(built.annotationFields.every((name) => typeof name === "string"), true,
    "names, not `requires` rows: the instance is owed nothing, so a row shaped like a requirement "
      + "would be a requirement that is not one");
  assert.equal("postTypeFields" in built.document, false,
    "and nothing to declare per capture — SH871's key is gone, its op is not");
  assert.match(describeConversion({ ...built, requires: [] }, {}),
    /4 field\(s\) annotated in place on 1 verbatim page\(s\)/);
  assert.match(describeConversion({ ...built, requires: [] }, {}),
    /stored in the markup that carries them \(SH872\) — Page gains nothing and declares nothing/);
});

test("SH872: the annotated value stays exactly where the source put it", () => {
  // The half that makes storing nothing correct rather than lossy: the element's own text is in
  // `content` already, and `ShRenderModel` substitutes only where a stored value DIFFERS from it
  // (SH870). So an unedited annotation storing nothing is the mechanism, not a gap.
  const built = verbatimConversion();
  const page = built.document.posts.find((post) => post.type === "Page");

  for (const name of built.annotationFields) {
    assert.match(page.data.content, new RegExp(`sh-field="${name}"`));
    assert.equal(name in page.data, false);
  }
});

test("SH607: the declaration is sorted and de-duplicated, so one capture converts to one document", () => {
  // P5: two candidates of one type produce one name, and the order is the sort's rather than the
  // plan's. `--out` writes this to disk and a human diffs two runs of it, so a set whose order came
  // from the acceptance order would show a change where the conversion made none.
  const { document } = buildConversion({
    proposal: {
      candidates: [
        candidate("b1a2", { type: "SectionRichText" }),
        candidate("c3d4"),
        candidate("e5f6"),
      ],
    },
    plan: {
      accepted: [
        { block: "e5f6", type: "SectionHero" },
        { block: "b1a2", type: "SectionRichText" },
        { block: "c3d4", type: "SectionHero" },
      ],
    },
    site: "Acme",
  });

  assert.deepEqual(document.postTypes, ["SectionHero", "SectionRichText"]);
});

test("SH607: a type the vocabulary has no definition for is left for the instance to refuse", () => {
  // SH379 turns an unmatched block into a `Block*` post-type of its own, which this catalogue was
  // never going to carry. Naming it here would trade the instance's honest `No post-type named
  // 'BlockPricing'` — which says exactly what to create — for a refusal about a vocabulary the
  // caller never asked for.
  const { document } = buildConversion({
    proposal: { candidates: [candidate("b1a2", { type: "BlockPricing" })] },
    plan: { accepted: [{ block: "b1a2", type: "BlockPricing" }] },
    site: "Acme",
  });

  assert.equal(document.postTypes, undefined);
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

test("SH1135: an applied conversion tells the site where it came from; a dry run does not", async () => {
  const probe = ctxWith();
  const id = await idOf(probe);
  const plan = { accepted: [{ block: id, type: "SectionHero" }] };

  const wet = ctxWith({ plan });
  await convert.run({ site: "Acme" }, [], wet);
  const recorded = wet.calls.filter((call) => call.url.includes("/agent/memory") && call.method === "PUT");
  assert.equal(recorded.length, 1,
    "SH1135: every other artefact of the round stays on this disk, so the instance hears about it here");
  const body = JSON.parse(recorded[0].body);
  assert.equal(body.scope, "Acme", "SH1135: scoped to the site, so it travels with `shio export`");
  assert.equal(body.key, convert.REPLICATION_NOTE_KEY);
  assert.match(body.note, /^Replicated from https:\/\/x\.test on \d{4}-\d{2}-\d{2} by shio convert/);
  assert.match(wet.stdout.text, /recorded: site:Acme memory "replication"/);

  const dry = ctxWith({ plan });
  await convert.run({ site: "Acme", dryRun: true }, [], dry);
  assert.ok(!dry.calls.some((call) => call.url.includes("/agent/memory")),
    "SH1135: a dry run writes nothing, and a record of a round that did not happen is a false one");
});

test("SH1135: the note names the source, the counts, the plan's digest and how to run it again", () => {
  const plan = { accepted: [{ block: "b1" }, { block: "b2" }] };
  const note = convert.replicationNote({
    inventory: { source: { origin: "https://acme.test", seed: "https://acme.test/start" } },
    plan, pages: [{}, {}, {}], verbatim: 1, mixed: 2, sections: [{}, {}], changed: 9,
    when: new Date("2026-09-27T12:00:00Z"),
  });
  assert.match(note, /^Replicated from https:\/\/acme\.test on 2026-09-27 by shio convert/);
  assert.match(note, /3 page\(s\) \(1 verbatim, 2 mixed\), 2 composed section\(s\) from 2 accepted/);
  assert.match(note, /9 object\(s\) written in DRAFT; plan [0-9a-f]{12}\./);
  assert.match(note, /shio clone https:\/\/acme\.test\/start --markup --assets/,
    "SH1135: the record hands the next person the command, which is the console's half as well");
  assert.notEqual(convert.replicationNote({ plan: { accepted: [] } }).match(/plan (\w+)/)[1],
    note.match(/plan (\w+)/)[1], "SH1135: two different plans are two different digests");
  assert.ok(note.length < 2000, "SH1135: under the instance's note limit");
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
  assert.match(layout.data.HTML, /<style data-shio-theme="\{\{theme\.title\}\}">\{\{theme\.css\}\}<\/style>/,
    "and inlines the captured theme, which is the one thing a replica must not take from the source"
      + " — SH829: named by the post it came from, so the digest's region table prints a title and"
      + " not an ordinal the reader has to resolve with a second call");
  assert.deepEqual(
    document.postTypeLayout,
    { Page: "Acme (layout)" },
    "SH328/SH449: the binding rides the document. A layout nobody bound renders nothing, and this "
      + "is the one edge no post.upsert can express — without it --out would be un-appliable",
  );
  // SH573 — the binding and the layout post's TITLE are the same string, which is the whole reason
  // renaming this label is safe. They are written from one call in one document, so a conversion
  // cannot emit a binding naming a title no post has; and because the post is addressed by URL, a
  // re-run of `convert` on a site converted under the old name updates that post and its binding
  // together. Asserted rather than trusted: they are two literals in two places in the output, and
  // a future edit that changed one would produce a site whose every page renders nothing.
  assert.equal(
    layout.data.TITLE,
    document.postTypeLayout[Object.keys(document.postTypeLayout)[0]],
    "SH573: the layout's TITLE and the postTypeLayout binding must be one string — a site whose "
      + "binding names a title no post carries resolves no layout, and every page renders empty",
  );
  assert.doesNotMatch(
    layout.data.TITLE,
    /fidelity|verbatim|authorable|mixed/i,
    "SH573: the title states what the post IS, not which mode it serves. SH464 made one layout "
      + "serve all three, so a mode in this name is decoration shaped like a fact — which is why "
      + "SH559's banner had to quote it rather than print it",
  );

  assert.deepEqual(
    document.postTypes,
    ["Page"],
    "SH607: and the model the binding names, because nothing else was going to create it. Fidelity "
      + "mode accepts nothing and still writes a `Page`, so this is the mode that proves the gap "
      + "was never about sections: a bare instance ships File, PageLayout, Redirect, Region, "
      + "SiteScripts and Theme, and every one of the other types this document writes is one of "
      + "them — which is exactly why the omission was invisible until CI booted an empty database",
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
  // SH830 — read from `PAGE_BODY_HTML` and not from this conversion's layout. The claim is about
  // the *template*: every collection field of the vocabulary is drawn somewhere in it. A conversion
  // now carries the branches for the types it created, and this fixture accepted one hero — so
  // asserting it here would be asserting that the layout draws a field no post on the site has.
  for (const collection of ["features", "questions", "logos", "quotes"]) {
    assert.match(
      PAGE_BODY_HTML,
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
  // SH684 amended the two assertions below, and the reason is worth more than the amendment.
  //
  // This asserted that the section IS written and that the banner is not among its fields. On THIS
  // fixture those two are only satisfiable by a `SectionRichText` whose fields are `{}` — the
  // candidate's one field is the banner — and `body` is a REQUIRED field of that type, so what the
  // green test described was a section `shio verify` rejects with `required-field-empty`. SH684's
  // defect, sitting inside the test of the rule that produced it, green for a block and a half.
  //
  // So the claim is unchanged and the shape it is asserted on is: the banner reaches no *section*,
  // and no empty section is written in its place either.
  //
  // Sections and not every post, deliberately. The banner is still in the page's verbatim `content`
  // and has to be: a declined block stays as markup, which is the promise `propose` prints in the
  // same run. Asserting over every post fails here, and it fails for the behaviour working.
  const sections = document.posts.filter((post) => post.url.startsWith(`${SECTIONS_FOLDER}/`));
  assert.ok(
    !sections.some((post) => JSON.stringify(post.data).includes("respect your privacy")),
    `the banner is nobody's authored content: ${JSON.stringify(sections)}`,
  );
  assert.deepEqual(
    sections,
    [],
    "and no empty section is written in its place — every value this candidate carries is chrome, so "
      + "there is no content left to author and the block stays verbatim (SH684)",
  );
  assert.match(
    ctx.stdout.text,
    /accepted candidate\(s\) declined here/,
    "and the decision is printed by the command that made it, not only by the one that found the "
      + "chrome — an outcome nobody states reads as a capture that never held the thing",
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

/**
 * SH684 — the guard was on one side of the seam.
 *
 * <p>`propose --accept all` declines a candidate whose REQUIRED field the derivation cannot fill, and
 * says why in its own words: *"the section would be content `shio verify` refuses."* Chrome-dropping
 * then emptied that same kind of field on candidates that had already passed the guard, and the two
 * decisions could not see each other — acceptance happens before chrome is known, chrome-dropping
 * happens after acceptance is final.</p>
 *
 * <p>Measured on a full authorable run of `www.viglet.org` at `542142e0`: four blocks declined for a
 * missing `heading`, nine field values dropped as chrome (seven of them `heading`), and then seven
 * `required-field-empty` errors on seven ids — <b>not the four propose declined</b>. Every authorable
 * replication of a site with repeated marketing chrome ended red for a reason no phase reported as
 * its own.</p>
 *
 * <p>The assertion this closes with is the one the task asked for: a conversion where those two
 * counts cannot disagree, stated as the invariant rather than as the number seven.</p>
 */
test("SH684: no section this conversion writes is missing a field its type requires", () => {
  // Both halves of the real shape in one fixture: `heading` is chrome (carried by candidates sharing
  // no page) and `SectionHero` declares it REQUIRED, while `intro` is chrome and is not required.
  const { document, dropped, kept } = buildConversion({
    proposal: {
      version: 3,
      candidates: [
        {
          id: "b1a2",
          type: "SectionHero",
          pages: ["/"],
          // Its own content, plus a repeated masthead heading, plus a consent line. The real shape:
          // a candidate that IS something, whose required field happens to be the chrome one.
          fields: { heading: "Viglet", body: "Home page copy", intro: "We respect your privacy here" },
        },
        {
          id: "c3d4",
          type: "SectionHero",
          pages: ["/about"],
          fields: { heading: "Viglet", body: "About page copy" },
        },
      ],
      pages: [
        { path: "/", title: "Home", blocks: ["b1a2"] },
        { path: "/about", title: "About", blocks: ["c3d4"] },
      ],
      adopted: [
        { field: "heading", value: "Viglet", candidates: ["b1a2", "c3d4"] },
        { field: "intro", value: "We respect your privacy here", candidates: ["b1a2", "e5f6"] },
      ],
    },
    plan: {
      accepted: [{ block: "b1a2", type: "SectionHero" }, { block: "c3d4", type: "SectionHero" }],
    },
    site: "Acme",
  });

  const sections = document.posts.filter((post) => SECTION_REQUIRED[post.type]);
  assert.ok(sections.length, "the fixture writes sections of a type that declares a required field");
  for (const post of sections) {
    assert.deepEqual(
      missingRequired(post.type, post.data),
      [],
      `${post.url} would be content \`shio verify\` refuses (required-field-empty) — which is exactly `
        + "what `propose` declines a candidate for, so the two sides of the seam disagreed",
    );
  }

  // Targeted, not a blanket "keep everything": the same run still drops the chrome that no field
  // requires, or this would be SH552 quietly reverted.
  assert.deepEqual(
    dropped,
    [{ section: "b1a2", field: "intro", value: "We respect your privacy here" }],
    "the non-required chrome value is still cut — SH552's rule is unchanged where it can be obeyed",
  );
  assert.deepEqual(
    kept.map((row) => `${row.section}.${row.field}`),
    ["b1a2.heading", "c3d4.heading"],
    "and the required one is kept on every section that carried it, not just the first",
  );
});

test("SH684: the keep is printed, because a keep nobody states reads as content from the page", () => {
  const { document, kept } = buildConversion({
    proposal: {
      version: 3,
      candidates: [
        {
          id: "b1a2",
          type: "SectionCta",
          pages: ["/"],
          fields: { heading: "Get in touch", body: "Talk to sales about the home offer" },
        },
        {
          id: "c3d4",
          type: "SectionCta",
          pages: ["/about"],
          fields: { heading: "Get in touch", body: "Talk to the team behind it" },
        },
      ],
      pages: [
        { path: "/", title: "Home", blocks: ["b1a2"] },
        { path: "/about", title: "About", blocks: ["c3d4"] },
      ],
      adopted: [{ field: "heading", value: "Get in touch", candidates: ["b1a2", "c3d4"] }],
    },
    plan: {
      accepted: [{ block: "b1a2", type: "SectionCta" }, { block: "c3d4", type: "SectionCta" }],
    },
    site: "Acme",
  });

  const report = describeConversion({ document, sections: [], extras: [], kept });
  assert.match(
    report,
    /chrome value\(s\) kept because their type requires the field/,
    "the keep is a cost and is reported as one — the value IS chrome, and saying so is what lets a "
      + "curator go and correct it rather than discover it",
  );
  assert.match(
    report,
    /b1a2\.heading \(SectionCta\)/,
    "naming the section, the field and the type, so the curator can open the one post that has it",
  );
  assert.match(report, /required-field-empty/, "and why it was not dropped, in the gate's own word");
});

/**
 * SH684 — the bound on the keep, and SH552's own test is what found it.
 *
 * <p>"Keep a chrome value its type requires" is right for a section that <em>is</em> something and
 * merely inherits a repeated heading. Applied unconditionally it is wrong, and wrong in a way this
 * repository has already paid for: a candidate whose only field is the consent text has that text as
 * its REQUIRED `body`, so keeping it writes the cookie banner SH552 removed from the replica's home
 * page. The unconditional version turned SH552's end-to-end test red on the first run, which is the
 * shipped rule defending itself.</p>
 *
 * <p>So a section that would be nothing but chrome is declined and its block stays verbatim — the
 * same answer `propose --accept all` gives one phase earlier, for the same stated reason.</p>
 */
test("SH684: a candidate that is nothing but chrome is declined, not kept", () => {
  const { document, kept, declined } = buildConversion({
    proposal: {
      version: 3,
      candidates: [
        { id: "b1a2", type: "SectionRichText", pages: ["/"], fields: { body: "We respect your privacy" } },
        { id: "c3d4", type: "SectionRichText", pages: ["/about"], fields: { body: "We respect your privacy" } },
      ],
      pages: [
        { path: "/", title: "Home", blocks: ["b1a2"] },
        { path: "/about", title: "About", blocks: ["c3d4"] },
      ],
      adopted: [{ field: "body", value: "We respect your privacy", candidates: ["b1a2", "c3d4"] }],
    },
    plan: {
      accepted: [{ block: "b1a2", type: "SectionRichText" }, { block: "c3d4", type: "SectionRichText" }],
    },
    site: "Acme",
  });

  assert.equal(
    document.posts.filter((post) => post.type === "SectionRichText").length,
    0,
    "no section is written: keeping the required value here would put the consent banner on the "
      + "home page, which is precisely the defect SH552 removed",
  );
  assert.equal(kept, undefined, "and it is not reported as a keep — nothing was kept");
  assert.deepEqual(
    declined.map((row) => `${row.section}:${row.fields.join(",")}`),
    ["b1a2:body", "c3d4:body"],
    "it is the third outcome, named, because an accepted candidate that quietly became verbatim "
      + "markup reads as a block the capture never held",
  );
  assert.match(
    describeConversion({ document, sections: [], extras: [], declined }),
    /accepted candidate\(s\) declined here/,
    "and the command that made the decision is the one that states it",
  );
});

test("SH684: a conversion whose chrome touched no required field says nothing about it", () => {
  // P3, and SH552's own rule one line along: a healthy run pays no bytes for a finding it does not
  // have. Guards the guard too — a report that always prints the line proves nothing by printing it.
  const { document, dropped, kept } = buildConversion({
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

  assert.equal(kept, undefined, "absent rather than empty on the conversion that kept nothing");
  assert.deepEqual(dropped, [{ section: "b1a2", field: "intro", value: "We respect your privacy here" }]);
  assert.ok(
    !describeConversion({ document, sections: [], extras: [], dropped }).includes("kept because"),
    "and the report gains nothing (P5: same bytes as before this task)",
  );
});

/**
 * SH693 — concatenation is not composition.
 *
 * <p>The generated layout emitted `{{{content}}}` whole and then appended every section, so there
 * was no position at which a section could land inside the kept markup. On a mixed page the source's
 * header, nav <b>and footer</b> therefore all preceded every section — by construction, on every
 * such page, in every replication. Measured by offset in the served home page (1,113,507 chars):
 * header at 95%, footer at 98%, then sections #1–#8 to 99%.</p>
 *
 * <p>The information was never missing. `contentFor` already knew where each accepted block stood —
 * it cut those spans out — and then glued the remainder into one string. Splitting instead of gluing
 * is the whole change.</p>
 */
test("SH693/SH763: the source's footer renders below the sections, as a section of its own", async () => {
  // The fixture's own shape plus a footer, and the SECOND block is the one accepted: accepting the
  // first would leave nothing before the sections and the assertion would pass for the wrong reason.
  const site = {
    "/": MIXED_PAGE.replace("</main>", "</main><footer class=bottom><p>Acme</p></footer>"),
  };
  const [, notes] = await blocksOfPage(ctxWith({ site }));
  const ctx = ctxWith({ site, plan: { accepted: [{ block: notes, type: "SectionRichText" }] } });

  await convert.run({ site: "Acme", out: "conversion.json" }, [], ctx);
  const document = JSON.parse(ctx.written[norm("/repo/conversion.json")]);
  const page = document.posts.find((post) => post.type === "Page");
  const trailing = page.data.sections.at(-1);
  const kept = document.posts.find((post) => post.url === trailing);

  assert.match(
    page.data.content ?? "",
    /class=hero/,
    "what stood before the accepted block is still `content`, above the loop",
  );
  assert.equal(kept.type, "SectionMarkup",
    "and what stood after it is the last entry in the page's own list (SH763)");
  assert.match(
    kept.data.html,
    /footer class=bottom/,
    "the footer stood after the accepted block, so it belongs below the sections. This is the "
      + "assertion the replica's home page would have failed: header at 95%, footer at 98%, "
      + "sections after both (SH693)",
  );
  assert.doesNotMatch(
    page.data.content ?? "",
    /footer class=bottom/,
    "and it is not in both halves — a remainder emitted twice is the defect wearing the fix's clothes",
  );
  assert.ok(
    !("contentAfter" in page.data),
    "and no `contentAfter`: the field no `Page` declares, which refused every conversion against a "
      + "real instance at ops[28] (SH763)",
  );
});

test("SH693: the wrapper's closing tag travels below, so the sections land inside it", async () => {
  // A property that falls out of splitting rather than gluing, and is worth pinning: the source's
  // `<main>` opens in the leading half and closes in the trailing one, so the sections render
  // INSIDE it. Gluing put them after `</main>`, outside the element the source used to hold its
  // content — which is the same defect as the footer, one level of nesting up.
  const site = { "/": MIXED_PAGE };
  const [, notes] = await blocksOfPage(ctxWith({ site }));
  const ctx = ctxWith({ site, plan: { accepted: [{ block: notes, type: "SectionRichText" }] } });

  await convert.run({ site: "Acme", out: "conversion.json" }, [], ctx);
  const document = JSON.parse(ctx.written[norm("/repo/conversion.json")]);
  const page = document.posts.find((post) => post.type === "Page");
  const kept = document.posts.find((post) => post.url === page.data.sections.at(-1));

  assert.match(page.data.content, /^<main>/, "the wrapper opens in the leading half");
  assert.equal(
    kept.data.html,
    "</main>",
    "and closes in the trailing one, so every section renders inside the element the source used "
      + "to hold its content (SH693)",
  );
});

test("SH763: the layout draws a markup section verbatim, in the place the list gives it", () => {
  // The other end of the same change: a section type nothing renders is a section that lost the
  // markup — which is what `contentAfter` was, one field earlier.
  const loop = PAGE_BODY_HTML.indexOf("{{#getRelation sections}}");
  const raw = PAGE_BODY_HTML.indexOf("{{#if html}}");

  assert.ok(raw > loop, `the markup branch must sit inside the sections loop: ${PAGE_BODY_HTML}`);
  assert.ok(
    raw < PAGE_BODY_HTML.indexOf("{{/getRelation}}"),
    "inside it, not after — the order is the list's now, and a branch below the loop would put "
      + "every run of kept markup back at the bottom of the page (SH763)",
  );
  assert.ok(
    PAGE_BODY_HTML.includes('<div sh-post="{{id}}" sh-field="html">{{{html}}}</div>'),
    "drawn raw and addressed: `sh-post` binds the block to the markup post and `sh-field` is what "
      + "lets a curator replace it from the editor rather than only from the console form",
  );
  assert.ok(
    !PAGE_BODY_HTML.includes("contentAfter"),
    "and the field that refused every conversion is gone from the template too (SH763)",
  );
  assert.ok(
    PAGE_BODY_HTML.indexOf("{{{content}}}") < loop,
    "the leading half still renders before the loop, which is the order the whole task is about",
  );
});

/**
 * Every field reference the template makes inside an `{{#each}}`, with whether it is scoped (SH832).
 *
 * A deliberate re-read of the bytes rather than a list: the defect was that nobody had counted, and
 * a list would go stale on the next field the vocabulary gains. Block names, `@index`, helper names
 * and quoted literals are not references; `../` is a reference to a scope this rule is not about.
 */
function eachScopedReferences(template) {
  const found = [];
  let depth = 0;
  for (const [, body] of template.matchAll(/\{\{\{?([^{}]+)\}?\}\}/g)) {
    const trimmed = body.trim();
    if (/^#each\b/.test(trimmed)) { depth += 1; continue; }
    if (/^\/each\b/.test(trimmed)) { depth -= 1; continue; }
    if (!depth || /^[!>=/]/.test(trimmed)) continue;
    const tokens = trimmed.replace(/^[#^]\S*\s*/, "").split(/\s+/);
    for (const token of tokens) {
      if (!token || /^[@"'(]/.test(token) || token.includes("=") || token.startsWith("../")) continue;
      if (["this", "else", "if", "unless", "each", "with", "file_url"].includes(token)) continue;
      found.push({ token, scoped: token.startsWith("./") });
    }
  }
  return found;
}

test("SH832: no field a row draws is spelled so Handlebars can find it on the parent", () => {
  // On the replica's home page a section rendered its heading three times: once as the `h2`, and
  // once inside each of two cards whose rows were genuinely empty (title=None body=None). A bare
  // name resolves UP the context chain, so `{{#if title}}` found the section post's title, was
  // true, and `{{title}}` printed the heading — the guard could not fire, because the thing it
  // guards against is never absent. Eight bare lookups, zero scoped, every collection type.
  const references = eachScopedReferences(PAGE_BODY_HTML);

  assert.ok(references.length >= 8,
    `the reader found ${references.length} reference(s) inside {{#each}} — a rule that matches `
      + "nothing is the way this check would silently stop working");
  assert.deepEqual(
    references.filter((each) => !each.scoped).map((each) => each.token), [],
    "each one must be `./name`: not a blank but a plausible wrong value is this project's recurring "
      + "failure shape, and this one is invisible to every count - `annotations` reports the card as "
      + "annotated and `diff` sees text where the capture had text",
  );
  // And the reader in the other direction, on the spelling that shipped the defect: a check whose
  // scanner silently matched nothing would pass forever after the next field was added bare.
  assert.deepEqual(
    eachScopedReferences('{{#each features}}{{#if title}}{{title}}{{/if}}{{/each}}')
      .map((each) => each.scoped), [false, false],
    "the reader finds the shape it exists to refuse",
  );
});

test("SH830: the layout branches for the types the conversion created, and for no others", () => {
  // The measurement: a replication onto an empty instance converted cleanly - 66 objects, exit 0,
  // four routes 200 - and two of its four `verify` warnings were `template-unknown-field · align`
  // and `· subheading` against the layout `convert` had just written. Both are SectionHero fields
  // and the capture accepted no hero, so the branches resolved for nothing. The warning is correct
  // and guaranteed, which is the combination that teaches a reader to stop reading warnings.
  const fields = sectionFieldsOf(["Page", "SectionFeatureGrid", "SectionMarkup"]);
  const body = pageBodyFor(fields);

  for (const gone of ["subheading", "align", "variant", "image", "ctaLabel", "quotes", "logos"]) {
    assert.ok(
      !body.includes(gone),
      `no type this conversion creates declares \`${gone}\`, so the branch that reads it is a `
        + "guaranteed warning against a file convert wrote seconds earlier",
    );
  }
  for (const kept of ["heading", "intro", "body", "features", "columns", "html"]) {
    assert.ok(body.includes(`{{${kept}}}`) || body.includes(`{{#if ${kept}}}`),
      `SectionFeatureGrid or SectionMarkup declares \`${kept}\`, and dropping a branch a post can `
        + "fill would lose content rather than a warning");
  }
  assert.match(body, /data-shio-section="\{\{type\}\}"/,
    "`type` is a key getRelation puts on every row, so no post-type has to declare it and no "
      + "conversion may drop it");

  // The markup branch is the one multi-line shape, and its *else* arm is the authorable page: an
  // authorable-only conversion has to lose the branch and keep the section.
  const authorable = pageBodyFor(sectionFieldsOf(["Page", "SectionRichText"]));
  assert.ok(!authorable.includes("{{#if html}}"), "no SectionMarkup, so nothing declares `html`");
  // SH878 — counted rather than absent. This used to read `!includes("{{else}}")`, which was a
  // true statement about the whole template only while the markup branch was the only thing in it
  // that branched; the nav's `{{#menu}}` now falls back to a page listing where a source stated no
  // navigation, and that `{{else}}` has nothing to do with SectionMarkup.
  assert.equal((authorable.match(/\{\{else\}\}/g) ?? []).length, 2,
    "and the markup branch went with it, opener and closer — the two left are both the nav's");
  assert.match(authorable,
    /\{\{#menu title="Primary"\}\}<li>\{\{#if url\}\}[\s\S]*?\{\{else\}\}[\s\S]*?\{\{\/if\}\}<\/li>\{\{else\}\}[\s\S]*?\{\{\/menu\}\}/,
    "which two: a row with no url is a group heading — see SH381 for that convention, and it is "
      + "why a curator nesting a menu later needs no template change — and a menu that resolves "
      + "to nothing falls back to the page listing");
  assert.match(authorable, /<section class="shio-section"[\s\S]*<\/section>/,
    "while the arm that draws a composed section survives - dropping it would render every "
      + "authorable page empty, which is a far worse defect than the warning this removes");
  assert.equal(
    (authorable.match(/\{\{\/getRelation\}\}/g) ?? []).length, 1,
    "and the template still closes exactly once: an unbalanced block is a layout that will not "
      + "compile, which SH192 refuses at write time",
  );

  // Nothing dropped when the whole vocabulary is present, or the filter would be removing branches
  // a site can fill - the direction that loses content instead of noise.
  assert.equal(pageBodyFor(sectionFieldsOf([...Object.keys(SECTION_TYPES), "SectionMarkup"])),
    PAGE_BODY_HTML,
    "a conversion using every type gets the template unchanged, byte for byte (P5)");
});

/**
 * SH763 — the assertion that would have caught it, at the boundary it crossed.
 *
 * SH693 shipped the writer and the generated template and **not the model**: `contentAfter` was
 * written at `convert.mjs`, emitted by the layout, and declared by no `Page` anywhere in the
 * repository. Every gate was green — `drift` answered `[]`, the CLI suite passed, the Java suite
 * passed — because every one of them checks this file against itself. The instrument that was
 * missing is the one that reads the **other side**: a conversion writes a field, and the catalogue
 * the instance provisions from either declares it or the batch dies at `ops[28]` with
 * `'Page' does not declare a field named 'contentAfter'`.
 *
 * Per SH322: the grep is the deliverable. This is not a test of the SH763 fix — it is the check
 * that finds the *next* field somebody adds to a post here and not to the model, before a
 * replication run against a real instance does.
 */
const VOCABULARY_CATALOGUE = new URL(
  "../../shio-app/src/main/resources/post-types/vocabulary/", import.meta.url,
);

/** Post-type name → every field name it declares, nested ones included. */
function declaredFields() {
  const out = new Map();
  for (const file of readdirSync(VOCABULARY_CATALOGUE)) {
    if (!file.endsWith(".json")) continue;
    const def = JSON.parse(readFileSync(new URL(file, VOCABULARY_CATALOGUE), "utf8"));
    const names = new Set();
    const walk = (fields) => (fields ?? []).forEach((field) => {
      if (field.name) names.add(field.name);
      walk(field.fields);
    });
    walk(def.fields);
    out.set(def.name, names);
  }
  return out;
}

test("SH766: the title is declared on every page shape, and drawn only where nothing else draws it", () => {
  // The template half. Which branch a page takes is decided by data, so `ShConvertedLayoutRenderTest`
  // holds the half this cannot: that a real mixed page really emits `data-shio-prop="title"`.
  const drawn = PAGE_BODY_HTML.indexOf('<h1 class="shio-page-title" sh-field="title">');
  const declared = PAGE_BODY_HTML.indexOf('{{#if content}}<div hidden sh-field="title">');

  assert.ok(drawn > 0, `the composed page still draws its own title: ${PAGE_BODY_HTML}`);
  assert.ok(
    declared > 0,
    "and the page that kept the source's markup declares it without drawing it — the branch SH766 "
      + "found empty, where the page's own title was rendered by a captured <h1> and editable by "
      + `nobody: ${PAGE_BODY_HTML}`,
  );
  assert.equal(
    (PAGE_BODY_HTML.match(/sh-field="title"/g) ?? []).length,
    2,
    "two declarations and never both on one page: `{{#unless content}}` and `{{#if content}}` are "
      + "complementary, so exactly one runs. A third would be a field annotated twice on one page",
  );
});

/**
 * SH779 — the warning that is the whole of SH772's bound.
 *
 * <h3>Why a `lines.push` earns a test</h3>
 *
 * <p>SH772 emits the captured stylesheet once per run of kept markup on a page with no `content`,
 * and bounded that by argument rather than by mechanism: the typical count is one, because such a
 * page's accepted blocks start at the top and a capture's accepted blocks are contiguous. The bound
 * is honest and it is not enforced — so the promise beside it is what carries the risk, and every
 * other claim that task makes is asserted while this one was not.</p>
 *
 * <h3>The shape, which is the only one that takes two copies</h3>
 *
 * <p>`<body>` begins with an accepted block, so there is no leading run and no `content`; and kept
 * markup stands **between** two accepted blocks as well as after the last, so there are two runs.
 * That is also the case SH693 said it could not place and SH763's N-split made ordinary — the
 * sections come back interleaved, `hero, between, notes, footer`, which this pins in passing.</p>
 */
test("SH779: a page that would take the captured stylesheet twice is named in the report", () => {
  const html = "<html><body><section class=hero><h1>Hi</h1></section><div class=mid><p>Between</p></div>"
    + "<section class=notes><h2>N</h2></section><footer class=bot><p>Foot</p></footer></body></html>";
  const at = (needle) => html.indexOf(needle);
  const proposal = {
    candidates: [
      candidate("b1", { fields: { heading: "Hi" } }),
      candidate("b2", { type: "SectionRichText", fields: { body: "<p>N</p>" } }),
      candidate("b3", { type: "SectionCta", mode: "fidelity", fields: {} }),
    ],
    pages: [{
      path: "/",
      title: "Home",
      blocks: ["b1", "b2", "b3"],
      spans: [
        [at("<section class=hero>"), at("<div class=mid>")],
        [at("<section class=notes>"), at("<footer class=bot>")],
        [at("<footer class=bot>"), at("</body>")],
      ],
    }],
    theme: { rows: [{ path: "brand", source: "declared" }], css: ":root{}", tokens: {} },
  };

  const built = buildConversion({
    proposal,
    plan: { accepted: [{ block: "b1", type: "SectionHero" }, { block: "b2", type: "SectionRichText" }] },
    site: "Acme",
    markup: new Map([["/", html]]),
  });
  const page = built.document.posts.find((post) => post.type === "Page");

  assert.equal(page.data.content, undefined,
    "the fixture has to produce the shape or the report has nothing to warn about: an accepted "
      + "block at the very top leaves no leading run, so there is no `content` (SH772)");
  assert.deepEqual(
    page.data.sections,
    [`${SECTIONS_FOLDER}/b1`, `${SECTIONS_FOLDER}/markup-home-1`,
      `${SECTIONS_FOLDER}/b2`, `${SECTIONS_FOLDER}/markup-home-2`],
    "and the two runs are interleaved where they stood — the case SH693 said it could not place, "
      + "which SH763's N-split made ordinary",
  );

  assert.match(
    describeConversion(built),
    /begins with an accepted block and keeps 2 runs/,
    "SH779: this line is the whole of SH772's bound. The conversion still runs and the pages are "
      + "still correct; the only thing missing without it is the sentence that would have made "
      + "somebody move an acceptance, and a reviewer reading a report with no warning concludes "
      + "there is nothing to hear.",
  );
});

test("SH763: every field a conversion writes is declared by the type it names", async () => {
  const declared = declaredFields();
  assert.ok(
    declared.get("Page")?.size,
    "the catalogue this reads is the one `ShVocabularyPostTypes` provisions from; an empty parse "
      + "would make the assertion below pass over nothing, which is the state SH693 shipped in",
  );

  const site = { "/": MIXED_PAGE.replace("</main>", "</main><footer class=bottom><p>Acme</p></footer>") };
  const [hero] = await blocksOfPage(ctxWith({ site }));
  const ctx = ctxWith({ site, plan: { accepted: [{ block: hero, type: "SectionHero" }] } });

  await convert.run({ site: "Acme", out: "conversion.json" }, [], ctx);
  const document = JSON.parse(ctx.written[norm("/repo/conversion.json")]);

  const checked = [];
  const undeclared = [];
  for (const post of document.posts) {
    const fields = declared.get(post.type);
    // A system type — `PageLayout`, `Theme`, `SiteScripts`, `Redirect`, `File` — is provisioned by
    // `ShRenderPostTypes` in Java and is not in this catalogue. Skipped rather than failed: its
    // model is declared on the other side of the same seam, and asserting it here would be this
    // file's opinion of a file it cannot read.
    if (!fields) continue;
    for (const name of Object.keys(post.data ?? {})) {
      checked.push(`${post.type}.${name}`);
      if (!fields.has(name)) undeclared.push(`${post.type}.${name} (${post.url})`);
    }
  }

  for (const owed of ["Page.sections", "Page.content", "SectionMarkup.html"]) {
    assert.ok(
      checked.includes(owed),
      `the fixture has to exercise the mixed shape and it did not write ${owed}, so this proves `
        + `nothing: ${checked.join(", ")}`,
    );
  }
  assert.deepEqual(
    undeclared,
    [],
    "Every name here is a field this command writes and the instance's own catalogue does not "
      + "declare, so the batch refuses the whole document at the op that carries it — which is how "
      + "`contentAfter` made every conversion against a real instance fail while every gate in the "
      + "repository stayed green (SH763).",
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

  const trailing = document.posts.find((post) => post.url === page.data.sections.at(-1));

  assert.notEqual(hero, notes, "the fixture is two blocks, or this test asserts nothing");
  assert.deepEqual(
    page.data.sections,
    [`${SECTIONS_FOLDER}/${hero}`, `${SECTIONS_FOLDER}/markup-home-1`],
    "the accepted block is a section, and so is the markup that follows it — SH763's whole shape: "
      + "the order is the list's, so there is a place for a run of kept markup wherever it stood",
  );
  // SH693 amended this from one field to two, and SH763 from two fields to one field and a section.
  // The claim is unchanged throughout: the block nobody accepted is still on the page, as the
  // source's own bytes with the source's own class attribute — the remainder, cut out of the
  // captured file rather than re-serialized. What changed is WHERE it sits. The hero was accepted
  // and stood first, so everything after it renders BELOW the sections instead of above them.
  assert.equal(
    `${page.data.content ?? ""}${trailing.data.html ?? ""}`,
    "<main><section class=notes><h2>Notes</h2><p>Kept exactly as the source wrote it.</p></section></main>",
    "the two parts are the remainder, and nothing of it was lost in being split",
  );
  assert.equal(
    trailing.data.html,
    "<section class=notes><h2>Notes</h2><p>Kept exactly as the source wrote it.</p></section></main>",
    "and the part below the sections is the markup that FOLLOWED the accepted block in the source — "
      + "which is the ordering the generated layout could not express (SH693)",
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
  // SH830 — the contract is a property of the *template*, so the roles are counted over it and over
  // the shell this conversion wrote (`shio-page` is on the layout's own `<body>` and in no branch).
  // A conversion carries the branches for the types it created, and this one accepted a hero: asking
  // its layout for `shio-grid` would be asking it to draw a field no post on the site has.
  const template = layoutElements(PAGE_BODY_HTML);
  const used = new Set([...elements, ...template].flatMap((element) => element.classes));

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
  const section = template.find((element) => element.classes.includes("shio-section"));
  assert.match(
    section.attrs, /data-shio-columns="\{\{columns\}\}"/,
    "the grid's column count is SectionFeatureGrid.columns and not a role: a theme reads "
      + "[data-shio-columns=\"3\"] .shio-grid, so the contract has both ends and the template writes "
      + "the number once",
  );

  // The two properties SH185 imposes, and the reason the rule names three roles rather than eight.
  // Over the template (SH830): the rules bind every wrapper the vocabulary can emit, not only the
  // ones the branches this conversion kept happen to reach.
  const wrappers = template.filter((element) =>
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
    template.some((element) =>
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

test("SH1039: the generated layout declares what may be added to the page", () => {
  // SH1038 made a section selectable, movable and removable, and an editor that can do all three
  // still cannot ADD one because nothing says which types fit where. The catalogue has to be the
  // template's — only the template knows what a container accepts — and `{{#getRelation sections}}`
  // is a BLOCK, not an element, so the element composing the sections is `<body>` itself. That is
  // where the server's pass finds the container and therefore where the declaration belongs.
  const { document } = buildConversion({
    proposal: {
      candidates: [
        candidate("b1a2"),
        candidate("c3d4", { type: "SectionCta", fields: { heading: "Start", ctaLabel: "Go" } }),
      ],
      pages: [{ path: "/", title: "Home", blocks: ["b1a2", "c3d4"] }],
    },
    plan: {
      accepted: [{ block: "b1a2", type: "SectionHero" }, { block: "c3d4", type: "SectionCta" }],
    },
    site: "Acme",
  });
  const html = document.posts.find((post) => post.type === "PageLayout").data.HTML;
  const body = layoutElements(html).find((element) => element.tag === "body");

  const accepts = /\bsh-accepts="([^"]*)"/.exec(body.attrs);
  assert.ok(accepts, "without this the console is told nothing fits, which is what an absent "
    + "declaration honestly means - so a converted site would compose sections nobody can add to");
  assert.deepEqual(accepts[1].split(" "), ["SectionCta", "SectionHero"],
    "what this conversion provisions and nothing else: naming a type the document does not create "
      + "would offer a curator something the instance would refuse, and the list is derived from "
      + "the same table the fields are, because a third copy of the vocabulary is the drift "
      + "SH245 bans");
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
  // SH830 — over the template, for the reason the SH508 test above states: this conversion accepted
  // a hero, so its layout carries no row at all and the claim being made is about the vocabulary.
  for (const row of ["features", "questions", "logos", "quotes"]) {
    assert.ok(PAGE_BODY_HTML.includes(`data-shio-row="${row}"`),
      `\`[data-shio-row="${row}"]\` is what \`.shio-${row}\` was, and it reaches the leaves too: `
        + `\`[data-shio-row="${row}"] h3\` is the title the class named`);
  }
});

// --- SH509: the theme the vocabulary never had ---

/** The DTCG tree flattened the way `ShThemeTokens` flattens it: `shio-color.ink` → `color-ink`. */
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
    "and the tokens ride it, so changing a colour is a field edit rather than a stylesheet rewrite "
      + "- this proposal declares no palette, so SH831 has nothing to bridge and the defaults stand");
});

test("SH831: the theme the replica renders with is painted in the source's own colours", () => {
  // The defect, in one line: a served page carried three token vocabularies and no mapping between
  // any two. `--shio-color-accent` was this file's dark green while the site's accent was orange, and the
  // extracted palette that knew better was read by nothing. `propose` even said so - "12 recurring
  // token(s) name the palette and change nothing on their own" - an accurate note about an artefact
  // the pipeline then shipped anyway.
  const conversion = buildConversion({
    proposal: withCapturedTheme({
      pages: [{ path: "/", title: "Home", blocks: ["b1a2"] }],
      theme: {
        css: CAPTURED_CSS,
        tokens: {},
        rows: [
          { path: "ink", value: "#10243a", type: "color", source: "declared", group: "color", role: "text", page: true },
          { path: "brand", value: "#0b7285", type: "color", source: "declared", group: "color", role: "text", refs: 4, page: false },
          { path: "color.accent-1", value: "#ff6600", type: "color", source: "recurring", group: "color", role: "accent", refs: 7 },
          // SH834 — a radius and a font stack beside them, each in a group with one role, so
          // neither needs the page-level statement a colour does.
          { path: "radius", value: "6px", type: "dimension", source: "declared", group: "radius", role: null, refs: 3, page: false },
        ],
      },
    }),
    plan: { accepted: [{ block: "b1a2", type: "SectionHero" }] },
    site: "Acme",
  });

  const { document, palette } = conversion;
  const written = document.posts.find((post) => post.data.TITLE === vocabularyThemeTitle("Acme"));
  const tokens = JSON.parse(written.data.TOKENS);
  assert.equal(tokens["shio-color"].ink.$value, "#10243a", "the page's own text colour");
  assert.equal(tokens["shio-color"].accent.$value, "#ff6600",
    "and the accent, which is the whole visual mismatch: eight `var(--color-*)` references in this "
      + "theme now resolve to the source's palette instead of to a green nobody chose");
  assert.equal(tokens["shio-color"].line.$value, VOCABULARY_THEME_TOKENS["shio-color"].line.$value,
    "a slot the palette says nothing about keeps the default, which is the right value to ship - a "
      + "replica with an invisible border is worse than one with a borrowed grey");
  // SH834 — a corner radius is a group with one role, so `--radius` is taken at its rank without
  // the page-level statement `--brand` was refused for. The two rules are different because the
  // groups are: `body { border-radius }` would mean nothing, so there is nothing to disambiguate.
  assert.equal(tokens["shio-size"].radius.$value, "6px");
  assert.equal(tokens["shio-space"].step.$value, VOCABULARY_THEME_TOKENS["shio-space"].step.$value,
    "and a slot the bridge names no group for keeps its default silently: a rhythm step is the "
      + "unit other spacing is a multiple of, which the commonest padding value is not");

  assert.deepEqual(palette.mapped.map((row) => row.name),
    ["shio-color.accent", "shio-color.ink", "shio-size.radius"]);
  assert.deepEqual(palette.unmapped.map((row) => row.name),
    ["shio-color.ink-muted", "shio-color.line", "shio-color.surface", "shio-color.surface-sunk", "shio-text.base",
      "shio-text.family"]);
  const report = describeConversion(conversion);
  assert.match(report, /--shio-color-accent #ff6600 from color\.accent-1 \(accent#1\)/,
    "said out loud, and traceable: the reader can check the value against the token it came from");
  assert.match(report, /--shio-color-line [^\n]*the source states no border#1/,
    "and so is the default that stood, because a reader cannot tell a colour that was matched from "
      + "one that was never looked for unless the run says which (P9)");
  assert.match(report, /--shio-text-base [^\n]*the source states no font\.size#1/,
    "SH834: a scale is refused rather than guessed — `recurringTokens` ranks font sizes by "
      + "reference count and this theme's five steps are ordered by size, so rank 2 is not "
      + "`shio-text.lead`. Only the step the page states outright can be taken");
});

test("SH846: a run says which slots refused a value, which is neither filled nor never-offered", () => {
  // The measurement: this source declares a transparent page and a near-white text colour, and the
  // run reported `--shio-color-surface: #0000` as one of *3 of 9 token(s) from the captured stylesheet*.
  // Two failures printed as two successes, on the one report a curator reads about appearance.
  const conversion = buildConversion({
    proposal: withCapturedTheme({
      pages: [{ path: "/", title: "Home", blocks: ["b1a2"] }],
      theme: {
        css: CAPTURED_CSS,
        tokens: {},
        rows: [
          { path: "vg.background", value: "#0000", type: "color", source: "declared", group: "color", role: "background", refs: 2, page: true },
          { path: "vg.foreground", value: "#f9fafb", type: "color", source: "declared", group: "color", role: "text", refs: 3, page: true },
        ],
      },
    }),
    plan: { accepted: [{ block: "b1a2", type: "SectionHero" }] },
    site: "Acme",
  });

  const written = conversion.document.posts.find((post) => post.data.TITLE === vocabularyThemeTitle("Acme"));
  const tokens = JSON.parse(written.data.TOKENS);
  assert.equal(tokens["shio-color"].surface.$value, VOCABULARY_THEME_TOKENS["shio-color"].surface.$value,
    "the default is the right value to ship - SH684's instinct, one surface along: a derivation "
      + "that cannot fill a required field declines rather than writing something unusable");
  assert.equal(tokens["shio-color"].ink.$value, VOCABULARY_THEME_TOKENS["shio-color"].ink.$value);
  assert.deepEqual(conversion.palette.rejected.map((row) => [row.name, row.reason]),
    [["shio-color.ink", "contrast"], ["shio-color.surface", "transparent"]]);

  const report = describeConversion(conversion);
  assert.match(report, /palette: 0 of 9 token\(s\)[^\n]*, 2 refused/,
    "and the count says so: a refusal used to be counted as a token taken from the stylesheet");
  assert.match(report, /--shio-color-surface [^\n]*REFUSED: the source's background#1 is #0000, which paints nothing/);
  assert.match(report, /--shio-color-ink [^\n]*REFUSED: #f9fafb from vg\.foreground reads at 1\.05:1/,
    "with the ratio, so a reader can check the refusal instead of taking it (P9)");
});

test("SH846: a palette the floor never touches reports exactly as it did before", () => {
  const conversion = buildConversion({
    proposal: withCapturedTheme({
      pages: [{ path: "/", title: "Home", blocks: ["b1a2"] }],
      theme: {
        css: CAPTURED_CSS,
        tokens: {},
        rows: [
          { path: "vg.background", value: "#0f172a", type: "color", source: "declared", group: "color", role: "background", refs: 2, page: true },
          { path: "vg.foreground", value: "#f1f5f9", type: "color", source: "declared", group: "color", role: "text", refs: 3, page: true },
        ],
      },
    }),
    plan: { accepted: [{ block: "b1a2", type: "SectionHero" }] },
    site: "Acme",
  });

  assert.deepEqual(conversion.palette.rejected, []);
  const count = /^ {2}palette: .*$/m.exec(describeConversion(conversion))[0];
  assert.match(count, /palette: 2 of 9 token\(s\) from the captured stylesheet/);
  assert.ok(!count.includes("refused"),
    "no `refused` clause where nothing was refused - a count that always carries a zero is a count "
      + "a reader stops reading");
  // SH849 — and the clause that is on every run, because it is arithmetic rather than an outcome:
  // nine slots the bridge names does not add up to a theme of fifteen, and a reader cannot tell a
  // report that omitted six from a theme that has six the source can never fill.
  assert.match(count, /and 6 slot\(s\) this theme has no source role for/);
});

test("SH854: the pair the floor was not allowed to fix is a line in the report, not only in --json", () => {
  // The same source as the test above, which is the point: a run that says `2 of 9, nothing refused`
  // over a replica whose cards are unreadable is two successes printed over a defect. SH852 computed
  // the fact and carried it to `--json`; a reader of the text had no reason to make that call.
  const conversion = buildConversion({
    proposal: withCapturedTheme({
      pages: [{ path: "/", title: "Home", blocks: ["b1a2"] }],
      theme: {
        css: CAPTURED_CSS,
        tokens: {},
        rows: [
          { path: "vg.background", value: "#0f172a", type: "color", source: "declared", group: "color", role: "background", refs: 2, page: true },
          { path: "vg.foreground", value: "#f1f5f9", type: "color", source: "declared", group: "color", role: "text", refs: 3, page: true },
        ],
      },
    }),
    plan: { accepted: [{ block: "b1a2", type: "SectionHero" }] },
    site: "Acme",
  });

  assert.deepEqual(conversion.palette.unheld.map((row) => [row.text, row.on]),
    [["shio-color.ink", "shio-color.surface-sunk"]]);

  const report = describeConversion(conversion);
  assert.match(report, /--shio-color-ink [^\n]*on --shio-color-surface-sunk [^\n]*contrast NOT HELD/,
    "neither refused nor unchecked: nothing was reverted, so `rejected` would name a slot the "
      + "source filled as one it did not, and the arithmetic ran, so `unchecked` would be a lie the "
      + "other way");
  assert.match(report, /--shio-color-surface-sunk is this theme's default and the source states none/,
    "the fix is the slot the source never stated - a curator told only that a pair is unreadable "
      + "cannot tell which of the two halves to go and change");
  assert.match(report, /Both halves ship as they are/,
    "and it says the pair ships, because the other two lines say a default stands and a reader who "
      + "read this as one of them would think the run had already handled it");
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
      + "compiles color.ink into --shio-color-ink and prepends the :root block, so an undeclared one is a "
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
  // `<style` and not `<style>`: SH829 gave the element an attribute, and a count keyed on the bare
  // closing bracket would have read one stylesheet as none — which is the direction that passes.
  assert.equal(layout.data.HTML.split("<style").length - 1, 1);
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

test("SH829: every <style> a conversion emits says which Theme post it came from", () => {
  // The two sheets a replica carries are two posts with titles, and the digest's region table
  // printed `style[1]` and `style[2]` — an ordinal the reader had to resolve with a second call.
  // Both emissions are here, so both are asserted here: a page that named one and not the other
  // would print a title beside an index, which is the lookup restored and disguised.
  const opened = CAPTURED_STYLE_HTML.match(/<style\b[^>]*>/g) ?? [];
  assert.equal(opened.length, 2, "both branches of SH772's condition emit the captured sheet");
  for (const tag of opened) {
    assert.equal(
      tag, '<style data-shio-theme="{{title}}">',
      "`title` is the row `getRelation` yields for the captured Theme post, and an attribute is "
        + "where Handlebars escapes it - a title is content, and HTML's own `title` on a <style> "
        + "would declare an alternate stylesheet set and stop the browser painting with both",
    );
  }

  const { document } = buildConversion({
    proposal: withCapturedTheme({ pages: [{ path: "/", title: "Home", blocks: ["b1a2"] }] }),
    plan: { accepted: [{ block: "b1a2", type: "SectionHero" }] },
    site: "Acme",
  });
  const layout = document.posts.find((post) => post.type === "PageLayout");
  assert.ok(
    layout.data.HTML.includes('<style data-shio-theme="{{theme.title}}">{{theme.css}}</style>'),
    "and the layout's own sheet is named from the model, so a hand-written layout that does not "
      + "ask for the title honestly keeps style[N] rather than being given an invented one",
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

  // By url and not by index since SH576: this capture answers nothing at `/`, so the conversion now
  // writes a front door and it sorts first. The page under test is still the one the source had.
  const quiet = pages.find((page) => page.url === "/quiet");
  assert.equal(quiet.data.title, "/quiet", "the path, not the site name: two untitled pages must "
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
  // SH878 — the query is the FALLBACK now, and this test's fixture is what reaches it: a capture
  // whose source stated no navigation. Five of the seven corpus archetypes are that shape, so the
  // branch is the ordinary case rather than a safety net.
  assert.match(nav[1], /\{\{#menu title="Primary"\}\}[\s\S]*\{\{else\}\}[\s\S]*\{\{#query type="Page"/,
    "the source's own menu first, the page listing only where it stated none");
});

test("SH880: the layout literal carries no interpolation, because the JVM reads its bytes", () => {
  // Found by SH880's own render on its first run. `PAGE_BODY_HTML` is a cross-language contract —
  // `ShConvertedLayoutRenderTest` slices it out of convert.mjs between two backticks (SH245), so a
  // `${…}` in it reaches the JVM as the four characters `${x}`. SH878 wrote the menu's title as an
  // interpolation, which shipped a layout asking for a menu called `${MENU_TITLE}`: it matches
  // nothing, `{{else}}` draws the page listing, and the page looks like a site with a working nav.
  // Every assertion in this file stayed green, because here the literal interpolates.
  assert.equal(PAGE_BODY_HTML.includes("${"), false,
    "an interpolation here is invisible to the reader that matters — it is correct in this "
      + "language and a literal in the other one");
  assert.match(PAGE_BODY_HTML, new RegExp(`\\{\\{#menu title="${MENU_TITLE}"\\}\\}`),
    "and the two spellings agree: the title the layout looks up is the title the Menu post carries");
});

test("SH878: a source that states its own navigation is replicated saying what it said", () => {
  // The measurement, at the boundary it crossed. On the `theme-heavy` archetype — the one corpus
  // source with a real <nav> — the replica used to render `About & contact` then `Northwind & Co`:
  // both labels the pages' own <title>, alphabetically, against a source whose menu says
  // `Northwind & Co` then `About`. One label wrong, the order inverted, and neither visible to a
  // reader who had not seen the source.
  const built = buildConversion({
    proposal: proposeBuild({
      inventory: { source: "https://x.test/", pages: [{ path: "/", title: "Northwind & Co" }] },
      markup: new Map([["/", VERBATIM_PAGE]]),
      html: { tokenize, parseAttrs, stripNonMarkup },
    }),
    plan: { accepted: [] },
    site: "Acme",
    markup: new Map([["/", VERBATIM_PAGE]]),
    inventory: {
      navigation: [
        { label: "Northwind & Co", href: "/", on: 1, occurrences: 1, external: false },
        // Site-wide but off-site: `MenuItem.href` exists for it and SH382's lint refuses a row
        // setting both, but a replica pointing off-site from its primary menu is a decision a
        // person makes rather than one a capture implies.
        { label: "Careers", href: "https://jobs.example.com", on: 1, occurrences: 1, external: true },
        // On one page of one, so site-wide — but no page was written at this address.
        { label: "Pricing", href: "/pricing", on: 1, occurrences: 1, external: false },
      ],
    },
  });

  const items = built.document.posts.filter((post) => post.type === "MenuItem");
  assert.deepEqual(items.map((item) => item.data.label), ["Northwind & Co"],
    "the source's own label, and only the entry whose target this conversion actually wrote");
  assert.deepEqual(items[0].data.target, ["/"]);
  const menu = built.document.posts.find((post) => post.type === "Menu");
  assert.deepEqual(menu.data, { title: "Primary", items: ["/menu/northwind-co"] },
    "in the source's order, which is the one ordering a source states outright");
  assert.deepEqual(built.menu, { items: 1, stated: 3 },
    "and the report carries both numbers: two entries were considered and dropped, which a bare "
      + "count of items would read as everything the source had");
  assert.ok(built.document.postTypes.includes("Menu")
    && built.document.postTypes.includes("MenuItem"),
    "provisioned, or the write is refused on a type nobody created (SH607)");
  assert.ok(built.document.folders.some((folder) => folder.path === "/menu"));
});

test("SH878: a source that states no navigation writes no menu, and keeps the page listing", () => {
  // P5, and the ordinary case: five of the seven corpus archetypes have no <nav> at all, so the
  // document a capture without one produces is the same bytes it produced before.
  const built = buildConversion({
    proposal: proposeBuild({
      inventory: { source: "https://x.test/", pages: [{ path: "/", title: "Home" }] },
      markup: new Map([["/", VERBATIM_PAGE]]),
      html: { tokenize, parseAttrs, stripNonMarkup },
    }),
    plan: { accepted: [] },
    site: "Acme",
    markup: new Map([["/", VERBATIM_PAGE]]),
  });

  assert.equal(built.menu, undefined);
  assert.equal(built.document.posts.some((post) => post.type === "Menu"), false);
  assert.equal(built.document.folders.some((folder) => folder.path === "/menu"), false);
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

/*
 * SH576 — the one address the grammar does not spell, unwritten.
 *
 * `post:<site>/` is a site's home page: the address form SH433 exists for because SH79's grammar
 * does not spell it. A source that answers `/` gives the conversion one for free; a source that does
 * not gave it nothing, and nothing is what was written. Measured on the `locale-prefixed` archetype
 * — every page under `/en-us/` or `/pt-br/`, `/` a 404 at the source — the document held pages at
 * both prefixes and none at `/`, so the replica's root, the first URL anybody opens and the one the
 * console frames, answered nothing while the run reported every page it wrote as correct.
 */

const LOCALE_PAGES = () => composed(
  [{ path: "/en-us/", title: "Home", blocks: ["hero1"] },
    { path: "/pt-br/", title: "Início", blocks: ["hero1"] }],
  [candidate("hero1")],
);
const ACCEPT_HERO = { accepted: [{ block: "hero1", type: "SectionHero" }] };

test("SH576: a source with no page at / gets a front door, and it lists the site's pages", () => {
  const built = buildConversion({ proposal: LOCALE_PAGES(), plan: ACCEPT_HERO, site: "Acme" });

  const home = built.pages.find((page) => page.url === "/");
  assert.ok(home, `no page at /. wrote: ${built.pages.map((page) => page.url).join(" ")}`);
  assert.equal(home.type, "Page");
  assert.equal(home.data.title, "Acme");
  // No `content` and no `sections` on purpose: that is the layout's `{{#unless content}}` branch,
  // which since SH575 is the site's own navigation over `{{#query type="Page"}}`. So the page a
  // reader lands on lists every page of the replica, with no words invented on the source's behalf.
  assert.deepEqual(Object.keys(home.data), ["title"]);
  assert.deepEqual(built.home, { as: "page", lists: 3 });
  assert.equal(built.pages[0].url, "/", "and it sorts first, which is where composePages would "
    + "have put it — two runs over one capture must write the same document (P5)");
});

test("SH576: and the run says so, which is the half that was missing", () => {
  const built = buildConversion({ proposal: LOCALE_PAGES(), plan: ACCEPT_HERO, site: "Acme" });

  const report = describeConversion(built);

  // The defect was not only the absent page. Every page this run wrote is correct, so a report that
  // says what it wrote describes a complete success and produces a site with no root: the one page
  // here that the source did not have is the one the reviewer has to be told about (P7).
  assert.match(report, /the source answers nothing at \//);
  assert.match(report, /--home <path>/, "and the flag that chooses differently, because a refusal "
    + "would stop a conversion in which every page is right over a page the source never had — an "
    + "instruction is what P6 asks for instead");
});

test("SH576: --home makes one of the captured pages the front door, as a Redirect", () => {
  const built = buildConversion({
    proposal: LOCALE_PAGES(), plan: ACCEPT_HERO, site: "Acme", home: "en-us",
  });

  const home = built.pages.find((page) => page.url === "/");
  assert.equal(home.type, "Redirect", "SH385's post-type: a post whose url is the path it answers "
    + "and whose TO is where it sends the reader, resolved on the read that already ran — so a "
    + "front door needs no route and no rewrite rule");
  assert.equal(home.data.TO, "/en-us/",
    "the PAGE's own spelling, not the reviewer's: `--home en-us` and `/en-us` are the same request, "
    + "and a redirect differing from the friendly URL by one character is a redirect to nothing");
  assert.ok(!home.data.permanent, "not a 301 — a reviewer's first answer to \"which page is the "
    + "front door\" is one they may revise, and a permanent redirect is the one a browser caches "
    + "past the correction");
  assert.ok(built.extras.includes("Redirect"), "and the post-type has to exist on the target, the "
    + "same way Theme and SiteScripts do");
  assert.match(describeConversion(built), /\/ redirects to \/en-us\//);
});

test("SH576: --home naming a page the conversion does not write is refused, with the list", () => {
  assert.throws(
    () => buildConversion({
      proposal: LOCALE_PAGES(), plan: ACCEPT_HERO, site: "Acme", home: "/fr-fr/",
    }),
    /--home \/fr-fr\/ is not a page[\s\S]*allowed: \/en-us\/, \/pt-br\/[\s\S]*404 with an extra hop/,
  );
});

test("SH576: a source that answers / is untouched — no invented page, no note", () => {
  // The other side of the condition, and the one that must stay byte-identical: almost every source
  // has a home page, and a conversion that started writing a second one over it would be this defect
  // inverted, on every site instead of on the few.
  const built = buildConversion({
    proposal: composed([{ path: "/", title: "Home", blocks: ["hero1"] }], [candidate("hero1")]),
    plan: ACCEPT_HERO,
    site: "Acme",
  });

  assert.equal(built.pages.filter((page) => page.url === "/").length, 1);
  assert.equal(built.pages[0].data.title, "Home", "the source's own page, not one named after the site");
  assert.equal(built.home, undefined);
  assert.ok(!describeConversion(built).includes("answers nothing at /"));
});

/**
 * SH584 — a bound the run chose, reported as damage.
 *
 * `shio clone --depth 2` over `openviglet.github.io` recorded `unfollowed: 22`, the model
 * catalogue's vendor pages one level past the ceiling. Twelve minutes and three commands later
 * `shio verify` exited 1 with 22 `page-link-broken` errors, one per unfollowed path, on a replica
 * whose only other error was SH467's. Reading the run, twenty-three errors look like a broken
 * conversion; it took opening `source-inventory.json` and matching the lists one to one to learn
 * that twenty-two of them were a flag somebody chose.
 *
 * The instance cannot know this — it has never seen the capture — so the phase that says it is the
 * one already holding both halves. SH557's shape, second instance.
 */

const CAPTURE_WITH_CEILING = {
  unfollowed: [
    { path: "/vendors/acme", refs: 12 },
    { path: "/vendors/globex", refs: 8 },
    { path: "/vendors/initech?ref=nav", refs: 2 },
  ],
};

test("SH584: convert says the links a depth ceiling will make verify call broken", () => {
  const built = buildConversion({
    proposal: composed([{ path: "/", title: "Home", blocks: ["hero1"] }], [candidate("hero1")]),
    plan: ACCEPT_HERO,
    site: "Acme",
    inventory: CAPTURE_WITH_CEILING,
  });

  assert.deepEqual(built.unfollowed, {
    paths: 3,
    refs: 22,
    first: ["/vendors/acme", "/vendors/globex", "/vendors/initech"],
  }, "returned and not only printed — --json is a caller, and so is the corpus");

  const report = describeConversion(built);
  assert.match(report, /22 link\(s\).*point at 3 page\(s\).*did not follow/);
  assert.match(report, /page-link-broken/, "the rule name verify will use, so the two runs join");
  assert.match(report, /raise --depth/);
  assert.match(report, /shio verify --accept/);
  assert.match(report, /bound this run chose, not damage the replica caused/);
});

test("SH584: a target this run does write is not counted — it is a join, not a copy", () => {
  // Echoing `inventory.unfollowed` would report a page the replica serves as a page it does not.
  // The number printed has to be the one `verify` will produce, or it is worse than silence.
  const built = buildConversion({
    proposal: composed(
      [{ path: "/", title: "Home", blocks: ["hero1"] },
        { path: "/vendors/acme/", title: "Acme", blocks: ["hero1"] }],
      [candidate("hero1")],
    ),
    plan: ACCEPT_HERO,
    site: "Acme",
    inventory: CAPTURE_WITH_CEILING,
  });

  assert.equal(built.unfollowed.paths, 2, "a page written at /vendors/acme/ answers /vendors/acme");
  assert.equal(built.unfollowed.refs, 10);
  assert.ok(!built.unfollowed.first.includes("/vendors/acme"));
});

test("SH584: a capture with no ceiling says nothing, and a pre-SH387 one does not crash", () => {
  // The note has to be absent on the ordinary run, or it becomes the line everybody skips. And
  // `unfollowed` arrived in inventory version 2, so a capture on disk may simply not have it.
  for (const inventory of [{ unfollowed: [] }, {}, null, { unfollowed: "not a list" }]) {
    const built = buildConversion({
      proposal: composed([{ path: "/", title: "Home", blocks: ["hero1"] }], [candidate("hero1")]),
      plan: ACCEPT_HERO,
      site: "Acme",
      inventory,
    });
    assert.equal(built.unfollowed, undefined);
    assert.ok(!describeConversion(built).includes("did not follow"));
  }
});

// --- SH849: the report the run does not print ---

test("SH849: a run prints the palette it computed, and the markup runs it wrote", async () => {
  // The measurement: `shio convert --site VigR6 --create-site` wrote 65 objects, a `Theme`, a
  // `Theme (Shio)` and a `PageLayout` in **38 lines**, none of which named a colour, a role or a
  // token. Grepping that run for `palette`, `role`, `filled`, `defaulted` and `rejected` returned
  // nothing. Every assertion above this one calls `describeConversion(conversion)` with the
  // builder's own object, so all of them pass through the one join the command does not make -
  // which is why four tasks' worth of coverage could not see it. `report-wiring.test.mjs` holds
  // the rule; this holds the run.
  const styled = {
    "/": PAGE.replace(
      "<body>",
      "<body><style>:root{--ink:#10243a;--wash:#eef3f8;--brand:#0b7285}"
        + "body{color:var(--ink);background:var(--wash)}"
        + ".btn{background-color:var(--brand);color:var(--wash)}</style>",
    ),
  };
  const probe = ctxWith({ site: styled });
  const id = await idOf(probe);
  const ctx = ctxWith({ site: styled, plan: { accepted: [{ block: id, type: "SectionHero" }] } });

  await convert.run({ site: "Acme", out: "conversion.json" }, [], ctx);

  assert.match(ctx.stdout.text, /palette: \d+ of \d+ token\(s\) from the captured stylesheet/,
    "the count, which is the line a curator reads about appearance");
  assert.match(ctx.stdout.text, /--shio-color-ink #10243a from ink \(text#1\)/,
    "and it is traceable: the value, and the token of the source's own that it came from");
  assert.match(ctx.stdout.text, /--shio-color-accent #0b7285 from brand \(accent#1\)/);
  assert.match(ctx.stdout.text, /--shio-color-line [^\n]*the source states no border#1/,
    "including the slots that kept a default, since a reader cannot tell a colour that was matched "
      + "from one that was never looked for unless the run says which (P9)");
});

test("SH849: --out and a live apply print the same report, because they now pass the same object", async () => {
  // Two call sites, the same fourteen fields written out twice, one word apart. That is the other
  // half of how a field goes missing, and it is why both are now one `const` above them.
  const probe = ctxWith();
  const id = await idOf(probe);
  const plan = { accepted: [{ block: id, type: "SectionHero" }] };

  const out = ctxWith({ plan });
  await convert.run({ site: "Acme", out: "conversion.json" }, [], out);
  const wet = ctxWith({ plan });
  await convert.run({ site: "Acme" }, [], wet);

  // Each run appends its own tail - `document: …` for one, the uploads and the change count for
  // the other - so the property is that everything `describeConversion` wrote is identical, not
  // that the two streams are.
  const MARK = "every post lands in DRAFT";
  const report = (text) => text.slice(0, text.indexOf(MARK) + MARK.length);
  assert.ok(out.stdout.text.includes(MARK) && wet.stdout.text.includes(MARK));
  assert.equal(report(out.stdout.text), report(wet.stdout.text),
    "a reviewer reading an --out run and an operator reading the apply are looking at one report");
  assert.match(report(wet.stdout.text), /palette: \d+ of \d+ token\(s\)/,
    "and the palette is in it - the apply path is the one the measurement was taken on");
});
