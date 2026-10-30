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

/**
 * SH1314 — a table column that draws two lines says so.
 *
 * The design system's 2026.3.24 gives a row 56px instead of 44px while a visible column declares
 * `lines: 2` (VDS232), because its identity cell (a name and a detail) and its change cell (a time
 * and an actor) draw two lines, and two lines in 44px touch both dividers. The row height is the
 * table's, so the declaration is the only way the table learns it: a column that draws two lines
 * and does not declare them renders exactly as before, crowded, with nothing on screen or in a
 * test to say why. That is what a new list would do by default, so it is read off the source.
 *
 * A change cell drawn `inline` keeps its actor on the time's line and is one line; a change cell
 * without an actor is one line; an identity cell without a detail is one line.
 */
const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src")

function* tsx(dir) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name)
    if (entry.isDirectory()) yield* tsx(path)
    else if (entry.name.endsWith(".tsx")) yield path
  }
}

/** Every two-line cell drawn inside a column's `cell:`, with the column it belongs to. */
function twoLineCells(source) {
  const found = []
  const cell = /<Bento(IdentityCell|ChangeCell)\b([^>]*)\/?>/gu
  for (const match of source.matchAll(cell)) {
    const [, kind, props] = match
    const twoLines = kind === "IdentityCell"
      ? /\bdetail=/u.test(props)
      : /\bactor=/u.test(props) && !/\binline\b/u.test(props)
    if (!twoLines) continue
    const before = source.slice(0, match.index)
    const start = before.lastIndexOf('id: "')
    if (start < 0) continue
    const column = before.slice(start)
    // A cell rendered outside a column (a card, a helper component) is not this rule's subject.
    if (!/\bcell:/u.test(column)) continue
    // SH1309 — a column whose second line is conditional may declare it conditionally, `lines: x ? 2 : 1`.
    found.push({ column: column.match(/^id: "([^"]+)"/u)[1], declared: /\blines:\s*(?:[\w.]+\s*\?\s*)?2\b/u.test(column) })
  }
  return found
}

test("SH1314: every column whose cell draws two lines declares lines: 2", () => {
  const missing = []
  let seen = 0
  for (const file of tsx(SRC)) {
    for (const { column, declared } of twoLineCells(readFileSync(file, "utf8"))) {
      seen++
      if (!declared) missing.push(`${relative(SRC, file)} column "${column}"`)
    }
  }
  assert.ok(seen >= 10,
    `SH1314: only ${seen} two-line columns were found; the console has at least ten, so the reader`
      + " stopped seeing them and this test now passes on nothing")
  assert.deepEqual(missing, [],
    "SH1314: these columns draw a name and a detail, or a time and an actor, without `lines: 2`, so"
      + " their rows stay 44px and the two lines touch both dividers. Declare it, or draw the change"
      + " cell `inline`.")
})

test("SH1314: the reader sees a missing declaration, and an inline cell is not one", () => {
  const without = 'const c = [{ id: "name", header: "Name", cell: (r) => <BentoIdentityCell name={r.n} detail={r.d} /> }]'
  assert.deepEqual(twoLineCells(without), [{ column: "name", declared: false }])
  const declared = without.replace('header: "Name",', 'header: "Name", lines: 2,')
  assert.deepEqual(twoLineCells(declared), [{ column: "name", declared: true }])
  const conditional = without.replace('header: "Name",', 'header: "Name", lines: twoLines ? 2 : 1,')
  assert.deepEqual(twoLineCells(conditional), [{ column: "name", declared: true }],
    "SH1309: a column that asks for two lines only while a row has a second line declares it")
  const neverTwo = without.replace('header: "Name",', 'header: "Name", lines: 1,')
  assert.deepEqual(twoLineCells(neverTwo), [{ column: "name", declared: false }])
  const inline = 'const c = [{ id: "when", cell: (r) => <BentoChangeCell at={r.at} actor={r.who} inline /> }]'
  assert.deepEqual(twoLineCells(inline), [])
})
