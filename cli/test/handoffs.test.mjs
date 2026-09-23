/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH792 — a sentence that hands a case to another instrument, and whether that instrument takes it.
 *
 * ## The failure this exists for
 *
 * `red-suites.test.mjs` excludes the `js` gate from its own comparison, for a good reason it states,
 * and adds: *nothing is lost — `ShDeclaredRedTest` reads the js stamp from inside the Java gate.*
 * That sentence was half true. The other file checked one of the two directions, so the repository's
 * only declared exception was judged by neither, and roughly fifty green runs never retired it
 * (SH789).
 *
 * The exclusion was right and its reason was right. What was wrong is the half that points at
 * somebody else's file: an exclusion is checkable because the code performs it, but the sentence
 * excusing it is **prose about another module**, as reliable as the day it was written and re-read
 * by nobody, because the file carrying it is green.
 *
 * This is SH583 one level up. That rule says two implementations of one idea agree only by accident;
 * this says two instruments *dividing* one idea are complementary only by accident, and the seam is
 * invisible from both sides.
 *
 * ## Why a classified list and not a ban
 *
 * Dividing work between two instruments is usually right — `ShAgentCsrfExemptionTest` proving the
 * effect while `ShCsrfExemptionLintTest` guards the configuration's shape is a better design than
 * one test doing both badly. The defect is never the division; it is that **nothing re-reads the
 * claim**. So this holds every such sentence, classified, asserted exactly — the shape
 * `ShAgentP1ConformanceTest` uses, and for its reason: gaining one must be a reviewable diff with a
 * verdict attached, rather than a number quietly moving.
 *
 * ## What the first run found
 *
 * Eleven sentences. Five are live hand-offs and **four of the five verify**; the fifth is SH789's,
 * already repaired. The rest are other shapes that read the same — a file explaining why it exists
 * *because* another does not cover something (the healthy direction), a file excluding itself from
 * its own walk and citing another as precedent for the technique, and one describing an exclusion
 * that has since been removed.
 *
 * So the answer to "were exclusions ever the cheap option they looked like" is: mostly yes, once.
 * That is worth saying out loud, because the opposite result was the expected one.
 */
import assert from "node:assert/strict";
import { readFile, readdir } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { join, extname, relative } from "node:path";
import { test } from "node:test";
import { isProse } from "./support/source-text.mjs";

// @scans prose reason: the subject IS the prose — a sentence excusing an exclusion by naming
// another instrument is a claim, and reading it is the whole point (SH740, SH792).

const ROOT = fileURLToPath(new URL("../../", import.meta.url));

/** How a sentence that names another instrument relates to this one. */
const Kind = {
  /** This instrument excludes a case and names another as covering it. The one that can rot. */
  HANDS_OFF: "hands-off",
  /** This instrument exists *because* another does not cover the case. Adds coverage, never assumes it. */
  REVERSE: "reverse",
  /** A file cutting itself out of its own walk, citing another as precedent for the technique. */
  SELF: "self-exclusion",
  /** Describes an exclusion that has since been removed. Kept as history, claims nothing now. */
  HISTORICAL: "historical",
  /**
   * States that a case is <b>not</b> covered — here or anywhere. The opposite of a hand-off and the
   * thing to write when tempted by one: it names the gap instead of asserting somebody else fills
   * it. Added when this reader caught SH794 writing one, which is the intended use.
   */
  UNCOVERED: "uncovered",
};

/**
 * Every sentence the scan finds, keyed `<file> -> <named instrument>`, with a verdict.
 *
 * <p>A `HANDS_OFF` entry must say how its claim was checked. The others say why they are not the
 * shape, which is the part a reader would otherwise re-derive.</p>
 *
 * <p>SH843 — bracketed as a <b>declaration</b>, so the ids these reasons name do not read as
 * assertions. `assertion-debt.test.mjs` counts every id on a line that is not a comment, and a
 * reason is a string in an object literal: code, by that rule. Eleven ids were credited from this
 * ledger and three across the tree were credited by <em>nothing else</em>.</p>
 */
/* debt-declaration:start — the reasons below name ids and assert none of them (SH843). */
const CLAIMS = {
  "cli/conformance/mcp-budget.conformance.mjs -> ShUnstableSortLintTest": {
    kind: Kind.HISTORICAL,
    why: "the exclusion it describes came off with SH757; the sentence is why the collision is "
      + "still seeded here, not a case being handed anywhere",
  },
  "cli/conformance/mcp-refusal.conformance.mjs -> ShTeachingErrorTest": {
    kind: Kind.REVERSE,
    why: "the header says the HTTP assertion does NOT cover the transport, and this file is the "
      + "cover — the healthy direction, where the gap is filled instead of pointed at",
  },
  "cli/test/help.test.mjs -> index.mjs": {
    kind: Kind.HANDS_OFF,
    why: "`help` is exempt from the flag lint because `index.mjs` answers it before the command "
      + "runs. Verified: cli/src/index.mjs handles `shio help <command>` itself (SH435), so the "
      + "exempted case is genuinely unreachable by the lint's subject",
  },
  "shio-app/src/test/java/com/viglet/shio/api/post/unified/ShHtmlSanitizerWriteBoundaryTest.java -> ShHtmlSanitizerTest": {
    kind: Kind.REVERSE,
    why: "the two halves of SH956, and neither excludes anything on the other's account. "
      + "ShHtmlSanitizerTest asserts the RULE — what an allow-list permits; this asserts WHERE it "
      + "runs, by writing a payload through create and update and reading the stored value back. "
      + "A sanitizer that is correct and wired into one writer is the defect, not the fix",
  },
  "cli/test/page-reference.test.mjs -> page-reference-drift.test.mjs": {
    kind: Kind.HANDS_OFF,
    why: "SH941's reader asserts the vendored page reference is present and internally coherent and "
      + "explicitly does NOT ask the package whether it is current, naming the other file as the "
      + "gate that does. Verified by running it: page-reference-drift.test.mjs spawns the bin's "
      + "`--check` against this repository and plants drift of each kind it can see — an edited "
      + "copy, an artboard the package no longer ships — requiring the check to name the file",
  },
  // SH862 removed `cli/test/known-debt.test.mjs -> floor-kind.test.mjs`, and it is worth saying why
  // rather than leaving a gap: that entry was the reader's own defect written down. Its `SELF`
  // reason said outright that the name came from *the ledger row being read* — a `path:` field on a
  // code line under a sentence about a staleness check three tests further down the same file. The
  // window stops at the prose now, so the reader no longer invents it and there is nothing to
  // classify. An entry that only existed because a scan over-matched is the shape SH794 named.
  "cli/test/regex-escape.test.mjs -> ShCsrfExemptionLintTest": {
    kind: Kind.UNCOVERED,
    why: "SH794's rule keys off the `@scans` declaration, which only the JS scanners carry, so the "
      + "Java ones are unchecked — and the sentence says exactly that rather than naming a file as "
      + "covering them. Caught by this reader on the run that added it, which is the first time it "
      + "fired on a claim being written rather than one already there",
  },
  "cli/test/red-suites.test.mjs -> ShDeclaredRedTest": {
    kind: Kind.HANDS_OFF,
    why: "the js gate is excluded here and this named the Java comparator as covering it. It did "
      + "not, in one of the two directions — the defect SH789 found and fixed. Verified now: "
      + "ShDeclaredRedTest holds both an undeclared-red and an outlived-declaration check",
  },
  "cli/test/script-paths.test.mjs -> assertion-debt.test.mjs": {
    kind: Kind.SELF,
    why: "`REM` lines are cut from its own walk, citing how assertion-debt separates mentioning an "
      + "id from asserting it — a precedent for the distinction, not a promise that file reads a "
      + "single `.cmd`. Without the exemption the fix could not document the path it replaced",
  },
  "cli/test/snapshot.test.mjs -> browser-refusal.test.mjs": {
    kind: Kind.REVERSE,
    why: "this test claims to notice something the other's resolution guard cannot, by using the "
      + "packages rather than resolving them; nothing is excluded here on its account",
  },
  "cli/test/source-corpus.test.mjs -> assertion-debt.test.mjs": {
    kind: Kind.SELF,
    why: "`INERT_KEYS` is cut from its own walk, citing how assertion-debt cuts its own "
      + "declaration — a precedent for the technique, not a promise that file covers anything",
  },
  "cli/test/test-runner-flags.test.mjs -> test-watchdog.test.mjs": {
    kind: Kind.HANDS_OFF,
    why: "SH814 — the lint asks only *where* the watchdog is installed and hands the question of "
      + "whether it works to the other file. Verified: test-watchdog.test.mjs spawns a suite that "
      + "leaks a listening server and asserts a non-zero exit naming `TCPServerWrap`, plus the "
      + "converse that a clean suite finishes well inside the deadline — so the mechanism is "
      + "covered there and this file may confine itself to the nine package.json scripts",
  },
  "shio-app/src/test/java/com/viglet/shio/ShDeclaredRedTest.java -> red-suites.test.mjs": {
    kind: Kind.HANDS_OFF,
    why: "SH800 — this comparator stopped judging `java` once that gate had a stamp, and says the "
      + "other reader judges it. Written with the words `nothing is lost`, which is the SH789 "
      + "sentence verbatim, and caught by this reader on the run that added it. Verified: "
      + "red-suites.test.mjs filters only its own SELF (`js`), so `java` is in its judged set, and "
      + "SH798's wrapper test holds the two exclusions to being exactly the two comparator hosts",
  },
  "cli/test/live-coverage.test.mjs -> ShRedirectOpTest": {
    kind: Kind.HANDS_OFF,
    why: "SH1064 — two declarations (the verb `redirects`, the op `post.redirect`) say the live "
      + "tier does not drive them and name the Java test as what does. Verified: ShRedirectOpTest "
      + "runs the op through the batch service — the create publishes, a re-run is a noop, a page "
      + "at the address is refused with `redirect-path-occupied` — and drives `verify` for the "
      + "`url-orphaned` finding and its disappearance after the redirect is written. What it does "
      + "NOT cover is the wire, which is exactly what the two declarations claim is uncovered",
  },
  "shio-app/src/test/java/com/viglet/shio/api/agent/ShAgentCanonicalSlashTest.java -> ShRenderRedirectTest": {
    kind: Kind.HANDS_OFF,
    why: "asserts the apply half and says the route half is covered elsewhere. Verified: "
      + "ShRenderRedirectTest asserts `/docs` 301s to the stored `/docs/`, which is that half",
  },
  "shio-app/src/test/java/com/viglet/shio/api/agent/ShAgentChangesTest.java -> ShAgentCommitOrderTest": {
    kind: Kind.HANDS_OFF,
    why: "does not assert what the watermark holds back and names the other test as doing so. "
      + "Verified: ShAgentCommitOrderTest injects ShAuditFeedWatermark, holds a position and "
      + "asserts the feed's ceiling stays below it",
  },
  "cli/test/assertion-debt.test.mjs -> handoffs.test.mjs": {
    kind: Kind.HANDS_OFF,
    why: "SH843 — the debt reader says two ledgers carry its declaration markers now, this one among "
      + "them, so the ids their reasons name stop reading as asserted. Verified by the run rather "
      + "than by the sentence: cutting the region here removes credits and leaves this file's own "
      + "test names paying, which is the pair `aReasonFieldCreditsNothing` asserts",
  },
  "cli/test/assertion-debt.test.mjs -> ShAgentReadTest": {
    kind: Kind.HISTORICAL,
    why: "SH843 — a DEBT comment explaining why SH641 LOOKED like debt: its deliverable is an id "
      + "deliberately not written in that file. It claims nothing now, and the paragraph says so: "
      + "the id was added to DEBT in the same edit and taken straight back out, because the reader "
      + "answered that it is asserted elsewhere. Kept because being wrong about it is the finding",
  },
  "shio-app/src/test/java/com/viglet/shio/api/agent/ShAgentJsonLeanTest.java -> ShAgentResponseBudgetTest": {
    kind: Kind.HANDS_OFF,
    why: "SH842 — this file asserts the header's NAME and says the assertion that would have caught "
      + "SH113 is the one measuring a response against its ceiling. Written while paying that id's "
      + "debt and caught by this reader on the same run, which is the intended use. Verified in the "
      + "same commit rather than assumed: `everyCanonicalResponseStaysWithinItsBudget` in "
      + "ShAgentResponseBudgetTest now names SH113 in its own failure message, and it is the "
      + "assertion that fails when a payload grows past its ceiling",
  },
  "shio-app/src/test/java/com/viglet/shio/api/agent/ShAgentReadTest.java -> assertion-debt.test.mjs": {
    kind: Kind.SELF,
    why: "an id is deliberately NOT written here so the debt walk does not credit it (SH641) — an "
      + "omission to protect another reader, not a case handed to it",
  },
  "shio-app/src/test/java/com/viglet/shio/spring/security/ShAgentCsrfExemptionTest.java -> ShCsrfExemptionLintTest": {
    kind: Kind.HANDS_OFF,
    why: "proves the effect and says the lint guards the configuration's shape. Verified: "
      + "ShCsrfExemptionLintTest asserts over the exemption list, refusing a blanket matcher not "
      + "wrapped in ShStatelessRequestMatcher",
  },
};

/* debt-declaration:end */

/**
 * A sentence saying this instrument is not doing something, or that another is.
 *
 * <p>The leading `\b` is load-bearing and was missing on the first run: without it `exempt` matches
 * inside <b>ShCsrfExemption</b>{@code LintTest}, so every file that merely names that class read as
 * a hand-off — three of them. A guard for unread claims whose own regex over-matches would have
 * taught exactly the wrong lesson about how much of this there is.</p>
 */
const HANDS_OFF = new RegExp(`\\b(?:${[
  "nothing is lost", "covered by", "covers it", "is the one that", "does it instead",
  "belongs (?:here|there|to)", "its own test", "handled (?:by|there)",
  "that is [A-Za-z0-9.]+'s (?:job|question)", "deliberately (?:not|does not)", "excluded", "exempt",
  "cannot judge", "does not (?:ask|check|assert|cover|judge)", "leaves .* to", "reads .* from inside",
].join("|")})`, "i");

/** Another instrument, by the two names this repository gives them. */
const NAMES_ANOTHER = /\b([A-Z][A-Za-z0-9]*Test|[a-z0-9-]+\.(?:test|conformance)\.mjs|[a-z0-9-]+\.mjs)\b/;

/** How far a wrapped sentence may run past the line that triggered it. */
const WRAP = 3;

/**
 * The instrument a hand-off sentence names, or null — reading **prose only** (SH862).
 *
 * <h3>The window ran into the code below it</h3>
 *
 * <p>The trigger has always been a comment line: {@link isProse} answers that, and it is right. The
 * *name* was then looked for across the next three lines whatever they were, so a sentence about an
 * exemption sitting directly above a call whose argument is a path read that argument as the
 * instrument being handed to. Measured on SH859's commit — twice on one commit:</p>
 *
 * <pre>  // SH859 — no file is exempt any more, and this is what says so
 *   assert.deepEqual(offences("cli/src/diff.mjs", …), …)</pre>
 *
 * <p>reported `control-characters.test.mjs -> diff.mjs`, a hand-off nobody wrote: that string is an
 * argument to the function under test and the assertion says nothing whatever about that module. The
 * author's only way out was to rename the fixture, which is a workaround the next author cannot read.
 * SH794 and SH824 are the same shape one construct along — <b>a scan over prose has to say which
 * construct it reads</b>, and a string handed to a call is not prose.</p>
 *
 * <p>So the window stops at the first line that is not prose. A wrapped sentence continues on the
 * next <em>comment</em> line — which is where SH789's own was, the case the window exists for — and a
 * line of code ends it.</p>
 */
export function namedBy(lines, at) {
  const window = [];
  for (let i = at; i < Math.min(at + WRAP, lines.length); i += 1) {
    if (!isProse(lines[i])) break;
    window.push(lines[i]);
  }
  const named = NAMES_ANOTHER.exec(window.join(" "));
  return named ? named[1] : null;
}

const isSuite = (path) => /\.test\.mjs$|\.conformance\.mjs$|Test\.java$|IT\.java$/.test(path);

async function suiteFiles(dir, out = []) {
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    if (["node_modules", "target", ".git", "dist"].includes(entry.name)) continue;
    const path = join(dir, entry.name);
    if (entry.isDirectory()) await suiteFiles(path, out);
    else if ([".mjs", ".java"].includes(extname(entry.name)) && isSuite(path)) out.push(path);
  }
  return out;
}

/** Every `<file> -> <named instrument>` a prose line in a suite claims. */
async function claimsFound() {
  const found = new Map();
  const files = [
    ...await suiteFiles(join(ROOT, "cli")),
    ...await suiteFiles(join(ROOT, "shio-app/src/test")),
  ];
  for (const path of files) {
    // This file is cut from its own walk, and the irony is the point: it is a `SELF` exclusion of
    // exactly the kind it catalogues. Unavoidable and stated rather than quiet — the header above
    // has to name the instruments it is about, in sentences about exclusion, so read raw it reports
    // half a dozen hand-offs that are really its own documentation. `assertion-debt.test.mjs` and
    // `source-corpus.test.mjs` cut themselves for the same reason, which is why both are in CLAIMS.
    if (path.endsWith("handoffs.test.mjs")) continue;
    const lines = (await readFile(path, "utf8")).split("\n");
    const self = path.split(/[/\\]/).pop();
    lines.forEach((line, at) => {
      if (!isProse(line) || !HANDS_OFF.test(line)) return;
      // A wrapped sentence puts the name on the next line, which is where SH789's own was — and only
      // as far as the prose goes, which is SH862.
      const named = namedBy(lines, at);
      if (!named || named === self) return;
      const key = `${relative(ROOT, path).replace(/\\/g, "/")} -> ${named}`;
      if (!found.has(key)) found.set(key, at + 1);
    });
  }
  return found;
}

test("SH792: every sentence handing a case to another instrument is classified", async () => {
  const found = await claimsFound();
  const declared = Object.keys(CLAIMS).sort();

  assert.deepEqual(
    [...found.keys()].sort(),
    declared,
    "a suite says another instrument covers something, and this file does not say whether it "
      + "does.\nIn `+ actual`: classify it. If it is `hands-off`, GO AND READ the named "
      + "instrument first — that sentence is prose about another module, and the one time nobody "
      + "checked, it was wrong for months while both files stayed green (SH789).\nIn `- expected`: "
      + "the sentence was reworded or deleted, so drop the entry.\n"
      + "What this reader will NOT report, so you do not have to rename anything to escape it "
      + "(SH862): a filename on a line of CODE below the sentence. A wrapped sentence continues on "
      + "the next comment line and a call's argument is not prose, so `f(\"cli/src/x.mjs\", …)` "
      + "under a comment about an exemption is not a hand-off.",
  );
});

test("SH862: the name comes from the sentence, not from the code the sentence sits above", () => {
  // The measured shape, verbatim: a comment about an exemption directly above a call whose argument
  // is a path. It fired twice on one commit and the only way out was renaming the fixture.
  const overCode = [
    "  // SH859 — no file is exempt any more, and this is what says so.",
    '  assert.deepEqual(offences("cli/src/diff.mjs", `a${NUL}b`), []);',
  ];
  assert.equal(namedBy(overCode, 0), null,
    "a string handed to a call is an argument, and the assertion says nothing whatever about that "
      + "module — SH794 and SH824 are the same over-match one construct along");

  // And the case the window exists for, which SH789's own sentence had: the name wrapping onto the
  // next line OF PROSE. Narrowing that away would trade this defect for the one SH789 found.
  const wrapped = [
    "  // this case is covered by",
    "  // ShAgentCommitOrderTest, which holds a watermark",
    "  const x = 1;",
  ];
  assert.equal(namedBy(wrapped, 0), "ShAgentCommitOrderTest");

  // A blank line between the sentence and the code ends it too — `isProse` is false for both, and
  // the window has to stop at the first of them or the gap merely moves the problem down one line.
  assert.equal(namedBy(["  // deliberately not covered", "", '  read("floor-kind.test.mjs");'], 0),
    null);
});

test("SH792: a hand-off entry says how its claim was checked, in words a reader can re-run", () => {
  const unchecked = Object.entries(CLAIMS)
    .filter(([, claim]) => claim.kind === Kind.HANDS_OFF && !/verified/i.test(claim.why))
    .map(([key]) => key);
  assert.deepEqual(
    unchecked,
    [],
    "a `hands-off` claim with no `Verified:` in its reason is the original defect written down "
      + "rather than fixed — the whole finding is that these read as settled while unread.",
  );
});

test("SH792: the scan is not vacuous — it finds the claim that was actually false", async () => {
  // The reader's own proof. SH789's sentence is the one this file exists because of, it is a
  // *wrapped* one so it exercises the three-line window, and a scan that stopped seeing it would
  // report a clean repository. Asserted by key rather than by count, because a count is satisfied
  // by any eleven sentences.
  const found = await claimsFound();
  assert.ok(
    found.has("cli/test/red-suites.test.mjs -> ShDeclaredRedTest"),
    `the scan found ${found.size} claim(s) and not the one it was written for; either the sentence `
      + "moved or the window stopped matching, and either way this file is now guarding nothing",
  );
  assert.ok(
    Object.values(CLAIMS).filter((claim) => claim.kind === Kind.HANDS_OFF).length >= 4,
    "and the classification is not all-one-bucket, which would mean the kinds are decoration",
  );
});
