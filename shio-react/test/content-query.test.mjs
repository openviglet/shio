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

import { CONTENT_FILTERS, activeFilterCount, filterParams } from "../src/lib/content-query.ts"

/**
 * SH1022 — the console's half of "one query vocabulary, four surfaces".
 *
 * The claim worth guarding is not that the popover renders. It is that **the filters travel**:
 * a client-side filter over a paged listing narrows one page, and the rows on that page were
 * chosen by a query that knew nothing about the filter — so the answer is wrong in a way nobody
 * can see until a folder outgrows a page. That is the half-migration §XIV.7 warns about and the
 * one SH165 already paid for once, with `sort`.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const PAGE = readFileSync(join(ROOT, "src", "app", "content", "object.children.page.tsx"), "utf8")
const QUERY_JAVA = join(ROOT, "..", "shio-app", "src", "main", "java", "com", "viglet", "shio",
  "api", "query", "ShContentQuery.java")

test("SH1022: every filter is sent to the server, none applied to the page in hand", () => {
  assert.match(PAGE, /for \(const \[key, value\] of Object\.entries\(filterParams\(filters\)\)\) params\.set\(key, value\)/u,
    "SH1022: the filters go into the request. Narrowing `listData.posts` here would filter one "
      + "page of rows the query never saw — right until the folder outgrows a page")
  // The fetch has to re-run when a filter moves, or the first page shown is the unfiltered one.
  assert.match(PAGE, /\}, \[objectId, sortBy, filters\]\)/u,
    "SH1022: `filters` is in the fetch's dependencies, so changing one re-queries")
  // SH1023 folded the search term's own debounce into the list state, so the query identity is
  // the folder plus what the URL says rather than four pieces joined by hand.
  assert.match(PAGE, /const queryKey = `\$\{objectId\}\|\$\{list\.key\}`/u,
    "SH1022: and it is part of the query identity, so the spinner belongs to this filter's answer")
})

test("SH1022: the console spells the server's words, not its own", () => {
  const source = readFileSync(QUERY_JAVA, "utf8")
  const block = /public static final List<String> KEYS = ([\s\S]*?);/u.exec(source)?.[1] ?? ""
  assert.ok(block, "SH1022: ShContentQuery.KEYS moved and this scan lost its subject")
  const declared = [...block.matchAll(/"([a-zA-Z]+)"/gu)].map((match) => match[1])

  for (const key of CONTENT_FILTERS) {
    assert.ok(declared.includes(key),
      `SH1022: the console sends \`${key}\` and the server does not serve it — every parameter it `
        + "does not know is a teaching 400, so this would be a listing that cannot load")
  }
  // The two the path already answers, and the five that page or order rather than filter.
  // `q` left this list with SH1023: the search box stopped being component state and became the
  // same URL word the other filters are, so the console sends it like any other. `folderPage` and
  // `folderSize` arrived with SH1025 and are the console's own paging, set beside these.
  const missing = declared.filter((key) => !CONTENT_FILTERS.includes(key))
  assert.deepEqual(missing.sort(),
    ["folder", "folderPage", "folderSize", "page", "site", "size", "sort"].sort(),
    "SH1022: a filter the server gained and the console did not offer — the curator can no "
      + "longer ask what an agent can")
})

test("SH1024: the author filters offer 'an agent', sent as the vocabulary's own word", () => {
  assert.deepEqual(filterParams({ agent: "true" }), { agent: "true" },
    "SH1024: the agent option narrows on the server, like every filter here")
  assert.match(PAGE, /checked=\{list\.value\.agent === "true"\}[\s\S]*?list\.set\("agent", checked \? "true" : ""\)/u,
    "SH1024: the browser's filters have no agent option, so a curator cannot ask what `shio find --agent true` asks")
  assert.match(PAGE, /agent: value\.agent/u, "SH1024: the option is in the URL and never reaches the request")
})

test("SH1022: a blank filter is not asked for, and the badge counts what is", () => {
  assert.deepEqual(filterParams({ type: "Article", author: "  ", state: undefined }),
    { type: "Article" })
  assert.equal(activeFilterCount({ type: "Article", modifiedSince: "2026-09-01" }), 2)
  assert.equal(activeFilterCount({}), 0,
    "SH1022: an unfiltered listing must not wear a badge saying it is narrowed")
})
