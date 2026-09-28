/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { existsSync, readdirSync, readFileSync } from "node:fs"
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
 * <h2>The second release it read</h2>
 *
 * SH940 moved the floor from 2026.3.5 to 2026.3.10 and this file went red on the version, as it
 * should. SH935's wait was still not over, and never will be: that line shipped with the collapse
 * abandoned, because Turing reads the two fields differently, so its entry was waiting on a release
 * nobody intends to cut. It left the list rather than being carried as a wait with no end. The two
 * lines that do wait on the package now, SH943 and SH944, entered it in the same read.
 *
 * <h2>The third release it read, and the one it was built for</h2>
 *
 * SH1088 moved the floor from 2026.3.10 to 2026.3.12, and eleven of fifteen waits ended at once:
 * SH943, SH946, SH947, SH963, SH964, SH966, SH968, SH988, SH1007, SH1024 and SH1026. Each read
 * "the wait is over" by name, which is the whole case for a probe per wait over one version pin.
 * SH963 shipped in the same commit, because the release removed the provider it existed to delete
 * and the console did not compile until it went. The other ten left this list and lost their
 * `DS VDS` dep, so they are ordinary work now. Four still wait: SH944, SH948, SH1051 and SH1053.
 *
 * <h2>And the list emptied, which the check was not written to allow</h2>
 *
 * Those four were read against 2026.3.12 and all four waits were over; SH944, SH1051 and SH1053
 * shipped with their entries still here, because the comparison ran in one direction only — a
 * waiting line with no entry failed, an entry whose line no longer waited did not. Dropping SH948's
 * dep then emptied the roadmap's side, and the check that its read matched *something* went red on
 * the state the header had always called fine. So both directions are asserted now, and the
 * line-shape proof reads a sample line instead of requiring the backlog to hold a wait (SH948).
 *
 * <h2>2026.3.15, and a round that named its waits another way</h2>
 *
 * 2026.3.15 carried VDS180–VDS185, the cross-product round SH1191–SH1204 was filed against. No
 * entry was waiting here, because those lines name the tasks as `VDS<n> in viglet-design-system`
 * rather than as `DS VDS<n>` waits, so the pin moved on its own. Their deps are dropped one by one
 * as each line ships against the release.
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
const PINNED = "2026.3.15"

function manifest() {
  return JSON.parse(readFileSync(join(PACKAGE_ROOT, "package.json"), "utf8"))
}

/** What one subpath exports, by name. Re-read per call: a probe reads the installed package. */
function exported(subpath) {
  const emitted = JSON.parse(readFileSync(join(PACKAGE_ROOT, "dist", "exports.json"), "utf8"))
  return emitted.entries?.[subpath]?.values ?? []
}

/**
 * One emitted declaration, or the empty string when it is gone.
 *
 * A missing file reads as an absence rather than throwing, because for two of the waits below the
 * absence *is* the thing waited for — VDS146 removes a type, and removing the file that declares
 * it is one of the shapes that takes.
 */
function declaration(file) {
  const path = join(PACKAGE_ROOT, "dist", file)
  return existsSync(path) ? readFileSync(path, "utf8") : ""
}

/** Whether the package exports this name, from whichever subpath carries it. */
function exportsAnywhere(name) {
  const emitted = JSON.parse(readFileSync(join(PACKAGE_ROOT, "dist", "exports.json"), "utf8"))
  return Object.values(emitted.entries ?? {}).some((entry) => (entry.values ?? []).includes(name))
}

/** Every emitted bento declaration at once — for a wait about a shape rather than about a name. */
function bentoDeclarations() {
  const root = join(PACKAGE_ROOT, "dist", "bento")
  if (!existsSync(root)) return ""
  return readdirSync(root)
    .filter((file) => file.endsWith(".d.ts"))
    .map((file) => readFileSync(join(root, file), "utf8"))
    .join("\n")
}

/**
 * One entry per roadmap line that is waiting on a design-system release.
 *
 * `over()` answers "has the wait ended". `proof()` answers "is `over()` reading anything at all" —
 * the failure a probe cannot catch about itself, since a read that has lost its file reports
 * "not yet" for the same reason a real wait does.
 */
const WAITS = [
  // Empty since SH948: every line that waited on a release has had its wait read and closed. An
  // entry is added with the `DS VDS<n>` dep that makes the roadmap line wait, and removed with it.
]

/** The waits this file can answer for itself. An entry with no probe says why, and is checked below. */
const PROBED = WAITS.filter((wait) => wait.over !== null)

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

for (const wait of PROBED) {
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
  for (const wait of PROBED) {
    assert.equal(wait.proof(), true,
      `SH937: ${wait.id}'s probe is not reading what it thinks it is — ${wait.proofIs}.`
        + " Until that holds, this entry's 'still waiting' result means nothing: it would report"
        + " a wait that has been over for months exactly as it reports a real one.")
  }
})

test("SH984: every entry either probes the package or says why it cannot", () => {
  /*
   * The list is the inventory of waits, and an inventory with a silent hole is worse than none
   * (SH142). SH984 filed seven lines that had no entry at all; one of the seven — SH948 — turned
   * out to be waiting on something that never reaches the published package, since `use:local`
   * runs from the design system's own checkout. Two ways to handle that, and only one of them is
   * honest: invent a probe that can never fire and let the entry read like the others, or carry
   * the entry with its probe declared absent and its reason stated. A wait with no entry is
   * invisible; a wait whose entry lies is worse, because it is trusted.
   */
  for (const wait of WAITS) {
    assert.ok(wait.instruction,
      `SH984: ${wait.id} has no instruction, which is the only part of an entry a reader acts on`)
    if (wait.over === null) {
      assert.ok(wait.unprobeable,
        `SH984: ${wait.id} carries no probe and no reason for having none. An entry may decline to`
          + " probe — it still ends with a release, and the version pin catches that — but the"
          + " declining has to be readable, or the next reader writes a probe that cannot fire.")
      assert.ok(!wait.proof,
        `SH984: ${wait.id} declares itself unprobeable and carries a proof, which is two answers to`
          + " one question")
      continue
    }
    assert.ok(wait.proof && wait.proofIs,
      `SH984: ${wait.id} probes the package with no proof that the probe reads anything`)
  }

  // And the list is the whole of what is waiting. Every roadmap line depending on a design-system
  // release carries `DS VDS<n>`, so the roadmap itself says how many entries there should be —
  // which is the read nobody made for seven lines in a row.
  const WAITING = /\*\*(SH\d+)\*\* \(deps: [^)]*DS VDS\d+/g
  const roadmap = readFileSync(join(CONSOLE_ROOT, "..", "docs", "ROADMAP.md"), "utf8")
  const waiting = [...roadmap.matchAll(WAITING)].map(([, id]) => id)
  // The read is proved on a line of the shape roadkeep writes, not on the backlog holding a wait:
  // an empty list is a state this file must allow, and requiring one made it red on the day it was
  // reached (SH948).
  assert.deepEqual(
    [..."- 📋 **SH1** (deps: SH2 ✅, DS VDS130) **a symptom** — a why. → §I.1".matchAll(WAITING)].map(([, id]) => id),
    ["SH1"],
    "SH984: the waiting-line read no longer matches the roadmap's line shape, so the comparison below proves nothing")
  assert.deepEqual(waiting.filter((id) => !WAITS.some((wait) => wait.id === id)), [],
    "SH984: these lines depend on a design-system release and have no entry here, so the release "
      + "that ends their wait will pass through unnoticed — which is the state SH937 exists to end. "
      + "Add one entry each: what it wants, a probe (or a stated reason there can be none), a proof "
      + "the probe reads something real, and the instruction to run when it fires.")
  assert.deepEqual(WAITS.map((wait) => wait.id).filter((id) => !waiting.includes(id)), [],
    "SH948: these entries wait for a line that no longer waits on a design-system release — it "
      + "shipped or lost its `DS VDS` dep. Delete the entry with the dep, or the list says something "
      + "is waiting that is not, which is how three shipped lines stayed here unnoticed.")
})
