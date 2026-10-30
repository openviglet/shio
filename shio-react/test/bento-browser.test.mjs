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
import { findElements } from "./support/jsx-elements.mjs"
import { stripComments } from "./support/source-text.mjs"
import { browserSource } from "./support/browser-source.mjs"

/**
 * SH900 — the content browser migrates by adapter, not by copy, and that is the finding.
 *
 * ## Why this surface took a different shape
 *
 * `object.children.page.tsx` is **965 lines** — twenty state variables, three view modes,
 * selection, pagination, copy/move — and its header and panels are four of them: `PageHero` and
 * three `Panel`s. A small entity-shaped page gets the package's own page shape; a large working
 * surface composes the seams, so its logic exists once.
 *
 * ## And a folder is nothing but identity
 *
 * `ShFolder` is `name` + `description`, both hero fields — so the editor's form body is empty,
 * which is precisely the shape that gave SH899 a dead Save button. `autosaveOnly` is the flag the
 * package already had for it: `showSaveSubmit = !(autosaveOnly && !isNew)`. Verified in a
 * browser: **0 save buttons, 0 forms**, and the hero's inline edit issues a `PUT` that survives
 * a reload.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")

/*
 * SH1145 — the browser is a directory. The page was 1,593 lines when these assertions were written
 * and is 592 now: the toolbar (with two of the three Panels), the listing's reads, the listbox and
 * four dialogs are modules beside it. A reader left on the page alone would go on passing while
 * asserting nothing about the code it names, which is the failure this file's own vacuity check
 * exists to catch — so the subject is the whole of it.
 */
const BROWSER = stripComments(browserSource())
const FOLDER_EDIT = stripComments(
  readFileSync(join(SRC, "app", "content", "folder.edit.page.tsx"), "utf8"))
const APP = stripComments(readFileSync(join(SRC, "App.tsx"), "utf8"))

test("SH900: the browser is one component, with no Card or PageHeader of its own", () => {
  assert.doesNotMatch(BROWSER, /@\/components\/ui\/card/,
    "SH900: the content browser imports Card again, a second card beside the Panels it renders.")
  assert.doesNotMatch(BROWSER, /@\/components\/page-header/,
    "SH900: the content browser imports PageHeader directly. PageHero is its header.")
  assert.match(BROWSER, /PageHero/, "SH900: the browser lost its adaptive header")

  /*
   * SH902 re-pointed this from `AdaptiveSectionCard` to `Panel`, and the correction is worth more
   * than the count. All three panels were written with `AdaptiveSectionCard` and **no header**,
   * which renders a plain card rather than the frosted section. The assertion passed on every one
   * of them while the route rendered three plain cards under the frosted shell: it counted the seam
   * and never asked whether the seam did anything. `checkBentoSurface` reading
   * `backdrop-filter: null` is what found it.
   *
   * A panel is not a section. These three carry a count, a breadcrumb and a list of rows, with
   * the hero directly above naming the page; a header on any of them would be noise.
   *
   * SH1307 — and then two of the three were not regions at all. The counts became the hero's
   * subtitle and the views joined the filter bar's row, because three framed strips above six rows
   * made a folder read as a toolbar. One panel is left, the listing, and it is exactly one: a second
   * frame above the rows is the band this task removed.
   */
  const panels = findElements(BROWSER, "Panel").filter((el) => !el.selfClosing)
  assert.equal(panels.length, 1,
    `SH900/SH1307: ${panels.length} Panel(s) in the browser; the listing is its one framed region,`
      + " and the counts and the controls sit unframed above it.")
  assert.doesNotMatch(BROWSER, /<AdaptiveSectionCard\b/,
    "SH902: the browser mounts an AdaptiveSectionCard again. Its regions are panels under a hero"
      + " that already names the page, not titled sections — use Panel (SH923).")
})

test("SH900: the bento route mounts that same component", () => {
  assert.match(APP, /<ObjectChildrenPage \/>/,
    "SH900: the bento route mounts something other than the console's own browser component."
      + " If it was copied after all, 960 lines now need every fix twice.")
})

test("SH900: a page whose entity is all identity renders no save button", () => {
  assert.match(FOLDER_EDIT, /autosaveOnly/,
    "SH900: the folder editor dropped `autosaveOnly`. A folder is name + description, both hero"
      + " fields, so its form body is empty — and a shell with a formId and no form is SH899's"
      + " dead Save button exactly. The package's own line is"
      + " `showSaveSubmit = !(autosaveOnly && !isNew)`.")
  assert.match(FOLDER_EDIT, /\{\(\) => null\}/,
    "SH900: the folder editor's render prop returns something. If it grew a form, it needs a"
      + " <form id> and `autosaveOnly` has to go — the two go together.")
})

test("SH900: the survey sees the browser's seams, and the two folder surfaces", () => {
  // SH960 — this asserted the browser stayed console-era. The digest has no era now; what it
  // records is what a page composes, which for this page is its seams.
  const surfaces = survey()
  const browser = surfaces["content/object.children.page.tsx"]
  assert.ok(browser?.chrome.includes("PageHero") && browser.chrome.includes("Panel"),
    "SH900: the survey records neither PageHero nor Panel on the content browser, so the digest is"
      + " blind to the header and the three panels that were the whole of its migration.")
  for (const name of ["folder.edit", "folder.new"]) {
    assert.ok(surfaces[`content/${name}.page.tsx`],
      `SH900: content/${name}.page.tsx is missing from the survey`)
  }
})

test("SH900: the reader is not vacuous — it reads a real browser and a real survey", () => {
  assert.ok(BROWSER.length > 20_000,
    `SH900: the browser read as ${BROWSER.length} characters, which is not the browser — every `
      + "assertion above is checking source that is not there. Since SH1145 that means the page "
      + "AND `content-browser/`, so a module dropped from the walk shows up here rather than as a "
      + "suite that quietly stopped checking.")
  assert.ok(Object.keys(survey()).length > 40, "SH900: the survey is reading an empty tree")
})
