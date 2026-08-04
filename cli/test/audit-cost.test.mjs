/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH391 — what an audit costs an agent, against what a screenshot costs it.
 *
 * WHY THIS FILE EXISTS
 * The task's own argument is a pair of numbers: a full-page PNG is ~1.6k tokens after downscaling,
 * twenty findings are a few hundred. "Measure before you fix, and the measurement is the first
 * deliverable" — a cost that is argued rather than measured fails quietly in somebody else's
 * environment, and the argument for a *third* browser command is entirely this ratio. So it is
 * asserted, and it prints on every run like the SH213 inventory sweep, so a regression is a red
 * build rather than a report nobody notices getting fatter.
 *
 * WHAT IS COUNTED
 * Characters of the report, converted at the project's conventional 4-chars-per-token ratio
 * (`ShTokenBudget.CHARS_PER_TOKEN`) — the same heuristic every `estTokens` on the wire uses, so
 * these numbers are comparable with the budget the agent surface reports. Not milliseconds: a
 * timing ceiling on a shared machine is a flake (SH250), and latency is not the cost this command
 * exists to beat.
 *
 * THE SCREENSHOT BASELINE
 * 1600 tokens, the figure §IX.11 cites for a downscaled full-page capture handed to a model. It is
 * a constant here rather than a measurement because measuring it would mean launching a browser in
 * a unit test; what matters is that it is the number the design decision was made against, written
 * where the comparison is made.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { classify, describe as describeReport } from "../src/audit.mjs";

/** The conventional ratio — `ShTokenBudget.CHARS_PER_TOKEN`, so these agree with `estTokens`. */
const CHARS_PER_TOKEN = 4;
const tokens = (text) => Math.ceil(text.length / CHARS_PER_TOKEN);

/** §IX.11's figure for one downscaled full-page PNG handed to a model. */
const SCREENSHOT_TOKENS = 1600;

/**
 * A realistic mixed page: things that overflow, text that does not clear AA, a broken asset, a
 * thrown exception, a collapsed row, a 0×0 image and a webfont that never arrived — observed at
 * both default widths, which is what the merge has to survive.
 */
function observed(count) {
  const share = Math.max(Math.floor(count / 5), 1);
  const at = (width) => ({
    width,
    overflow: Array.from({ length: share }, (_, index) => ({
      selector: `section.features > div.card:nth-of-type(${index + 1}) > figure.media`,
      side: "right",
      px: 24 + index * 7 + (width < 800 ? 180 : 0),
    })),
    text: Array.from({ length: share }, (_, index) => ({
      selector: `footer.site-footer > nav.links > a:nth-of-type(${index + 1})`,
      color: "rgb(153, 153, 153)",
      background: "rgb(255, 255, 255)",
      fontSize: 14,
      bold: false,
      sample: "Terms and conditions apply to every",
    })),
    collapsed: Array.from({ length: share }, (_, index) => ({
      selector: `main > div.row:nth-of-type(${index + 1})`,
      children: 3,
    })),
    zeroSize: Array.from({ length: share }, (_, index) => ({
      selector: `header.masthead > img.logo:nth-of-type(${index + 1})`,
      width: 0,
      height: 0,
      kind: "img",
    })),
    fonts: [{ family: "Inter Variable", loaded: false, source: "font-face" }],
  });
  return {
    probes: [at(1280), at(375)],
    requests: Array.from({ length: Math.max(share - 1, 0) }, (_, index) => ({
      url: `https://blog.example.com/assets/hero-${index}.avif`,
      status: 404,
      width: 1280,
    })),
    consoleErrors: [{ text: "TypeError: window.carousel is not a function", width: 1280 }],
  };
}

function report(label, count) {
  const { findings, counts, capped } = classify(observed(count));
  const text = describeReport({
    target: "post:Blog/blog/hello-world",
    url: "https://blog.example.com/blog/hello-world",
    widths: [1280, 375],
    findings,
    counts,
    capped,
  });
  const cost = tokens(text);
  // eslint-disable-next-line no-console
  console.log(
    `SH391 ${label}: ${counts.total} finding(s) (${counts.errors} error, ${counts.warnings} warn) ` +
      `= ${text.length} chars ≈ ${cost} tokens ` +
      `(a full-page PNG ≈ ${SCREENSHOT_TOKENS}; ratio ${(SCREENSHOT_TOKENS / cost).toFixed(1)}×)`,
  );
  return { cost, counts, text };
}

test("SH391: twenty findings cost a fraction of one screenshot", () => {
  const { cost, counts } = report("a page with ~20 findings", 20);

  assert.ok(counts.total >= 20, `the fixture produced ${counts.total} findings`);
  // The claim the task is built on. Generous against the estimate on purpose — what would falsify
  // the design is this creeping toward the PNG, not the exact figure.
  assert.ok(
    cost <= SCREENSHOT_TOKENS / 2,
    `${counts.total} findings cost ${cost} tokens against a screenshot's ${SCREENSHOT_TOKENS}`,
  );
});

test("SH391: a clean page is nearly free, which is the common case in a loop", () => {
  const { findings, counts, capped } = classify({ probes: [], requests: [], consoleErrors: [] });
  const text = describeReport({
    target: "post:Blog/blog/hello-world",
    url: "https://blog.example.com/blog/hello-world",
    widths: [1280, 375],
    findings,
    counts,
    capped,
  });
  const cost = tokens(text);
  // eslint-disable-next-line no-console
  console.log(`SH391 a clean page: ${text.length} chars ≈ ${cost} tokens`);

  // An agent editing a page runs this after each change, so the *passing* run is the one paid
  // most often — and a screenshot costs the same 1.6k whether anything is wrong or not.
  assert.ok(cost <= 60, `a clean page cost ${cost} tokens`);
});

test("SH391: the per-finding cost is what bounds a bad page, so it is asserted too", () => {
  const small = report("a page with ~5 findings", 5);
  const large = report("a page with ~50 findings", 50);
  const perFinding = (large.cost - small.cost) / (large.counts.total - small.counts.total);

  // eslint-disable-next-line no-console
  console.log(`SH391 marginal cost: ≈${perFinding.toFixed(1)} tokens per finding`);
  // One line, one selector, one number. A finding that grows past this is a finding that started
  // explaining itself, and prose belongs in the docs the selector points a reader at. The ceiling
  // is not tighter because most of a line is the **selector**, whose length is the audited page's
  // choice and not this report's — the fixture's are deliberately deep for that reason.
  assert.ok(perFinding <= 35, `each finding costs ≈${perFinding.toFixed(1)} tokens`);
});

test("SH391: --limit is what keeps a catastrophic page from costing more than a picture", () => {
  const { findings, counts, capped } = classify(observed(200));
  const full = tokens(
    describeReport({ target: "t", url: "u", widths: [1280, 375], findings, counts, capped }),
  );
  const bounded = tokens(
    describeReport({ target: "t", url: "u", widths: [1280, 375], findings, counts, capped }, { limit: 20 }),
  );
  // eslint-disable-next-line no-console
  console.log(
    `SH391 ${counts.total} findings: ${full} tokens unbounded, ${bounded} with --limit 20`,
  );

  // The honest half of this: a page with hundreds of defects *does* cost more than a photograph of
  // it, and the flag is the answer rather than a silent cut — the count in the header still covers
  // the whole run, so nobody reads a bounded report as a clean one.
  assert.ok(full > SCREENSHOT_TOKENS, "the unbounded report on a catastrophic page is genuinely large");
  assert.ok(bounded <= SCREENSHOT_TOKENS / 2, `--limit 20 cost ${bounded} tokens`);
});
