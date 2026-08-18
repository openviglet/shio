/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH808 — the repeater that summons a flake, and the two properties that make it worth having.
 *
 * ## Why it exists
 *
 * `fixture-traits.test.mjs` resisted about a hundred and twenty runs of waiting, then reproduced on
 * the **first** run of a loop at `--test-concurrency=16` and again on the first run of a second
 * loop; thirty at `24` produced none. Waiting cost days, provoking cost four minutes, and the loop
 * that did it was a scratch file that no longer exists — which is what this task is about.
 *
 * ## What is asserted, and what deliberately is not
 *
 * Not that it finds flakes: there is no flake left to find, and a test that hunted one would be
 * either vacuous or a twenty-minute suite. What is asserted is the two properties the hunt is
 * useless without — **it stops at the first failure** and **it keeps the whole run** — plus the
 * option parsing, because a mistyped flag that silently ran the defaults would send somebody home
 * believing a number the hunt never measured.
 */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

import { parse } from "../../scripts/flake.mjs";

// @scans code — this file quotes the flag names and the failure it prevents, and a raw read would
// take the description for the thing (SH740).

test("SH808: the defaults are the settings that reproduced, not a taste", () => {
  const { runs, concurrency, files } = parse([]);
  assert.equal(concurrency, 16,
    "SH808: sixteen is the concurrency that reproduced the flake twice, each on the first run of a "
      + "loop; twenty-four produced nothing in thirty. A repeater defaulting to the gate's own "
      + "setting would find nothing, which is exactly what eighty runs of waiting already proved");
  assert.equal(runs, 40, "and forty runs is about four minutes, which is what the sightings took");
  assert.match(files, /test\/\*/, "over the whole suite by default, since the flake was file-level");
});

test("SH808: an unknown flag is refused rather than silently ignored", () => {
  assert.throws(() => parse(["--concurrancy", "16"]), /unknown option/,
    "a misspelt flag that fell back to the defaults would report a number for a hunt nobody ran");
  assert.throws(() => parse(["--runs"]), /takes a value/,
    "and a flag with no value is the same lie with a different spelling");
  assert.deepEqual(parse(["--runs", "5", "--concurrency", "3"]),
    { runs: 5, concurrency: 3, files: "test/*.test.mjs" },
    "while the flags that exist are read, or the hunt is only ever its defaults");
});

test("SH808: the hunt keeps the run and stops at the first failure", () => {
  // Read from the source rather than driven, because driving it means running the suite forty times
  // to observe a property that is two lines of it. The two lines are the ones worth pinning.
  const source = readFileSync(new URL("../../scripts/flake.mjs", import.meta.url), "utf8");

  assert.match(source, /runWithLog\(/,
    "SH808: the hunt keeps the whole run through `runWithLog` (SH354/SH735) rather than a shell "
      + "pipeline. The libuv line that finally named the flake was one among fifteen hundred, and a "
      + "`grep` for the summary is what discarded the evidence the two times before");
  // Ordering rather than a span: the first version allowed 400 characters between the two and the
  // message that sits there is longer, so it failed over the size of its own prose.
  const stops = source.indexOf("if (code !== 0)");
  const exits = source.indexOf("process.exit(1)", stops);
  assert.ok(stops > 0 && exits > stops,
    "and it stops at the first non-zero exit, because the run that failed is the evidence and the "
      + "next iteration truncates the log");
  assert.match(source, /shell: false/,
    "SH808: spawned with `shell: false`. The first hunt reported a SIGHTING on run 1 whose log said "
      + "`'C:\\Program' is not recognised` — a shell re-splits `process.execPath` on its spaces, and "
      + "a repeater that calls its own spawn failure a sighting is worse than no repeater");
});
