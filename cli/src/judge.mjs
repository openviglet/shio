/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH589 — one verdict, and the disagreements that produced it.
 *
 * <p>Judging a replica means running `verify`, `diff`, `audit` and `snapshot`, reading four reports
 * in four vocabularies, and knowing which one answers which question. Round eight: `verify` 0,
 * `audit` 0, `diff` 1. Round twelve: `verify` 0, `diff` 1. Both ended with a real defect and neither
 * ended with a number anybody could act on.</p>
 *
 * <h2>The composition is the easy half</h2>
 *
 * <p>The half worth building is that it prints <b>where they disagreed</b>, because that is where
 * every finding of this work came from: SH521 (the gate said `ok` on a page `/agent/render` had
 * already scored at nine unstyled classes), SH554 (`diff` reported a loss `verify` could not see,
 * and it was `diff` that was wrong), SH577 (`diff` red and `verify` green, and the mode was the
 * reason), SH586 (`diff` red where `verify` was clean, and the links were leaving the site). Four
 * for four. A disagreement between two instruments is not noise to be reconciled; it is the
 * highest-yield signal this chain produces, and it reaches a reader only if the reader happens to
 * run both and notice.</p>
 *
 * <h2>The one thing it must not do is average</h2>
 *
 * <p>A green from three and a red from one is a <b>red</b>, and the report names which and on what,
 * so the disagreement survives the summary rather than being dissolved by it. The same rule one
 * step along: an instrument that did not run is <b>unproven</b> and never green — "we could not
 * check this" and "this is fine" are different answers, and a verdict that collapses them is the
 * defect SH410 fixed in the baseline and SH588 refused in the annotation count.</p>
 *
 * <p>Pure. The command owns running the four; everything here is the reconciliation, which is what
 * has to be asserted and what a browser must not be needed to test.</p>
 *
 * <h2>SH860 — and none of it was a verdict, because there was no bar</h2>
 *
 * <p>This exited 1 on any finding, so every round ended with work outstanding and none ended
 * *accepted*. Block Q carries 131 shipped entries and has been declared closed and reopened **six**
 * times: each close was a line count reaching zero and each reopen was the next round finding
 * something, because emptiness was standing in for doneness. `acceptance.properties` is the bar and
 * `docs/agents/replication.md` the criterion it encodes; what is here is the join — a finding at or
 * below its bar is **noted** and printed, one above it **holds**, and a kind the file does not
 * mention holds too, since accepting by omission is how a criterion stops meaning anything.</p>
 */

import { ACCEPTANCE_FILE, classify } from "./acceptance.mjs";

/**
 * What each instrument answers, and over what.
 *
 * <p>Stated here rather than left to a reader, because "which one answers which question" is
 * precisely the knowledge no command carried. `scope` is what makes a disagreement computable: an
 * instrument only disagrees about a subject it <em>covered</em>, and an instrument that never
 * looked at a page is not evidence that the page is fine.</p>
 */
export const INSTRUMENTS = {
  verify: {
    asks: "does the content lint, and do its routes resolve",
    reads: "the content graph and the projected tree — no browser, no rendering",
  },
  diff: {
    asks: "does the replica still serve what the capture declared",
    reads: "the served bytes against the inventory, counts rather than pixels",
  },
  audit: {
    asks: "is anything wrong with the page a browser renders",
    reads: "a real render: 404s, thrown exceptions, overflow, contrast, unstyled classes",
  },
  snapshot: {
    asks: "does it look different from what it is being compared with",
    reads: "pixels, against a baseline or the source origin",
  },
};

/**
 * A verdict a subject can carry, worst first. The order *is* the precedence — see `judgeRuns`.
 *
 * <p>SH860 — `accepted` beside `ok`, and they are not synonyms. `ok` was "no instrument said
 * anything", which is the only green a run with no bar can offer; `accepted` is "every finding is at
 * or below the declared bar", which is a claim about a criterion and the one a block can close on. A
 * round with nothing to say is `accepted` too — the bar is met vacuously — so `ok` survives only as
 * the answer a caller gets when it passes no bar at all.</p>
 */
export const VERDICTS = ["finding", "unproven", "accepted", "ok"];

/**
 * One verdict over N instrument runs, and the disagreements behind it.
 *
 * @param {Array<{name: string, ran: boolean, reason?: string, exitCode?: number,
 *   covered?: string[], flagged?: Array<{subject: string, why: string}>}>} runs
 *   `covered` is every subject the instrument actually looked at; `flagged` is the subset it has
 *   something to say about. A run with `ran: false` carries `reason` and contributes nothing but
 *   its own name to `unproven` — which is the whole point of tracking it.
 * @returns {{verdict: string, exitCode: number, instruments: object[], disagreements: object[],
 *   agreed: object[], unproven: string[]}}
 */
export function judgeRuns(runs, { bars = null, mode = "authorable" } = {}) {
  const instruments = runs.map((run) => ({
    name: run.name,
    ran: Boolean(run.ran),
    reason: run.reason ?? null,
    exitCode: run.ran ? Number(run.exitCode ?? 0) : null,
    covered: [...new Set(run.covered ?? [])].sort(),
    flagged: [...(run.flagged ?? [])].sort(bySubject),
  }));

  const ran = instruments.filter((entry) => entry.ran);
  const unproven = instruments.filter((entry) => !entry.ran).map((entry) => entry.name);

  // SH860 — every flagged row from every instrument, judged against the declared bar at once. Per
  // kind rather than per instrument, because the bar is a statement about how many subjects may
  // carry a finding and not about how many instruments noticed one.
  const all = ran.flatMap((entry) => entry.flagged.map((row) => ({ ...row, instrument: entry.name })));
  const { held, noted } = bars ? classify(all, bars, mode) : { held: all, noted: [] };

  // Never an average. One red is the verdict, however many greens are standing next to it — and with
  // a bar in hand, "red" is a finding the bar does not admit rather than any finding at all. An
  // instrument's own non-zero exit still counts: it is the one signal that is not a per-subject row,
  // so a run that failed for a reason it could not attribute to a page is never quietly accepted.
  const failed = ran.some((entry) => entry.exitCode !== 0 && !entry.flagged.length);
  const red = held.length > 0 || failed;
  let verdict = "unproven";
  if (ran.length) verdict = red ? "finding" : bars ? "accepted" : "ok";

  const subjects = new Set();
  for (const entry of ran) for (const row of entry.flagged) subjects.add(row.subject);

  const disagreements = [];
  const agreed = [];
  for (const subject of [...subjects].sort()) {
    const flaggedBy = ran.filter((entry) => entry.flagged.some((row) => row.subject === subject));
    // Covered it and had nothing to say. An instrument that never looked is neither — which is the
    // distinction that keeps this from inventing a disagreement out of a run's own scope.
    const clearedBy = ran.filter((entry) =>
      entry.covered.includes(subject) && !flaggedBy.includes(entry));
    const row = {
      subject,
      flaggedBy: flaggedBy.map((entry) => entry.name),
      clearedBy: clearedBy.map((entry) => entry.name),
      why: flaggedBy.flatMap((entry) => entry.flagged
        .filter((finding) => finding.subject === subject)
        .map((finding) => ({ instrument: entry.name, why: finding.why }))),
    };
    (clearedBy.length ? disagreements : agreed).push(row);
  }

  return {
    verdict,
    // The gate. `unproven` alone does not fail a build — it is a statement about coverage, and
    // failing on it would make an optional browser a hard dependency of every run (SH411's split).
    exitCode: verdict === "finding" ? 1 : 0,
    instruments,
    disagreements,
    agreed,
    unproven,
    // SH860 — which findings the bar admitted, and under which number. Returned as well as printed
    // for SH576's reason: the corpus asserts it and `--json` is a caller too. Present only where a
    // bar was passed, so a caller that judges without one gets the pre-SH860 shape unchanged.
    ...(bars ? { mode, held, noted } : {}),
  };
}

function bySubject(a, b) {
  return String(a.subject).localeCompare(String(b.subject));
}

/**
 * The report, and it leads with the disagreements rather than with the four reports.
 *
 * <p>The four reports are what a reader already had. What they did not have is the join, so the
 * join goes first and the per-instrument line — which is the old output compressed to one row each
 * — goes underneath as the evidence for it.</p>
 */
export function describeJudgement(judgement, { site } = {}) {
  const { verdict, instruments, disagreements, agreed, unproven, mode, held, noted } = judgement;
  const ran = instruments.filter((entry) => entry.ran);
  const lines = [
    `shio judge: ${site ?? "the replica"} — ${verdict.toUpperCase()}`
      + `${mode ? ` as ${mode}` : ""}`
      + ` from ${ran.length} instrument(s)`
      + `${unproven.length ? `, ${unproven.length} unproven (${unproven.join(", ")})` : ""}`
      + `${disagreements.length ? `, ${disagreements.length} disagreement(s)` : ""}`,
  ];

  // SH860 — the bar, said out loud, and the mode with it. `docs/agents/replication.md`'s second trap
  // is *say which mode ran*: fidelity reaches 0.00% and looks finished, which is exactly why it is
  // not evidence about the other two, and the bar this run applied differs between them.
  if (mode) {
    lines.push(
      `  judged against ${ACCEPTANCE_FILE} as ${mode} — ${held.length} finding(s) hold this round, `
        + `${noted.length} at or below the bar`,
    );
  }
  if (held?.length) {
    lines.push("  HOLDS — above the declared bar, or of a kind the bar does not mention:");
    for (const row of held) {
      lines.push(
        `    ${row.subject}  ${row.kind}  ${row.instrument}: ${row.why}`
          + `${row.undeclared
            ? ` — no bar declares \`${row.kind}\`; add one to ${ACCEPTANCE_FILE} or fix this`
            : ` — bar ${row.bar}`}`,
      );
    }
  }
  // Printed, always, and this is the half that makes `accepted` an honest word: a finding the bar
  // admitted is one somebody decided to admit, and a reader has to be able to see what they bought.
  if (noted?.length) {
    lines.push(`  ${noted.length} finding(s) the bar admits — a task for later, not a reason to hold:`);
    for (const row of noted) {
      lines.push(`    ${row.subject}  ${row.kind}  ${row.instrument}: ${row.why} — bar ${row.bar}`);
    }
  }

  if (disagreements.length) {
    lines.push(
      "  WHERE THEY DISAGREED — one instrument found this and another looked and did not. Every",
      "  finding of this work came from a pair like these (SH521, SH554, SH577, SH586), and one of",
      "  the two is always wrong:",
    );
    for (const row of disagreements) {
      lines.push(
        `    ${row.subject}`
          + `  ${row.flaggedBy.join(" + ")} say something; ${row.clearedBy.join(" + ")} looked and did not`,
      );
      for (const reason of row.why) {
        lines.push(`      ${reason.instrument}: ${reason.why}`);
      }
    }
  }

  if (agreed.length) {
    lines.push(`  ${agreed.length} subject(s) every instrument that looked agrees about:`);
    for (const row of agreed) {
      lines.push(`    ${row.subject}  ${row.flaggedBy.join(" + ")}`);
    }
  }

  lines.push("  instruments:");
  for (const entry of instruments) {
    const asks = INSTRUMENTS[entry.name]?.asks ?? "";
    if (!entry.ran) {
      // Never green, and it says what it would have answered — an unproven question is one the
      // reader may want to go and ask, and they cannot if the report only says a name.
      lines.push(`    ${entry.name}  NOT RUN — ${entry.reason ?? "no reason given"}`);
      lines.push(`      it would have answered: ${asks}`);
      continue;
    }
    lines.push(
      `    ${entry.name}  ${entry.exitCode === 0 && !entry.flagged.length ? "ok" : "finding"}`
        + `  ${entry.flagged.length} subject(s) of ${entry.covered.length} looked at`
        + `  — ${asks}`,
    );
  }

  // SH860 — and `accepted` needs it at least as much as `ok` did: it is the word a block closes on,
  // so a reader has to be told which questions were not asked before they close one on it.
  if ((verdict === "ok" || verdict === "accepted") && unproven.length) {
    lines.push(
      `  ${verdict} is a claim about the ${ran.length} instrument(s) that ran. `
        + `${unproven.join(", ")} did not, and did not agree — nothing here says the question `
        + "they answer came back clean",
    );
  }
  return `${lines.join("\n")}\n`;
}

/**
 * `post:Acme/about` → `/about`; the site's home page is `post:Acme/` → `/` (SH433).
 *
 * <p>The subject a disagreement is keyed on has to be the same string whichever instrument produced
 * it, and the four speak two vocabularies: `verify` names an SH79 address, while `diff`, `audit`
 * and `snapshot` name a served path. Normalising to the path is the direction that loses nothing —
 * the site is already the command's own argument.</p>
 */
export function subjectOf(address) {
  const raw = String(address ?? "").trim();
  if (!raw) return null;
  const post = /^post:[^/]*(\/.*)?$/.exec(raw);
  if (post) return post[1] || "/";
  const folder = /^folder:/.test(raw) || /^site:/.test(raw) || /^id:/.test(raw);
  // A finding about the site or a folder is real and is not about a page. Keyed on its own address
  // so it still reaches the report; it simply never joins a per-page disagreement.
  if (folder) return raw;
  return raw.startsWith("/") ? raw : `/${raw}`;
}
