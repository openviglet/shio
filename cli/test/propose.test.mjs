/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
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
  lossFor,
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
  assert.ok(ctx.stdout.text.includes("loses:"));
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
