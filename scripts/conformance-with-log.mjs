/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH755 — `pnpm conformance`, with the output kept and the outcome recorded.
 *
 * ## Two lessons this gate had not been given
 *
 * SH645 built `test-with-log.mjs` because a red `pnpm test` had twice been read through a `grep`
 * for the summary line, so what survived was a count and never the failing test's name. SH354 says
 * the same thing about the Maven suite in `agents.md`. The conformance gate — the slowest of the
 * three, the only one needing a running instance, and therefore the one nobody re-runs casually —
 * had neither: it streamed to a terminal and was gone.
 *
 * That is worse here than there. A conformance red can be staleness, credentials, a downed
 * instance or a real defect, and telling them apart is the first thing anybody does. Losing the run
 * means doing the ten-minute part again to read a line.
 *
 * ## And it is where the missing half of SH579 attaches
 *
 * The ledger claims which gates are red; nothing compared the claim to what a gate said. So this
 * also leaves a stamp, which is what makes `red-suites.test.mjs` able to check the claim at all.
 * Same shape as the JS gate, deliberately: two wrappers that differ would be two behaviours to
 * remember.
 */
import { join } from "node:path";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";

import { stamp } from "./gate-stamp.mjs";
import { runWithLog } from "./test-with-log.mjs";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");

/** One fixed name, for the reason the JS log takes: the useful comparison is the previous run. */
const LOG = join(ROOT, "shio-conformance.log");

/**
 * The command, unchanged from what `package.json` ran before this wrapper existed. Wrapping a gate
 * must not quietly change which scenarios it runs — that is a decision, and doing it as a side
 * effect of adding a log is the scope change this repository keeps finding.
 */
export const PNPM_ARGS = ["--reporter", "append-only",
  "--filter", "@viglet/shio", "run", "conformance"];

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const code = await runWithLog("pnpm", PNPM_ARGS, LOG);
  stamp(ROOT, "conformance", code);
  process.exit(code);
}
