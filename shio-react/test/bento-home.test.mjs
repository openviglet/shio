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
 * SH898 — the home page, and the survey that has to be able to see it.
 *
 * `surface-digest.mjs` once answered **"No surface moved (43 compared)"** across a whole slice of
 * work, and it was right about the directory it walked and useless about the one being changed.
 * So the question here is not only what the home composes but whether the survey reaches it.
 *
 * Digests are deliberately not asserted as literals: they hash the composed components and the
 * surface classes, so pinning one would fail on every edit to the page and teach the next reader to
 * update the number without reading it.
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

test("SH898: the migrated home composes the package's parts and no Card", () => {
  // SH1311 retired the tiles: a 390px frame per number gave an inventory count the weight of a
  // waiting review. What is owed leads as rows, and the counts and quick actions are one line each.
  for (const component of ["BentoHero", "BentoSection", "BentoEmptyState"]) {
    assert.ok(homeSource.includes(component),
      `SH898: the bento home no longer composes ${component}. §LV.1 asked for the package's parts`
        + " rather than Card, and that is the whole of what this task changed.")
  }
  for (const tile of ["<BentoCountTile", "<BentoTile"]) {
    assert.ok(!homeSource.includes(tile), `SH1311: the home draws ${tile.slice(1)} again`)
  }
  assert.ok(homeSource.indexOf('t("home.attention")') < homeSource.indexOf('t("home.overview")'),
    "SH1311: what waits for the curator comes after the inventory again")
  assert.equal((homeSource.match(/variant=\{index === 0 \? "default" : "outline"\}/g) ?? []).length, 1,
    "SH1311: the quick actions keep one filled button, New site")
  assert.doesNotMatch(homeSource, /@\/components\/ui\/card/,
    "SH898: the bento home imports the console Card. A page that renders both is a page where"
      + " half the surfaces re-skin and half do not.")
  assert.doesNotMatch(homeSource, /@\/components\/page-header/,
    "SH898: the bento home imports PageHeader, which is console chrome — the hero replaces it")
})

test("SH1318: the waiting section lists the runs and notes themselves, each a link to where it is resolved", () => {
  assert.match(homeSource, /queryKey: addressKey\("summary:notes"\),\s*queryFn: \(\{ signal \}\) => noteService\.open\(\{ signal \}\),\s*enabled: openNotes > 0,/,
    "SH1318: the inbox reads notes under its own key, or before the summary says there are any")
  assert.match(homeSource, /\.\.\.pendingRuns\.map\(/, "SH1318: a pending agent run is a count again, not an item")
  assert.match(homeSource, /\.\.\.shownNotes\.map\(/, "SH1318: an open note is a count again, not an item")
  assert.match(homeSource, /\{settledRuns\.map\(\(run\) =>/,
    "SH1318: a pending run is listed twice, in the inbox and again in the runs below")
  // Author, not modifier: measured live, a blueprint's writes carry no modifier, so "modified by
  // me" listed none of the drafts a curator's own run had made.
  assert.match(homeSource, /params: \{ state: "draft", author: user\?\.username, ids: true, limit: 100 \}/,
    "SH1318: the curator's own drafts are read by modifier again, which misses every blueprint-made page")
  assert.match(homeSource, /\.filter\(\(row\) => !row\.published\)/, "SH1318: a live page is listed as a waiting draft")
  assert.match(homeSource, /!isPending && inbox\.length === 0 && counts\.length === 0 && \(/,
    "SH1318: an empty inbox no longer says so, or says it before the summary has answered")
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

test("SH898: the activity rows mark a change, not a hardcoded hue", () => {
  // SH898 moved six hardcoded badge hues onto a tone per action; SH1211 moved that tone onto
  // BentoChangeMark, because since VDS182 a list's colour is a state or a change, not an area.
  assert.match(homeSource, /<BentoChangeMark state=\{changeStateOf\(/,
    "SH1211: the activity rows no longer draw BentoChangeMark through changeStateOf, the mapping the"
      + " bulk plan uses, so the two lists of changes can disagree about what a change looks like")
  assert.doesNotMatch(homeSource, /\bACTION_TONE\b/,
    "SH1211: a colour per activity action is back on the bento home")
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
