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
import { stripComments } from "./support/source-text.mjs"

/**
 * SH901 — the heaviest forms, and the adapter that had to span a 1:2 mapping.
 *
 * ## What §LV.4 predicted, and how it actually landed
 *
 * "This is the slice most likely to reveal that the adapter needs a prop it does not have." It
 * was sharper than a prop. The save-bar morph is **one** component — `BentoFormHero` renders the
 * controls in the hero and fades a fixed bar in as the hero scrolls away — and the authoring
 * contract forbids composing those halves by hand, because "they are two halves that drift". So
 * `PageHero` absorbed the save bar: one call in the page.
 *
 * ## And a check that could never have found what it was looking for
 *
 * SH897's `checkSaveBarMorph` looked for `[data-bento-save-bar], .bento-save-bar`. **Neither
 * exists.** The package renders `.bento-save-bar-spacer` and wraps the fixed bar in
 * `.bento-fade-in`. So the check reported "no surface carries a save-bar morph yet" for reasons
 * that had nothing to do with whether one did — and would have gone on reporting it after this
 * task shipped one. Fixed against the package's real markup, and the browser check now says
 * `morph: ok` on the post editor and `no surface carries one` on the home, which are the two
 * answers it is supposed to be able to tell apart.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")

const read = (p) => stripComments(readFileSync(join(SRC, p), "utf8"))
const EDIT = read("app/content/post.edit.page.tsx")
const NEW = read("app/content/post.new.page.tsx")
const ADAPTER = read("components/page-hero.tsx")
const CHECK = stripComments(readFileSync(join(HERE, "..", "e2e", "console-check.mjs"), "utf8"))

test("SH901: the post pages compose one header call, not a header and a save bar", () => {
  for (const [name, source] of [["post.edit", EDIT], ["post.new", NEW]]) {
    assert.match(source, /PageHero/, `SH901: ${name} lost its adaptive header`)
    assert.doesNotMatch(source, /<StickySaveBar/,
      `SH901: ${name} renders StickySaveBar directly again. In bento the hero and the bar are one`
        + " component and the authoring contract forbids composing the halves by hand — a page"
        + " that mounts the console bar itself cannot become the morph.")
    assert.doesNotMatch(source, /@\/components\/page-header/,
      `SH901: ${name} imports PageHeader directly, so its header cannot follow the chrome`)
  }
})

test("SH901: the adapter renders the morph, which is the half that survived", () => {
  assert.match(ADAPTER, /BentoFormHero/,
    "SH901: the adapter no longer renders BentoFormHero, so a page with a save bar gets a plain"
      + " hero and its controls never morph")

  /*
   * SH910 — the `StickySaveBar` assertion is gone, and its absence is now the rule: `PageHero`
   * renders `BentoFormHero` and nothing else, so a page composing a sticky save bar directly is the
   * regression.
   */
  assert.doesNotMatch(ADAPTER, /StickySaveBar/,
    "SH901: the adapter renders StickySaveBar again. That is console-era chrome, deleted with the"
      + " shell in SH910 — the morph is one component now, and composing the two halves by hand is"
      + " what the authoring contract warns drifts.")

  assert.match(ADAPTER, /const hasSaveBar =/,
    "SH901: the adapter no longer distinguishes a page with a save bar from one without. The"
      + " content browser passes `actions` and wants no bar; deciding on `actions` alone would"
      + " give it one.")
})

test("SH901: the widgets are untouched — the whole point of the adapter", () => {
  assert.match(EDIT, /PostFormFields/,
    "SH901: the post editor no longer renders PostFormFields. Fifteen dynamic widgets, a schema"
      + " built at runtime and `widgetSettings` the backend can return as the string \"null\" —"
      + " none of it was to be rewritten, and the adapter exists so it did not have to be.")
  assert.match(EDIT, /postFormSchema\(/,
    "SH901: the runtime schema build is gone from the post editor")
})

test("SH901: the browser check looks for markup the package actually renders", () => {
  assert.match(CHECK, /bento-save-bar-spacer/,
    "SH901: the morph check is not looking for `.bento-save-bar-spacer`. SH897 looked for"
      + " `[data-bento-save-bar]` and `.bento-save-bar`, neither of which the package renders, so"
      + " the check could not have found a morph on any page — and its report said so in words"
      + " that sounded like a finding.")
  assert.match(CHECK, /bento-fade-in/,
    "SH901: the morph check no longer reads the fade. The bar is in the DOM from the first paint"
      + " and `opacity: 0` until it fades in, so presence proves nothing about visibility.")
  assert.doesNotMatch(CHECK, /data-bento-save-bar/,
    "SH901: the invented selector is back")
})

test("SH901: the three post surfaces exist, and the chooser is the copy", () => {
  const surfaces = survey()
  // Adapter surfaces: the console page IS the bento page, so there is no bento/ copy of them.
  for (const name of ["post.edit", "post.new"]) {
    // SH960 — the digest has no era; what makes these adapter pages is the seam they compose.
    assert.ok(surfaces[`content/${name}.page.tsx`]?.chrome.includes("PageHero"),
      `SH901: content/${name}.page.tsx is missing or no longer composes PageHero, the seam`
        + " through which one component renders the bento header.")
    // SH961 — one tree, so "no bento/ copy" is asked as "exactly one".
    assert.deepEqual(Object.keys(surfaces).filter((key) => key.endsWith(`/${name}.page.tsx`)),
      [`content/${name}.page.tsx`],
      `SH901: a second copy of ${name} appeared. These two migrate by adapter precisely so 500`
        + " lines of save/publish/preview handling do not need every fix twice.")
  }
  assert.ok(surfaces["content/post-type-select.page.tsx"],
    "SH901: the post-type chooser has no bento surface. Its cards are choices rather than chrome,"
      + " so their counterpart is a tile and the page is worth writing twice.")
})

test("SH901: the reader is not vacuous — it reads the real pages and a real survey", () => {
  assert.ok(EDIT.length > 8000,
    `SH901: post.edit.page.tsx read as ${EDIT.length} characters, which is not the editor`)
  assert.ok(CHECK.length > 10_000, "SH901: console-check.mjs is not the script")
  assert.ok(Object.keys(survey()).length > 40, "SH901: the survey is reading an empty tree")
})
