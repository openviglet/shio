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
import { after, test } from "node:test"
import { fileURLToPath } from "node:url"

/**
 * SH964 — a toast moves the dock, and nothing in the console had to change for it.
 *
 * The claim worth testing is the one the design rests on: `useAssistantNotifications` reads the
 * live toast list, so the console's existing `toast.error(...)` calls reach the dock untouched.
 * That only exists with effects running, which `renderToStaticMarkup` never does — so this mounts
 * the real component in jsdom and drives it with the package's own `toast`, the one every file in
 * `src` imports.
 */
const { JSDOM } = await import("jsdom")
const dom = new JSDOM("<!doctype html><html><body><div id=root></div></body></html>", { pretendToBeVisual: true })
let reducedMotion = false
dom.window.matchMedia = (query) => ({
  matches: /prefers-reduced-motion:\s*reduce/.test(query) ? reducedMotion : false,
  media: query, onchange: null,
  addListener() {}, removeListener() {}, addEventListener() {}, removeEventListener() {}, dispatchEvent() { return false },
})
for (const key of ["window", "document", "navigator", "HTMLElement", "Node", "MutationObserver", "getComputedStyle",
  "requestAnimationFrame", "cancelAnimationFrame", "matchMedia"]) {
  Object.defineProperty(globalThis, key, { value: dom.window[key] ?? dom.window, configurable: true, writable: true })
}
globalThis.ResizeObserver ??= class { observe() {} unobserve() {} disconnect() {} }
globalThis.IS_REACT_ACT_ENVIRONMENT = true

const { createElement: h, act } = await import("react")
const { createRoot } = await import("react-dom/client")
const { toast } = await import("@viglet/viglet-design-system")
const { BentoStatusDock } = await import("../src/app/system/bento-status-dock.ts")

const HERE = dirname(fileURLToPath(import.meta.url))
const container = dom.window.document.getElementById("root")
const root = createRoot(container)
after(() => act(() => root.unmount()))

/** The mascot draws on a canvas and names its state there; that attribute is the dock's state. */
const stateOf = () => container.querySelector("canvas[data-state]")?.getAttribute("data-state")
const settle = () => act(() => new Promise((resolve) => setTimeout(resolve, 30)))

test("SH964: the dock mounts idle, with no composer, because Shio passes no onSend", async () => {
  await act(() => root.render(h(BentoStatusDock)))
  await settle()
  assert.ok(container.innerHTML.length > 0, "SH964: the dock rendered nothing")
  assert.equal(container.querySelector("textarea, input[type=text]"), null,
    "SH964: the dock renders a composer. Shio hosts no model; a chat box with nothing behind it is a"
      + " promise the product cannot keep, which is why the component passes no onSend.")
  assert.equal(stateOf(), "idle", "SH964: with no toast on screen the dock is not idle")
})

test("SH964: a toast.error moves the dock to error, with the toast's title as its caption", async () => {
  await act(() => { toast.error("Publish refused: the page has no title") })
  await settle()
  assert.equal(stateOf(), "error",
    "SH964: a toast.error did not move the dock. The hook reads the live toast list, so this means"
      + " the dock and the console's toast are not the same sonner instance.")
  assert.match(container.textContent, /Publish refused: the page has no title/,
    "SH964: the dock's caption is not the toast's title")
  assert.ok(container.querySelector("[aria-live]"),
    "SH964: the caption is not a live region, so a screen-reader user hears nothing")
})

test("SH964: an empty toast list returns the dock to idle", async () => {
  await act(() => { toast.dismiss() })
  await act(() => new Promise((resolve) => setTimeout(resolve, 600)))
  assert.equal(stateOf(), "idle", "SH964: the dock did not return to idle once the toast list emptied")
  assert.doesNotMatch(container.textContent, /Publish refused/,
    "SH964: the dock still carries a dismissed toast's caption")
})

test("SH964: the shell hands the dock its corner", () => {
  const shell = readFileSync(join(HERE, "..", "src", "app", "system", "bento.root.page.tsx"), "utf8")
  assert.match(shell, /dock=\{<BentoStatusDock \/>\}/,
    "SH964: the dock is not passed to BentoShell's dock slot. Mounted anywhere else it pins itself to"
      + " the viewport corner and covers back-to-top, which is what VDS133 moved into the shell.")
})
