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

/**
 * SH1025 — the browser asks for a page of folders, and everything that relied on holding them
 * all was paid for in the same change.
 *
 * `ShConsoleListingBenchmarkTest` is the measurement and the server half's proof. This is the
 * half a Java test cannot reach, and it is not "does the fetch send `folderPage`" — it is the
 * **three behaviours that were correct because the folders came back whole**, each of which
 * becomes wrong the moment they do not:
 *
 * - **Select → Folders** selected what was on screen, which was all of them. On a page it would
 *   select fifty, and the delete dialog would say "50 item(s)" — the P7 surprise SH237 removed,
 *   coming back through the other half of the listing.
 * - **The move/copy dialog's blocked set** was the folders among the rows on screen, which was
 *   every folder that could be in the selection. A walked select-everything now puts ids in the
 *   selection that no row shows, and a missed one is a folder offered as its own destination.
 * - **The folder sort** ran here, which said something true about a complete set. Over a page it
 *   sorts one page and reads exactly like a correct answer.
 *
 * None of the three fails loudly, which is why they are asserted rather than remembered.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const PAGE = readFileSync(join(ROOT, "src", "app", "content", "object.children.page.tsx"), "utf8")

/** The source with whole-line comments dropped: this file's prose names every symbol it scans. */
const code = PAGE.split("\n").filter((line) => !/^\s*(\/\/|\*|\/\*)/u.test(line)).join("\n")

test("SH1025: the listing request asks for a page of folders", () => {
  assert.match(code, /folderPage: String\(page\),\s*\n\s*folderSize: String\(PAGE_SIZE\),/u,
    "SH1025: the browser opts in to folder paging. It is opt-in on the server so the move/copy "
      + "picker's whole-set read stays correct, which means this list has to ask.")
})

test("SH1025: both halves accumulate, and neither replaces the other", () => {
  assert.match(code, /folders: \[\.\.\.prev\.folders, \.\.\.\(res\.data\.folders \?\? \[\]\)\],/u,
    "SH1025: a page of folders that replaced the array would drop the rows already on screen and "
      + "read as a list that had lost its start")
  assert.match(code, /posts: \[\.\.\.prev\.posts, \.\.\.\(res\.data\.posts \?\? \[\]\)\],/u)
})

test("SH1025: the folder order is the server's now, not a local sort over a page", () => {
  assert.doesNotMatch(code, /if \(sortBy === "name"\) return a\.name\.localeCompare\(b\.name\)/u,
    "SH1025: sorting a page client-side sorts one page and reads exactly like a correct answer — "
      + "the half-migration SH165 named when it moved the post order to the server")
  assert.match(code, /const folderItems: ObjectChild\[\] = listData\.folders\.map/u,
    "SH1025: the rows are mapped in the order they arrived")
})

test("SH1025: Select → Folders walks the pages rather than taking what is on screen", () => {
  assert.match(code, /case "folder":\s*\n\s*setSelectedItems\(new Set\(await allMatchingFolderIds\(\)\)\)/u,
    "SH1025: \"everything\" has to BE everything (see SH237), now that the folder half is "
      + "paged too")
  assert.match(code, /while \(ids\.length < Math\.min\(listData\.totalFolders, SELECT_ALL_CAP\)\)/u,
    "SH1025: bounded by the same cap as the posts, so an unbounded folder is not pulled into the "
      + "browser to satisfy a menu item")
  assert.match(code, /t\("content\.selectAllCapped",\s*\n?\s*\{ loaded: ids\.length, total: listData\.totalFolders \}\)/u,
    "SH1025: and when it stops short it says so — the number in the dialog is still the number "
      + "that will be acted on")
})

test("SH1025: the move/copy dialog blocks every selected folder, seen or on screen", () => {
  assert.match(code,
    /setFolderDialogBlockedIds\(new Set\(ids\.filter\(\(id\) => knownFolderIds\.current\.has\(id\)\)\)\)/u,
    "SH1025: against every folder id this page has seen, not the rows it is showing. A selection "
      + "made by the select-everything walk (see SH237) holds ids no row shows, and a missed one "
      + "is a folder offered as a destination for itself — the recursion that dialog's own "
      + "guard exists to refuse (see SH203)")
  // The ref only helps if it is filled everywhere folders arrive: the first page, `load more`
  // and the walk. Three call sites, and a missed one is a silent hole in the blocked set.
  assert.equal((code.match(/^\s*rememberFolders\(/gmu) ?? []).length, 3,
    "SH1025: three call sites — the first page, load-more and the walk. A missed one is a hole "
      + "in the blocked set that shows up as a folder the dialog let somebody move into itself")
})

test("SH1025: the page boundary names both halves", () => {
  assert.match(code, /\{!loading && hasMoreRows && \(/u)
  assert.match(code, /loadedFolders, totalFolders: listData\.totalFolders,/u,
    "SH1025: a curator who cannot see their subfolder is owed the same sentence a curator who "
      + "cannot see their post has had since SH165")
  assert.match(code, /const folderCount = listData\.totalFolders/u,
    "SH1025: the toolbar counts what the folder holds, not what this page fetched — otherwise it "
      + "says fifty for every folder over fifty")

  for (const locale of ["en", "pt"]) {
    const strings = JSON.parse(
      readFileSync(join(ROOT, "src", "i18n", "locales", locale, "content.json"), "utf8"))
    assert.ok(strings["content.showingRows"], `SH1025: ${locale} is missing content.showingRows`)
  }
})

test("SH1025: the move/copy picker still reads the folders whole", () => {
  assert.match(code, /`\/v2\/object\/\$\{id\}\/list\?size=1`/u,
    "SH1025: no folder page, which is what keeps its walk complete — a dialog handed one page of "
      + "a site would stop above a destination that exists, and it has no load-more to notice with")
})
