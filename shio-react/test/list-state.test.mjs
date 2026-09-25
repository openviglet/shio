/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { readFileSync, readdirSync } from "node:fs"
import { dirname, join, relative } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"

import {
  clearedParams, listState, narrowedBy, nextParams,
} from "../src/lib/list-state.ts"

/**
 * SH1023 — the same address produces the same view, and the view on screen has an address.
 *
 * Two halves, and the second is the one that will still be true next year. The first asserts the
 * round trip on the pure functions: what `nextParams` writes, `listState` reads back. The second
 * is a **census over the console's list routes** — every one of them either drives its state from
 * the URL or is declared here with the reason it has no state to drive.
 *
 * The census is the deliverable (SH322). A round-trip test proves the hook works and says nothing
 * about the six pages that do not use it, and "put the list state in the URL" is exactly the kind
 * of rule that is applied to four lists and forgotten on the fifth — which is how this started:
 * `useSearchParams` was in three console files and only one of them was a list.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const APP = join(ROOT, "src", "app")

const BROWSER = { q: "", sort: "order", view: "list", type: "", state: "" }

test("SH1023: what the URL is written with is what it reads back", () => {
  const written = nextParams(BROWSER, new URLSearchParams(), { q: "pricing", state: "draft" })
  assert.deepEqual(listState(BROWSER, written),
    { q: "pricing", sort: "order", view: "list", type: "", state: "draft" })
  assert.equal(written.toString(), "q=pricing&state=draft",
    "SH1023: a default is absent, not written — `?sort=order&view=list&type=` is the unfiltered "
      + "view wearing a filtered view's address, and nobody can skim it")
})

test("SH1023: the address for a view does not depend on the order it was built in", () => {
  const one = nextParams(BROWSER, nextParams(BROWSER, new URLSearchParams(), { state: "draft" }),
    { q: "pricing" })
  const other = nextParams(BROWSER, nextParams(BROWSER, new URLSearchParams(), { q: "pricing" }),
    { state: "draft" })
  assert.equal(one.toString(), other.toString(),
    "SH1023: two URLs for one view is P5 broken where a person compares by eye — \"is this the "
      + "link I sent you\" has to be answerable by looking")
})

test("SH1023: a key the list does not own survives every write", () => {
  const carried = new URLSearchParams("tab=media&q=old")
  const next = nextParams(BROWSER, carried, { q: "new" })
  assert.equal(next.get("tab"), "media",
    "SH1023: a hook that wrote the whole query string would drop whatever else the console put "
      + "there, on the first keystroke")
  assert.equal(next.get("q"), "new")
  assert.equal(clearedParams(BROWSER, next).get("tab"), "media")
  assert.equal(clearedParams(BROWSER, next).get("q"), null)
})

test("SH1023: setting a key back to its default removes it rather than writing it", () => {
  const set = nextParams(BROWSER, new URLSearchParams(), { view: "grid" })
  assert.equal(set.toString(), "view=grid")
  assert.equal(nextParams(BROWSER, set, { view: "list" }).toString(), "",
    "SH1023: otherwise every control a curator touched and put back leaves a trace in the address")
})

test("SH1023: the badge counts what narrows, not what was merely set", () => {
  const params = new URLSearchParams("sort=date&state=draft&q=pricing")
  assert.equal(narrowedBy(BROWSER, params), 3)
  assert.equal(narrowedBy(BROWSER, params, ["sort", "q"]), 1,
    "SH1023: a sort is not a filter, and a search box shows its own term — a badge counting them "
      + "says a folder is narrowed when it is sorted")
})

/**
 * The list routes, and what each one does about its state.
 *
 * A page is a list when its file name says so or when it renders rows from a service; rather than
 * guess, this names them. An entry with `null` uses the hook; an entry with a string is declared
 * as having no list state, and the string is the reason — which is the half a bare list would
 * lose, and the question somebody will ask again.
 */
const LIST_ROUTES = {
  "content/object.children.page.tsx": null,
  "content/search.page.tsx": null,
  "content/static-file.list.page.tsx": null,
  "admin/activity/activity.list.page.tsx": "SH1005 got there first: it reads and writes its "
    + "filters through useSearchParams directly, which is this hook's job done by hand. Left "
    + "alone rather than converted, because its paging is a keyset cursor with a walked-back "
    + "stack and the hook has no opinion about that.",
  // SH1024 — the trash gained the filter bar, and with it `q` and `type` in its URL.
  "content/trash.page.tsx": null,
  "admin/review/review.list.page.tsx": "the queue is what is waiting, in order, and the only "
    + "state is which session is expanded — a disclosure, not a view. Narrowing it is SH1001's "
    + "own question and not this one's.",
  "content/scheduled.page.tsx": "one list in time order, and the order is the point of the page.",
  "content/post-type.list.page.tsx": "the model, which is a handful of rows on every instance "
    + "this ships to — a search box over twelve types is chrome, not navigation.",
  "content/site.list.page.tsx": "the sites, for the post-type list's reason: an instance with "
    + "enough of them to need filtering is one nobody has yet run.",
}

/** Every `*.page.tsx` under `src/app`, relative to it, in a stable order. */
function pages(dir = APP, prefix = "") {
  const found = []
  for (const entry of readdirSync(dir, { withFileTypes: true }).sort((a, b) =>
    a.name.localeCompare(b.name))) {
    const at = join(dir, entry.name)
    if (entry.isDirectory()) {
      found.push(...pages(at, `${prefix}${entry.name}/`))
    } else if (entry.name.endsWith(".page.tsx")) {
      found.push(`${prefix}${entry.name}`)
    }
  }
  return found
}

test("SH1023: every declared list route exists, and every one drives its state or says why not", () => {
  const present = new Set(pages().map((path) => path.replace(/\\/gu, "/")))
  for (const [route, reason] of Object.entries(LIST_ROUTES)) {
    assert.ok(present.has(route),
      `SH1023: ${route} is declared here and is not in src/app — a census naming a page that `
        + "moved is one that stopped checking anything (SH142's rule)")

    const source = readFileSync(join(APP, route), "utf8")
    const drives = source.includes("useListState(") || source.includes("useSearchParams(")
    if (reason === null) {
      assert.ok(drives,
        `SH1023: ${route} is declared as URL-driven and reads its state from neither useListState `
          + "nor useSearchParams — so the view on screen has no address again")
    } else {
      assert.ok(reason.length > 40,
        `SH1023: ${route} is declared stateless with a reason too short to be one`)
      assert.ok(!source.includes("useListState("),
        `SH1023: ${route} is declared as having no list state and now uses the hook — move its `
          + "entry to null, or the census is describing a page that no longer exists")
    }
  }
})

test("SH1023: no list page keeps its search in component state", () => {
  // The shape this replaced, as the thing it must not come back as: a `searchQuery`/`appliedQuery`
  // pair with a debounce effect between them was written out on the browser, and copying it is
  // how the next list gets state the URL does not know about.
  for (const [route, reason] of Object.entries(LIST_ROUTES)) {
    if (reason !== null) continue
    const source = readFileSync(join(APP, route), "utf8")
    assert.doesNotMatch(source, /useState\(""\)\s*\n\s*.*setTimeout\(\(\) => setApplied/u)
    assert.doesNotMatch(source, /const \[searchQuery, setSearchQuery\]/u,
      `SH1023: ${route} holds its search term in component state. The hook's debounce replaces `
        + "that pair, and the term belongs in the address — a search nobody can send is the "
        + "defect this task is about.")
  }
})

test("SH1023: the census is not vacuous — it found the pages it names", () => {
  const all = pages()
  assert.ok(all.length >= 20, `SH1023: the walk found ${all.length} pages, which is too few`)
  assert.ok(Object.keys(LIST_ROUTES).length >= 8,
    `SH1023: ${relative(ROOT, APP)} has ${all.length} pages and only `
      + `${Object.keys(LIST_ROUTES).length} are classified`)
})
