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
 * SH991 — the curator's agenda, and the timezone a schedule is read in.
 *
 * Read from source, like the other surface tests: what can go wrong is an absence (a page wired into
 * neither the router nor the nav declaration), a second answer to "what is scheduled" (a page reading
 * something other than the listing an agent and `shio schedule` read), and the defect the design named
 * in the dialog — local-time inputs against a stored instant, with no zone said.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const read = (...path) => readFileSync(join(ROOT, ...path), "utf8")

test("SH991: the Scheduled page is declared, decorated and routed", () => {
  const surfaces = JSON.parse(readFileSync(
    join(ROOT, "..", "shio-app", "src", "main", "resources", "console", "surfaces.json"), "utf8"))
  const declared = surfaces.surfaces.find((surface) => surface.id === "scheduled")
  assert.ok(declared, "SH991: console/surfaces.json declares no `scheduled` surface")
  assert.equal(declared.agent?.target, "shio_find",
    "SH991: an agent reaches the same answer through shio_find scheduled=, which is the P1 leg")

  assert.match(read("src", "app", "system", "bento-nav.ts"), /"scheduled":\s*\{[^}]*route:\s*ROUTES\.SCHEDULED/)
  assert.match(read("src", "App.tsx"), /path="content\/scheduled"/)
})

test("SH991: the agenda reads the agent's listing, walked to the end, not a second endpoint", () => {
  const service = read("src", "services", "schedule", "schedule.service.ts")
  assert.match(service, /"\/v2\/agent\/find"/)
  assert.match(service, /scheduled:\s*"any"/)
  assert.match(service, /cursor/, "SH991: find pages; an agenda that stops at the first page is a short one")
})

test("SH991: the page and the schedule dialog both name the zone their times are in", () => {
  assert.match(read("src", "app", "content", "scheduled.page.tsx"), /t\("content\.scheduledZone"/)
  assert.match(read("src", "components", "post-schedule.dialog.tsx"), /t\("content\.scheduleZone"/,
    "SH991: the dialog's inputs are local time against a stored instant, and it said nothing about which")

  for (const locale of ["en", "pt"]) {
    const content = JSON.parse(read("src", "i18n", "locales", locale, "content.json"))
    for (const key of ["content.scheduledZone", "content.scheduleZone"]) {
      assert.match(content[key] ?? "", /\{\{zone\}\}/, `SH991: ${locale} ${key} must interpolate the zone`)
    }
  }
})
