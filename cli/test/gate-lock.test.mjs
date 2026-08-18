/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH803 — one run of a gate at a time, and a dead holder never outlives its process.
 *
 * ## The failure
 *
 * Two runs of the same gate corrupt each other. Measured by accident: a backgrounded `pnpm java`
 * and a foreground one overlapped, and the foreground reported **3 errors over 1092 tests** where
 * the tree alone reports **1894 green**, while `grep` answered `Binary file shio-test.log matches`
 * because two writers had interleaved into one fixed-name log. Neither number was about the code.
 *
 * Before SH800 that was confusing. After it, it is **recorded**: the wrapper stamped `red: true`
 * for a failure that never happened, and the comparators read that stamp and demand a
 * `red-suites.json` entry against an open task. A false red is worse than an absent stamp —
 * absence says nothing, and this manufactures a declaration for a defect nobody can find, which is
 * the exact shape SH755 says teaches people that red means nothing.
 *
 * ## Driven, not raced
 *
 * The lock takes its pid and its clock as arguments, so both sides of it are asserted here in
 * milliseconds against fabricated holders — a test that actually launched two gates would take
 * thirty seconds to prove the same thing, and would be a flake of its own.
 */
import assert from "node:assert/strict";
import { mkdtempSync, readFileSync, writeFileSync, mkdirSync, existsSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";

import { readFile } from "node:fs/promises";

import {
  acquire, alive, clear, gateLines, release, stamp, stamps, GATES_DIR,
} from "../../scripts/gate-stamp.mjs";

// @scans code — the lock's own reasons quote the collision it prevents, and a raw read of this
// header would take the description of the defect for the defect (SH740).

/** A throwaway root, because a lock is a file and the real `.gates/` belongs to real runs. */
function root() {
  return mkdtempSync(join(tmpdir(), "shio-gate-lock-"));
}

/** A pid nothing is running: the highest a process id can take, plus one. */
const DEAD_PID = 2 ** 22 + 1;

test("SH803: a second run of the same gate is refused, and told who holds it", () => {
  const dir = root();
  // The holder must be a LIVE pid, or the lock is correctly treated as stale and taken over — the
  // first draft of this test used a made-up number and asserted the opposite of what it set up.
  const first = acquire(dir, "java", process.pid, "2026-08-17T21:00:00.000Z");
  assert.equal(first.taken, true, "the first run takes the lock");

  const second = acquire(dir, "java", 8765, "2026-08-17T21:00:05.000Z");
  assert.equal(second.taken, false,
    "SH803: the second run of one gate must be refused — two share a log and, for java, a target/, "
      + "and the loser's damage is stamped as that gate's colour");
  assert.equal(second.holder.pid, process.pid,
    "and it names the holder, because whoever meets this usually forgot they had backgrounded it");
  assert.equal(second.holder.since, "2026-08-17T21:00:00.000Z", "and since when");
});

test("SH803: a different gate is not refused, because it collides with nothing", () => {
  const dir = root();
  acquire(dir, "java", process.pid, "2026-08-17T21:00:00.000Z");
  const other = acquire(dir, "js", 8765, "2026-08-17T21:00:01.000Z");
  assert.equal(other.taken, true,
    "SH803: the lock is per gate. `java` and `js` share neither a log nor a build directory, and "
      + "running them together is what people do to save time — a global lock would refuse that");
});

test("SH803: a holder that has died is cleared rather than obeyed", () => {
  const dir = root();
  mkdirSync(join(dir, GATES_DIR), { recursive: true });
  writeFileSync(join(dir, GATES_DIR, "java.lock"),
    JSON.stringify({ gate: "java", pid: DEAD_PID, since: "2026-08-10T00:00:00.000Z" }));

  const taken = acquire(dir, "java", process.pid, "2026-08-17T21:00:00.000Z");
  assert.equal(taken.taken, true,
    "SH803: a crashed run must not stop the next one — a gate refusing to work because of a "
      + "process that died three days ago is worse than the collision the lock guards");
  assert.equal(taken.stale.pid, DEAD_PID, "and it says whose lock it cleared, rather than doing it quietly");
});

test("SH803: releasing removes the file, and releasing twice is not an error", () => {
  const dir = root();
  acquire(dir, "java", process.pid, "2026-08-17T21:00:00.000Z");
  assert.ok(existsSync(join(dir, GATES_DIR, "java.lock")), "the lock is a file while it is held");
  release(dir, "java");
  assert.ok(!existsSync(join(dir, GATES_DIR, "java.lock")), "and it is gone afterwards");
  assert.doesNotThrow(() => release(dir, "java"),
    "a run that never took one still finishes — bookkeeping must never become the failure");
});

test("SH803: the runner releases outside the try, because process.exit skips a finally", () => {
  // The bug this file's own subject nearly shipped with. `process.exit()` inside the `try` ends the
  // process synchronously and the `finally` never runs, so the lock outlives every successful run
  // and the guard refuses the NEXT one. A lock that leaks is worse than no lock.
  const runner = readFileSync(new URL("../../scripts/run-gate.mjs", import.meta.url), "utf8");
  const finallyAt = runner.indexOf("} finally {");
  const exitAt = runner.lastIndexOf("process.exit(code)");
  assert.ok(finallyAt > 0 && exitAt > finallyAt,
    "SH803: `process.exit(code)` must come AFTER the `finally` that releases the lock, never "
      + "inside the `try` — exiting does not run a finally, so the lock would never be given back");
});

test("SH803: the liveness probe tells a running process from one that is gone", () => {
  assert.equal(alive(process.pid), true, "this process is running, or nothing here is");
  assert.equal(alive(DEAD_PID), false, "and a pid nothing occupies is not");
  assert.equal(alive(undefined), false,
    "a malformed lock naming no pid is a dead one — it must not hold the gate for ever");
});

/**
 * SH818 — clearing a stamp is an act with a record, not the absence of one.
 *
 * <p>The escape from a comparator cycle was `rm .gates/java.json .gates/js.json`, and it worked:
 * the readers treat an absent stamp as *no run recorded in this tree*, which is honest. What it
 * left behind was nothing, so a stamp discarded because the red was about the recording looked
 * exactly like one discarded because somebody did not want to fix it. This is the same escape with
 * the act written down — which is the only difference, and the whole of it.</p>
 */
test("SH818: a cleared stamp carries no colour, and says it was cleared", () => {
  const dir = mkdtempSync(join(tmpdir(), "shio-gate-clear-"));

  stamp(dir, "java", 1);
  assert.equal(stamps(dir).java?.red, true, "the fixture is a red stamp, or this proves nothing");

  const record = clear(dir, "java");
  assert.equal(record?.cleared, true, "clearing returns what it wrote");
  assert.ok(existsSync(join(dir, GATES_DIR, "java.json")),
    "SH818: the file SURVIVES. A deletion is invisible to `git status`, to a reader, and to the "
    + "next person wondering why a gate has no colour — which is the difference between an "
    + "exit and a hole");

  assert.equal(stamps(dir).java, undefined,
    "SH818: and it is not a colour. A cleared stamp has no `red`, so it falls out of `stamps` "
    + "exactly where an absent one does — both comparators then say `no run recorded in this "
    + "tree` with no new branch, which is why this is a field and not a rule");

  // The half that keeps the verb from becoming a way to make any red green: it clears what a gate
  // ANSWERED, and cannot answer for it. Running the gate is still the only thing that writes one.
  assert.equal(clear(dir, "not-a-gate"), null,
    "a name no gate has is refused rather than writing a file no reader will ever look at");
  stamp(dir, "java", 0);
  assert.equal(stamps(dir).java?.red, false,
    "and a real run overwrites the clearing, which is the direction that must always work");
});

/**
 * SH819 — one formatter, and the four sentences it has to be able to say.
 *
 * <p>The table SH755 built was reachable only by running the Java or JS suite, so reading four
 * lines derived from four small files cost minutes — and SH818's exit instructions depend on that
 * read, in the one state where running a gate cannot help. `pnpm gates` is the read; `gateLines` is
 * what stops it being a third copy of the formatting. The duplication had already drifted once:
 * `runWithLog` announced every gate as "JS suites" from the second wrapper onward (SH802).</p>
 *
 * <p>Driven against a fabricated world, because the real stamps are whatever the last run left and
 * an assertion about those is a test of this afternoon.</p>
 */
test("SH819: the gate table says which of four things it knows about each gate", () => {
  const world = {
    java: { red: false, sha: "abcdef1234", when: "2026-08-18T00:00:00Z", stale: false },
    conformance: { red: true, sha: "abcdef1234", when: "2026-08-18T00:00:00Z", stale: false },
    docs: { red: true, sha: "0000000000", when: "2026-08-01T00:00:00Z", stale: true },
  };
  const lines = gateLines(world, new Set(["conformance"]), "js", new Set()).join("\n");

  assert.match(lines, /java — green at abcdef12/, "a fresh stamp is a colour and names the commit");
  assert.match(lines, /conformance — RED at abcdef12.*declared/,
    "a red the ledger excuses says so on the same line — the reader needs both at once, or a "
    + "declared red reads as an undeclared one");
  assert.match(lines, /docs — last run.*NOT this tree, so not a colour/,
    "SH770: a stamp from another commit names the run somebody has to do, which neither a colour "
    + "nor a silence does");
  assert.match(lines, /js — this suite, running now/,
    "SH800: the caller's own gate is never rendered as a colour, because the stamp on disk is the "
    + "previous run's — that is the whole reason each comparator skips one");

  // SH818's record, which is the reason clearing writes a file instead of unlinking one. Without
  // this branch the report says `no run recorded`, and the visibility the verb exists for is lost
  // at the one place anybody would look for it.
  const cleared = gateLines({}, new Set(), null, new Set(["java"])).join("\n");
  assert.match(cleared, /java — cleared, so not a colour; run it/,
    "SH819: a cleared stamp is distinguishable from an absent one HERE, or SH818 wrote a field "
    + "nothing ever reads");
  assert.match(cleared, /js — no run recorded in this tree/,
    "and a gate nobody has run still says exactly that — a fresh clone is honest, not red");

  // With no `self`, every gate has a last outcome. That is the one thing the command can say and
  // neither comparator can, and it is why the parameter is optional rather than required.
  assert.doesNotMatch(gateLines(world, new Set(), null, new Set()).join("\n"), /running now/,
    "a reader that runs nothing has no gate in progress to except");
});

test("SH819: `pnpm gates` runs nothing, and the comparator formats nothing of its own", async () => {
  const report = await readFile(new URL("../../scripts/gates-report.mjs", import.meta.url), "utf8");
  const comparator = await readFile(new URL("./red-suites.test.mjs", import.meta.url), "utf8");

  assert.match(report, /gateLines\(/, "the command prints the shared lines rather than its own");
  assert.match(comparator, /gateLines\(/,
    "SH819: and so does the comparator — a fourth copy of this formatting is how the third one "
    + "came to call every gate `JS suites` for two wrappers");

  for (const forbidden of ["spawn", "execFile", "runWithLog", "stamp("]) {
    assert.ok(!report.includes(`${forbidden}`),
      `SH819: the report must not ${forbidden} — a reporter that ran or stamped a gate would be a `
      + "fifth gate whose verdict came from another gate's leftovers, which is SH770's mistake "
      + "promoted to a command");
  }
  assert.ok(!/process\.exit\([^0]/.test(report),
    "and it exits 0 whatever the colours are: it reports, the gates judge");
});
