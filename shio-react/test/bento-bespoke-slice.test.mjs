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
import { findElements } from "./support/jsx-elements.mjs"
import { stripComments } from "./support/source-text.mjs"

/**
 * SH906 — the review queue, the activity log and the marketplace.
 *
 * §LV.9's prediction held: two of the three are "read-mostly surfaces, which is the page shape the
 * package covers least well — a hero over a table is not a mosaic". It said to "let it have" a
 * plain section, and that is what they got. The third genuinely is a mosaic and kept its grid.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")
const admin = (rel) => stripComments(readFileSync(join(SRC, "app", "admin", rel), "utf8"))

const REVIEW = admin("review/review.list.page.tsx")
const ACTIVITY = admin("activity/activity.list.page.tsx")
const MARKETPLACE = admin("marketplace/marketplace.list.page.tsx")
const APP = stripComments(readFileSync(join(SRC, "App.tsx"), "utf8"))

test("SH906: the curator's approval surface follows the chrome", () => {
  /*
   * The one stated done-when: "the review queue no longer looks like the old console". It is the
   * curator's half of the agent-native design — where they go when they do not trust what an
   * agent did — and the last place a half-migrated console should show.
   */
  assert.match(REVIEW, /PageHero/,
    "SH906: the review queue renders a console PageHeader again, so the curator's approval surface"
      + " is the one page that still looks like the old console under the bento shell.")
  assert.doesNotMatch(REVIEW, /@\/components\/ui\/card/,
    "SH906: the review queue imports Card again — a raw Card is a panel that cannot follow the"
      + " provider, so the session list renders console chrome in bento.")
  assert.match(REVIEW, /Panel/,
    "SH906: the session list lost its adaptive panel. It has no header — the hero above names the"
      + " page — so it is Panel and not AdaptiveSectionCard (SH902).")
})

test("SH906: a hero and a section heading do not say the same thing twice", () => {
  /*
   * All three pages rendered `<h2>{t("x.title")}</h2>` with `{t("x.description")}` underneath,
   * directly below a PageHeader already showing the title. The bento hero takes both, so keeping
   * the pair would name the page twice with nothing between them — the duplication SH898 and
   * SH899 each walked into twice.
   */
  for (const [name, source, ns] of [
    ["review", REVIEW, "review"],
    ["activity", ACTIVITY, "activity"],
  ]) {
    assert.doesNotMatch(source, new RegExp(`<h2[^>]*>\\{t\\("${ns}\\.title"\\)\\}`),
      `SH906: the ${name} page repeats its own title in an h2 directly under the hero that already`
        + " shows it. The hero takes `subtitle`; the description belongs there.")
    assert.match(source, new RegExp(`subtitle=\\{t\\("${ns}\\.description"\\)\\}`),
      `SH906: the ${name} page dropped its description instead of moving it into the hero — the`
        + " console header had no slot for it, which is why it was in an h2 block at all.")
  }
})

test("SH906: a catalogue tile is a button, not a div that listens for clicks", () => {
  /*
   * The marketplace's tiles were `<Card onClick={…}>`: not focusable, not announced, no key
   * handler — **the whole catalogue was mouse-only**. This is SH922's sweep from the other side.
   * SH922 found interactive things nested inside interactive things; this is a thing that looks
   * interactive and is not one, which no amount of nesting analysis finds.
   *
   * Asserted as "no element that is not a button carries onClick inside the grid", which is the
   * property rather than the fix: wrapping it in a button, making it a link, or giving the card
   * real button semantics all pass.
   */
  const clickable = findElements(MARKETPLACE, "Panel").filter((el) => /onClick=/.test(el.raw))
  assert.deepEqual(clickable.map((el) => el.raw.slice(0, 60)), [],
    "SH906: a marketplace tile carries onClick on the panel itself. A div with a handler is not"
      + " focusable and not announced — put the handler on a real <button> inside the panel.")
  assert.match(MARKETPLACE, /<button\s+[\s\S]{0,120}?type="button"[\s\S]{0,200}?onClick=\{\(\) => open\(item\)\}/,
    "SH906: the marketplace tile no longer opens its detail from a real button, so the catalogue"
      + " is reachable by pointer only.")
})

test("SH906: all three are mounted under the bento tree", () => {
  for (const path of ["admin/review", "admin/activity", "admin/marketplace"]) {
    assert.match(APP, new RegExp(`path="${path}"`),
      `SH906: ${path} is not mounted under /bento — a migrated page nothing routes to is`
        + " indistinguishable from a page that was never migrated.")
  }
})
