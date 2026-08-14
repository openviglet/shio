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

// @scans prose reason: the governed ledgers are Markdown; the declared-debt entries this reads
// back are prose by construction (SH740).

const ROOT = fileURLToPath(new URL("../../", import.meta.url));

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
      // Its own declaration, read the way that file reads itself: the array between the markers.
      const from = text.indexOf("const DEBT = [");
      const to = text.indexOf("];", from);
      assert.ok(from >= 0 && to > from, "assertion-debt no longer declares DEBT as an array");
      // Every entry IS its own owner here — the debt is "this shipped id has no assertion" — so the
      // shipped check below would fire on all of them. That is the one ledger where a shipped owner
      // is the normal state rather than the defect, which is why it declares `ownerIsSubject`.
      return [...text.slice(from, to).matchAll(/"(SH\d+)"/g)]
        .map(([, id]) => ({ owner: id, what: `${id} carries no assertion` }));
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
  assert.equal(ledgers.length, 5, "a new ledger is a row in LEDGERS, not a sixth reader");
  // Not vacuous: if every reader came back empty the join would pass forever, which is the
  // lint-that-reports-zero failure every instrument in this repository is warned about.
  assert.ok(
    ledgers.some((ledger) => ledger.entries.length > 0),
    "no ledger yielded an entry — a reader that parses nothing agrees with everything",
  );
});

test("SH594: no entry is owned by a task that has already shipped", async () => {
  const [ledgers, shipped] = await Promise.all([readLedgers(), shippedIds()]);

  const orphans = [];
  for (const ledger of ledgers) {
    if (ledger.ownerIsSubject) continue;
    for (const entry of ledger.entries) {
      if (entry.owner && shipped.has(entry.owner)) {
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

${orphans.map((row) => `      ${row}`).join("\n")}`);
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
