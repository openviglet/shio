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

const HERE = dirname(fileURLToPath(import.meta.url))
const CONSOLE_ROOT = join(HERE, "..")

/**
 * SH915. Block AQ's twelve tasks and Block AR's fourteen all begin with one import —
 * `@viglet/viglet-design-system/bento` — and for the whole time they sat in the roadmap that
 * import did not resolve. Not because the layer was unwritten: VDS8 shipped it, and the design
 * system's own checkout declares the subpath. The copy installed here was **2026.3.2 from npm**,
 * which declares no such export, so `require.resolve` answered `ERR_PACKAGE_PATH_NOT_EXPORTED`.
 *
 * Two things made that expensive out of proportion to its size.
 *
 * The first is that the roadmap named the wrong blocker. Nine tasks were filed against
 * design-system ids, three of which had already shipped, so `pick` reported "blocked" and gave a
 * reason that had stopped being true — while the real blocker, a release, was written down
 * nowhere. Twice in one session the conclusion "this needs a publish from the user" was reported
 * without re-reading npm, and by then **2026.3.4 was published**. A blocker is a claim with a
 * timestamp on it.
 *
 * The second is the failure's shape. `^2026.3.2` *permits* 2026.3.2, so a range that looks
 * satisfied does not make the subpath present: a fresh `pnpm install` could resolve a version
 * without `bento` and the first line of the first migrated page would stop compiling. Worse, the
 * escape hatch inverts it — pushing a local `dist` over `node_modules` makes the import work on
 * the machine that did it and nowhere else, so the green run and the red run are on different
 * disks and CI is what finds out.
 *
 * So this asserts the thing itself rather than the version: every subpath the console depends on
 * **resolves**, from the console's own directory, through whatever pnpm actually laid down.
 */

/**
 * The subpaths the console imports, or is about to. `bento` and `bento.css` are Block AQ's whole
 * premise; the rest are what it already uses, included so this reader would notice a design-system
 * release that dropped one — the same class of break, arriving from the other direction.
 */
const REQUIRED = [
  "@viglet/viglet-design-system/bento",
  "@viglet/viglet-design-system/bento.css",
  "@viglet/viglet-design-system/preset",
  "@viglet/viglet-design-system/styles",
  "@viglet/viglet-design-system/i18n",
]

const require_ = createRequire(join(CONSOLE_ROOT, "noop.js"))

function resolves(specifier) {
  try {
    require_.resolve(specifier)
    return null
  } catch (error) {
    return error.code ?? String(error)
  }
}

test("SH915: every design-system subpath the console imports actually resolves", () => {
  const broken = REQUIRED.map((specifier) => ({ specifier, code: resolves(specifier) }))
    .filter((entry) => entry.code !== null)

  assert.deepEqual(broken, [],
    "SH915: a subpath the console imports does not resolve from the installed package.\n"
    + "  ERR_PACKAGE_PATH_NOT_EXPORTED means the installed version predates the subpath — check\n"
    + "  `npm view @viglet/viglet-design-system version` before concluding anything is unreleased,\n"
    + "  because that is the check twice skipped when this defect was reported as needing a publish.\n"
    + "  Raise the floor in shio-react/package.json and `pnpm install`; do NOT copy a local dist\n"
    + "  over node_modules, which makes it work on one machine and leaves CI to find out.")
})

test("SH915: the declared floor is a version that has the subpaths, not merely one that allows them", () => {
  const manifest = JSON.parse(readFileSync(join(CONSOLE_ROOT, "package.json"), "utf8"))
  const range = manifest.dependencies["@viglet/viglet-design-system"]
  const floor = /(\d+)\.(\d+)\.(\d+)/.exec(range)

  assert.ok(floor, `SH915: could not read a floor out of ${range}`)
  const [, year, minor, patch] = floor.map(Number)

  // `bento` landed in 2026.3.3. A caret on .2 resolves .4 today and .2 on a fresh lockfile, so
  // the range being satisfiable is not the same as the subpath being there — which is the whole
  // defect, and the reason this asserts the floor rather than what happens to be installed.
  assert.ok(year > 2026 || minor > 3 || patch >= 3,
    `SH915: the floor is ${range}, which permits a version with no /bento — the console would then`
    + " fail to compile on a fresh install while resolving fine on any machine that had updated."
    + " Raise the floor to the release that carries the subpath.")
})

test("SH915: the reader is not vacuous — it would catch a subpath that is genuinely absent", () => {
  assert.equal(resolves("@viglet/viglet-design-system/bento"), null,
    "SH915: the control — bento must resolve, or the two tests above are passing over nothing")
  assert.equal(
    resolves("@viglet/viglet-design-system/a-subpath-that-was-never-published"),
    "ERR_PACKAGE_PATH_NOT_EXPORTED",
    "SH915: a missing subpath must report ERR_PACKAGE_PATH_NOT_EXPORTED — if this changes, the"
    + " message above is teaching the wrong diagnosis")
})
