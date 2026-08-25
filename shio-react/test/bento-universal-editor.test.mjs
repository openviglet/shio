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
 * SH907 — the Universal Editor, the one surface where a shell swap breaks *behaviour*.
 *
 * §LV.10's warning was specific: "the bridge depends on what surrounds the frame — the sizing, the
 * scroll container, the overlay stacking — and the bento shell changes all three."
 *
 * ## What the measurement said
 *
 * `visual-editor.page.tsx` is `flex flex-col h-screen`. Mounted inside the bento shell and read in
 * a browser at 1600x1000, against the same page mounted outside it:
 *
 * | | iframe top | iframe bottom | fits |
 * |---|---|---|---|
 * | inside the shell | 137 | 1048 | **no — 48px below the fold** |
 * | outside it | 73 | 984 | yes |
 *
 * 100vh *plus* the shell's 64px `sticky top-0` header, and two sticky bars stacked at the top.
 *
 * ## Why the answer is a route and not a container
 *
 * The fix is not a taller wrapper or a `calc(100vh - 4rem)`, both of which make the editor's
 * height a fact about the shell — the coupling §LV.10 is warning about, written down. **A surface
 * that owns the viewport has no chrome to migrate.** The editor draws its own toolbar, its own
 * split and its own overlays; it renders the same in both eras because it renders neither era's
 * chrome. So it is mounted as a sibling of the shell, and the bridge's environment is byte-
 * identical to the one it has always had.
 *
 * ## What is NOT proven here
 *
 * §LV.10 also says "a digest is least sufficient and a real click-through is required". The
 * click-through — `universal-editor-drive` in `console-check.mjs` — **skipped**: on a dev box the
 * console is `:5173` and the renderer answers as `:2710`, so the bridge posts to an origin this
 * window is not. That is a real gap in this task's evidence, not a formality. What did run: both
 * routes rendered with the same iframe `src`, the same edit-mode switch and the same properties
 * panel, with no page errors — and the bento one with *more* room than the console one.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")
const APP = stripComments(readFileSync(join(SRC, "App.tsx"), "utf8"))
const EDITOR = stripComments(
  readFileSync(join(SRC, "app", "console", "universal-editor", "visual-editor.page.tsx"), "utf8"))
const PICKER = stripComments(
  readFileSync(join(SRC, "app", "console", "universal-editor", "editor-sites.page.tsx"), "utf8"))

test("SH907: the visual editor is mounted outside the bento shell", () => {
  /*
   * The whole task, held as one property. `BentoRootPage` renders a `sticky top-0` header and
   * wraps the routed page in `bento-rail-gutter`; an `h-screen` editor inside that is 100vh plus
   * 64px, and the bottom of the preview goes below the fold with no scrollbar that reaches it.
   */
  const shell = APP.slice(APP.indexOf("<Route path={ROUTES.BENTO}"), APP.indexOf("<Route path={ROUTES.CONSOLE}"))
  assert.doesNotMatch(shell, /path="universal-editor\/:siteId"/,
    "SH907: the visual editor is nested under the bento shell again. It is `h-screen` and draws"
      + " its own toolbar, so inside a shell with a 64px sticky header it is one viewport plus a"
      + " header tall — measured: the iframe's bottom moves from 984 to 1048 at a 1000px viewport,"
      + " and two sticky bars stack. Mount it as a sibling, the way /bento-vocabulary is.")
  assert.match(APP, /path="\/bento\/universal-editor\/:siteId"/,
    "SH907: the bento route for the visual editor is gone, so /bento has no way into the editor"
      + " at all — which is worse than the wrong chrome.")
})

test("SH907: the editor stays h-screen and owns its own frame", () => {
  /*
   * The other half of the same decision. If a later edit makes the editor's height depend on the
   * shell — `calc(100vh - 4rem)`, `h-full` inside a sized parent — the route placement above stops
   * being sufficient and the coupling is back, spelled in CSS where nothing reads it.
   */
  assert.match(EDITOR, /h-screen/,
    "SH907: the visual editor no longer sizes itself to the viewport. If its height now comes from"
      + " a parent, it has become a fact about whatever shell mounts it — which is the coupling"
      + " §LV.10 warned about and the reason this page is routed outside the shell.")
  assert.doesNotMatch(EDITOR, /AdaptivePageHeader|AdaptiveSectionCard|AdaptivePanel/,
    "SH907: the visual editor grew adaptive chrome. It renders neither era's chrome on purpose —"
      + " that is what makes the bridge's environment identical in both — so a chrome adapter here"
      + " means the frame is now surrounded by something that changes between eras.")
})

test("SH907: the site picker is an ordinary surface and got the ordinary treatment", () => {
  for (const [what, pattern] of [
    ["adaptive header", /AdaptivePageHeader/],
    ["adaptive panels", /AdaptivePanel/],
    ["the shared empty state", /AdaptiveEmptyState/],
  ]) {
    assert.match(PICKER, pattern,
      `SH907: the editor site picker lost ${what}. It is a list of sites with a hero above it —`
        + " nothing about the editor's viewport problem applies to the page that links to it.")
  }
  assert.doesNotMatch(PICKER, /@\/components\/ui\/card/,
    "SH907: the site picker imports Card again, so its two panels cannot follow the chrome.")
})
