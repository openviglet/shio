/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH379 — `shio propose`, the conversion proposal.
 *
 * Network-free and instance-free by construction, which is the point rather than a convenience: the
 * command reads the tree and only the tree, so a test is a fake filesystem holding a capture. What
 * that buys is the ability to pin the properties a proposal is worthless without — that a re-run
 * produces the same ids, that a block's loss is reported rather than assumed empty, that an
 * unmatched block becomes a proposed post-type instead of the nearest section, and that accepting
 * one candidate leaves every other one in fidelity mode.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { INVENTORY_FILE, MARKUP_DIR, markupFileFor, parseAttrs, planMarkup, stripNonMarkup, tokenize } from "../src/clone.mjs";
import {
  PLAN_FILE,
  PROPOSAL_FILE,
  blocksOf,
  buildProposal,
  buildTree,
  classify,
  describeProposal,
  fieldsFor,
  idFor,
  keepsFor,
  keptShare,
  lossFor,
  missingRequired,
  repeatedUnits,
  signature,
} from "../src/propose.mjs";
import { proposeScripts } from "../src/propose.mjs";
import { flatten } from "../src/theme.mjs";
import * as propose from "../src/commands/propose.mjs";
import { norm } from "./support/paths.mjs";

const HTML = { tokenize, parseAttrs, stripNonMarkup };

const tree = (html) => buildTree(stripNonMarkup(html), tokenize, parseAttrs);

// --- the tree ---

test("a stray close tag does not re-root the rest of the document", () => {
  // The commonest defect in hand-written markup, and the one whose damage is invisible: honouring
  // `</div>` too many closes the page container, so every later block becomes a top-level block and
  // the signatures stop describing what a reader sees.
  const root = tree("<main><div><p>one</p></div></div><section><p>two</p></section></main>");
  const blocks = blocksOf(root);

  assert.deepEqual(blocks.map((block) => block.tag), ["div", "section"]);
});

test("an unclosed <p> is closed by the block that follows it", () => {
  const root = tree("<main><section><p>one<p>two</section><section><p>three</p></section></main>");
  const blocks = blocksOf(root);

  assert.equal(blocks.length, 2);
  assert.equal(blocks[0].children.filter((child) => child.tag === "p").length, 2);
});

test("a void element never opens a scope", () => {
  const root = tree("<main><section><img src=a.png><p>after</p></section></main>");
  const [block] = blocksOf(root);

  // If <img> opened a scope the paragraph would be inside it, and the loss report would call the
  // paragraph consumed when the fields never saw it.
  assert.deepEqual(block.children.map((child) => child.tag), ["img", "p"]);
});

test("a lone <section> is one block, not its own children", () => {
  // Over-descent in the direction nobody notices: the candidate list still looks plausible, it is
  // just a list of headings and paragraphs instead of a list of blocks.
  assert.deepEqual(
    blocksOf(tree("<main><section><h1>Hi</h1><p>There</p></section></main>")).map((block) => block.tag),
    ["section"],
  );
});

test("single-child wrappers are descended through, so a themed page is not one block", () => {
  const root = tree(
    "<main><div class=container><div class=row>" +
      "<section><h2>a</h2></section><section><h2>b</h2></section>" +
      "</div></div></main>",
  );

  assert.deepEqual(blocksOf(root).map((block) => block.tag), ["section", "section"]);
});

test("SH425: a card grid inside a wrapper is the block, not two children of it", () => {
  // The measured case, from openviglet.github.io: one page, a hero and a two-card grid, and
  // `shio propose` reported `0 candidate block(s)` — emptiest exactly where it should be richest.
  // The walk went main > div.wrap > div.cards and stopped AT div.cards, returning its children. But
  // div.cards IS the block: a container whose repeated units are the cards, which `repeatedUnits`
  // would have classified as a SectionFeatureGrid.
  const root = tree(
    "<main><div class=wrap><div class=cards>" +
      "<div class=card><h3>Fast</h3><p>Very</p></div>" +
      "<div class=card><h3>Safe</h3><p>Quite</p></div>" +
      "</div></div></main>",
  );

  const blocks = blocksOf(root);
  assert.deepEqual(blocks.map((block) => block.attrs.class), ["cards"],
    "the container is the candidate; its cards are its repeated units, not blocks of their own");
  assert.equal(repeatedUnits(blocks[0]).length, 2);
  assert.equal(classify(blocks[0], { first: false }).type, "SectionFeatureGrid");
});

test("SH425: a link wrapping a heading and a paragraph is a block, not prose", () => {
  // The second mechanism, which alone was enough to produce zero: INLINE held `a`, and the
  // whole-card-is-a-link pattern wraps <h3> and <p> in <a class=card>. "Inline by HTML default" and
  // "cannot be a block" are not the same claim, and an anchor containing block content is neither
  // rare nor ambiguous.
  const root = tree(
    "<main><div class=cards>" +
      "<a class=card href=/one><h3>One</h3><p>First</p></a>" +
      "<a class=card href=/two><h3>Two</h3><p>Second</p></a>" +
      "</div></main>",
  );

  const blocks = blocksOf(root);
  assert.deepEqual(blocks.map((block) => block.attrs.class), ["cards"]);
  assert.equal(repeatedUnits(blocks[0]).length, 2, "the two link-wrapped cards are the units");
  assert.equal(classify(blocks[0], { first: false }).type, "SectionFeatureGrid");
  // And a link inside a sentence is still prose: the rule asks the node, not the tag.
  const prose = tree("<main><section><h2>Hi</h2><p>Read <a href=/more>more</a> about it.</p></section></main>");
  assert.deepEqual(blocksOf(prose).map((block) => block.tag), ["section"]);
});

test("SH425: a hero and a grid on one page are two candidates, which is what the page has", () => {
  // The whole point, stated as the number that was wrong. This is the shape of the page the defect
  // was measured on, and the failure had no symptom other than the count — a candidate list that
  // looks plausible is what let it ship.
  const root = tree(
    "<main><div class=wrap>" +
      "<section class=hero><h1>Shio</h1><p>An agent-native CMS.</p><a href=/start>Start</a></section>" +
      "<div class=cards>" +
        "<a class=card href=/a><h3>Model catalog</h3><p>Every model.</p></a>" +
        "<a class=card href=/b><h3>Benchmarks</h3><p>Every run.</p></a>" +
      "</div>" +
      "</div></main>",
  );

  const blocks = blocksOf(root);
  assert.equal(blocks.length, 2, "a hero and a grid — zero was the answer before SH425");
  assert.deepEqual(
    blocks.map((block, index) => classify(block, { first: index === 0 }).type),
    ["SectionHero", "SectionFeatureGrid"],
    "and both are classified — `first` comes from the caller, which is what makes the opening block "
      + "a hero rather than a call to action",
  );
});

test("SH426: a page's own header before main is a block, because it is content", () => {
  // The shape on openviglet.github.io: the site chrome is `div.topbar`, and the hero is
  // `header.hero-wrap` — an eyebrow, an h1, a lead and two CTA links — as a sibling just before
  // <main>. contentRoot narrows to <main> to skip site chrome, which is the right reason for the
  // wrong element here: <header> for introductory content is what the HTML spec is for.
  const root = tree(
    "<body><div class=topbar><nav><a href=/>Home</a></nav></div>" +
      "<header class=hero-wrap><p class=eyebrow>Open source</p><h1>Shio</h1>" +
        "<p>An agent-native CMS.</p><a href=/start>Start</a><a href=/docs>Docs</a></header>" +
      "<main><section class=cards><h2>Features</h2></section></main></body>",
  );

  const blocks = blocksOf(root);
  assert.deepEqual(
    blocks.map((block) => block.attrs.class),
    ["hero-wrap", "cards"],
    "the header comes first, so `first` reaches it — and `classify` carries a positional hero test "
      + "precisely so a hero is not filed as a CTA, which contentRoot guaranteed could never fire",
  );
  assert.equal(classify(blocks[0], { first: true }).type, "SectionHero");
  assert.ok(
    !blocks.some((block) => block.attrs.class === "topbar"),
    "and the site chrome is still not a block: it is a div before the header, not the header",
  );
});

test("SH426: the closest header wins, and a page with no header is unchanged", () => {
  const both = tree(
    "<body><header class=chrome><nav><a href=/>Home</a></nav></header>" +
      "<header class=hero><h1>Title</h1><p>Lead</p></header>" +
      "<main><section><h2>Body</h2></section></main></body>",
  );
  assert.deepEqual(
    blocksOf(both).map((block) => block.attrs.class ?? block.tag),
    ["hero", "section"],
    "two headers before main: the closest is the page's own, which is the one worth proposing",
  );

  const plain = tree("<body><header><nav><a href=/>Home</a></nav></header><main><section><h2>a</h2></section></main></body>");
  assert.deepEqual(
    blocksOf(plain).map((block) => block.tag),
    ["header", "section"],
    "a site whose chrome IS <header> yields it as ONE candidate — signatures are deduped, so it is "
      + "one decision on N pages — and repetition is what tells a reviewer which it is",
  );

  const nothing = tree("<main><section><h2>a</h2></section></main>");
  assert.deepEqual(blocksOf(nothing).map((block) => block.tag), ["section"]);
});

// --- signatures and recurrence ---

test("a signature ignores text and classes, so one hero on twelve pages is one candidate", () => {
  const a = tree("<main><section class='hero is-active'><h1>Buy this</h1><p>Now</p></section></main>");
  const b = tree("<main><section class=hero><h1>Buy that</h1><p>Later</p></section></main>");

  assert.equal(signature(blocksOf(a)[0]), signature(blocksOf(b)[0]));
  assert.equal(idFor(signature(blocksOf(a)[0])), idFor(signature(blocksOf(b)[0])));
});

test("two repeated units count as repetition, because a two-column grid is a real thing", () => {
  const root = tree(
    "<main><section><h2>What we do</h2>" +
      "<div><h3>Fast</h3><p>Very</p></div><div><h3>Safe</h3><p>Quite</p></div>" +
      "</section></main>",
  );

  assert.equal(repeatedUnits(blocksOf(root)[0]).length, 2);
});

// --- classification ---

const CASES = [
  [
    "SectionFaq",
    "<section><h2>Questions</h2><details><summary>a</summary><p>1</p></details>" +
      "<details><summary>b</summary><p>2</p></details></section>",
  ],
  [
    "SectionTestimonial",
    "<section><h2>Loved</h2><div><blockquote>Great</blockquote><cite>Ana</cite></div>" +
      "<div><blockquote>Good</blockquote><cite>Bo</cite></div></section>",
  ],
  [
    "SectionLogoWall",
    "<section><h2>Used by</h2><div><img src=a.png alt=A></div><div><img src=b.png alt=B></div>" +
      "<div><img src=c.png alt=C></div></section>",
  ],
  [
    "SectionFeatureGrid",
    "<section><h2>Features</h2><div><h3>Fast</h3><p>Very fast</p></div>" +
      "<div><h3>Safe</h3><p>Quite safe</p></div></section>",
  ],
  ["SectionCta", "<section><h2>Ready?</h2><p>Start now.</p><a href=/signup>Sign up</a></section>"],
  ["SectionRichText", "<section><h2>About</h2><p>One.</p><p>Two.</p><p>Three.</p></section>"],
];

for (const [expected, markup] of CASES) {
  test(`${expected} is what the rules pick for its own shape`, () => {
    const [block] = blocksOf(tree(`<main>${markup}</main>`));

    // `first: false` throughout: a hero is positional, and every one of these would also be a hero
    // if it happened to open the page. That overlap is why the rule order is part of the design.
    assert.equal(classify(block, { first: false })?.type, expected);
  });
}

test("a testimonial wall is not filed as a feature grid", () => {
  // The failure this ordering exists for: three blockquotes are also three repeated units with text,
  // so a grid-first rule set would classify them as a grid — and the fields are close enough in
  // shape that nothing downstream would notice the quotes had become feature bodies.
  const [block] = blocksOf(tree(
    "<main><section><div><blockquote>A</blockquote><cite>1</cite></div>" +
      "<div><blockquote>B</blockquote><cite>2</cite></div>" +
      "<div><blockquote>C</blockquote><cite>3</cite></div></section></main>",
  ));

  assert.equal(classify(block, { first: false }).type, "SectionTestimonial");
});

test("the first block with a heading is a hero, and the same block later is not", () => {
  const [block] = blocksOf(tree("<main><section><h1>Welcome</h1><p>To us</p><img src=a.png></section></main>"));

  assert.equal(classify(block, { first: true }).type, "SectionHero");
  assert.notEqual(classify(block, { first: false })?.type, "SectionHero");
});

test("a block the vocabulary has no type for classifies as nothing at all", () => {
  const [block] = blocksOf(tree("<main><section><video src=v.mp4></video></section></main>"));

  assert.equal(classify(block, { first: false }), null);
});

// --- fields and loss ---

test("the fields a mapping would carry come off the block, not out of a template", () => {
  const [block] = blocksOf(tree(
    "<main><section><h1>Ship faster</h1><p>The lead line.</p>" +
      "<a href=/signup>Start</a><img src=/hero.png></section></main>",
  ));
  const { fields } = fieldsFor("SectionHero", block);

  assert.equal(fields.heading, "Ship faster");
  assert.equal(fields.subheading, "The lead line.");
  assert.equal(fields.ctaLabel, "Start");
  assert.equal(fields.ctaHref, "/signup");
  assert.equal(fields.image, "/hero.png");
});

test("repeated units become the repeat field's rows", () => {
  const [block] = blocksOf(tree(
    "<main><section><h2>Features</h2>" +
      "<div><h3>Fast</h3><p>Very</p></div><div><h3>Safe</h3><p>Quite</p></div></section></main>",
  ));
  const { fields } = fieldsFor("SectionFeatureGrid", block);

  assert.deepEqual(fields.features, [
    { title: "Fast", body: "Very" },
    { title: "Safe", body: "Quite" },
  ]);
});

/**
 * SH476 — the measured case, and the round trip that says the escape happens exactly once.
 *
 * <p>The source spelled `rerank &amp; media models`, which a reader sees as `rerank & media models`.
 * The replica stored the five characters and the renderer escaped the `&` again — correctly, it is
 * emitting text into HTML — so the page read `rerank &amp; media models`. Neither half is wrong;
 * what was missing is the decode where markup becomes content, and it compounds, because each
 * re-capture escapes what the last one stored.</p>
 *
 * <p>The oracle is `escapeOnce`: what the renderer will do to a stored value. Applied to a field
 * that was decoded it reproduces the source's own bytes, and applied to one that was not it
 * produces the doubled form — so the assertion is the round trip rather than a spelling.</p>
 */
const escapeOnce = (value) => value.replace(/&/g, "&amp;").replace(/</g, "&lt;");

test("SH476: an entity is decoded when text becomes a field, so the render escapes it once", () => {
  const source = "rerank &amp; media models";
  const [block] = blocksOf(tree(
    `<main><section><h2>${source}</h2><p>Costs &lt; &pound;5 &mdash; &#8220;really&#8221;</p>`
      + "</section></main>",
  ));
  const { fields } = fieldsFor("SectionHero", block);

  assert.equal(fields.heading, "rerank & media models");
  assert.equal(
    escapeOnce(fields.heading),
    source,
    "the round trip: what the renderer emits for this stored value is what the source spelled. "
      + "Storing `&amp;` would make this `rerank &amp;amp; media models`, which is what a curator "
      + "saw and could not repair — the editor field held the entity too",
  );
  assert.equal(
    fields.subheading,
    "Costs < £5 — “really”",
    "named, numeric and the ones that are not `&amp;` — a decoder that only knew ampersands would "
      + "leave every dash and quote a designer typed",
  );
});

test("SH476: a source's own escaped entity survives, and an unknown name is left alone", () => {
  const [block] = blocksOf(tree(
    "<main><section><h2>Write &amp;lt;p&amp;gt; to mean a tag</h2>"
      + "<p>Angle &thetasym; and &#xZZ; stay</p></section></main>",
  ));
  const { fields } = fieldsFor("SectionHero", block);

  assert.equal(
    fields.heading,
    "Write &lt;p&gt; to mean a tag",
    "one pass, left to right — a second pass would turn the author's escaped entity into a tag "
      + "and lose the distinction they were drawing",
  );
  assert.equal(
    fields.subheading,
    "Angle &thetasym; and &#xZZ; stay",
    "an unknown name is a rendering imperfection a reader can report; dropping it is content that "
      + "vanished, and the table here is the ones a page actually spells rather than HTML5's 2500",
  );
});

test("the loss is the complement of what the fields took, so it cannot under-report", () => {
  // An <iframe> nothing consumed, an onclick, an id an anchor could target, and the classes: all four
  // are things a reviewer needs before they agree to this, and none of them is in a list of
  // known-lossy cases somebody has to remember to extend.
  const [block] = blocksOf(tree(
    "<main><section class='hero dark'><h1 id=top>Hi</h1><p>Lead</p>" +
      "<button onclick='go()'>Go</button><iframe src=/map></iframe></section></main>",
  ));
  const { consumed } = fieldsFor("SectionHero", block);
  const loss = lossFor(block, consumed);

  assert.ok(loss.elements.some((each) => each.name === "iframe"), "the iframe is content that disappears");
  assert.ok(loss.behaviour.some((each) => each.name === "button[onclick]"));
  assert.ok(loss.behaviour.some((each) => each.name === "h1[id]"));
  assert.equal(loss.classes, 2);
});

test("a consumed element is not reported as lost", () => {
  const [block] = blocksOf(tree("<main><section><h1>Hi</h1><p>Lead</p></section></main>"));
  const { consumed } = fieldsFor("SectionHero", block);

  assert.deepEqual(lossFor(block, consumed).elements, []);
});

// --- the proposal ---

const SITE = {
  "/": "<html><body><main>" +
    "<section class=hero><h1>Home</h1><p>Welcome.</p><a href=/signup>Start</a></section>" +
    "<section><h2>Features</h2><div><h3>Fast</h3><p>Very</p></div><div><h3>Safe</h3><p>Quite</p></div></section>" +
    "</main></body></html>",
  "/about": "<html><body><main>" +
    "<section class=hero><h1>About</h1><p>Who we are.</p><a href=/team>Team</a></section>" +
    "<section><video src=/v.mp4></video></section>" +
    "</main></body></html>",
};

function inventoryFor(paths = Object.keys(SITE)) {
  return {
    version: 1,
    source: { origin: "https://x.test", seed: "https://x.test" },
    crawl: { pages: paths.length, truncated: false, markup: true },
    pages: paths.map((path) => ({ path, title: path, markup: markupFileFor(path) })),
  };
}

function ctxWith({ inventory = inventoryFor(), plan = null, site = SITE } = {}) {
  const files = new Map();
  files.set(norm(`/repo/${INVENTORY_FILE}`), JSON.stringify(inventory));
  for (const [path, html] of Object.entries(site)) {
    files.set(norm(`/repo/${MARKUP_DIR}/${markupFileFor(path)}`), html);
  }
  if (plan) files.set(norm(`/repo/${PLAN_FILE}`), JSON.stringify(plan));
  const written = {};
  return {
    env: {},
    cwd: "/repo",
    stdout: sink(),
    stderr: sink(),
    written,
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

function sink() {
  const out = { text: "" };
  out.write = (chunk) => {
    out.text += chunk;
  };
  return out;
}

test("SH426: a candidate on every page read is named as what chrome looks like", async () => {
  // The other half of including a page's own header: the evidence that separates a hero from chrome
  // is repetition, and the capture already computes it. Three pages is the floor for saying so —
  // on two, a hero with one structure is on both as well, and a hint that fires on every small
  // capture is one a reviewer learns to ignore.
  const chrome = "<header><nav><a href=/>Home</a><a href=/about>About</a></nav></header>";
  const page = (heading) => `<html><body>${chrome}<main>`
    + `<section class=hero><h1>${heading}</h1><p>Words about ${heading}.</p></section>`
    + "</main></body></html>";
  const site = { "/": page("Home"), "/about": page("About"), "/pricing": page("Pricing") };
  const ctx = ctxWith({ inventory: inventoryFor(Object.keys(site)), site });

  const { proposal } = await propose.run({}, [], ctx);

  const header = proposal.candidates.find((row) => row.tag === "header");
  assert.ok(header, "the page's header is a candidate at all, which is SH426");
  assert.equal(header.pages.length, 3);
  assert.ok(
    proposal.notes.some((note) => /appear on all 3 pages read, which is what site chrome looks like/.test(note)),
    "and the recurrence is said out loud, because a reviewer reading a candidate list cannot see it",
  );
});

test("SH426: two pages are too few for the chrome hint, and it stays silent", async () => {
  const ctx = ctxWith();
  const { proposal } = await propose.run({}, [], ctx);

  assert.ok(!proposal.notes.some((note) => /site chrome looks like/.test(note)));
});

test("the proposal names each candidate, the pages it recurs on and what it would lose", async () => {
  const ctx = ctxWith();
  const { proposal } = await propose.run({}, [], ctx);

  const hero = proposal.candidates.find((row) => row.type === "SectionHero");
  assert.ok(hero, "the hero, which is the same structure on both pages, is one candidate");
  assert.deepEqual(hero.pages, ["/", "/about"]);
  assert.equal(hero.fields.heading, "Home");
  assert.equal(hero.mode, "fidelity");
  assert.ok(proposal.candidates.some((row) => row.type === "SectionFeatureGrid"));
  assert.ok(ctx.stdout.text.includes("discards:"));
});

test("SH511: the report splits what the mode discards from what it replaces", async () => {
  // `loses: 311 class name(s) — all styling` framed the appearance as damage a reviewer weighs, and
  // 311 dropped classes always sounds worse than none — so the line read as an argument for fidelity
  // on every candidate. Under SH507 it is the mode's contract, and a report that calls a decision a
  // loss asks the reader to prevent something nobody intends to prevent.
  const ctx = ctxWith();
  await propose.run({}, [], ctx);
  const text = ctx.stdout.text;

  assert.ok(!text.includes("loses:"), "one line said both things and they are two conversations");
  assert.ok(!text.includes("all styling"));
  assert.match(
    text, /^ {2}replaces: \d+ class name\(s\) of the source's styling$/m,
    "the count stays - a reviewer is entitled to know how much of the source's styling is being set "
      + "aside - and the label rather than a repeated sentence is what stops calling it a loss. The "
      + "sentence is true of the MODE and not of this block, so it is in the header once (P2, P3): "
      + "repeating it per candidate took the report from 74 to 120 tokens each",
  );
  assert.match(text, /^ {2}discards: /m, "and what is genuinely dropped keeps a line of its own");
  assert.ok(
    text.includes("fidelity keeps the source's appearance and offers no fields; authorable gives "
      + "these fields, presented by the site's own theme"),
    "the choice stated as two good answers rather than one with a penalty, which is what the loss "
      + "list was being read as",
  );
});

test("SH511: an empty half is omitted, and a candidate that loses nothing still says so", async () => {
  const ctx = ctxWith();
  const { proposal } = await propose.run({}, [], ctx);

  // The fixture's own shape is the assertion's premise: if every candidate declared classes, the
  // branch under test would never run and this would pass over nothing.
  const bare = proposal.candidates.filter((row) => !row.loss.classes);
  assert.ok(bare.length, "the fixture has to contain a block with no styling of its own");
  assert.ok(proposal.candidates.some((row) => row.loss.classes),
    "and one with styling, or `replaces` is never exercised");

  const replaces = [...ctx.stdout.text.matchAll(/^ {2}replaces: /gm)];
  assert.equal(
    replaces.length, proposal.candidates.length - bare.length,
    "a half with nothing in it prints no line at all: the split doubled the per-candidate cost, and "
      + "paying it to say twice that there is nothing to say is the part of that with no reader (P3)",
  );
  assert.ok(
    !/^ {2}(discards|replaces): *$/m.test(ctx.stdout.text),
    "and never an empty one, which is the same cost with a worse reading",
  );
  assert.match(
    ctx.stdout.text, /^ {2}discards: nothing this reader can see$/m,
    "but a candidate that loses nothing at all still says so - two absent lines would be "
      + "indistinguishable from an unreported candidate",
  );
});

test("SH436: the proposal states the fields the target has to declare, before anything is written", async () => {
  // The state a real instance was in: `Theme` declared TITLE, CSS and JAVASCRIPT and no TOKENS,
  // because the post-type predates SH195, `ensure` never touches an existing one, and reconcile is
  // additive AND opt-in. All three are right; the gap is when anybody learns. clone and propose never
  // talk to an instance by design, so without this the first moment a missing field matters is when a
  // token is written — after a capture, a conversion and a review have all been paid for.
  const ctx = ctxWithStyles();
  const { proposal } = await propose.run({}, [], ctx);

  const theme = proposal.requires.find((row) => row.postType === "Theme");
  assert.ok(theme, "the theme half knows what it needs");
  assert.equal(theme.field, "TOKENS");
  assert.match(theme.fix, /render\/post-types\/reconcile/,
    "and names the repair: a precondition a reader cannot satisfy is a complaint");
  assert.match(theme.fix, /drift/, "plus the read that answers it without writing");
  assert.match(ctx.stdout.text, /requires on the target instance/);
  assert.match(ctx.stdout.text, /Theme\.TOKENS/);
});

test("SH436: a proposal with no theme and no scripts requires nothing", async () => {
  // Derived from what the proposal actually holds, not listed: a capture that produced neither half
  // has no precondition, and a requirement that is always there is one nobody reads.
  const ctx = ctxWith();
  const { proposal } = await propose.run({}, [], ctx);

  assert.deepEqual(proposal.requires, []);
  assert.ok(!/requires on the target instance/.test(ctx.stdout.text));
});

test("it applies nothing: the only files it writes are the proposal and, on accept, the plan", async () => {
  const ctx = ctxWith();
  await propose.run({}, [], ctx);

  assert.deepEqual(Object.keys(ctx.written), [norm(`/repo/${PROPOSAL_FILE}`)]);
});

test("a block the vocabulary has no type for gets a post-type proposal, named from its own signal", async () => {
  const ctx = ctxWith();
  const { proposal } = await propose.run({}, [], ctx);

  const unmatched = proposal.candidates.find((row) => !row.type);
  assert.ok(unmatched, "the <video> block matches nothing, which is a real answer");
  assert.ok(unmatched.postType.name.startsWith("Block"));
  assert.ok(unmatched.postType.fields.some((field) => field.name === "embedUrl"));
});

test("two runs over unchanged markup produce identical bytes", async () => {
  const first = ctxWith();
  const second = ctxWith();
  await propose.run({}, [], first);
  await propose.run({}, [], second);

  // The property every acceptance depends on. A positional id would renumber the moment the source
  // added a block, and every entry in the plan would then name a candidate nobody reviewed.
  assert.equal(first.written[norm(`/repo/${PROPOSAL_FILE}`)], second.written[norm(`/repo/${PROPOSAL_FILE}`)]);
});

test("accepting one candidate moves that one to authorable and leaves the rest verbatim", async () => {
  const ctx = ctxWith();
  const { proposal } = await propose.run({}, [], ctx);
  const hero = proposal.candidates.find((row) => row.type === "SectionHero");

  const after = await propose.run({ accept: true, reason: "same on every page" }, [hero.id], ctx);

  const plan = JSON.parse(ctx.written[norm(`/repo/${PLAN_FILE}`)]);
  assert.deepEqual(plan.accepted, [{ block: hero.id, type: "SectionHero", reason: "same on every page" }]);
  assert.equal(after.proposal.accepted, 1);
  assert.equal(after.proposal.candidates.find((row) => row.id === hero.id).mode, "authorable");
  assert.ok(after.proposal.candidates.filter((row) => row.id !== hero.id).every((row) => row.mode === "fidelity"));
});

test("accepting the same candidate twice writes one entry", async () => {
  const ctx = ctxWith();
  const { proposal } = await propose.run({}, [], ctx);
  const hero = proposal.candidates.find((row) => row.type === "SectionHero");

  await propose.run({ accept: true }, [hero.id], ctx);
  await propose.run({ accept: true }, [hero.id], ctx);

  assert.equal(JSON.parse(ctx.written[norm(`/repo/${PLAN_FILE}`)]).accepted.length, 1);
});

test("--accept all takes every candidate the vocabulary matched, and no others", async () => {
  const ctx = ctxWith();
  const { proposal } = await propose.run({}, [], ctx);
  const matched = proposal.candidates.filter((row) => row.type).length;

  await propose.run({ accept: true }, ["all"], ctx);

  assert.equal(JSON.parse(ctx.written[norm(`/repo/${PLAN_FILE}`)]).accepted.length, matched);
});

test("an id from an older run is refused, with the ids this proposal holds", async () => {
  const ctx = ctxWith();
  await assert.rejects(
    () => propose.run({ accept: true }, ["bdeadbeef"], ctx),
    /no candidate bdeadbeef[\s\S]*The ids here are: b/,
  );
});

test("a candidate with no section type cannot be accepted as one", async () => {
  const ctx = ctxWith();
  const { proposal } = await propose.run({}, [], ctx);
  const unmatched = proposal.candidates.find((row) => !row.type);

  await assert.rejects(
    () => propose.run({ accept: true }, [unmatched.id], ctx),
    /matches no section type[\s\S]*needs a post-type of its own/,
  );
});

test("--accept with nothing to accept teaches instead of accepting everything", async () => {
  const ctx = ctxWith();
  await assert.rejects(() => propose.run({ accept: true }, [], ctx), /--accept needs the candidate/);
});

test("no capture at all is a teaching error naming the flag that fixes it", async () => {
  const ctx = ctxWith();
  ctx.fs.readFile = async (path) => {
    const error = new Error(`ENOENT: ${path}`);
    error.code = "ENOENT";
    throw error;
  };

  await assert.rejects(() => propose.run({}, [], ctx), /there is no capture[\s\S]*shio clone .* --markup/);
});

test("a page whose cached markup is gone is a counted note, not a failed run", async () => {
  const ctx = ctxWith({ inventory: inventoryFor(["/", "/about", "/pricing"]) });
  const { proposal, exitCode } = await propose.run({}, [], ctx);

  assert.equal(exitCode, 0);
  assert.deepEqual(proposal.read, ["/", "/about"]);
  assert.ok(proposal.notes.some((note) => /1 of 3 captured page\(s\) have no markup/.test(note)));
});

test("a partial capture says so, because a site-wide block would look page-local", () => {
  const proposal = buildProposal({
    inventory: { ...inventoryFor(["/"]), crawl: { pages: 1, truncated: true, markup: true } },
    markup: new Map([["/", SITE["/"]]]),
    html: HTML,
  });

  assert.ok(proposal.notes.some((note) => /PARTIAL/.test(note)));
});

test("the report declares its cut, so a truncated one cannot read as complete", () => {
  const proposal = buildProposal({
    inventory: inventoryFor(),
    markup: new Map(Object.entries(SITE)),
    html: HTML,
  });
  const report = describeProposal(proposal, { limit: 1 });

  assert.match(report, /more candidate\(s\) in shio\/\.shio\/conversion-proposal\.json/);
});

// --- SH380: the theme half of the same artefact ---

const STYLESHEET = `
  :root{--brand-primary:#0b5533;--space-4:16px}
  body{background:url(/img/bg.png)}
  h1{color:#0b5533}h2{color:#0b5533}h3{color:#0b5533}
`;

function withStyles(inventory = inventoryFor()) {
  return {
    ...inventory,
    assets: [
      { path: "/css/site.css", kind: "stylesheet", refs: 2, file: "Acme/css/site.css" },
      { path: "/img/bg.png", kind: "image", refs: 1, file: "Acme/img/bg.png" },
      { path: "https://cdn.test/f.woff2", kind: "font", refs: 1, external: true },
    ],
  };
}

function ctxWithStyles(inventory = withStyles()) {
  const ctx = ctxWith({ inventory });
  const readFile = ctx.fs.readFile;
  ctx.fs.readFile = async (path, encoding) => {
    if (norm(path).endsWith("shio/assets/Acme/css/site.css")) return STYLESHEET;
    return readFile(path, encoding);
  };
  return ctx;
}

test("the proposal carries the theme beside the candidates, in one artefact", async () => {
  const ctx = ctxWithStyles();
  const { proposal } = await propose.run({}, [], ctx);

  assert.ok(proposal.candidates.length, "the block half is still there");
  assert.deepEqual(proposal.theme.tokens.brand.primary, { $value: "#0b5533", $type: "color" });
  assert.ok(proposal.theme.rows.some((row) => row.source === "recurring" && row.value === "#0b5533"));
  assert.match(ctx.stdout.text, /^theme: 1 stylesheet\(s\)/m);
});

test("the TOKENS field is JSON ShThemeTokens can flatten, which is what SH195 requires of it", async () => {
  const ctx = ctxWithStyles();
  const { proposal } = await propose.run({}, [], ctx);

  // SH195's rule is load-bearing rather than tidy: a TOKENS field that is not JSON degrades the
  // render. Round-tripping it through the serializer is the cheapest proof that what lands in the
  // file is what a reader will parse.
  const written = JSON.parse(ctx.written[norm(`/repo/${PROPOSAL_FILE}`)]);
  assert.deepEqual(written.theme.tokens, proposal.theme.tokens);
  assert.ok(Object.keys(flatten(written.theme.tokens)).includes("brand.primary"));
});

test("a capture with stylesheets and no markup still yields a theme, not a refusal", async () => {
  // The state a caller lands in after `clone --assets --site X` without `--markup`. Refusing it would
  // withhold the theme over a flag that has nothing to do with the theme.
  const ctx = ctxWithStyles({ ...withStyles(), pages: [{ path: "/", title: "Home" }] });
  const { proposal, exitCode } = await propose.run({}, [], ctx);

  assert.equal(exitCode, 0);
  assert.deepEqual(proposal.candidates, []);
  assert.ok(proposal.theme.rows.length);
});

test("SH427: a palette declared in a <style> block is a theme, not silence", async () => {
  // The measured case: openviglet.github.io declares sixteen custom properties inside one <style>
  // block and links ZERO external stylesheets, and `shio propose` emitted `theme: null`, `notes: []`
  // and printed no theme section at all. `stripNonMarkup` removes those blocks — right for building
  // a tree, and the reason the theme phase could not see them. A declared custom property is the
  // token SH380 calls genuinely editable, so this is the strong half of the theme, not a leftover.
  const inline = `<html><head><style>
      :root { --bg: #0b0f14; --brand: #35d07f; --text: #e6edf3 }
      .hero { color: var(--text); background: var(--bg) url("img/hero.png") }
    </style></head><body><main>
      <section class=hero><h1>Shio</h1><p>An agent-native CMS.</p></section>
    </main></body></html>`;
  const ctx = ctxWith({
    inventory: { ...inventoryFor(["/"]), assets: [{ path: "/img/hero.png", kind: "image", refs: 1, file: "Acme/img/hero.png" }] },
    site: { "/": inline },
  });

  const { proposal } = await propose.run({}, [], ctx);

  assert.ok(proposal.theme, "a theme at all, which is SH427");
  const declared = proposal.theme.rows.filter((row) => row.source === "declared");
  assert.equal(declared.length, 3, "the page's own custom properties, which the source already var()s");
  assert.equal(proposal.theme.sheets.length, 1);
  assert.equal(proposal.theme.sheets[0].inline, true);
  assert.match(proposal.theme.sheets[0].path, /^\/ <style>$/, "labelled with the page it came from");
  assert.ok(
    proposal.theme.notes.some((note) => /every stylesheet read here is an inline <style> block/.test(note)),
    "and the report says where the CSS was: 16 tokens with no stylesheet in the tree is a state a "
      + "reader would otherwise try to explain",
  );
  assert.match(ctx.stdout.text, /^theme: 1 stylesheet\(s\)/m);
});

test("SH427: an inline url() resolves against the page, which is SH380's rule inverted", async () => {
  // The one thing that differs from an external sheet, and the commonest defect in anything that
  // touches CSS urls. `url(img/hero.png)` in a <style> block is /img/hero.png; the same text in
  // /assets/css/site.css would be /assets/css/img/hero.png, which names a file that does not exist.
  const inline = `<html><head><style>.hero { background: url("img/hero.png") }</style></head>`
    + "<body><main><section class=hero><h1>Hi</h1><p>There</p></section></main></body></html>";
  const ctx = ctxWith({
    inventory: { ...inventoryFor(["/"]), assets: [{ path: "/img/hero.png", kind: "image", refs: 1, file: "Acme/img/hero.png" }] },
    site: { "/": inline },
  });

  const { proposal } = await propose.run({}, [], ctx);

  assert.equal(proposal.theme.urls.rewritten, 1, "resolved against the document and found in the map");
  assert.deepEqual(proposal.theme.urls.unresolved, []);
  // SH461: found in the map, written as the SOURCE path. The map value (`Acme/img/hero.png`) is a
  // path in shio/assets/ and a browser cannot ask for it; `/img/hero.png` is what the instance
  // resolves, and what the markup beside it has named all along.
  assert.ok(proposal.theme.css.includes('url("/img/hero.png")'));
  assert.ok(!proposal.theme.css.includes("Acme/img/hero.png"));
});

test("SH427: a <style type> that is not CSS is skipped rather than parsed as CSS", async () => {
  const inline = '<html><head><style type="text/less">@brand: #35d07f; .hero { color: @brand }</style>'
    + "</head><body><main><section class=hero><h1>Hi</h1><p>There</p></section></main></body></html>";
  const ctx = ctxWith({ inventory: inventoryFor(["/"]), site: { "/": inline } });

  const { proposal } = await propose.run({}, [], ctx);

  assert.ok(!proposal.theme, "no sheet was read, so there is no theme to report — and guessing that "
    + "a preprocessor dialect is CSS is how a token list stops meaning anything");
});

test("neither markup nor a stylesheet is what the refusal is about, and it names both flags", async () => {
  // The failure this message exists for: an empty proposal reads as "this site has no convertible
  // blocks and no theme", which is a statement about the *site* rather than about the capture.
  const ctx = ctxWith({ inventory: { ...inventoryFor(), pages: [{ path: "/", title: "Home" }] }, site: {} });

  await assert.rejects(() => propose.run({}, [], ctx), /neither[\s\S]*--markup --assets --site/);
});

test("an off-origin stylesheet has no bytes in the tree, so it is not read", async () => {
  const ctx = ctxWithStyles({
    ...withStyles(),
    assets: [{ path: "https://cdn.test/site.css", kind: "stylesheet", refs: 1, external: true }],
  });
  const { proposal } = await propose.run({}, [], ctx);

  assert.equal(proposal.theme, undefined);
});

test("--min-refs is validated before anything is read, and says what it is for", async () => {
  const ctx = ctxWithStyles();

  await assert.rejects(() => propose.run({ minRefs: "1" }, [], ctx), /--min-refs takes a whole number of 2 or more/);
});

// --- the markup cache the proposal reads ---

test("--markup names a file per page, and two paths never share one", () => {
  const visited = [
    { url: "https://x.test/about-team", doc: {}, html: "<p>a</p>" },
    { url: "https://x.test/about/team", doc: {}, html: "<p>b</p>" },
  ];
  const { files, collisions } = planMarkup(visited);

  // Both slugify to `about-team`; the digest suffix is what keeps them apart, and it is
  // unconditional so a file's name never depends on which other pages were captured.
  assert.equal(collisions.length, 0);
  assert.equal(new Set(files.map((item) => item.file)).size, 2);
});

test("a page with no markup in hand gets no file, so the cache never lies about coverage", () => {
  const { files } = planMarkup([{ url: "https://x.test/", doc: {}, html: "<p>a</p>" }, { url: "https://x.test/x", doc: {} }]);

  assert.deepEqual(files.map((item) => item.path), ["/"]);
});

// --- SH389: the site-wide scripts a capture found and nothing could hold ---

const SCRIPTS = [
  { host: "cdn.cookiebot.com", refs: 12, sample: "/uc.js", async: true, defer: false },
  { host: "www.googletagmanager.com", refs: 12, sample: "/gtm.js?id=GTM-1", async: false, defer: true },
  { host: "widget.intercom.io", refs: 3, sample: "/widget/abc", async: false, defer: false },
  { host: "example-partner.test", refs: 1, sample: "/embed.js", async: false, defer: false },
];

test("SH389: a captured script becomes a SiteScripts entry, so a capture stops dropping it", () => {
  // Until there was somewhere to put these, a replica had no consent banner, no analytics and no
  // chat — subtly not the site, and nothing said so.
  const rows = proposeScripts({ scripts: SCRIPTS });

  assert.equal(rows.length, 4);
  assert.equal(rows[0].src, "https://cdn.cookiebot.com/uc.js");
  assert.equal(rows[1].defer, true);
  assert.equal(rows[0].async, true);
  assert.equal(rows[1].refs, 12);
});

test("SH389: every entry is proposed for the head, because moving one later breaks a consent order", () => {
  // A reviewer moving an entry down is a decision; this command proposing it is not.
  for (const row of proposeScripts({ scripts: SCRIPTS })) {
    assert.equal(row.position, "head");
  }
});

test("SH389: a category is recognised where the host says so, and blank where it does not", () => {
  const byHost = new Map(proposeScripts({ scripts: SCRIPTS })
    .map((row) => [row.src, row.category]));

  assert.equal(byHost.get("https://cdn.cookiebot.com/uc.js"), "consent");
  assert.equal(byHost.get("https://www.googletagmanager.com/gtm.js?id=GTM-1"), "analytics");
  assert.equal(byHost.get("https://widget.intercom.io/widget/abc"), "functional");
  // A guess presented as a fact would be worse than a blank on a consent gate.
  assert.equal(byHost.get("https://example-partner.test/embed.js"), "");
});

test("SH389: an absolute sample is kept as it is rather than rebuilt from the host", () => {
  const rows = proposeScripts({ scripts: [{ host: "x.test", refs: 1, sample: "https://y.test/a.js" }] });

  assert.equal(rows[0].src, "https://y.test/a.js");
});

test("SH389: a capture with no scripts proposes none, and the report says nothing about them", async () => {
  const ctx = ctxWith();
  const { proposal } = await propose.run({}, [], ctx);

  assert.deepEqual(proposal.scripts, []);
  assert.ok(!ctx.stdout.text.includes("site scripts:"));
});

test("SH389: the proposal carries them and the report counts the ones a reviewer must classify", async () => {
  const ctx = ctxWith({ inventory: { ...inventoryFor(), scripts: SCRIPTS } });
  const { proposal } = await propose.run({}, [], ctx);

  assert.equal(proposal.scripts.length, 4);
  assert.match(ctx.stdout.text, /site scripts: 4 third-party script\(s\)/);
  // The unclassified count is the only number here that names remaining work rather than a finding.
  assert.match(ctx.stdout.text, /1 with no category a reviewer has to set/);
  assert.match(ctx.stdout.text, /every entry is proposed for the head/);
});

// --- SH438: the axis a signature-keyed proposal could not have ---

test("SH438: the proposal records each page's blocks in source order, and its title", () => {
  // `candidates` is keyed by signature, which is what makes a block one decision on N pages instead
  // of N decisions — and is exactly why it can say *which* pages a block is on and never in what
  // order. Without this, the sections a page composes could only be ordered by id, an ordering the
  // source never had.
  const proposal = buildProposal({
    inventory: inventoryFor(),
    markup: new Map(Object.entries(SITE)),
    html: HTML,
  });

  assert.deepEqual(proposal.pages.map((page) => page.path), ["/", "/about"]);
  assert.deepEqual(proposal.pages.map((page) => page.title), ["/", "/about"]);
  for (const page of proposal.pages) {
    assert.equal(page.blocks.length, 2, "both top-level sections of the fixture page");
    for (const id of page.blocks) {
      assert.ok(
        proposal.candidates.some((row) => row.id === id),
        "every id resolves to a candidate, which is what lets `convert` turn the list into section URLs",
      );
    }
  }
  // The shared hero is one candidate on both pages, and first on each — the recurrence claim and the
  // ordering claim are about the same block, read two different ways.
  const hero = proposal.candidates.find((row) => row.pages.length === 2);
  assert.deepEqual(proposal.pages.map((page) => page.blocks[0]), [hero.id, hero.id]);
});

test("SH505: an overlay is chrome, so its text never becomes a section's field", () => {
  // The measurement: three candidates on three different pages proposed with the same `intro` —
  // "We respect your privacy", the cookie banner, which sits in `<div aria-live="polite"
  // class="fixed inset-x-0 bottom-0 z-50">` at the bottom of every page. Its first paragraph was
  // attributed to a SectionFaq on / and to a SectionFeatureGrid on three others, so a curator
  // opening the FAQ to edit "the intro" would have been editing a consent notice.
  const withBanner = `<html><body><main>
    <section class=hero><h1>Home</h1><p>Welcome.</p><a href=/go>Start</a></section>
    <div aria-live="polite" class="fixed inset-x-0 bottom-0 z-50 border-t bg-white p-4">
      <p>We respect your privacy. We use cookies to improve your experience.</p>
      <button>Accept</button>
    </div>
    </main></body></html>`;
  const proposal = buildProposal({
    inventory: inventoryFor(["/"]),
    markup: new Map([["/", withBanner]]),
    html: HTML,
  });

  assert.equal(proposal.pages[0].blocks.length, 1, "the hero, and the banner is not a block");
  assert.ok(
    !JSON.stringify(proposal.candidates).includes("respect your privacy"),
    "and no candidate carries a word of it — the value that was being adopted is the whole defect",
  );
});

test("SH505: each of the four signals marks an overlay on its own", () => {
  // `contentRoot` already excludes chrome and could not reach this one, because an overlay is
  // neither header nor footer. A capture has no computed style, so these are the evidence: any one
  // of them is the author saying the region is announced or painted rather than read in order.
  const overlays = [
    '<div class="fixed bottom-0"><p>Painted over the page below it.</p></div>',
    '<div style="position: fixed; bottom: 0"><p>Painted over the page below it.</p></div>',
    '<div aria-live="polite"><p>Announced rather than read in order.</p></div>',
    '<div role="dialog" aria-modal="true"><p>A modal, which is not this page.</p></div>',
  ];
  for (const overlay of overlays) {
    const page = `<html><body><main><section class=hero><h1>Home</h1><p>Copy.</p></section>${overlay}</main></body></html>`;
    assert.deepEqual(
      blocksOf(tree(page)).map((block) => block.tag),
      ["section"],
      `this one was proposed as content: ${overlay}`,
    );
  }
  const sticky = '<html><body><main><section class=hero><h1>Home</h1><p>Copy.</p></section>'
    + '<div class="sticky top-0"><h2>Contents</h2><p>Copy.</p></div></main></body></html>';
  assert.deepEqual(
    blocksOf(tree(sticky)).map((block) => block.tag),
    ["section", "div"],
    "and `sticky` is not in the set: a sticky sub-nav is IN FLOW and scrolls with the page until "
      + "it pins, so it is content in a way a consent banner is not",
  );
});

test("SH506: a block that is only a script is not a candidate, and the cut is stated", () => {
  // The measurement: one of six candidates on a real site was a `<script>` — `"signature":
  // "script"`, `"fields": {}`, a loss line reading `1×<script>` (the whole of the block), and a
  // proposed post-type `{"name":"Block1","fields":[]}`. It recurred on all six pages, which is what
  // promoted it: a block seen everywhere looks structural. A script is behaviour, and SH469 gave
  // behaviour its own list, so this is the block walker reaching a node the phase beside it owns.
  const page = `<html><body><main>
    <section class=hero><h1>Home</h1><p>Welcome.</p><a href=/go>Start</a></section>
    <script>window.dataLayer = [];</script>
    <noscript><p>Turn it on.</p></noscript>
    </main></body></html>`;
  const proposal = buildProposal({
    inventory: inventoryFor(["/"]),
    markup: new Map([["/", page]]),
    html: HTML,
  });

  assert.deepEqual(proposal.candidates.map((row) => row.tag), ["section"]);
  assert.ok(
    proposal.notes.some((note) => /not proposed as candidates/.test(note) && /script/.test(note)),
    "and the cut is stated: a shortened list of candidates reads exactly like a shorter list of "
      + `them, and six is few enough that a reader checks every row. Notes: ${JSON.stringify(proposal.notes)}`,
  );
});

test("SH506: a post-type with no derivable field is not proposed at all", () => {
  // The more general of the two rules, and the one that covers a case nobody has hit yet. A
  // fieldless post-type is not a conversion of anything: nothing can be authored in it, `verify`
  // would report an empty type, and the name is a counter — `Block1`, then `Block2` — so it says
  // nothing either. The block below is not a script and still yields no field.
  const bare = `<html><body><main>
    <section class=hero><h1>Home</h1><p>Welcome.</p><a href=/go>Start</a></section>
    <div class=spacer><span></span></div>
    </main></body></html>`;
  const proposal = buildProposal({
    inventory: inventoryFor(["/"]),
    markup: new Map([["/", bare]]),
    html: HTML,
  });

  const empty = proposal.candidates.find((row) => row.tag === "div");
  assert.ok(empty, "the block is still a candidate — it is content the vocabulary does not fit");
  assert.ok(
    !("postType" in empty),
    "but there is no post-type to offer, and an absent key is the honest form: `{fields: []}` "
      + "reads as an offer, and accepting it would write an empty type into the model",
  );
  assert.match(
    describeProposal(proposal),
    /no type/,
    "and the report says so rather than printing `NEW undefined`",
  );
});

test("SH505: a field value on candidates that share no page is reported, whatever produced it", () => {
  // The predicate above excludes the overlays this pass can recognise; the note reports the shape
  // itself, which is what makes the property hold on a site nobody has looked at. Same reasoning as
  // the `everywhere` note one level up — recurrence across pages is what chrome looks like — except
  // that a field is the unit a curator would actually edit.
  const banner = "<h2>Before you continue</h2><p>We use cookies to improve your experience here.</p>";
  const proposal = buildProposal({
    inventory: inventoryFor(["/", "/about"]),
    markup: new Map([
      ["/", `<html><body><main><section class=a><h1>Home</h1><p>Welcome.</p></section><section class=b>${banner}</section></main></body></html>`],
      ["/about", `<html><body><main><section class=c><h1>About</h1><p>Who.</p><a href=/x>Go</a></section><div class=d>${banner}</div></main></body></html>`],
    ]),
    html: HTML,
  });

  assert.ok(
    proposal.notes.some((note) => /share no page/.test(note) && /cookies/.test(note)),
    `the note names the value and the candidates carrying it. Notes: ${JSON.stringify(proposal.notes)}`,
  );
  // SH552 — and as data, because the reader that has to act on it is a command. The note is composed
  // from these rows, so the sentence a reviewer approves and the cut `convert` performs are one fact.
  const adopted = proposal.adopted ?? [];
  assert.deepEqual(
    adopted.map((row) => row.field).sort(),
    ["body", "heading"],
    `one row per (field, value) pair — the banner contributes two, and both are chrome: `
      + `${JSON.stringify(adopted)}`,
  );
  const body = adopted.find((row) => row.field === "body");
  assert.ok(
    body.value.includes("cookies to improve your experience"),
    "the full value and not `preview`'s 40-character truncation — `convert` matches on it, and a "
      + "truncated string matches nothing",
  );
  assert.equal(body.candidates.length, 2, "with both carriers, sorted");
  assert.ok(
    proposal.notes.some((note) => /shio convert/.test(note)),
    "and the note says which command performs the cut, so a reviewer reading it knows the banner "
      + "will not be written rather than that somebody should remember not to write it",
  );
});

/**
 * SH551 — a disclosure list converted without its labels.
 *
 * <p>`SectionFaq` is proposed on the evidence *two or more `<details>`, which is a disclosure list*,
 * and a `<details>` is exactly two parts. The extraction took the body and dropped the `<summary>`:
 * viglet.org's home page yielded seven rows, each with an `answer` and no `question`, so the section
 * rendered seven answers to nothing.</p>
 *
 * <p>Nothing reported it, and the reason is the template being careful — `sh-field` for the question
 * sits inside `{{#if question}}`, which is right, and the effect is that a field that is *never* filled
 * is indistinguishable from one a curator has not filled yet. The editor count agreed: ten annotated
 * props on that page, seven of them `questions.N.answer`.</p>
 */
test("SH551: a <details> list keeps its summaries, because that is where the question is", () => {
  const proposal = buildProposal({
    inventory: inventoryFor(["/"]),
    markup: new Map([["/", "<html><body><main><section class=faq><h2>Questions</h2>"
      + "<details><summary>Do you offer a trial?</summary><p>Yes, for thirty days.</p></details>"
      + "<details><summary>Where is my data held?</summary><p>In the region you pick.</p></details>"
      + "<details><summary>Can I cancel?</summary><p>At any time, from the console.</p></details>"
      + "</section></main></body></html>"]]),
    html: HTML,
  });

  const faq = proposal.candidates.find((row) => row.type === "SectionFaq");
  assert.ok(faq, `the disclosure list is proposed as a FAQ: ${JSON.stringify(proposal.candidates)}`);
  assert.equal(faq.fields.questions.length, 3, "one row per <details>");
  assert.deepEqual(
    faq.fields.questions.map((row) => row.question),
    ["Do you offer a trial?", "Where is my data held?", "Can I cancel?"],
    "the label a reader clicks is the question — before this every row carried an answer and no "
      + "question, and seven answers to nothing rendered as a section nobody could read",
  );
  assert.ok(
    faq.fields.questions.every((row) => row.answer),
    "and the answers are still there: the fix adds a source for the label, it does not move the body",
  );
  assert.ok(
    !proposal.notes.some((note) => /empty on every row/.test(note)),
    `a complete extraction says nothing: ${JSON.stringify(proposal.notes)}`,
  );
});

test("SH551: a row field the markup offered and no row carries is a note about this tool", () => {
  // The check that would have caught it. `<cite>` is present on every unit and empty, so `author` is
  // *offered* and produced on no row — which is the shape of the defect above, reachable without
  // deleting a rule from the source to prove it fires.
  const unit = (quote) => `<blockquote><p>${quote}</p></blockquote><cite></cite>`;
  const proposal = buildProposal({
    inventory: inventoryFor(["/"]),
    markup: new Map([["/", "<html><body><main><section class=quotes><h2>What they say</h2>"
      + `<div class=q>${unit("It shipped in a week and nothing broke.")}</div>`
      + `<div class=q>${unit("The migration was three commands and a coffee.")}</div>`
      + `<div class=q>${unit("Support answered before we finished the ticket.")}</div>`
      + "</section></main></body></html>"]]),
    html: HTML,
  });

  const note = proposal.notes.find((each) => /empty on every row/.test(each));
  assert.ok(note, `the finding is reported: ${JSON.stringify(proposal.notes)}`);
  assert.match(note, /\.author —/, "naming the type and the field");
  assert.match(
    note,
    /statement about this tool and not about the source/,
    "and saying whose problem it is: a section type whose rows all leave one field empty is a claim "
      + "about the extractor, and the template's own `{{#if}}` is what makes it look like a to-do",
  );
});

test("SH551: a field the markup never offered is not a finding, and `role` never is", () => {
  // The first version of this check reported a feature grid's `icon` on cards holding no `<img>` —
  // true, useless, and a note that fires on every healthy proposal. So the predicate is the pair:
  // empty on every row *and* offered by the markup.
  const card = (title, body) => `<div class=card><h3>${title}</h3><p>${body}</p></div>`;
  const proposal = buildProposal({
    inventory: inventoryFor(["/"]),
    markup: new Map([["/", "<html><body><main><section class=features><h2>What you get</h2>"
      + card("Fast", "Measured in milliseconds.")
      + card("Safe", "Audited every release.")
      + card("Simple", "One command to deploy.")
      + "</section></main></body></html>"]]),
    html: HTML,
  });

  const grid = proposal.candidates.find((row) => row.type === "SectionFeatureGrid");
  assert.ok(grid, "the cards are a feature grid");
  assert.ok(
    grid.fields.features.every((row) => !row.icon),
    "no card has an image, so no row has an icon — which is the source's shape and not a defect",
  );
  assert.ok(
    !proposal.notes.some((note) => /empty on every row/.test(note)),
    `so nothing is reported: ${JSON.stringify(proposal.notes)}`,
  );
});

test("SH551: `offered` is working state and never reaches the artefact", () => {
  // One note reads it, inside this module. A list on every candidate would be bytes every reader of
  // the proposal pays for and none reads — the per-row cost `propose-cost.test.mjs` measures.
  const proposal = buildProposal({
    inventory: inventoryFor(["/"]),
    markup: new Map([["/", "<html><body><main><section class=faq><h2>Questions</h2>"
      + "<details><summary>One?</summary><p>Yes it is.</p></details>"
      + "<details><summary>Two?</summary><p>Also yes.</p></details>"
      + "</section></main></body></html>"]]),
    html: HTML,
  });

  assert.ok(
    proposal.candidates.every((row) => !("offered" in row)),
    `no candidate carries it: ${JSON.stringify(proposal.candidates)}`,
  );
});

test("SH520: a block's span cuts that block's own bytes out of the source, comments and all", () => {
  // The property everything in SH520 rests on. `buildTree` reads `stripNonMarkup`'s output, so a
  // span means something in the *source* only because that pass blanks what it removes instead of
  // deleting it. The fixture below is built to break the naive version: a comment and a `<script>`
  // body sit inside the first block, so a deleting strip would put every later offset short by
  // their combined length and the second block's span would land mid-tag.
  const source = "<html><body><main>"
    + "<section class=hero><!-- a note nobody sees --><h1>Home</h1>"
    + "<script>var x = \"</section>\";</script><p>Welcome.</p><a href=/go>Start</a></section>"
    + "<section class=notes><h2>Notes</h2><p>Kept.</p></section>"
    + "</main></body></html>";
  const proposal = buildProposal({
    inventory: inventoryFor(["/"]),
    markup: new Map([["/", source]]),
    html: HTML,
  });

  const page = proposal.pages[0];
  assert.equal(page.blocks.length, 2);
  assert.deepEqual(
    page.spans.map(([start, end]) => source.slice(start, end)),
    [
      "<section class=hero><!-- a note nobody sees --><h1>Home</h1>"
        + "<script>var x = \"</section>\";</script><p>Welcome.</p><a href=/go>Start</a></section>",
      "<section class=notes><h2>Notes</h2><p>Kept.</p></section>",
    ],
    "each span is that block and exactly that block, taken from the bytes the capture wrote — a "
      + "slice, not a re-serialization, which is the difference SH449 spent a task on",
  );
});

test("SH438: a page with no cached markup is in `read` and in `pages` neither", () => {
  const proposal = buildProposal({
    inventory: inventoryFor(["/", "/about", "/pricing"]),
    markup: new Map(Object.entries(SITE)),
    html: HTML,
  });

  assert.deepEqual(proposal.read, proposal.pages.map((page) => page.path));
  assert.ok(!proposal.pages.some((page) => page.path === "/pricing"),
    "a page nothing was read from composes nothing, and claiming otherwise would put an empty Page "
      + "at a URL the replica cannot serve");
});

// --- SH580: one block a page, and the rest of the page ---

/**
 * The shape that produced the finding: a source whose body is one root `<div>`.
 *
 * <p>viglet.org is a React SPA, so `blocksOf`'s wrapper walk stops at the app root — its children are
 * repeated generic units, which is exactly the case SH425 taught it to treat as one block — and the
 * page yielded a single candidate whose span was the whole document. The cards below carry three
 * paragraphs each, and a `SectionFeatureGrid` row holds one, which is where the missing text goes.</p>
 */
const SPA = "<html><body><div id=root><div class=app>"
  + "<div class=card><h3>Fast</h3><p>Measured in milliseconds on every request we serve.</p>"
  + "<p>The second paragraph nobody has a field for, and it is most of the card.</p>"
  + "<p>A third one, longer still, describing the thing at the length a real page does.</p></div>"
  + "<div class=card><h3>Safe</h3><p>Audited every release by somebody who is not the author.</p>"
  + "<p>The second paragraph nobody has a field for, and it is most of this card too.</p>"
  + "<p>A third one, longer still, describing the thing at the length a real page does.</p></div>"
  + "</div></div></body></html>";

test("SH580: a block whose fields would drop most of its text is walked into, not accepted", () => {
  const blocks = blocksOf(tree(SPA));

  // Before the descent this was one block — `div.app`, the whole body — matching SectionFeatureGrid
  // and keeping a heading and one paragraph per card. The walk now hands back the cards, and each of
  // them is prose the vocabulary can hold whole.
  assert.deepEqual(blocks.map((block) => block.attrs.class), ["card", "card"]);
  for (const block of blocks) {
    const guess = classify(block, {});
    assert.equal(guess.type, "SectionRichText");
    assert.ok(keptShare(keepsFor(block, fieldsFor(guess.type, block).fields)) >= 90,
      "the block the walk stopped at keeps its own text");
  }
});

test("SH580: the descent has to keep more text, or the block stays whole", () => {
  // Two units of text no vocabulary type has a field for, so the parent keeps nothing — and so would
  // the children. A threshold alone would descend here and yield a candidate per <div>; the rule is a
  // comparison, so a split that keeps no more is not taken. This is over-descent in the direction
  // nobody notices: a longer candidate list reads as a more thorough one.
  const html = "<main><div class=wrap><div><span>Some text with no paragraph to live in.</span></div>"
    + "<div><span>And a second one exactly like it, so they read as repeated units.</span></div>"
    + "</div></main>";
  const blocks = blocksOf(tree(html));

  assert.deepEqual(blocks.map((block) => block.attrs.class), ["wrap"]);
});

test("SH580: every candidate carries what accepting it would keep, and the report prints it", () => {
  const proposal = buildProposal({
    inventory: inventoryFor(),
    markup: new Map(Object.entries(SITE)),
    html: HTML,
  });

  // The complement of `loss`, and a different unit on purpose: an element count says nothing about
  // how much of a page's reading survives, which is the fact SH577's text floor judges it on.
  for (const row of proposal.candidates) {
    assert.equal(typeof row.keeps.chars, "number");
    assert.equal(row.keeps.of, row.keeps.chars + (row.keeps.of - row.keeps.chars));
    assert.ok(row.keeps.chars <= row.keeps.of, "a block cannot keep more text than it has");
  }
  const report = describeProposal(proposal, {});
  assert.match(report, /keeps N% is how much of a block's own text/);
  assert.match(report, /^b[0-9a-f]{8} {2}SectionHero {2}\[fidelity] {2}keeps \d+% {2}on /m);
});

/**
 * The one the walk could not improve on: it keeps almost nothing, and `--accept all` would take it.
 *
 * <p>The heading is there so this stays a test about SH580's *score* — without one the candidate is
 * also SH582-incomplete, `--accept all` declines it, and the assertion below would be passing for the
 * other task's reason.</p>
 */
const THIN = "<main><div class=wrap><h2>Grid</h2>"
  + "<div><span>Some text with no paragraph to live in.</span></div>"
  + "<div><span>And a second one exactly like it, so they read as repeated units.</span></div>"
  + "</div></main>";

test("SH580: a candidate that would drop most of its text is a note, before anything is written", () => {
  const html = THIN;
  const proposal = buildProposal({
    inventory: inventoryFor(["/"]),
    markup: new Map([["/", html]]),
    html: HTML,
  });

  const note = proposal.notes.find((each) => /less than 50% of their\s+own text/.test(each));
  assert.ok(note, `no thin-candidate note in ${JSON.stringify(proposal.notes)}`);
  assert.match(note, /keeps \d+% of \d+ chars/);
});

test("SH580: accepting a thin candidate says so, because --accept all reads none of the proposal", async () => {
  const ctx = ctxWith({ inventory: inventoryFor(["/"]), site: { "/": THIN } });

  await propose.run({ accept: true }, ["all"], ctx);

  // Named, not refused: a thin block is still a decision a reviewer is entitled to make, and P7 asks
  // only that it not be a surprise.
  assert.match(ctx.stdout.text, /keeps \d+% of its \d+ characters of text — the rest has no field to live in/);
  assert.equal(JSON.parse(ctx.written[norm(`/repo/${PLAN_FILE}`)]).accepted.length, 1);
});

// --- SH582: an acceptance that writes what the gate will refuse ---

/**
 * A page whose second block matches a type and gives it no `heading`.
 *
 * <p>The measured shape: `propose --accept all` on `openviglet.github.io` accepted fifteen of
 * twenty-one candidates, three of them became sections with an empty `heading`, and `shio verify`
 * exited 1 with three `required-field-empty` errors on a replica that was otherwise the best the
 * chain had produced. The hero here fills what its type requires; the grid does not.</p>
 */
const NO_HEADING = "<html><body><main>"
  + "<section class=hero><h1>Home</h1><p>Welcome.</p><a href=/signup>Start</a></section>"
  + "<section class=cards><div><p>A card with prose and no heading at all.</p></div>"
  + "<div><p>A second card built exactly the same way, so they are units.</p></div></section>"
  + "</main></body></html>";

const incompleteIn = (proposal) => proposal.candidates.filter((row) => row.missing?.length);

test("SH582: a required field the derivation cannot fill is on the candidate, at proposal time", () => {
  const proposal = buildProposal({
    inventory: inventoryFor(["/"]),
    markup: new Map([["/", NO_HEADING]]),
    html: HTML,
  });

  // The proposal already held every fact needed to answer this — it lists the fields each candidate
  // would fill — so whether a derived set covers the type's required fields is one comparison.
  const grid = proposal.candidates.find((row) => row.type === "SectionFeatureGrid");
  assert.deepEqual(grid.missing, ["heading"]);
  assert.deepEqual(missingRequired("SectionFeatureGrid", grid.fields), ["heading"]);
  const hero = proposal.candidates.find((row) => row.type === "SectionHero");
  assert.equal(hero.missing, undefined,
    "absent rather than empty on a healthy candidate: nothing is missing, and a key saying so on "
      + "every row is bytes every reader pays for");
  assert.ok(proposal.notes.some((note) => /would leave a REQUIRED field empty/.test(note)));
  assert.match(describeProposal(proposal, {}), /missing \(required\): heading — --accept all declines this/);
});

test("SH582: --accept all declines it and says so, and the block stays verbatim", async () => {
  const ctx = ctxWith({ inventory: inventoryFor(["/"]), site: { "/": NO_HEADING } });
  const { proposal } = await propose.run({}, [], ctx);
  const grid = proposal.candidates.find((row) => row.type === "SectionFeatureGrid");

  const after = await propose.run({ accept: true }, ["all"], ctx);

  const plan = JSON.parse(ctx.written[norm(`/repo/${PLAN_FILE}`)]);
  assert.ok(!plan.accepted.some((entry) => entry.block === grid.id),
    "a conversion that writes content its own gate refuses is not a smaller conversion, it is the "
      + "wrong one — and an unaccepted block keeps its content as verbatim markup");
  assert.ok(plan.accepted.some((entry) => entry.type === "SectionHero"), "the rest is still accepted");
  assert.equal(after.proposal.candidates.find((row) => row.id === grid.id).mode, "fidelity");
  assert.match(ctx.stdout.text, /1 declined — a REQUIRED field the derivation could not fill/);
  assert.match(ctx.stdout.text, new RegExp(`${grid.id} SectionFeatureGrid has no heading`));
});

test("SH582: naming the id still accepts it, because that is a reviewer's decision", async () => {
  const ctx = ctxWith({ inventory: inventoryFor(["/"]), site: { "/": NO_HEADING } });
  const { proposal } = await propose.run({}, [], ctx);
  const grid = proposal.candidates.find((row) => row.type === "SectionFeatureGrid");

  const after = await propose.run({ accept: true }, [grid.id], ctx);

  // P7 asks that it not be a surprise, not that it be denied. The one thing it cannot do is happen
  // silently, which is what the three `required-field-empty` errors were.
  assert.equal(after.proposal.candidates.find((row) => row.id === grid.id).mode, "authorable");
  assert.match(
    ctx.stdout.text,
    new RegExp(`${grid.id} was accepted with no heading — shio verify will report required-field-empty`),
  );
});

test("SH582: a healthy proposal declines nothing and prints no line about it", async () => {
  const ctx = ctxWith();

  await propose.run({ accept: true }, ["all"], ctx);

  // The note and the per-candidate line both fire only where there is a finding, which is what keeps
  // them off the token budget of every working run (`propose-cost.test.mjs` measures exactly that).
  assert.ok(!/declined/.test(ctx.stdout.text));
  assert.ok(!/missing \(required\)/.test(ctx.stdout.text));
});
