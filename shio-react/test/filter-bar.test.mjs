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
import { contentFacets, countLocally, fromBarValue, matchesLocally, matchesRun, runSites, searchStateKeys, toBarValue, withCounts, withSiteFacet } from "../src/lib/filter-bar.ts"
import { viewHref } from "../src/lib/saved-view.ts"
import { browserSource } from "./support/browser-source.mjs"

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

test("SH1024: the review queue filters its runs by q and site, and draws the same bar", () => {
  const run = { session: "s-1", intent: "Refresh the pricing pages", agentName: "claude",
    actor: "ana", bySite: { Blog: 2, Shop: 1 } }
  const bare = { session: "s-2", intent: null, bySite: null }
  assert.ok(matchesRun(run, "PRICING", ""), "SH1024: q reads the run's premise, whatever its case")
  assert.ok(matchesRun(run, "claude", "") && matchesRun(run, "ana", ""),
    "SH1024: q reads the agent and who ran it, the other two names a curator knows a run by")
  assert.ok(matchesRun(bare, "s-2", ""), "SH1024: a run with no premise is still found by its id")
  assert.ok(matchesRun(run, "", "Shop") && !matchesRun(run, "", "Docs"),
    "SH1024: site keeps a run that touched it, and only those")
  assert.ok(!matchesRun(bare, "", "Blog"), "SH1024: a run that touched no site is not on any site")
  assert.deepEqual(runSites([run, bare, { session: "s-3", bySite: { Blog: 1 } }]).map((each) => each.name),
    ["Blog", "Shop"], "SH1024: the site facet offers the sites the listed runs touched, once each")

  const page = readFileSync(join(SRC, "app", "admin", "review", "review.list.page.tsx"), "utf8")
  assert.match(page, /<BentoFilterBar[\s\S]*?value=\{barValue\}[\s\S]*?onChange=\{onBarChange\}/,
    "SH1024: the review queue has no filter bar")
  assert.match(page, /const barValue = useMemo\(/, "SH1024: the bar's value is rebuilt every render")
  assert.match(page, /useListState\(REVIEW_LIST\)/, "SH1024: the queue's filters are not in its URL")
  assert.match(page, /review\.noMatchHint/,
    "SH1024: a filter that matches nothing says no agent has run, which is a different fact")
})

test("SH1024: the browser's facet options carry the server's counts, asked for with the first page", () => {
  const facets = [
    { id: "type", kind: "choice", label: "Type", options: [{ value: "Article", label: "Article" }, { value: "Page", label: "Page" }] },
    { id: "modified", kind: "date", label: "Changed" },
  ]
  const counted = withCounts(facets, { type: { Article: 12 } })
  assert.deepEqual(counted[0].options.map((option) => option.label), ["Article · 12"],
    "SH1185: a value the listing has none of leaves the menu — sixteen types at 0 around one at 13 is a menu nobody can read")
  assert.equal(counted[1], facets[1], "SH1024: a date facet has no counts and is passed through untouched")
  assert.deepEqual(withCounts(facets, undefined), facets)
  const chosen = { query: "", facets: { type: ["Page"] } }
  assert.deepEqual(withCounts(facets, { type: { Article: 12 } }, chosen)[0].options.map((option) => option.label),
    ["Article · 12", "Page · 0"], "SH1185: the chosen value stays at 0, so it can still be seen and cleared")
  assert.deepEqual(withCounts(facets, { type: {} }).map((facet) => facet.id), ["modified"],
    "SH1185: a choice facet with nothing to offer leaves the bar")
  for (const page of [["app", "content", "object.children.page.tsx"], ["app", "content", "search.page.tsx"],
    ["app", "content", "trash.page.tsx"], ["app", "admin", "review", "review.list.page.tsx"]]) {
    assert.match(readFileSync(join(SRC, ...page), "utf8"), /, barValue\)/,
      `SH1185: ${page.at(-1)} counts its facets without saying what is chosen, so a chosen 0 would vanish`)
  }

  const page = browserSource()
  assert.match(page, /if \(counted\) params\.set\("facets", "true"\)/,
    "SH1024: the browser never asks for the counts, so its menus offer values with no idea how many")
  assert.match(page, /counted = page === 0/, "SH1024: the counts come with the first page, not with every page")
  assert.match(page, /fetchPage\(listData\.page \?\? 0, undefined, true\)/,
    "SH1024: a re-read after a write keeps the counts from before the write")
  assert.match(page, /facets: prev\.facets/, "SH1024: loading the next page drops the counts")
})

test("SH1024: the lists that hold their rows count them, and a row counts once per value", () => {
  const runs = [{ bySite: { Blog: 2, Shop: 1 } }, { bySite: { Blog: 1 } }, { bySite: null }]
  assert.deepEqual(countLocally(runs, (run) => Object.keys(run.bySite ?? {})), { Blog: 2, Shop: 1 },
    "SH1024: a site's count is the runs that touched it, not the changes they made there")
  assert.deepEqual(countLocally([{ s: "draft", live: true }], (row) => [row.s, row.live ? "published" : null, "draft"]),
    { draft: 1, published: 1 }, "SH1024: a row carrying a value twice is one row, and a draft that is live is both")
  for (const page of [["app", "content", "trash.page.tsx"], ["app", "content", "search.page.tsx"],
    ["app", "admin", "review", "review.list.page.tsx"]]) {
    const source = readFileSync(join(SRC, ...page), "utf8")
    assert.ok(source.includes("withCounts(") && source.includes("countLocally("),
      `SH1024: ${page.at(-1)} offers facets with no idea how many each would list`)
  }
})

test("SH1024: every bar offers saved views, and a view opens the URL it saved", () => {
  assert.equal(viewHref({ path: "/console/content/search", query: "state=draft" }),
    "/console/content/search?state=draft")
  assert.equal(viewHref({ path: "/console/content/search", query: "?state=draft" }),
    "/console/content/search?state=draft", "SH1024: a query stored with its ? must not open as ??")
  assert.equal(viewHref({ path: "/console/content/trash", query: "" }), "/console/content/trash",
    "SH1024: the unfiltered list is a view too, and opens with no dangling ?")

  for (const [page, list] of [
    [["app", "content", "object.children.page.tsx"], "browser"],
    [["app", "content", "search.page.tsx"], "search"],
    [["app", "content", "trash.page.tsx"], "trash"],
    [["app", "admin", "review", "review.list.page.tsx"], "review"],
  ]) {
    assert.match(readFileSync(join(SRC, ...page), "utf8"), new RegExp(`views=\\{<SavedViews list="${list}" />\\}`),
      `SH1024: the ${list} bar offers no saved views, so a filtered ${list} list cannot be kept by name`)
  }
  const component = readFileSync(join(SRC, "components", "saved-views.tsx"), "utf8")
  assert.match(component, /service\.save\(list, name, location\.pathname, location\.search, shared\)/,
    "SH1024: a view saves the address as it stands, which is the whole state of the list (SH1023)")
})

test("SH1024: the content browser draws the bar over its URL, and / still reaches its search", () => {
  const page = browserSource()
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

test("SH1240: search counts a draft of a live page under Draft and Published, by the server's own field", () => {
  // Shaped as ShAgentFound.Row sends it (SH545): draft-preferred, with `published` when it is live.
  const rows = [
    { address: "post:Blog/a", state: "DRAFT", published: true },
    { address: "post:Blog/b", state: "DRAFT" },
    { address: "post:Blog/c", state: "PUBLISHED", published: true },
  ]
  assert.deepEqual(countLocally(rows, searchStateKeys), { draft: 2, published: 2 },
    "SH1240: the Published chip left out a live page with a pending draft, reading a field the server never sends")
  const search = readFileSync(join(SRC, "app", "content", "search.page.tsx"), "utf8")
  assert.match(search, /state: countLocally\(results, searchStateKeys\)/)
  assert.doesNotMatch(readFileSync(join(SRC, "services", "search", "search.service.ts"), "utf8"), /\blive\?:/,
    "SH1240: the search row declares `live`, which the server does not send")
})
