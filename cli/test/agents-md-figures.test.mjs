/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH974 — a figure about an instrument's inventory is cited from the run, or it is not written.
 *
 * <p>`agents.md` said of the stateless gate: "of 25 mutating paths, which 8 an agent can write to
 * without a cookie". The scan finds twenty-eight roots and the security config carries eleven
 * exemptions, so both numbers had moved — in the same paragraph that explains, about the gate one
 * sentence earlier, that "the run prints both counts, which is why neither is written here". The
 * rule was known and the exception was written two lines below it.</p>
 *
 * <p>Nothing could have caught it. `ShAgentP1ConformanceTest` refuses an unreproducible figure only
 * inside its own exception reasons, and no instrument reads prose. So the three P1 instruments now
 * write their counts to `shio-app/target/p1-inventory.json` (SH974), and this test refuses a figure
 * in the index or an area doc unless the paragraph cites that file — and, when the file is there,
 * unless the figure is one the run actually measured.</p>
 *
 * <h2>Why a sentence, and why only these three</h2>
 *
 * <p>The unit is the sentence that names the instrument. A paragraph was measured first and is too
 * coarse for these documents: a block here runs to half a section, so the stateless gate's paragraph
 * swept in every response budget printed near it and the check became a list nobody would read —
 * which is the failure mode SH551 and SH142 both name. The cost is stated rather than hidden: a
 * figure written two sentences away from the instrument's name is out of reach, so this rule is
 * enforceable rather than complete.</p>
 *
 * <p>The population is named rather than inferred, for the same reason: a scan for "a number near a
 * test class" sweeps in every benchmark figure, and those are measured against
 * `token-budgets.properties` and cited by a different mechanism. Adding an instrument means adding
 * it here, which is one line and a reviewable diff.</p>
 */
// @scans prose reason: the subject IS the prose — a figure written in a Markdown document, which is
// the claim being checked. There is no code to strip here.
import assert from "node:assert/strict";
import { existsSync, readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";

const ROOT = new URL("../../", import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1");

/** The instruments whose inventories are counted, and which write the artefact. */
const INSTRUMENTS = [
  "ShAgentP1ConformanceTest",
  "ShStatelessReachabilityLintTest",
  "ShAgentReadReachabilityLintTest",
  // SH982 — the P7 census writes to the same artefact, so a figure about its four axes is cited
  // from the run like every other one.
  "ShAgentP7ConformanceTest",
  // SH983 — the P9 census, same walk and same artefact.
  "ShAgentP9ConformanceTest",
];

/** Where a run writes what it measured (ShP1Inventory). */
const INVENTORY = join("shio-app", "target", "p1-inventory.json");

/** What a paragraph says to cite the run. */
const CITATION = "p1-inventory.json";

/** The documents a reader takes as current fact about the product. */
function documents() {
  const files = [["agents.md", readFileSync(join(ROOT, "agents.md"), "utf-8")]];
  const area = join(ROOT, "docs", "agents");
  for (const name of readdirSync(area).filter((file) => file.endsWith(".md")).sort()) {
    files.push([`docs/agents/${name}`, readFileSync(join(area, name), "utf-8")]);
  }
  return files;
}

/**
 * Sentences, as a reader would break them.
 *
 * Markdown's own breaks come first — a blank line, a bullet, a heading, a table row — because a
 * bullet that follows a full stop is a new claim and not a continuation. Missing that ran one
 * bullet's figures into the next bullet's instrument, which is a false positive, and the check has
 * to be readable before it can be believed.
 */
function sentences(text) {
  return text
    .split(/\n\s*\n/u)
    .flatMap((block) => block.split(/\n(?=\s*(?:[-*+>|]|#{1,6}\s|\d+\.\s))/u))
    // Not a colon: a colon introduces the claim ("prints the inventory: 25 roots"), so breaking
    // there would put the figure in a sentence that no longer names the instrument — which is the
    // one stale figure this was written for.
    .flatMap((block) => block.split(/(?<=[.!?])\s+(?=[A-Z`*\[(])/u))
    .map((sentence) => sentence.trim())
    .filter(Boolean);
}

/**
 * The counting figures in one paragraph.
 *
 * One to three digits, so a year or a port is not a count, and never the digits of an id: `SH973`
 * has no word boundary before its number. Inline code is dropped first — a `limit=500` in a code
 * span is an argument, not a claim about an inventory.
 */
function figures(paragraph) {
  const prose = paragraph.replace(/`[^`]*`/gu, " ");
  return [...prose.matchAll(/(?<![\w.])\d{1,3}(?![\w.%])/gu)].map((match) => Number(match[0]));
}

/** Every number the run measured, whichever instrument measured it. */
function measured() {
  if (!existsSync(join(ROOT, INVENTORY))) return null;
  const recorded = JSON.parse(readFileSync(join(ROOT, INVENTORY), "utf-8"));
  const values = new Set();
  for (const counts of Object.values(recorded)) {
    for (const value of Object.values(counts)) {
      if (typeof value === "number") values.add(value);
    }
  }
  return values;
}

/** Sentences naming one of the instruments, with where they are. */
function claims() {
  const found = [];
  for (const [name, text] of documents()) {
    for (const sentence of sentences(text)) {
      if (INSTRUMENTS.some((instrument) => sentence.includes(instrument))) {
        found.push({ name, sentence });
      }
    }
  }
  return found;
}

test("SH974: a figure about a P1 inventory is cited from the run that measured it", () => {
  const uncited = [];
  for (const { name, sentence } of claims()) {
    const stated = figures(sentence);
    if (stated.length > 0 && !sentence.includes(CITATION)) {
      uncited.push(`${name}: ${stated.join(", ")} — ${sentence.slice(0, 90).replace(/\n/gu, " ")}…`);
    }
  }

  assert.deepEqual(uncited, [],
    "SH974: a number in prose is a claim, and one with no artefact behind it is checked by whoever "
      + "happens to re-derive it — which is how agents.md came to say 25 and 8 when the run says "
      + "otherwise. Say what the instrument does and let the run print the count, or cite "
      + `${INVENTORY} in the same paragraph.`);
});

test("SH974: a cited figure is one the run actually measured", () => {
  const values = measured();
  if (values === null) {
    console.log(`[SH974] ${INVENTORY} is absent — run the Java gate to check the cited figures\n`);
    return;
  }

  const wrong = [];
  for (const { name, sentence } of claims()) {
    if (!sentence.includes(CITATION)) continue;
    for (const figure of figures(sentence)) {
      if (!values.has(figure)) wrong.push(`${name}: ${figure}`);
    }
  }

  assert.deepEqual(wrong, [],
    "SH974: the paragraph cites the run and states a number the run did not measure. Re-read "
      + `${INVENTORY} after the Java gate and correct the prose — a citation to an artefact that `
      + "says something else is worse than no citation, because it reads as checked.");
});

test("SH974: the scan finds the paragraphs it is about", () => {
  // The failure mode of every doc lint: a moved file or a tightened pattern turns the instrument
  // off and reports the same green.
  assert.ok(claims().length >= 3,
    `SH974: only ${claims().length} sentences name a P1 instrument, so this scan has stopped `
      + "reading the documents it is about");
  assert.deepEqual(figures("of 25 mutating paths, which 8 an agent can write to"), [25, 8]);
  assert.deepEqual(figures("SH973 shipped in 2026, `limit=500`"), [],
    "an id, a year and a code span are not claims about an inventory");
});
