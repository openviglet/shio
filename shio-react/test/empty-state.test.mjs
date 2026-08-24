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
const ADAPTER = join(SRC, "components", "adaptive-empty-state.tsx")

/** The hand-rolled void: a centred `py-16` column, and the muted icon chip inside it. */
const HAND_ROLLED_VOID = /\bpy-16\b[^"]*\btext-center\b/
const MUTED_ICON_CHIP = /\bh-8 w-8 text-muted-foreground\b/

/**
 * The ten pages that still hand-roll it, each named with the task that removes it. **This is the
 * debt**, and the shape is `console-chrome-census.test.mjs`'s `STILL_CONSOLE` for the same reason:
 * a ceiling ("no more than ten") lets one migration pay for the next, while a list fails by name
 * and the diff that shortens it is a diff somebody reviews.
 *
 * ## Why ten are held rather than fixed
 *
 * §LV.6 named two pages and this reader found thirteen. That gap is the whole argument for
 * writing the grep before the fixes (SH322) — but it is not licence to migrate ten pages that
 * belong to four unstarted tasks. Every one of these is a page SH904, SH905, SH906 or SH908
 * rewrites wholesale; converting the empty state now means touching each file twice and reviewing
 * a diff that spans four tasks' worth of surfaces.
 *
 * So each entry names its owner. When that task migrates its pages it deletes its own rows here,
 * and the day the list is empty this constant goes with it.
 */
const STILL_HAND_ROLLED = {
  "app/console/admin/activity/activity.list.page.tsx": "SH906",
  "app/console/admin/api-token/api-token.list.page.tsx": "SH905",
  "app/console/admin/auth-provider/auth-provider.list.page.tsx": "SH905",
  "app/console/admin/exchange-provider/exchange-provider.list.page.tsx": "SH905",
  "app/console/admin/review/review.list.page.tsx": "SH906",
  "app/console/admin/site-webhook/site-webhook.list.page.tsx": "SH905",
  "app/console/admin/tenant/tenant.list.page.tsx": "SH905",
  "app/console/graphql/graphql.page.tsx": "SH908",
  "app/console/universal-editor/editor-sites.page.tsx": "SH907",
}

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
      + " chip, which is the markup AdaptiveEmptyState exists to hold once. Use"
      + " <AdaptiveEmptyState icon title description action />: it renders this block in console"
      + " chrome and BentoEmptyState in bento, so the page also stops being one that looks"
      + " finished while rendering console markup under the bento shell.")

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
 * nothing anywhere reports a clean tree and an unused regex identically. So: the adapter itself
 * must still carry both halves. When it stops, this pattern has gone stale and every page it was
 * meant to catch is invisible to it.
 */
test("SH903: the pattern still describes the markup it is looking for", () => {
  const adapter = stripComments(readFileSync(ADAPTER, "utf8"))
  assert.match(adapter, HAND_ROLLED_VOID,
    "SH903: the adapter's console branch no longer renders a centred py-16 void, so the pattern"
      + " above matches nothing and the lint is vacuous. Re-derive it from what the branch renders"
      + " now, or the next hand-rolled copy lands unseen.")
  assert.match(adapter, MUTED_ICON_CHIP,
    "SH903: the adapter's console branch no longer renders the muted 8x8 icon chip — same"
      + " staleness, other half.")
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
    readFileSync(join(SRC, "app", "console", "content", "search.page.tsx"), "utf8"))

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
