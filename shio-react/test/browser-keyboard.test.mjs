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
import { stripComments } from "./support/source-text.mjs"
import { browserSource } from "./support/browser-source.mjs"

/**
 * SH1020 — a list a curator can operate without a mouse.
 *
 * Every claim here is invisible on a screenshot: the list looked identical before and a keyboard
 * user could open a post and do nothing else to it. Eight row actions lived inside a block that was
 * `hidden` until a pointer hovered the row, which is unreachable without one and announced to a
 * screen reader as nothing at all.
 *
 * @scans code
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const read = (...path) => readFileSync(join(ROOT, ...path), "utf8")
const PAGE = browserSource()
/** SH1026 — each row's markup, now drawn by one renderer per view. */
const VIEWS = read("src", "app", "content", "content-browser", "browser-views.tsx")

/** Source with comments stripped, so a rule is never asserted against the prose explaining it. */
// SH1118 — the shared helper, not a local regex. The two-line version this replaced opened a
// block comment on a `/*` inside a string literal and deleted the rest of the file, so a scan for
// an absence passed by seeing nothing at all.
const code = (source) => stripComments(source)

test("SH1020: the list is a multi-select grid and every row is a selectable row", () => {
  // SH1054 — a grid and not a listbox: an option may not contain controls and every row carries
  // three, which axe measured as a serious nested-interactive on every folder. The row's controls
  // live in its gridcell, which the grid pattern allows.
  assert.match(PAGE, /role="grid"/u)
  assert.doesNotMatch(code(PAGE), /role="(listbox|option)"/u,
    "SH1054: a listbox is back, and its options would hold controls again")
  assert.match(PAGE, /aria-multiselectable="true"/u,
    "SH1020: the checkbox column has always been a multi-select — the semantics were what was "
      + "missing, not the behaviour")
  assert.match(PAGE, /role="row"/u)
  assert.match(PAGE, /role="gridcell"/u, "SH1054: a row's controls sit in its gridcell")
  assert.match(PAGE, /aria-selected=\{selectedItems\.has\(item\.id\) \? "true" : "false"\}/u,
    "SH1020: a row that is selected and does not say so is selected for the pointer only")
  // SH1026 — the tab stop is the focused row, or the window's first row when it has scrolled away.
  assert.match(PAGE, /tabIndex=\{index === tabStop \? 0 : -1\}/u,
    "SH1020: a roving tabindex, so Tab moves past the list rather than through a hundred rows — "
      + "which is the contract `role=\"listbox\"` announces")
})

test("SH1020: no row action is reachable by pointer alone", () => {
  for (const source of [PAGE, VIEWS]) {
    assert.doesNotMatch(code(source), /hidden group-hover:flex/u,
      "SH1020: eight actions lived in that block. A pointer was the only way to see them, and a "
        + "screen reader was never told they existed.")
  }
  assert.match(VIEWS, /<BentoActionsMenu/u,
    "SH1020: the design system's menu, which is where the site list moved its four in its own sweep")
  // SH1026 — every view draws it: the list row and the card render <Actions>; the table's rows get
  // the same eight through BentoDataTable's rowActions, asserted in browser-views.test.mjs.
  assert.equal(VIEWS.match(/<Actions item=\{item\} actions=\{actions\} \/>/gu)?.length, 2,
    "SH1026: a view dropped the row's actions, so there they are reachable by nothing")
  // SH1059 — per row by `rowLabel`, the title plus its locale, since two translations share a title.
  // SH1125 gave `rowLabel` the word for "default" as a second argument, so the name it builds now says
  // which of two translations is the original as well as which language each is. Followed rather than
  // loosened: a regex that stopped naming the call would keep passing for a trigger that had lost it.
  assert.match(
    VIEWS,
    /triggerLabel=\{t\("content\.rowActions", \{ name: rowLabel\(item, t\("content\.localeDefault"\)\) \}\)\}/u,
    "SH1020: named per row, or a screen reader hears eight identical 'Actions' buttons")
})

test("SH1020: arrows move, Space selects, Shift extends, Enter opens, Delete confirms", () => {
  const handler = /const onListKeyDown[\s\S]*?\n  \}\n/u.exec(PAGE)?.[0] ?? ""
  assert.ok(handler.length > 0, "SH1020: the key handler moved and this scan lost its subject")

  for (const key of ["ArrowDown", "ArrowUp", "Home", "End", "Enter", "Delete"]) {
    assert.ok(handler.includes(`"${key}"`), `SH1020: ${key} has no binding`)
  }
  assert.match(handler, /case " ":/u, "SH1020: Space toggles selection")
  assert.match(handler, /if \(event\.shiftKey\) selectRange\(anchorRow, next\)/u,
    "SH1020: Shift extends from the anchor — where the selection last started, which is the rule "
      + "every file manager uses and the reason the anchor is state rather than the previous row")
  // SH1145 — the listbox is a hook now, so the key calls `onDelete` and the page decides what that
  // is. Both halves are asserted: a hook that called the server directly, or a page that wired the
  // key to anything but the confirm flow, is the defect this has always been about.
  assert.match(handler, /if \(item\) onDelete\(item\)/u,
    "SH1020: Delete goes through the handler the page supplies, never straight to the server")
  assert.match(browserSource(),
    /onDelete: \(item\) => \{ setDeleteTarget\(item as ObjectChild\); setDeleteDialogOpen\(true\) \}/u,
    "SH1020: and what the page supplies is the confirm flow — it is one keystroke away from every "
      + "other key on this list")
})

test("SH1020: Shift-click extends the same range Shift-arrow does", () => {
  assert.match(PAGE, /const onRowClick = \(event: React\.MouseEvent<HTMLDivElement>, index: number\) => \{/u)
  assert.match(PAGE, /if \(event\.shiftKey\) \{\s*event\.preventDefault\(\)\s*selectRange\(anchorRow, index\)/u,
    "SH1020: one rule for both gestures, or a curator learns two")
})

test("SH1020: slash focuses search, and never steals a keystroke from a field", () => {
  const binding = /const onSlash = \(event: KeyboardEvent\) => \{[\s\S]*?\n    \}/u.exec(PAGE)?.[0] ?? ""
  assert.ok(binding.length > 0, "SH1020: the slash binding moved and this scan lost its subject")

  assert.match(binding, /if \(editing\) return/u,
    "SH1020: otherwise typing a slash into a name jumps the cursor out of the box it was typed in")
  assert.match(binding, /active instanceof HTMLInputElement/u)
  assert.match(binding, /isContentEditable/u,
    "SH1020: the rich-text fields are contenteditable, and they are exactly where a slash gets typed")
  // SH1024 took the toggle away: the search is the filter bar's own text field, always on screen,
  // so the binding focuses it in place and there is nothing to open first.
  assert.match(binding, /searchRef\.current\?\.querySelector\("input"\)\?\.focus\(\)/u,
    "SH1020: slash no longer reaches the list's search field")
})

test("SH1020: every string the new surface asks for exists in both locales", () => {
  for (const key of ["content.browserList", "content.rowActions", "content.viewPage"]) {
    for (const locale of ["en", "pt"]) {
      const strings = JSON.parse(read("src", "i18n", "locales", locale, "content.json"))
      assert.ok(strings[key], `SH1020: ${locale} is missing ${key}`)
    }
  }
})
