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
 *
 * ## What it measures since SH960, and what it does not
 *
 * The chrome a page composes, in source order, and the frosted-surface classes it carries. **Not an
 * era**: that field classified pages by whether they imported the bento subpath, had no subject after
 * SH909, and labelled 33 of 45 surfaces `console` on the day it went. **Not pixels**,
 * and not whether a page looks right — a matching digest says the same components were composed in
 * the same order with the same surface vocabulary, which is the part a refactor can break silently.
 *
 * SH960 also deleted SH886's page census. Every page it declared carried
 * an empty list and nothing was declared migrated, so it asserted an absence the vocabulary census
 * (SH957) and this digest both walk; its one live probe — a page composing `Card` again — is the
 * last test below, on this walk, so there is one reader of that absence rather than two that can
 * disagree about it.
 */

/** A minimal one-chrome page, written out rather than read, so the noise cases can be exact. */
const PAGE = `
import { PageHero } from "@/components/page-hero"
import { Panel } from "@/components/panel"

export default function Thing() {
  const handleSave = () => save()
  return (
    <>
      <PageHero title="Thing" />
      <Panel className="rounded-2xl border bg-card shadow-sm">
        <table className="p-0">{rows}</table>
      </Panel>
    </>
  )
}
`

test("SH896: the digest is stable under changes that are not about structure", () => {
  const base = digestOf(PAGE)

  // Reformatting. A reviewer who reflows a file has not changed what renders.
  assert.equal(digestOf(PAGE.replace(/\n\s+/g, "\n ")), base, "SH896: reindenting moved the digest")

  // A comment, including one that names chrome. If a commented-out <Panel> counted, every page
  // documenting what it replaced would read as still rendering it.
  assert.equal(
    digestOf(PAGE.replace("return (", "// was: <Panel><BentoSection/></Panel>\n  return (")),
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

  // One component swapped for another.
  assert.notEqual(digestOf(PAGE.replace("<Panel ", "<BentoSection ")), base,
    "SH896: swapping Panel for BentoSection left the digest alone — the tool would go green across "
      + "the change it exists to witness")

  // Order, not just membership. A hero after the panel is a different page.
  const reordered = PAGE.replace("<PageHero title=\"Thing\" />\n      ", "")
    .replace("</Panel>", "</Panel>\n      <PageHero title=\"Thing\" />")
  assert.notEqual(digestOf(reordered), base, "SH896: moving the hero did not move the digest")

  // And the surface vocabulary §LIV.11 names — the frosted panel and its lift.
  assert.notEqual(digestOf(PAGE.replace("shadow-sm", "backdrop-blur-md")), base,
    "SH896: the frosted-surface classes are not in the digest, so a panel could lose its lift "
      + "without the proof noticing")
})

test("SH960: an import is not structure — the digest has no era", () => {
  // The inverse of what SH896 asserted about the era: importing the package without composing
  // anything from it changes nothing a page renders, so it must not move the digest.
  assert.equal(
    digestOf(`import { BentoHero } from "@viglet/viglet-design-system/bento"\n${PAGE}`),
    digestOf(PAGE),
    "SH960: an unused bento import moved the digest. That is the era field back: a classification"
      + " by import that labelled 33 of 45 surfaces `console` in a console with one chrome.",
  )
  assert.deepEqual(Object.keys(structureOf(PAGE)).sort(), ["chrome", "surface"],
    "SH960: the structure carries a field beyond chrome and surface. Say in surface-digest.mjs what"
      + " it measures and what it does not before a saved survey starts comparing it.")
})

test("SH896: compare names the page and the field that moved", () => {
  const before = { "a.page.tsx": { digest: "aaa", chrome: ["Panel"], surface: [] } }
  const after = { "a.page.tsx": { digest: "bbb", chrome: ["BentoSection"], surface: [] } }

  const moved = compare(before, after)
  assert.equal(moved.length, 1)
  assert.match(moved[0], /a\.page\.tsx/, "SH896: the report does not name the page")
  assert.match(moved[0], /Panel.*BentoSection/s, "SH896: the report does not name the chrome change")
  assert.doesNotMatch(moved[0], /\bera\b/, "SH960: the report still speaks of an era")

  // A survey compared with itself is the baseline case, and it must be silent — a tool that always
  // reports something is a tool nobody reads.
  assert.deepEqual(compare(before, before), [], "SH896: comparing a survey with itself reported a change")

  // Appearing and vanishing are both real events.
  assert.match(compare({}, before)[0], /new \(aaa\)/)
  assert.match(compare(before, {})[0], /gone/)
})

/**
 * The surfaces that compose no chrome at all, each by design. **SH960's measurement**: with the old
 * vocabulary 33 of 45 surfaces digested nothing, because most pages compose this console's
 * seams rather than the package; with the seams named, one does — the visual editor, which owns the
 * viewport and draws its own frame (SH907). A page joining it is a page the proof cannot see.
 */
const NO_CHROME = ["console/universal-editor/visual-editor.page.tsx"]

test("SH896: every surface yields a digest, the survey is reproducible, and it sees the pages", () => {
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
  }

  const blind = names.filter((name) => first[name].chrome.length === 0)
  assert.deepEqual(blind, NO_CHROME,
    "SH960: the set of surfaces the digest records no chrome for changed. A page in `+ actual`"
      + " composes something the CHROME vocabulary does not name — add the component, or the proof"
      + " will stay green across any change to that page's structure.")

  const distinct = new Set(Object.values(first).map((s) => s.digest))
  assert.ok(distinct.size > 20,
    `SH896: only ${distinct.size} distinct digest(s) across ${names.length} surfaces — the chrome `
      + "vocabulary is matching almost nothing and the proof is empty")

  console.log(
    `SH896: ${names.length} surfaces, ${distinct.size} distinct digests, `
      + `${blind.length} composing no chrome`,
  )
})

test("SH960: no surface composes a Card, the one retired component whose module still resolves", () => {
  // SH910 kept this as the census's only live probe: `@/components/ui/card` is still imported by the
  // login form, so a page reaching for it is possible, and it renders a surface outside the bento
  // vocabulary. Folded here from the deleted census, onto the walk that already records it.
  const withCard = Object.entries(survey())
    .filter(([, s]) => s.chrome.some((name) => name.startsWith("Card")))
    .map(([name]) => name)
  assert.deepEqual(withCard, [],
    "SH960: a page composes shadcn's Card. The console has one chrome; a panel is Panel or"
      + " AdaptiveSectionCard, and a section is BentoSection.")

  assert.deepEqual(structureOf(`<Card className="border"><CardContent /></Card>`).chrome,
    ["Card", "CardContent"],
    "SH960: the vocabulary no longer records Card, so the check above can never fire")
})

test("SH896: structureOf keeps chrome in source order and the surface set sorted", () => {
  const s = structureOf(PAGE)
  assert.deepEqual(s.chrome, ["PageHero", "Panel"],
    "SH896: chrome is not recorded in source order")
  // Sorted by codepoint, never by locale: the digest hashes this order, so a locale-dependent
  // comparator would hash one page to two values on two machines.
  assert.deepEqual(s.surface, [...s.surface].sort((a, b) => (a < b ? -1 : a > b ? 1 : 0)),
    "SH896: the surface set is not in codepoint order, so the digest is host-dependent")
  assert.deepEqual(s.surface, ["bg-card", "border", "rounded", "shadow"])
})
