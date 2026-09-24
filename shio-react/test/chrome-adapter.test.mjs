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
import { createElement as h } from "react"
import { renderToStaticMarkup } from "react-dom/server"
import * as bento from "@viglet/viglet-design-system/bento"

const { AdaptiveSectionCard } = bento

/**
 * SH892, after SH963 — the heaviest forms render one chrome, and the adapter needs nothing above it.
 *
 * ## What changed
 *
 * SH892 made the post and post-type editors compose `AdaptiveSectionCard` so one element tree
 * rendered in either chrome: a console card by default, a frosted bento section under a provider.
 * Two rules followed from how the package's bento branch found its parts. Every adopted section
 * needed a header, or it fell through to the console card, and `Content` had to be a direct
 * child, because the branch did not descend.
 *
 * VDS146 (design system 2026.3.11) removed the console branch, the provider and the hook. There is
 * one chrome, so both rules are gone: a header-less card renders the bento section, and a nested
 * `Content` keeps its body. This file now holds the new facts, by rendering the built package, and
 * the one claim that survives the change: the caller's own markup reaches the page untouched.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")

const field = (html) => (/<input[^>]*>/.exec(html) ?? [null])[0]
const header = h(AdaptiveSectionCard.StaticHeader, { icon: () => null, title: "Section", description: "D" })
const input = h("input", { name: "sh892-field", defaultValue: "unforked", readOnly: true })

/** The forms that compose the adapter, named, so one quietly going back to the console Card fails. */
const ADAPTED = [
  "app/content/post.edit.page.tsx",
  "app/content/post-type.edit.page.tsx",
]

test("SH963: with nothing above it, the adapter renders the bento section", () => {
  const html = renderToStaticMarkup(
    h(AdaptiveSectionCard, null, header, h(AdaptiveSectionCard.Content, null, input)))

  assert.match(html, /bento-glass/,
    "SH963: the adapter no longer renders the frosted bento section on its own. The provider that "
      + "used to choose it is gone (VDS146), so a card that renders anything else has no way back.")
  assert.doesNotMatch(html, /rounded-xl border/,
    "SH963: the console card's shape is back, which VDS146 removed from the package")
})

test("SH963: the package exports no chrome switch, so nothing in the console can declare one", () => {
  const chrome = Object.keys(bento).filter((name) => /Chrome/.test(name))
  assert.deepEqual(chrome, [],
    `SH963: the bento entry exports ${chrome.join(", ")} again. A chrome switch reappearing means a `
      + "console default could be picked up again by any surface that forgets to set it.")
})

test("SH892: the caller's own markup survives the adapter byte-identically", () => {
  const html = renderToStaticMarkup(
    h(AdaptiveSectionCard, null, header, h(AdaptiveSectionCard.Content, null, input)))
  const bare = renderToStaticMarkup(input)

  assert.equal(field(html), field(bare),
    "SH892: the adapter changed the caller's own markup. A wrapper that transforms what it is "
      + "handed forces the field logic to know it is wrapped, which in the post editor means fifteen "
      + "widgets reading a widgetSettings value the backend can send as the string \"null\".")
})

test("SH963: SH892's two structural rules are gone with the console branch", () => {
  const headerless = renderToStaticMarkup(
    h(AdaptiveSectionCard, null, h(AdaptiveSectionCard.Content, null, input)))
  const nested = renderToStaticMarkup(
    h(AdaptiveSectionCard, null, header, h("div", null, h(AdaptiveSectionCard.Content, null, input))))

  assert.match(headerless, /bento-glass/,
    "SH963: a header-less card falls through to something other than the bento section again, so "
      + "SH902's rule that every adopted section carries a header is back and should be restored")
  assert.ok(nested.includes('name="sh892-field"'),
    "SH963: a nested Content loses its body again, so SH892's direct-child rule is back and the "
      + "editors' cards must be checked for Content wrapped in another element")
})

test("SH892: the heavy forms compose the adapter and no longer import the console Card", () => {
  for (const page of ADAPTED) {
    const source = readFileSync(join(SRC, page), "utf8")
    assert.match(source, /AdaptiveSectionCard/,
      `SH892: ${page} no longer composes the adapter, so its sections are forked again.`)
    assert.doesNotMatch(source, /from\s*["']@\/components\/ui\/card["']/,
      `SH892: ${page} imports @/components/ui/card again, a second card beside the bento one.`)
  }
})

test("SH892: the reader is not vacuous — it renders the real package, not a stub", () => {
  const html = renderToStaticMarkup(
    h(AdaptiveSectionCard, null, header, h(AdaptiveSectionCard.Content, null, input)))
  assert.ok(html.length > 200,
    `SH892: the render produced ${html.length} characters, so the adapter resolved to something `
      + "that is not the component")
  assert.equal(typeof AdaptiveSectionCard.StaticHeader, "function",
    "SH892: AdaptiveSectionCard.StaticHeader is not a function, so the compound component changed")
})
