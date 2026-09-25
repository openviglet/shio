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
 * SH1066 — the Forms page: a site's submissions, and where its form sends them.
 *
 * A submission is a post, so it had storage, trash and ACL; it had no place a curator went to read
 * one. These pin the three things that make the page that place rather than another list: it asks
 * the same `submission=true` question an agent asks, it edits the site's one form configuration
 * (creating it the first time, updating it after), and the rail offers it.
 *
 * @scans code
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const read = (...path) => readFileSync(join(ROOT, ...path), "utf8")
const PAGE = read("src", "app", "content", "forms.page.tsx")

test("SH1066: the Forms page lists what a form wrote, by the agent's own question", () => {
  assert.match(PAGE, /searchService\.search\(\{ site: site\.name, submission: "true" \}/u,
    "SH1066: the page does not ask submission=true, so it is a second definition of a submission")
})

test("SH1066: the settings are the site's one form row, created once and updated after", () => {
  assert.match(PAGE, /formId\s*\?\s*await formService\.update\(formId, settings\)\s*:\s*await formService\.create\(settings\)/u,
    "SH1066: saving always creates, and SH632 refuses a second configuration for a site with 409")
  for (const key of ["folderUrl", "allowedTypes", "allowedOrigins"]) {
    assert.match(PAGE, new RegExp(`field\\("${key}"`, "u"), `SH1066: ${key} has no field`)
  }
})

test("SH1066: the rail offers Forms, and every string exists in both locales", () => {
  const nav = read("src", "app", "system", "bento-nav.ts")
  assert.match(nav, /"forms": \{[^}]*route: ROUTES\.FORMS/u, "SH1066: the Forms surface has no nav entry")
  const surfaces = JSON.parse(read("..", "shio-app", "src", "main", "resources", "console", "surfaces.json"))
  const forms = (surfaces.surfaces ?? surfaces).find((surface) => surface.id === "forms")
  assert.equal(forms?.agent?.target, "shio_find",
    "SH1066: the surface must name its agent counterpart, which is shio_find with submission=true")
  for (const locale of ["en", "pt"]) {
    const content = JSON.parse(read("src", "i18n", "locales", locale, "content.json"))
    for (const key of ["content.formsTitle", "content.formsDescription", "content.formsSettings",
      "content.formsFolder", "content.formsTypes", "content.formsOrigins", "content.formsEnabled",
      "content.formsSaved", "content.formsEmptyTitle", "content.formsEmptyHint", "content.formsTrash"]) {
      assert.ok(content[key], `SH1066: ${locale} is missing ${key}`)
    }
    assert.ok(JSON.parse(read("src", "i18n", "locales", locale, "nav.json"))["nav.desc.forms"],
      `SH1066: ${locale} is missing nav.desc.forms`)
  }
})
