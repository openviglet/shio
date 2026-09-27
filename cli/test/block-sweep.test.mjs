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
  AY: {
    swept: true,
    why: "what the conformance instruments cannot see: a line of instrument rounds and their "
      + "corrections, ending with SH1162 (a socket premise measured on one platform) and SH1147 (the last "
      + "hand-written comment strippers). docs-shio moved: COVERAGE's instruments row names both and the "
      + "mirror is regenerated. README and shio-site are NOT moved: neither describes the test "
      + "instruments, and no count or claim they state changed",
  },
  AU: {
    swept: true,
    why: "the design system's consumer contract, adopted and gated: the page reference vendored and "
      + "held against drift, the canvas this repository draws in, and the round closing with SH944 (the "
      + "viglet-ds plugin enabled, carrying the skill while only the artboards stay vendored) and SH948 "
      + "(copy-ds.cmd through the latest worktree junction). docs-shio moved: COVERAGE's Block AU and "
      + "instruments rows name both, with the mirror regenerated. README and shio-site are NOT moved: "
      + "neither mentions the design system, its plugin or the local loop",
  },
  BG: {
    swept: true,
    why: "the console as an application, cost and access: a bundle and a per-route cost budget, "
      + "cancellable reads through one query layer, failures that stay in their screen, and the access "
      + "half closing with SH1053 (one main, a skip link first, focus that follows the page), SH1054 "
      + "(axe on every route and at three widths, the document's language, dark: following the theme) "
      + "and SH1051 (the accent measured to AA and primary claimed). docs-shio moved: content-console "
      + "gained the keyboard paragraph and COVERAGE names every id, with the mirror regenerated. README "
      + "and shio-site are NOT moved: neither states anything about the console's cost or accessibility",
  },
  BH: {
    swept: true,
    why: "content i18n as a decision rather than a description: a declared locale policy, the locale "
      + "segment selecting the translation along its fallback chain, locale on every CDA and GraphQL "
      + "read, staleness marks, the coverage board and workspace, the default-language chip, and "
      + "SH1130's verify warnings for a language promised and never written. docs-shio moved: "
      + "content-i18n gained the verify section and COVERAGE's content i18n row names every id, with "
      + "the mirror regenerated; the curator hub already routes to that page. README and shio-site are "
      + "NOT moved: neither makes a claim about languages, so no outward sentence became untrue",
  },
  BF: {
    swept: true,
    why: "the visual editor as a composer: an independent Duplicate, rail thumbnails and a drop caret, "
      + "section-not-accepted in verify, patterns pushed and placed from the rail, a save that names "
      + "what moved, and share links narrowed to one page and listable. docs-shio moved: the Universal "
      + "Editor page gained composing, patterns and the save line, content-lifecycle gained the share "
      + "link section, the verify table gained section-not-accepted, and COVERAGE names every id and "
      + "the block. README moved (47 verbs, for shio share). shio-site did NOT move: its verb list is a "
      + "sample, and nothing it claims about the editor or preview changed",
  },
  BE: {
    swept: true,
    why: "ten tasks on media: what an asset costs to list, where it is used, its alt text, its words in "
      + "the tree, replacing its bytes, and two storage corrections (SH1099's PostgreSQL text, SH1100's "
      + "dropped reference tables). docs-shio's COVERAGE.md names all ten: the Media Library row carries "
      + "SH1029-SH1035 and SH1098, the full-text search row SH1099, the internal row SH1100, and the "
      + "mirror is regenerated. README and shio-site are NOT moved and that is the finding: neither "
      + "describes the media library, asset usage or the database, and no count they state changed",
  },
  BB: {
    swept: false,
    why: "twelve tasks building the curator's return channel: notes anchored to a page, per-change "
      + "triage and verdicts an agent can read, the Activity trail's live line, and the dock's "
      + "conversation with the agent. README and shio-site make no "
      + "claim about notes, triage or the dock, so no outward sentence became untrue. docs-shio is "
      + "where this block's story belongs, and the ids it has not named yet are on the coverage "
      + "reader's list and in the vigletdocs pass still waiting to be committed (see SH1072); "
      + "`docs/agents/console-ui.md` and `mcp.md` carry the decisions",
  },
  AZ: {
    swept: false,
    why: "six tasks giving a page a revision history an agent and a curator can read, roll back and "
      + "compare (SH985-SH989 and SH1080). README says nothing about versions, and shio-site's one sentence, "
      + "'draft-published diff and restore', stayed true and only became truer: the diff now spans "
      + "any two revisions and a created page. The outward prose that does describe revisions and "
      + "rollback is docs-shio's content-lifecycle page, written by another session's docs pass that "
      + "is still uncommitted in vigletdocs (see SH1072, set aside for that same pass), so the coverage "
      + "mirror moves when that lands rather than here; `docs/agents/console-ui.md` carries SH988",
  },
  AW: {
    swept: false,
    why: "seven tasks retiring the console's second chrome, closed by SH963 deleting the root "
      + "provider once design system 2026.3.11 removed the switch. Every surface a curator sees was "
      + "already bento before the block opened (Block AR's cutover), so what moved was the machinery "
      + "that could have rendered the other look: the provider, the hook, the branches that read it "
      + "and the prose that narrated them. None of README, shio-site or docs-shio describes a chrome "
      + "switch, so no outward sentence became untrue; `docs/agents/console-ui.md` carries the change, "
      + "and the census that was the block's criterion reads zero",
  },
  AV: {
    swept: true,
    why: "nine defect fixes, and seven of them change what a reader can do or see, so the sweep is "
      + "docs-shio. security.md gains a section on uploaded files and rich text — the headers every "
      + "asset now carries, the upload policy and its three verdicts, the allow-list a rich-text save "
      + "runs, and a download name that stays itself (SH950, SH951, SH956, SH1071); mcp.md lists the "
      + "four op fields shio_write now declares and the digest shio_read returns for `expect` (SH952, "
      + "SH1073); content-console.md says what a refused save and a partly refused batch delete tell "
      + "a curator (SH953, SH955). SH954 deleted a button no page had ever described. COVERAGE.md "
      + "names all nine and the mirror is regenerated. README and shio-site are NOT moved, and that "
      + "is the finding: neither describes asset headers, upload policy, the console's error "
      + "reporting or shio_write's argument list, and the tool count did not change",
  },
  AR: {
    swept: true,
    why: "the console cutover block, and the sweep is docs-shio: COVERAGE.md now names SH927 and "
      + "SH935 beside the four this block's instruments row already carried, so every id it shipped "
      + "is accounted for in its cluster. README and shio-site are NOT moved and that is the finding "
      + "rather than an omission — the README mentions the console only as a pointer to "
      + "docs/agents/console-ui.md and shio-site does not describe the console at all, so neither "
      + "carries a sentence this block made untrue. The user-facing half — /console becoming "
      + "/bento, the command palette, the tenant switch and the pending-review count — was "
      + "already carried by the administration guide as those tasks shipped",
  },
  AQ: {
    swept: false,
    why: "seventeen tasks adopting the shared bento layer, and almost all of them are instruments "
      + "rather than surfaces — a page census, a surface digest, an era classifier, an i18n reader, "
      + "the duplicate gate, and the provider wiring that lets one form render in two shells. They "
      + "change what a contributor may write and nothing a curator or an agent can do. The block's "
      + "two outward consequences, the move from `/console` to `/bento` and the Ctrl/Cmd+K palette, "
      + "are already carried by docs-shio's administration guide, including the note that old "
      + "addresses redirect; they arrived with Block AR's surface work rather than with this "
      + "block's closing line, which was SH919 deleting a test workaround once the design system "
      + "shipped VDS78",
  },
  AT: {
    swept: false,
    why: "three tasks about this repository's own build and its own reading of itself — SH916 removed "
      + "six Tika declarations nothing imported, taking 23 compile-scope artifacts with them; SH917 "
      + "made the silent-catch inventory count decisions rather than catch blocks; and SH939 deleted "
      + "an uncalled method whose declaration left a second org.json.JSONObject on the test "
      + "classpath, where classpath order decided which one a test resolved. All three change what a "
      + "contributor may leave in the POM or write in a handler; none changes anything a curator or "
      + "a reader of the product can do. `docs/agents/build.md` carries all three, and docs-shio's "
      + "internal cluster already accounts for instruments of exactly this kind",
  },
  AS: {
    swept: true,
    why: "SH912, SH913 and SH914 all change what a reader gets out of search, so docs-shio's "
      + "COVERAGE.md Turing row names each: a failed push retried before it is reported, an "
      + "attachment's text reaching the index through Turing's OCR, and a post-type field declaring "
      + "itself indexed with a type so a facet can filter on it. README and shio-site are NOT "
      + "moved and that is the finding rather than an omission — neither documents Turing "
      + "configuration at all; the README links the product and shio-site points at the roadmap, "
      + "so there was no sentence in either that this block made untrue",
  },
  S: {
    swept: true,
    why: "SH930 closed it a third time and the sweep is the decision it recorded: the coverage matrix "
      + "and shio-site's own backlog now say how far the em-dash rule reaches — sample content a "
      + "blueprint publishes, not the field help a curator reads",
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
    why: "SH879 and SH880, both instruments about this project's own reading of itself - which "
      + "source shapes the corpus can produce, and which branch of the generated layout any render "
      + "has ever reached. SH880 found a live defect doing it, and the defect was invisible in "
      + "exactly the direction an outward surface cannot describe: a nav that renders either way. "
      + "SH879 asks which SOURCE shapes the corpus can produce — the question SH833 and SH838 ask "
      + "of this repository's own mechanisms, turned outward. It changes what a contributor may "
      + "leave unmeasured and nothing a reader of the product can do; `docs/agents/cli.md` group N "
      + "carries it, and shio-site's README records the closure with the same verdict",
  },
  J: {
    swept: true,
    why: "SH884 - `docs-shio`'s blueprints page says an applied section package arrives editable on "
      + "the page, page title and description included, which is a thing a curator can now do and "
      + "could not before. SH881 owed no sweep on its own (a post-type both packages already "
      + "carried, and the check that holds a directory against its manifest); it is recorded here "
      + "rather than in a second entry because a block declares one verdict. The old reason: "
      + "SH881 lists a post-type both section packages already carried, and adds the check that "
      + "holds a package's directory against its manifest. What a reader of the product can do is "
      + "unchanged - an applied package installs one more type it was always meant to - and no "
      + "outward surface states a package's post-type count as a promise; `docs/agents/"
      + "blueprints.md` carries it, and the one historical count there now says it is historical",
  },
  K: {
    swept: true,
    why: "SH882, SH883 and SH885 - `docs-shio`'s replication page names `shio annotations` where it had "
      + "only told a reader to count props by hand, and says what the check now reports and what it "
      + "no longer "
      + "fails on. It is one of the few instrument fixes that IS an outward claim: the check is the "
      + "one a reader runs to decide whether a replica is authorable. SH883 narrowed what counts as "
      + "drawn and changed no sentence there - the page describes what the check reports, not how "
      + "it decides, and SH885 is the same: it corrects the denominator, and the sentence that "
      + "sends a reader to the command is true either way",
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
  return log ? shipsIn(log) : new Map();
}

/** The parse {@link shipCommits} runs, separated from `git` so the rule below can state it. */
function shipsIn(log) {
  const byId = new Map();
  let commit = null;
  let added = new Map();
  let removed = new Set();

  /*
   * SH911 — an id that leaves as well as arrives was **amended**, not shipped.
   *
   * `roadkeep record amend` rewrites an entry in place, so its diff carries the id on a `-` line
   * and again on a `+` one. Reading only the `+` side dates a years-old entry to the commit that
   * corrected a word in it — and since a block is "finished" here when the ledger holds it and the
   * roadmap declares no heading, every long-closed block whose prose was touched reads as having
   * finished *today* and owing a four-surface sweep.
   *
   * Measured: SH910 deleted forty files and corrected the seven ledger entries whose prose named
   * them, and the next run demanded sweeps for Blocks O, E and G — none of which had shipped
   * anything in months. The correction is the one signal the diff already carries.
   */
  const settle = () => {
    for (const [id, sha] of added) {
      if (!removed.has(id) && !byId.has(id)) byId.set(id, sha);
    }
    added = new Map();
    removed = new Set();
  };

  for (const line of log.split("\n")) {
    if (line.startsWith(COMMIT_MARK)) {
      settle();
      commit = line.slice(COMMIT_MARK.length).trim();
      continue;
    }
    // First wins: the log is newest first, so an id touched twice keeps the commit that shipped it.
    const gained = /^\+- \*\*(SH\d+)\*\*/.exec(line);
    if (gained && commit && !added.has(gained[1])) added.set(gained[1], commit);
    const lost = /^-- \*\*(SH\d+)\*\*/.exec(line);
    if (lost && commit) removed.add(lost[1]);
  }
  settle();
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

test("SH911: an amended ledger entry is a correction, not a ship", () => {
  /*
   * The diff SH910 actually produced, in miniature: one entry written for the first time, and one
   * whose prose was corrected because it named a file that commit deleted. Both arrive as `+`
   * lines, and only the second also leaves as a `-` one.
   */
  const log = [
    `${COMMIT_MARK}aaaa111`,
    "+- **SH9301** — Forty files deleted by a reachability walk.",
    "-- **SH9302** — **\"View Site\" goes somewhere that exists** ([site.list.page.tsx](../old)).",
    "+- **SH9302** — **\"View Site\" goes somewhere that exists** (`site.list.page.tsx`, deleted).",
  ].join("\n");

  const ships = shipsIn(log);

  assert.deepEqual([...ships.keys()], ["SH9301"],
    "SH911: an entry that leaves and arrives in one commit was amended in place, and dating it to"
      + " that commit reports every long-closed block whose prose was touched as having finished"
      + " today. SH910 corrected seven such entries and this reader demanded a four-surface sweep"
      + " for Blocks O, E and G, none of which had shipped in months.")
  assert.equal(ships.get("SH9301"), "aaaa111",
    "SH911: a genuine ship lost its commit — the amendment rule must not swallow the ids it is"
      + " written to keep, which is the direction that fails silently: no block ever looks"
      + " finished and this whole reader goes quiet.")
})

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
