/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { existsSync, readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { DOMAINS, survey } from "../scripts/surface-digest.mjs"
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
 * keys them under `src/app`, so `console/home/home.page.tsx` and `system/home.page.tsx` are
 * two surfaces with two eras.
 *
 * ## The before and after §LV.1 asked for
 *
 * | surface | era | chrome |
 * |---|---|---|
 * | `console/home/home.page.tsx` | console | PageHeader, Card ×3 |
 * | `system/home.page.tsx` | bento | BentoHero, BentoSection, BentoCountTile, BentoTile, BentoEmptyState |
 *
 * Digests are deliberately not asserted as literals: they are a hash of era, chrome and surface
 * classes, so pinning one here would fail on every edit to the page and teach the next reader to
 * update the number without reading it. What is asserted is what the digest is *for* — that the
 * two surfaces exist, that their eras differ, and that they do not hash to the same value.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")

const HOME = "system/home.page.tsx"

const homeSource = stripComments(
  readFileSync(join(SRC, "app", "system", "home.page.tsx"), "utf8"))

/*
 * SH961 — one page tree by domain, and the survey has to walk every one of them.
 *
 * SH898's finding was a digest that walked only `src/app/console` and answered "No surface moved
 * (43 compared)" across an entire migration, because the pages being written were in a second
 * tree. SH910 turned this into "the survey still walks both trees"; SH961 merged the two into
 * `admin`, `content`, `editor` and `system`, so the same blindness now means a walk that lost one
 * domain — and a retired tree coming back is a page placed by the era it was migrated from.
 */
test("SH898: the survey walks every domain tree, and the home is in it", () => {
  const surfaces = survey()
  const names = Object.keys(surfaces)

  for (const retired of ["console", "bento"]) {
    assert.ok(!existsSync(join(SRC, "app", retired)),
      `SH961: src/app/${retired} exists again. Pages live in their domain tree (${DOMAINS.join(", ")});`
        + " a directory named after an era is where the next page gets filed by the wrong question.")
  }
  for (const domain of DOMAINS) {
    assert.ok(names.some((name) => name.startsWith(`${domain}/`)),
      `SH898: the survey found no surface under ${domain}/, so the walk lost a directory — the`
        + " blindness that reported 'No surface moved' across the whole of Block AR's first slice.")
  }

  assert.ok(surfaces[HOME], `SH898: ${HOME} is not in the survey`)
  // SH960 — asked of what the digest records now, since it has no era field.
  assert.ok(surfaces[HOME].chrome.includes("BentoHero"),
    "SH898: the survey records no BentoHero on the home, so either the page stopped composing the"
      + " package's hero or the digest's vocabulary lost it.")
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
  assert.ok(Object.keys(surfaces).every((name) => DOMAINS.some((d) => name.startsWith(`${d}/`))),
    "SH898: a survey key does not start with a domain, so the keys are not relative to src/app")
})
