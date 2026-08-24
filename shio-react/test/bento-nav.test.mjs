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
 * SH889 — every Shio surface is declared once, and both readers take it from there.
 *
 * ## What is actually at risk
 *
 * Not "is there a nav array". The risk §LIV.4 names is **two arrays**: the rail and the command
 * palette both need to know what surfaces exist, what they are called and what gates them, and
 * declared twice they disagree within a release rather than at some later reckoning. So the
 * assertions here are about *one* declaration reaching both, and about the shape being settled —
 * because this is the file each of Block AR's fourteen surface tasks appends to, and a shape
 * settled after five of them have shipped is a shape five tasks have to be reopened for.
 *
 * ## Read as text, on purpose
 *
 * `bento-nav.ts` imports `@/hooks/use-discovery` and `@/app/routes.const`, so importing it here
 * would need Vite's alias resolution and a DOM for the hook. The properties worth asserting are
 * all structural — an id, two route fields, a key per item — and they are visible in the source.
 * Where behaviour rather than structure is at stake (the multi-tenant filter), the *logic* is
 * asserted rather than the data, by re-implementing the one-line predicate against the declared
 * items; that is a copy, and the test says so, but a copy of one line beats a DOM.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")

const navSource = stripComments(readFileSync(join(SRC, "app", "bento", "bento-nav.ts"), "utf8"))
const shellSource = stripComments(readFileSync(join(SRC, "app", "bento", "bento.root.page.tsx"), "utf8"))

/** Every `{ id: "...", ... }` entry in the ITEMS array, as a flat object of its string fields. */
function items() {
  const block = /const ITEMS: ShioNavItem\[\] = \[([\s\S]*?)\n\]/.exec(navSource)
  assert.ok(block, "SH889: could not find the ITEMS array in bento-nav.ts")
  return [...block[1].matchAll(/\{([^}]*)\}/g)].map(([, body]) => {
    const entry = {}
    for (const [, key, value] of body.matchAll(/(\w+):\s*"([^"]*)"/g)) entry[key] = value
    entry.raw = body
    return entry
  })
}

const locale = (name) => JSON.parse(
  readFileSync(join(SRC, "i18n", "locales", name, "nav.json"), "utf8"))

test("SH889: one declaration reaches both readers", () => {
  // The rail takes groups, the palette takes the flattened items, and both come from the same
  // module. A palette built from its own array is the defect this task exists to prevent, and it
  // would look completely fine — right up to the release where one of them lists a surface the
  // other does not.
  assert.match(shellSource, /groups=\{groups\}/,
    "SH889: BentoNavRail is no longer given the declared groups")
  assert.match(shellSource, /items=\{items\}/,
    "SH889: BentoCommandPalette is no longer given the declared items")
  assert.match(shellSource, /useBentoNav\b/, "SH889: the shell does not read the nav declaration")
  assert.match(shellSource, /useBentoNavItems\b/,
    "SH889: the palette's list does not come from the nav declaration")

  assert.doesNotMatch(shellSource, /titleKey|fallbackRoute|descriptionKey/,
    "SH889: the shell spells nav entry fields itself, which means a second list is forming in the"
      + " one file that should only ever consume one.")
})

test("SH889: every declared surface carries the fields both readers render", () => {
  const declared = items()

  assert.ok(declared.length >= 15,
    `SH889: only ${declared.length} surfaces are declared. The console sidebar renders 17; a nav`
      + " that lists fewer is a palette a curator cannot find things in, which is worse than no"
      + " palette because they will stop opening it.")

  const REQUIRED = ["id", "titleKey", "descriptionKey", "section", "tone"]
  for (const item of declared) {
    for (const field of REQUIRED) {
      assert.ok(item[field],
        `SH889: a nav entry is missing ${field}.\n  ${item.raw.trim().slice(0, 120)}\n`
          + "  Every field here is rendered by something: titleKey and descriptionKey by both the"
          + " palette row and the hub tile, tone by the tile, section by the rail's grouping.")
    }
    // fallbackRoute is a template literal for two entries, so it is matched in the raw body.
    assert.match(item.raw, /fallbackRoute:/,
      `SH889: ${item.id} declares no fallbackRoute. bentoNavTarget() falls back to it while a`
        + " surface has not moved, so an entry without one navigates nowhere for the whole of"
        + " Block AR.")
  }

  const ids = declared.map((item) => item.id)
  assert.deepEqual([...new Set(ids)], ids,
    "SH889: two nav entries share an id, so one of them silently wins in every keyed render")
})

test("SH889: a migration is one line — bentoRoute is the only field that flips", () => {
  const declared = items()
  const moved = declared.filter((item) => /bentoRoute:/.test(item.raw))

  // Printed on every run: this is the migration's own progress bar, and it is the number Block
  // AR moves. Zero today, seventeen when SH909 can retire the console tree.
  console.log(`SH889: ${moved.length}/${declared.length} surfaces point at a bento route`)

  // The invariant, which holds vacuously today and stops being vacuous with the first migration:
  // an entry that has moved must keep the route it moved *from*. `bentoNavTarget` prefers
  // `bentoRoute`, so dropping the fallback looks harmless — until the bento route is reverted,
  // which is the entire reason SH888 built a parallel tree instead of migrating in place.
  for (const item of moved) {
    assert.match(item.raw, /fallbackRoute:/,
      `SH889: ${item.id} has a bentoRoute and no fallbackRoute. Reversibility is what the parallel`
        + " tree buys; an entry that cannot point back at the console page is a slice that cannot"
        + " be reverted without editing this file again.")
  }

  // And the entries are typed as the package's contract, which is what makes `bentoRoute`
  // available at all. A local shape that merely resembled it would take the field and hand the
  // rail something it does not read.
  assert.match(navSource, /interface ShioNavItem extends BentoNavItem/,
    "SH889: Shio's entry type no longer extends the package's. `bentoRoute` and `fallbackRoute`"
      + " are the package's fields and bentoNavTarget() is what reads them — a parallel local"
      + " shape would compile and navigate nowhere.")
})

test("SH889: visibility is Shio's, and it is applied before the package sees the list", () => {
  assert.match(navSource, /requires\?: "multiTenant"/,
    "SH889: ShioNavItem no longer carries `requires`. BentoNavItem is deliberately the render"
      + " contract — the package cannot read a feature model — so a gate dropped from Shio's own"
      + " type is a gate that stops existing rather than one that moves.")

  const declared = items()
  const gated = declared.filter((item) => /requires:/.test(item.raw))
  assert.deepEqual(gated.map((item) => item.id), ["tenants"],
    "SH889: the set of gated surfaces changed. SH46's finding was that an ungated tenants entry"
      + " offers every single-tenant install a page for managing tenants it does not have —"
      + " `shio.multi-tenant` is off by default.")

  // The filter itself, re-implemented from the declaration's one line. A copy, and worth it: the
  // alternative is a DOM and Vite's alias resolution to exercise a single predicate.
  const visible = (multiTenant) =>
    declared.filter((item) => !/requires: "multiTenant"/.test(item.raw) || multiTenant)
  assert.equal(visible(true).length, declared.length,
    "SH889: a multi-tenant instance must see every declared surface")
  assert.equal(visible(false).length, declared.length - 1,
    "SH889: a single-tenant instance must see one fewer — the tenants page")
})

test("SH889: every key the nav names exists in both locales", () => {
  const en = locale("en")
  const pt = locale("pt")
  const declared = items()

  assert.deepEqual(Object.keys(en).sort(), Object.keys(pt).sort(),
    "SH889: en and pt nav bundles describe different key sets")

  // Only the keys this file mints. A titleKey is deliberately one the console sidebar already
  // renders — the authoring contract's rule, that the same entity in two chromes is the same
  // entity — so those live in their own bundles and i18n-parity.test.mjs is what watches them.
  const minted = [
    ...declared.map((item) => item.descriptionKey),
    ...[...navSource.matchAll(/descriptionKey: "(nav\.desc\.section\.[^"]+)"/g)].map(([, k]) => k),
  ]
  const missing = [...new Set(minted)].filter((key) => !(key in en))

  assert.deepEqual(missing, [],
    "SH889: a description key the nav declares has no translation, so the palette row and the hub"
      + " tile render the key itself. Visible, but only to somebody looking at that surface in"
      + " that language — which is why this is asserted rather than left to be noticed.")
})

test("SH889: the reader is not vacuous — it parses real entries with real fields", () => {
  const declared = items()
  assert.ok(declared.length > 0, "SH889: the ITEMS parse yielded nothing, so every loop above is empty")

  const sites = declared.find((item) => item.id === "sites")
  assert.ok(sites, "SH889: the `sites` entry is gone, or the parse no longer reads ids")
  assert.equal(sites.titleKey, "content.sites",
    "SH889: `sites` no longer reuses the sidebar's own key. Minting a parallel namespace for a"
      + " surface that already has one is what the authoring contract tells consumers not to do.")
})
