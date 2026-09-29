/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1205 — a Page's Sections field showed friendly URLs as chips (`/sections/b59589bb5`), which named
 * nothing a curator recognises, wrapped so the order did not read as an order, and reordered only by
 * removing and re-adding. It is an ordered list of titles now, and the stored value is unchanged.
 */
import { strict as assert } from "node:assert"
import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { stripComments } from "./support/source-text.mjs"
import { friendlyUrlOf, moved, siteOf } from "../src/lib/section-urls.ts"

const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src")

test("SH1205: a post's own address names the site its sections live in", () => {
  assert.equal(siteOf("post:VigR3/about"), "VigR3")
  assert.equal(siteOf("post:Console Check/menu/home"), "Console Check")
  assert.equal(siteOf(undefined), null)
})

test("SH1205: a search hit becomes the friendly URL the field stores, and only one of this site", () => {
  assert.equal(friendlyUrlOf("post:VigR3/sections/b59589bb5", "VigR3"), "/sections/b59589bb5")
  assert.equal(friendlyUrlOf("post:VigR4/sections/x", "VigR3"), null, "SH1205: a section from another site was offered")
  assert.equal(friendlyUrlOf("post:VigR30/sections/x", "VigR3"), null, "SH1205: a site whose name extends this one matched")
})

test("SH1205: reordering swaps neighbours and leaves the value a list of the same URLs", () => {
  const values = ["/sections/hero", "/sections/faq", "/sections/cta"]
  assert.deepEqual(moved(values, 1, -1), ["/sections/faq", "/sections/hero", "/sections/cta"])
  assert.deepEqual(moved(values, 2, 1), values, "SH1205: moving the last section down changed the order")
  assert.deepEqual([...moved(values, 0, 1)].sort(), [...values].sort())
})

test("SH1205: the Sections field is drawn by the list, and other multi-selects are not", () => {
  const widget = stripComments(readFileSync(join(SRC, "components", "widgets", "multi-select.widget.tsx"), "utf8"))
  assert.match(widget, /if \(attr\.shPostTypeAttr\?\.name === "sections" && postId\) \{[\s\S]*?<SectionList/,
    "SH1205: the Sections field is chips of URLs again")
})
