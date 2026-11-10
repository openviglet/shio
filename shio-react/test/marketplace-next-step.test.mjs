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
import { stripComments } from "./support/source-text.mjs"

/**
 * SH1422 — installing a Marketplace package ends on a next step, not a toast.
 *
 * A plain install closed its dialog onto the catalogue, and a setup that created a site linked to
 * the list of every site. Now the install keeps the dialog on the package and points at its setup
 * form, and a setup ends on the site it made: Visual Editor first, then a preview, then its pages.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")
const read = (...parts) => stripComments(readFileSync(join(SRC, ...parts), "utf8"))
const LIST = read("app", "admin", "marketplace", "marketplace.list.page.tsx")
const SETUP = read("app", "admin", "marketplace", "marketplace.setup.tsx")
const NEXT = read("app", "admin", "marketplace", "marketplace.next-step.tsx")

test("SH1422: a plain install keeps the dialog on the package and says what to do with it", () => {
  const install = LIST.slice(LIST.indexOf("const install = useCallback"), LIST.indexOf("const uninstall = useCallback"))
  assert.doesNotMatch(install, /setSelected\(null\)/,
    "SH1422: installing closes the dialog onto the catalogue again, with no next step")
  assert.match(install, /setSelected\(\{ \.\.\.item, installed: true \}\)/,
    "SH1422: the dialog no longer shows the package as installed after the install")
  assert.match(LIST, /installed !== null && \([\s\S]*?marketplace\.installedNext/,
    "SH1422: the install's answer no longer names the setup form as the next step")
})

test("SH1422: a setup ends on the site it made, read from the setup's own answer", () => {
  assert.match(SETUP, /\{done && <MarketplaceNextStep siteName=\{done\.apply\.site\} \/>\}/,
    "SH1422: the setup no longer ends on the site it applied to")
  assert.match(SETUP, /invalidateQueries\(\{ queryKey: addressKey\("site:"\) \}\)/,
    "SH1422: the site catalogue is not refreshed, so the new site cannot be found by name")
})

test("SH1422: the next step leads with the Visual Editor, then a preview, then the pages", () => {
  const order = ["editor", "preview", "pages"].map((step) => NEXT.indexOf(`data-next-step="${step}"`))
  assert.ok(order.every((at) => at > 0), "SH1422: one of the three ways into the new site is missing")
  assert.deepEqual([...order].sort((a, b) => a - b), order, "SH1422: the Visual Editor is no longer first")
  assert.match(NEXT, /editorHref\(ROUTES\.VISUAL_EDITOR, site\.id\)/, "SH1422: the editor link does not open this site")
  // A preview, because the apply wrote drafts and the published route has nothing to show yet.
  assert.match(NEXT, /siteDestination\(site\)\.url/, "SH1422: the preview is not this site's draft-preferring preview")
})

test("SH1422: every next-step string exists in English and Portuguese", () => {
  for (const lang of ["en", "pt"]) {
    const strings = JSON.parse(readFileSync(join(SRC, "i18n", "locales", lang, "marketplace.json"), "utf8")).marketplace
    for (const key of ["installedNext", "nextTitle", "nextLead", "nextEditor", "nextPreview", "nextPages", "setupOpen"]) {
      assert.ok(strings?.[key], `SH1422: marketplace.${key} is missing in ${lang}`)
    }
  }
})
