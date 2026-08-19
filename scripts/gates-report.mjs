/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH819 — the gate table, without spending a suite to see it.
 *
 * ## What it is for
 *
 * SH755's deliverable is a printed table: every gate, what it last answered here, and whether that
 * answer is about this tree. It is genuinely the repair — the defect was somebody reading one green
 * gate and never learning about the other three. But it was printed by `ShDeclaredRedTest` and
 * `red-suites.test.mjs`, so seeing it meant running the Java suite or the JS suite: minutes, for
 * four lines derived from four small files.
 *
 * SH818 then depended on that read. Its exit says to work out whether the two comparator gates are
 * red about each other before clearing anything — and getting the table meant running a gate, which
 * in a cycle is the one operation that cannot help. This is the read that advice assumes.
 *
 * ## What it deliberately does not do
 *
 * It runs nothing, stamps nothing, and **exits 0 whatever the colours are**. A reporter that failed
 * on a red would be a fifth gate, and one whose verdict came from another gate's leftovers rather
 * than from any suite — which is the stale-colour mistake SH770 exists to refuse, promoted to a
 * command. It reports; the gates judge.
 */
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";

import {
  CYCLE_EXIT, GATES, clearedGates, comparatorCycle, gateLines, owedGates, stamps,
} from "./gate-stamp.mjs";

const ROOT = fileURLToPath(new URL("../", import.meta.url));

/** The gates `red-suites.json` excuses. Unreadable or absent is none, never a crash. */
async function declaredSuites() {
  try {
    const ledger = JSON.parse(await readFile(new URL("../red-suites.json", import.meta.url), "utf8"));
    return new Set((ledger?.exceptions ?? []).map((entry) => entry.suite));
  } catch {
    return new Set();
  }
}

const recorded = stamps(ROOT);
const declared = await declaredSuites();
const cleared = clearedGates(ROOT);

// No `self`: nothing is running, so every gate has an honest last outcome and there is no stamp
// that stands in for a run in progress. That is the one thing this reader can say and neither
// comparator can — each of those has a gate it must not read.
process.stdout.write(`the gates, as last run in this tree\n${
  gateLines(recorded, declared, null, cleared, owedGates(ROOT, recorded)).join("\n")}\n`
  + "  (stamps are local and untracked; a gate with no run here is not a failure)\n");

if (comparatorCycle(recorded)) {
  process.stdout.write(`${CYCLE_EXIT}\n`);
}

const unrun = GATES.filter((gate) => !recorded[gate]);
if (unrun.length > 0) {
  process.stdout.write(`\n  never run or cleared here: ${unrun.join(", ")}\n`);
}
