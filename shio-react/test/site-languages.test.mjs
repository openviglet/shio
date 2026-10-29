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
import {
  declarationOf, draftOf, offeredLocales, prune, setFallback, setLabel, toggleLocale,
} from "../src/lib/site-languages.ts"

/**
 * SH1056 — the console half of a site's locale policy: the card that edits it, and the menu that
 * stops offering forty-five languages on every post.
 *
 * @scans code
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const read = (...path) => readFileSync(join(ROOT, ...path), "utf8")

const CATALOGUE = ["en", "es", "fr", "pt", "pt-br"].map((initials) => ({ initials, en: initials, pt: initials }))
const DECLARED = { defaultLocale: "pt-br", enabled: ["pt-br", "en"], fallback: {}, declared: true }
const DERIVED = { defaultLocale: "en", enabled: ["en"], fallback: {}, declared: false }

test("SH1056: a declared policy is what the translations menu offers, in its order", () => {
  assert.deepEqual(offeredLocales(CATALOGUE, DECLARED).map((l) => l.initials), ["pt-br", "en"],
    "SH1056: the site said which languages it publishes — offering the rest is offering to write "
      + "pages nothing will link to")
})

test("SH1056: an undeclared site is offered the whole catalogue, not the languages it already has", () => {
  assert.equal(offeredLocales(CATALOGUE, DERIVED).length, CATALOGUE.length,
    "SH1056: narrowing to the derived set would stop a one-language site ever adding a second")
  assert.equal(offeredLocales(CATALOGUE, null).length, CATALOGUE.length,
    "SH1056: a policy that failed to load must not empty the menu")
})

test("SH1056: the draft never composes a policy the server would refuse", () => {
  const draft = { ...draftOf(DECLARED), fallback: { en: "pt-br" } }

  const withoutDefault = toggleLocale(draft, "pt-br", false)
  assert.equal(withoutDefault.defaultLocale, "en",
    "SH1056: a default outside the enabled set is not a default, so disabling it re-picks one")
  assert.deepEqual(withoutDefault.fallback, {},
    "SH1056: a fallback pointing at a locale the site no longer offers is a redirect to nothing")

  const added = toggleLocale(draft, "fr", true)
  assert.deepEqual(added.enabled, ["pt-br", "en", "fr"],
    "SH1056: a newly offered locale goes last — a switcher's order is a decision, not an alphabet")

  assert.deepEqual(prune({ ...draft, fallback: { "pt-br": "en" } }).fallback, {},
    "SH1056: a rule from the default is dropped — every chain already ends there")
  assert.deepEqual(setFallback(added, "fr", "").fallback, { en: "pt-br" },
    "SH1056: clearing a rule sends that locale's missing pages back to the default")
})

test("SH1281: a site's switcher labels survive a console save, and leave with their locale", () => {
  const draft = draftOf({ ...DECLARED, labels: { "pt-br": "Português (Brasil)" } })
  assert.deepEqual(declarationOf(draft).labels, { "pt-br": "Português (Brasil)" },
    "SH1281: the policy is replaced whole, so a save that left labels out would erase them")
  assert.deepEqual(setLabel(draft, "en", "EN").labels, { "pt-br": "Português (Brasil)", en: "EN" })
  assert.deepEqual(setLabel(draft, "pt-br", " ").labels, {},
    "SH1281: a blank label clears it, so the language's own name comes back")
  assert.deepEqual(toggleLocale(draft, "pt-br", false).labels, {},
    "SH1281: a label for a locale the site no longer offers is one the server refuses")
})

test("SH1056: what the card sends is the document site.upsert takes", () => {
  assert.deepEqual(declarationOf(draftOf(DECLARED)),
    { default: "pt-br", enabled: ["pt-br", "en"], fallback: {} },
    "SH1056: one vocabulary on both doors — `default`, as the agent writes it")
})

test("SH1056: the menu reads the post's site and narrows through the one rule", () => {
  const MENU = read("src", "components", "post-translations.menu.tsx")
  assert.match(MENU, /offeredLocales\(locales, policy\)/u,
    "SH1056: the menu offers what the rule returns, not the raw catalogue")
  /*
   * SH1152 moved this read into `useSiteLocalePolicy`, because `site.edit.page.tsx` asks for the same
   * answer. The claim is the same and is followed rather than relaxed: the menu narrows by **the
   * post's own site**, so it must pass that site's id, and the hook must read that site's policy.
   * Without both, the menu would offer every locale the instance has.
   */
  assert.match(MENU, /useSiteLocalePolicy\(siteId\)/u,
    "SH1056: the menu reads a policy that is not this post's site's")
  assert.match(read("src", "hooks", "use-catalogue.ts"),
    /queryFn: \(\{ signal \}\) => siteService\.locales\(siteId \?\? "", \{ signal \}\)/u,
    "SH1056: `useSiteLocalePolicy` no longer reads the site's own declared languages, so what the "
      + "menu narrows by is no longer the policy")
  assert.match(read("src", "app", "content", "post.edit.page.tsx"),
    /<PostTranslationsMenu postId=\{postId\} siteId=\{post\.shSite\?\.id \?\? post\.shFolder\?\.shSite\?\.id\}/u,
    "SH1056: without the site the menu cannot know the policy and offers everything")
})

test("SH1056: the card is part of the one save, and writes only when it moved", () => {
  const PAGE = read("src", "app", "content", "site.edit.page.tsx")
  const form = /<form id="site-edit" onSubmit=\{onSubmit\}>([\s\S]*?)<\/form>/u.exec(PAGE)?.[1] ?? ""
  assert.match(form, /<SiteLanguagesSection/u,
    "SH1056: inside the form the shell's Save submits (SH899), not a second button")
  assert.match(PAGE, /if \(languages && languagesDirty\)/u,
    "SH1056: an untouched card must not turn a derived policy into a declared copy of itself")
  assert.match(PAGE, /service\.declareLocales\(site\.id, declarationOf\(languages\)\)/u)
  assert.match(PAGE, /service\.clearLocales\(site\.id\)/u,
    "SH1056: unticking 'declare' goes back to the content's languages rather than saving an empty list")
})

test("SH1056: every string the card and the menu ask for exists in both locales", () => {
  const keys = [
    "content.siteLanguages", "content.siteLanguagesDeclare", "content.siteLanguagesDeclaredHint",
    "content.siteLanguagesDerivedHint", "content.siteLanguagesEnabled", "content.siteLanguagesDefault",
    "content.siteLanguagesFallback", "content.siteLanguagesFallbackHint",
    "content.siteLanguagesFallbackDefault", "content.translationsUndeclared", "content.defaultLocale",
  ]
  for (const locale of ["en", "pt"]) {
    const strings = JSON.parse(read("src", "i18n", "locales", locale, "content.json"))
    for (const key of keys) assert.ok(strings[key], `SH1056: ${locale} is missing ${key}`)
  }
})

test("SH1062: the board asks the connected agent through a request on the draft it creates", () => {
  const BOARD = read("src", "app", "content", "languages.page.tsx")
  // The two existing moves, in order: the missing draft, then the request note on it. The draft is
  // what carries the locale and the translation group to the agent, so no new note kind is needed.
  assert.match(BOARD, /existing \?\? \(await unifiedService\.translate\(sourceId, locale\)\)\.id/u,
    "SH1062: a missing cell must create its draft before asking, or the request has no page to hang on")
  assert.match(BOARD, /sendRequest\(target, body, t\)/u,
    "SH1062: the ask is not the dock's own request note, so the answer would not arrive in the dock")
  assert.match(BOARD, /const askButton = [^\n]*agentAvailable &&/u,
    "SH1062: the ask is offered with nobody around to answer it — SH1019's rule, one surface along")
  assert.match(BOARD, /if \(value\.requested\)/u,
    "SH1062: a requested draft reads as current, so the board says the work is done before it starts")
  // A row and a column are the same ask over many cells: one draft and one note each, and a cell
  // already asked for, current, or requested is not asked again.
  assert.match(BOARD, /if \(value\?\.requested\) return \[\]/u,
    "SH1062: a row or column ask re-asks cells already waiting on the agent, doubling its work")
  assert.match(BOARD, /askManyButton\(columnTargets\(locale\)/u, "SH1062: a locale column cannot be asked for")
  // SH1231 — the row ask is the table's row menu and selection bar now, over one row or many.
  assert.match(BOARD, /rows\.flatMap\(rowTargets\)/u, "SH1062: a row cannot be asked for")
  assert.match(BOARD, /rowActions=\{agentAvailable \? \[openPage, askRows\] : \[openPage\]\}/u,
    "SH1062: the row ask is offered with nobody around to answer it")
  const WORKSPACE = read("src", "app", "content", "translate.page.tsx")
  assert.match(WORKSPACE, /sendRequest\(postId, t\("content\.coverageAskWrite"/u,
    "SH1062: the translation workspace cannot ask the agent, so a translator has to go back to the board")
  assert.match(WORKSPACE, /agentAvailable && source &&/u,
    "SH1062: the workspace offers the ask with nobody around to answer it")
  for (const locale of ["en", "pt"]) {
    const strings = JSON.parse(read("src", "i18n", "locales", locale, "content.json"))
    for (const key of ["content.coverageAsk", "content.coverageAskWrite", "content.coverageAskRefresh",
      "content.coverageAskedMany_one", "content.coverageAskedMany_other", "content.coverageAskRow",
      "content.coverageAskColumn", "content.coverageRequested", "content.coverageRequestedHint"]) {
      assert.ok(strings[key], `SH1062: ${locale} is missing ${key}`)
    }
  }
})
