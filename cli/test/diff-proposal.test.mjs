/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH522 — the second link of the chain: intent against artefact.
 *
 * `--against capture` judges the served page against what the source declared. This judges the
 * content the instance holds against what the plan said the conversion would make — a different
 * question, kept apart on purpose. A page that composes the right sections and serves them badly
 * is the capture comparison's finding, and merging the two would leave a reader unable to tell
 * "the conversion did not do what it promised" from "it did, and the layout is wrong".
 *
 * The hand-errors SH522 exists to end were both of this kind: zero broken references reported on a
 * page with a missing logo, and 106 editable fields counted on a page that had lost most of its
 * content. Both times the declaration was on disk and the artefact was one read away.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { compareProposal, describeProposalDiff } from "../src/diff.mjs";

const SECTIONS = "/sections";

const PROPOSAL = {
  candidates: [
    { id: "b1", type: "Hero", fields: ["HEADLINE", "CTA"] },
    { id: "b2", type: "Feature", fields: ["TITLE"] },
  ],
  pages: [
    { path: "/", title: "Home", blocks: ["b1", "b2"] },
    { path: "/about", title: "About", blocks: ["b1"] },
  ],
};

const PLAN = { accepted: [{ block: "b1", type: "Hero" }, { block: "b2", type: "Feature" }] };

const heldWith = (entries) => new Map(Object.entries(entries));

const compare = (held, plan = PLAN, proposal = PROPOSAL) =>
  compareProposal({ plan, proposal, held: heldWith(held), sectionsFolder: SECTIONS, pagesFolder: "" });

test("SH618: a conversion that did what it promised reports no break", () => {
  const result = compare({
    "/sections/b1": { data: { HEADLINE: "hi", CTA: "go" } },
    "/sections/b2": { data: { TITLE: "t" } },
    "/": { data: { sections: ["/sections/b1", "/sections/b2"] } },
    "/about": { data: { sections: ["/sections/b1"] } },
  });

  assert.equal(result.broken, 0);
  assert.match(describeProposalDiff(result), /matches the proposal/);
});

test("SH857: a collection every row of which is blank is not a promise the artefact broke", () => {
  // `convert` refuses to write such a row — the layout draws one card per row and a row with nothing
  // in it is a grey rectangle — so the proposal promises a field the correct artefact does not carry.
  // Before this, that read as `incomplete, missing features` against a conversion that was right.
  const proposal = {
    candidates: [{
      id: "b1",
      type: "FeatureGrid",
      fields: { heading: "Ask a question, get an answer", features: [{}, { title: "  " }] },
    }],
    pages: [{ path: "/", title: "Home", blocks: ["b1"] }],
  };
  const plan = { accepted: [{ block: "b1", type: "FeatureGrid" }] };
  const result = compare(
    {
      "/sections/b1": { data: { heading: "Ask a question, get an answer" } },
      "/": { data: { sections: ["/sections/b1"] } },
    },
    plan,
    proposal,
  );

  assert.equal(result.sections[0].status, "ok");
  assert.deepEqual(result.sections[0].missing, [],
    "a blank row is `{}` or a row whose every value is whitespace - the same test `convert` applies "
      + "before writing, asked of the promise so the two cannot disagree");
  assert.equal(result.broken, 0);
});

test("SH857: a collection with one row that says something is still promised", () => {
  const proposal = {
    candidates: [{
      id: "b1",
      type: "FeatureGrid",
      fields: { heading: "Features", features: [{}, { title: "Fast" }] },
    }],
    pages: [{ path: "/", title: "Home", blocks: ["b1"] }],
  };
  const result = compare(
    {
      "/sections/b1": { data: { heading: "Features" } },
      "/": { data: { sections: ["/sections/b1"] } },
    },
    { accepted: [{ block: "b1", type: "FeatureGrid" }] },
    proposal,
  );

  assert.deepEqual(result.sections[0].missing, ["features"],
    "the exemption is `every`, not `any`: one row with a value is a collection the artefact owes, "
      + "and widening it to `some` would stop scoring the case the check exists for");
});

test("SH618: a section the plan accepted and the write never made is named", () => {
  const result = compare({
    "/sections/b1": { data: { HEADLINE: "hi", CTA: "go" } },
    "/": { data: { sections: ["/sections/b1"] } },
    "/about": { data: { sections: ["/sections/b1"] } },
  });

  const b2 = result.sections.find((row) => row.url === "/sections/b2");
  assert.equal(b2.status, "absent");
  assert.deepEqual(b2.missing, ["TITLE"]);
  assert.equal(result.broken, 2, "the section is absent AND the page that composed it is short");
  assert.match(describeProposalDiff(result), /ABSENT {6}\/sections\/b2/);
});

test("SH618: a section that exists without the fields it promised is incomplete, not absent", () => {
  const result = compare({
    "/sections/b1": { data: { HEADLINE: "hi" } },
    "/sections/b2": { data: { TITLE: "t" } },
    "/": { data: { sections: ["/sections/b1", "/sections/b2"] } },
    "/about": { data: { sections: ["/sections/b1"] } },
  });

  const b1 = result.sections.find((row) => row.url === "/sections/b1");
  assert.equal(b1.status, "incomplete",
    "present-but-hollow is the shape the hand-count got wrong: 106 editable fields on a page that " +
      "had lost most of its content");
  assert.deepEqual(b1.missing, ["CTA"]);
  assert.match(describeProposalDiff(result), /missing: CTA/);
});

test("SH618: a field the artefact gained is reported and never counted as a failure", () => {
  const result = compare({
    "/sections/b1": { data: { HEADLINE: "hi", CTA: "go", title: "the post-type's own" } },
    "/sections/b2": { data: { TITLE: "t" } },
    "/": { data: { sections: ["/sections/b1", "/sections/b2"] } },
    "/about": { data: { sections: ["/sections/b1"] } },
  });

  assert.equal(result.broken, 0,
    "the write adds fields the post-type declares, and a gate that called that damage would fire " +
      "on every working conversion — compareCapture's rule, applied to the same question one link " +
      "up the chain");
  assert.deepEqual(result.sections.find((row) => row.url === "/sections/b1").extra, ["title"]);
  assert.match(describeProposalDiff(result), /1 field\(s\) the artefact carries beyond the proposal/);
});

test("SH618: a page that lost one of its sections is short, and says which", () => {
  const result = compare({
    "/sections/b1": { data: { HEADLINE: "hi", CTA: "go" } },
    "/sections/b2": { data: { TITLE: "t" } },
    "/": { data: { sections: ["/sections/b1"] } },
    "/about": { data: { sections: ["/sections/b1"] } },
  });

  const home = result.pages.find((row) => row.path === "/");
  assert.equal(home.status, "incomplete");
  assert.deepEqual(home.missing, ["/sections/b2"],
    "the section exists; the page stopped composing it — which the capture comparison would have " +
      "read as lost text and not as a composition the plan declared");
  assert.equal(result.broken, 1);
});

test("SH618: a plan accepting a block the proposal never declared is its own finding", () => {
  const result = compare(
    {
      "/sections/b1": { data: { HEADLINE: "hi", CTA: "go" } },
      "/sections/b2": { data: { TITLE: "t" } },
      "/": { data: { sections: ["/sections/b1", "/sections/b2"] } },
      "/about": { data: { sections: ["/sections/b1"] } },
    },
    { accepted: [...PLAN.accepted, { block: "ghost", type: "Hero" }] },
  );

  const ghost = result.sections.find((row) => row.url === "/sections/ghost");
  assert.equal(ghost.status, "undeclared",
    "the plan is a file a human may edit, and two declarations disagreeing with each other before " +
      "the artefact is involved is worth its own word");
  assert.equal(result.broken, 1);
});

test("SH618: only the pages the plan actually touched are judged", () => {
  const result = compare(
    { "/sections/b1": { data: { HEADLINE: "hi", CTA: "go" } }, "/": { data: { sections: ["/sections/b1"] } } },
    // b2 is dropped from the plan, so `/` composes one accepted block and `/about` still composes
    // b1 — but a page whose blocks were ALL declined has nothing this comparison can claim.
    { accepted: [{ block: "b1", type: "Hero" }] },
  );

  assert.deepEqual(result.pages.map((row) => row.path), ["/", "/about"],
    "both compose b1, so both are judged; a page composing only declined blocks is skipped, "
      + "because the proposal promised nothing about it and an empty claim is not a clean one");
  assert.equal(result.pages.find((row) => row.path === "/about").status, "absent",
    "a page the proposal composed and the instance does not hold is absent, not silently skipped");
  assert.ok(result.sections.every((row) => row.url !== "/sections/b2"),
    "and a declined block is not expected to exist");
});
