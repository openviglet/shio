/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH818 — the sanctioned exit from a comparator cycle.
 *
 * ## Why a verb exists at all
 *
 * `java` and `js` each host a comparator that judges the other, which is what makes every gate
 * judged once (SH800). It also has a fixed point: with both stamps red, every run of either
 * re-reads the other's red and stamps itself red again. The Java suite was 1907/0 and its one
 * failure was the js stamp; the js suite was clean but for the java stamp. Neither had a defect and
 * no sequence of runs said so.
 *
 * The escape is to discard the stamps and re-run in order, and that already worked by deleting the
 * files — the readers treat an absent stamp as *no run recorded*, which is honest. What it did not
 * do is leave a trace, so a stamp discarded to break a cycle looked exactly like one discarded to
 * dodge a red nobody wanted to fix. This writes `cleared: true` instead of unlinking, which carries
 * no colour for the same reason an absent stamp does not, and does say when and at which commit.
 *
 * ## What it deliberately does not do
 *
 * It does not decide whether you are in a cycle, and it does not run anything afterwards. Both
 * would make it the thing that concludes a gate is not evidence, and that conclusion belongs to
 * whoever read the two kept logs. A verb that clears a stamp and re-runs the gate is a verb that
 * turns any red green if you type it twice.
 */
import { join } from "node:path";
import { fileURLToPath } from "node:url";

import { GATES, clear } from "./gate-stamp.mjs";

const ROOT = fileURLToPath(new URL("../", import.meta.url));

const asked = process.argv.slice(2);
const unknown = asked.filter((gate) => !GATES.includes(gate));

if (asked.length === 0 || unknown.length > 0) {
  const named = unknown.length > 0 ? `not a gate: ${unknown.join(", ")}\n` : "";
  process.stderr.write(
    `${named}usage: pnpm gates:clear <gate>...   (${GATES.join(", ")})\n\n`
    + "Discards what a gate last answered HERE, recording that it was discarded rather than\n"
    + "deleting the record. Read the kept log before you clear a gate: this removes the\n"
    + "evidence of a red, and the only reason to do that is having established the red was\n"
    + "about the recording rather than the code (SH818).\n");
  process.exit(1);
}

for (const gate of asked) {
  const record = clear(ROOT, gate);
  process.stdout.write(record
    ? `cleared ${gate} — ${join(".gates", `${gate}.json`)} now records the clearing, not a colour\n`
    : `could not clear ${gate}; the stamp is unwritable, so delete .gates/${gate}.json by hand\n`);
}
