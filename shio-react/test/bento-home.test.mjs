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
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { survey } from "../scripts/surface-digest.mjs"
import { stripComments } from "./support/source-text.mjs"

/**
 * SH898 — the console home, migrated, and the digest that can see it.
 *
 * ## What the first migration actually found
 *
 * Not a problem with the page. `surface-digest.mjs` answered **"No surface moved (43 compared)"**
 * for the whole of this task, and it was right: it walked `src/app/console`, nothing there had
 * changed, and the new page was in `src/app/bento`. The census had the same blind spot from the
 * same cause.
 *
 * That is what building the first surface is for. SH888 chose a parallel tree — the console root
 * wraps everything in a `SidebarProvider` no bento component can live under — so a migration
 * *adds* a surface rather than editing one, and every instrument that measured "the console
 * directory" was measuring the half that does not move. The digest now surveys both trees and
 * keys them under `src/app`, so `console/home/home.page.tsx` and `bento/home/home.page.tsx` are
 * two surfaces with two eras.
 *
 * ## The before and after §LV.1 asked for
 *
 * | surface | era | chrome |
 * |---|---|---|
 * | `console/home/home.page.tsx` | console | PageHeader, Card ×3 |
 * | `bento/home/home.page.tsx` | bento | BentoHero, BentoSection, BentoCountTile, BentoTile, BentoEmptyState |
 *
 * Digests are deliberately not asserted as literals: they are a hash of era, chrome and surface
 * classes, so pinning one here would fail on every edit to the page and teach the next reader to
 * update the number without reading it. What is asserted is what the digest is *for* — that the
 * two surfaces exist, that their eras differ, and that they do not hash to the same value.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")

const CONSOLE_HOME = "console/home/home.page.tsx"
const BENTO_HOME = "bento/home/home.page.tsx"

const homeSource = stripComments(
  readFileSync(join(SRC, "app", "bento", "home", "home.page.tsx"), "utf8"))

/*
 * SH910 — there is one home now, and the assertion is the other half of the same fact.
 *
 * While both trees were mounted this asserted that the survey saw two homes in two eras. The
 * console one is deleted, so the question becomes: does the survey still walk **both directories**
 * — and is the surviving home the bento one?
 *
 * The second half is not a formality. SH898's actual finding was a digest that walked only
 * `src/app/console` and therefore answered "No surface moved (43 compared)" across an entire
 * migration. A survey that quietly went back to one directory would now report the console home as
 * simply absent and pass, which is the same blindness wearing this task's clothes.
 */
test("SH898: the survey still walks both trees, and the one home left is the bento one", () => {
  const surfaces = survey()

  assert.equal(surfaces[CONSOLE_HOME], undefined,
    `SH898: ${CONSOLE_HOME} is back in the survey. SH910 deleted the console tree — a file there`
      + " again means somebody re-added a page to the era this migration retired.")
  assert.ok(surfaces[BENTO_HOME],
    `SH898: ${BENTO_HOME} is not in the survey. If the digest walked only src/app/console again,`
      + " every migration in Block AR would report 'No surface moved' — which is exactly what the"
      + " whole of SH898 reported before the survey learned about the second tree.")

  assert.equal(surfaces[BENTO_HOME].era, "bento",
    "SH898: the migrated home is not classified as bento. Era is decided by the bento subpath"
      + " import, so this means the page is composing something else.")

  // The survey must still reach the console directory, or "the console home is gone" is a claim
  // about a walk that stopped looking rather than about a file that stopped existing.
  const consoleSurfaces = Object.keys(surfaces).filter((name) => name.startsWith("console/"))
  assert.ok(consoleSurfaces.length > 15,
    `SH898: the survey found ${consoleSurfaces.length} console-tree surfaces. Twenty-three adapter`
      + " pages are still there, so a number near zero means the walk lost the directory and the"
      + " assertion above is vacuous.")
})

test("SH898: the migrated home composes the package's tiles and no Card", () => {
  for (const component of ["BentoHero", "BentoCountTile", "BentoTile", "BentoEmptyState"]) {
    assert.ok(homeSource.includes(component),
      `SH898: the bento home no longer composes ${component}. §LV.1 asked for the package's tiles`
        + " and count tiles rather than Card, and that is the whole of what this task changed.")
  }
  assert.doesNotMatch(homeSource, /@\/components\/ui\/card/,
    "SH898: the bento home imports the console Card. A page that renders both is a page where"
      + " half the surfaces re-skin and half do not.")
  assert.doesNotMatch(homeSource, /@\/components\/page-header/,
    "SH898: the bento home imports PageHeader, which is console chrome — the hero replaces it")
})

test("SH898: the counts wait rather than answering zero", () => {
  // "0 Sites" on the first screen after login is a real answer to the question and a wrong one.
  // The console page rendered `—` while loading; BentoCountTile takes a number, so the tiles are
  // withheld instead. Asserted because `?? 0` is the obvious edit and it reads as harmless.
  assert.match(homeSource, /siteCount !== null && postTypeCount !== null/,
    "SH898: the count tiles render before their counts have arrived. BentoCountTile takes a"
      + " number, so an un-guarded `?? 0` puts '0 Sites' on the first thing a curator sees after"
      + " login for as long as the request takes.")
})

test("SH898: the activity badges take a tone, not a hardcoded hue", () => {
  assert.match(homeSource, /bentoChipClass/,
    "SH898: the activity badges no longer use bentoChipClass. The console page spelled six colour"
      + " strings out — `border-blue-300 text-blue-600 dark:…` and five more — and a tone is what"
      + " lets SH890's re-key reach them.")
  assert.doesNotMatch(homeSource, /border-(blue|indigo|green|amber|red|teal)-\d/,
    "SH898: a hardcoded badge hue is back on the bento home")
})

test("SH898: the reader is not vacuous — it runs the real survey over a real tree", () => {
  const surfaces = survey()
  assert.ok(Object.keys(surfaces).length > 40,
    `SH898: the survey yielded ${Object.keys(surfaces).length} surfaces — it is reading an empty`
      + " directory, and every comparison above is passing over nothing")
  assert.ok(Object.keys(surfaces).some((name) => name.startsWith("console/")),
    "SH898: no console surface in the survey, so the keys are not relative to src/app")
  assert.ok(Object.keys(surfaces).some((name) => name.startsWith("bento/")),
    "SH898: no bento surface in the survey, which is the blind spot this task removed")
})
