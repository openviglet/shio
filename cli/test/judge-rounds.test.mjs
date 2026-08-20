/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH869 — the round that quadrupled a defect and reported nothing.
 *
 * The measured shape, and the reason this exists rather than a note somewhere:
 *
 *     dangling var() occurrences   231  →  958  →  231
 *     verify                         0 errors on all three rounds
 *     judge                          same findings, same count
 *
 * Every instrument in the chain answers about **one** run, so a gate saying *0 errors* on the round
 * that quadrupled the defect is not wrong — it is answering the only question it has. What found it
 * was somebody keeping the previous number in a scratch file, and what reported it was a curator
 * saying the page looked worse.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { acceptanceBars } from "../src/acceptance.mjs";
import { describeJudgement, judgeRuns } from "../src/judge.mjs";
import {
  KEEP_ROUNDS, REGRESSED, appendRound, compareRounds, describeRounds, lastRound, regressions,
  roundRow,
} from "../src/judge-rounds.mjs";

const bars = acceptanceBars();
const ran = (name, flagged = []) => ({
  name, ran: true, exitCode: flagged.length ? 1 : 0, covered: ["/"], flagged,
});

/** N findings of a kind the authorable bar admits — the shape the measured regression had. */
const admitted = (n) => Array.from({ length: n }, (_, at) => ({
  subject: `/page-${at}`, kind: "snapshot.pixels-differ", why: "a dangling token",
}));

const roundOf = (n) => judgeRuns(
  [ran("verify"), ran("diff"), ran("snapshot", admitted(n))],
  { bars, mode: "authorable" },
);

test("SH869: a count that rose between two accepted rounds is what makes the second a finding", () => {
  // Both rounds pass the bar on their own — that is the whole defect. The movement is the finding,
  // and it is graded by the same bar file rather than by a rule of its own.
  const before = roundOf(3);
  const after = roundOf(12);
  assert.equal(before.verdict, "accepted");
  assert.equal(after.verdict, "accepted", "which is why no instrument could see the regression");

  const moved = compareRounds(roundRow(before, { at: "2026-08-18" }), roundRow(after));
  assert.deepEqual(moved.rose, [{ kind: "snapshot.pixels-differ", was: 3, now: 12 }]);

  const graded = judgeRuns([
    ran("verify"), ran("diff"), ran("snapshot", admitted(12)),
    { name: "rounds", ran: true, exitCode: 1, covered: [], flagged: regressions(moved) },
  ], { bars, mode: "authorable" });
  assert.equal(graded.verdict, "finding");
  assert.equal(graded.exitCode, 1);
  assert.equal(graded.held[0].kind, REGRESSED);
  assert.match(describeJudgement(graded), /snapshot\.pixels-differ\s+round\.regressed\s+rounds: 3 → 12/);
});

test("SH869: a count that fell is reported and holds nothing", () => {
  const moved = compareRounds(roundRow(roundOf(12)), roundRow(roundOf(3)));

  assert.deepEqual(moved.rose, [], "a round that fixed something is not a round to refuse");
  assert.deepEqual(moved.fell, [{ kind: "snapshot.pixels-differ", was: 12, now: 3 }]);
  assert.deepEqual(regressions(moved), [],
    "so nothing is flagged — treating an improvement as a change somebody has to justify is how a "
      + "ratchet becomes a thing people route around");
  assert.match(describeRounds(moved), /fell {2}snapshot\.pixels-differ {2}12 → 3/);
});

test("SH869: a kind that appears for the first time is counted from zero", () => {
  // The case a subtraction over shared keys alone would miss entirely, and the one most worth
  // catching: a defect class that was not there last time.
  const moved = compareRounds(
    roundRow(judgeRuns([ran("verify"), ran("diff")], { bars, mode: "authorable" })),
    roundRow(roundOf(2)),
  );

  assert.deepEqual(moved.rose, [{ kind: "snapshot.pixels-differ", was: 0, now: 2 }]);
});

test("SH869: two rounds judged in different modes are not compared", () => {
  const authorable = roundRow(roundOf(3));
  const fidelity = { ...roundRow(roundOf(30)), mode: "fidelity" };
  const moved = compareRounds(authorable, fidelity);

  assert.equal(moved.comparable, false);
  assert.deepEqual(moved.rose, [],
    "the bars differ, so the finding sets differ, and subtracting one from the other produces a "
      + "number that looks like a regression and is a change of question");
  assert.match(describeRounds(moved), /so their bars are different/);
  assert.equal(lastRound({ rounds: [authorable, fidelity] }, "authorable"), authorable,
    "and `--since` looks back past a round in another mode rather than refusing outright");
});

test("SH869: with no previous round there is nothing to compare and nothing is invented", () => {
  const moved = compareRounds(null, roundRow(roundOf(5)));

  assert.equal(moved.comparable, false);
  assert.deepEqual(moved.rose, []);
  assert.match(describeRounds(moved), /no previous round is recorded/);
});

test("SH869: a row is the numbers the run already had, sorted, and the file is bounded", () => {
  const row = roundRow(roundOf(4), { site: "VigR7", at: "2026-08-20" });

  assert.deepEqual(row, {
    at: "2026-08-20",
    site: "VigR7",
    mode: "authorable",
    verdict: "accepted",
    counts: { "snapshot.pixels-differ": { findings: 4, subjects: 4 } },
    instruments: { diff: true, snapshot: true, verify: true },
  }, "per kind rather than as a total, because a total conceals the shape the measured regression "
    + "had — the same number of findings, four times as many occurrences of one of them");

  let file = null;
  for (let at = 0; at < KEEP_ROUNDS + 5; at += 1) {
    file = appendRound(file, roundRow(roundOf(1), { at: `day-${at}` }));
  }
  assert.equal(file.rounds.length, KEEP_ROUNDS);
  assert.equal(file.rounds.at(-1).at, `day-${KEEP_ROUNDS + 4}`, "oldest first, newest last");
  assert.equal(file.rounds[0].at, "day-5", "and the oldest fall off the front");
});
