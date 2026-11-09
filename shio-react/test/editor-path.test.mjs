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
import { editorHref, followedAddress, framePath } from "../src/lib/editor-path.ts"

/**
 * SH1392 — the Universal Editor opens a named page, and its address follows the frame.
 *
 * It framed a site's home and nothing else, so a page reached it only through a link drawn on
 * another one, and a draft (linked from nowhere) could not be opened there at all.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const read = (...path) => readFileSync(join(HERE, "..", "src", ...path), "utf8")

test("SH1392: ?path= names the page to frame, and the home is the bare route", () => {
  assert.equal(framePath(null), "/")
  assert.equal(framePath(""), "/")
  assert.equal(framePath("pricing"), "/pricing")
  assert.equal(framePath("//blog/hello"), "/blog/hello", "SH1392: one leading slash, never a protocol-relative src")
  assert.equal(editorHref("/bento/universal-editor", "s1", "/"), "/bento/universal-editor/s1")
  assert.equal(editorHref("/bento/universal-editor", "s1", "/blog/hello world"),
    "/bento/universal-editor/s1?path=blog%2Fhello%20world")
})

test("SH1392: the address follows the frame within the site, and nowhere else", () => {
  const current = "https://shio.test/bento/universal-editor/s1?path=pricing"
  const base = "/preview/Console%20Check/"
  assert.equal(followedAddress("https://shio.test/preview/Console%20Check/about?editor=true", base, current),
    "https://shio.test/bento/universal-editor/s1?path=about")
  assert.equal(followedAddress("https://shio.test/preview/Console%20Check/?editor=true", base, current),
    "https://shio.test/bento/universal-editor/s1", "SH1392: back on the home, the parameter goes")
  assert.equal(followedAddress("https://elsewhere.test/preview/Console%20Check/about", base, current), null,
    "SH1392: a frame that left for another origin names no page here")
  assert.equal(followedAddress("https://shio.test/preview/Other/about", base, current), null,
    "SH1392: nor one that left the site")
})

test("SH1392: the editor frames the requested page, and the page entries offer it", () => {
  const editor = read("app", "editor", "visual-editor.page.tsx")
  assert.match(editor, /siteDestination\(site\.data, framePath\(requestedPath\)\)/u,
    "SH1392: the frame's first src is the requested page, not the site's home")
  assert.match(editor, /followFrameRef\.current\(reported\)/u,
    "SH1392: each bridged load the frame reports moves the address with it")
  assert.match(editor, /history\.replaceState/u,
    "SH1392: by replacing the history entry, so following never reloads the page being edited")
  assert.match(read("app", "content", "post.edit.page.tsx"), /editorHref\(ROUTES\.UNIVERSAL_EDITOR, siteId, post\.furl\)/u,
    "SH1392: a page's post editor opens it in the editor on itself")
  assert.match(read("app", "content", "content-browser", "site-pages.tsx"), /editorHref\(ROUTES\.UNIVERSAL_EDITOR, siteId, node\.path\)/u,
    "SH1392: and so does each page row of the site's pages view")
})
