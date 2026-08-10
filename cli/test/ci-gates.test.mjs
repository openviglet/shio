import assert from "node:assert/strict";
import { readFileSync, readdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join } from "node:path";
import { test } from "node:test";

/**
 * SH241 — the gates run on the branch the work happens on.
 *
 * Three workflows existed and none of them ran. `build.yml` and `codeql-analysis.yml` were
 * `workflow_dispatch`-only, and `console-check.yml` triggered on `push: branches: [main]` while the
 * default branch is **2026.3** — so on a push to the branch everything is developed on, nothing
 * happened. SH579 later added `suites.yml` for two of the three gates; the Java suite and the
 * console walk were still dispatch-only.
 *
 * **A gate that does not run is a script.** Not hypothetical: picking up Block O's follow-ups meant
 * building the console, and it was already red, unreported.
 *
 * <h2>Asserted here rather than trusted</h2>
 *
 * A trigger is one line, it is invisible in review, and nothing else in this repository reads it.
 * The failure it produces is silence, which is the same thing a green run looks like from outside.
 */

const ROOT = fileURLToPath(new URL("../../", import.meta.url));
const WORKFLOWS = join(ROOT, ".github", "workflows");

const read = (name) => readFileSync(join(WORKFLOWS, name), "utf8");

/** The `on:` block, which is everything before the first top-level key that follows it. */
function triggers(name) {
  const text = read(name);
  const start = text.search(/^on:\s*$/m);
  assert.ok(start >= 0, `${name} declares no on: block`);
  const rest = text.slice(start + 3);
  const end = rest.search(/^\S/m);
  return end < 0 ? rest : rest.slice(0, end);
}

test("SH241: the branch the work happens on is the one the gates run on", () => {
  // The original defect in one assertion. `main` is not this repository's default branch, and a
  // workflow naming only it is a workflow that has never run on a real push.
  const on = triggers("suites.yml");
  assert.match(on, /branches:.*"2026\.3"/,
    "suites.yml must trigger on the default branch — a push filter naming only `main` is how "
      + "three workflows ran zero times (SH241)");
});

test("SH241: all three gates agents.md names have a workflow that runs unasked", () => {
  // `./mvnw clean test`, `pnpm test` and `pnpm conformance` are the three commands docs say are
  // gates. A gate reachable only by `workflow_dispatch` is one somebody has to decide to run,
  // which is the same as not being visible.
  const suites = read("suites.yml");
  for (const [gate, command] of [
    ["the Java suite", /mvnw[^\n]*clean test/],
    ["the JS workspace", /pnpm test/],
    ["the conformance suite", /pnpm conformance|conformance/],
  ]) {
    assert.match(suites, command, `${gate} has no job in suites.yml`);
  }

  const on = triggers("suites.yml");
  assert.match(on, /push:/, "and they run on a push, not only when asked");
  assert.match(on, /pull_request:/, "and on a pull request, which is where a reviewer looks");
});

test("SH241: the console walk runs on a pull request", () => {
  // Deliberately not on every push: it boots a jar, provisions a site and drives Chromium, and a
  // gate that expensive on every push to a branch somebody is iterating on becomes the one people
  // cancel. A pull request is when the question is actually being asked.
  const on = triggers("console-check.yml");
  assert.match(on, /pull_request:/,
    "the console is the surface no API test can see — a form rendering zero fields answers 200 — "
      + "so a browser walk nobody triggers is the gap it was built to close (SH241)");
});

test("SH241: the Java job publishes surefire's own count, not a derived one", () => {
  // SH283 and SH354, in the one place a number is read by somebody who did not run the command.
  // `target/surefire-reports/` accumulates, so summing it is wrong after any targeted run, and
  // `-q` suppresses the only authoritative line. Both mistakes are invisible in the output.
  const suites = read("suites.yml");
  assert.match(suites, /GITHUB_STEP_SUMMARY/,
    "the run's headline should be the count, so nobody re-derives it");
  assert.match(suites, /Tests run:.*Failures.*Errors/,
    "and it should be surefire's own summary line");
  assert.match(suites, /tee /,
    "the log is kept rather than piped away: a filter that reads the total discards the "
      + "`<<< FAILURE!` lines with it, and then a red run says which count and not which test");
  assert.doesNotMatch(suites, /mvnw[^\n]*\s-q\b/,
    "`-q` hides the Results: line, which is the one number that is not a guess (SH283)");
});

test("SH241: no workflow claims a branch this repository does not develop on", () => {
  // The original defect was a *stale* branch name, not a missing one, and it read as configured.
  // Every workflow is checked because the next one to be added will be copied from these.
  const stale = [];
  for (const name of readdirSync(WORKFLOWS).filter((f) => f.endsWith(".yml"))) {
    const text = read(name);
    for (const match of text.matchAll(/branches:\s*\[([^\]]*)\]/g)) {
      const named = match[1].split(",").map((b) => b.trim().replace(/['"]/g, ""));
      if (named.length && !named.includes("2026.3")) stale.push(`${name} → ${named.join(", ")}`);
    }
  }
  assert.deepEqual(stale, [],
    "a push filter that does not name the default branch is a workflow that never runs, and it "
      + "looks exactly like one that does (SH241)");
});
