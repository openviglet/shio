/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH379 — what a conversion proposal costs to read, and why recurrence is what makes it affordable.
 *
 * WHY THIS FILE EXISTS
 * The proposal's whole design claim is that a candidate is a **signature, not a block**: the same
 * hero on sixty pages is one decision to review. That is an argument about cost, and "measure before
 * you fix, and the measurement is the first deliverable" applies to it exactly as it applied to
 * SH391's screenshot ratio. The alternative — one entry per block per page — is what an
 * implementation naturally produces, it reads as more thorough, and on a real site it is the
 * difference between a report an agent can hold and one it must page through. So the ratio is
 * asserted, and it prints on every run: a regression here is a red build rather than a file quietly
 * getting fatter.
 *
 * WHAT IS COUNTED
 * Characters of the report, at the project's conventional 4-chars-per-token ratio
 * (`ShTokenBudget.CHARS_PER_TOKEN`), so these numbers are comparable with every `estTokens` on the
 * wire. Not milliseconds — a timing ceiling on a shared machine is a flake (SH250).
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { parseAttrs, stripNonMarkup, tokenize } from "../src/clone.mjs";
import { buildProposal, describeProposal } from "../src/propose.mjs";

const CHARS_PER_TOKEN = 4;
const tokens = (text) => Math.ceil(text.length / CHARS_PER_TOKEN);
const HTML = { tokenize, parseAttrs, stripNonMarkup };

/**
 * A marketing site of `pages` pages: the same header, hero, feature grid and CTA on every one, plus
 * one page-specific prose block each. Which is what a real replication target looks like, and the
 * reason the numbers below are the ones that matter.
 */
function site(pages) {
  const out = {};
  for (let index = 0; index < pages; index += 1) {
    const path = index === 0 ? "/" : `/page-${index}`;
    out[path] = `<html><body><main>
      <section class="hero dark"><h1>Headline ${index}</h1><p>A supporting line for page ${index}.</p>
        <a class="btn" href="/signup">Get started</a></section>
      <section class="features"><h2>What you get</h2>
        <div class="card"><h3>Fast</h3><p>Measured in milliseconds.</p></div>
        <div class="card"><h3>Safe</h3><p>Audited every release.</p></div>
        <div class="card"><h3>Simple</h3><p>One command to deploy.</p></div></section>
      <section class="prose"><h2>Page ${index} detail</h2><p>Body copy unique to this page.</p>
        <p>A second paragraph of it.</p></section>
      <section class="cta"><h2>Ready?</h2><p>Start now.</p><a href="/trial">Free trial</a></section>
    </main></body></html>`;
  }
  return out;
}

function proposalFor(pages) {
  const markup = new Map(Object.entries(site(pages)));
  return buildProposal({
    inventory: {
      source: { origin: "https://x.test", seed: "https://x.test" },
      crawl: { pages, truncated: false, markup: true },
      pages: [...markup.keys()].map((path) => ({ path, markup: `${path}.html` })),
    },
    markup,
    html: HTML,
  });
}

test("recurrence is what bounds the report: sixty pages cost barely more than four", () => {
  const small = proposalFor(4);
  const large = proposalFor(60);
  const smallCost = tokens(describeProposal(small, { limit: 100 }));
  const largeCost = tokens(describeProposal(large, { limit: 100 }));

  console.log(
    `SH379 4 pages: ${small.candidates.length} candidates ≈ ${smallCost} tokens; ` +
      `60 pages: ${large.candidates.length} candidates ≈ ${largeCost} tokens`,
  );

  // The claim the design rests on. Sixty pages of the same four blocks is four decisions plus the
  // page list each recurs on — so the growth is a path per page, not a candidate per page. A
  // per-block implementation would be 240 candidates here and roughly sixty times this number.
  assert.equal(small.candidates.length, large.candidates.length);
  assert.ok(
    largeCost < smallCost * 4,
    `60 pages cost ${largeCost} tokens against 4 pages' ${smallCost} — recurrence is not folding`,
  );
});

test("a candidate's marginal cost is a few dozen tokens, loss report included", () => {
  const proposal = proposalFor(8);
  const cost = tokens(describeProposal(proposal, { limit: 100 }));
  const perCandidate = cost / proposal.candidates.length;

  console.log(
    `SH379 marginal cost: ≈${perCandidate.toFixed(1)} tokens per candidate ` +
      `(${proposal.candidates.length} candidates, ${cost} tokens)`,
  );

  // Most of it is the field previews and the loss line, both of which are the *source's* choices —
  // a block with forty class names costs more to report than one with two, and that is the honest
  // answer rather than something to trim.
  assert.ok(perCandidate <= 120, `each candidate costs ≈${perCandidate.toFixed(1)} tokens`);
});

test("--limit bounds the read, and the header still covers the whole run", () => {
  const proposal = proposalFor(30);
  const full = tokens(describeProposal(proposal, { limit: 1000 }));
  const bounded = tokens(describeProposal(proposal, { limit: 2 }));

  console.log(`SH379 ${proposal.candidates.length} candidates: ${full} tokens unbounded, ${bounded} with --limit 2`);

  assert.ok(bounded < full);
  // The count in the first line is the whole run's, cut or not — a bounded report that reported a
  // bounded count would let a reviewer believe they had seen every block about to change.
  assert.match(describeProposal(proposal, { limit: 2 }), new RegExp(`^shio propose: ${proposal.candidates.length} candidate`));
});
