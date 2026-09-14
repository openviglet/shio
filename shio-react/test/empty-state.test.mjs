/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { readdirSync, readFileSync } from "node:fs"
import { dirname, join, relative } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { stripComments } from "./support/source-text.mjs"

/**
 * SH903 — the "nothing here" block, which existed three times before anyone counted.
 *
 * ## Why a lint rather than three edits
 *
 * §LV.6 asked for a shared empty state on three pages, and three edits would have delivered that
 * and nothing else. The reason the duplication happened is that **nothing objected**: each of the
 * three was written months apart by someone who could not see the other two, and each was right
 * in isolation. The fourth would have been written the same way.
 *
 * SH322's rule is that when a task says "and whatever a grep turns up", the grep is the
 * deliverable and it lands *before* the fixes. This is that grep. It ran first, and it is what
 * established the count was three rather than the two §LV.6 named — the static-file page had one
 * too, wrapped in a component with a second axis the other two never grew.
 *
 * ## What it matches, and why not the class names
 *
 * The tell is not any single class — it is the **pairing** of a centred `py-16` void with the
 * muted 8×8 icon that every one of the three used. A page that legitimately centres something
 * tall does not also render that icon chip; a page that renders the icon chip is drawing an empty
 * state whatever it calls the component. Matching the pair rather than either half is what keeps
 * this from firing on the load spinner or on a centred dialog.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")
const ADAPTER = join(SRC, "components", "empty-state.tsx")

/** The hand-rolled void: a centred `py-16` column, and the muted icon chip inside it. */
const HAND_ROLLED_VOID = /\bpy-16\b[^"]*\btext-center\b/
const MUTED_ICON_CHIP = /\bh-8 w-8 text-muted-foreground\b/

/**
 * The pages that still hand-roll it, each named with the task that removes it. **This is the
 * debt**, and the shape is `console-chrome-census.test.mjs`'s `STILL_CONSOLE` for the same reason:
 * a ceiling lets one migration pay for the next, while a list fails by name and the diff that
 * shortens it is a diff somebody reviews.
 *
 * ## Why they are held rather than fixed
 *
 * §LV.6 named two pages and this reader found thirteen. That gap is the whole argument for
 * writing the grep before the fixes (SH322) — but it was not licence to migrate ten pages that
 * belonged to four unstarted tasks, touching each file twice and reviewing a diff spanning four
 * tasks' worth of surfaces.
 *
 * ## Re-attributed once, and why that is the mechanism working
 *
 * Five of these were filed against SH905 on the assumption it would convert the console lists.
 * It did not: those five became **new bento pages beside** the console ones (SH899's copy shape,
 * which is right for a list that is forty lines of configuration), so the console originals are
 * untouched and go when the console route tree does. They name **SH909** now. A debt row whose
 * owner shipped without paying it is a row that has to be re-owned in the open, not quietly
 * inherited — otherwise the list stops meaning what it says on the first task that surprises it.
 */
/*
 * SH910 — and all five went the way the row above says they would.
 *
 * They named SH909 because the console originals were untouched and would go with the route tree.
 * They did: the five files are deleted, the bento lists beside them use the shared
 * `EmptyState`, and this debt is **empty**.
 *
 * Empty is the interesting state for a held list, because it is the one where somebody deletes the
 * reader. Do not. The rule it enforces — nobody writes the `py-16 text-center` void a second time —
 * is what the list was always for, and the census below runs over every `.tsx` under `src/`. A new
 * page hand-rolling that markup fails against an empty declaration exactly as it would have failed
 * against a full one.
 */
const STILL_HAND_ROLLED = {}

/** Every `.tsx` under `src/`, minus the adapter itself — it is allowed to hold the markup once. */
function sources() {
  const out = []
  const walk = (dir) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const full = join(dir, entry.name)
      if (entry.isDirectory()) walk(full)
      else if (entry.name.endsWith(".tsx") && full !== ADAPTER) out.push(full)
    }
  }
  walk(SRC)
  return out
}

test("SH903: nobody hand-rolls the empty state a second time", () => {
  const offenders = sources()
    .filter((file) => {
      const source = stripComments(readFileSync(file, "utf8"))
      return HAND_ROLLED_VOID.test(source) && MUTED_ICON_CHIP.test(source)
    })
    .map((f) => relative(SRC, f).replace(/\\/g, "/"))

  const known = Object.keys(STILL_HAND_ROLLED)
  console.log(`SH903: ${offenders.length} pages hand-roll the empty state, ${known.length} declared`)

  assert.deepEqual(
    offenders.filter((f) => !(f in STILL_HAND_ROLLED)),
    [],
    "SH903: these pages draw their own empty state — a centred py-16 void with a muted 8x8 icon"
      + " chip, which is the markup EmptyState exists to hold once. Use"
      + " <EmptyState icon title description action />: it renders BentoEmptyState, so the"
      + " page also stops being one that looks finished while rendering its own markup under the"
      + " bento shell.")

  /*
   * And the other direction, which is the half a debt list usually forgets. A page that no longer
   * hand-rolls it must leave this list in the same diff — otherwise the list outlives the debt,
   * every future reader trusts a number that is too high, and the day it reaches zero nobody
   * notices because it never does.
   */
  assert.deepEqual(
    known.filter((f) => !offenders.includes(f)),
    [],
    "SH903: these are declared as still hand-rolling an empty state and no longer do — either the"
      + " owning task migrated them and left the row behind, or the file moved. Delete the rows:"
      + " a debt list that is not shortened by the work that pays it is a list that only grows.")
})

/**
 * The reader is worth nothing if the pattern no longer describes the markup — a lint that matches
 * nothing anywhere reports a clean tree and an unused regex identically.
 *
 * SH959 — asked of the markup itself now, not of the adapter. This used to require that the
 * adapter's console branch still carried both halves, which was a live specimen for as long as
 * that branch existed; SH959 deleted it, so `EmptyState` renders `BentoEmptyState` and nothing in
 * `src/` holds the pattern on purpose. The block a hand-rolled copy writes is the specimen instead,
 * and the control is the adapter, which must no longer match.
 */
test("SH903: the pattern still describes the markup it is looking for", () => {
  const handRolled = `
    <div className="flex flex-col items-center justify-center py-16 text-center">
      <div className="rounded-full bg-muted p-4 mb-4">
        <Icon className="h-8 w-8 text-muted-foreground" />
      </div>
    </div>`
  assert.match(handRolled, HAND_ROLLED_VOID,
    "SH903: the pattern no longer matches a centred py-16 void, so the lint is vacuous and the next"
      + " hand-rolled copy lands unseen.")
  assert.match(handRolled, MUTED_ICON_CHIP,
    "SH903: the pattern no longer matches the muted 8x8 icon chip — same staleness, other half.")

  const adapter = stripComments(readFileSync(ADAPTER, "utf8"))
  assert.ok(!(HAND_ROLLED_VOID.test(adapter) && MUTED_ICON_CHIP.test(adapter)),
    "SH959: EmptyState hand-rolls the block again. It renders BentoEmptyState and nothing else;"
      + " a second rendering is the console branch coming back.")
})

/**
 * SH903 — search has **three** states, and the third is the one it opens in.
 *
 * Before a query has been run it rendered nothing at all below the form. §LV.6 called search a
 * surface "where an empty result is normal", and it is more than that: an empty result is normal
 * *and* so is not having asked yet, and the two want different words. "No results for that" on a
 * page nobody has searched reports a failure that did not happen.
 *
 * The live check is what found it — `bento-search` opens with no query, so the probe landed on
 * exactly the state that rendered no surface. This holds the fix from the other side: the page
 * has to distinguish "not searched" from "searched, nothing matched" by more than a falsy check.
 */
test("SH903: an unsearched search page says so, and says something else than a failed one", () => {
  const search = stripComments(
    readFileSync(join(SRC, "app", "content", "search.page.tsx"), "utf8"))

  assert.match(search, /content\.searchPromptTitle/,
    "SH903: the search page lost its unsearched state. It opens with no query, so that branch is"
      + " the first thing a curator sees — and rendering nothing there is the shape a broken page"
      + " has.")
  assert.notEqual(
    /content\.searchPromptTitle/.source, /content\.searchNoResults\b/.source,
    "SH903: the two states must not share a message")
  assert.match(search, /content\.searchNoResults\b/,
    "SH903: the search page lost its no-results state, which is the one §LV.6 actually named.")

  for (const locale of ["en", "pt"]) {
    const bundle = JSON.parse(
      readFileSync(join(SRC, "i18n", "locales", locale, "content.json"), "utf8"))
    for (const key of ["content.searchPromptTitle", "content.searchPromptHint"]) {
      assert.ok(bundle[key],
        `SH903: ${locale} is missing ${key}. Both locales take a key in the same commit or the`
          + " console renders the key name to whoever is not reading English.")
    }
  }
})
