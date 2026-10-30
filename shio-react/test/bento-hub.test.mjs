/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1193 — the rail was three icons over forty-odd routes, and the second level never rendered:
 * Content's `areaRoute` was the Sites list and Administration's a redirect to Users, so Trash,
 * Media, Tokens and the rest had no path a reader could see. The bento navigation is rail, then hub,
 * then item, and each level holds a handful of choices. These assertions hold both halves: every
 * section's rail link is its hub, and the hub accounts for every surface in groups of four or fewer.
 */
// @scans prose — reason: the subjects are a JSON declaration and two sources whose route strings and id lists are read as data.
import { strict as assert } from "node:assert"
import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { stripComments } from "./support/source-text.mjs"

const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")
const SURFACES = JSON.parse(readFileSync(join(HERE, "..", "..", "shio-app", "src", "main", "resources", "console", "surfaces.json"), "utf8")).surfaces
const NAV = stripComments(readFileSync(join(SRC, "app", "system", "bento-nav.ts"), "utf8"))
const APP = stripComments(readFileSync(join(SRC, "App.tsx"), "utf8"))

/** HUB_GROUPS, read from the source: section id → the id list of each group, in order. */
function hubGroups() {
  const body = NAV.match(/export const HUB_GROUPS[^=]*=\s*\{([\s\S]*?)\n\}/)?.[1]
  assert.ok(body, "SH1193: HUB_GROUPS was not found in bento-nav.ts")
  const out = {}
  for (const [, section, groups] of body.matchAll(/(\w+):\s*\[([\s\S]*?)\n\s{2}\],/g)) {
    out[section] = [...groups.matchAll(/ids:\s*\[([^\]]*)\]/g)].map(([, ids]) => [...ids.matchAll(/"([^"]+)"/g)].map((m) => m[1]))
  }
  return out
}

test("SH1193: each rail section links to its hub, not to one of its surfaces", () => {
  for (const section of ["content", "administration"]) {
    assert.match(NAV, new RegExp("areaRoute: `\\$\\{ROUTES\\.BENTO_AREAS\\}/" + section + "`"),
      `SH1193: the ${section} section's rail link is not its hub, so its second level never renders`)
  }
  assert.match(APP, /<Route path="areas\/:sectionId" element=\{<BentoAreaPage \/>\}/, "SH1193: no route renders the hub")
  assert.match(APP, /<Route path="admin" element=\{<Navigate to=\{`\$\{ROUTES\.BENTO_AREAS\}\/administration`\}/,
    "SH1193: the old admin root should land on Administration's hub, where the rail goes")
})

test("SH1193: every surface of a section is in exactly one of its hub's groups", () => {
  const groups = hubGroups()
  for (const section of new Set(SURFACES.map((surface) => surface.section))) {
    // The surface the declaration marks as navigation is the hub itself, so it is this page, not a tile.
    const declared = SURFACES.filter((surface) => surface.section === section && surface.exception?.reason !== "navigation")
      .map((surface) => surface.id).sort()
    const grouped = (groups[section] ?? []).flat()
    assert.deepEqual([...grouped].sort(), declared,
      `SH1193: ${section}'s hub groups do not account for its surfaces. A surface in no group has no path`
        + " from the rail; one in two groups is a tile drawn twice.")
    assert.equal(new Set(grouped).size, grouped.length, `SH1193: a surface appears twice in ${section}'s hub`)
  }
})

test("SH1193: no hub tile is the hub, and Users is a surface of its own", () => {
  // The first draft drew `admin` — the declaration's navigation surface, i.e. the hub — as a tile
  // titled Users inside the hub, routed at the admin root that now lands on that same hub: a link to
  // the page the reader was already on. Users, groups and roles had no surface to be a tile of.
  const hubs = SURFACES.filter((surface) => surface.exception?.reason === "navigation").map((surface) => surface.id)
  assert.deepEqual(Object.values(hubGroups()).flat().filter((id) => hubs.includes(id)), [],
    "SH1193: a hub lists itself as one of its own tiles")
  const users = SURFACES.find((surface) => surface.id === "users")
  assert.equal(users?.route, "/bento/admin/users", "SH1193: Users, groups and roles are not a declared surface")
  assert.equal(users?.section, "administration")
})

test("SH1193: no hub group offers more than four surfaces, and no hub more than four groups", () => {
  for (const [section, groups] of Object.entries(hubGroups())) {
    assert.ok(groups.length <= 4, `SH1193: ${section}'s hub has ${groups.length} groups`)
    for (const ids of groups) {
      assert.ok(ids.length <= 4, `SH1193: a ${section} group offers ${ids.length} surfaces (${ids.join(", ")});`
        + " a level that asks the reader to scan more than a handful is the 1.0 sidebar laid out as a grid")
    }
  }
})

test("SH1338: the Content hub opens a few sites directly, so the Sites card is not a step", () => {
  // Writing and publishing a page was six screens, four of them getting to the form; the Sites card
  // was one of the four. `create-page` in curator-task-budget.json measures the saving end to end;
  // this holds the hub to naming the sites, and to stopping at the threshold where a list is better.
  const area = stripComments(readFileSync(join(SRC, "app", "system", "bento-area.page.tsx"), "utf8"))
  assert.match(area, /\buseSites\(\)/, "SH1338: the hub no longer reads the sites, so it cannot name them")
  assert.match(area, /sectionId === "content"/, "SH1338: the sites group is no longer the Content hub's")
  const threshold = Number(area.match(/const DIRECT_SITES = (\d+)/)?.[1])
  assert.ok(threshold >= 1 && threshold <= 4,
    `SH1338: the hub names up to ${threshold} sites; past four a hub of tiles is a worse list than the Sites list`)
  assert.match(area, /content\/object\/\$\{site\.id\}/, "SH1338: a site tile no longer opens the site")
  const budget = JSON.parse(readFileSync(join(HERE, "..", "curator-task-budget.json"), "utf8"))
  assert.ok(budget.tasks["create-page"].clicks <= 5,
    `SH1338: create-page is budgeted at ${budget.tasks["create-page"].clicks} clicks again, above the five this saving measured`)
})
