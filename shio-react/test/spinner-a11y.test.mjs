/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { readdirSync, readFileSync } from "node:fs"
import { dirname, join, relative } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"

/**
 * SH1053 — a spinner says what it is, and a busy button keeps its focus.
 *
 * Measured before this landed: 25 `<Loader2>` renders across 18 files, no `aria-busy` anywhere and
 * four `sr-only` spans in the whole console. A spinner drawn inside a button sat beside
 * `disabled={saving}`, which drops the focus of the control just pressed — the design system's
 * `Button` and `GradientButton` take `loading` instead (VDS143), which draws the spinner, sets
 * `aria-busy` and keeps focus. A spinner standing on its own said nothing to a screen reader at all.
 *
 * So `Loader2` has exactly one home, `components/spinner.tsx`, which hides the icon and carries the
 * words; every other spinner is a button's `loading` or a `<Spinner>`.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")
const HOME = "components/spinner.tsx"

function sources(dir) {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name)
    if (entry.isDirectory()) return sources(path)
    return entry.name.endsWith(".tsx") ? [path] : []
  })
}

test("SH1053: no spinner is drawn by hand outside the one component that labels it", () => {
  const offenders = []
  for (const file of sources(SRC)) {
    const rel = relative(SRC, file).replaceAll("\\", "/")
    if (rel === HOME) continue
    readFileSync(file, "utf8").split(/\r?\n/).forEach((line, i) => {
      if (/<Loader2\b/.test(line)) offenders.push(`${rel}:${i + 1}`)
    })
  }
  assert.deepEqual(offenders, [],
    "SH1053: a spinner inside a button is the button's `loading` (it keeps focus and sets"
      + " aria-busy); one on its own is <Spinner>, which a screen reader can hear:\n  "
      + offenders.join("\n  "))
})

test("SH1053: the one spinner hides its icon and says what it is", () => {
  const spinner = readFileSync(join(SRC, HOME), "utf8")
  assert.match(spinner, /<Loader2[^>]*aria-hidden="true"/u,
    "SH1053: the icon is decoration; the words are the spinner")
  assert.match(spinner, /className="sr-only"/u, "SH1053: the label is there for a screen reader")
  assert.match(spinner, /aria-busy/u, "SH1053: the region it stands in for says it is busy")
})
