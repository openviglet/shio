/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH861 — the sweep a finished block owes, asked by something other than a memory.
 *
 * ## The failure
 *
 * The upkeep rule says a block's last task brings three outward surfaces up to date in one pass:
 * `docs-shio`, `shio-site` and `README.md`. Block Q finished with 136 entries and **one** of the
 * three moved — `COVERAGE.md`, and only because a gate asked (SH799's queue ceiling failed the build
 * at six unnamed ids). Nothing asked about the other two, so nothing happened to them, and what they
 * said was a replication story ending at *convert*: no acceptance criterion, no `shio judge`, and no
 * mention that looking like the source is deliberately not one of authorable mode's tests — the
 * sentence that let the block close after six false closes.
 *
 * A sweep that happens because somebody remembered is the arrangement that produced those six.
 *
 * ## Why a declaration and not a derived answer
 *
 * "Did the surfaces move" is a `git log` comparison and this makes it. What it cannot derive is
 * whether a sweep was **owed**: Block AN is one task about a lint over source bytes, and no outward
 * surface describes it, so a reader demanding three edits there would be demanding noise — and a
 * gate that fires where nothing is wrong is the one that teaches people to skip gates.
 *
 * So each finished block says which it is, and the claim is *checked*: a block declaring it was swept
 * must have moved at least one surface since its own last ship, or the declaration is a sentence
 * nobody paid for. That is the division `handoffs.test.mjs` takes — the classification is written
 * down, and the half that can rot is verified against the artefact.
 *
 * ## Bounded to a recent window, deliberately
 *
 * Only blocks whose last entry landed inside {@link WINDOW} commits are asked about. Every block that
 * finished before this instrument existed is out of scope by construction rather than by an
 * exemption list, which is what keeps this from arriving as forty findings nobody will act on — the
 * failure mode SH642's 88 triaged entries measured. A block that finishes tomorrow is in scope on the
 * commit after it closes, which is the only moment the answer is cheap.
 */
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";

// @scans prose reason: the subject is the ledger and the roadmap, which are documentation, plus a
// git log of paths. The ids and the block headings are what this extracts and no code stripper
// would help with them (SH740).

const ROOT = new URL("../../", import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1");

/**
 * How far back a block's last ship may be and still be this reader's business.
 *
 * <p>About a session's output, which is the same quantity SH799 reasoned about when it bounded the
 * coverage queue at five. The point of this reader is to ask <b>while the answer is cheap</b> — on
 * the `pnpm test` that follows the commit closing a block — and a window measured in months would
 * turn it into a backlog of its own. Measured at 40 it named nine blocks, seven of them instrument
 * rounds finished a fortnight ago whose sweep nobody was ever going to write; that is the shape
 * SH642's 88 triaged entries took, and none of them moved.</p>
 *
 * <p>What falls out of the window is not forgiven, it is <b>recorded</b>: the declaration in
 * {@link SWEEP} stays whether or not the block is still in scope, so the answer survives the ask.</p>
 */
const WINDOW = 12;

/**
 * The three surfaces a finished block's sweep is about.
 *
 * <p>`docs-shio` is in another repository, so what stands for it here is the mirror
 * `pnpm run coverage:mirror` writes — the same artefact SH758's reader consults, and the only part of
 * that repository this one can see.</p>
 */
const SURFACES = ["README.md", "shio-site/src", "docs/coverage-named.json"];

/**
 * What each recently finished block owes, and whether it paid.
 *
 * <p>`swept: true` is a claim about the artefact and is verified below. `swept: false` is a claim
 * about the block — that no outward surface describes what it changed — and is verified by nothing,
 * which is why it has to carry a reason a reader can disagree with.</p>
 */
const SWEEP = {
  S: {
    swept: true,
    why: "SH861 is itself an edit to the public site — the /replicate page gained the `judge` phase "
      + "and the mode table says an authorable replica is not judged against the source's pixels",
  },
  Q: {
    swept: true,
    why: "SH861 — README's replication section and shio-site's /replicate phases carry `judge`, the "
      + "declared bar, and the exclusion that looking like the source is not authorable mode's test",
  },
  AO: {
    swept: false,
    why: "one task declaring the repository's line endings so a one-line edit stops diffing as the "
      + "whole file. It changes what a reviewer can see and nothing a reader of the product can do; "
      + "`docs/agents/troubleshooting.md` carries it, including the one-off tree refresh",
  },
  AI: {
    swept: false,
    why: "two tasks about scans matching outside the construct they read — a regex bound and a "
      + "hand-off reader's line window. Both change what a contributor may write and neither changes "
      + "anything a reader of the product can do; `docs/agents/build.md` is where that lands",
  },
  W: {
    swept: false,
    why: "SH879 asks which SOURCE shapes the corpus can produce — the question SH833 and SH838 ask "
      + "of this repository's own mechanisms, turned outward. It changes what a contributor may "
      + "leave unmeasured and nothing a reader of the product can do; `docs/agents/cli.md` group N "
      + "carries it, and shio-site's README records the closure with the same verdict",
  },
  AN: {
    swept: false,
    why: "one task about a lint over source bytes (a raw NUL making a file binary to a grep). No "
      + "outward surface describes it and nothing a reader of the product can do changed — the "
      + "audience is a contributor, and `docs/agents/troubleshooting.md` is where that lands",
  },
};

/** `## Block X — …` → the ids filed under it, in file order. */
function blocksIn(text) {
  const blocks = new Map();
  let current = null;
  for (const line of text.split(/\r?\n/)) {
    const heading = /^##+\s+Block\s+([A-Z0-9]+)\b/.exec(line);
    if (heading) {
      current = heading[1];
      blocks.set(current, blocks.get(current) ?? []);
      continue;
    }
    const id = /^- (?:[^*]*)?\*\*(SH\d+)\*\*/.exec(line);
    if (id && current) blocks.get(current).push(id[1]);
  }
  return blocks;
}

/** Every block label the roadmap still declares a heading for — which is to say, is not finished. */
function openBlocks(text) {
  return new Set([...text.matchAll(/^##+\s+Block\s+([A-Z0-9]+)\b/gm)].map(([, label]) => label));
}

function git(args) {
  try {
    return execFileSync("git", args, { cwd: ROOT, encoding: "utf8", maxBuffer: 32 * 1024 * 1024 });
  } catch {
    return null;
  }
}

/** The record separator for the log below — a word, for the reason {@link shipCommits} states. */
const COMMIT_MARK = "shio-commit ";

/**
 * Every id the ledger gained inside the window, keyed to the commit that added it.
 *
 * <p><b>One `git log`, and that is not tidiness.</b> The first draft asked `git log -S` per id — a
 * pickaxe search over the whole history, once for each of three hundred ledger entries — and the
 * suite ran past two minutes without finishing. This reads the same answer out of one diff of one
 * file, newest commit first, which is the read `coverage-mirror.mjs` already makes.</p>
 *
 * <p>The separator is a word rather than `%x01`: SH859 has just spent a task removing the last raw
 * control character from this tree, and a marker that cannot occur in a diff of Markdown is all this
 * needs. The byte would arrive in git's output rather than in this file, and it is the shape somebody
 * copies into a source string next.</p>
 */
function shipCommits() {
  const log = git([
    "log", `-${WINDOW}`, "-p", "--unified=0", `--format=${COMMIT_MARK}%H`, "--", "docs/CHANGELOG.md",
  ]);
  if (!log) return new Map();
  const byId = new Map();
  let commit = null;
  for (const line of log.split("\n")) {
    if (line.startsWith(COMMIT_MARK)) {
      commit = line.slice(COMMIT_MARK.length).trim();
      continue;
    }
    const id = /^\+- \*\*(SH\d+)\*\*/.exec(line);
    // First wins: the log is newest first, so an id touched twice keeps the commit that shipped it.
    if (id && commit && !byId.has(id[1])) byId.set(id[1], commit);
  }
  return byId;
}

/**
 * Which of {@link SURFACES} has moved since `sha` — in a commit, or in the working tree.
 *
 * <p>The working tree half is not a convenience. A sweep and the ship that closes the block are one
 * commit, so a reader that only looked at history would fail on the run *before* that commit and
 * pass on the run after it — a gate nobody can satisfy while they are doing the work it asks for,
 * which is the shape people learn to skip. What is being asked is whether the sweep happened, and an
 * edit in the tree is an edit that happened.</p>
 *
 * <p><b>And the range starts one commit early, which this reader found by failing on itself.</b> The
 * first draft asked `sha..HEAD` and Block S — finished by SH861, whose own commit carries the
 * `shio-site` edits — came back `moved: nothing`. A block whose sweep lands **in** its closing commit
 * is the best case there is, and a rule that only counts what came after it would fail precisely the
 * blocks that got it right.</p>
 */
function movedSince(sha) {
  const pending = git(["status", "--porcelain", "--", ...SURFACES]) ?? "";
  const from = git(["rev-parse", "--verify", `${sha}^`])?.trim() ? `${sha}^` : sha;
  return SURFACES.filter((path) => {
    if (pending.split("\n").some((line) => line.slice(3).replace(/"/g, "").startsWith(path))) {
      return true;
    }
    const log = git(["log", "--format=%H", `${from}..HEAD`, "--", path]);
    return Boolean(log?.trim());
  });
}

/**
 * The finished blocks whose last ship is inside the window.
 *
 * <p>Finished is *the ledger records it and the roadmap declares no heading for it*, which is what
 * `roadkeep block drop` leaves behind: history keeps the heading it was filed under, and the backlog
 * does not carry one over nothing.</p>
 */
function recentlyFinished() {
  const ledger = readFileSync(join(ROOT, "docs", "CHANGELOG.md"), "utf8");
  const roadmap = readFileSync(join(ROOT, "docs", "ROADMAP.md"), "utf8");
  const open = openBlocks(roadmap);
  const shipped = shipCommits();
  const found = [];
  for (const [label, ids] of blocksIn(ledger)) {
    if (open.has(label) || !ids.length) continue;
    // By the LOG's order and not the ledger's. A block's entries are not written newest-first — the
    // first draft read them in file order and reported Block Q as finishing on SH829, thirty-one
    // entries before the one that actually closed it. `shipCommits` walks the log newest first, so
    // the first id in it that belongs to this block is the ship the block finished on.
    const own = new Set(ids);
    const last = [...shipped.keys()].find((id) => own.has(id));
    if (last) found.push({ label, id: last, at: shipped.get(last) });
  }
  return found;
}

test("SH861: every block that finished recently says whether its sweep was owed", () => {
  const finished = recentlyFinished();

  // Never green on nothing: git absent, a shallow clone or a rewritten history would otherwise read
  // exactly like a repository whose blocks are all swept — the failure every scan here is written
  // against. Said rather than asserted, because a contributor's clone legitimately has no depth.
  if (!finished.length) {
    console.log(`SH861: no block finished inside the last ${WINDOW} commit(s) — nothing to ask`);
    return;
  }

  console.log(`SH861: ${finished.length} recently finished block(s): `
    + finished.map((each) => `${each.label} (last ${each.id})`).join(", "));
  const undeclared = finished.filter((each) => !SWEEP[each.label]).map((each) => each.label);
  assert.deepEqual(undeclared, [],
    "a block reached zero and nothing says whether the three outward surfaces owe it a pass.\n"
      + "Declare it in SWEEP: `swept: true` with what moved, or `swept: false` with why no outward "
      + "surface describes what the block changed.\n"
      + "The upkeep rule names README.md, shio-site and docs-shio, and Block Q finished with one of "
      + "the three moved — because a different gate happened to ask about that one (SH861).");
});

test("SH861: a block that claims it was swept has moved a surface since its own last ship", () => {
  const finished = recentlyFinished().filter((each) => SWEEP[each.label]?.swept);
  if (!finished.length) {
    console.log("SH861: no recently finished block claims a sweep — nothing to corroborate");
    return;
  }

  const unpaid = [];
  for (const each of finished) {
    const moved = movedSince(each.at);
    console.log(`SH861: ${each.label} swept at ${each.id} — moved: ${moved.join(", ") || "nothing"}`);
    if (!moved.length) unpaid.push(`${each.label} (since ${each.id})`);
  }

  assert.deepEqual(unpaid, [],
    "the declaration says this block's sweep was done and no commit since its last ship touches "
      + "README.md, shio-site or the coverage mirror. A claim nobody paid for is worse than an "
      + "absent one: it is the sentence that makes the next reader stop looking (SH861).");
});

test("SH861: a block declared as owing no sweep has to say why", () => {
  // `swept: false` is verified by nothing — it is a judgement about what the block changed — so the
  // reason is the whole of it, and a reason a reader cannot disagree with is not one.
  const thin = Object.entries(SWEEP)
    .filter(([, claim]) => !claim.swept && String(claim.why ?? "").split(/\s+/).length < 12)
    .map(([label]) => label);

  assert.deepEqual(thin, []);
  for (const [label, claim] of Object.entries(SWEEP)) {
    assert.equal(typeof claim.why, "string", `${label} declares no reason at all`);
  }
});
