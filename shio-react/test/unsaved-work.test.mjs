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

/**
 * Imported, not read: Node 26 strips the types itself, so these run the module the console loads
 * rather than a copy of it a regex in this file reconstructed. The earlier version did the latter
 * and broke the moment a `string[]` appeared — a test that can fail for a reason that has nothing
 * to do with the rule it asserts.
 */
const { isDirty, canonicalJson, changedAttrs, changedKeys } =
  await import("../src/lib/dirty.ts")

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

  // SH1090 — Cancel is one in-app navigation among many, and the router blocker asks for all of them.
  assert.match(page, /useUnsavedGuard\(dirty\)/,
    "SH1011: the editor's unsaved work is guarded, or Cancel means discard without ever saying so")
  assert.doesNotMatch(page, /if \(dirty && !globalThis\.confirm\(t\("content\.discardUnsaved"\)\)\)/,
    "SH1090: a page confirming its own exit as well as the blocker asks the curator twice")
  for (const locale of ["en", "pt"]) {
    const content = JSON.parse(read("src", "i18n", "locales", locale, "content.json"))
    assert.ok(content["content.discardUnsaved"], `SH1011: ${locale} is missing the question`)
  }
})

test("SH1090: the router is a data router, so an in-app navigation can be blocked", () => {
  // The cheaper instrument the design named: useBlocker throws outside a data router, and the
  // console mounted <BrowserRouter> for as long as SH1011's note said it could not block a route.
  const app = read("src", "app", "..", "App.tsx")
  const main = read("src", "main.tsx")
  assert.match(app, /createBrowserRouter\(createRoutesFromElements\(/,
    "SH1090: the routes are not a data router's, so useBlocker throws on every page that asks")
  assert.match(app, /<RouterProvider router=\{router\} \/>/)
  assert.doesNotMatch(main, /<BrowserRouter>/,
    "SH1090: a <BrowserRouter> above the data router makes it a router inside a router")
  const guard = read("src", "hooks", "use-unsaved-guard.ts")
  assert.match(guard, /useBlocker\(\(\{ currentLocation, nextLocation \}\) =>\s*dirty && currentLocation\.pathname !== nextLocation\.pathname\)/u,
    "SH1090: the guard does not block an in-app navigation, or blocks a list writing its filters to "
      + "the URL, which would put a dialog in front of every keystroke")
})

test("SH1011: an autosave names the changed keys and nothing else", () => {
  assert.deepEqual(changedKeys({ a: 1, b: 2 }, { a: 1, b: 3 }), ["b"],
    "SH1011: naming `a` too would overwrite another writer's change to a field this page never "
      + "touched — the merge verb's rule is that a field NOT named keeps its value, and that is "
      + "worth something only if the caller names as little as it can")
  assert.deepEqual(changedKeys({ a: 1 }, { a: 1 }), [])
})

test("SH1011: clearing a field is an edit an autosave can express", () => {
  assert.deepEqual(changedKeys({ title: "x" }, {}), ["title"],
    "SH1011: a key present in the load and absent from the form is a clear, and an autosave that "
      + "could not say so would silently keep the old value")
  assert.deepEqual(changedAttrs({ title: "x" }, {}), { title: "" },
    "SH1011: and it is sent as the empty value, not as undefined, which JSON would drop")
})

test("SH1011: autosave writes the draft, stops on a refusal, and never races the curator", () => {
  const hook = read("src", "hooks", "use-autosave.ts")

  assert.match(hook, /stopped\.current = true/u,
    "SH1011: a conditional write refused with a 409 means somebody else wrote — retrying is the "
      + "overwrite the guard exists to prevent, and retrying silently on a timer is that overwrite "
      + "with nobody watching")
  assert.match(hook, /if \(!dirty \|\| busy \|\| !pending \|\| stopped\.current\)/u,
    "SH1011: `busy` is the curator's own save, publish or dialog — two writes racing on one row is "
      + "what SH985 would then refuse, and the refusal would be the page arguing with itself")
  assert.match(hook, /\}, \[dirty, busy, pending, signature\]\)/u,
    "SH1011: debounced on the VALUE — on the key list alone it would fire three seconds after the "
      + "FIRST keystroke rather than after the typing stops")

  const page = read("src", "app", "content", "post.edit.page.tsx")
  assert.match(page, /unifiedService\.patch\(postId, attrs\)/u,
    "SH1011: the merge verb (SH335), which the console service already sends If-Match on")
  assert.match(page, /busy: isLoading \|\| isPublishing \|\| conflict !== undefined/u)
})

test("SH1011: the browser's own copy is offered, never restored", () => {
  const page = read("src", "app", "content", "post.edit.page.tsx")
  const buffer = read("src", "lib", "draft-buffer.ts")

  assert.match(page, /setOffered\(\{ data: buffer\.data, at: buffer\.at \}\)/u,
    "SH1011: a buffer that restored itself would make the page a second writer with no name on "
      + "it — a curator who finds text they do not remember typing cannot tell whose it is")
  assert.match(page, /content\.bufferedRestore/u)
  assert.match(page, /content\.bufferedDiscard/u)
  for (const locale of ["en", "pt"]) {
    const content = JSON.parse(read("src", "i18n", "locales", locale, "content.json"))
    for (const key of ["content.bufferedOffer", "content.bufferedRestore",
      "content.bufferedDiscard", "content.autosaveSaved", "content.autosaveStopped"]) {
      assert.ok(content[key], `SH1011: ${locale} is missing ${key}`)
    }
  }

  assert.match(buffer, /request\.onerror = \(\) => resolve\(null\)/u,
    "SH1011: a private window, blocked site data or a quota refuses, and a dialog about browser "
      + "storage helps nobody — the work is still on screen and autosave is still running")
})
