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
import {
  browserView, editableTitleField, listboxClass, readTableLayout, stepFor, tableLayoutKey, thumbnailSrc,
} from "../src/app/content/content-browser/browser-view.ts"

/**
 * SH1026 — three view buttons that highlighted when pressed and rendered the identical list.
 *
 * `viewMode` was read only to colour the button that set it. What has to hold now is that the
 * choice reaches the render, that each view is a renderer of its own inside the one listbox (so
 * selection and the keyboard are shared, not copied), and that the URL's word is read safely.
 */
const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src")
const read = (...path) => readFileSync(join(SRC, ...path), "utf8")

test("SH1026: the URL's view word is read as a view, and an unknown one is the list", () => {
  for (const view of ["list", "grid", "spreadsheet"]) assert.equal(browserView(view), view)
  assert.equal(browserView("kanban"), "list", "SH1026: an unknown view word drew nothing")
  assert.equal(browserView(""), "list")
})

test("SH1026: a grid steps across as well as down; the list and the table do not", () => {
  assert.equal(stepFor("grid", "ArrowRight"), 1)
  assert.equal(stepFor("grid", "ArrowLeft"), -1)
  assert.equal(stepFor("list", "ArrowRight"), 0,
    "SH1026: Right moved focus in a one-column list, which a screen reader does not announce")
  assert.equal(stepFor("spreadsheet", "ArrowDown"), 1)
  assert.notEqual(listboxClass("grid"), listboxClass("list"), "SH1026: the grid lays out as the list does")
})

test("SH1026: the chosen view reaches the render, through one renderer per view", () => {
  const page = read("app", "content", "object.children.page.tsx")
  assert.match(page, /<BrowserItem[\s\S]*?view=\{viewMode\}/,
    "SH1026: the browser's rows are not drawn by the chosen view — the buttons colour themselves again")
  assert.match(page, /className=\{listboxClass\(viewMode\)\}/, "SH1026: the listbox ignores the view")
  // One listbox for every view, so selection, roving focus and Shift-extend are one implementation.
  // Attributes only: the keyboard handler's comment names the role in backticks.
  assert.equal(page.match(/role="listbox"(?!`)/g)?.length, 1, "SH1026: a view grew a listbox of its own")
  const views = read("app", "content", "content-browser", "browser-views.tsx")
  for (const renderer of ["ListRow", "GridCard"]) {
    assert.match(views, new RegExp(`function ${renderer}\\(`), `SH1026: the ${renderer} renderer is gone`)
  }
  assert.match(views, /props\.view === "grid"/, "SH1026: BrowserItem does not dispatch on the view")
  assert.match(page, /viewMode === "spreadsheet" && sortedItems\.length > 0 && \(\s*<BrowserTable/,
    "SH1026: the table view is not the data table")
})

test("SH1026: the table is the design system's, with its layout kept and its selection reported", () => {
  const table = read("app", "content", "content-browser", "browser-table.tsx")
  assert.match(table, /<BentoDataTable/, "SH1026: the table view is hand-rolled again, so nothing virtualises it")
  assert.match(table, /layout=\{layout\}\s*\n\s*onLayoutChange=\{setLayout\}/,
    "SH1026: the column picker's choice is not kept, so every visit shows every column again")
  // The table makes rows selectable only when it has selection actions; without them the toolbar's
  // verbs would see an empty selection in this view.
  assert.match(table, /selectionActions=\{selectionActions\}/, "SH1026: the table's rows cannot be selected")
  assert.match(table, /rowActions=\{rowActions\}/, "SH1026: the table's rows lost the eight row actions")
  const page = read("app", "content", "object.children.page.tsx")
  assert.match(page, /onSelectionChange=\{\(ids\) => setSelectedItems\(new Set\(ids\)\)\}/,
    "SH1026: the table's selection never reaches the page, so its bulk verbs act on nothing")
})

test("SH1026: a table cell edits the title only where one plain-text field is the title", () => {
  assert.equal(editableTitleField([{ name: "headline", isTitle: true, widget: "Text" }, { name: "body" }]),
    "headline")
  assert.equal(editableTitleField([{ name: "first", isTitle: true }, { name: "last", isTitle: true }]),
    undefined, "SH1026: a title joined from two fields would be written half by one cell")
  assert.equal(editableTitleField([{ name: "title", isTitle: true, widget: "HTML Editor" }]), undefined,
    "SH1026: a rich-text title flattened by a plain input loses its markup")
  assert.equal(editableTitleField([]), undefined)
})

test("SH1026: an inline title edit reads the draft first and merges under its version", () => {
  const cell = read("app", "content", "content-browser", "title-cell.tsx")
  // The read is what makes the write conditional: the service keeps the ETag it answered with and
  // sends it as If-Match on the merge, so a write in between is refused rather than overwritten.
  assert.match(cell, /await postService\.get\(row\.id, "DRAFT"\)/u,
    "SH1026: the edit opens on the listing's value, so the merge carries no version and overwrites")
  assert.match(cell, /await postService\.patch\(row\.id, \{ \[editing\.field\]: editing\.value \}\)/u,
    "SH1026: the inline edit is not the merge verb, so it writes the whole state")
  assert.match(cell, /postService\.forgetVersion\(row\.id\)/u,
    "SH1026: a refused edit keeps the stale version, so the next try is refused the same way")
  const table = read("app", "content", "content-browser", "browser-table.tsx")
  assert.match(table, /cell: \(row\) => <TitleCell row=\{row\} onSaved=\{afterEdit\} \/>/u,
    "SH1026: the table's title column is not the editable cell")
})

test("SH1026: a stored layout is read defensively, per user", () => {
  assert.deepEqual(readTableLayout(null), { hidden: [] })
  assert.deepEqual(readTableLayout("null"), { hidden: [] },
    "SH1026: the literal \"null\" parses to null and crashed the read, the widgetSettings trap")
  assert.deepEqual(readTableLayout("{not json"), { hidden: [] })
  assert.deepEqual(readTableLayout('{"hidden":["locale",3,"state"]}'), { hidden: ["locale", "state"] })
  assert.notEqual(tableLayoutKey("ana"), tableLayoutKey("bruno"),
    "SH1026: two curators on one browser hide each other's columns")
})

test("SH1026: the view buttons are named and say which is pressed", () => {
  const page = read("app", "content", "object.children.page.tsx")
  for (const key of ["viewAsList", "viewAsGrid", "viewAsTable"]) {
    assert.match(page, new RegExp(`aria-label=\\{t\\("content\\.${key}"\\)\\} aria-pressed=`),
      `SH1026: the ${key} button is an unnamed icon again`)
  }
})

test("SH1026: a grid card shows the image the listing named, through the transform pipeline", () => {
  assert.equal(thumbnailSrc("/file_source/abc/hero%20image.png"),
    "/file_source/abc/hero%20image.png?w=320&h=192&crop=fill&format=webp",
    "SH1026: a card asks the pipeline for its own size, as the media library's thumbnails do")
  const views = read("app", "content", "content-browser", "browser-views.tsx")
  assert.match(views, /item\.thumbnail \? \(\s*<img src=\{thumbnailSrc\(item\.thumbnail\)\} alt="" loading="lazy"/,
    "SH1026: the card draws the kind's icon even when the listing names an image")
  assert.match(read("app", "content", "object.children.page.tsx"), /thumbnail: post\.thumbnail,/,
    "SH1026: the row mapping drops the thumbnail the listing sent")
})
