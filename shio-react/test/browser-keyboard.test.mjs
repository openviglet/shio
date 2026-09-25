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
const PAGE = read("src", "app", "content", "object.children.page.tsx")

/** Source with comments stripped, so a rule is never asserted against the prose explaining it. */
const code = (source) => source
  .replace(/\/\*[\s\S]*?\*\//gu, "")
  .replace(/^\s*\/\/.*$/gmu, "")

test("SH1020: the list is a listbox and every row is an option", () => {
  assert.match(PAGE, /role="listbox"/u)
  assert.match(PAGE, /aria-multiselectable="true"/u,
    "SH1020: the checkbox column has always been a multi-select — the semantics were what was "
      + "missing, not the behaviour")
  assert.match(PAGE, /role="option"/u)
  assert.match(PAGE, /aria-selected=\{selectedItems\.has\(item\.id\) \? "true" : "false"\}/u,
    "SH1020: a row that is selected and does not say so is selected for the pointer only")
  assert.match(PAGE, /tabIndex=\{index === focusedRow \? 0 : -1\}/u,
    "SH1020: a roving tabindex, so Tab moves past the list rather than through a hundred rows — "
      + "which is the contract `role=\"listbox\"` announces")
})

test("SH1020: no row action is reachable by pointer alone", () => {
  assert.doesNotMatch(code(PAGE), /hidden group-hover:flex/u,
    "SH1020: eight actions lived in that block. A pointer was the only way to see them, and a "
      + "screen reader was never told they existed.")
  assert.match(PAGE, /<BentoActionsMenu/u,
    "SH1020: the design system's menu, which is where the site list moved its four in its own sweep")
  // SH1059 — per row by `rowLabel`, the title plus its locale, since two translations share a title.
  assert.match(PAGE, /triggerLabel=\{t\("content\.rowActions", \{ name: rowLabel\(item\) \}\)\}/u,
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
  assert.match(handler, /setDeleteTarget\(item\)\s*\n\s*setDeleteDialogOpen\(true\)/u,
    "SH1020: Delete goes through the confirm flow, never straight to the server — it is one "
      + "keystroke away from every other key on this list")
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
