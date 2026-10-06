/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1203 — the content browser opened on 80 px cards with a numbered circle, a link-blue title, an
 * English relative date ("about 1 month ago") and five pills each, and it said the folder's path a
 * third time in a toolbar panel. It now opens on its table, speaks the console's language for dates
 * and states, leaves the path to the hero's trail, and shows the folders around this one.
 */
import { strict as assert } from "node:assert"
import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { stripComments } from "./support/source-text.mjs"
import { browserView } from "../src/app/content/content-browser/browser-view.ts"
import { relativeDate } from "../src/lib/relative-date.ts"

const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src")
const read = (...parts) => stripComments(readFileSync(join(SRC, ...parts), "utf8"))

test("SH1203: a folder opens on its table", () => {
  assert.equal(browserView(""), "spreadsheet")
  assert.match(read("app", "content", "content-browser", "browser-listing.ts"), /view: "spreadsheet"/,
    "SH1203: the URL's default view is not the table, so a bare link opens the cards")
})

test("SH1203: a relative date is in the console's language", () => {
  const month = Date.now() - 32 * 24 * 3600 * 1000
  assert.match(relativeDate(month, "pt-BR"), /^há /, "SH1203: a Portuguese console printed an English date")
  assert.match(relativeDate(month, "en"), /ago$/)
  assert.equal(relativeDate(undefined, "pt-BR"), "")
  for (const file of [["app", "content", "content-browser", "browser-table.tsx"], ["app", "content", "content-browser", "browser-view.ts"],
    ["app", "admin", "review", "review.list.page.tsx"]]) {
    assert.doesNotMatch(read(...file), /formatDistanceToNow\(/, `SH1203: ${file.at(-1)} formats a date with no locale`)
  }
})

test("SH1203: the table's state is a word in the console's language, not the server's DRAFT", () => {
  const table = read("app", "content", "content-browser", "browser-table.tsx")
  assert.match(table, /cell: \(row\) => <StateMark status=\{row\.publishStatus\} t=\{t\} \/>/)
  assert.match(table, /t\("content\.filterDraft"\)/)
})

test("SH1203: the path is said once, in the trail, and the folders around this one sit beside it", () => {
  const toolbar = read("app", "content", "content-browser", "browser-toolbar.tsx")
  assert.doesNotMatch(toolbar, /breadcrumbPath/, "SH1203: the toolbar draws the folder path again")
  const page = read("app", "content", "object.children.page.tsx")
  assert.match(page, /<BrowserTree path=\{breadcrumbPath\} folders=\{listData\.folders\} \/>/,
    "SH1203: the folder tree is not mounted beside the listing")
})

test("SH1223: the browser's table scrolls with the page, not inside a fixed box", () => {
  const table = read("app", "content", "content-browser", "browser-table.tsx")
  assert.match(table, /<BentoDataTable\b/)
  assert.doesNotMatch(table, /\bheight=\{/,
    "SH1223: a fixed height scrolls a long folder inside a box, with a second scrollbar in the busiest screen")
})

test("SH1239: nothing around the browser's table is a scroll container, so its bar and header can stick", () => {
  // `overflow-hidden` on the panel made it the sticky elements' scroll container, one that never
  // scrolls: the package's classes were in place and the bulk verbs still left the screen. Found by
  // e2e/selection-bar.check.mjs, which measures it on a live page.
  const page = read("app", "content", "object.children.page.tsx")
  // The panel that opens nearest above the table, not the first on the page (the stats strip).
  const panel = [...page.slice(0, page.indexOf("<BrowserTable")).matchAll(/<Panel\b[^>]*>/g)].at(-1)?.[0]
  assert.ok(panel, "SH1239: the content browser no longer wraps its table in a Panel; re-point this read")
  assert.doesNotMatch(panel, /\boverflow-(hidden|auto|scroll)\b/,
    "SH1239: the panel around the table clips by scrolling, which unsticks the selection bar and the header row")
})
