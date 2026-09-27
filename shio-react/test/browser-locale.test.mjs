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
import { browserSource } from "./support/browser-source.mjs"

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
const PAGE = browserSource()
// SH1026 — the row's markup and its label moved beside the three view renderers.
const VIEW = read("src", "app", "content", "content-browser", "browser-view.ts")
const VIEWS = read("src", "app", "content", "content-browser", "browser-views.tsx")

/**
 * The browser's own `rowLabel`, run rather than read — the claim is about what it returns.
 *
 * SH1125 gave it a second argument (the already-translated word for "default"), so the extractor
 * takes both. Followed rather than loosened: a regex that stopped naming the signature would keep
 * passing for a function that had lost the parameter.
 */
function rowLabelFromSource() {
  const source = /function rowLabel\(item: BrowserRow, defaultWord\?: string\): string \{([\s\S]*?)\n\}/u
    .exec(VIEW)?.[1]
  assert.ok(source, "SH1059: rowLabel moved and this test lost its subject")
  return new Function("item", "defaultWord", source)
}

/** SH1125 — the same, for the rule that decides whether a row may be marked at all. */
function declaredDefaultFromSource() {
  const source = /export function declaredDefaultLocale\(\s*policy[^)]*\): string \| null \{([\s\S]*?)\n\}/u
    .exec(VIEW)?.[1]
  assert.ok(source, "SH1125: declaredDefaultLocale moved and this test lost its subject")
  return new Function("policy", source);
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
  assert.match(VIEWS, /aria-label=\{`Select \$\{rowLabel\(item, t\("content\.localeDefault"\)\)\}`\}/u,
    "SH1059: the checkbox — two 'Select Home' boxes are the ones a curator ticks the wrong one of")
  assert.match(VIEWS, /onClick=\{onOpen\} aria-label=\{rowLabel\(item, t\("content\.localeDefault"\)\)\}/u,
    "SH1059: the title opens the post, so it is the control whose name must say which one")
  assert.match(PAGE, /onOpen=\{\(\) => handleItemClick\(item\)\}/u, "SH1059: the title no longer opens the post")
  assert.match(VIEWS, /name: rowLabel\(item, t\("content\.localeDefault"\)\)/u,
    "SH1059: the row's actions trigger")
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

/*
 * SH1125 — which of two translations is the original.
 *
 * SH1059's design asked that the chip mark the site's default locale "where the site's enabled set
 * exists", and it did not exist then: the console could not read a site's policy until SH1056 gave it
 * `GET /api/v2/site/{id}/locales`. It could afterwards and the browser still did not ask — so a folder
 * holding a page in English and in Portuguese said which row was which and **not which one came
 * first**, which is the question a curator deleting one of them is actually asking.
 */
test("SH1125: with a declared default, that row's accessible name says so and the other's does not", () => {
  const rowLabel = rowLabelFromSource()
  const source = { id: "a", name: "Home", date: "", objectType: "POST", locale: "pt-br", isDefaultLocale: true }
  const translation = { id: "b", name: "Home", date: "", objectType: "POST", locale: "en" }

  assert.equal(rowLabel(source, "default"), "Home (pt-br, default)",
    "SH1125: the assertion the design names — the pt-br row says it is the default")
  assert.equal(rowLabel(translation, "default"), "Home (en)",
    "SH1125: and the en row does not, or the mark says nothing")
  assert.equal(rowLabel(source), "Home (pt-br)",
    "SH1125: with no word to use it stays SH1059's label rather than inventing an English one — the "
      + "word is passed in translated, because this function is pure and a hard-coded \"default\" "
      + "would be an English word in a Portuguese console")
})

test("SH1125: an UNdeclared policy marks nothing, which is SH1056's whole distinction", () => {
  const declaredDefaultLocale = declaredDefaultFromSource()

  assert.equal(declaredDefaultLocale({ defaultLocale: "pt-br", declared: true }), "pt-br")
  assert.equal(declaredDefaultLocale({ defaultLocale: "pt-br", declared: false }), null,
    "SH1125: a site that chose nothing still REPORTS a default — the locale its content happens to "
      + "be written in — and a chip saying \"default\" about that is exactly the confusion SH1056 "
      + "exists to remove. So a folder of two translations then says which language each is and "
      + "stays silent about which came first, which is the honest answer.")
  assert.equal(declaredDefaultLocale(null), null)
  assert.equal(declaredDefaultLocale({ declared: true }), null,
    "SH1125: declared with no locale is not a default")
  assert.equal(declaredDefaultLocale({ declared: true, defaultLocale: "  " }), null)
})

test("SH1125: the browser reads the policy once per site, through the shared query", () => {
  const LISTING = read("src", "app", "content", "content-browser", "use-browser-listing.ts")

  assert.match(LISTING, /const localePolicy = useSiteLocalePolicy\(siteId \?\? undefined\)/u,
    "SH1125: through the hook, so it is cached under the same key the query layer already uses for "
      + "the site and walking a tree inside one site asks once")
  assert.match(LISTING, /const defaultLocale = declaredDefaultLocale\(localePolicy\.data\)/u,
    "SH1125: and the declared-only rule is applied where the value is read, not at the chip")
  assert.match(LISTING, /isDefaultLocale: Boolean\(post\.locale && defaultLocale && post\.locale === defaultLocale\)/u,
    "SH1125: computed once per listing rather than at each of the four places a label is built")
})

test("SH1125: a sighted curator sees it too, and both locales have the words", () => {
  assert.match(VIEWS, /\{item\.isDefaultLocale && \(\s*<span/u,
    "SH1125: the chip carries the word, not only the accessible name — the curator comparing two "
      + "rows on screen is the one the question comes from")
  assert.match(VIEWS, /title=\{item\.isDefaultLocale\s*\?\s*t\("content\.localeChipDefault"/u,
    "SH1125: and its tooltip says what being the default means")
  for (const locale of ["en", "pt"]) {
    const strings = JSON.parse(read("src", "i18n", "locales", locale, "content.json"))
    assert.ok(strings["content.localeDefault"], `SH1125: ${locale} is missing content.localeDefault`)
    assert.ok(strings["content.localeChipDefault"]?.includes("{{locale}}"),
      `SH1125: ${locale} is missing content.localeChipDefault`)
  }
})

test("SH1125: the spreadsheet view names a translation the way the other two do", () => {
  const TABLE = read("src", "app", "content", "content-browser", "browser-table.tsx")

  assert.match(TABLE, /getRowLabel=\{\(row\) => rowLabel\(row, t\("content\.localeDefault"\)\)\}/u,
    "SH1125: `getRowLabel={rowLabel}` would leave the table as the one view where the mark is "
      + "missing, which is the half-migration this repository keeps finding")
})
