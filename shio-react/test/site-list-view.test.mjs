/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"

import {
  GRID_UP_TO, defaultSitesView, initialsOf, readSitesView, sitesViewKey,
} from "../src/lib/site-list-view.ts"
import { stripComments } from "./support/source-text.mjs"

/**
 * SH1220 — the sites list as a grid of home pages, the default below thirty sites, and a reader's
 * choice that survives a reload.
 */
const PAGE = stripComments(readFileSync(join(dirname(fileURLToPath(import.meta.url)), "..", "src",
  "app", "content", "site.list.page.tsx"), "utf8"))

test("SH1220: the default follows the count, a grid up to thirty and rows past it", () => {
  assert.equal(GRID_UP_TO, 30)
  assert.equal(defaultSitesView(0), "grid")
  assert.equal(defaultSitesView(30), "grid")
  assert.equal(defaultSitesView(31), "list",
    "SH1220: past thirty the reader is comparing, not picking, and rows are the shape for that")
})

test("SH1220: a reader's choice is kept per person, and a stored value that is not one is ignored", () => {
  assert.notEqual(sitesViewKey("alice"), sitesViewKey("bob"),
    "SH1220: one curator's view must not become another's")
  assert.equal(readSitesView("grid"), "grid")
  assert.equal(readSitesView("list"), "list")
  assert.equal(readSitesView("tiles"), undefined)
  assert.equal(readSitesView(null), undefined)
})

test("SH1220: both views render from the one listing read, and the choice is controlled", () => {
  assert.match(PAGE, /renderTile=\{\(site: ShSite\) => <SiteCard site=\{site\} actions=\{cardActions\(site\)\} \/>\}/,
    "SH1220: the sites list passes no card renderer, so the package draws rows only")
  assert.match(PAGE, /const view = chosenView \?\? defaultSitesView\(sites\?\.length \?\? 0\)/)
  assert.match(PAGE, /view=\{view\}\s+onViewChange=\{chooseView\}/)
  assert.equal((PAGE.match(/useSites\(\)/g) ?? []).length, 1, "SH1220: one data read for both views")
})

test("SH1220: a site with no picture shows its initials and says so", () => {
  assert.match(PAGE, /t\("content\.noHomePageYet"\)/)
  assert.equal(initialsOf("Cafeteria"), "CA")
  assert.equal(initialsOf("viglet blog"), "VB")
  assert.equal(initialsOf("my-site"), "MS")
})
