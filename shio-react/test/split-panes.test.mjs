/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SH1331 — the console check's split-pages probe counts the split's own panes and switch.
 *
 * The probe read the post editor at 1440px as three panes and three tabs: its second pane holds
 * Notes, History and Insights tabs, and the count took them for the split's own. These are the
 * two shapes BentoSplitPage renders, desktop and phone, with that nested tab set in a pane.
 */
import { strict as assert } from "node:assert"
import { test } from "node:test"
import { JSDOM } from "jsdom"
import { splitPanesOf } from "../e2e/split-panes.mjs"

/** Runs the probe's own function over `body`, with `[hidden]` standing for what has no box. */
function seen(body) {
  const { window } = new JSDOM(`<!doctype html><body>${body}</body>`)
  window.Element.prototype.getClientRects = function () { return this.closest("[hidden]") ? [] : [{}] }
  const saved = { document: globalThis.document, getComputedStyle: globalThis.getComputedStyle }
  globalThis.document = window.document
  globalThis.getComputedStyle = window.getComputedStyle.bind(window)
  try {
    return splitPanesOf()
  } finally {
    Object.assign(globalThis, saved)
  }
}

const NESTED_TABS = `<div role="tablist"><button role="tab">Notes</button><button role="tab">History</button>
  <button role="tab">Insights</button></div><div role="tabpanel">notes</div>`

test("SH1331: at desktop width the editor is two panes and no switch, whatever a pane holds", () => {
  const desktop = `<div data-slot="bento-split-page"><div>
    <section aria-label="Content">form</section>
    <section aria-label="Notes">${NESTED_TABS}</section></div></div>`
  assert.deepEqual(seen(desktop), { split: true, panes: 2, tabs: 0 },
    "SH1331: the tabs inside the editor's second pane were counted as the split's own")
})

test("SH1331: at phone width it is one pane and the split's own two-tab switch", () => {
  const phone = `<div data-slot="bento-split-page">
    <div role="tablist" aria-label="Show"><button role="tab">Content</button><button role="tab">Notes</button></div>
    <div role="tabpanel"><section aria-label="Content">form</section></div>
    <div role="tabpanel" hidden><section aria-label="Notes">${NESTED_TABS}</section></div></div>`
  assert.deepEqual(seen(phone), { split: true, panes: 1, tabs: 2 })
})
