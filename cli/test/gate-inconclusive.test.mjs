
/**
 * SH828 — a build that never began is not evidence about the code.
 *
 * ## Measured twice, in one session
 *
 * `run-gate.mjs` stamped whatever the command exited with. A Maven `clean` that cannot delete a
 * locked file exits non-zero having run **zero tests**, and was written down exactly as a red
 * suite. Both times the IDE's Java language server held something under `shio-app/target`, both
 * cleared on a retry, and neither touched anything that had changed. What each left behind was a
 * `red: true` stamp — so `red-suites.test.mjs` demanded a ledger entry against an open task for a
 * defect nobody can find, `ShDeclaredRedTest` went red about that stamp, and the pair reached
 * SH818's deadlock. The machinery diagnosed the cycle correctly; it should not have started.
 *
 * ## The rule already existed, for a different cause
 *
 * SH803, about two overlapping runs: *a false red is worse than an absent stamp — absence says
 * nothing, and this manufactures a declaration for a defect nobody can find.* The same sentence
 * covers a file lock, a compile error and an unresolvable dependency.
 *
 * ## Why the log and not the exit code
 *
 * The distinction cannot come from the code — Maven exits 1 for a failed test and 1 for a failed
 * `clean`. It comes from evidence the gate already keeps: a run that reached surefire prints
 * `Tests run:`, node's runner prints its counts. A Spring context that will not load still prints
 * a count, with errors, so it stays red — which is right, because that IS about the code.
 */
import assert from "node:assert/strict";
import { mkdtempSync, readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";

import { inconclusive, stamps, suiteRan, GATES_DIR } from "../../scripts/gate-stamp.mjs";

test("SH828: a log with no test count is not a colour, and one with a count is", () => {
  // The real shapes, from the two failures that produced this task and from an ordinary red run.
  const lockedClean = "[INFO] --- clean:3.2.0:clean (default-clean) @ shio-app ---\n"
    + "[ERROR] Failed to execute goal org.apache.maven.plugins:maven-clean-plugin:3.2.0:clean "
    + "(default-clean) on project shio-app: Failed to clean project: Failed to delete "
    + "D:\\Git\\viglet\\shio\\2026.3\\shio-app\\target\\classes\\ui\\public\\media\\assets\n";
  assert.equal(suiteRan(lockedClean), false,
    "SH828: this exact log stamped the Java gate red twice in one session. Zero tests ran, so it "
    + "says nothing about the code — and a red stamp here asks somebody to declare a ledger entry "
    + "against an open task for a defect that does not exist");

  assert.equal(suiteRan("[ERROR] Tests run: 1910, Failures: 1, Errors: 0, Skipped: 0"), true,
    "a Maven run that reached surefire IS a colour, whatever it says");
  assert.equal(suiteRan("ℹ tests 1536\nℹ fail 2\n"), true,
    "and so is node's runner, which is what three of the four gates use");

  // The case that must NOT become inconclusive: a context that will not load is about the code,
  // and surefire still reports a count for it.
  assert.equal(suiteRan("[ERROR] Tests run: 12, Failures: 0, Errors: 12, Skipped: 0"), true,
    "SH828: twelve errors and no failures is a broken Spring context — a real red, and the one "
    + "this check most needs to keep, because it is the failure that runs no test METHOD while "
    + "still being entirely about the code");

  assert.equal(suiteRan(""), false, "an empty log is no evidence");
  assert.equal(suiteRan(null), false, "and neither is an unreadable one");
});

test("SH828: an inconclusive stamp carries no colour and says why", () => {
  const dir = mkdtempSync(join(tmpdir(), "shio-gate-inconclusive-"));

  const record = inconclusive(dir, "java", 1, "the command failed before any suite ran");
  assert.equal(record?.inconclusive, true, "it returns what it wrote");
  assert.equal(record.red, undefined,
    "SH828: and no `red`, which is the whole mechanism — it falls out of `stamps` exactly where "
    + "an absent stamp does, so both comparators say `no run recorded in this tree` with no new "
    + "branch, the way SH818's `cleared` already works");
  assert.equal(stamps(dir).java, undefined, "so it is not a colour any reader can find");

  const written = JSON.parse(readFileSync(join(dir, GATES_DIR, "java.json"), "utf8"));
  assert.equal(written.code, 1,
    "the exit code survives: `nobody ran this` and `this could not run` are different states and "
    + "the difference is the only reason to write a file at all");
  assert.match(written.why, /failed before any suite ran/,
    "and the reason is in it, because a reader meeting this stamp has no other way to tell it "
    + "from a gate nobody has run here");

  assert.equal(inconclusive(dir, "not-a-gate", 1, "x"), null,
    "a name no gate has is refused rather than writing a file no reader will look at");
});
