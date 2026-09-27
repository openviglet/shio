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
import { a11yFindings, GATED_IMPACTS, staleDebt } from "../scripts/a11y-debt.mjs"
import { stripComments } from "./support/source-text.mjs"

/**
 * SH1054 — axe against every console route, and what a result is allowed to mean.
 *
 * The run needs a browser and lives in `console-check.mjs`; the decision is pure and lives here. The
 * first axe pass found 78 violations on 65 routes — unnamed back links on 28 of them, 32 unnamed
 * icon buttons in one table, `<html lang="en">` over a Portuguese console, and Shio's own `dark:`
 * utilities following the operating system instead of the theme — and the gate now holds zero.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const DEBT = JSON.parse(readFileSync(join(ROOT, "a11y-debt.json"), "utf8"))
const read = (...parts) => readFileSync(join(ROOT, ...parts), "utf8")

test("SH1054: a planted unlabelled input names the route, the rule and the element", () => {
  const planted = [{ id: "label", impact: "critical", nodes: 1, target: "#planted-input" }]
  const { findings } = a11yFindings("post-edit:Page", "post-edit", planted, DEBT)
  assert.equal(findings.length, 1)
  assert.match(findings[0], /^post-edit:Page: critical label \(1 element, first #planted-input\)/u,
    "SH1054: a finding a curator's developer cannot act on names less than all three")
})

test("SH1054: serious and critical are never debt, and a new lesser one is not either", () => {
  const debt = { routes: { sites: ["heading-order", "label"] } }
  for (const impact of GATED_IMPACTS) {
    const { findings } = a11yFindings("sites", "sites", [{ id: "label", impact, nodes: 2, target: "x" }], debt)
    assert.equal(findings.length, 1, `SH1054: a listed rule at ${impact} still fails`)
  }
  assert.equal(a11yFindings("sites", "sites",
    [{ id: "heading-order", impact: "moderate", nodes: 1, target: "h3" }], debt).findings.length, 0,
    "SH1054: a moderate rule the family holds passes")
  assert.equal(a11yFindings("sites", "sites",
    [{ id: "region", impact: "moderate", nodes: 1, target: "div" }], debt).findings.length, 1,
    "SH1054: a moderate rule the family does not hold fails — nothing arrives silently")
})

test("SH1054: the map shrinks only by deletion — a paid entry is named", () => {
  const debt = { routes: { sites: ["heading-order", "region"] } }
  const notes = staleDebt(debt, new Map([["sites", new Set(["heading-order"])]]))
  assert.deepEqual(notes, ["sites: region is no longer found — delete it from a11y-debt.json"])
  assert.deepEqual(staleDebt(debt, new Map()), [],
    "SH1054: a family this run did not walk is a fixture gap, not a paid debt")
})

test("SH1054: every vendored exclusion says why, and the committed map holds nothing gated", () => {
  for (const [selector, why] of Object.entries(DEBT.vendor ?? {})) {
    assert.ok(why.length >= 80, `SH1054: vendor exclusion ${selector} needs its reason, not a label`)
  }
  assert.ok(Object.keys(DEBT.vendor ?? {}).length <= 1,
    "SH1054: a second vendored exclusion is a decision; add it with its reason and raise this count")
})

test("SH1054: the check runs axe on every route, at three widths, and checks the document's language", () => {
  const check = stripComments(read("e2e", "console-check.mjs"))
  assert.match(check, /probe\.a11y = await runAxe\(page\)/u, "SH1054: the per-route axe run is gone")
  assert.match(check, /a11yFindings\(route\.name, family, probe\.a11y\.violations, A11Y_DEBT\)/u)
  assert.match(check, /langMismatch/u, "SH1054: the document-language check is gone")
  assert.match(check, /await checkViewports\(browser\)/u, "SH1054: the phone and tablet widths are gone")
  assert.match(check, /\{ name: "phone", width: 390/u)
  assert.match(check, /\{ name: "tablet", width: 820/u)
})

test("SH1054: the document names its language, and dark: follows the theme the curator chose", () => {
  assert.match(read("src", "i18n", "index.ts"), /document\.documentElement\.lang = lng/u,
    "SH1054: <html lang> stays `en` over a Portuguese console again")
  const css = read("src", "index.css")
  assert.match(css, /@custom-variant dark \(&:is\(\.dark \*\)\);/u,
    "SH1054: without it Tailwind's dark: follows prefers-color-scheme, not the console's theme")
  assert.match(css, /\.dark \.prose \{[\s\S]*?--tw-prose-body: var\(--tw-prose-invert-body\);/u,
    "SH1054: the design system's prose ships both palettes and nothing swaps them in dark")
})

test("SH1054: jsx-a11y's recommended rules run with the console's own lint", () => {
  const config = read("eslint.config.js")
  assert.match(config, /import jsxA11y from 'eslint-plugin-jsx-a11y'/u)
  assert.match(config, /jsxA11yRecommended,\s*\n\s*\]/u, "SH1054: the jsx-a11y rules left the tsx config")
})
