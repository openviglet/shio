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
import { buttonIndex, caretY, gapIndex, placeAt } from "../src/app/editor/section-placement.ts"
import { stripComments } from "./support/source-text.mjs"

/**
 * SH1105 — a drop on the caret is a way of naming the same number a button names.
 *
 * Three sections stacked 100px apart. The caret's gaps are 0..3, and with section `i` selected,
 * Above names gap `i` and Below names gap `i + 1`: the pointer over the lower half of section `i`,
 * or the upper half of `i + 1`, is that same gap, so the array written is the same array.
 */
const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src")
const boxes = [0, 100, 200].map((top) => ({ top, left: 0, width: 800, height: 90 }))
const entries = ["/s/hero", "/s/features", "/s/faq"]

test("SH1105: a drop between two sections writes the same array a Below button writes", () => {
  for (let selected = 0; selected < boxes.length; selected++) {
    const below = placeAt(entries, "/s/new", buttonIndex(selected, true, entries.length))
    const lowerHalf = boxes[selected].top + boxes[selected].height * 0.75
    assert.deepEqual(placeAt(entries, "/s/new", gapIndex(boxes, lowerHalf)), below,
      `SH1105: a drop just under section ${selected} must write what Below writes with it selected`)
  }
  assert.deepEqual(placeAt(entries, "/s/new", gapIndex(boxes, 10)),
    placeAt(entries, "/s/new", buttonIndex(0, false, entries.length)),
    "SH1105: and the top half of the first section is Above it")
  assert.deepEqual(placeAt(entries, "/s/new", gapIndex(boxes, 5000)),
    placeAt(entries, "/s/new", buttonIndex(null, true, entries.length)),
    "SH1105: past the last section is At end")
})

test("SH1105: the caret is drawn in the gap it names, and the input array is never mutated", () => {
  assert.equal(caretY(boxes, 0), 0)
  assert.equal(caretY(boxes, 1), 95, "midway between the first box's bottom (90) and the second's top (100)")
  assert.equal(caretY(boxes, 3), 290)
  assert.equal(caretY([], 0), null, "no boxes, no caret: there is nothing to be between")
  const before = [...entries]
  placeAt(entries, "/s/new", 1)
  assert.deepEqual(entries, before)
})

test("SH1105: the editor places through section-placement, not a splice of its own", () => {
  const page = stripComments(readFileSync(join(SRC, "app", "editor", "visual-editor.page.tsx"), "utf8"))
  assert.match(page, /placeAt\(sectionArray\.entries, reference, at\)/,
    "SH1105: the insert writes the one arithmetic both the buttons and the caret name an index for")
  assert.match(page, /insertSection\(type, dropGap\(event\)\)/, "SH1105: a drop inserts at the gap")
  assert.match(page, /insertSection\(type, buttonIndex\(/, "SH1105: a button inserts at the index it names")
})
