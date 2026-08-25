/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { spawnSync } from "node:child_process"
import { readFileSync, mkdirSync, mkdtempSync, writeFileSync } from "node:fs"
import { createRequire } from "node:module"
import { tmpdir } from "node:os"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"

/**
 * SH937 — the roadmap lines that are waiting on somebody else's release, and the only thing that
 * can tell them the wait is over.
 *
 * <h2>Why a list rather than three tests</h2>
 *
 * Three lines wait on one external event — a design-system release Shio installs. SH919 wants
 * VDS78's argument-parse fix, SH923 wants a frosted panel the bento vocabulary does not export,
 * SH935 wants `BentoNavItem` to collapse `fallbackRoute` and `bentoRoute` into one field.
 *
 * SH919 shipped with an assertion that the installed gate **still** drops a positional root — a
 * strange thing to assert on purpose, and the right shape, because it goes red on the day the wait
 * ends and its message is the instruction. The other two shipped with nothing. When a release
 * lands that exports the panel, the only thing that would say so is somebody re-reading the
 * roadmap and then re-reading `node_modules` — which is how SH919's line came to still read ⏳
 * after the fix had been committed upstream for a week.
 *
 * So the trick is generalised: the waits are a **named list**, each entry carrying the id, what it
 * is waiting for, and a probe that answers "is the wait over". A ceiling would let one wait pay for
 * the others; a list makes each expiry its own diff, and the next wait costs a line rather than a
 * test file.
 *
 * <h2>Two probes per wait, and why the coarse one is the important one</h2>
 *
 * A precise probe reads the installed package for the exact thing the wait wants. It can be wrong
 * in the direction that matters: SH923 is waiting for a component whose **name nobody has chosen
 * yet**, so a probe looking for `BentoPanel` misses a release that called it `BentoSurface`, and
 * misses it silently — a wait-watcher that under-reports is worse than none, because it is trusted.
 *
 * The coarse probe cannot miss. Every wait here ends with *a release*, so pinning the installed
 * version and failing when it moves catches all three regardless of what the release is called or
 * what it contains. It costs a deliberate red on every design-system bump, which is exactly the
 * moment a human should be re-reading these three lines. Clearing it is one edit to `PINNED`.
 *
 * <h2>What this replaces</h2>
 *
 * `no-duplicate-components.test.mjs` used to carry SH919's expiry assertion beside the gate it was
 * about. It was right and it is here now, because two files asserting the same fact about the same
 * installed package is two files that can disagree about it — and the one nobody edits is the one
 * that stays green.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const CONSOLE_ROOT = join(HERE, "..")
const require_ = createRequire(join(CONSOLE_ROOT, "noop.js"))

/**
 * The package root, reached through the one subpath it exports that has a fixed place in the tree.
 * `package.json` is deliberately not in the package's `exports`, so `require` cannot reach it by
 * name — which is also why this resolves the manifest rather than asking for the version directly.
 */
const PACKAGE_ROOT = dirname(dirname(require_.resolve("@viglet/viglet-design-system/exports.json")))
const INSTALLED = JSON.parse(readFileSync(join(PACKAGE_ROOT, "package.json"), "utf8")).version

/**
 * The version every wait below was measured against.
 *
 * Bumping the design system is meant to fail this. Read the three entries, decide which waits are
 * over, ship those, and then move this line — in that order, because moving it first is how a
 * release that ended a wait passes through unnoticed.
 */
const PINNED = "2026.3.4"

function gatePath() {
  return join(PACKAGE_ROOT, "scripts", "check-duplicates.mjs")
}

function runGate(args, cwd) {
  const result = spawnSync(process.execPath, [gatePath(), ...args], { cwd, encoding: "utf8" })
  return `${result.stdout}${result.stderr}`
}

/** A throwaway tree with a duplicate planted in `src` and an empty `elsewhere` beside it. */
function plantedTree(prefix) {
  const dir = mkdtempSync(join(tmpdir(), prefix))
  mkdirSync(join(dir, "src"))
  mkdirSync(join(dir, "elsewhere"))
  writeFileSync(join(dir, "src", "planted.tsx"), "export function DialogDelete() { return null }\n")
  return dir
}

const BENTO_TYPES = join(PACKAGE_ROOT, "dist", "bento")

function bentoIndex() {
  return readFileSync(join(BENTO_TYPES, "index.d.ts"), "utf8")
}

function bentoNavTypes() {
  return readFileSync(join(BENTO_TYPES, "bento-nav.d.ts"), "utf8")
}

/**
 * One line per roadmap line that is waiting on a design-system release.
 *
 * `over()` answers "has the wait ended". `proof()` answers "is `over()` reading anything at all" —
 * the failure a probe cannot catch about itself, since a read that has lost its file reports
 * "not yet" for the same reason a real wait does.
 */
const WAITS = [
  {
    id: "SH919",
    waitingFor: "VDS78 — the duplicate gate reads the positional root it is given",
    over: () => {
      // A duplicate in `src` and a root argument pointing at an empty directory. A gate that reads
      // its argument finds nothing; a gate that drops it scans `./src` and finds the plant.
      const dir = plantedTree("sh919-")
      return !/DialogDelete/.test(runGate(["elsewhere"], dir))
    },
    proof: () => {
      // With `--manifest` present the parse is accidentally correct — the flag sits at index 1, so
      // `manifestFlag + 1` lands on the manifest path as intended. That is why the upstream suite
      // never caught this, and here it proves the spawn, the plant and the scan are all live.
      const dir = plantedTree("sh919-proof-")
      const manifest = require_.resolve("@viglet/viglet-design-system/exports.json")
      return !/DialogDelete/.test(runGate(["elsewhere", "--manifest", manifest], dir))
    },
    proofIs: "with --manifest the gate reads its root and must not see the plant in ./src",
    instruction: "Delete NO_POSITIONAL_ROOTS from no-duplicate-components.test.mjs, pass `src` the"
      + " ordinary way, drop this entry, and ship SH919.",
  },
  {
    id: "SH923",
    waitingFor: "a frosted panel with no heading, exported from the bento entry point",
    over: () => /export \{[^}]*Bento(Panel|Surface|Glass|Frame)\b/.test(bentoIndex()),
    proof: () => /export \{[^}]*BentoSection\b/.test(bentoIndex()),
    proofIs: "the bento index still declares BentoSection, so this read reached the real manifest",
    instruction: "Re-export it from components/adaptive-panel.tsx, delete the local implementation,"
      + " ship SH923 — and then SH932, which its design says waits on this.",
  },
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
    `SH937: **the design system moved from ${PINNED} to ${INSTALLED}.** Three roadmap lines are`
      + " waiting on a release, and this is the one check that cannot miss the release that ends"
      + " them — the precise probes below each look for a specific thing, and one of them is"
      + " waiting for a component whose name has not been chosen yet.\n\n"
      + WAITS.map((w) => `  ${w.id} — ${w.waitingFor}\n      ${w.instruction}`).join("\n")
      + `\n\nRead all three against ${INSTALLED}, ship whichever are over, and only then move`
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
  // yet" when its read finds nothing — the same answer a real wait gives — so a moved file, a
  // renamed export or a gate that no longer spawns would leave all three green for ever, which is
  // precisely the state SH937 exists to end.
  for (const wait of WAITS) {
    assert.equal(wait.proof(), true,
      `SH937: ${wait.id}'s probe is not reading what it thinks it is — ${wait.proofIs}.`
        + " Until that holds, this entry's 'still waiting' result means nothing: it would report"
        + " a wait that has been over for months exactly as it reports a real one.")
  }
})
