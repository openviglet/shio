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
 * SH1005 — the console's half: a trail that pages, narrows, and says when it moved.
 *
 * What can go wrong here is a page that keeps its filters in component state (so a narrowed trail
 * cannot be sent to anybody, bookmarked, or survive the back button), a "load more" that re-reads
 * page zero with a bigger limit, and a live stream that redraws the table under the row the curator
 * is reading.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const read = (...path) => readFileSync(join(ROOT, ...path), "utf8")

test("SH1005: the filters are the URL, not component state", () => {
  const page = read("src", "app", "admin", "activity", "activity.list.page.tsx")

  assert.match(page, /useSearchParams\(\)/,
    "SH1005: a narrowed trail is a thing a curator sends to somebody, and state that only exists "
      + "in a component cannot be sent, bookmarked, or survive the browser's own back button")
  for (const key of ["who", "action", "actor", "before"]) {
    assert.match(page, new RegExp(`search\\.get\\("${key}"\\)`),
      `SH1005: ${key} is read from the URL`)
  }
  assert.doesNotMatch(page, /useState<"" \| "agent" \| "curator">/,
    "SH1005: the filter that used to live in useState is the one this replaced")
})

test("SH1005: the page walks with the cursor the server hands back", () => {
  const page = read("src", "app", "admin", "activity", "activity.list.page.tsx")

  assert.match(page, /before: before \|\| undefined/,
    "SH1005: the next page is asked for by position, not by a bigger limit — every read of this "
      + "trail used to be page zero, capped, on the one table that only grows")
  assert.match(page, /setMore\(page\.more\)/,
    "SH1005: `more` comes from the server as a fact, so the last page does not cost a round trip "
      + "that returns nothing")
  // SH1136 made the walk state rather than a ref, because "Newer" reads it during render.
  assert.match(page, /const previous = back\.at\(-1\)/,
    "SH1005: paging back up is the way it came down, so `newer` is not a second guess")
  assert.match(page, /disabled=\{loading \|\| back\.length === 0\}/,
    "SH1136: whether there is anywhere to go back to is read during render, so it is state")
})

test("SH1005: a stream event offers the new rows, it does not impose them", () => {
  const page = read("src", "app", "admin", "activity", "activity.list.page.tsx")

  assert.match(page, /useChangeStream\(/,
    "SH1005: SH967's stream reaches the console here — the page learns while it is open")
  assert.match(page, /setArrived\(true\)/)
  assert.doesNotMatch(page, /useChangeStream\(useCallback\(\(\) => \{[^}]*load\(\)/,
    "SH1005: redrawing on every event takes the row the curator is reading off the screen, and on "
      + "this page that row is the reason they came (P7)")

  const hook = read("src", "hooks", "use-change-stream.ts")
  assert.match(hook, /withCredentials: true/,
    "SH1005: the stream is read with the curator's own session, like every other console read")
  assert.doesNotMatch(hook, /onerror[^\n]*close\(\)/,
    "SH1005: an EventSource reconnects on its own; tearing it down on an error turns a blip into "
      + "a page that never updates again")
})

test("SH1005: the post editor answers 'who changed this' on the page it is about", () => {
  const editor = read("src", "app", "content", "post.edit.page.tsx")

  assert.match(editor, /<PostHistoryPanel objectId=\{postId\} onCompare=/,
    "SH1005: the question every CMS is asked, and this console could only answer it by leaving "
      + "the page and scanning an instance-wide log")
  // It shares the review card with the notes and the analysis, one tab each.
  assert.match(editor, /<TabsTrigger value="history"/)

  const panel = read("src", "components", "post-history.panel.tsx")
  // SH1116 added the third argument, the signal; the narrowing is the first two.
  assert.match(panel, /service\.byObject\(objectId, \{ limit: PAGE_SIZE, before \}, \{ signal: abort\.signal \}\)/,
    "SH1005: narrowed in the query, so a post with more history than one screen pages correctly "
      + "rather than filtering a page of the whole trail")
})

test("SH1005: one client for the trail, and the dead per-site method is gone", () => {
  const service = read("src", "services", "history", "history.service.ts")

  assert.match(service, /Promise<ShHistoryPage>/,
    "SH1005: a page with a cursor, not a list")
  // The declaration, not the word: the comment above it says why it is gone, and a scan that
  // matched prose would fail on the sentence that explains itself.
  assert.doesNotMatch(service, /async bySite\(/,
    "SH1005: a site is one narrowing among six and it had no caller — a method per narrowing is "
      + "how a client grows the second reader this task removed from the server")
  assert.match(service, /import axios from "@\/lib\/axios"/,
    "the singleton, or CSRF and the base URL arrive by import order (SH233)")
})
