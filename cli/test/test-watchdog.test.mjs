/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH807 — a run that will not end names what is holding it.
 *
 * ## The failure
 *
 * SH780 removed `--test-force-exit`, which was half the race that aborted the process inside libuv.
 * The flag was also killing any process that had a handle open and would never have exited — so a
 * leaked handle used to be **invisible**, because the run passed. Without the flag the run does not
 * fail either: it does not end. In CI the job burns its 20-minute timeout and prints no summary, so
 * what a reader gets is that twenty minutes passed.
 *
 * ## Driven with a real leak, not a fabricated one
 *
 * The subject is whether a process that cannot exit is reported, which is a property of an actual
 * process. So this spawns one that leaks a listening server from a passing test, with the deadline
 * turned down to a second, and reads what comes back. A unit test of the timer would assert that
 * `setTimeout` was called.
 */
import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { promisify } from "node:util";
import { test } from "node:test";

// @scans code — this file quotes the watchdog's own message, and a raw read would take the
// description of the failure for the failure (SH740).

const run = promisify(execFile);
// The `file://` HREF, not a path: `--import` rejects a bare Windows absolute path with
// `ERR_UNSUPPORTED_ESM_URL_SCHEME`, reading `D:` as a protocol. The gate's own wiring passes a
// relative specifier, which is why this bites only here.
const WATCHDOG = new URL("../../scripts/test-watchdog.mjs", import.meta.url).href;

/** A throwaway suite: one passing test that leaves a listening server behind. */
function leakingSuite() {
  const dir = mkdtempSync(join(tmpdir(), "shio-watchdog-"));
  writeFileSync(join(dir, "leak.test.mjs"), [
    'import { createServer } from "node:http";',
    'import { test } from "node:test";',
    'test("passes, and leaks a listening server", async () => {',
    "  const server = createServer(() => {});",
    '  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));',
    "});",
  ].join("\n"));
  return dir;
}

async function runSuite(dir, file, watchdogMs) {
  // `NODE_TEST_CONTEXT` is set by the runner this test is *itself* running under, and a child that
  // inherits it behaves as a nested test child rather than a run of its own — which made both
  // assertions here fail against a watchdog that was working perfectly.
  const env = { ...process.env, SHIO_TEST_WATCHDOG_MS: String(watchdogMs) };
  delete env.NODE_TEST_CONTEXT;
  delete env.NODE_OPTIONS;

  try {
    const { stdout, stderr } = await run(process.execPath,
      ["--import", WATCHDOG, "--test", file], { cwd: dir, env });
    return { code: 0, out: `${stdout}${stderr}` };
  } catch (error) {
    return { code: error.code ?? 1, out: `${error.stdout ?? ""}${error.stderr ?? ""}` };
  }
}

test("SH807: a suite that cannot exit fails, and names the handle holding it", async () => {
  const { code, out } = await runSuite(leakingSuite(), "leak.test.mjs", 1500);

  assert.notEqual(code, 0,
    "SH807: a process that will not exit has to FAIL. Passing is what `--test-force-exit` used to "
      + "do here, and it made a leaked handle invisible; hanging is what happens without either, "
      + "and in CI that is a 20-minute timeout with no summary");
  assert.match(out, /SH807/, "the report names the rule, so a reader can find why this fired");
  assert.match(out, /TCPServerWrap/,
    "and it names the handle type still alive — a listening server here, which is the finding "
      + "itself: there is no stack to print because nothing threw and every test passed");
  assert.match(out, /leak\.test\.mjs/,
    "and node marks the file, so a hang becomes a named file rather than a silent wait");
});

test("SH807: a suite that exits on its own never meets the watchdog", async () => {
  // The half that matters on every green run: an unref'd timer must not be the reason a process
  // stays alive, or the check would turn each run into a deadline-long one.
  const dir = mkdtempSync(join(tmpdir(), "shio-watchdog-ok-"));
  writeFileSync(join(dir, "clean.test.mjs"), [
    'import { test } from "node:test";',
    'test("closes everything it opens", () => {});',
  ].join("\n"));

  const started = Date.now();
  const { code, out } = await runSuite(dir, "clean.test.mjs", 30_000);
  const elapsed = Date.now() - started;

  assert.equal(code, 0, "a clean suite passes with the watchdog installed");
  assert.doesNotMatch(out, /SH807/, "and never hears from it");
  assert.ok(elapsed < 10_000,
    `SH807: the clean suite took ${elapsed}ms against a 30s deadline, so the watchdog is holding `
      + "the loop open — `unref()` is the entire mechanism and it is not working");
});
