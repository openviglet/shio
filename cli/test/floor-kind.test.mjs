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

import { readFileSync, readdirSync, statSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join } from "node:path";

const SUITES = fileURLToPath(new URL("./", import.meta.url));

/**
 * The other half of the repository, and the reason SH738 exists.
 *
 * <p>SH672 shipped complete against its own brief and blind against its reach: the walk read one
 * flat directory of `.test.mjs`, so `shio-app/src/test/java/**` — where the governed-docs readers
 * with the longest history live — was outside it entirely. SH737 then happened in exactly the shape
 * the header above describes: a floor over `IMPROVEMENTS.md`'s section count, written at 50,
 * lowered to 10, wrong again at 10 when block completions pruned the file to exactly that. This
 * classifier did not miss it. It <b>could not see it</b>, and would have flagged it on the first
 * run — which is what {@code theRuleWouldHaveCaughtSH737} asserts rather than claims.</p>
 */
const JAVA_SUITES = fileURLToPath(new URL("../../shio-app/src/test/java/", import.meta.url));

/**
 * The governed files whose contents are **meant to shrink**.
 *
 * <p>`ROADMAP.md` empties as work ships and `IMPROVEMENTS.md` loses a section per ship. A test that
 * reads either and demands a count above one is asserting that the project has not made progress.</p>
 */
const SHRINKING = ["IMPROVEMENTS", "ROADMAP"];

/**
 * And the ones that only grow, over which an absolute floor is an honest claim.
 *
 * <p>`CHANGELOG.md` gains an entry per ship and never loses one; a source corpus gains files. A floor
 * over those cannot fire for the project being healthy, which is the whole distinction.</p>
 */
const GROWING = ["CHANGELOG", "src", "suite"];

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
  // The only thing the Java half turned out to hold (SH738). Measured when the walk was extended:
  // 277 more files, five of them reading a shrinking corpus, one floor among them — and it counts
  // the ledger's shipped ids, so it is the honest kind. Its neighbour in the same file was the
  // dishonest kind and is gone: SH737 replaced `headings > 10` with the yes/no it always meant.
  ["ShImprovementsStaleSectionRatchetTest.java:hasSizeGreaterThan>100",
    "counts the CHANGELOG's shipped ids, the same growing corpus as the two entries above — a "
      + "ledger gains an entry per ship and never loses one"],
]);

/** `assert.ok(x.length > 12` / `x.size > 12` — the shape a non-vacuity floor is written in. */
const FLOOR = /assert\.ok\(\s*([A-Za-z_$][\w$.]*)\.(length|size)\s*>\s*(\d+)/g;

/**
 * The same claim in AssertJ, which is the second half of why SH737 was invisible (SH738).
 *
 * <p>A bound here is a <i>method</i>, and several of them mean the same thing, so this is a list
 * where the JavaScript side is one comparison. The subject is whatever the assertion was opened on
 * — usually a chained expression rather than a name — so it is captured loosely and only used to
 * label the offender.</p>
 */
const JAVA_FLOOR =
  /\.(hasSizeGreaterThan|hasSizeGreaterThanOrEqualTo|isGreaterThan|isGreaterThanOrEqualTo)\(\s*(\d+)\s*\)/g;

function suites() {
  const files = new Map();
  for (const name of readdirSync(SUITES)) {
    if (name.endsWith(".test.mjs")) {
      files.set(name, readFileSync(new URL(name, import.meta.url), "utf8"));
    }
  }
  for (const path of javaFiles(JAVA_SUITES)) {
    files.set(path.split(/[/\\]/).pop(), readFileSync(path, "utf8"));
  }
  return files;
}

/** Every `.java` under a tree. Recursive, because the Java suites are a package hierarchy. */
function javaFiles(dir) {
  const out = [];
  let entries;
  try {
    entries = readdirSync(dir);
  } catch {
    return out;
  }
  for (const entry of entries) {
    const path = join(dir, entry);
    if (statSync(path).isDirectory()) out.push(...javaFiles(path));
    else if (entry.endsWith(".java")) out.push(path);
  }
  return out;
}

/** Which corpus a file reads — by the governed names it mentions at all. */
function corporaOf(body) {
  return {
    shrinking: SHRINKING.filter((name) => body.includes(name)),
    growing: GROWING.filter((name) => body.includes(name)),
  };
}

/** Every numeric floor in a file, with its subject and its bound. */
function floorsIn(body) {
  const found = [];
  FLOOR.lastIndex = 0;
  let match;
  while ((match = FLOOR.exec(body)) !== null) {
    found.push({ subject: match[1], bound: Number(match[3]) });
  }
  JAVA_FLOOR.lastIndex = 0;
  while ((match = JAVA_FLOOR.exec(body)) !== null) {
    found.push({ subject: match[1], bound: Number(match[2]) });
  }
  return found;
}

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
