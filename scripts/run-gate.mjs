/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH802 — one runner over the gate table, where there were four wrappers.
 *
 * Every gate keeps the two properties its wrapper existed for: the output is kept (SH645, SH354)
 * and the exit code is not swallowed. It also leaves a stamp, which is what lets the comparators
 * check `red-suites.json`'s claim against what a gate actually said (SH755, SH798, SH800).
 *
 * What differs per gate is a row in `gates.mjs`, prose included. What does not differ is here.
 *
 *   node scripts/run-gate.mjs <key> [extra args passed to the command]
 */
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

import { GATE_RUNS, gateRun } from "./gates.mjs";
import { acquire, release, stamp } from "./gate-stamp.mjs";
import { runWithLog } from "./test-with-log.mjs";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const [key, ...extra] = process.argv.slice(2);
  if (!key || !Object.hasOwn(GATE_RUNS, key)) {
    process.stderr.write(`run-gate: expected one of ${Object.keys(GATE_RUNS).join(", ")}`
      + `${key ? `, got ${key}` : " and got nothing"}\n`);
    process.exit(2);
  }

  // SH803 — one run of a gate at a time. Two share a log and, for `java`, a `target/`, and the
  // second one's damage is now recorded as that gate's colour rather than merely confusing.
  const lock = acquire(ROOT, key, process.pid, new Date().toISOString());
  if (!lock.taken) {
    process.stderr.write(
      `\nrun-gate: the \`${key}\` gate is already running here — pid ${lock.holder?.pid}, since `
      + `${lock.holder?.since ?? "an unrecorded time"}.\n`
      + "Two runs of one gate share a log and a build directory, and the second corrupts both: a "
      + "measured collision reported 3 errors over 1092 tests where the tree alone reports 1894 "
      + "green, and stamped that as the gate's colour (SH803).\n"
      + "Wait for it, or stop it. If you had forgotten it was backgrounded, that is who this "
      + `message is for. A dead holder is cleared automatically, so \`.gates/${key}.lock\` never `
      + "outlives the process that wrote it.\n",
    );
    process.exit(2);
  }
  if (lock.stale) {
    process.stdout.write(`run-gate: cleared a ${key} lock left by pid ${lock.stale.pid}, which is `
      + "gone — a crashed run must not stop the next one.\n");
  }

  // `process.exit` is AFTER the `finally`, never inside the `try`: exiting terminates the process
  // synchronously and a `finally` does not run, so the lock would outlive every successful run and
  // the guard would refuse the next one — a lock that leaks is worse than no lock.
  let code = 2;
  try {
    const gate = gateRun(key, ROOT);
    code = await runWithLog(gate.command, [...gate.args, ...extra], gate.log,
      { label: gate.label });
    stamp(ROOT, key, code);
  } finally {
    release(ROOT, key);
  }
  process.exit(code);
}
