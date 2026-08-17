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
import { stamp } from "./gate-stamp.mjs";
import { runWithLog } from "./test-with-log.mjs";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const [key, ...extra] = process.argv.slice(2);
  if (!key || !Object.hasOwn(GATE_RUNS, key)) {
    process.stderr.write(`run-gate: expected one of ${Object.keys(GATE_RUNS).join(", ")}`
      + `${key ? `, got ${key}` : " and got nothing"}\n`);
    process.exit(2);
  }
  const gate = gateRun(key, ROOT);
  const code = await runWithLog(gate.command, [...gate.args, ...extra], gate.log,
    { label: gate.label });
  stamp(ROOT, key, code);
  process.exit(code);
}
