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
 * SH909 — the choice, made once: bento is the console, and the old shell answers nothing.
 *
 * ## What this is guarding against
 *
 * §LV.12's argument is not about appearance, it is about a state that becomes permanent by
 * nobody deciding: "the other console has been in exactly this state, with forty pages beside its
 * migrated ones, long enough for it to look normal". Two shells is the cost the parallel route was
 * worth paying **while a migration was in flight**, and Block AR finished. So the failure this
 * reader exists to catch is not a broken page — it is a second shell quietly coming back, or a
 * constant quietly pointing at `/console` again, in some later diff that had a good local reason.
 *
 * ## The three claims, each checkable from source
 *
 * 1. **No console shell is mounted.** `console.root.page.tsx` is what wrapped everything in the
 *    `SidebarProvider`; App.tsx must not mount it. (The file survives until SH910 deletes it,
 *    along with the rest of the console chrome.)
 * 2. **Every route constant points into the bento tree**, since those are what every `navigate()`,
 *    every `to=`, every `urlBase` and the whole nav declaration are built from. One that still
 *    reads `/console` is a link that bounces through the redirect — which works, and puts a
 *    curator through a URL change on a click inside the product.
 * 3. **The legacy paths still resolve.** Redirect rather than drop: `/console/**` is in bookmarks,
 *    in e-mails and in redirects this repository does not control, and a 404 for each of them is a
 *    removal wearing a migration's clothes.
 *
 * The live half — that the redirect really lands on the bento surface, in a browser, following the
 * navigation — is `console-check.mjs`'s `redirectsTo` probes, because a source scan cannot watch a
 * URL change.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")
const APP = stripComments(readFileSync(join(SRC, "App.tsx"), "utf8"))
const ROUTES_CONST = stripComments(readFileSync(join(SRC, "app", "routes.const.ts"), "utf8"))
/**
 * The same file unstripped. `stripComments` says of itself that "block comments inside string
 * literals would be stripped too", and the legacy patterns are `` `${consolePath}/*` `` — a route
 * wildcard that is character-for-character a block-comment opener, so the stripped copy loses
 * everything from there to the next comment close, and this file reads one backtick of four.
 */
const ROUTES_RAW = readFileSync(join(SRC, "app", "routes.const.ts"), "utf8")

/** Every `NAME: value` in the ROUTES object, as written. */
function routeConstants() {
  const object = /export const ROUTES = \{([\s\S]*?)\n\};/.exec(ROUTES_CONST)
  assert.ok(object, "SH909: the ROUTES object is no longer a literal this reader can parse — it "
    + "moved or changed shape, and every claim below is now being made about nothing.")
  return [...object[1].matchAll(/^\s*(\w+):\s*(.+?),\s*$/gm)].map(([, name, value]) => ({ name, value }))
}

test("SH909: the console shell is not mounted", () => {
  assert.doesNotMatch(APP, /ConsoleRootPage/,
    "SH909: App.tsx mounts the console shell again. Block AR migrated every surface to the bento "
      + "tree and this task made it the default; a second shell beside it is the state §LV.12 is "
      + "about — one that nobody chose, that works, and that becomes permanent because it works.")
  assert.doesNotMatch(APP, /\{(Content|Admin|GraphQL|UniversalEditor)Routes\}/,
    "SH909: a console route fragment is mounted again. Those trees render the migrated components "
      + "in the chrome this block retires, and every fix would then need landing twice.")
})

test("SH909: every route constant points into the bento tree", () => {
  const outside = routeConstants().filter(({ name, value }) => {
    if (name === "LEGACY_CONSOLE") return false // the one that must still be the old prefix
    return /console/.test(value)
  })

  assert.deepEqual(outside.map((entry) => `${entry.name}: ${entry.value}`), [],
    "SH909: this route constant still points at the console prefix. Every navigate(), to=, "
      + "urlBase and nav entry in the product is built from these, so one of them left behind is a "
      + "link that bounces through the legacy redirect — it works, and it changes the URL under a "
      + "curator who clicked something inside the product.")
})

test("SH909: the legacy console paths still resolve, rather than 404ing", () => {
  assert.match(ROUTES_RAW, /LEGACY_CONSOLE_PATTERNS/,
    "SH909: the legacy console patterns are gone. §LV.12 said to redirect rather than drop: those "
      + "URLs are in bookmarks and in links this repository does not control, and dropping them is "
      + "a removal rather than a migration.")
  assert.match(APP, /LegacyConsoleRedirect/,
    "SH909: App.tsx no longer mounts the legacy redirect, so every /console/** URL now falls to "
      + "the catch-all and reports 404 — which is what this task exists not to do.")

  // Both patterns, and the tenant-prefixed one is the half that is easy to lose: it is the URL a
  // multi-tenant install's users actually have, and it looks redundant next to the bare one.
  const patterns = /LEGACY_CONSOLE_PATTERNS\s*=\s*\[([^\]]*)\]/.exec(ROUTES_RAW)
  assert.ok(patterns, "SH909: LEGACY_CONSOLE_PATTERNS is no longer an array literal")
  assert.equal([...patterns[1].matchAll(/`/g)].length / 2, 2,
    "SH909: the legacy redirect no longer declares both patterns. The tenant-prefixed one "
      + "(`/:tenant/console/*`) is what a multi-tenant install's bookmarks carry, and it is the "
      + "one that looks redundant beside the bare path right up until it is deleted.")
})

test("SH909: the redirect maps the two paths that are not a prefix swap", () => {
  /*
   * `/console` and `/console/home` are the same page under bento, and it is the shell's **index**
   * rather than a `/home` route (SH898). A pure prefix swap sends them to `/bento/` and
   * `/bento/home` — the first is the index by luck, the second matches nothing and lands on the
   * bento 404 that SH908 built. That is the redirect failing on the one URL most likely to be
   * bookmarked: the home page.
   */
  const source = stripComments(readFileSync(join(SRC, "app", "routes.const.ts"), "utf8"))
  assert.match(source, /function bentoPathFor/,
    "SH909: the mapping is gone from routes.const.ts, where it sits beside the constants it is "
      + "derived from")
  assert.match(source, /"home"/,
    "SH909: bentoPathFor no longer special-cases `home`. Under bento the home page is the shell's "
      + "index, so a pure prefix swap sends /console/home to /bento/home — which matches no route "
      + "and renders the 404, on the URL most likely to be somebody's bookmark.")
})
