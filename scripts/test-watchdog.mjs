/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH807 — a suite that will not exit says what is holding it, instead of burning the CI timeout.
 *
 * ## What SH780 exposed by removing `--test-force-exit`
 *
 * That flag was half the race that aborted the process inside libuv, its removal is verified over
 * 120 runs at the provoking concurrency, and a paired measurement puts its cost at nothing. It was
 * also doing one other thing: killing a process that had a handle open and would never have exited.
 *
 * That made a leaked handle **invisible** — the run passed. Without it the run does not fail either;
 * it does not end. `pnpm test` waits, and in CI the job burns its 20-minute timeout and prints no
 * summary, so what a reader gets is the fact that twenty minutes passed.
 *
 * ## Why an unref'd timer is the whole mechanism
 *
 * `unref()` means this timer does not, by itself, keep the process alive. A healthy run therefore
 * never sees it: the loop empties, the process exits, and the timer dies with it having done
 * nothing. It fires only when something *else* is keeping the loop alive at the deadline — which is
 * precisely the condition being watched, and cannot be detected by asking whether anything is
 * pending, because at that moment plenty legitimately is.
 *
 * So the check costs nothing on every green run and is exact on the one red one.
 *
 * ## What it prints, and why that and not a stack
 *
 * `process.getActiveResourcesInfo()` names the handle types still alive. There is no stack to print:
 * nothing is failing, nothing threw, and the tests have all passed. The handle is the finding —
 * `SH805`'s probe was built for a different question and this is the same call answering a better
 * one. SH698 argued the same thing for a teardown that hangs, one layer down, and its message names
 * the server and its open sockets for the same reason.
 */
const DEADLINE_MS = Number(process.env.SHIO_TEST_WATCHDOG_MS ?? 120_000);
const started = Date.now();

if (DEADLINE_MS > 0) {
  const watchdog = setTimeout(() => {
    const alive = process.getActiveResourcesInfo();
    process.stderr.write(
      `\nSH807: this process has been alive ${Math.round((Date.now() - started) / 1000)}s and the `
      + `event loop is still held. Every test may well have passed — a handle nobody closed keeps a\n`
      + `run from ENDING rather than making it fail, and in CI that is a 20-minute timeout with no\n`
      + `summary at all.\n`
      + `Still alive: ${JSON.stringify(alive)}\n`
      + `That list is the finding: a socket, a server or a timer that a test opened and did not\n`
      + `close. \`--test-force-exit\` used to hide this by killing the process (SH780 removed it,\n`
      + `because it also aborted the process inside libuv). Raise or disable the deadline with\n`
      + `SHIO_TEST_WATCHDOG_MS if this suite is genuinely meant to take longer.\n`,
    );
    process.exit(1);
  }, DEADLINE_MS);

  // The whole mechanism: it must never be the reason the process is alive, or it would turn every
  // run into a two-minute one.
  watchdog.unref();
}
