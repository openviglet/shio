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
import { survey } from "../scripts/surface-digest.mjs"
import { findElements } from "./support/jsx-elements.mjs"
import { stripComments } from "./support/source-text.mjs"

/**
 * SH899 — the site slice, and the rule that makes a save bar do something.
 *
 * ## The defect this file exists for
 *
 * `BentoEntityShell` renders its Save as `<GradientButton type="submit" form={formId}>`. That
 * submits **an HTML form with that id, and nothing else**. The first draft of the site editor
 * returned a `BentoFormSection` from the render prop with no `<form>` around it, so:
 *
 * - the page rendered perfectly,
 * - the hero showed Save Changes and Cancel,
 * - clicking Save issued no request, logged nothing, and threw nothing.
 *
 * `tsc` cannot see it — `formId` is a string and the shell has no way to check that a form with
 * that id exists. Only typing into a field, clicking Save, reloading and reading the value back
 * found it. Every one of Block AR's remaining entity editors composes this same pair, so the rule
 * is asserted here rather than remembered: **a shell that names a `formId` must contain a `<form>`
 * with that id.**
 *
 * `onUpdate` is a different path and does not cover it. The shell calls that when the *hero*
 * identity is edited — type, blur, autosave — so a page can have a working hero and a dead body.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")

const read = (name) =>
  stripComments(readFileSync(join(SRC, "app", "content", name), "utf8"))

const EDIT = read("site.edit.page.tsx")
const LIST = read("site.list.page.tsx")
const NEW = read("site.new.page.tsx")
const IMPORT = read("site.import.page.tsx")

/** Every `formId="..."` a `BentoEntityShell` declares, per file. */
function shellFormIds(source) {
  return findElements(source, "BentoEntityShell")
    .flatMap((el) => [...el.attrs.matchAll(/formId="([^"]+)"/g)].map(([, id]) => id))
}

test("SH899: a shell that names a formId contains a form with that id", () => {
  for (const [name, source] of [["site.edit.page.tsx", EDIT]]) {
    const ids = shellFormIds(source)
    assert.ok(ids.length > 0, `SH899: ${name} declares no BentoEntityShell formId`)

    for (const id of ids) {
      assert.match(source, new RegExp(`<form[^>]*id="${id}"`),
        `SH899: ${name} gives its shell formId="${id}" and contains no <form id="${id}">.\n`
          + "  The shell's Save is `<GradientButton type=\"submit\" form={formId}>`, so it submits"
          + " an HTML form with that id and nothing else. Without one the button is inert: no"
          + " request, no error, no console message, and a page that looks completely correct.\n"
          + "  `onUpdate` does not cover this — the shell calls that for *hero* identity edits, so"
          + " the title and description can save while the whole form body does not.")
    }
  }
})

test("SH899: the form the shell submits actually writes", () => {
  // Narrower than "there is a form": it has to have a submit handler that reaches the service.
  // A `<form id="site-edit">` with no onSubmit satisfies the rule above and still does nothing.
  assert.match(EDIT, /<form id="site-edit" onSubmit=\{onSubmit\}>/,
    "SH899: the site editor's form has no submit handler, so the id is satisfied and the button"
      + " is still inert")
  assert.match(EDIT, /service\.update\(/,
    "SH899: the site editor's submit path no longer calls the service")
})

/*
 * SH910 — the console four were untouched right up until they were deleted, which is the shape
 * this slice was built to allow.
 *
 * The reversibility this asserted was real and it was spent: each console page stayed byte-identical
 * while its bento counterpart was written beside it, and the whole set went in one commit once the
 * route tree did. What is left to assert is that the four bento surfaces exist and are bento-era,
 * and that the four console originals are **gone** rather than lingering unmounted — an unreachable
 * page is what the next new surface copies, which is SH910's whole argument.
 */
test("SH899: the four site surfaces are bento, and the console four are gone", () => {
  const surfaces = survey()
  for (const name of ["site.list", "site.new", "site.edit", "site.import"]) {
    const page = `content/${name}.page.tsx`
    assert.ok(surfaces[page]?.chrome.length > 0, `SH899: ${page} is missing or composes no chrome`)
    // SH961 — one tree, so "the console copy is gone" is asked as "there is exactly one".
    const copies = Object.keys(surfaces).filter((key) => key.endsWith(`/${name}.page.tsx`))
    assert.deepEqual(copies, [page],
      `SH899: ${name}.page.tsx exists in more than one place. SH910 deleted the console site pages;`
        + " a second copy is a page every fix has to land in twice.")
  }
})

test("SH899: the list uses the package's list page, and keeps the filter the console had", () => {
  assert.match(LIST, /BentoListPage/,
    "SH899: the site list hand-rolls its grid again. §LV.2 asked for the package's list page"
      + " precisely because this is the shape ten more list screens will copy.")
  assert.match(LIST, /toolbar=/,
    "SH899: the filter is gone. BentoListPage has no search of its own, so dropping it here is a"
      + " regression on the console list most likely to grow — `toolbar` is the seam (SH1221).")

  // A failed load must not read as an empty list. `undefined` means "loading" to the package's
  // gate, so a rejected query has to become an empty array AND an error, or the page spins.
  // SH1046 replaced the effect's `.catch(() => setSites([]))` with the same rule over `useQuery`:
  // the spelling moved, the claim did not.
  assert.match(LIST, /const sites = data \?\? \(failure \? \[\] : undefined\)/,
    "SH899: a failed site query no longer resolves the items, so the list spins for ever")
  assert.match(LIST, /error=\{error\}/,
    "SH899: the list no longer passes its error, so a failed load renders the empty state — 'no"
      + " sites' and 'the request failed' become the same screen, which is what the console page"
      + " did and what the package's list page exists to stop.")
})

test("SH899: the create and import forms let the hero own both halves of the morph", () => {
  for (const [name, source] of [["site.new.page.tsx", NEW], ["site.import.page.tsx", IMPORT]]) {
    assert.match(source, /BentoFormHero/,
      `SH899: ${name} no longer uses BentoFormHero`)
    assert.doesNotMatch(source, /BentoScrollSaveBar|className="sticky/,
      `SH899: ${name} composes a save bar by hand. BentoFormHero renders both halves of the morph`
        + " itself, and the authoring contract names composing them separately as the mistake —"
        + " they are two halves that drift.")
  }

  // The import page saves imperatively, so its buttons go through `actions` — which replaces the
  // default pair in BOTH copies of the morph. A button rendered below the fields instead would
  // leave the hero's own Save doing nothing, which is the same defect as the shell one above.
  assert.match(IMPORT, /actions=\{/,
    "SH899: the import page no longer passes its own button through the hero's `actions`, so the"
      + " hero's default Save is on screen with nothing behind it")
})

test("SH899: the reader is not vacuous — it parses real shells and a real survey", () => {
  assert.deepEqual(shellFormIds(EDIT), ["site-edit"],
    "SH899: the formId scan found something other than the editor's one shell, so the rule above"
      + " is checking a different thing than it says")
  assert.equal(shellFormIds('<BentoEntityShell formId="x">{() => null}</BentoEntityShell>')[0], "x",
    "SH899: the scan cannot read a formId off a minimal shell, so it would find none in any file")
  assert.ok(Object.keys(survey()).length > 40, "SH899: the survey is reading an empty tree")
})

/*
 * SH960 — "the census and the digest classify eras the same way" is gone, with both classifiers.
 * The census file is deleted and the digest has no era field, so there is no second reader left to
 * disagree with; `surface-digest.test.mjs` says what the digest measures now.
 */

test("SH1329: the site card's link is named by its own text, not by the site alone", () => {
  // The card is one link holding the name, the description, the pages figures and the last change.
  // An `aria-label` of the site's name left most of that visible text out of the name, which axe
  // fails as label-content-name-mismatch (WCAG 2.5.3): a voice-control user who says what the card
  // shows gets no answer. Named from its content, every word on the card is in its name.
  const card = LIST.slice(LIST.indexOf("function SiteCard"), LIST.indexOf("function useSitesView"))
  const links = findElements(card, "Link")
  assert.equal(links.length, 1, `SH1329: SiteCard should hold exactly one Link, found ${links.length}`)
  assert.doesNotMatch(links[0].attrs, /aria-label(?:ledby)?\s*=/,
    "SH1329: the site card's Link carries an aria-label again, so its name is narrower than its visible text")
})
