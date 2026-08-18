/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH808 — summon a flake instead of waiting for one.
 *
 * ## The measurement that made this worth writing
 *
 * `fixture-traits.test.mjs` failed at file level with every assertion green. It then resisted about
 * **a hundred and twenty runs**: SH778 shipped an instrument and waited, twelve runs, then thirty
 * seven, then fifty, and the negative result began to read as evidence the defect had gone.
 *
 * It was reproduced **on the first run** of a loop that repeated the whole suite at
 * `--test-concurrency=16` — a setting the gate never uses — and again on the first run of a second
 * loop. Thirty runs at `24` produced none. The kept run named the cause in a line nobody had seen:
 * a libuv abort on a handle already closing, which also explained why the instrument had stayed
 * silent, because an abort skips `process.on("exit")`.
 *
 * Eighty runs of waiting cost days of wall clock. Provoking it cost four minutes.
 *
 * ## The two properties that matter more than the ergonomics
 *
 * **It keeps the run.** The evidence was one line among fifteen hundred and a `grep` for the summary
 * would have discarded it — SH354's rule, and the reason `runWithLog` is used here rather than a
 * shell pipeline. A failing run is also copied aside by `keepIfRed`, so the next attempt cannot
 * erase it (SH735).
 *
 * **It takes the concurrency.** The whole finding is that the gate's own setting is the one that
 * does not reproduce. A repeater that ran the suite as configured would have found nothing in forty
 * attempts, which is precisely what the eighty runs of waiting had already proved.
 *
 *   pnpm flake                          40 runs at concurrency 16
 *   pnpm flake --runs 100 --concurrency 24
 *   pnpm flake --files "test/fixture-*.test.mjs"
 */
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

import { runWithLog } from "./test-with-log.mjs";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const CLI = join(ROOT, "cli");

/** Its own log, so a hunt never truncates the gate's evidence from `shio-js-test.log`. */
const LOG = join(ROOT, "shio-flake.log");

/**
 * Defaults chosen from the two sightings rather than from taste.
 *
 * <p>Sixteen is the concurrency that reproduced, twice, on the first run of a loop; twenty-four
 * produced nothing in thirty. Forty runs is roughly four minutes and is what the sightings took —
 * a hunt that finds nothing in forty says something, and one that runs for an hour says only that
 * somebody left it going.</p>
 */
const DEFAULTS = { runs: 40, concurrency: 16, files: "test/*.test.mjs" };

export function parse(argv) {
  const options = { ...DEFAULTS };
  for (let at = 0; at < argv.length; at += 1) {
    const flag = argv[at].replace(/^--/, "");
    if (!Object.hasOwn(DEFAULTS, flag)) {
      throw new Error(`flake: unknown option \`${argv[at]}\` — one of `
        + `${Object.keys(DEFAULTS).map((name) => `--${name}`).join(", ")}`);
    }
    const value = argv[at + 1];
    if (value === undefined) {
      throw new Error(`flake: \`--${flag}\` takes a value`);
    }
    options[flag] = flag === "files" ? value : Number(value);
    at += 1;
  }
  return options;
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const { runs, concurrency, files } = parse(process.argv.slice(2));
  process.stdout.write(`SH808: hunting — ${runs} run(s) of ${files} at concurrency ${concurrency}.\n`
    + "The gate's own setting is the one that does not reproduce, which is the point.\n\n");

  for (let run = 1; run <= runs; run += 1) {
    // `shell: false`, which `runWithLog`'s own comment predicts: a shell re-splits the command on
    // spaces and `process.execPath` here is `C:\Program Files\nodejs\node.exe`. Left at the default
    // the first hunt reported a SIGHTING on run 1 whose log read
    // `'C:\Program' is not recognised` — a repeater that calls its own spawn failure a sighting is
    // worse than no repeater, and the only reason it was caught immediately is that it keeps the run.
    const code = await runWithLog(process.execPath,
      ["--test", `--test-concurrency=${concurrency}`, files], LOG,
      {
        cwd: CLI,
        shell: false,
        label: `flake run ${run}/${runs}`,
        // Quiet per run: forty full suites of output is not readable, and the whole run is on disk.
        io: { stdout: { write() {} }, stderr: { write() {} } },
      });

    if (code !== 0) {
      process.stdout.write(`\nSH808: SIGHTING on run ${run} of ${runs}, exit ${code}.\n`
        + `The whole run is at ${LOG} and a copy that the next attempt cannot truncate is beside it `
        + "as `.red.log` (SH735).\nRead it before running anything else: the evidence is usually one "
        + "line among fifteen hundred, and a `grep` for the summary is what discarded it last time "
        + "(SH354).\n");
      process.exit(1);
    }
    process.stdout.write(`  run ${run}/${runs} green\n`);
  }

  process.stdout.write(`\nSH808: no sighting in ${runs} runs at concurrency ${concurrency}.\n`
    + "That is a result and not a silence — say the number, because the next person's alternative "
    + "is waiting.\n");
}
