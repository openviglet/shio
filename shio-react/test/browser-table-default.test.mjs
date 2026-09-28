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
