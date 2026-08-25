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
import { createElement as h } from "react"
import { findElements } from "./support/jsx-elements.mjs"
import { stripComments } from "./support/source-text.mjs"
import { renderToStaticMarkup } from "react-dom/server"
import { AdaptiveSectionCard, SectionCardChromeProvider } from "@viglet/viglet-design-system/bento"

/**
 * SH892 — the three heaviest forms in this product render in either chrome, unforked.
 *
 * ## Why this one renders rather than reads
 *
 * Every other reader in this directory is a source scan, and `console-ui.md` gives the reason:
 * "the instance does not run a browser" is a standing non-goal, and a console page resolves a
 * router, three contexts and a service call before it paints. That argument is about *pages*.
 * The adapter is not a page — it is a wrapper with one input (a provider above it) and one
 * output (markup), no router, no service and no data. `react-dom/server` renders the **built
 * package** in about a millisecond, so the claim this task actually makes can be asserted
 * instead of described.
 *
 * And it needed asserting. "One form renders in either chrome" is not checkable by reading an
 * import: a page can import the adapter and still be forked, and the adapter could regress into
 * rendering one chrome under both — which would look exactly like success in every source scan,
 * in `tsc`, and in the console, right up until Block AR wrapped a page in the provider and got
 * console cards back.
 *
 * ## What "unforked" means here, precisely
 *
 * Not "the two renderings are similar" — they must be *different*, that is the whole feature.
 * The invariant is narrower and it is the one that matters: **the caller's own markup is
 * byte-identical under both chromes, and only the wrapper differs.** That is what lets the post
 * editor's fifteen dynamic widgets, its runtime-built schema and its `widgetSettings` parsing be
 * written once. If a chrome ever started transforming what it was handed, the field logic would
 * have to know which chrome it was in, and the fork this task exists to prevent would be back —
 * spread across the three largest files in the console instead of concentrated in one wrapper.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")

/** Every `.tsx` under src — the adapter can be used from a page or a component. */
function consoleSources(dir = SRC, found = []) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name)
    if (entry.isDirectory()) consoleSources(path, found)
    else if (entry.name.endsWith(".tsx")) found.push(path)
  }
  return found
}

/**
 * The caller's markup, pulled out of whatever each chrome rendered around it.
 *
 * Deliberately not compared against a hardcoded expected string: the first draft of this file
 * spelled the `<input>` by hand, and React emits `readOnly=""` first and the attributes in its
 * own order, so the reader failed on a detail that has nothing to do with the claim. The claim is
 * that **the two chromes agree**, so the two renders are compared to each other and the literal
 * never has to be written down.
 */
const field = (html) => (/<input[^>]*>/.exec(html) ?? [null])[0]

const tree = h(
  AdaptiveSectionCard,
  null,
  h(AdaptiveSectionCard.StaticHeader, { icon: () => null, title: "Section", description: "D" }),
  h(AdaptiveSectionCard.Content, null,
    h("input", { name: "sh892-field", defaultValue: "unforked", readOnly: true })),
)

const render = (chrome) => (chrome === null
  ? renderToStaticMarkup(tree)
  : renderToStaticMarkup(h(SectionCardChromeProvider, { chrome }, tree)))

/**
 * The forms this task adopted the adapter in. A named list, so a page that quietly goes back to
 * `@/components/ui/card` fails by name.
 *
 * SH910 — three became two. `site.edit.page.tsx` was deleted with the console route tree: unlike
 * the other two it had a full bento counterpart (SH899 wrote all four site surfaces fresh, because
 * an entity-shaped page of 100–200 lines is worth writing twice), so nothing was left to adapt.
 * The post and post-type editors are the opposite case and the reason the adapter exists — a
 * runtime-built schema and fifteen dynamic widgets that were never going to be rewritten twice.
 */
const ADAPTED = [
  "app/console/content/post.edit.page.tsx",
  "app/console/content/post-type.edit.page.tsx",
]

test("SH892: the adapter renders two different chromes from one element tree", () => {
  const consoleHtml = render("console")
  const bentoHtml = render("bento")

  assert.notEqual(consoleHtml, bentoHtml,
    "SH892: both chromes rendered the same markup. The adapter's entire purpose is that a"
      + " provider above the page decides between a console card and a frosted bento section —"
      + " if it renders one thing under both, Block AR will wrap a page and get the old chrome"
      + " back, and every source scan will still say the migration happened.")

  assert.match(consoleHtml, /rounded-xl border/,
    "SH892: the console branch must render the bordered card shape — it is what 35 pages still"
      + " render directly, and the swap is only safe while the two look the same")
  assert.match(bentoHtml, /bento-tile|bento-glass/,
    "SH892: the bento branch must render the frosted tile, whose classes come from bento.css")
  assert.doesNotMatch(consoleHtml, /bento-glass/,
    "SH892: the console branch is leaking bento classes, so every un-migrated page carrying the"
      + " adapter would half-change appearance with nothing in its own diff")
})

test("SH892: console is the default, so adopting the adapter changes nothing until asked", () => {
  assert.equal(render(null), render("console"),
    "SH892: with no provider the adapter must render the console branch. That default is what"
      + " made this task a safe swap across three files totalling 1,600 lines — the pages look"
      + " exactly as they did, and SH899/SH901/SH902 opt in by adding a provider. A default that"
      + " flipped to bento would have re-skinned three console pages in a commit about wrappers.")
})

test("SH892: the caller's own markup survives both chromes byte-identically", () => {
  const consoleHtml = render("console")
  const bentoHtml = render("bento")

  assert.ok(field(consoleHtml), "SH892: the console chrome rendered no field at all")
  assert.ok(field(bentoHtml), "SH892: the bento chrome rendered no field at all")

  assert.equal(field(consoleHtml), field(bentoHtml),
    "SH892: the two chromes rendered the caller's own markup differently.\n"
      + "A chrome that transforms what it is handed forces the field logic to know which chrome"
      + " it is in, which is the fork this task exists to prevent — and it would land in the post"
      + " editor, where the schema is built at runtime and fifteen widgets read a `widgetSettings`"
      + " value the backend can return as the literal string \"null\". The wrapper may differ as"
      + " much as it likes; the contents may not differ at all.")
})

test("SH892: the heavy forms compose the adapter and no longer import the console Card", () => {
  for (const page of ADAPTED) {
    const source = readFileSync(join(SRC, page), "utf8")

    assert.match(source, /AdaptiveSectionCard/,
      `SH892: ${page} no longer composes the adapter. Its section chrome would then be forked by`
        + " the parallel bento route, which is the case §LIV.7 was filed against.")
    assert.doesNotMatch(source, /from\s*["']@\/components\/ui\/card["']/,
      `SH892: ${page} imports @/components/ui/card again. A page that renders both is a page`
        + " where half the sections re-skin and half do not — worse than either era alone.")
  }
})

/**
 * The two ways adopting the adapter silently achieves nothing, both found by rendering rather
 * than by reading, and both present in the first draft of SH892's own diff.
 */
test("SH892: a section with no header never re-skins, so every adopted section has one", () => {
  const headerless = h(AdaptiveSectionCard, null,
    h(AdaptiveSectionCard.Content, null, h("input", { name: "x", readOnly: true })))
  const asConsole = renderToStaticMarkup(
    h(SectionCardChromeProvider, { chrome: "console" }, headerless))
  const asBento = renderToStaticMarkup(
    h(SectionCardChromeProvider, { chrome: "bento" }, headerless))

  // This is the package's documented behaviour, not a bug: "a section built some other way falls
  // through to the console card rather than losing its content silently". Asserted here because
  // the consequence for a *consumer* is the opposite of obvious — it means a header-less adopted
  // section is a no-op that looks like a migration in every source scan.
  assert.equal(asConsole, asBento,
    "SH892: a header-less section now re-skins. If the package changed that, this file's reason"
      + " for demanding a header on every adopted section has gone, and the pages can drop theirs.")

  for (const page of ADAPTED) {
    const source = readFileSync(join(SRC, page), "utf8")
    assert.match(source, /AdaptiveSectionCard\.(Static)?Header/,
      `SH892: ${page} composes the adapter with no header, so it renders the console card under`
        + " both chromes. SH899/SH901/SH902 would wrap it in a provider, see no change, and have"
        + " nothing in any test or scan to explain why — which is how this was nearly shipped.")
  }
})

test("SH892: Content must be a direct child, or the bento branch renders an empty section", () => {
  const header = h(AdaptiveSectionCard.StaticHeader, { icon: () => null, title: "T" })
  const marker = h("input", { name: "sh892-body", readOnly: true })
  const direct = h(AdaptiveSectionCard, null, header, h(AdaptiveSectionCard.Content, null, marker))
  const nested = h(AdaptiveSectionCard, null, header,
    h("div", null, h(AdaptiveSectionCard.Content, null, marker)))

  const bento = (tree) => renderToStaticMarkup(
    h(SectionCardChromeProvider, { chrome: "bento" }, tree))

  assert.ok(bento(direct).includes('name="sh892-body"'),
    "SH892: a direct Content child must survive the bento branch — the control")

  // The trap, stated as the assertion it is. The bento branch finds its header and content with
  // `Children.forEach`, which does not descend. The first draft of the post-type editor's swap
  // wrapped Content in <Tabs>; with a header present that renders a section with no body at all,
  // and it would have surfaced as "the whole post-type form disappeared" in whatever commit
  // eventually switched the chrome on — nowhere near the commit that caused it.
  assert.ok(!bento(nested).includes('name="sh892-body"'),
    "SH892: a nested Content now survives, so the direct-child rule this file teaches — and the"
      + " comments in the three editor pages that cite it — are wrong and should be corrected.")

  // The source scan below has to be able to see the thing it is looking for. Its own failure
  // mode is a regex that matches nothing, which reads as every page being correct.
  const nestedSample = "<AdaptiveSectionCard>\n<Tabs>\n<AdaptiveSectionCard.Content>"
  const sampleHead = /<AdaptiveSectionCard>([\s\S]*?)<AdaptiveSectionCard\.Content>/.exec(nestedSample)
  assert.deepEqual(
    [...sampleHead[1].matchAll(/<([A-Z][\w.]*)/g)]
      .map((m) => m[1])
      .filter((name) => !name.startsWith("AdaptiveSectionCard")),
    ["Tabs"],
    "SH892: the intruder scan no longer spots a Content nested in <Tabs> — which is the exact"
      + " shape it exists to catch, and the shape this task's first draft shipped as far as tsc.")

  for (const page of ADAPTED) {
    const source = readFileSync(join(SRC, page), "utf8")
    // Every card in the file, not just the first: post-type.edit.page.tsx has two, and the one
    // that carried the trap was the first — so a reader that stopped there would have gone green
    // on a file whose *other* card was the broken one, which is the same bug wearing a hat.
    const cards = [...source.matchAll(/<AdaptiveSectionCard>([\s\S]*?)<AdaptiveSectionCard\.Content>/g)]
    const opens = (source.match(/<AdaptiveSectionCard>/g) ?? []).length

    assert.equal(cards.length, opens,
      `SH892: ${page} opens ${opens} AdaptiveSectionCard(s) but only ${cards.length} reach a`
        + " Content — one of them holds no body at all, which renders nothing under bento.")

    for (const [, head] of cards) {
      const intruders = [...head.matchAll(/<([A-Z][\w.]*)/g)]
        .map((m) => m[1])
        .filter((name) => !name.startsWith("AdaptiveSectionCard"))
      assert.deepEqual(intruders, [],
        `SH892: ${page} wraps AdaptiveSectionCard.Content in <${intruders[0]}>. The bento branch`
          + " does not descend, so it would render the header and drop the entire body.")
    }
  }
})

test("SH892: the reader is not vacuous — it renders the real package, not a stub", () => {
  const html = render("bento")
  assert.ok(html.length > 200,
    `SH892: the bento render produced ${html.length} characters — the adapter resolved to`
      + " something that is not the component, and the comparisons above are passing over noise")
  assert.equal(typeof AdaptiveSectionCard.StaticHeader, "function",
    "SH892: AdaptiveSectionCard.StaticHeader is not a function, so the compound component's"
      + " shape changed and this file is asserting against a different API than the pages use")
})

/**
 * SH902 — every `AdaptiveSectionCard` in this console has a header, or it is not one.
 *
 * The rule above proves the *package's* behaviour: a section with no header falls through to the
 * console card, deliberately, so its content cannot go missing. Nothing asserted that **Shio's
 * own usages** carry one — and four of them did not.
 *
 * The content browser's stats strip, its toolbar and its listing, and the post-type list, are
 * panels rather than sections: a count, a breadcrumb, a view toggle, a list of rows, under a hero
 * that already names the page. A header on any of them is noise, so all four were written without
 * one and all four silently rendered console cards under the bento shell. SH900 introduced three
 * of them in a single commit, written by the same hand that had documented the no-op eight tasks
 * earlier.
 *
 * `AdaptivePanel` is what those four use now, and **SH923** asks the package for the component it
 * stands in for. This reader is what stops the mistake being made a fifth time: reach for
 * `AdaptiveSectionCard` and you must have something to title.
 */
test("SH902: no AdaptiveSectionCard in this console is missing its header", () => {
  const offenders = []
  for (const path of consoleSources()) {
    const source = stripComments(readFileSync(path, "utf8"))
    for (const element of findElements(source, "AdaptiveSectionCard")) {
      if (element.selfClosing) continue
      if (/AdaptiveSectionCard\.(Static)?Header/.test(element.body)) continue
      offenders.push(relative(SRC, path).replace(/\\/g, "/"))
    }
  }

  assert.deepEqual(offenders, [],
    "SH902: an AdaptiveSectionCard has no Header/StaticHeader child, so in bento chrome it falls"
      + " through to a console card — silently, with no error and nothing visible in review.\n"
      + "  If the panel has something to title, give it a StaticHeader.\n"
      + "  If it does not — a stats strip, a toolbar, a bare list under a hero that already names"
      + " the page — it is not a section. Use `AdaptivePanel` (SH923).")
})
