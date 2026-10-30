import assert from "node:assert/strict";
import { existsSync, readFileSync, readdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join } from "node:path";
import { test } from "node:test";

import { DEFAULT_DOCS_DIR } from "../../scripts/docs-location.mjs";

// @scans prose reason: workflow YAML and package scripts are read as text, and the gate names
// inside them are the subject rather than any code around them (SH740).

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

/**
 * Every `run:` script in a workflow, one entry per step (SH788).
 *
 * <p>A hand parse rather than a YAML dependency, because the question is small and the shape is
 * this repository's own: a `run: |` block scalar whose body is the lines indented past it, or a
 * one-line `run: <command>`. A step's script is the unit that matters — `set -o pipefail` applies
 * to the shell that runs one step and to nothing else, so a rule asked file-wide would pass on a
 * `pipefail` three jobs away.</p>
 */
function runBlocks(text) {
  const lines = text.split("\n");
  const blocks = [];
  for (let at = 0; at < lines.length; at += 1) {
    const inline = /^\s*(?:- )?run: +(?!\|)(.+)$/.exec(lines[at]);
    if (inline) {
      blocks.push(inline[1]);
      continue;
    }
    const opens = /^(\s*)(?:- )?run: +\|-?\s*$/.exec(lines[at]);
    if (!opens) continue;
    const indent = opens[1].length;
    const body = [];
    for (let next = at + 1; next < lines.length; next += 1) {
      const line = lines[next];
      if (line.trim() !== "" && line.length - line.trimStart().length <= indent) break;
      body.push(line);
    }
    blocks.push(body.join("\n"));
  }
  return blocks;
}

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

test("SH1102: the docs gate runs on every push, bare, now that the lint is clean", () => {
  // It ran only by hand, against a baseline, because six false `code.renamed` findings kept it red
  // (roadkeep RK1707, RK1708). Both fixed, a baseline would only forgive what a branch adds, and a
  // gate nobody triggers is how the docs drifted in the first place.
  const on = triggers("roadkeep.yml");
  assert.match(on, /push:/, "SH1102: roadkeep lint runs only when somebody asks for it");
  assert.match(on, /pull_request:/, "SH1102: roadkeep lint does not run on a pull request");
  assert.doesNotMatch(read("roadkeep.yml").replace(/^\s*#.*$/gm, ""), /^\s*baseline:/m,
    "SH1102: the docs gate forgives standing findings again; fix them rather than baselining them");
});

/**
 * SH655 — a suite whose input is another repository needs that repository checked out.
 *
 * <p>Three instruments read `SHIO_DOCS_DIR`, whose default is one contributor's drive letter, and
 * nothing in CI supplied it — so on a runner the directory was absent, and SH499 chose absent to be
 * a hard <b>failure</b> rather than a skip precisely so an unread checkout could not pass quietly.
 * The consequence is the one worth naming: the instrument SH628 landed could not fail anywhere but
 * that one machine, which makes it a check nobody was running rather than a check that passed.</p>
 *
 * <p>SH1262 removed the second repository: the pages and COVERAGE.md moved into
 * `shio-site/docs/src/content/docs`, and the readers default to it. So the facts held now are that
 * the default resolves inside this checkout and that CI no longer clones vigletdocs, whose frozen
 * copy would be read instead of the pages this commit ships.</p>
 */
test("SH655, SH1262: the docs suites read the pages in this checkout, not a second repository", () => {
  const suites = read("suites.yml");

  assert.ok(existsSync(join(DEFAULT_DOCS_DIR, "COVERAGE.md")),
    `SH1262: the docs readers default to ${DEFAULT_DOCS_DIR}, and the coverage matrix is not there`);
  assert.doesNotMatch(suites, /repository: vigletdocs\/vigletdocs\.github\.io|SHIO_DOCS_DIR:/,
    "SH1262: the pages live in this repository now; a vigletdocs checkout, or SHIO_DOCS_DIR pointed "
      + "at one, makes the gates read a frozen copy instead of the pages this commit ships");

  // The split is the decision SH655 asked for, so it is held rather than left to a reader of YAML:
  // two of the three need no instance, and putting them behind the boot would mean the cheapest
  // question about the published documentation waits on a JVM.
  assert.match(suites, /pnpm conformance:docs/,
    "SH655: `docs-vocabulary` and `docs-stack` read source and build files only, so they gate "
      + "every push beside `pnpm test`; only `docs-examples` drives the booted app");

  // A workflow naming a script that does not exist fails on the runner and nowhere else, which is
  // the same shape as the defect above one level down.
  const cli = JSON.parse(readFileSync(new URL("../package.json", import.meta.url), "utf8"));
  const script = cli.scripts["conformance:docs"] ?? "";
  assert.ok(script, "the job runs `pnpm conformance:docs`, so the script has to be there");
  assert.doesNotMatch(
    script,
    /docs-examples/,
    "SH655: and it must NOT include `docs-examples`, which needs the booted instance — a job "
      + "without one would fail on `requireInstance` and read as the documentation being wrong",
  );

  // SH653 — the third instance-free instrument. Written into the script explicitly, because the
  // job names files rather than a glob: a conformance file this line does not list runs on one
  // laptop and nowhere else, which is the state SH655 found `docs-stack` in.
  assert.match(
    script,
    /docs-coverage\.conformance\.mjs/,
    "SH653: the coverage summary's check reads the same other repository and needs no instance, "
      + "so it belongs in the every-push job beside the other two — a derived number nothing "
      + "recomputes in CI is the typed number again",
  );
});

/**
 * SH665 — the console's lint runs, and it runs against a baseline rather than against nothing.
 *
 * <p>`shio-react/eslint.config.js` extended `eslint-plugin-react-hooks@7.1.1`'s preset, whose
 * flat config still spells `plugins: ["react-hooks"]`. ESLint 10 rejects an array there **before
 * reading a rule**, so `eslint` exited 2 on every file — including ones nobody had touched — and
 * no workflow ran it, so there was no way to date when it broke. The rules it silenced are the
 * ones that catch a missing dependency array, which no source-reading test can see.</p>
 *
 * <p>Three facts, because they break apart: the workflow runs it, the suppressions baseline is
 * checked in, and the config registers the plugin itself instead of extending the preset that
 * cannot load. A job that runs a lint whose config throws is the same silence with a green tick,
 * and a gate with no baseline is one somebody deletes the first morning it is right.</p>
 */
test("SH665: the console lint gates, against a checked-in baseline", () => {
  const suites = read("suites.yml");
  assert.match(suites, /run: pnpm lint/,
    "SH665: nothing ran the console's lint, which is why a config that threw on every file "
      + "survived — the break is invisible until somebody types the command by hand");

  const baseline = join(ROOT, "shio-react", "eslint-suppressions.json");
  assert.ok(existsSync(baseline),
    "SH665: gating without the baseline makes the job red on 33 findings that predate it, and a "
      + "gate that is red on arrival is one people delete rather than pay down");

  const suppressed = Object.values(JSON.parse(readFileSync(baseline, "utf8")))
    .flatMap((rules) => Object.values(rules))
    .reduce((total, each) => total + each.count, 0);
  assert.ok(suppressed > 0,
    "an empty baseline is the same file as no baseline, and would pass this either way");

  // The config half. `extends: [reactHooks.configs[...]]` is exactly what threw, so the fix is
  // gone the moment somebody puts it back — and the symptom is a lint that reports nothing.
  const config = readFileSync(join(ROOT, "shio-react", "eslint.config.js"), "utf8");
  assert.match(config, /plugins:\s*\{\s*'react-hooks':\s*reactHooks\s*\}/,
    "SH665: the plugin has to be registered here, because 7.1.1 is the newest stable and every "
      + "release past it is a canary — there is no version to pin to");
});

test("SH667: the console walk refuses to let the editor drive skip (SH238, SH659)", () => {
  const workflow = read("console-check.yml");

  assert.match(workflow, /--strict/,
    "SH667: a skipped probe keeps `ok: true`, counts toward \"N/N probes ok\" and leaves "
      + "the exit code at 0. On a laptop that is right — the topology and the fixtures are "
      + "whatever they happen to be. In CI both are provisioned by this repository one step "
      + "earlier, so a skip there is this instrument not running, and it reads as coverage");

  // The other half, and they break apart: a flag the workflow passes to a script that ignores it
  // is the same absent gate with an extra step — SH655's own lesson, one instrument over.
  const script = readFileSync(
    new URL("../../shio-react/e2e/console-check.mjs", import.meta.url), "utf8");

  assert.match(script, /const STRICT = has\("strict"\)/,
    "SH667: and the script has to read the flag the workflow sends");
  assert.match(script, /STRICT && driveProbe\.skipped/,
    "SH667: and act on it for the drive. SH664 — a fixture that excluded itself from the drive's "
      + "own candidate loop — survived from the commit that introduced `provision.mjs`, reported "
      + "ok the whole time, and was found by reading the code rather than by a red run");
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

/**
 * The gate commands, as the test above names them — one list, so the two cannot drift (SH583).
 *
 * <p>Deliberately the *commands* and not every line in a workflow. A pipe whose exit code nobody
 * reads is not a defect: the Java job's summary step pipes `grep` into `tail` to publish a count,
 * with `|| echo` handling the miss, and demanding `pipefail` there would be noise. The rule is about
 * a line whose exit code IS the verdict.</p>
 */
const GATE_COMMANDS = /\b(mvnw|pnpm (test|conformance|lint))\b/;

/**
 * SH788 — the assertion for a gate that reports somebody else's exit code.
 *
 * <h2>The measurement, and it is the reassuring half</h2>
 *
 * <p>This rule was written after `pnpm test 2>&1 | tee js-test.log | grep -E "…"` printed `fail 2`
 * and the shell reported <b>0</b>, because a pipeline's status is its <i>last</i> command's. The
 * first thing SH788 asked was whether CI had the same shape. It does not: three of the four jobs
 * run their gate unpiped, and the Java job — the only one that pipes, into `tee` for SH354 — already
 * sets `set -o pipefail` on the line above. So this was a local habit, not a live hole.</p>
 *
 * <h2>Which is exactly when the instrument is worth writing</h2>
 *
 * <p>The `pipefail` in that job is one line with no comment saying what it defends, and SH354's rule
 * as written is about <i>keeping the log</i>. Someone adding a fourth gate would copy the `tee` —
 * that part is documented — and has no reason to copy the `set`. Then the job reports `tee`'s exit
 * code, which is 0 for as long as the disk has room, and a red suite is a green tick.</p>
 *
 * <p>Not the same failure as SH354's, which is why knowing that rule does not prevent this one:
 * there the evidence is lost and the verdict is honest, here the evidence survives and the verdict
 * is wrong. The second is worse, because nobody goes looking.</p>
 */
test("SH788: a gate that pipes its output declares pipefail, or it reports the filter's verdict", () => {
  const offenders = [];
  for (const name of readdirSync(WORKFLOWS).filter((f) => f.endsWith(".yml"))) {
    for (const block of runBlocks(read(name))) {
      const piped = block.split("\n").filter((line) => {
        const shell = line.replace(/\|\|/g, " ");
        return shell.includes("|") && GATE_COMMANDS.test(shell);
      });
      if (piped.length && !/set +-[a-z]*o +pipefail|set +-o +pipefail/.test(block)) {
        offenders.push(`${name}: ${piped[0].trim()}`);
      }
    }
  }
  assert.deepEqual(
    offenders,
    [],
    "a pipeline's exit status is its LAST command's, so a gate piped into `tee` or `grep` reports "
      + "that filter's verdict and not the suite's — green for as long as the disk has room.\n"
      + "Add `set -o pipefail` as the first line of the step's script. Keep the `tee`: SH354 is a "
      + "separate rule about not losing which test failed, and it does not imply this one.",
  );
});

test("SH788: and the reader is not vacuous — it finds the one piped gate that exists", () => {
  // A scan that parses no run block agrees with every workflow. The Java job is the case: it pipes
  // its suite into `tee` and sets `pipefail` above it, so it is both the shape this rule is about
  // and the proof the parser can see one.
  const blocks = runBlocks(read("suites.yml"));
  const piped = blocks.filter((b) => GATE_COMMANDS.test(b) && b.replace(/\|\|/g, " ").includes("|"));
  assert.ok(
    piped.length > 0 && piped.every((b) => /pipefail/.test(b)),
    `the parser found ${blocks.length} run block(s) and ${piped.length} piped gate(s); it should `
      + "find the Java job piping `clean test` into `tee` with `pipefail` set. Zero means the block "
      + "reader stopped matching this file's shape, and the rule above is guarding nothing",
  );
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

test("SH1076: the Java job checks the viglet-core pin is published before it runs the suite", async () => {
  // SH110 pinned a version only one ~/.m2 held: every local gate passed and every CI run failed
  // before the reactor, with no count and no failing name, for a month. The check that would have
  // caught it runs first, so a missing artifact fails with the version and the fix.
  const suites = read("suites.yml");
  const check = suites.indexOf("node scripts/check-published-deps.mjs");
  const suite = suites.indexOf("clean test -Dskip.npm=true");
  assert.ok(check > 0, "SH1076: the Java job no longer checks the pin against Maven Central");
  assert.ok(check < suite, "SH1076: the check runs before the suite, or the suite's failure hides it");

  const { pinnedVersion, bomUrl } = await import(new URL("../../scripts/check-published-deps.mjs", import.meta.url));
  const pom = readFileSync(join(ROOT, "pom.xml"), "utf8");
  assert.match(pinnedVersion(pom) ?? "", /^\d{4}\.\d+\.\d+$/,
    "SH1076: the reader finds the pin in pom.xml, or the check is checking nothing");
  assert.equal(bomUrl("2026.3.12"),
    "https://repo1.maven.org/maven2/com/viglet/core/viglet-core-bom/2026.3.12/viglet-core-bom-2026.3.12.pom");
});

/**
 * SH1126 — the run that must see the console's bytes is the one that builds them.
 *
 * <p>SH1043's size comparison runs against whatever build sits in the static root and `pnpm test`
 * never built one, so the gate measured a build of <em>some</em> tree with nothing saying which. It
 * went wrong in both directions on consecutive tasks: one passed against bytes older than its own
 * change, and the next failed the headroom check only because a build made outside the session
 * happened to include it.</p>
 *
 * <p>`build-info.json` lets the test tell (SH1126), and locally a mismatch is a <b>diagnostic</b> —
 * a source edit must not demand a one-minute build before `pnpm test`. Which means the number is only
 * real where something builds, and this asserts that somewhere does: the `js` job builds the console
 * and sets `SHIO_REQUIRE_CONSOLE_BUILD=1`, so "not measured" fails there. Without both halves the
 * task ships a reader that is allowed to report nothing forever, which is the failure every
 * instrument in this repository is written against — and a comment in a YAML nobody reads is not the
 * half that stops it.</p>
 */
test("SH1126: CI builds the console and refuses to be told nothing about its size", () => {
  const suites = read("suites.yml");

  assert.match(suites, /run: pnpm run build:console/,
    "SH1126: the `js` job does not build the console, so its size gate measures whatever build the "
      + "runner happens to have — which on a fresh clone is none, and the verdict is then that "
      + "nothing was measured on every run");
  assert.match(suites, /SHIO_REQUIRE_CONSOLE_BUILD: "1"/,
    "SH1126: the run that builds does not require a measurement, so a build step and a suite that "
      + "disagreed about the tree would pass quietly — which is the same defect one layer along");

  // Order matters and is worth asserting rather than trusting: a build after the suite measures
  // nothing, and the two lines look equally correct in a diff.
  assert.ok(suites.indexOf("run: pnpm run build:console") < suites.indexOf('SHIO_REQUIRE_CONSOLE_BUILD: "1"'),
    "SH1126: the console is built AFTER the run that requires it, so the suite ran against no build");
});

test("SH1259: a job that boots the jar waits for its own login, not for an open port", () => {
  // The run that found it: setup's POST landed before ShOnStartup seeded `admin`, answered 403
  // (no row reads as "setup done"), the step accepted 403, and every conformance call was a 401.
  // A workflow that runs the jar, not one that only names it: release.yml attaches it (SH1339).
  const booting = readdirSync(WORKFLOWS).filter((name) => /java -jar .*viglet-shio\.jar/.test(read(name)));
  assert.ok(booting.length >= 2,
    `SH1259: expected the conformance and console-check jobs to boot the jar, found ${booting.join(", ") || "none"}`);

  for (const name of booting) {
    const boots = runBlocks(read(name)).filter((script) => /java -jar .*viglet-shio\.jar/.test(script));
    assert.ok(boots.length, `SH1259: ${name} names the jar and no step runs it`);
    for (const script of boots) {
      assert.match(script, /SHIO_ADMIN_PASSWORD="?\$SHIO_PASSWORD"? .*java -jar/,
        `SH1259: ${name} boots the jar without SHIO_ADMIN_PASSWORD, so the password depends on `
          + "winning a race against the first-access seed");
      assert.match(script, /curl [^\n]*-u "\$SHIO_USER:\$SHIO_PASSWORD"/,
        `SH1259: ${name} calls the instance ready before its credentials authenticate — an open `
          + "port is not a usable instance");
    }
  }
});
