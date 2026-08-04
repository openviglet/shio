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

test("a capture taken without --markup is refused by name, not by an empty proposal", async () => {
  // The failure this message exists for: an empty candidate list reads as "this site has no
  // convertible blocks", which is a statement about the site rather than about the capture.
  const ctx = ctxWith({
    inventory: { ...inventoryFor(), pages: [{ path: "/", title: "Home" }] },
    site: {},
  });

  await assert.rejects(() => propose.run({}, [], ctx), /none of them has markup[\s\S]*--markup/);
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
