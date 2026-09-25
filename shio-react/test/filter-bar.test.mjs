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
import { contentFacets, fromBarValue, matchesLocally, toBarValue, withSiteFacet } from "../src/lib/filter-bar.ts"

/**
 * SH1024 — the filter bar speaks the list's URL, and nothing else.
 *
 * The bar owns no state (VDS139) and the list keeps its state in the address in the server's words
 * (SH1022, SH1023). So what matters is the translation: a URL read into the bar and written back
 * is the same URL, a removed chip clears its key, and a facet only offers values that exist.
 */
const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src")

test("SH1024: a URL read into the bar and written back is the same URL", () => {
  const url = { q: "pricing", site: "Blog", type: "Article", state: "draft", locale: "pt-br",
    modifiedSince: "2026-09-01", modifiedBefore: "2026-09-08" }
  assert.deepEqual(fromBarValue(toBarValue(url)), url,
    "SH1024: the bar changed the request it was handed, so a shared link no longer reloads the same view")
})

test("SH1024: a removed chip clears its key rather than leaving the filter on", () => {
  const bar = toBarValue({ type: "Article", state: "draft" })
  const next = fromBarValue({ ...bar, facets: { ...bar.facets, state: [] } })
  assert.equal(next.state, "", "SH1024: removing the state chip left state in the URL")
  assert.equal(next.type, "Article")
  assert.deepEqual(toBarValue({}).facets.modified, undefined, "SH1024: an empty range reads as a filter")
})

test("SH1024: facets offer only values that exist", () => {
  const words = { type: "Type", state: "State", locale: "Language", modified: "Changed", draft: "Draft", published: "Published" }
  const one = contentFacets([{ name: "Article", title: "Article" }], ["en", "en", ""], words)
  assert.deepEqual(one.map((facet) => facet.id), ["type", "state", "modified"],
    "SH1024: a single-language list offered a language filter, which can only ever empty it")
  const two = contentFacets([], ["en", "pt-br", "en"], words)
  assert.deepEqual(two.find((facet) => facet.id === "locale").options.map((option) => option.value), ["en", "pt-br"])
})

test("SH1024: the site facet is by name, first, and only where there is a choice", () => {
  const rest = [{ id: "state", kind: "choice", label: "State", options: [] }]
  assert.deepEqual(withSiteFacet(rest, [{ name: "Blog" }], "Site").map((facet) => facet.id), ["state"],
    "SH1024: one site offered as a filter, which can only ever return the whole list")
  const two = withSiteFacet(rest, [{ name: "Shop" }, { name: "Blog" }], "Site")
  assert.equal(two[0].id, "site")
  assert.deepEqual(two[0].options.map((option) => option.value), ["Blog", "Shop"],
    "SH1024: the site facet must say the name /agent/find takes, so a shared search is the agent's question")
})

test("SH1024: search draws the bar over its URL, and a filter alone is a search", () => {
  const page = readFileSync(join(SRC, "app", "content", "search.page.tsx"), "utf8")
  assert.match(page, /<BentoFilterBar[\s\S]*?value=\{barValue\}[\s\S]*?onChange=\{onBarChange\}/,
    "SH1024: search's filters are not the bar over its URL state")
  assert.match(page, /const barValue = useMemo\(\s*\(\) => toBarValue\(/,
    "SH1024: the bar's value is rebuilt every render, which restarts its debounce and drops the query")
  assert.match(page, /const onBarChange = useCallback\(/, "SH1024: the bar's handler is rebuilt every render")
  assert.doesNotMatch(page, /if \(!q\.trim\(\)\)/,
    "SH1024: search runs only on a typed term, so 'every draft on Blog' from the bar asks nothing")
})

test("SH1024: the trash filters what it holds by q and type, and draws the same bar", () => {
  const page = { title: "Pricing", type: "Article" }
  const folder = { title: "Pricing archive" }
  assert.ok(matchesLocally(page, "pric", ""), "SH1024: q is a case-insensitive substring of the title")
  assert.ok(matchesLocally(folder, "PRICING", ""))
  assert.ok(!matchesLocally(folder, "", "Article"),
    "SH1024: a folder has no type, so a type filter must leave it out rather than keep it")
  assert.ok(matchesLocally(page, "", "Article"))

  const trash = readFileSync(join(SRC, "app", "content", "trash.page.tsx"), "utf8")
  assert.match(trash, /<BentoFilterBar[\s\S]*?value=\{barValue\}[\s\S]*?onChange=\{onBarChange\}/,
    "SH1024: the trash has no filter bar")
  assert.match(trash, /const barValue = useMemo\(/, "SH1024: the bar's value is rebuilt every render")
  assert.match(trash, /content\.trashNoMatchHint/,
    "SH1024: a filter that matches nothing says the trash is empty, which is a different fact")
})

test("SH1024: the content browser draws the bar over its URL, and / still reaches its search", () => {
  const page = readFileSync(join(SRC, "app", "content", "object.children.page.tsx"), "utf8")
  assert.match(page, /<BentoFilterBar[\s\S]*?value=\{barValue\}[\s\S]*?onChange=\{onBarChange\}/,
    "SH1024: the browser's filters are not the bar over its URL state")
  // Found in a browser, not by reading: the bar debounces typing in an effect keyed on `value` and
  // `onChange`, and a fresh object per render restarted its timer, so the query was never sent.
  assert.match(page, /const barValue = useMemo\(\s*\(\) => toBarValue\(/,
    "SH1024: the bar's value is rebuilt every render, which restarts its debounce and drops the query")
  assert.match(page, /const onBarChange = useCallback\(/, "SH1024: the bar's handler is rebuilt every render")
  assert.match(page, /searchRef\.current\?\.querySelector\("input"\)\?\.focus\(\)/,
    "SH1020: `/` no longer focuses the list's search")
})
