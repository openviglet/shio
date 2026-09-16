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
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"

/**
 * SH1012 — a widget the documentation calls editable has to be able to write.
 *
 * The Relator widget took `onChange` in its props, never called it, and printed that editing was
 * disabled — while the widget table in `console-ui.md` went on describing a nested items list with
 * add and remove. Neither half was checkable from the other, so the drift survived a model
 * migration and every review since.
 *
 * This is the instrument, and it is deliberately not a test of the Relator: it reads the table, and
 * for every row whose description is not marked read-only or container-only it asserts the widget
 * calls `onChange`. The next widget to quietly stop writing fails here, named.
 *
 * @scans code, prose
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const read = (...path) => readFileSync(join(ROOT, ...path), "utf8")
const DOC = join(ROOT, "..", "docs", "agents", "console-ui.md")

/**
 * A row the table itself excuses, by saying so in its own description.
 *
 * Held as a phrase rather than a widget list on purpose: adding a widget that does not write means
 * writing down, in the table a reader sees, why it does not — not editing a ledger in a test file
 * that the reader never opens.
 */
const NOT_EDITABLE = /container, no data|not rendered visually/i

/**
 * The widget table's rows, and only that table's.
 *
 * Anchored to the `Widget Names` heading and cut at the next one. A bare row regex over the whole
 * document also matches the theme-token table, the route table and the component table — thirty-two
 * "widgets", of which nineteen have no widget to check, so the scan passes by skipping almost
 * everything it claims to read. That is the same shape as the drift this file exists to catch.
 */
function documentedWidgets() {
  const doc = readFileSync(DOC, "utf8")
  const start = doc.indexOf("### Widget Names")
  assert.ok(start > 0, "SH1012: console-ui.md no longer has a `### Widget Names` heading, so this "
    + "scan cannot find the table it judges")
  const after = doc.indexOf("\n### ", start + 1)
  const table = doc.slice(start, after < 0 ? undefined : after)
  return [...table.matchAll(/^\| `([^`]+)` \| ([^|]+) \|/gmu)]
    .map(([, name, description]) => ({ name, description: description.trim() }))
    .filter(({ description }) => !NOT_EDITABLE.test(description))
}

/** Widget name → source file, from the map the form actually uses. */
function widgetSources() {
  const index = read("src", "components", "widgets", "index.ts")
  const imports = new Map([...index.matchAll(/import \{ (\w+) \} from "\.\/([\w.-]+)"/gu)]
    .map(([, symbol, file]) => [symbol, `${file}.tsx`]))
  const byName = new Map()
  for (const [, name, symbol] of index.matchAll(/"([^"]+)":\s*(\w+)/gu)) {
    const file = imports.get(symbol)
    if (file) byName.set(name, file)
  }
  return byName
}

test("SH1012: every widget the table calls editable calls onChange", () => {
  const sources = widgetSources()
  const documented = documentedWidgets()

  // The vacuity guard: a regex that stopped matching the table would pass this file in silence,
  // which is the same failure mode as the drift it exists to catch.
  assert.ok(documented.length >= 10,
    `SH1012: only ${documented.length} widget row(s) parsed out of console-ui.md — the table's `
      + "shape changed and this scan is now measuring nothing")

  // Every row the table names must BE a widget. Skipping the ones that are not is how a scan over
  // the wrong table reports success, so an unrecognised name is a failure rather than a `continue`.
  const unmapped = documented.map((w) => w.name).filter((name) => !sources.has(name))
  assert.deepEqual(unmapped, [],
    "SH1012: the table names widgets the form's own map does not register — either the row is "
      + "stale or WIDGET_MAP is missing one, and both make the table a description of something "
      + "that is not there")

  const mute = []
  for (const { name } of documented) {
    const file = sources.get(name)
    const source = read("src", "components", "widgets", file)
    // Called, not merely received. `onChange` in the props type is exactly what the Relator had.
    if (!/onChange\(|onChange\?\.\(/u.test(source)) mute.push(`${name} (${file})`)
  }

  assert.deepEqual(mute, [],
    "SH1012: these widgets are described as editable and never call onChange, so a curator's "
      + "typing goes nowhere and the form reports no error. Either wire the widget, or say in the "
      + "table's own description that it does not write — the sentence a reader sees is the one "
      + "that has to be true.")
})

test("SH1012: the Relator writes, and does not write a row's index into the row", () => {
  const widget = read("src", "components", "widgets", "relator.widget.tsx")
  const form = read("src", "components", "widgets", "post-form-fields.tsx")

  assert.match(widget, /shChildrenRelatorItems: next\.map\(\(item, index\) => \(\{ \.\.\.item, ordinal: index \}\)\)/u,
    "SH1012: every mutation goes through one emit that renumbers, or ordinal-is-position is half "
      + "true — which is worse than not being true at all")
  assert.doesNotMatch(form, /out\._id = |_id: item\.id|out\._ordinal/u,
    "SH1012: a collection row is a plain map of its declared child fields — that is what lets "
      + "ShPostDraftMergeService address features.1.body. Writing _id or _ordinal back into the "
      + "value puts undeclared keys into content the moment the widget can save, which is how "
      + "this fix would have become the defect.")
  assert.match(form, /if \(isRepeatable\(field\)\) \{\s*return \(attr\.shChildrenRelatorItems \?\? \[\]\)/u,
    "SH1012: an emptied repeatable answers with [], or removing the last row is indistinguishable "
      + "from a field never filled in and does not survive the save")
})

test("SH1012: reordering is reachable without a pointer", () => {
  const widget = read("src", "components", "widgets", "relator.widget.tsx")

  assert.match(widget, /useSensor\(KeyboardSensor, \{ coordinateGetter: sortableKeyboardCoordinates \}\)/u,
    "SH1012: drag is the only way to reorder unless this sensor is on, and a feature reachable by "
      + "pointer alone is one some curators do not have")
  assert.match(widget, /aria-label=\{t\("content\.relatorReorder"\)\}/u)
  assert.match(widget, /aria-expanded=\{isOpen \? "true" : "false"\}/u,
    "SH1012: a collapsed row says whether it is open, or a screen reader reads a button with no state")
})

test("SH1012: every string the widget asks for exists in both locales", () => {
  const widget = read("src", "components", "widgets", "relator.widget.tsx")
  const keys = [...widget.matchAll(/t\("(content\.[\w.]+)"/gu)].map(([, key]) => key)
  assert.ok(keys.length >= 5, `SH1012: only ${keys.length} key(s) found — the scan stopped matching`)

  for (const locale of ["en", "pt"]) {
    const content = JSON.parse(read("src", "i18n", "locales", locale, "content.json"))
    for (const key of keys) {
      assert.ok(content[key], `SH1012: ${locale} is missing ${key}`)
    }
  }
})

/** The listing exists so a reader of a red run can see what the scan actually covered. */
test("SH1012: the inventory is printed, not just asserted", () => {
  const sources = widgetSources()
  const documented = documentedWidgets()
  console.log(`SH1012 — ${documented.length} editable widget row(s) over ${sources.size} mapped `
    + `widget(s): ${documented.map((w) => w.name).join(", ")}`)
  assert.ok(sources.size >= 10)
})
