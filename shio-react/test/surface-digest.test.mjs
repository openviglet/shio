/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { test } from "node:test"

import { compare, digestOf, structureOf, survey } from "../scripts/surface-digest.mjs"

/**
 * SH896 — the only evidence a console surface rendered correctly was a person opening it.
 *
 * P9 asks for cheap textual proof, and 43 surfaces judged by eye is the perception loop left open
 * on the largest single change the console has had. `scripts/surface-digest.mjs` closes it; this is
 * what says the digest is worth trusting.
 *
 * ## A digest is only proof if it is wrong in exactly one direction
 *
 * Two failure modes, and they pull against each other. A digest that moves on **everything** —
 * reformatting, a renamed handler, a new comment — is noise, and a reviewer learns within a week to
 * re-baseline without reading it, which is worse than having none because it looks like proof. A
 * digest that moves on **nothing** is worse still: it goes green across a migration it never saw,
 * which is precisely SH897's complaint about the console walk.
 *
 * So both directions are asserted here, and neither would be caught by testing the hash function.
 * `stable under noise` is the property that makes the tool usable; `moves on structure` is the
 * property that makes it a proof. A change to the chrome vocabulary that broke either would pass a
 * naive round-trip test.
 */

/** A minimal console page, written out rather than read, so the noise cases can be exact. */
const PAGE = `
import { PageHeader } from "@/components/page-header"
import { Card, CardContent } from "@/components/ui/card"

export default function Thing() {
  const handleSave = () => save()
  return (
    <div className="p-6">
      <PageHeader title="Thing" />
      <Card className="rounded-lg border bg-card shadow-sm">
        <CardContent className="p-0">{rows}</CardContent>
      </Card>
    </div>
  )
}
`

test("SH896: the digest is stable under changes that are not about structure", () => {
  const base = digestOf(PAGE)

  // Reformatting. A reviewer who reflows a file has not changed what renders.
  assert.equal(digestOf(PAGE.replace(/\n\s+/g, "\n ")), base, "SH896: reindenting moved the digest")

  // A comment, including one that names chrome. If a commented-out <Card> counted, every page
  // documenting the layer it is leaving would read as still rendering it.
  assert.equal(
    digestOf(PAGE.replace("return (", "// was: <Card><CardHeader/></Card>\n  return (")),
    base,
    "SH896: a comment moved the digest — and a comment naming chrome would read as rendering it",
  )

  // Logic. Renaming a handler or adding a hook is the bulk of what changes in these files.
  assert.equal(
    digestOf(PAGE.replace("handleSave", "persist").replace("const rows", "const items")),
    base,
    "SH896: renaming a local moved the digest, which makes the tool noise",
  )
})

test("SH896: the digest moves when the structure moves, and says which field", () => {
  const base = digestOf(PAGE)

  // The migration itself: one component swapped for its bento counterpart.
  assert.notEqual(digestOf(PAGE.replace("<Card ", "<BentoSection ")), base,
    "SH896: swapping Card for BentoSection left the digest alone — the tool would go green across "
      + "the migration it exists to witness")

  // Order, not just membership. A hero after the sections is a different page.
  const reordered = PAGE.replace("<PageHeader title=\"Thing\" />\n      ", "")
    .replace("</Card>", "</Card>\n      <PageHeader title=\"Thing\" />")
  assert.notEqual(digestOf(reordered), base, "SH896: moving the header did not move the digest")

  // The era, on its own. A page can import the subpath before it renders anything from it.
  assert.notEqual(
    digestOf(`import { BentoHero } from "@viglet/viglet-design-system/bento"\n${PAGE}`),
    base,
    "SH896: the era is not in the digest",
  )

  // And the surface vocabulary §LIV.11 names — the frosted panel and its lift.
  assert.notEqual(digestOf(PAGE.replace("shadow-sm", "backdrop-blur-md")), base,
    "SH896: the frosted-surface classes are not in the digest, so a panel could lose its lift "
      + "without the proof noticing")
})

test("SH896: compare names the page and the field that moved", () => {
  const before = { "a.page.tsx": { digest: "aaa", era: "console", chrome: ["Card"], surface: [] } }
  const after = { "a.page.tsx": { digest: "bbb", era: "bento", chrome: ["BentoSection"], surface: [] } }

  const moved = compare(before, after)
  assert.equal(moved.length, 1)
  assert.match(moved[0], /a\.page\.tsx/, "SH896: the report does not name the page")
  assert.match(moved[0], /era console -> bento/, "SH896: the report does not name the era change")
  assert.match(moved[0], /Card.*BentoSection/s, "SH896: the report does not name the chrome change")

  // A survey compared with itself is the baseline case, and it must be silent — a tool that always
  // reports something is a tool nobody reads.
  assert.deepEqual(compare(before, before), [], "SH896: comparing a survey with itself reported a change")

  // Appearing and vanishing are both real events across a migration that adds a parallel route.
  assert.match(compare({}, before)[0], /new \(console, aaa\)/)
  assert.match(compare(before, {})[0], /gone/)
})

test("SH896: every console surface yields a digest, and the survey is reproducible", () => {
  const first = survey()
  const names = Object.keys(first)

  assert.ok(names.length > 40,
    `SH896: the survey covered ${names.length} surfaces — it is reading the wrong tree, and an `
      + "empty survey compares clean against anything")

  // Reproducible in the same process and across a second walk: a digest that drifts between two
  // runs of the same tree makes every `--against` report a lie in both directions.
  assert.deepEqual(survey(), first, "SH896: two surveys of one tree disagreed")

  for (const [name, s] of Object.entries(first)) {
    assert.match(s.digest, /^[0-9a-f]{12}$/, `SH896: ${name} has no digest`)
    assert.ok(s.era === "console" || s.era === "bento", `SH896: ${name} has era ${s.era}`)
  }

  // Not vacuous in the way that matters: the walk must be reading real chrome, or every page would
  // digest to the same empty structure and the tool would be silent for ever.
  const distinct = new Set(Object.values(first).map((s) => s.digest))
  assert.ok(distinct.size > 10,
    `SH896: only ${distinct.size} distinct digest(s) across ${names.length} surfaces — the chrome `
      + "vocabulary is matching almost nothing and the proof is empty")

  console.log(
    `SH896: ${names.length} console surfaces, ${distinct.size} distinct digests, `
      + `${Object.values(first).filter((s) => s.era === "bento").length} on bento`,
  )
})

test("SH896: structureOf keeps chrome in source order and the surface set sorted", () => {
  const s = structureOf(PAGE)
  assert.deepEqual(s.chrome, ["PageHeader", "Card", "CardContent"],
    "SH896: chrome is not recorded in source order")
  // Sorted by codepoint, never by locale: the digest hashes this order, so a locale-dependent
  // comparator would hash one page to two values on two machines.
  assert.deepEqual(s.surface, [...s.surface].sort((a, b) => (a < b ? -1 : a > b ? 1 : 0)),
    "SH896: the surface set is not in codepoint order, so the digest is host-dependent")
  assert.deepEqual(s.surface, ["bg-card", "border", "rounded", "shadow"])
})
