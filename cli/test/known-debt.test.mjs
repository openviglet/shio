/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH594 — the ledgers of what is knowingly wrong, and one reader that can answer for all of them.
 *
 * ## The device is sound and it has been invented five times
 *
 * | where | what it holds | since |
 * |---|---|---|
 * | `shio/verify-baseline.json` | accepted `verify` findings, per project | SH142 |
 * | `cli/test/assertion-debt.test.mjs` `DEBT` | shipped ids carrying no assertion | SH527 |
 * | `fixtures/source-sites/open-findings.json` | corpus assertions red at HEAD | SH560 |
 * | `red-suites.json` | a suite allowed to be red, with its reason | SH579 |
 * | `cli/conformance/docs-open-findings.json` | published examples red against a live instance | SH499 |
 *
 * <p>The fifth arrived after this file existed and cost one row in `LEDGERS`, which is the whole
 * claim the paragraph below makes. It was invented anyway — SH499 reached for the shape without
 * reading this file — so the device being obvious is confirmed rather than contradicted; what this
 * reader prevents is the fifth being invented *and left unjoined*.</p>
 *
 * Each is a **named list rather than a ceiling**, for the same stated reason every time: a ceiling
 * lets one task pay the debt and the next spend it. Each is checked in both directions, so an entry
 * that goes green fails the run. Each was the right call on its own.
 *
 * Together they are four formats, four readers and four places to look, and nothing joined them.
 *
 * ## What this reader adds, and what it deliberately does not
 *
 * It does **not** merge the files. Their scopes are genuinely different and merging them would
 * throw away the per-ledger checks that make each one honest. It answers the one question none of
 * them can: *what is this repository knowingly carrying, and which of it has an owner who has
 * already gone.*
 *
 * That second half is not hypothetical. `open-findings.json` held a red case owned by **SH555 after
 * SH555 had shipped** — the fix landed on the render side and the CLI's generated layout, which the
 * corpus reads, still emitted `{{CSS}}`. Only a check written in the same round noticed, and it had
 * to read `docs/ROADMAP.md` to do it. An entry whose owner has shipped is the worst state a debt
 * ledger has: it reads as tracked work when the person who would have done it has moved on.
 *
 * This is SH583's shape one level up — five places decided what a trailing slash meant and they
 * agreed only by accident. Four ledgers agree about what is knowingly broken only while nobody's
 * debt is anybody else's.
 */
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { join } from "node:path";
import { test } from "node:test";
import { masked } from "./support/source-text.mjs";

// @scans both reason: the governed ledgers are Markdown and the declared-debt entries this reads
// back are prose by construction — but three rows slice a declaration out of a *source* file, and
// there the brackets in somebody's explanation are not the brackets being matched (SH740, SH786).

const ROOT = fileURLToPath(new URL("../../", import.meta.url));

/**
 * The text a declaration encloses, found by matching its bracket rather than by searching for one.
 *
 * <p>SH785 — three rows here sliced a declaration by hand, from {@code const X = [} to the next
 * {@code "];"}. {@code DEBT} closes with {@code ].sort(byId);}, so that search ran three hundred
 * lines past the end, swallowed the next declaration whole, and reported 144 entries for a list of
 * 120. The guard beside it passed, because a scan that looks for <em>a</em> terminator always finds
 * one somewhere later in the file — which is what makes this a class rather than an instance.</p>
 *
 * <p>SH786 — and it counts brackets on {@link masked}, not on the file. Every bracket in these
 * declarations is either the structure being matched or a character in somebody's explanation of an
 * entry, and only the first kind may move the depth. That is not a precaution: `AT_THE_WALL`'s
 * reasons hold eleven pairs of parentheses inside Java strings, and the count came out right because
 * all eleven happen to balance. The mask is the same length as the file, so an offset found in it
 * addresses the original — which is the whole reason {@link code} could not be used here.</p>
 *
 * @param text the file
 * @param open the declaration's opening text, ending in its bracket — e.g. {@code const DEBT = [}
 * @returns what the bracket encloses, exclusive of the brackets themselves
 */
function enclosedBy(text, open) {
  const from = text.indexOf(open);
  assert.ok(from >= 0, `the declaration \`${open}\` is no longer there to read`);
  const bracket = open.at(-1);
  const close = { "[": "]", "(": ")", "{": "}" }[bracket];
  assert.ok(close, `\`${open}\` does not end in a bracket, so nothing can be matched to it`);
  const structure = masked(text);
  let depth = 0;
  for (let at = from + open.length - 1; at < structure.length; at += 1) {
    if (structure[at] === bracket) depth += 1;
    else if (structure[at] === close) {
      depth -= 1;
      if (depth === 0) return text.slice(from + open.length, at);
    }
  }
  assert.fail(`\`${open}\` is never closed — the declaration this row reads is malformed`);
}

/**
 * The four, each with how to read it and what an entry's *owner* is.
 *
 * <p>A table rather than four bespoke readers, because the point of this file is that the four are
 * one population — and the moment a fifth ledger is invented, adding it here is one row rather than
 * a fifth reader nobody joins to the others.</p>
 */
const LEDGERS = [
  {
    name: "open-findings.json",
    path: "fixtures/source-sites/open-findings.json",
    holds: "corpus assertions red at HEAD",
    since: "SH560",
    async entries(text) {
      return (JSON.parse(text).findings ?? []).map((row) => ({
        owner: row.id ?? null,
        what: `${row.archetype}/${row.assertion}`,
      }));
    },
  },
  {
    name: "red-suites.json",
    path: "red-suites.json",
    holds: "a suite allowed to be red, with its reason",
    since: "SH579",
    async entries(text) {
      return (JSON.parse(text).exceptions ?? []).map((row) => ({
        owner: row.task ?? row.id ?? null,
        what: row.suite ?? "(unnamed suite)",
      }));
    },
  },
  {
    name: "assertion-debt DEBT",
    path: "cli/test/assertion-debt.test.mjs",
    holds: "shipped ids carrying no assertion",
    since: "SH527",
    async entries(text) {
      // Its own declaration, read the way that file reads itself: the arrays between the markers.
      // SH785 — matched, not searched for: `DEBT` closes `].sort(byId);` and the old search for
      // `];` ran on to the next one three hundred lines later.
      //
      // Both declarations, because both are carried. SH642 split one number into two lists — work
      // with nothing standing behind it, and work whose test exists without naming the id — and the
      // assertion that holds the debt is over `[...DEBT, ...REVEALED_IDS]`. Reading one of them
      // would answer this file's question with two thirds of its subject missing, which is the same
      // defect as reading too much and only looks more careful.
      const ids = ["const DEBT = [", "const REVEALED = {"]
        .flatMap((open) => [...enclosedBy(text, open).matchAll(/"(SH\d+)"/g)].map(([, id]) => id));
      // Every entry IS its own owner here — the debt is "this shipped id has no assertion" — so the
      // shipped check below would fire on all of them. That is the one ledger where a shipped owner
      // is the normal state rather than the defect, which is why it declares `ownerIsSubject`.
      return ids.map((id) => ({ owner: id, what: `${id} carries no assertion` }));
    },
    ownerIsSubject: true,
  },
  {
    // The fifth, and the first added the way this file said one should be: a row, not a reader.
    name: "docs-open-findings.json",
    path: "cli/conformance/docs-open-findings.json",
    holds: "published examples whose live request is red at HEAD",
    since: "SH499",
    async entries(text) {
      return (JSON.parse(text).findings ?? []).map((row) => ({
        owner: row.id ?? null,
        what: row.request ?? "(unnamed request)",
      }));
    },
  },
  {
    // The sixth, and the one this file named in advance and then did not catch (SH741). SH672
    // built `TOLERATED` in the shape assertion-debt uses and said so in its header — accepted
    // exemptions, a written reason beside each — and never joined here, so the count above read
    // five while six existed. Its entries name a *floor* rather than a task, which is why the
    // staleness check below is its own test: the shipped/open owner checks have nothing to bite on.
    name: "floor-kind TOLERATED",
    path: "cli/test/floor-kind.test.mjs",
    holds: "absolute floors over a shrinking corpus, each with its reason",
    since: "SH672",
    async entries(text) {
      // SH785 — matched rather than searched for, like the DEBT row above and for its reason.
      const slice = enclosedBy(text, "const TOLERATED = new Map([");
      return [...slice.matchAll(/\[\s*"([^"]+)"/g)]
        .map(([, key]) => ({ owner: null, what: key, unsigned: false }));
    },
    // A floor key is the subject and no task owns it, so "owned by a shipped task" and "names an
    // owner" would both fire on every row. Declared rather than worked around, the way the
    // assertion-debt row declares `ownerIsSubject`.
    ownerIsSubject: true,
  },
  {
    // The seventh, joined on the day it was written (SH743) rather than several blocks later,
    // which is the whole point of SH741 having asserted the count.
    name: "response budgets AT_THE_WALL",
    path: "shio-app/src/test/java/com/viglet/shio/api/agent/benchmark/ShAgentResponseBudgetTest.java",
    holds: "response budgets accepted with no headroom, and why each cannot be trimmed",
    since: "SH743",
    async entries(text) {
      // SH785 — matched rather than searched for, like the two rows above and for their reason.
      const slice = enclosedBy(text, "AT_THE_WALL = Map.of(");
      // The keys are the budget names, each on its own line before its reason.
      return [...slice.matchAll(/^\s{12}"([\w.]+)",$/gm)]
        .map(([, key]) => ({ owner: null, what: key, unsigned: false }));
    },
    // A budget name is the subject and no task owns it — the same declaration the TOLERATED and
    // assertion-debt rows make, and for the same reason.
    ownerIsSubject: true,
  },
  {
    // The eighth, joined on the day it was written (SH795) rather than several blocks later —
    // which is what SH741 asserted the count for, and the second time in a row it has held.
    name: "per-tool AT_THE_WALL",
    path: "shio-app/src/test/java/com/viglet/shio/api/agent/mcp/ShMcpToolBudgetTest.java",
    holds: "MCP tools with no room for another argument, and why each cannot be trimmed",
    since: "SH795",
    async entries(text) {
      const slice = enclosedBy(text, "AT_THE_WALL = Map.of(");
      // The keys are tool names, each on its own line before its reason — the same shape the
      // response-budget row reads, because SH795 built this one in that one's image on purpose.
      return [...slice.matchAll(/^\s{12}"([\w.]+)",$/gm)]
        .map(([, key]) => ({ owner: null, what: key, unsigned: false }));
    },
    // A tool name is the subject and no task owns it — the declaration the TOLERATED, assertion-debt
    // and response-budget rows all make, for the reason they make it.
    ownerIsSubject: true,
  },
  {
    name: "verify-baseline.json",
    path: "shio/verify-baseline.json",
    holds: "accepted verify findings, per project",
    since: "SH142",
    optional: true,
    async entries(text) {
      const parsed = JSON.parse(text);
      return (parsed.accepted ?? parsed.findings ?? []).map((row) => ({
        // An accepted finding names a rule and an address, not a task. Its owner is whoever wrote
        // the `reason`, and an entry with no reason is exactly the shape this reader exists to
        // surface: a suppression nobody signed.
        owner: ownerIn(row.reason),
        what: `${row.rule ?? "?"} @ ${row.address ?? "?"}`,
        unsigned: !row.reason,
      }));
    },
  },
];

/** The first `SH<n>` in a free-text reason — how a baseline entry names the task that owns it. */
function ownerIn(reason) {
  const found = /\b(SH\d+)\b/.exec(String(reason ?? ""));
  return found ? found[1] : null;
}

/** Every id the ledger records as shipped. Retirements are not shipments and do not appear. */
async function shippedIds() {
  const ledger = await readFile(join(ROOT, "docs/CHANGELOG.md"), "utf8");
  return new Set([...ledger.matchAll(/^- \*\*(SH\d+)/gm)].map(([, id]) => id));
}

/** Every id still open on the roadmap. */
async function openIds() {
  const roadmap = await readFile(join(ROOT, "docs/ROADMAP.md"), "utf8");
  return new Set([...roadmap.matchAll(/\*\*(SH\d+)\*\*/g)].map(([, id]) => id));
}

async function readLedgers() {
  const found = [];
  for (const ledger of LEDGERS) {
    let text;
    try {
      text = await readFile(join(ROOT, ledger.path), "utf8");
    } catch (error) {
      if (ledger.optional && error?.code === "ENOENT") {
        // `shio/verify-baseline.json` is a *project* file: this repository is not a Shio project
        // and carries none. Absent is a real state and not a failure — but the row still prints, so
        // "there are four of these" stays true for a reader of the output.
        found.push({ ...ledger, entries: [], absent: true });
        continue;
      }
      throw error;
    }
    found.push({ ...ledger, entries: await ledger.entries(text), absent: false });
  }
  return found;
}

test("SH594: one reader answers what this repository is knowingly carrying", async () => {
  const ledgers = await readLedgers();
  const total = ledgers.reduce((sum, ledger) => sum + ledger.entries.length, 0);

  // Printed on every run, like the two counts `assertion-debt` prints and for the same reason: the
  // number is the deliverable, and a number nobody sees is one nobody acts on. Not asserted against
  // a ceiling — that is the mistake all four of these files were built to avoid.
  console.log(`\n[SH594] knowingly carried — ${total} entr(y|ies) across ${ledgers.length} ledgers`);
  for (const ledger of ledgers) {
    console.log(
      `  ${ledger.name.padEnd(24)} ${String(ledger.entries.length).padStart(3)}`
        + `  ${ledger.absent ? "(absent — not a Shio project)" : ledger.holds} [${ledger.since}]`,
    );
  }

  // The number is asserted, not merely printed, so inventing a *sixth* ledger without joining it
  // here fails the run. SH499 is the case: it reached for this shape unprompted, and the failing
  // count is what turned "a new debt file exists somewhere" into a one-line edit.
  assert.equal(ledgers.length, 8, "a new ledger is a row in LEDGERS, not a ninth reader");
  // Not vacuous: if every reader came back empty the join would pass forever, which is the
  // lint-that-reports-zero failure every instrument in this repository is warned about.
  assert.ok(
    ledgers.some((ledger) => ledger.entries.length > 0),
    "no ledger yielded an entry — a reader that parses nothing agrees with everything",
  );
});

/**
 * SH785 — the assertion that would have caught the count this file printed for three blocks.
 *
 * <p>A row that slices a declaration out of a source file and overruns it does not fail: it reports
 * a larger number, and a larger number on a debt ledger reads as debt. What it cannot avoid is
 * reading the *same* entry twice, because whatever it swallowed past the end is another list of the
 * same kind of thing. Twenty-four of the DEBT row's 144 were an id it had already counted.</p>
 *
 * <p>Asserted per ledger rather than over the total, so the failure names which reader overran.</p>
 */
/**
 * SH786 — the assertion for the half SH785 could not see.
 *
 * <p>SH785 caught the scan that ran <em>past</em> the end, and it caught it by noticing duplicates:
 * an overrun swallows a neighbouring list of the same kind of thing. A scan that stops <em>short</em>
 * swallows nothing. It returns a strict prefix — no duplicate, no dangling owner, every other check
 * green, and a smaller number, which on a debt ledger is the direction nobody questions.</p>
 *
 * <p>So this asserts the property directly, on a declaration built to break it, and then asserts the
 * mask is load-bearing on the real files rather than decorative.</p>
 */
test("SH786: a bracket in a comment or a string does not end a declaration early", () => {
  // The ids are in the SH9xxx range nothing ships into, which assertion-debt's own fixtures use for
  // the same reason: a real id spelled inside an assertion message IS a payment to that reader, and
  // a fixture written with `"SH3"` credits SH3 and drops it off the debt list. Which it did.
  const declaration = [
    "const DEBT = [",
    '  "SH9201",',
    "  // the ] case: a reason may close a bracket it never opened",
    '  "SH9202",  // and ] again, after code on the same line',
    '  "SH9203", // a key like "with a ] inside" is a string, not structure',
    "].sort(byId);",
    "",
    "const NEXT = [",
    '  "SH9299",',
    "];",
  ].join("\n");

  const ids = [...enclosedBy(declaration, "const DEBT = [").matchAll(/"(SH\d+)"/g)]
    .map(([, id]) => id);
  assert.deepEqual(
    ids,
    ["SH9201", "SH9202", "SH9203"],
    "three unbalanced `]` characters sit in this declaration's prose, and none of them is its end.\n"
      + "Stopping short is the bracket count reading somebody's explanation as structure, and the "
      + "ledger then silently under-reports. Running past — an `SH9299` here — is the SH785 overrun.",
  );
});

test("SH786: the mask is load-bearing on the real declarations, not a precaution", async () => {
  // Not vacuous, the way every instrument in this repository is required to be: if the files this
  // reads had no bracket inside any string or comment, the test above would be asserting a property
  // of a fixture and nothing else. The measurement that justified the fix is re-taken here, so the
  // day it stops being true this says so instead of quietly guarding nothing.
  const budgets = await readFile(
    join(ROOT, "shio-app/src/test/java/com/viglet/shio/api/agent/benchmark/ShAgentResponseBudgetTest.java"),
    "utf8",
  );
  const from = budgets.indexOf("AT_THE_WALL = Map.of(");
  const span = budgets.slice(from, budgets.indexOf("\n    );", from));
  const hidden = [...span].filter((c) => c === "(" || c === ")").length
    - [...masked(span)].filter((c) => c === "(" || c === ")").length;
  assert.ok(
    hidden > 0,
    `AT_THE_WALL's prose holds no bracket the mask has to hide (${hidden}), so this row's scan would `
      + "read the same either way and SH786 is guarding nothing here. Either the reasons changed or "
      + "the mask stopped working — and the second is the one that matters.",
  );
});

test("SH785: no ledger reports the same entry twice, which is what an overrunning scan does", async () => {
  for (const ledger of await readLedgers()) {
    const seen = new Set();
    const twice = ledger.entries.map((entry) => entry.what)
      .filter((what) => seen.size === seen.add(what).size);
    assert.deepEqual(
      twice,
      [],
      `${ledger.name} yielded ${ledger.entries.length} entries and ${twice.length} of them repeat `
        + "one it had already read.\nA ledger holds each thing once, so a duplicate is this reader's "
        + "own fault: its slice of the declaration ran past the end and swallowed the next one. "
        + "Read it with `enclosedBy`, which matches the bracket instead of searching for a string "
        + "that closes it — a search for `];` finds the first one ANYWHERE below, and every source "
        + "file has one.",
    );
  }
});

/**
 * SH781 — a half is not a whole, and the ledger could not tell them apart.
 *
 * <p>`roadkeep ship --part` records the half that landed and <b>leaves the roadmap line open</b>,
 * which is how progress reaches the ledger without the work leaving the backlog. This rule read the
 * changelog alone, so the first half of a task orphaned every entry that named it — and the entry
 * still had a reason, because the work was still open.</p>
 *
 * <p>The two rules were unsatisfiable together, which is the collision SH764 named from the other
 * side: <i>the act of recording a finding disabled the gate that would confirm it</i>. Here the act
 * of recording progress disabled the exception that was tracking the rest of it. Hit for real
 * shipping SH778's evidence half with a `red-suites.json` entry open against it, and worked around
 * by filing the remainder as its own id — acceptable once, and wrong as a convention, because it
 * grows the backlog for a bookkeeping reason rather than a product one.</p>
 *
 * <p>The fix is the rule's own stated intent, which the implementation had drifted from: an owner
 * is spent when the changelog holds it <b>and the roadmap does not</b>. Both files were already
 * read here — `openIds` is three tests down — so this is a comparison that was available and not
 * being made.</p>
 */
test("SH594: no entry is owned by a task that has already shipped", async () => {
  const [ledgers, shipped, open] = await Promise.all([readLedgers(), shippedIds(), openIds()]);

  const orphans = [];
  for (const ledger of ledgers) {
    if (ledger.ownerIsSubject) continue;
    for (const entry of ledger.entries) {
      // SH781 — `&& !open.has(...)`: a partially shipped task is in both files, and it is the one
      // state where an entry naming it is still exactly right.
      if (entry.owner && shipped.has(entry.owner) && !open.has(entry.owner)) {
        orphans.push(`${ledger.name}: ${entry.what} — owned by ${entry.owner}, which has SHIPPED`);
      }
    }
  }

  assert.deepEqual(orphans, [], `
    an entry whose owner has shipped is the worst state a debt ledger has: it reads as tracked
    work when the person who would have done it has moved on. This is not hypothetical —
    open-findings.json held a red case owned by SH555 AFTER SH555 shipped, because the fix landed
    on the render side and the CLI's generated layout still emitted {{CSS}}, and only a check
    written that round noticed.

    Two honest resolutions, and picking is the point: the finding is still real, so re-file it
    under a NEW open task; or it is gone, so delete the entry. Re-opening a shipped id is neither.

    A task shipped with --part is NOT this state and is not reported here (SH781): its line is
    still on the roadmap, so the entry naming it still has a reason.

${orphans.map((row) => `      ${row}`).join("\n")}`);
});

test("SH781: a partially shipped owner is not an orphan, and a fully shipped one still is", () => {
  // Driven against a fabricated world for the reason the comparator's twin in red-suites.test.mjs
  // is: the real ledgers are whatever this afternoon left, so an assertion about them would be a
  // test of today. The three states are what matter, and only two of them existed before.
  const shipped = new Set(["SH9211", "SH9212"]);
  const open = new Set(["SH9212", "SH9213"]);
  const orphan = (owner) => shipped.has(owner) && !open.has(owner);

  assert.equal(orphan("SH9211"), true,
    "shipped and gone from the roadmap: the entry names somebody who has moved on, which is the "
      + "state SH594 was built for");
  assert.equal(orphan("SH9212"), false,
    "SH781: shipped as a PART and still on the roadmap. This is the state that made the rules "
      + "unsatisfiable together — `ship --part` writes a ledger entry to record progress, and "
      + "reading the changelog alone turned that into an orphaned exception for work that is "
      + "still open. Hit for real on the evidence half of see SH778, with a red-suites entry "
      + "against it — spelled as a reference because a bare id here would read to SH527's reader "
      + "as a claim to have closed that task, which is SH731's trap and would pay off a debt this "
      + "very file declares.");
  assert.equal(orphan("SH9213"), false,
    "and an owner that has not shipped at all was never this rule's subject — the dangling case "
      + "below is what covers an owner in neither file");

  // And the rule as it stood, on the same world: reading the changelog alone cannot tell the half
  // from the whole, so SH9212 orphaned an entry whose work was still open. Kept as the contrast
  // rather than described, because "it used to be wrong" is the claim this test exists to hold.
  const orphanBefore = (owner) => shipped.has(owner);
  assert.equal(orphanBefore("SH9212"), true,
    "SH781: this is what the two rules being unsatisfiable together looked like — recording "
      + "progress with --part disabled the exception that was tracking the rest of it, which is "
      + "SH764's collision from the other side");
});

test("SH594: every entry names an owner, or says it is unsigned", async () => {
  // The other way a ledger rots. An entry with no owner cannot be checked in either direction —
  // nothing says it has gone green and nothing says who would notice — so it outlives every reason
  // it was added for. `red-suites.json` and `open-findings.json` both require one by construction;
  // this asserts they still do, and surfaces a baseline suppression nobody signed.
  const ledgers = await readLedgers();

  const unowned = [];
  for (const ledger of ledgers) {
    if (ledger.ownerIsSubject) continue;
    for (const entry of ledger.entries) {
      if (!entry.owner) unowned.push(`${ledger.name}: ${entry.what}`);
    }
  }

  assert.deepEqual(unowned, [], `
    a debt entry with no owner is a suppression nobody signed: nothing can say it has gone green,
    and nothing says who would notice if it had not.

${unowned.map((row) => `      ${row}`).join("\n")}`);
});

test("SH594: an owner that is neither shipped nor open is a dangling reference", async () => {
  // The third state, and the one a per-ledger check cannot see: an id that is in no roadmap and no
  // changelog was retired, renumbered, or never existed. The entry then names nothing at all.
  const [ledgers, shipped, open] = await Promise.all([readLedgers(), shippedIds(), openIds()]);

  const dangling = [];
  for (const ledger of ledgers) {
    if (ledger.ownerIsSubject) continue;
    for (const entry of ledger.entries) {
      if (entry.owner && !shipped.has(entry.owner) && !open.has(entry.owner)) {
        dangling.push(`${ledger.name}: ${entry.what} — owned by ${entry.owner}, which is neither `
          + "open nor shipped (retired? renumbered?)");
      }
    }
  }

  assert.deepEqual(dangling, [], `
    an owner in neither file names nothing: the task was retired or renumbered and the debt stayed.

${dangling.map((row) => `      ${row}`).join("\n")}`);
});

test("SH741: no tolerated floor exempts a floor that is no longer there", async () => {
  // The check the other five get by way of their owner and this one cannot: a `TOLERATED` entry
  // names a floor, not a task, so "owned by a shipped id" has nothing to bite on. The equivalent
  // question is whether the floor it excuses still exists.
  //
  // Not hypothetical. SH740 changed `floor-kind.test.mjs` to strip comments where it had read raw,
  // which could have removed a floor written inside one and left its exemption standing forever.
  // All four survived — and answering that took a throwaway script, because nothing in the suite
  // could say so. An exemption for a floor that is gone is the quietest kind of stale debt: it
  // never fires, so it never asks to be re-read.
  const [ledgers, floorKind] = await Promise.all([
    readLedgers(),
    readFile(join(ROOT, "cli/test/floor-kind.test.mjs"), "utf8"),
  ]);
  const tolerated = ledgers.find((ledger) => ledger.name === "floor-kind TOLERATED");
  assert.ok(tolerated, "the TOLERATED row left LEDGERS, which is the defect SH741 closed");
  assert.ok(tolerated.entries.length > 0, "the reader parsed no entry, so it agrees with anything");

  // `suites()` is the same walk the lint uses, not a second implementation of it — SH583's rule,
  // and why SH741 moved the mechanism into support/floors.mjs rather than re-grepping here. The
  // ledger stayed in floor-kind.test.mjs; only the scan is shared.
  const { floorsIn, suites } = await import("./support/floors.mjs");
  const live = new Set();
  for (const [file, body] of suites()) {
    for (const floor of floorsIn(body)) live.add(`${file}:${floor.subject}>${floor.bound}`);
  }

  const stale = tolerated.entries.map((entry) => entry.what).filter((key) => !live.has(key));
  assert.deepEqual(stale, [], `
    a tolerated key names a floor that no longer exists — it was deleted, renamed, or its bound
    moved — so the exemption now excuses nothing and hides the next one. Delete the line.
    (floor-kind.test.mjs currently declares ${floorKind.split("\n").length} lines.)

${stale.map((row) => `      ${row}`).join("\n")}`);
});
