/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH645 — the JS gate keeps its run, and does not swallow a failure to do it.
 *
 * ## What this asserts, and why it is not a test of the suite
 *
 * `pnpm test` reported `cli test: ℹ fail 1` on an otherwise green tree, twice, and neither time was
 * the failing test's NAME captured: both runs were read through a `grep` for the summary line, which
 * is the one thing a filter leaves behind. The fix is capture — `scripts/test-with-log.mjs` streams
 * every byte to stdout and to `shio-js-test.log` — and capture has exactly two load-bearing
 * properties, both of which can fail silently:
 *
 * - **the output is kept**, or the next sighting is another count;
 * - **the exit code is the child's**, or the gate stops failing and nobody notices for a block.
 *
 * The second is the dangerous one. A runner that logs perfectly and returns 0 on a red suite is
 * strictly worse than the flake it was written for, because it converts an intermittent false red
 * into a permanent false green.
 *
 * Driven against a trivial child rather than against `pnpm test`, which is why `runWithLog` is
 * exported at all: running the workspace suite to test the thing that runs the workspace suite would
 * take minutes and assert the same two facts less directly.
 *
 * ## Why it lives in `cli/`
 *
 * Because `cli/` is the package whose suite reported the flake, and because the root has no test
 * runner of its own — adding one for a single file would be a ninth package in the gate. The subject
 * is the repository's, not this package's, which is stated here so nobody moves it on the assumption
 * that a `cli/` test must be about the CLI.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { PNPM_ARGS, runWithLog } from "../../scripts/test-with-log.mjs";

/** A stdout/stderr pair that records instead of printing, so a case can read what a caller saw. */
function recorder() {
  const seen = { out: "", err: "" };
  return {
    seen,
    stdout: { write: (chunk) => { seen.out += String(chunk) } },
    stderr: { write: (chunk) => { seen.err += String(chunk) } },
  };
}

async function inTemp(run) {
  const dir = await mkdtemp(join(tmpdir(), "shio-sh645-"));
  try {
    return await run(join(dir, "run.log"));
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
}

test("SH645: a failing child's exit code reaches the caller, unchanged", async () => {
  const io = recorder();
  const code = await inTemp((log) =>
    runWithLog(process.execPath, ["-e", "process.exit(3)"], log, { io, shell: false }));

  assert.equal(
    code,
    3,
    "the runner returns the child's code. A gate that logs beautifully and exits 0 on red converts "
      + "an intermittent false red into a permanent false green, which is worse than the flake",
  );
  assert.match(io.seen.out, /FAILED \(exit 3\)/,
    "and says so where a human is looking, naming the log rather than assuming they know it exists");
});

test("SH735: a green re-run cannot erase the red run it followed", async () => {
  // The assertion that would have caught it, at the boundary it crossed. `fixture-traits.test.mjs`
  // went red at file level with all nine assertions green — SH698's exact symptom — and the re-run
  // that checks whether a flake reproduces truncated the log carrying SH698's own stderr handler
  // output. That handler exists BECAUSE the failure never printed a reason.
  const io = recorder();
  await inTemp(async (log) => {
    await runWithLog(process.execPath, ["-e", "console.log('the reason it went red'); process.exit(1)"],
      log, { io, shell: false });
    const kept = log.replace(/(\.log)?$/, ".red.log");
    assert.match(await readFile(kept, "utf8"), /the reason it went red/,
      "SH735: the failing run has to survive somewhere the next run does not write");

    // The re-run. This is the whole defect: it is what anybody does after a flake.
    await runWithLog(process.execPath, ["-e", "0"], log, { io, shell: false });

    assert.doesNotMatch(await readFile(log, "utf8"), /the reason it went red/,
      "the fixed name is still overwritten, which is the behaviour its own comment argues for — "
      + "the green path is deliberately untouched");
    assert.match(await readFile(kept, "utf8"), /the reason it went red/,
      "SH735: and the kept copy is still there. SH354 made this argument one level over, for the "
      + "Maven suite; this is that defect inside the instrument built from that lesson");
  });
});

test("SH735: a green run keeps nothing aside, so the copy means what it says", async () => {
  const io = recorder();
  await inTemp(async (log) => {
    await runWithLog(process.execPath, ["-e", "0"], log, { io, shell: false });
    await assert.rejects(() => readFile(log.replace(/(\.log)?$/, ".red.log"), "utf8"),
      "a .red.log written by a green run would make its presence meaningless, and somebody would "
      + "read a stale one as this run's evidence");
  });
});

test("SH645: a green child exits 0 and the log still names itself", async () => {
  const io = recorder();
  const code = await inTemp((log) => runWithLog(process.execPath, ["-e", "0"], log, { io, shell: false }));

  assert.equal(code, 0);
  assert.match(io.seen.out, /green — full output in/,
    "a green run says where the evidence is too: the next red one belongs to somebody who did not "
      + "read this file");
});

test("SH645: every byte the child wrote is in the log, not only the summary", async () => {
  const io = recorder();
  const body = await inTemp(async (log) => {
    await runWithLog(process.execPath,
      // Both streams, because the defect is a caller filtering ONE of them: a failing assertion's
      // text and its summary line can arrive on different ones.
      ["-e", "console.log('a-line-only-the-log-should-hold');console.error('and-a-stderr-one')"],
      log, { io, shell: false });
    return readFile(log, "utf8");
  });

  assert.match(body, /a-line-only-the-log-should-hold/);
  assert.match(body, /and-a-stderr-one/);
  assert.match(body, /^\$ /, "the log opens with the command, so a reader knows what produced it");
});

test("SH645: the runner is what `pnpm test` runs, over the packages the gate has always run", async () => {
  const pkg = JSON.parse(await readFile(new URL("../../package.json", import.meta.url), "utf8"));

  assert.match(
    pkg.scripts.test,
    /scripts\/run-gate\.mjs js/,
    "SH645: `pnpm test` must route through the runner. Restoring the bare `pnpm --filter …` here "
      + "is how the capture is lost again, and it would look like a simplification.\n"
      + "SH802 moved that runner from `test-with-log.mjs` to `run-gate.mjs`, which drives all four "
      + "gates from one table — the rule is the routing, never the filename",
  );
  // The filter list is duplicated between the runner and `test:raw`, deliberately (changing which
  // packages are tested is a decision). Duplicated and unchecked is how the two drift, so the check
  // is here rather than the duplication being argued away.
  const raw = pkg.scripts["test:raw"] ?? ""
  const inRaw = [...raw.matchAll(/--filter (\S+)/g)].map((m) => m[1]).sort()
  const inRunner = PNPM_ARGS.filter((_, at) => PNPM_ARGS[at - 1] === "--filter").sort()
  assert.deepEqual(inRunner, inRaw,
    "the runner and `test:raw` test the same packages, or the gate and its escape hatch disagree "
      + "about what the gate is");
});

test("SH1292: the JS gate runs every package, even after one of them is red", async () => {
  // pnpm's recursive run stops at the first failing package (ERR_PNPM_RECURSIVE_RUN_FIRST_FAIL).
  // With one declared red in shio-react, the cli package never ran, and its two real failures were
  // invisible: a red-suites exception licenses the tests it names, and a bail extended that licence
  // to every package behind it.
  const pkg = JSON.parse(await readFile(new URL("../../package.json", import.meta.url), "utf8"));
  assert.ok(PNPM_ARGS.includes("--no-bail"),
    "SH1292: GATE_RUNS.js must pass --no-bail, or a red package hides every package after it");
  assert.match(pkg.scripts["test:raw"] ?? "", /--no-bail/,
    "SH1292: `test:raw` is the gate without its log, and must run the same packages the same way");
});
