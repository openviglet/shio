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

import { readFileSync, readdirSync } from "node:fs";
import { fileURLToPath } from "node:url";

const SUITES = fileURLToPath(new URL("./", import.meta.url));

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
]);

/** `assert.ok(x.length > 12` / `x.size > 12` — the shape a non-vacuity floor is written in. */
const FLOOR = /assert\.ok\(\s*([A-Za-z_$][\w$.]*)\.(length|size)\s*>\s*(\d+)/g;

function suites() {
  const files = new Map();
  for (const name of readdirSync(SUITES)) {
    if (name.endsWith(".test.mjs")) {
      files.set(name, readFileSync(new URL(name, import.meta.url), "utf8"));
    }
  }
  return files;
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

test("SH672: the tolerated list is a list, not a ceiling", () => {
  // The `assertion-debt` shape and its reason: a ceiling lets one task pay it and the next spend it,
  // so what is held is WHICH floors are tolerated rather than how many.
  process.stdout.write(`\nSH672 floors: ${TOLERATED.size} tolerated over a shrinking corpus\n`);
  for (const [key, why] of TOLERATED) {
    assert.ok(why && why.length > 20, `${key} is tolerated without a reason anybody can weigh`);
  }
});
