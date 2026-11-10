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
 * SH1420 — the Marketplace is where a first site starts.
 *
 * It sat under Administration, Instance, beside the e-mail settings, and nothing a newcomer meets
 * on an empty instance named it: the site list said "create your first site", New site was three
 * blank fields, and home led with a blank site. These hold the four places that now say it.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")
const read = (...parts) => stripComments(readFileSync(join(SRC, ...parts), "utf8"))
const SURFACES = JSON.parse(readFileSync(
  join(HERE, "..", "..", "shio-app", "src", "main", "resources", "console", "surfaces.json"), "utf8"))
const NAV = read("app", "system", "bento-nav.ts")
const START = read("app", "content", "first-site-start.tsx")

test("SH1420: the Marketplace surface is in Content, in the hub's Start group, and not a curator's", () => {
  const marketplace = SURFACES.surfaces.find((surface) => surface.id === "marketplace")
  assert.equal(marketplace?.section, "content",
    "SH1420: the Marketplace went back to Administration, where no newcomer looks for how to start")
  assert.match(NAV, /labelKey: "nav\.hub\.content\.start", ids: \["marketplace"/,
    "SH1420: the Content hub lost its Start group, so the Marketplace has no tile where sites start")
  assert.match(NAV, /BUILDER_SURFACES = new Set\(\[[^\]]*"marketplace"/,
    "SH1420: a curator's rail offers the Marketplace, though installing a package is an administrator's act")
})

test("SH1420: the first-use choices lead with the Marketplace, offered to an administrator only", () => {
  const order = ["\"marketplace\"", "\"blank\"", "\"import\""].map((id) => START.indexOf(`id: ${id}`))
  assert.ok(order.every((at) => at > 0), "SH1420: a first-use choice is missing")
  assert.deepEqual([...order].sort((a, b) => a - b), order,
    "SH1420: the Marketplace is no longer the first way a first site starts")
  assert.match(START, /\.\.\.\(admin \? \[\{\s*id: "marketplace"/,
    "SH1420: the Marketplace choice is no longer gated on an administrator")
  assert.match(START, /to: ROUTES\.ADMIN_MARKETPLACE/, "SH1420: the Marketplace choice opens nothing")
})

test("SH1420: an empty site list, an empty home and the New site form all name the start", () => {
  const list = read("app", "content", "site.list.page.tsx")
  assert.match(list, /if \(sites\?\.length === 0 && !error\) \{[\s\S]*?<FirstSiteStart \/>/,
    "SH1420: an empty site list is an empty table again, with no way to a ready-made site")
  const home = read("app", "system", "home.page.tsx")
  assert.match(home, /const firstUse = siteCount === 0/, "SH1420: home no longer knows the instance is new")
  assert.match(home, /\{firstUse && \([\s\S]*?<FirstSiteStart \/>/,
    "SH1420: home on an empty instance no longer shows how a first site starts")
  assert.match(home, /url: ROUTES\.ADMIN_MARKETPLACE/, "SH1420: home's quick actions lost the Marketplace")
  const create = read("app", "content", "site.new.page.tsx")
  assert.match(create, /\{admin && \([\s\S]*?ROUTES\.ADMIN_MARKETPLACE/,
    "SH1420: the New site form no longer names the Marketplace as the faster start")
})

test("SH1420: every first-use string exists in English and Portuguese", () => {
  const locale = (lang, file) => JSON.parse(readFileSync(join(SRC, "i18n", "locales", lang, file), "utf8"))
  const keys = ["startTitle", "startSubtitle", "startMarketplaceTitle", "startMarketplaceDesc",
    "startRecommended", "startBlankTitle", "startBlankDesc", "startImportTitle", "startImportDesc",
    "startGo", "newSiteMarketplaceHint", "newSiteMarketplaceLink"]
  for (const lang of ["en", "pt"]) {
    const content = locale(lang, "content.json")
    for (const key of keys) assert.ok(content[`content.${key}`], `SH1420: content.${key} is missing in ${lang}`)
    assert.ok(locale(lang, "nav.json")["nav.hub.content.start"], `SH1420: nav.hub.content.start is missing in ${lang}`)
    assert.ok(locale(lang, "home.json").home?.actionMarketplace, `SH1420: home.actionMarketplace is missing in ${lang}`)
  }
})
