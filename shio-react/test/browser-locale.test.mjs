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
 * SH1059 — two languages of one page are two different rows.
 *
 * The server sent `locale` on every post row and the browser's mapping kept id, title, date, type
 * and status and dropped it. A page and its translation — which the unique key permits by design —
 * rendered as two rows with the same title, date and type, and a curator deleting the one that
 * looked like a duplicate deleted a translation.
 *
 * @scans code
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const read = (...path) => readFileSync(join(ROOT, ...path), "utf8")
const PAGE = read("src", "app", "content", "object.children.page.tsx")
// SH1026 — the row's markup and its label moved beside the three view renderers.
const VIEW = read("src", "app", "content", "content-browser", "browser-view.ts")
const VIEWS = read("src", "app", "content", "content-browser", "browser-views.tsx")

/** The browser's own `rowLabel`, run rather than read — the claim is about what it returns. */
function rowLabelFromSource() {
  const source = /function rowLabel\(item: BrowserRow\): string \{([\s\S]*?)\n\}/u.exec(VIEW)?.[1]
  assert.ok(source, "SH1059: rowLabel moved and this test lost its subject")
  return new Function("item", source)
}

test("SH1059: the mapping keeps the locale the server sends", () => {
  const mapping = /const postItems: ObjectChild\[\] = listData\.posts\.map\(\(post\) => \(\{([\s\S]*?)\}\)\)/u
    .exec(PAGE)?.[1] ?? ""
  assert.ok(mapping.length > 0, "SH1059: the post mapping moved and this scan lost its subject")
  assert.match(mapping, /locale: post\.locale/u,
    "SH1059: the row type declared locale and the mapping dropped it, so two translations rendered "
      + "as two identical rows")
})

test("SH1059: a page and its translation have different accessible names", () => {
  const rowLabel = rowLabelFromSource()
  const page = { id: "a", name: "Home", date: "", objectType: "POST", locale: "en" }
  const translation = { id: "b", name: "Home", date: "", objectType: "POST", locale: "pt-br" }

  assert.notEqual(rowLabel(page), rowLabel(translation),
    "SH1059: same title, same date, same type — the locale is the only thing that tells them apart")
  assert.equal(rowLabel(translation), "Home (pt-br)")
  assert.equal(rowLabel({ id: "f", name: "Docs", date: "", objectType: "FOLDER" }), "Docs",
    "SH1059: a folder has no locale, and its name must not grow an empty pair of brackets")
})

test("SH1059: every name the row gives a screen reader is that label", () => {
  assert.match(VIEWS, /aria-label=\{`Select \$\{rowLabel\(item\)\}`\}/u,
    "SH1059: the checkbox — two 'Select Home' boxes are the ones a curator ticks the wrong one of")
  assert.match(VIEWS, /onClick=\{onOpen\} aria-label=\{rowLabel\(item\)\}/u,
    "SH1059: the title opens the post, so it is the control whose name must say which one")
  assert.match(PAGE, /onOpen=\{\(\) => handleItemClick\(item\)\}/u, "SH1059: the title no longer opens the post")
  assert.match(VIEWS, /name: rowLabel\(item\)/u, "SH1059: the row's actions trigger")
  assert.match(VIEWS, /\{item\.locale && \(\s*<Chip/u,
    "SH1059: and a sighted curator sees it, as a chip beside the type both rows share")
})

test("SH1059: the chip's tooltip exists in both locales", () => {
  for (const locale of ["en", "pt"]) {
    const strings = JSON.parse(read("src", "i18n", "locales", locale, "content.json"))
    assert.ok(strings["content.localeChip"]?.includes("{{locale}}"),
      `SH1059: ${locale} is missing content.localeChip`)
  }
})
