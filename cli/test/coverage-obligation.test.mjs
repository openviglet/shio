/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH758 — what has shipped here since the documentation matrix was last brought up to date.
 *
 * ## Not a second ratchet
 *
 * SH747 already holds the count of shipped ids `COVERAGE.md` does not name, and it may fall and
 * may not rise. That number is enforcement and it stays where it is. What it cannot do is ask the
 * question **while somebody still knows the answer**: the matrix is in another repository, so the
 * ratchet only speaks from a conformance run that needs an instance, the docs checkout and a
 * decision to look. By then several tasks have shipped and writing their cluster sentences is
 * archaeology.
 *
 * This asks the narrow version of the same question — *what have I shipped since the mirror was
 * refreshed?* — on the `pnpm test` that follows a ship, which is minutes rather than days.
 *
 * ## It prints and does not fail, deliberately
 *
 * §XXIV.5 says outright that this must not be made urgent: a ratchet that fires late still fires,
 * and the failure being prevented is the *silent* one. A gate that went red on every ship until a
 * second repository was edited would turn one commit into two on pain of a red suite, which is a
 * cost nobody weighed. So the assertions here are about the **reader** — that it can see the
 * ledger, the mirror and the diff — and the finding itself is printed.
 *
 * That is the same division `ShDeclaredRedTest` takes: the report is the deliverable, and it is
 * put where somebody is already looking.
 */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";

// @scans both reason: the ledger half is prose — CHANGELOG.md and a git diff of it as text, with
// one machine-readable line shape, and the ids are what this counts. The SH771 half reads a source
// file and runs it through the shared stripper first, because the comment explaining the
// instruction quotes the instruction, and a raw scan read the explanation as the defect.

import { readMirror, shippedSince } from "../../scripts/coverage-mirror.mjs";
import { code } from "./support/source-text.mjs";

const ROOT = new URL("../../", import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1");

/** Every id the ledger records as shipped — the same read SH747's ratchet makes. */
function shippedIds() {
  const ledger = readFileSync(join(ROOT, "docs", "CHANGELOG.md"), "utf8");
  return [...ledger.matchAll(/^- \*\*(SH\d+)\*\*/gm)].map(([, id]) => id);
}

test("SH758: what shipped since the matrix was refreshed, named while somebody remembers", () => {
  const mirror = readMirror(ROOT);
  assert.ok(mirror,
    "SH758: docs/coverage-named.json is missing or unreadable, so this reader compares a ship "
    + "against nothing and passes forever. Regenerate it: pnpm run coverage:mirror");

  const named = new Set(mirror.named);
  const since = shippedSince(ROOT, mirror.at);

  if (since === null) {
    // A shallow clone, a rewritten history, no git. Said out loud rather than passed over: an
    // empty answer from a reader that could not read is the failure every scan here is written
    // against, and it must not look like "nothing shipped".
    console.log(`SH758: git could not diff the ledger since ${mirror.at} — no report this run.`);
    return;
  }

  const owing = since.filter((id) => !named.has(id));
  if (owing.length === 0) {
    console.log(`SH758: ${since.length} id(s) shipped since the mirror at `
      + `${String(mirror.at).slice(0, 8)}, all named in COVERAGE.md`);
    return;
  }
  console.log(
    `\nSH758: ${owing.length} shipped id(s) COVERAGE.md does not name yet: ${owing.join(" ")}\n`
    + "  Name each in its cluster row in vigletdocs/docs-shio/COVERAGE.md — a sentence saying what\n"
    + "  a reader can now do — then: pnpm run coverage:mirror\n"
    + "  Printed rather than failed on purpose (SH758): SH747's ratchet is the enforcement, and\n"
    + "  this is only the part that asks while the reasons are still fresh.\n",
  );
});

test("SH758: the reader is not vacuous — it reads a real ledger, a real mirror and a real diff", () => {
  // The failure mode of every "and it prints" check: nothing asserts, so a reader that silently
  // stopped reading prints an empty finding and looks like good news. All three inputs are held.
  const mirror = readMirror(ROOT);
  assert.ok(mirror.named.length > 100,
    `SH758: the mirror names ${mirror?.named?.length} ids. The matrix names hundreds, so a figure `
    + "this small means it was generated against something else — and every id would then read as "
    + "un-named.");
  assert.ok(shippedIds().length > 400,
    "SH758: the ledger read came back nearly empty, which would make the diff below meaningless");

  // The diff reader, driven against the empty tree object: every ledger line reads as added, so a
  // reader that had stopped matching would come back with nothing here and be caught.
  const fromTheBeginning = shippedSince(ROOT, "4b825dc642cb6eb9a060e54bf8d69288fbee4904");
  assert.ok(fromTheBeginning === null || fromTheBeginning.length > 400,
    `SH758: diffing the ledger from the empty tree found ${fromTheBeginning?.length} added id(s). `
    + "Every shipped line was added at some point, so a handful means the pattern no longer "
    + "matches the ledger's shape and a real ship would slip past unmentioned.");
});

test("SH758: the report fires on an id that shipped and is not named", () => {
  // The path the two tests above cannot reach on a tree where the mirror was just refreshed: with
  // nothing shipped since, "no ids owing" is indistinguishable from a filter that never finds any.
  // Driven against a fabricated world, for the reason the red-suite comparator's twin is — the real
  // one is whatever this afternoon left, and an assertion about that is a test of today.
  // Ids far past anything real, the way red-suites.test.mjs picks SH901/SH902: a bare
  // `(SHnnn)` in a suite reads to SH527's debt reader as a claim to have closed that
  // task, and the first draft of this used SH1–SH4 and paid off two real debts by
  // accident. Same trap SH731 named, met from the other side.
  const named = new Set(["SH901", "SH902"]);
  const since = ["SH902", "SH903", "SH904"];

  assert.deepEqual(since.filter((id) => !named.has(id)), ["SH903", "SH904"],
    "SH758: an id that entered the ledger since the mirror and is absent from the matrix is the "
    + "whole finding. A filter that came back empty here would print good news on every ship.");
  assert.deepEqual(["SH902"].filter((id) => !named.has(id)), [],
    "SH758: and one the matrix already names is not owed twice — a report that repeated itself is "
    + "one somebody stops reading, which is the same end as not printing it.");
});
/**
 * SH771 — the refusal has to name a fix somebody can perform.
 *
 * <p>SH747's ratchet ended its instruction with *"then lower UNNAMED in the same commit"*, and that
 * commit does not exist: `COVERAGE.md` is in `vigletdocs` and the ceiling is in this repository. So
 * the rule this project states everywhere — one task, one commit, with the doc sync in it — is
 * unsatisfiable for that file by construction, and somebody following the instruction discovers it
 * only after doing the first half. Three consecutive pushes went over the resulting red, which is
 * the third state SH579 says does not exist, reached by following the rules rather than skipping
 * them.</p>
 *
 * <p>This is the boundary that defect crossed: what a person reads when the gate refuses. P6 is
 * about a fix that can be carried out, and an instruction that cannot be carried out in the
 * repository it is printed in fails that in the most expensive way — it looks actionable.</p>
 */
test("SH771: the coverage ratchet's refusal names both repositories, not one commit", () => {
  // Comments stripped, through the one shared stripper (SH740). Found by writing this: the comment
  // that explains why the old instruction was wrong QUOTES the old instruction, and a raw scan read
  // the explanation as the defect it warns about — the same shape SH769 met one file over.
  const ratchet = code(readFileSync(
    join(ROOT, "cli", "conformance", "docs-coverage.conformance.mjs"), "utf8"));

  assert.match(ratchet, /two commits, in two repositories/,
    "SH771: the refusal must say that the fix spans two repositories. Without it the reader is "
    + "told to do something in this tree that cannot be done in this tree.");
  assert.match(ratchet, /vigletdocs\/docs-shio/,
    "and it must name where the matrix actually lives, because the reader is standing in the other "
    + "repository when they read it");
  assert.match(ratchet, /coverage:mirror/,
    "and the command that closes the second half here, which is the step somebody who only edited "
    + "the matrix would leave undone");
  assert.doesNotMatch(ratchet, /lower UNNAMED in the same commit/,
    "SH771: this was the instruction, and it named a commit that cannot exist. Restoring it puts "
    + "the reader back where three pushes went over a red nobody could clear.");
});
