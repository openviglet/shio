/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH610 — one heading per block, asserted by a gate that actually runs.
 *
 * ## What went wrong, and why nothing caught it
 *
 * `docs/CHANGELOG.md` is written newest-first per block, so a run of follow-ups arriving after
 * another block's region had already opened needed a heading to re-establish the label — and the
 * honest way to write that sentence was to name the block again. Seven such headings accumulated:
 * five for Block K, one each for I and J.
 *
 * `roadkeep lint` reported every one of them, correctly, as `block.repeated`. Nothing read that
 * report. So the condition grew until `ship` — which refuses a repeated label outright, because *a
 * write files under this one by position alone* — could no longer write into three of the twelve
 * blocks. Shipping SH560 hit it on a file the task never touched; the first repair attempt renamed
 * the five headings, detached ten shipped entries from their block, and had to be reverted.
 *
 * That is the whole shape of the defect: **a lint nobody is obliged to read is a preference.** This
 * file is the obligation. `pnpm test` is a gate (agents.md: a red suite is a stop), and a merge or a
 * hand edit that recreates the condition now fails it in the same run that would have shipped.
 *
 * ## Why it re-states two of roadkeep's rules instead of shelling out to it
 *
 * `roadkeep` is the authority on these files and this is deliberately **not** a second
 * implementation of its schema — it asserts two structural facts and nothing else. Calling the tool
 * would be the stronger check and it is not available to assert with: roadkeep resolves to a Python
 * checkout outside this repository, which is a machine dependency `pnpm test` does not have and
 * which has twice been mid-merge in another session during this work. A guard that cannot run is
 * how the last one ended up unread.
 *
 * SH245's warning about a second copy of a contract applies and is accepted on its own terms: the
 * drift this can produce is a red run here against a green `roadkeep lint`, which is visible and
 * cheap. The drift it prevents is a ledger nothing can write to, which was neither.
 */
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { join } from "node:path";
import { test } from "node:test";

// @scans prose reason: CHANGELOG.md is Markdown and its headings are the subject, so there is
// no code half to separate — every line read is prose on purpose (SH740).

const ROOT = fileURLToPath(new URL("../../", import.meta.url));

/**
 * A heading that declares a block — at **any** level, which is the whole point.
 *
 * <p>Scanning `##` alone would have missed every one of the seven: they were written `### Block K
 * follow-ups — …`, a deeper level and a longer title, and roadkeep counts them as declarations
 * because the region they open is indistinguishable from the one `## Block K` opens. Measured
 * against the pre-merge ledger, a `##`-only regex reported **none**.</p>
 */
const BLOCK_HEADING = /^#{2,6}\s+(Block\s+\S+)\b/;

/** Any heading at all — because a block's region ends at the next one, whatever its level. */
const ANY_HEADING = /^#{1,6}\s/;

/** A shipped entry: the line `ship` writes and the only line that has to sit under a block. */
const ENTRY = /^-\s+\*\*(SH\d+)/;

async function ledger() {
  return (await readFile(join(ROOT, "docs/CHANGELOG.md"), "utf8")).split(/\r?\n/);
}

test("SH610: one label is one heading", async () => {
  const lines = await ledger();
  const seen = new Map();
  lines.forEach((line, index) => {
    const found = BLOCK_HEADING.exec(line);
    if (found) {
      const label = found[1].replace(/\s+/g, " ");
      seen.set(label, [...(seen.get(label) ?? []), index + 1]);
    }
  });

  const repeated = [...seen.entries()]
    .filter(([, at]) => at.length > 1)
    .map(([label, at]) => `${label} declared on lines ${at.join(", ")}`);

  assert.deepEqual(repeated, [], `
    a block label declared twice is a block nothing can ship into: roadkeep's \`ship\` files an
    entry under a heading by POSITION, so with two candidates it refuses rather than guess, and
    the refusal lands on whoever is holding an unrelated diff.

    The repair is a merge, not a rename. Renaming a second heading detaches every entry below it
    from its block (that is what \`block.missing\` means) — the entries have to MOVE into the one
    region, newest-first, and the title that introduced them becomes a bold paragraph rather than
    a heading, because a block's region ends at the next heading of any level.

${repeated.map((row) => `      ${row}`).join("\n")}`);

  // Not vacuous. A regex that stopped matching would make the check above pass forever, which is
  // the failure mode every instrument in this repository is warned about.
  assert.ok(
    seen.size >= 10,
    `only ${seen.size} block heading(s) matched — the ledger declares far more, so BLOCK_HEADING `
      + "no longer recognises how they are written and this file is asserting nothing",
  );
});

test("SH610: every shipped entry sits under a block heading", async () => {
  const lines = await ledger();

  let current = null;
  const orphans = [];
  let entries = 0;
  lines.forEach((line, index) => {
    if (ANY_HEADING.test(line)) {
      const found = BLOCK_HEADING.exec(line);
      // The other half of the same rule, and the one the first repair attempt broke: a `###`
      // inside a block region ENDS it. A sub-heading is therefore not a way to group entries —
      // bold text is.
      current = found ? found[1] : null;
      return;
    }
    const entry = ENTRY.exec(line);
    if (!entry) return;
    entries += 1;
    if (!current) orphans.push(`line ${index + 1}: ${entry[1]}`);
  });

  assert.deepEqual(orphans, [], `
    a shipped entry with no block heading above it is unreachable to every reader that walks the
    ledger by block — including \`stats\`, which is what says whether a block is complete, and the
    block-completion sweep that hangs off it.

${orphans.map((row) => `      ${row}`).join("\n")}`);

  assert.ok(
    entries >= 100,
    `only ${entries} entr(y|ies) matched — ENTRY no longer recognises a ledger line, so the walk `
      + "above visited nothing",
  );
});
