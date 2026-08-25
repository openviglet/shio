/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { readFileSync } from "node:fs"
import { createRequire } from "node:module"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"

/**
 * SH937 — the roadmap lines that are waiting on somebody else's release, and the only thing that
 * can tell them the wait is over.
 *
 * <h2>Why a list rather than one test per wait</h2>
 *
 * Three lines waited on one external event — a design-system release Shio installs. SH919 wanted
 * VDS78's argument-parse fix, SH923 wanted a frosted panel the bento vocabulary did not export,
 * SH935 wants `BentoNavItem` to collapse `fallbackRoute` and `bentoRoute` into one field.
 *
 * SH919 shipped with an assertion that the installed gate **still** dropped a positional root — a
 * strange thing to assert on purpose, and the right shape, because it goes red on the day the wait
 * ends and its message is the instruction. The other two shipped with nothing, and SH919's line
 * still read ⏳ a week after its fix was committed upstream, because "has it landed" was three
 * reads across two repositories that somebody had to remember to do.
 *
 * <h2>It has been through one release, and it worked</h2>
 *
 * 2026.3.5 carried VDS78 and VDS79. This reader failed three times in one run: the version had
 * moved, SH919's wait was over, SH923's wait was over — and it correctly left SH935 alone, which
 * that release deliberately did not address. Both were shipped off the back of it. That is the
 * measurement this file exists to produce, so it is recorded here rather than argued.
 *
 * <h2>Two probes per wait, and why the coarse one is the important one</h2>
 *
 * A precise probe reads the installed package for the exact thing the wait wants — SH935's is
 * `fallbackRoute` disappearing from the nav type. It can be wrong in the direction that matters:
 * SH923 was waiting for a component **whose name nobody had chosen yet**, so a probe looking for
 * `BentoPanel` would have missed a release that called it `BentoSurface`, and missed it silently.
 * A wait-watcher that under-reports is worse than none, because it is trusted.
 *
 * The coarse probe cannot miss. Every wait here ends with *a release*, so pinning the installed
 * version and failing when it moves catches the release regardless of what it is called or what it
 * contains. It costs a deliberate red on every design-system bump, which is exactly the moment
 * somebody should be re-reading these entries. Clearing it is one edit to `PINNED`.
 *
 * <h2>What this replaced</h2>
 *
 * `no-duplicate-components.test.mjs` carried SH919's expiry assertion beside the gate it was about.
 * It was right, and it moved here because two files asserting the same fact about the same
 * installed package are two files that can disagree about it — and the one nobody edits is the one
 * that stays green. That assertion has since done its job and been deleted with SH919.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const CONSOLE_ROOT = join(HERE, "..")
const require_ = createRequire(join(CONSOLE_ROOT, "noop.js"))

/**
 * The package root, reached through the one subpath it exports that has a fixed place in the tree.
 * `package.json` is deliberately not in the package's `exports`, so `require` cannot reach it by
 * name — which is why this resolves the manifest rather than asking for the version directly.
 */
const PACKAGE_ROOT = dirname(dirname(require_.resolve("@viglet/viglet-design-system/exports.json")))
const INSTALLED = JSON.parse(readFileSync(join(PACKAGE_ROOT, "package.json"), "utf8")).version

/**
 * The version every wait below was measured against.
 *
 * Bumping the design system is meant to fail this. Read the entries, decide which waits are over,
 * ship those, and then move this line — in that order, because moving it first is how a release
 * that ended a wait passes through unnoticed.
 */
const PINNED = "2026.3.5"

function bentoNavTypes() {
  return readFileSync(join(PACKAGE_ROOT, "dist", "bento", "bento-nav.d.ts"), "utf8")
}

/**
 * One entry per roadmap line that is waiting on a design-system release.
 *
 * `over()` answers "has the wait ended". `proof()` answers "is `over()` reading anything at all" —
 * the failure a probe cannot catch about itself, since a read that has lost its file reports
 * "not yet" for the same reason a real wait does.
 */
const WAITS = [
  {
    id: "SH935",
    waitingFor: "BentoNavItem collapsing fallbackRoute and bentoRoute into one route field",
    over: () => !/\bfallbackRoute\b/.test(bentoNavTypes()),
    proof: () => /\bbentoRoute\b/.test(bentoNavTypes()),
    proofIs: "the nav type still declares bentoRoute, so this read reached the real declaration",
    instruction: "Collapse the two fields at every entry in bento-nav.ts and ship SH935.",
  },
]

test("SH937: the installed design system is the version these waits were measured against", () => {
  assert.equal(INSTALLED, PINNED,
    `SH937: **the design system moved from ${PINNED} to ${INSTALLED}.** Some roadmap lines are`
      + " waiting on a release, and this is the one check that cannot miss the release that ends"
      + " them — a precise probe looks for one specific thing, and the thing a wait is waiting for"
      + " does not always arrive under the name the probe expects.\n\n"
      + WAITS.map((w) => `  ${w.id} — ${w.waitingFor}\n      ${w.instruction}`).join("\n")
      + `\n\nRead every entry against ${INSTALLED}, ship whichever are over, and only then move`
      + " PINNED. Moving it first is how a release that ended a wait passes through unnoticed.")
})

for (const wait of WAITS) {
  test(`SH937: ${wait.id} is still waiting — ${wait.waitingFor}`, () => {
    assert.equal(wait.over(), false,
      `SH937: **the wait is over.** ${wait.id} wanted ${wait.waitingFor}, and the installed`
        + ` ${INSTALLED} now has it. This is good news, and this assertion is the only thing`
        + " standing between you and a tidier suite:\n"
        + `  ${wait.instruction}\n`
        + "  Then delete this entry from WAITS.\n"
        + "  If instead the probe is reading something else, check its `proof` — an entry whose"
        + " read has drifted reports the wrong answer in both directions.")
  })
}

test("SH937: the probes read the installed package, not nothing", () => {
  // The one failure a wait-watcher cannot catch about itself. Every `over()` above answers "not
  // yet" when its read finds nothing — the same answer a real wait gives — so a moved file or a
  // renamed export would leave the list green for ever, which is precisely the state SH937 exists
  // to end. A list that has emptied is a different thing and is fine: nothing is waiting.
  for (const wait of WAITS) {
    assert.equal(wait.proof(), true,
      `SH937: ${wait.id}'s probe is not reading what it thinks it is — ${wait.proofIs}.`
        + " Until that holds, this entry's 'still waiting' result means nothing: it would report"
        + " a wait that has been over for months exactly as it reports a real one.")
  }
})
