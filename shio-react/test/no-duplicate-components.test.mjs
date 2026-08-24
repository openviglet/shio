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
import { createRequire } from "node:module"
import { existsSync, mkdirSync, mkdtempSync, writeFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"

/**
 * SH894 — a component re-declared here fails this repository's CI.
 *
 * ## Why a lint, when the count was already zero
 *
 * §LIV.9's argument was that all 24 files under `components/ui` are one-line re-exports with
 * **zero forks**, reached by discipline alone, and that "discipline is what fails first under
 * time pressure" — with a large shared surface about to come within reach, the moment a bento
 * component needs one prop it does not have, the cheapest path is a local copy.
 *
 * The count was zero for `components/ui`. It was **seven** for `src`, which is what the gate
 * scans, and nobody had looked because nothing was looking:
 *
 * - `mode-toggle-sidebar.tsx` was a fork of `ModeToggleSidebar` sitting **beside its own shim**
 *   — `mode-toggle.tsx` already re-exported the shared one — with zero importers. Dead code that
 *   would have been imported by whoever found it first.
 * - `dialog.delete.tsx` was 91 lines that had drifted behind the shared dialog, rendered by
 *   eighteen pages, none of which could offer the "cannot delete, still in use" branch the
 *   shared one has.
 * - The other five are real product variants and now say so, each with its reason.
 *
 * So the instrument found what the reasoning predicted, one block earlier than predicted, which
 * is SH322's rule arriving on time for once: the grep is the deliverable.
 *
 * ## Why it runs here rather than as its own CI step
 *
 * `pnpm test` is one of this repository's three gates, and a check reachable only by a second
 * command is a check that runs when somebody remembers. Spawning the package's own script from
 * inside the suite means it runs on every push through `suites.yml`, gets the watchdog, and
 * reports beside the other readers. The script's stdout carries the import that replaces each
 * duplicate, so the assertion message *is* the fix — which is the whole reason the package emits
 * a manifest with specifiers per entry point rather than a list of names.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const CONSOLE_ROOT = join(HERE, "..")
const require_ = createRequire(join(CONSOLE_ROOT, "noop.js"))

/** Resolved through the package, so a release that moves the script is a failure here, not a skip. */
function gatePath() {
  const manifest = require_.resolve("@viglet/viglet-design-system/exports.json")
  return join(dirname(dirname(manifest)), "scripts", "check-duplicates.mjs")
}

function run(args, cwd = CONSOLE_ROOT) {
  return spawnSync(process.execPath, [gatePath(), ...args], { cwd, encoding: "utf8" })
}

/**
 * **Never pass a positional root to this gate.** Its argument parsing is
 *
 *   const manifestFlag = args.indexOf("--manifest")           // -1 when absent
 *   const roots = args.filter((a, i) => !a.startsWith("--") && i !== manifestFlag + 1)
 *
 * so with no `--manifest`, `manifestFlag + 1` is **0** and the filter discards argument zero —
 * the first positional root, always. `check-duplicates no-such-dir-at-all` scans `./src` and
 * reports success; `check-duplicates src app` scans only `app`. Measured, not read: a planted
 * duplicate in a temp directory passed as a root came back clean while the gate happily scanned
 * 181 files somewhere else.
 *
 * `check-duplicates src` masks it perfectly, because the argument it drops is the same directory
 * it then defaults to — which is why this was invisible until a test tried to point the gate
 * somewhere. Everything here therefore uses the **default** root and steers with `cwd`, which is
 * the one form that cannot be silently ignored.
 *
 * **Fixed upstream in VDS78**, with two tests that run without `--manifest` — the branch every
 * existing test avoided. This workaround stays until an installed release carries that fix:
 * the gate this suite runs is the one in `node_modules`, so the version here is what decides,
 * not the version in a checkout. When it lands, delete this constant and pass `src` the ordinary
 * way; SH919 is the open half that says so.
 */
const NO_POSITIONAL_ROOTS = []

test("SH894: no component here re-declares one the design system already exports", () => {
  const result = run(NO_POSITIONAL_ROOTS)

  assert.equal(result.status, 0,
    "SH894: a component declared in this console is already exported by the design system.\n\n"
      + `${result.stdout}${result.stderr}\n`
      + "Replace the local declaration with the import the report names — a one-line re-export\n"
      + "shim is the sanctioned pattern and this gate does not count it.\n\n"
      + "If the collision is deliberate — the local one genuinely does a different job — say so\n"
      + "in the file, with the reason:\n"
      + "  // viglet-ds-allow-duplicate <Name> -- why this one is not the shared one\n"
      + "The reason is the point. A gate with no way to say \"this one is mine\" is a gate\n"
      + "somebody deletes, and five of the seven findings this gate opened with were exactly\n"
      + "that case.")
})

test("SH894: the gate is reachable and reads a real manifest, not a fallback", () => {
  assert.ok(existsSync(gatePath()),
    `SH894: the duplicate gate is not at ${gatePath()}. A design-system release moved or dropped`
      + " it, and this reader would otherwise be reporting a spawn failure as a clean tree.")

  const manifest = require_("@viglet/viglet-design-system/exports.json")
  const names = Object.keys(manifest.exports ?? manifest ?? {})
  assert.ok(names.length > 0,
    "SH894: the exports manifest is empty, so every name in this console is unknown to the gate"
      + " and it would pass over a tree of nothing but duplicates")
})

test("SH894: the gate is not vacuous — a planted duplicate fails it", () => {
  // The one check that matters and the one nobody writes: a gate wired up wrong reports success
  // for the same reason a clean tree does. VDS5's own CI plants a duplicate; so does this, so the
  // proof does not depend on the design system's suite having run — or on the version installed
  // here behaving the way that suite's version did.
  //
  // Planted into a throwaway `src` and reached by running *in* that directory, never by naming it
  // as a root — see NO_POSITIONAL_ROOTS. The first draft of this test passed the directory as an
  // argument, the gate dropped it, scanned this console instead, found nothing, and the test
  // read that as "a planted duplicate does not fail the gate". Which was true, and for a reason
  // that had nothing to do with the gate's logic.
  const dir = mkdtempSync(join(tmpdir(), "sh894-"))
  const src = join(dir, "src")
  mkdirSync(src)
  const manifest = require_.resolve("@viglet/viglet-design-system/exports.json")
  const planted = join(src, "planted.tsx")

  writeFileSync(planted, "export function DialogDelete() { return null }\n")
  const failed = run(["--manifest", manifest], dir)

  assert.notEqual(failed.status, 0,
    "SH894: a file declaring DialogDelete did not fail the gate, so the passing run above proves"
      + " nothing about this console. Check that the scan reached the planted file — the report"
      + " prints how many it read — before concluding the manifest lost the name.")
  assert.match(`${failed.stdout}${failed.stderr}`, /DialogDelete/,
    "SH894: the gate failed without naming what it found, so its output cannot be the fix")
  assert.match(`${failed.stdout}${failed.stderr}`, /viglet-design-system\/router/,
    "SH894: the report no longer names the import that replaces the duplicate. That specifier is"
      + " the reason the package emits a manifest keyed by entry point instead of a list of"
      + " names, and without it the finding is a complaint rather than an instruction.")

  // And the exemption has to actually exempt, or the five deliberate variants in this console
  // would start failing a gate they have a written reason to fail — and somebody would delete
  // the gate rather than argue with it.
  writeFileSync(planted,
    "// viglet-ds-allow-duplicate DialogDelete -- planted by SH894's own test\n"
    + "export function DialogDelete() { return null }\n")
  assert.equal(run(["--manifest", manifest], dir).status, 0,
    "SH894: the allow-duplicate comment no longer exempts a name. app-footer, nav-main,"
      + " page-header and the two user-context exports all depend on it.")
})

/**
 * SH919 — the workaround's expiry date, expressed as an assertion.
 *
 * The gate this suite spawns is the one in `node_modules`, so what decides whether
 * `NO_POSITIONAL_ROOTS` is still necessary is the **installed** version — not the fix sitting in
 * a checkout, and not the changelog. VDS78 corrected the parse upstream; until a release carries
 * it here, passing a root would silently scan a different directory.
 *
 * So this asserts the bug is **still present**, which is a strange thing to assert on purpose and
 * is the point: it is the only shape that fails at the right moment. The day an installed release
 * carries VDS78 this goes red, and its message is the instruction to delete the workaround and
 * close SH919's open half. A comment saying "remove this later" expires silently; a test does not.
 */
test("SH919: the installed gate still drops a positional root, so the workaround is still needed", () => {
  const dir = mkdtempSync(join(tmpdir(), "sh919-"))
  const src = join(dir, "src")
  mkdirSync(src)
  // A duplicate in `src`, and a root argument pointing somewhere else entirely. A gate that reads
  // its argument finds nothing; a gate that drops it scans `./src` and finds the plant.
  mkdirSync(join(dir, "elsewhere"))
  writeFileSync(join(src, "planted.tsx"), "export function DialogDelete() { return null }\n")

  const manifest = require_.resolve("@viglet/viglet-design-system/exports.json")
  const named = run(["elsewhere", "--manifest", manifest], dir)

  // With `--manifest` present the parse is accidentally correct — the flag sits at index 1, so
  // `manifestFlag + 1` lands on the manifest path as intended. That is exactly why the upstream
  // suite never caught this. The failing shape is the one WITHOUT it.
  assert.equal(named.status, 0,
    "SH919: with --manifest the gate reads its root correctly and must not see a plant in ./src."
      + " If this failed, the installed gate's parse changed in a way this test does not model.")

  const bare = run(["elsewhere"], dir)
  const output = `${bare.stdout}${bare.stderr}`

  assert.match(output, /DialogDelete/,
    "SH919: **the installed duplicate gate now reads the root it is given.** That is VDS78 having"
      + " arrived, and it is good news — this test has done its job and is now the only thing"
      + " standing between you and a tidier suite.\n"
      + "  1. Delete NO_POSITIONAL_ROOTS above and pass `src` the ordinary way.\n"
      + "  2. Delete this test.\n"
      + "  3. Close SH919's open half: python .roadkeep/scripts/roadkeep.py ship SH919\n"
      + "  If instead the gate is failing for some other reason, check that it still resolves"
      + " from node_modules before assuming the fix landed.")
})
