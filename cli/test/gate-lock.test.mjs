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

import { acquire, alive, release, GATES_DIR } from "../../scripts/gate-stamp.mjs";

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
