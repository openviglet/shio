/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH869 — a round nobody keeps.
 *
 * ## Every instrument answers about one run
 *
 * `verify` counts today's errors, `diff` today's losses, `snapshot` needs a baseline the caller
 * supplies, and `judge` reaches a verdict against a declared bar (SH860). None of them remembers
 * yesterday, and the only regression that reached a curator was invisible in every one:
 *
 *     dangling var() occurrences   231  →  958  →  231
 *     verify                         0 errors on all three rounds
 *     judge                          same findings, same count
 *
 * It was found because somebody had kept the previous round's number in a scratch file, and reported
 * because a curator said the page looked worse. A gate answering *0 errors* on the round that
 * quadrupled a defect is not wrong — it is answering a question about **one** run, which is the only
 * question it has.
 *
 * ## Why here and why it is cheap
 *
 * `judge` already gathers everything a comparison needs: the mode, the bar, the finding kinds and
 * their counts, and which instruments ran. **Nothing new is measured.** The numbers exist and are
 * thrown away at the end of every run, so this is a file and a subtraction.
 *
 * ## What a delta is, and what it is not
 *
 * A count that **rose** is a finding of its own kind — `round.regressed` — so it is graded by the
 * same bar file as everything else rather than by a rule of its own. A count that fell is reported
 * and never held: a round that fixed something is not a round to refuse, and treating an improvement
 * as a change to justify is how a ratchet becomes a thing people route around.
 *
 * Pure, for `judge.mjs`' reason: the command owns the file and this owns the arithmetic, which is
 * what has to be asserted without a disk.
 */

/** Where a project keeps its rounds — beside the plan, which is the other reviewed artefact. */
export const ROUNDS_FILE = "shio/judge-rounds.json";

/** How many rounds a file keeps. Old enough to see a trend, small enough that a reader reads it. */
export const KEEP_ROUNDS = 20;

/** The kind a risen count is reported as, so the bar grades it like any other finding. */
export const REGRESSED = "round.regressed";

/**
 * One round as a row: the verdict, and the counts a later round subtracts from.
 *
 * <p>Counted **per kind** rather than as a total, because a total conceals the shape the measured
 * regression had — the same number of findings, four times as many occurrences of one of them. The
 * subjects are counted too, since one page carrying a kind and forty carrying it are different
 * facts and a per-kind count alone cannot tell them apart.</p>
 *
 * <p>`at` is passed in rather than read from a clock here: this module is pure so that two runs over
 * one judgement produce one row (P5), and the caller is what knows when it ran.</p>
 */
export function roundRow(judgement, { site = null, at = null } = {}) {
  const counts = {};
  for (const row of [...(judgement?.held ?? []), ...(judgement?.noted ?? [])]) {
    const kind = String(row?.kind ?? "unknown");
    const own = counts[kind] ?? { findings: 0, subjects: new Set() };
    own.findings += 1;
    own.subjects.add(row?.subject ?? "");
    counts[kind] = own;
  }
  return {
    at,
    site,
    mode: judgement?.mode ?? null,
    verdict: judgement?.verdict ?? null,
    // Sorted, so two runs over one judgement write byte-identical rows and a diff of the file shows
    // what moved rather than how an engine happened to order a map (P5).
    counts: Object.fromEntries(Object.keys(counts).sort().map((kind) => [kind, {
      findings: counts[kind].findings,
      subjects: counts[kind].subjects.size,
    }])),
    instruments: Object.fromEntries((judgement?.instruments ?? [])
      .map((entry) => [entry.name, entry.ran === true])
      .sort((a, b) => a[0].localeCompare(b[0]))),
  };
}

/**
 * What moved between two rounds, and which of it is a regression (SH869).
 *
 * <p>A kind absent from the earlier row is counted **from zero**, which is the reading that catches
 * a defect class appearing for the first time — the case a subtraction over shared keys alone would
 * miss entirely, and the one most worth catching.</p>
 *
 * <p>The comparison is refused outright where the two rounds were judged in **different modes**: the
 * bars differ, so the finding sets differ, and subtracting one from the other produces a number that
 * looks like a regression and is a change of question. Reported as `incomparable` rather than as
 * zero movement, for the reason an instrument that did not run is never green.</p>
 *
 * @returns `{comparable, reason, rose, fell, verdict}` — `rose` is what holds, `fell` is reported
 */
export function compareRounds(previous, current) {
  if (!previous) {
    return { comparable: false, reason: "no previous round is recorded", rose: [], fell: [] };
  }
  if (previous.mode !== current.mode) {
    return {
      comparable: false,
      reason: `the last round was judged as ${previous.mode ?? "an unstated mode"} and this one as `
        + `${current.mode ?? "an unstated mode"}, so their bars are different and the difference `
        + "between their counts is a change of question rather than a movement",
      rose: [],
      fell: [],
    };
  }
  const kinds = [...new Set([...Object.keys(previous.counts ?? {}), ...Object.keys(current.counts ?? {})])];
  const rose = [];
  const fell = [];
  for (const kind of kinds.sort()) {
    const was = previous.counts?.[kind]?.findings ?? 0;
    const now = current.counts?.[kind]?.findings ?? 0;
    if (now > was) rose.push({ kind, was, now });
    else if (now < was) fell.push({ kind, was, now });
  }
  return {
    comparable: true,
    reason: null,
    rose,
    fell,
    verdict: { was: previous.verdict ?? null, now: current.verdict ?? null },
  };
}

/**
 * A risen count as a flagged row, so `judgeRuns` grades it with everything else (SH869).
 *
 * <p>Subject is the kind rather than a page: a movement is a fact about the round, and inventing a
 * page for it would put it in a per-page disagreement it is not about.</p>
 */
export function regressions(moved) {
  return (moved?.rose ?? []).map((row) => ({
    subject: row.kind,
    kind: REGRESSED,
    instrument: "rounds",
    why: `${row.was} → ${row.now}`,
  }));
}

/** The file's rows with this one appended, oldest first, bounded by {@link KEEP_ROUNDS}. */
export function appendRound(held, row) {
  const rows = Array.isArray(held?.rounds) ? held.rounds : [];
  return {
    note: "SH869 — one row per `shio judge --record`. Read by `--since`, which reports what moved "
      + "since the last row judged in the same mode. Nothing here is measured twice: every number "
      + "is one the run already had.",
    rounds: [...rows, row].slice(-KEEP_ROUNDS),
  };
}

/** The most recent row in the same mode, or null — the one `--since` compares against. */
export function lastRound(held, mode) {
  const rows = Array.isArray(held?.rounds) ? held.rounds : [];
  for (let at = rows.length - 1; at >= 0; at -= 1) {
    if (!mode || rows[at]?.mode === mode) return rows[at];
  }
  return null;
}

/** The movement as a reader sees it — the risen first, because that is the half that holds. */
export function describeRounds(moved, { previous } = {}) {
  if (!moved) return "";
  const lines = [];
  if (!moved.comparable) {
    return `  since: ${moved.reason} — nothing is compared\n`;
  }
  const when = previous?.at ? ` (${previous.at})` : "";
  if (!moved.rose.length && !moved.fell.length) {
    lines.push(`  since the last round${when}: every count is where it was`);
  }
  for (const row of moved.rose) {
    lines.push(`  ROSE  ${row.kind}  ${row.was} → ${row.now}${when}`);
  }
  for (const row of moved.fell) {
    lines.push(`  fell  ${row.kind}  ${row.was} → ${row.now}`);
  }
  if (moved.verdict && moved.verdict.was !== moved.verdict.now) {
    lines.push(`  verdict: ${moved.verdict.was} → ${moved.verdict.now}`);
  }
  return `${lines.join("\n")}\n`;
}
