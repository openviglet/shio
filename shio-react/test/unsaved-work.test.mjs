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
 * SH1011 — the editor stops losing work silently.
 *
 * The rule is asserted directly where it can be: `isDirty` is a pure function, so this executes it
 * rather than reading it. What can go wrong there is the failure that makes a dirty cue useless —
 * an untouched page reading as dirty, because a widget handed back the same document with its keys
 * in a different order, or `null` where the load had `""`. A cue that is always on is a cue nobody
 * looks at, and a `beforeunload` that always fires is a dialog everybody clicks through.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const read = (...path) => readFileSync(join(ROOT, ...path), "utf8")

/** The module is TypeScript with no imports, so its two functions can be executed as JS. */
const source = read("src", "lib", "dirty.ts")
  .replace(/export function/gu, "function")
  .replace(/: unknown|: string|: boolean|<unknown>|<string>/gu, "")
  .replace(/ as Record<string, unknown>/gu, "")
const { isDirty, canonicalJson } = new Function(
  `${source}; return { isDirty, canonicalJson };`)()

test("SH1011: key order is not a change", () => {
  assert.equal(isDirty({ a: 1, b: 2 }, { b: 2, a: 1 }), false,
    "SH1011: a form re-serializes the document it was given, so comparing raw JSON would report "
      + "every page as dirty the moment it rendered")
  assert.equal(canonicalJson({ b: 2, a: 1 }), canonicalJson({ a: 1, b: 2 }))
})

test("SH1011: an empty string and null are the same emptiness", () => {
  assert.equal(isDirty({ title: "" }, { title: null }), false,
    "SH1011: a widget that clears its input hands back one or the other depending on the widget, "
      + "and an untouched page must not read as dirty because one mounted")
})

test("SH1011: a real edit is dirty, nested and shallow", () => {
  assert.equal(isDirty({ title: "a" }, { title: "b" }), true)
  assert.equal(isDirty({ seo: { title: "a" } }, { seo: { title: "b" } }), true,
    "SH1011: the comparison is recursive, or a nested edit would be invisible")
  assert.equal(isDirty({ tags: ["a", "b"] }, { tags: ["b", "a"] }), true,
    "SH1011: a list's order IS its content — sorting it would call a reordered list unchanged")
})

test("SH1011: the editor derives dirty and passes it to the cue", () => {
  const page = read("src", "app", "content", "post.edit.page.tsx")

  assert.match(page, /const dirty = useMemo\(\(\) => post != null && isDirty\(post\.data \?\? \{\}, data\)/,
    "SH1011: derived rather than tracked by a flag somebody has to remember to set — a flag set on "
      + "change and cleared on save is one more thing that can be wrong in the direction that "
      + "loses work")
  assert.match(page, /dirty=\{dirty\}/,
    "SH1011: the design system has rendered this cue since it shipped, on the one page that never "
      + "passed the prop")
  assert.match(page, /useUnsavedGuard\(dirty\)/)
})

test("SH1011: the browser is asked only while there is something to lose", () => {
  const guard = read("src", "hooks", "use-unsaved-guard.ts")

  assert.match(guard, /if \(!dirty\) return undefined/,
    "SH1011: a permanently-registered handler makes every reload of every clean page show the "
      + "dialog, which is how a guard becomes the thing people click through without reading")
  assert.match(guard, /globalThis\.removeEventListener\("beforeunload", ask\)/,
    "SH1011: and it is removed, or leaving the editor leaves the guard behind")
  // A call, not a mention: the comment above explains why neither is used, and a scan that matched
  // prose would fail on the sentence that explains itself.
  assert.doesNotMatch(guard, /useBlocker\(|usePrompt\(/,
    "SH1011: both need a data router and this console mounts BrowserRouter — calling either here "
      + "throws rather than degrading (SH1090)")
})

test("SH1011: Cancel asks before it discards", () => {
  const page = read("src", "app", "content", "post.edit.page.tsx")

  assert.match(page, /if \(dirty && !globalThis\.confirm\(t\("content\.discardUnsaved"\)\)\)/,
    "SH1011: this page's own exit is the one in-app navigation it can still guard, and the "
      + "alternative is Cancel meaning discard without ever saying so")
  for (const locale of ["en", "pt"]) {
    const content = JSON.parse(read("src", "i18n", "locales", locale, "content.json"))
    assert.ok(content["content.discardUnsaved"], `SH1011: ${locale} is missing the question`)
  }
})
