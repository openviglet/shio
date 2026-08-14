/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH672 — a floor may only fire when the reader breaks, never when the corpus is right.
 *
 * ## The shape of the defect
 *
 * Every reader over the governed docs carries a **non-vacuity floor**: a number it must clear, so a
 * regex that stopped matching cannot report "zero problems out of zero subjects" and be green for the
 * one reason that proves nothing. The floors are right. Some of them are the wrong *kind* of number.
 *
 * `cited.length > 5`, `anchors.size > 20`, `listed.length > 10` all count a corpus **meant to
 * empty**: `roadkeep ship` drops a rationale section and takes its citation and its contents row with
 * it. So each was a second deadline nobody declared, enforced by a test whose stated purpose is
 * something else — and this is not a prediction. `improvements-debt.test.mjs` records three earlier
 * floors firing this way in one session, when shipping five tasks took the backlog from 24 to 19
 * while every floor still said 20, and a fourth when SH226 took the file from 51 sections to 50
 * against `> 50`.
 *
 * ## The instrument, not the three edits (SH338)
 *
 * A floor can be classified by **what it counts**. A registry and a ledger only grow, so an absolute
 * floor over them is honest — `shipped.size > 100` will be true forever. The roadmap and the
 * rationale do not: they are corpora whose whole purpose is to reach zero. Over those, the only
 * honest floor is *"the reader read something"*.
 *
 * So this walks the suites, finds every numeric floor, and holds the ones over a shrinking corpus as
 * a named list — the shape `assertion-debt` already uses, and for its reason: a ceiling lets one task
 * pay it and the next spend it.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

// @scans code — a javadoc explaining why a floor is the honest kind must not itself read as one.
// The scan itself lives in support/floors.mjs since SH741, because known-debt.test.mjs now asks
// the same question and two implementations of "what is a floor" would agree only by accident.
import { SHRINKING, corporaOf, floorsIn, suites } from "./support/floors.mjs";

/**
 * Floors over a shrinking corpus that are still absolute, each with the reason it is tolerated.
 *
 * <p>Empty is the healthy state. An entry here is a floor somebody decided is worth the risk of
 * firing on a correct corpus, and it has to say why — because the failure it produces looks exactly
 * like a broken reader, which is the confusion that costs the session.</p>
 */
const TOLERATED = new Map([
  // These three read the file's *shrinking* neighbours too, so the walk below flags them — and each
  // counts something that only grows, which is what makes an absolute bound evidence rather than a
  // deadline. Declared rather than auto-classified: naming the corpus a subject counts is a judgement
  // about the test, and a heuristic over variable names is the kind of guess this file exists to stop.
  ["improvements-debt.test.mjs:shipped>100",
    "counts the CHANGELOG's shipped ids — a ledger gains an entry per ship and never loses one, so "
      + "a hundred can only fall if the reader broke"],
  ["improvements-debt.test.mjs:coIds>5",
    "counts ledger entries that bold a second id, which is the same growing corpus one column along"],
  ["clean-room.test.mjs:box.files>5",
    "counts the files a clean-room box was handed, which is a source tree and grows"],
  // SH745 taught assertion-debt to read ROADMAP.md, which correctly moved it into this
  // population — the classifier reacting to a real change rather than a false positive. Both of
  // its floors count growing corpora, which is the same argument the two entries above make.
  ["assertion-debt.test.mjs:shipped>400",
    "counts the CHANGELOG's shipped ids — a ledger gains an entry per ship and never loses one, "
      + "so four hundred can only fall if the reader broke"],
  ["assertion-debt.test.mjs:found>100",
    "counts the ids the SUITES name, which grows with the test corpus; it guards the walk having "
      + "run at all, not how much work is left"],
  // The only thing the Java half turned out to hold (SH738). Measured when the walk was extended:
  // 277 more files, five of them reading a shrinking corpus, one floor among them — and it counts
  // the ledger's shipped ids, so it is the honest kind. Its neighbour in the same file was the
  // dishonest kind and is gone: SH737 replaced `headings > 10` with the yes/no it always meant.
  ["ShImprovementsStaleSectionRatchetTest.java:hasSizeGreaterThan>100",
    "counts the CHANGELOG's shipped ids, the same growing corpus as the two entries above — a "
      + "ledger gains an entry per ship and never loses one"],
]);

test("SH672: no floor over a shrinking corpus demands more than the reader having read", () => {
  const offenders = [];
  for (const [file, body] of suites()) {
    if (file === "floor-kind.test.mjs") continue;
    const { shrinking, growing } = corporaOf(body);
    // A file that reads only growing corpora may bound them absolutely: a ledger that lost a
    // hundred entries IS a broken reader, so the number is evidence rather than a deadline.
    if (!shrinking.length) continue;
    for (const floor of floorsIn(body)) {
      // `> 1` and below is "the reader read something", which is the claim a non-vacuity floor is
      // supposed to make. Anything higher is a count of how much work has not shipped yet.
      if (floor.bound <= 1) continue;
      const key = `${file}:${floor.subject}>${floor.bound}`;
      if (TOLERATED.has(key)) continue;
      offenders.push(
        `${key}  (reads ${shrinking.join(", ")}${growing.length ? ` and ${growing.join(", ")}` : ""})`,
      );
    }
  }

  assert.deepEqual(
    offenders,
    [],
    `A floor over ${SHRINKING.join(" or ")} fires when the corpus is CORRECT, not when the reader `
      + `breaks — three did exactly that in one session when shipping five tasks took the backlog `
      + `from 24 to 19 while every floor still said 20 (SH672). Use \`> 0\`, which is the claim a `
      + `non-vacuity floor actually makes, or add the key to TOLERATED with the reason.\n  `
      + offenders.join("\n  "),
  );
});

test("SH672: the reader finds floors at all, and tells the two kinds apart", () => {
  // Guards the guard, in both directions. A regex that stopped matching would report no offenders
  // and be green for the one reason that proves nothing — which is the defect this file is about,
  // one level up.
  const shrinking = "reads IMPROVEMENTS\nassert.ok(cited.length > 5, 'x');";
  const growing = "reads CHANGELOG only\nassert.ok(shipped.size > 100, 'x');";

  assert.deepEqual(floorsIn(shrinking), [{ subject: "cited", bound: 5 }]);
  assert.deepEqual(floorsIn(growing), [{ subject: "shipped", bound: 100 }]);
  assert.deepEqual(corporaOf(shrinking).shrinking, ["IMPROVEMENTS"]);
  assert.deepEqual(corporaOf(growing).shrinking, [], "a ledger-only reader is not held to this rule");
  assert.ok(
    floorsIn("assert.ok(rows.length > 0, 'x')")[0].bound === 0,
    "and a `> 0` floor is still found — it is simply the honest one",
  );
});

test("SH738: the walk reaches the Java suites, and would have caught SH737 there", () => {
  // The assertion that would have caught it, at the boundary it crossed. Not a paraphrase: this is
  // the line ShImprovementsStaleSectionRatchetTest actually carried, over the corpus it actually
  // read, and it is asserted to be an offender — because the whole finding is that this classifier
  // was built to refuse exactly this and never saw the file it was in.
  const sh737 = [
    'private static final Path IMPROVEMENTS = Path.of("..", "docs", "IMPROVEMENTS.md");',
    "assertThat(HEADING.matcher(read(IMPROVEMENTS)).results().count())",
    "        .isGreaterThan(10);",
  ].join("\n");

  assert.deepEqual(corporaOf(sh737).shrinking, ["IMPROVEMENTS"],
    "the file reads a corpus whose whole purpose is to reach zero");
  assert.deepEqual(floorsIn(sh737), [{ subject: "isGreaterThan", bound: 10 }],
    "and AssertJ's spelling of a floor is a method, which the JavaScript-only pattern could not "
      + "express — half the reason SH737 lived through two wrong values unseen");

  // The other half: the tree. A walk that stopped finding Java files would report the same green
  // as one that read them and found nothing, which is this file's own subject one level up.
  const names = [...suites().keys()];
  assert.ok(names.includes("ShImprovementsStaleSectionRatchetTest.java"),
    "the Java walk found nothing: the file SH737 happened in is the one this must reach");
  assert.ok(names.filter((n) => n.endsWith(".java")).length > 200,
    `the Java walk reached ${names.filter((n) => n.endsWith(".java")).length} files, which is too `
      + "few to be the suite tree — a moved directory turns this instrument off silently");
  assert.ok(names.some((n) => n.endsWith(".test.mjs")),
    "and the original half is still walked, so extending the reach did not replace it");
});

test("SH672: the tolerated list is a list, not a ceiling", () => {
  // The `assertion-debt` shape and its reason: a ceiling lets one task pay it and the next spend it,
  // so what is held is WHICH floors are tolerated rather than how many.
  process.stdout.write(`\nSH672 floors: ${TOLERATED.size} tolerated over a shrinking corpus\n`);
  for (const [key, why] of TOLERATED) {
    assert.ok(why && why.length > 20, `${key} is tolerated without a reason anybody can weigh`);
  }
});
