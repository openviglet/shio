/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1192 — four page-header anatomies: the title started at 140, 188, 208 or 380 px depending on
 * the route. Three causes, and each is held here at the place it lived.
 *
 * A 28 px outline icon beside the title on twenty-six routes and a 48 px gradient chip on the list
 * pages moved the title by different amounts; the design system's direction (VDS182) keeps the chip
 * for an entity's identity only, so neither is drawn on a page hero any more. Six multi-section
 * editors sat in the `narrow` column, which is for one question (bento-column.test.mjs holds that).
 * And content that nests named one parent, so a folder three levels down said "Sites" and its title
 * was the site's name: it now has the trail VDS184 added, and its own name as the title.
 */
import { strict as assert } from "node:assert"
import { readdirSync, readFileSync } from "node:fs"
import { dirname, join, relative } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { stripComments } from "./support/source-text.mjs"
import { heroTrail } from "../src/app/content/content-browser/browser-view.ts"

const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src")
const read = (...parts) => stripComments(readFileSync(join(SRC, ...parts), "utf8"))

function pages() {
  const out = []
  const walk = (dir) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const path = join(dir, entry.name)
      if (entry.isDirectory()) walk(path)
      else if (entry.name.endsWith(".tsx")) out.push(path)
    }
  }
  walk(join(SRC, "app"))
  return out
}

test("SH1192: the page hero draws no icon beside the title", () => {
  const hero = read("components", "page-hero.tsx")
  assert.doesNotMatch(hero, /\bleading=/, "SH1192: PageHero passes a leading icon again")
  assert.doesNotMatch(hero, /\bicon\??:/, "SH1192: PageHero takes an icon again, and every caller will pass one")
})

test("SH1192: no page gives a hero a leading icon or a list hero a chip", () => {
  const offenders = []
  for (const path of pages()) {
    const name = relative(SRC, path).replace(/\\/g, "/")
    // The vocabulary harness proves the package's props, `leading` among them.
    if (name === "app/system/bento-vocabulary.page.tsx") continue
    const code = stripComments(readFileSync(path, "utf8"))
    if (/<BentoHero\b[^>]*\bleading=/.test(code) || /\bheroIcon=/.test(code) || /<PageHero\b[^>]*\bicon=/.test(code)) {
      offenders.push(name)
    }
  }
  assert.deepEqual(offenders, [],
    "SH1192: an icon beside a page title moves the title by the icon's width, which is how one"
      + " console came to have four title offsets. The chip belongs to an entity's identity hero only.")
})

test("SH1192: a folder's trail names its ancestors, and its title is the folder itself", () => {
  const sites = { to: "/bento/content", label: "Sites" }
  const route = (id) => `/bento/content/object/${id}`
  const path = [
    { id: "s", name: "VigR3", type: "SITE" },
    { id: "h", name: "Home", type: "FOLDER", root: true },
    { id: "a", name: "assets", type: "FOLDER" },
  ]
  assert.deepEqual(heroTrail(path, sites, route, { ownPath: true }), [
    sites, { to: route("s"), label: "VigR3" }, { to: route("h"), label: "Home" },
  ], "SH1192: the folder browser's trail stops at the parent; the folder is the title")
  assert.deepEqual(heroTrail(path, sites, route, { ownPath: false }).at(-1), { to: route("a"), label: "assets" },
    "SH1192: a post's trail ends at the folder it sits in")
  assert.deepEqual(heroTrail([path[0]], sites, route, { ownPath: true }), [sites],
    "SH1192: a site's root listing has one step, which the hero draws as the arrow back link")
})

test("SH1192: the folder browser and the post editor hand their hero a trail", () => {
  assert.match(read("app", "content", "object.children.page.tsx"), /<PageHero[\s\S]{0,400}\btrail=\{heroTrail\(/)
  assert.match(read("app", "content", "post.edit.page.tsx"), /<PageHero[\s\S]{0,200}\btrail=\{trail\}/)
})
