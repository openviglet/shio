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
import { execFileSync } from "node:child_process";
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";

// @scans both reason: the ledger half is prose — CHANGELOG.md and a git diff of it as text, with
// one machine-readable line shape, and the ids are what this counts. The SH771 half reads a source
// file and runs it through the shared stripper first, because the comment explaining the
// instruction quotes the instruction, and a raw scan read the explanation as the defect.

import { readMirror, refresh, shippedSince } from "../../scripts/coverage-mirror.mjs";
import { code } from "./support/source-text.mjs";

const ROOT = new URL("../../", import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1");

/**
 * How many unnamed ids are a queue, above which they are a backlog (SH799).
 *
 * <h2>Why a ceiling here, where this repository usually refuses one</h2>
 *
 * <p>Every other ledger in this tree is a <b>named list rather than a ceiling</b>, always for the
 * same stated reason: a ceiling lets one task pay the debt and the next spend it. That objection is
 * about <i>permanent exemptions</i>, and it does not apply to this population. An entry here leaves
 * the moment somebody names the id — nothing accumulates, so there is no debt to spend.</p>
 *
 * <h2>Five, and where the number comes from</h2>
 *
 * <p>SH771 made this reader print rather than fail, because `COVERAGE.md` is in another repository
 * and failing the commit in front of you punishes somebody for a gap they cannot close from where
 * they are standing. That reasoning is exactly right about <b>a queue</b> — one or two ids between a
 * ship and the next docs pass — and it stops being right at some size, because past that the gap is
 * no longer the commit in front of you.</p>
 *
 * <p>Five is a session's ordinary output. SH797 measured the pathology at <b>twelve</b>, accumulated
 * across one session while this very line printed the ids on every run and nobody read them; the
 * only thing that ever objected was the gate needing `vigletdocs` checked out. At five this would
 * have failed on the sixth ship rather than the twelfth, which is the whole of the improvement.</p>
 */
const QUEUE_CEILING = 5;

/** Whether a queue of unnamed ids has stopped being one (SH799). */
function isBacklog(owing) {
  return owing.length > QUEUE_CEILING;
}

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

  // SH1115 — an id named only by an UNCOMMITTED edit to the matrix is not named. The mirror is read
  // from a working directory in another repository, and nothing used to ask whether the edit that
  // put an id there had ever been committed; observed twice in one session, and the conformance
  // check reads the same working copy, so mirror and matrix agreed permanently while the published
  // page never gained the sentence. `refresh` now records which ids the matrix's own HEAD does not
  // carry, and this subtracts them, so the obligation comes back instead of being forgotten.
  const provisional = new Set(mirror.matrix?.provisional ?? []);
  const named = new Set(mirror.named.filter((id) => !provisional.has(id)));
  const since = shippedSince(ROOT, mirror.at);

  if (mirror.matrix?.clean === false) {
    console.log(`SH1115: the matrix was uncommitted when this mirror was taken`
      + `${provisional.size ? `, and ${provisional.size} id(s) are named only by that edit: `
        + `${[...provisional].join(" ")}` : ""}`);
  }

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
    + "  Name each in its cluster row in shio-site/docs/src/content/docs/COVERAGE.md — a sentence saying what\n"
    + "  a reader can now do — then: pnpm run coverage:mirror\n"
    + "  Printed rather than failed on purpose (SH758): SH747's ratchet is the enforcement, and\n"
    + "  this is only the part that asks while the reasons are still fresh.\n",
  );

  // SH799 — printed, and bounded. See QUEUE_CEILING.
  assert.ok(
    !isBacklog(owing),
    `SH799: ${owing.length} shipped ids are waiting to be named, over the ${QUEUE_CEILING} this `
      + "reader will carry.\n"
      + "SH758 prints rather than fails, and that argument holds for A QUEUE — one or two ids "
      + "between a ship and the next docs pass. It stops holding here: this is no longer the commit "
      + "in front of you owing something, it is a backlog.\nOne commit, in this repository:\n"
      + "  1. shio-site/docs/src/content/docs/COVERAGE.md — name them in their cluster rows\n"
      + "  2. pnpm run coverage:mirror\n"
      + `Ids: ${owing.join(" ")}`,
  );
});

/**
 * SH799 — the assertion for the bound, driven rather than waited for.
 *
 * <p>The check above only fires when the repository actually owes six ids, which is a state nobody
 * should have to reach to find out whether the guard works — and the reason SH797 got to twelve is
 * that the only thing objecting was a gate somewhere else. So the boundary is asserted on both
 * sides of itself, with synthetic ids in the reserved range (SH787).</p>
 */
test("SH799: a queue passes at the ceiling and fails one past it", () => {
  // `SH${9800 + at}` and not `SH9${800 + at}`: the second leaves the literal `SH9` in this file, and
  // the debt walk reads source rather than runtime values, so it credited SH9 — a real task this
  // test asserts nothing about. SH787's hazard in a shape its reserved range does not cover, found
  // by that walk going red on the first run of this test.
  const ids = (count) => Array.from({ length: count }, (_, at) => `SH${9800 + at}`);

  assert.equal(isBacklog(ids(0)), false, "nothing owed is not a backlog");
  assert.equal(isBacklog(ids(QUEUE_CEILING)), false,
    "and neither is a full queue — SH771's argument holds all the way to the ceiling: the fix is in "
      + "another repository, so failing a commit for a gap it cannot close punishes the wrong person");
  assert.equal(isBacklog(ids(QUEUE_CEILING + 1)), true,
    "one past it is, and that is the whole claim: past this size the gap is not the commit in front "
      + "of you, it is a backlog nobody is looking at");

  // The number itself, held against the two facts that chose it, so moving it is a reviewed diff
  // with an argument rather than a quiet loosening.
  assert.ok(QUEUE_CEILING >= 1,
    "a ceiling of zero fails the commit that ships an id, which is exactly what SH771 refused");
  assert.ok(QUEUE_CEILING < 12,
    "and it must be under twelve, the measured pathology (see SH797) — a ceiling at or above the "
      + "incident would not have fired during the session that produced it");
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

test("SH911: an entry amended in place is not a ship the matrix owes a row for", () => {
  /*
   * `shippedSince` reads added ledger lines out of a range diff, and `roadkeep record amend`
   * rewrites an entry in place — so a correction arrives as a `-` line and a `+` line for the same
   * id. Counting only the `+` side dates a years-old entry to the commit that fixed a word in it.
   *
   * Measured: SH910 deleted forty files and corrected the seven ledger entries whose prose named
   * them. This reader then listed all seven as owing a COVERAGE row, and the queue of five real
   * ids became eleven — one past the ceiling that fails the build, on ids nobody had shipped.
   *
   * Driven through the real HEAD range so the parse is the one that ships. `SH179` is one of the
   * seven SH910 corrected, and `SH910` itself is the genuine ship in the same commit — the pair
   * that has to come apart.
   */
  const COMMIT = "b4199ee46";
  let diff;
  try {
    diff = execFileSync(
      "git", ["show", COMMIT, "-p", "--unified=0", "--", "docs/CHANGELOG.md"],
      { cwd: ROOT, encoding: "utf8", maxBuffer: 64 * 1024 * 1024 },
    );
  } catch {
    console.log(`SH911: ${COMMIT} is not in this clone — skipped`);
    return;
  }

  /*
   * Both sets are derived from the commit rather than written down. Naming the amended ids here
   * would credit them as asserted (SH630 counts an id in an assertion message), and one of them is
   * on the declared debt — so a test *about* amendments would quietly pay a debt it never closed.
   */
  const gained = new Set([...diff.matchAll(/^\+- \*\*(SH\d+)\*\*/gm)].map(([, id]) => id));
  const lost = new Set([...diff.matchAll(/^-- \*\*(SH\d+)\*\*/gm)].map(([, id]) => id));
  const amended = [...gained].filter((id) => lost.has(id));
  const genuine = [...gained].filter((id) => !lost.has(id));

  assert.ok(amended.length >= 2 && genuine.length >= 1,
    `SH911: that commit no longer carries the shape this asks about (${amended.length} amended,`
      + ` ${genuine.length} shipped), so the two assertions below prove nothing.`);

  const range = shippedSince(ROOT, `${COMMIT}~1`);
  if (range === null) {
    console.log("SH911: git could not diff that range — skipped");
    return;
  }

  assert.deepEqual(range.filter((id) => amended.includes(id)), [],
    "SH911: an entry amended in place is being reported as newly shipped. Those entries shipped"
      + " years before the commit that corrected the file paths in their sentences, and counting"
      + " them demands end-user documentation rows for work documented long ago.");
  assert.deepEqual(genuine.filter((id) => !range.includes(id)), [],
    "SH911: a genuine ship in that same commit is missing, so the amendment rule has swallowed"
      + " real arrivals — the direction that fails silently, since the queue then reads empty and"
      + " nothing is ever asked for.");
});

test("SH758: the report fires on an id that shipped and is not named", () => {
  // The path the two tests above cannot reach on a tree where the mirror was just refreshed: with
  // nothing shipped since, "no ids owing" is indistinguishable from a filter that never finds any.
  // Driven against a fabricated world, for the reason the red-suite comparator's twin is — the real
  // one is whatever this afternoon left, and an assertion about that is a test of today.
  // Ids far past anything real, the way red-suites.test.mjs picks SH9401/SH9402: a bare
  // `(SHnnn)` in a suite reads to SH527's debt reader as a claim to have closed that
  // task, and the first draft of this used SH1–SH4 and paid off two real debts by
  // accident. Same trap SH731 named, met from the other side.
  const named = new Set(["SH9401", "SH9402"]);
  const since = ["SH9402", "SH9403", "SH9404"];

  assert.deepEqual(since.filter((id) => !named.has(id)), ["SH9403", "SH9404"],
    "SH758: an id that entered the ledger since the mirror and is absent from the matrix is the "
    + "whole finding. A filter that came back empty here would print good news on every ship.");
  assert.deepEqual(["SH9402"].filter((id) => !named.has(id)), [],
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
test("SH771, SH1262: the coverage ratchet's refusal names where the matrix lives and the commit it takes", () => {
  // Comments stripped, through the one shared stripper (SH740). Found by writing this: the comment
  // that explains why the old instruction was wrong QUOTES the old instruction, and a raw scan read
  // the explanation as the defect it warns about — the same shape SH769 met one file over.
  const ratchet = code(readFileSync(
    join(ROOT, "cli", "conformance", "docs-coverage.conformance.mjs"), "utf8"));

  // SH771 asserted "two commits, in two repositories" while the matrix was in vigletdocs. SH1262
  // moved it here, so the refusal must now name this tree's path and not send a reader elsewhere.
  assert.match(ratchet, /One commit, in this repository/,
    "SH1262: the matrix is in this tree now, so the fix is one commit; a refusal still naming two "
    + "repositories sends the reader to a copy that is no longer read.");
  assert.match(ratchet, /shio-site\/docs\/src\/content\/docs\//,
    "and it must name where the matrix actually lives");
  assert.doesNotMatch(ratchet, /vigletdocs\/docs-shio/,
    "SH1262: vigletdocs' copy is marked moved and nothing reads it");
  assert.match(ratchet, /coverage:mirror/,
    "and the command that closes the second half here, which is the step somebody who only edited "
    + "the matrix would leave undone");
  assert.doesNotMatch(ratchet, /lower UNNAMED in the same commit/,
    "SH771: this was the instruction, and it named a commit that cannot exist. Restoring it puts "
    + "the reader back where three pushes went over a red nobody could clear.");
});

/**
 * SH1115 — driven against two real repositories, because the defect is about a revision.
 *
 * <p>Everything else in this file reads the live tree, which cannot answer this: the live docs
 * checkout is whatever the machine happens to have, and on a machine that has none at all the whole
 * question is unobservable. So these build a docs repository and a shio repository in a temp
 * directory and commit into them — the two states the mirror has to be able to tell apart.</p>
 *
 * <p>Why this is worth a fixture rather than a comment: the failure was not a wrong answer, it was
 * a question nobody asked. `refresh` wrote `at: <this repository's HEAD>` and read `COVERAGE.md`
 * out of a directory, and the two facts look like one until somebody says them side by side.</p>
 */
function repositories() {
  const base = mkdtempSync(join(tmpdir(), "sh1115-"));
  const docs = join(base, "docs-shio");
  const shio = join(base, "shio", "docs");
  mkdirSync(docs, { recursive: true });
  mkdirSync(shio, { recursive: true });
  const git = (cwd, ...args) => execFileSync("git", args, { cwd, encoding: "utf8", stdio: "pipe" });
  for (const cwd of [docs, join(base, "shio")]) {
    git(cwd, "init", "-q");
    git(cwd, "config", "user.email", "t@example.invalid");
    git(cwd, "config", "user.name", "t");
    git(cwd, "commit", "-q", "--allow-empty", "-m", "root");
  }
  const matrix = (body) => writeFileSync(join(docs, "COVERAGE.md"), body, "utf8");
  return { base, docs, shio: join(base, "shio"), git, matrix };
}

test("SH1115: a mirror taken from an uncommitted matrix names the ids it cannot vouch for", () => {
  const { base, docs, shio, git, matrix } = repositories();
  try {
    matrix("| cluster | ids |\n| a | SH9001 SH9002 |\n");
    git(docs, "add", "-A");
    git(docs, "commit", "-q", "-m", "matrix");

    // The clean case first, or a pass below proves nothing: this is the state the mirror has always
    // been able to express, and it must not change.
    const settled = refresh(shio, docs);
    assert.equal(settled.matrix.clean, true,
      "SH1115: a committed matrix is clean, and this is the case that must read exactly as before");
    assert.deepEqual(settled.matrix.provisional, [],
      "SH1115: nothing is provisional when every id is in the matrix's own HEAD");
    assert.deepEqual(settled.named, ["SH9001", "SH9002"]);
    assert.equal(settled.matrix.at, git(docs, "rev-parse", "HEAD").trim(),
      "SH1115: the revision recorded is the MATRIX's repository, not this one — `at` is this "
      + "repository's HEAD and answers a different question, which is how the two were conflated");
    assert.notEqual(settled.matrix.at, settled.at,
      "SH1115: and they are genuinely different shas here, so the assertion above is not passing "
      + "by both fields happening to hold the same value");

    // Now the observed failure: an id added to the matrix and never committed there.
    matrix("| cluster | ids |\n| a | SH9001 SH9002 |\n| b | SH9003 |\n");
    const dirty = refresh(shio, docs);

    assert.equal(dirty.matrix.clean, false,
      "SH1115: the matrix differs from its own HEAD, and a mirror that cannot say so marks SH9003 "
      + "named here forever while the published page never gains the sentence");
    assert.deepEqual(dirty.matrix.provisional, ["SH9003"],
      "SH1115: and it names WHICH id depends on the uncommitted edit. SH9001 and SH9002 are committed, "
      + "so crediting them is right; only SH9003 is a claim this mirror cannot support.");
    assert.deepEqual(dirty.named, ["SH9001", "SH9002", "SH9003"],
      "SH1115: `named` stays the working copy's set, because docs-coverage.conformance.mjs asserts "
      + "the mirror equals what the real matrix names — checked against the authority, and the "
      + "authority is the file. The provisional list is how the mirror disagrees with itself "
      + "honestly rather than by holding a different set.");

    // And committing it settles the obligation, which is the loop closing.
    git(docs, "add", "-A");
    git(docs, "commit", "-q", "-m", "name SH9003");
    assert.deepEqual(refresh(shio, docs).matrix.provisional, [],
      "SH1115: committing the matrix is what clears it, and nothing else — a re-run against the "
      + "same dirty tree must keep reporting, which is what `provisional` buys over a refusal");
  } finally {
    rmSync(base, { recursive: true, force: true });
  }
});

test("SH1262: a matrix inside this repository is settled by the commit that carries the mirror", () => {
  // The matrix moved into the Shio tree, so it and the mirror land in ONE commit and the matrix is
  // always dirty when the mirror is taken. SH1115's rule applied here would mark every id the ship
  // names provisional and withhold its credit forever after the commit.
  const base = mkdtempSync(join(tmpdir(), "sh1262-"));
  const docs = join(base, "shio-site", "docs", "src", "content", "docs");
  mkdirSync(docs, { recursive: true });
  mkdirSync(join(base, "docs"), { recursive: true });
  const git = (...args) => execFileSync("git", args, { cwd: base, encoding: "utf8", stdio: "pipe" });
  try {
    git("init", "-q");
    git("config", "user.email", "t@example.invalid");
    git("config", "user.name", "t");
    git("commit", "-q", "--allow-empty", "-m", "root");
    writeFileSync(join(docs, "COVERAGE.md"), "| cluster | ids |\n| a | SH9005 |\n", "utf8");

    const mirror = refresh(base, docs);
    assert.equal(mirror.matrix.sameRepository, true,
      "SH1262: the matrix and the mirror share a repository, and the mirror must say so");
    assert.deepEqual(mirror.matrix.provisional, [],
      "SH1262: an id named by the uncommitted matrix is not provisional when the same commit carries "
      + "the matrix and the mirror");
    assert.deepEqual(mirror.named, ["SH9005"]);
  } finally {
    rmSync(base, { recursive: true, force: true });
  }
});

test("SH1115: a provisional id is not credited, so the obligation comes back", () => {
  // The consequence, at the reader rather than at the writer. A mirror can hold the state and still
  // be useless if whoever reads it subtracts the whole `named` set, which is what happened.
  const provisional = new Set(["SH9003"]);
  const named = new Set(["SH9001", "SH9002", "SH9003"].filter((id) => !provisional.has(id)));

  assert.deepEqual(["SH9003"].filter((id) => !named.has(id)), ["SH9003"],
    "SH1115: an id named only in an uncommitted matrix still owes a row, or the mirror records the "
    + "state and the reader forgets it anyway");
  assert.deepEqual(["SH9002"].filter((id) => !named.has(id)), [],
    "SH1115: and a committed one does not, or every ship would read as owing after any local edit");

  // The shape the live mirror is read with, held so the `?.` cannot quietly become a crediting of
  // everything: a mirror written before SH1115 has no `matrix` key at all.
  const old = { named: ["SH9001"], at: "abc" };
  assert.deepEqual([...new Set(old.matrix?.provisional ?? [])], [],
    "SH1115: a mirror from before this field reads as nothing provisional, which is the only "
    + "backward-compatible answer — and is why the writer prints a warning rather than relying on "
    + "the reader to notice");
});
