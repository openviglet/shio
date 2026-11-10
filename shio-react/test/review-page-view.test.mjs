/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1429 — a curator approves a page having seen it. SH1425 — and one row per page, not per write.
 */
import { strict as assert } from "node:assert"
import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { stripComments } from "./support/source-text.mjs"

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const read = (...path) => stripComments(readFileSync(join(ROOT, ...path), "utf8"))
const TRIAGE = read("src", "components", "review-triage.tsx")
const THUMBNAIL = read("src", "components", "review-page-thumbnail.tsx")
const PREVIEW_URL = read("src", "lib", "draft-preview-url.ts")
const PAGE = read("src", "app", "admin", "review", "review.list.page.tsx")

test("SH1429: each row draws its page and offers its preview, and the address is no longer the row", () => {
  assert.match(TRIAGE, /<ReviewPageThumbnail url=\{preview\}/,
    "SH1429: the row shows the page it asks the curator to decide on")
  assert.match(TRIAGE, /t\("review\.preview"\)/, "SH1429: a Preview action opens the draft whole")
  assert.doesNotMatch(TRIAGE, /<code[^>]*>\{entry\.address\}<\/code>/,
    "SH1429: the address in code type is back as the row's second line")
  assert.match(TRIAGE, /title=\{entry\.address\}/, "SH1429: the address stays reachable as a tooltip")
})

test("SH1429: the thumbnail is the curator's browser drawing the draft, lazily and without scripts", () => {
  assert.match(THUMBNAIL, /loading="lazy"/, "SH1429: a forty-page run renders only the rows on screen")
  assert.match(THUMBNAIL, /sandbox="allow-same-origin"/,
    "SH1429: a thumbnail must not run the page's scripts or take focus")
  assert.match(PREVIEW_URL, /`\/preview\/\$\{encodeURIComponent\(site\)\}\//,
    "SH1429: the draft preview of the address, which the console session already reaches")
})

test("SH1425: the open run lists pages, and the run's title counts pages", () => {
  assert.match(PAGE, /entries=\{detail\.pages \?\? detail\.entries\}/,
    "SH1425: the triage lists audit rows again, so four new pages read as eight changes")
  assert.match(PAGE, /run\.byPage \?\? run\.byAction/, "SH1425: the title counts writes again")
})
