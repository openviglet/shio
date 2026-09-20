/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH769 — a claim that a capability is absent, over a ledger that says it shipped.
 *
 * <p>Two turned up in one session, and only one had an instrument. The first was caught:
 * `improvements-debt.test.mjs` failed on an anchor a rationale cited and a ship had removed. The
 * second was not caught at all and was found by reading. `docs/agents/agent-surface.md` said, of
 * the regions reader:</p>
 *
 *     `styled by` — naming the theme in force per region, and warning when two are — is
 *     deliberately not here: it needs class-to-stylesheet provenance, which `definedIn` does not
 *     keep.
 *
 * <p>SH694 kept the provenance and shipped the warning. The sentence sat <b>four lines above</b>
 * the section documenting that, and had been false since SH694 landed. A reader deciding whether to
 * build the feature would have believed it.</p>
 *
 * <h2>Why this is a signed list and not a rule</h2>
 *
 * <p>The obvious rule — an absence claim naming a shipped id is stale — was measured first and is
 * noise: 98 sentences in these docs assert an absence, 38 of them beside a shipped id, and almost
 * every one is <b>true</b>. "There is no submissions subsystem", "`{{#if_eq}}` does not exist".
 * A check that reported those would be read once and then ignored, which is the failure mode SH551
 * and SH142 both name.</p>
 *
 * <p>What is left after narrowing to the phrasing that claims something about <em>this product,
 * now</em> — `is deliberately not here`, `is not part of`, `is not yet`, `is not available` — is
 * eight sentences. That is a population somebody can sign, and signing it is the whole
 * instrument:
 * a new claim cannot be written without a sentence saying what would make it false, and a claim
 * whose prose changed has to be re-read by whoever changed it.</p>
 *
 * <h2>What it cannot do, said out loud</h2>
 *
 * <p>It cannot catch the <b>ship</b> that falsifies a standing claim, which is the half that cost
 * SH769. `styled by` was not findable by text: the ledger entry for SH694 does not contain the
 * phrase, and the sentence's own reasoning was about a Java method's behaviour. Nothing short of
 * reading finds that, and the value here is that the reading now has a list to start from and a
 * reason written beside each entry to check it against.</p>
 */
// @scans prose reason: the subject IS the prose — a sentence in a Markdown document asserting that
// something is absent, which is the thing being classified. There is no code here to strip, and
// stripping comments would be reading the wrong half of the file.
import assert from "node:assert/strict";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";

import { unquoted } from "./support/source-text.mjs";

const ROOT = new URL("../../", import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1");

/** The trees whose prose a reader takes as current fact about the product. */
const TREES = ["docs/agents", "docs/specs"];
const FILES = ["agents.md"];

/**
 * Present-tense claims that something is absent from this product.
 *
 * <p>Deliberately not `does not exist` or `there is no`: those are overwhelmingly statements about
 * a design — "there is no layout engine", "`{{#if_eq}}` does not exist" — and including them was
 * measured at 98 sentences, of which the ones this exists to find are a handful. The forms kept are
 * the ones that read as <em>a decision about scope that could be revisited</em>, which is exactly
 * the sentence a later ship makes false.</p>
 */
const ABSENT =
  /\b(?:is|are)\s+(?:deliberately\s+|intentionally\s+)?not\s+(?:here|yet\b|part of|covered|available|supported|implemented)\b/i;

/**
 * The sentence with everything it is <em>quoting</em> removed, which is SH740's rule one level up.
 *
 * <p>Found by writing this: the section of `docs/agents/docs-process.md` that documents SH769 both
 * quotes the sentence that cost the task and lists the phrasings this matches on, and a scan over
 * raw text read a page about the rule as two breaches of it. A phrase inside backticks or inside
 * quotation marks is being <b>named</b>, not asserted — which is exactly the distinction SH740 draws
 * between a rule and a sentence about the rule, and the reason its own scanners strip comments
 * before matching.</p>
 *
 * <h2>Why this is not `unquoted`, which does the same three replacements (SH822)</h2>
 *
 * <p>It looks like a fourth copy of the shared stripper and it is not one: the two take
 * <b>different input</b> and differ in exactly the place that matters. `unquoted` reads a whole
 * markdown <i>document</i> line by line, so its patterns are `"[^"\n]*"` — a stray quotation mark
 * must not run away down the page. This reads one <i>sentence</i>, already bounded by a full stop
 * or a blank line above, where a quotation <b>legitimately wraps</b>; so the newline must be
 * allowed inside the span, and the sentence boundary is what bounds the damage.</p>
 *
 * <p>Measured rather than argued, because the difference is invisible and load-bearing. Swapping
 * in `unquoted` makes this scan report <b>three more claims</b>, and all three are quotations that
 * happen to wrap: `content-api.md` quoting SH137's *"the new folder is not yet in the tree…"*,
 * `docs-process.md` quoting the sentence that cost SH694, and the paragraph in that same file
 * listing the phrasings `ABSENT` matches on. Each would be filed as a standing absence claim
 * somebody has to sign. `absent-claims.test.mjs` pins this.</p>
 */
const asserted = (sentence) => sentence
  .replace(/`[^`]*`/g, " ")
  .replace(/"[^"]*"/g, " ")
  .replace(/“[^”]*”/g, " ");

/**
 * Every standing absence claim, and what would make it false.
 *
 * <p>Keyed by file, the phrase, and the shipped ids the sentence cites — stable across a typo fix
 * and unstable across a change of subject, which is the trade that makes re-affirmation mean
 * something. The value is not documentation: it is the sentence somebody had to write to add the
 * entry, because writing it is the moment the claim gets checked.</p>
 */
const DECLARED = new Map(Object.entries({
  "docs/agents/agent-surface.md | is deliberately not covered":
    "`post.unpublish` has no undo entry. False the day the read surface returns the published "
    + "row for a DRAFT-preferred read, which is a change to `ShAgentReadService`, not to a doc.",
  "docs/agents/build.md | is not available":
    "`java.project.outputPath` has no effect on a Maven project. False if the Red Hat extension "
    + "gains a per-project override — an upstream change, checkable in its release notes.",
  "docs/agents/cli.md | is deliberately not here | SH429,SH437":
    "The delivery half of the captured-asset choice. False the day a static file keeps the "
    + "source's own path, which needs a delivery route the instance does not have.",
  "docs/agents/cli.md | is not available | SH437":
    "The same absence, stated as the reason the other answer was chosen. Falsified by the same "
    + "change, and the two sentences have to move together.",
  "docs/agents/cli.md | is not part of | SH477,SH658":
    "An archetype's mark whose aria-label repeats its visible text. False if the archetype set "
    + "grows one, which is a change in this repository's own corpus.",
  "docs/agents/blueprints.md | is not here":
    "`shio_apply --at --mode insert`, which SH1040's design named and which needs the SERVER to "
    + "hold the pattern. False the day a project file can reach the registry without the "
    + "marketplace's upload — a change to how `shio.blueprint.path` is filled, not to a doc.",
  "docs/agents/renderer.md | are not here":
    "Rebasing a reference `snapshot --against` can still measure. False if the snapshot "
    + "comparison stops depending on the original's own paths.",
  "docs/agents/renderer.md | is not available":
    "`ShReference` joins object_from/object_to and cannot carry the computed value §XV.11 "
    + "proposed. False if that entity gains the column, which is a Liquibase change.",
  "docs/specs/SH249-sections-and-regions.md | is not yet | SH169":
    "A page whose post-type is not yet bound to a layout, which is the normal state of a site "
    + "mid-build. This one is about a site's state and no ship can falsify it.",
}));

function walk(dir, out = []) {
  for (const entry of readdirSync(dir)) {
    const path = join(dir, entry);
    if (statSync(path).isDirectory()) walk(path, out);
    else if (path.endsWith(".md")) out.push(path);
  }
  return out;
}

/** Every id the ledger records as shipped — the same read SH747's ratchet makes. */
function shippedIds() {
  const ledger = readFileSync(join(ROOT, "docs", "CHANGELOG.md"), "utf8");
  return new Set([...ledger.matchAll(/^- \*\*(SH\d+)/gm)].map(([, id]) => id));
}

/** Every absence claim in the documented trees, keyed the way `DECLARED` keys them. */
function claims() {
  const shipped = shippedIds();
  const paths = [...TREES.flatMap((tree) => walk(join(ROOT, tree))),
    ...FILES.map((file) => join(ROOT, file))];
  const found = new Map();
  for (const path of paths) {
    const relative = path.slice(ROOT.length).replace(/\\/g, "/");
    const text = readFileSync(path, "utf8");
    for (const sentence of text.split(/(?<=[.!?])\s+|\n\n/)) {
      const matched = ABSENT.exec(asserted(sentence));
      if (!matched) continue;
      const ids = [...new Set([...sentence.matchAll(/\bSH\d+\b/g)].map(([id]) => id))]
        .filter((id) => shipped.has(id))
        .sort();
      const key = `${relative} | ${matched[0].toLowerCase().replace(/\s+/g, " ")}`
        + (ids.length ? ` | ${ids.join(",")}` : "");
      found.set(key, sentence.replace(/\s+/g, " ").slice(0, 160));
    }
  }
  return found;
}

test("SH769: every standing absence claim is signed, with what would make it false", () => {
  const found = claims();

  process.stdout.write(
    `\nSH769: ${found.size} absence claim(s) in ${TREES.join(", ")} and ${FILES.join(", ")}, `
      + `${DECLARED.size} declared\n`,
  );

  assert.ok(
    found.size > 5,
    "a walk that came back empty would make the assertions below pass over nothing, which is "
      + "the state every scan in this directory is written against",
  );

  const unsigned = [...found].filter(([key]) => !DECLARED.has(key))
    .map(([key, sentence]) => `${key}\n      ${sentence}`);

  assert.deepEqual(
    unsigned,
    [],
    "SH769: this sentence tells a reader a capability is absent, and nothing has said what would "
      + "make it false. That is the shape that cost a task: `styled by` was documented as "
      + "deliberately absent four lines above the section that shipped it, and it read as a "
      + "decision rather than as rot. Add the claim to DECLARED with one sentence naming what "
      + "would falsify it — writing that sentence is the check.",
  );
});

test("SH769: a signed claim whose sentence is gone is removed, not left standing", () => {
  const found = claims();
  const stale = [...DECLARED.keys()].filter((key) => !found.has(key));

  assert.deepEqual(
    stale,
    [],
    "SH769: this claim is signed here and no longer in the documents. An exemption for something "
      + "that does not exist is a line nobody removes and the next reader trusts (SH142) — and "
      + "here it is worse than usual, because the entry says what would falsify a claim that is "
      + "not being made any more. Delete it.",
  );
});

/**
 * SH822 — two strippers that look interchangeable, and what happens when you swap them.
 *
 * ## The finding, which is the opposite of the one that was filed
 *
 * This file's `asserted` was read as a fourth private copy of `source-text.mjs`'s `unquoted`, and
 * as the wrong copy: `unquoted` matches `"[^"\n]*"` where this matches `"[^"]*"`, and a character
 * class excludes newlines only if told to. Stated that way it is a defect — one stray quotation
 * mark runs away down the page.
 *
 * It is not, because the two are handed different things. `unquoted` reads a **document**, line by
 * line, and there the newline guard is what stops a runaway. This reads a **sentence**, already
 * split on a full stop or a blank line, and there a quotation *legitimately wraps* — so excluding
 * the newline is what breaks it, and the sentence boundary is what bounds the damage.
 *
 * ## Which is why it is pinned rather than commented
 *
 * The difference is one character in a character class, in two files, and it decides three
 * findings. A comment saying so is a sentence the next reader has to believe; this runs both over
 * the case that separates them. The wrapped quotation is real, from `docs/agents/content-api.md`.
 */
test("SH822: the sentence stripper lets a quotation wrap, and the document one must not", () => {
  const wrapped = 'SH137 argued copy needed none — "the new folder is not yet in the tree, so it\n'
    + 'cannot be its own ancestor" — which is true of the folder being created.';

  assert.doesNotMatch(asserted(wrapped), /is not yet/,
    "SH822: a quotation that wraps is still a quotation. This is `content-api.md` verbatim, and "
    + "with the newline excluded from the span the quoted `is not yet` survives stripping and is "
    + "filed as an absence claim somebody has to sign — one of three the swap produced, every one "
    + "of them a sentence quoting somebody else");

  assert.match(unquoted(wrapped), /is not yet/,
    "SH822: and the document reader does NOT strip it, which is correct for a document and is the "
    + "whole reason these are two functions. Over a page, an unpaired quotation mark that could "
    + "cross a newline swallows every line to the next one, and a scan that strips too much "
    + "reports a clean pass");

  // The property each one is protecting, stated as the case it must get right.
  const runaway = 'a stray " here\nTHE CLAIM IS not yet HERE\nand another " there';
  assert.match(unquoted(runaway), /THE CLAIM IS/,
    "SH822: the document reader keeps a line trapped between two unpaired quotes on OTHER lines — "
    + "this is the runaway its guard exists for");
  assert.doesNotMatch(asserted(runaway), /THE CLAIM IS/,
    "and the sentence reader does swallow it, which is the trade it makes knowingly: its input is "
    + "one sentence, so the blast radius is one sentence, and allowing the wrap is worth it");
});
