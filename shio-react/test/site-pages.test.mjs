/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"

import { localesOf, pageCount, pageTree, pageTypesOf, pathOf } from "../src/lib/site-pages.ts"

/**
 * SH1316 — opening a site showed six storage folders and "0 posts" for a site of 130 pages. A page
 * is a post of a type the site binds to a layout, and the tree is the pages' URLs.
 */
const SITE = { name: "Acme", postTypeLayout: '{"Page":"Marketing","Article":"Blog"}', pageType: "Page" }
const ROWS = [
  { address: "post:Acme/", type: "Page", title: "Home", locale: "en", published: true },
  { address: "post:Acme/about", type: "Page", title: "About", locale: "en", published: true },
  { address: "post:Acme/blog/hello", type: "Article", title: "Hello", locale: "en" },
  { address: "post:Acme/sobre", type: "Page", title: "Sobre", locale: "pt" },
  { address: "post:Acme/sections/home/hero", type: "SectionHero", title: "Hero", locale: "en" },
  { address: "post:Acme/menu/primary", type: "Menu", title: "Primary" },
]

test("SH1316: a page is a type the site binds to a layout, or its declared page type", () => {
  assert.deepEqual([...pageTypesOf(SITE)].sort(), ["Article", "Page"])
  assert.deepEqual([...pageTypesOf({ name: "X", postTypeLayout: "null", pageType: "Page" })], ["Page"],
    "SH1316: the stored binding can be the literal \"null\", which parses to null")
  assert.deepEqual([...pageTypesOf({ name: "X", postTypeLayout: "{not json" })], [])
})

test("SH1316: the tree is the pages' URLs, and sections, menus and themes are not in it", () => {
  const tree = pageTree(ROWS, SITE, "en")
  assert.equal(tree.page?.title, "Home", "SH1316: the home is the tree's root")
  assert.deepEqual(tree.children.map((node) => node.segment), ["about", "blog"])
  const blog = tree.children[1]
  assert.equal(blog.page, undefined, "SH1316: /blog is only a parent here, with no page of its own")
  assert.equal(blog.children[0].page?.title, "Hello")
  assert.equal(pageCount(tree), 3, "SH1316: a section or a menu counted as a page")
})

test("SH1316: a site's root opens on its pages, which offer New, and the folders are one switch away", () => {
  const read = (...parts) => readFileSync(join(dirname(fileURLToPath(import.meta.url)), "..", "src", ...parts), "utf8")
  const page = read("app", "content", "object.children.page.tsx")
  assert.match(page, /rootMode === "pages" && rootSite \? <SitePages site=\{rootSite\} folderId=\{objectId\} \/>/,
    "SH1316: the site's root shows its storage folders again")
  assert.match(page, /\{atSiteRoot && <SiteRootSwitch mode=\{rootMode\} onChange=\{setRootMode\} \/>\}/,
    "SH1316: the switch back to the folders is gone, so the builder's view is unreachable")
  const view = read("app", "content", "content-browser", "site-pages.tsx")
  assert.match(view, /\/post-type\/\$\{encodeURIComponent\(newType\)\}\/new/,
    "SH1316: the pages view offers no New, so a curator at the root cannot make a page")
  assert.match(view, /staleTime: 0/, "SH1316: a page just saved is hidden by the cache on return")
})

test("SH1316: a locale switch shows one language's pages, and an untranslated page shows in all", () => {
  assert.deepEqual(localesOf(ROWS), ["en", "pt"])
  const pt = pageTree(ROWS, SITE, "pt")
  assert.deepEqual(pt.children.map((node) => node.segment), ["sobre"])
  const untranslated = pageTree([{ address: "post:Acme/x", type: "Page", title: "X" }], SITE, "pt")
  assert.equal(pageCount(untranslated), 1)
  assert.equal(pathOf("post:Acme/", "Acme"), "/")
  assert.equal(pathOf("post:Acme/a/b", "Acme"), "/a/b")
})
