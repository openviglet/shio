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
import { createElement as h } from "react"
import { renderToStaticMarkup } from "react-dom/server"
import { StaticRouter } from "react-router-dom"
import { SectionCardChromeProvider } from "@viglet/viglet-design-system/bento"
import { survey } from "../scripts/surface-digest.mjs"
import { findElements } from "./support/jsx-elements.mjs"
import { stripComments } from "./support/source-text.mjs"

/**
 * SH900 — the content browser migrates by adapter, not by copy, and that is the finding.
 *
 * ## Why this surface took a different shape
 *
 * SH898 and SH899 migrated by **adding** a surface beside the console one. Right for a home page
 * of 154 lines and four site pages of 100–200. `object.children.page.tsx` is **965 lines** —
 * twenty state variables, three view modes, selection, pagination, copy/move — and exactly
 * **four** of them were chrome: a `PageHeader` and three `Card`s.
 *
 * Copying it would have duplicated 960 lines of the most defect-prone code in the console so that
 * four could differ, and every fix would then need landing twice until SH909. So the browser is
 * written once and a `SectionCardChromeProvider` above the route decides which chrome it renders
 * — the arrangement SH892 built the adapter for, extended to the page header by
 * `PageHero`.
 *
 * **The migration shape depends on the surface.** A small entity-shaped page is worth writing
 * twice, because what changes is its whole structure. A large working surface is not, because
 * what changes is its chrome.
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

const BROWSER = stripComments(
  readFileSync(join(SRC, "app", "console", "content", "object.children.page.tsx"), "utf8"))
const FOLDER_EDIT = stripComments(
  readFileSync(join(SRC, "app", "bento", "content", "folder.edit.page.tsx"), "utf8"))
const APP = stripComments(readFileSync(join(SRC, "App.tsx"), "utf8"))

test("SH900: the browser is one component with no console chrome of its own", () => {
  assert.doesNotMatch(BROWSER, /@\/components\/ui\/card/,
    "SH900: the content browser imports Card again. Its three panels go through"
      + " AdaptiveSectionCard so the same component renders console cards or frosted sections —"
      + " a raw Card is a panel that cannot follow the provider.")
  assert.doesNotMatch(BROWSER, /@\/components\/page-header/,
    "SH900: the content browser imports PageHeader directly, so its header cannot follow the"
      + " chrome. PageHero is the seam.")
  assert.match(BROWSER, /PageHero/, "SH900: the browser lost its adaptive header")

  /*
   * SH902 re-pointed this from `AdaptiveSectionCard` to `Panel`, and the correction is
   * worth more than the count. All three of these panels were written here with
   * `AdaptiveSectionCard` and **no header** — and the package's own branch is
   * `chrome === "bento" && header`, so a header-less one falls back to the console card in both
   * eras. The assertion below passed on every one of them while the bento route rendered three
   * console cards under the frosted shell: it counted the seam and never asked whether the seam
   * did anything. `checkBentoSurface` reading `backdrop-filter: null` is what found it.
   *
   * A panel is not a section. These three carry a count, a breadcrumb and a list of rows, with
   * the hero directly above naming the page; a header on any of them would be noise.
   */
  const panels = findElements(BROWSER, "Panel").filter((el) => !el.selfClosing)
  assert.ok(panels.length >= 3,
    `SH900/SH902: only ${panels.length} Panel(s) in the browser; the three panels — the`
      + " stats strip, the toolbar and the listing — are what the console chrome was.")
  assert.doesNotMatch(BROWSER, /<AdaptiveSectionCard\b/,
    "SH902: the browser mounts an AdaptiveSectionCard again. If it has no Header child it renders"
      + " a console card in bento chrome and nothing says so — use Panel, or give it a"
      + " header and mean it.")
})

test("SH900: the bento route wraps that same component in the provider", () => {
  assert.match(APP, /SectionCardChromeProvider chrome="bento"/,
    "SH900: nothing in App.tsx provides bento chrome, so the browser renders console cards under"
      + " the bento shell — the half-migrated look this block exists to end.")
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

test("SH900: the provider really does change what the same tree renders", async () => {
  // The claim the whole task rests on, exercised rather than described: one element tree, two
  // chromes. `chrome-adapter.test.mjs` proves it for the section; this proves the arrangement
  // the route actually uses.
  const { AdaptiveSectionCard } = await import("@viglet/viglet-design-system/bento")
  const tree = h(AdaptiveSectionCard, null,
    h(AdaptiveSectionCard.StaticHeader, { icon: () => null, title: "T" }),
    h(AdaptiveSectionCard.Content, null, h("span", null, "body")))
  const render = (chrome) => renderToStaticMarkup(
    h(StaticRouter, { location: "/bento/content" },
      h(SectionCardChromeProvider, { chrome }, tree)))

  assert.notEqual(render("console"), render("bento"),
    "SH900: the provider no longer changes the rendering, so mounting the browser under it"
      + " achieves nothing and the bento route shows console chrome.")
})

test("SH900: the console browser stays console-era, because the adapter is era-neutral", () => {
  const surfaces = survey()
  assert.equal(surfaces["console/content/object.children.page.tsx"]?.era, "console",
    "SH900: the browser changed era. It imports only era-neutral names from the bento subpath —"
      + " AdaptiveSectionCard — and PageHero keeps `BentoHero` on the components/ side"
      + " of the boundary precisely so both classifiers stay honest about the page.")
  for (const name of ["folder.edit", "folder.new"]) {
    assert.equal(surfaces[`bento/content/${name}.page.tsx`]?.era, "bento",
      `SH900: bento/content/${name}.page.tsx is missing or not bento-era`)
  }
})

test("SH900: the reader is not vacuous — it reads a real browser and a real survey", () => {
  assert.ok(BROWSER.length > 20_000,
    `SH900: object.children.page.tsx read as ${BROWSER.length} characters, which is not the 965-line`
      + " browser — every assertion above is checking a file that is not there")
  assert.ok(Object.keys(survey()).length > 40, "SH900: the survey is reading an empty tree")
})
