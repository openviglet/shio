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
import { editorHref, followedAddress, framePath, pageOnSite } from "../src/lib/editor-path.ts"

/**
 * SH1392 — the Visual Editor opens a named page, and its address follows the frame.
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
  assert.equal(editorHref("/bento/visual-editor", "s1", "/"), "/bento/visual-editor/s1")
  assert.equal(editorHref("/bento/visual-editor", "s1", "/blog/hello world"),
    "/bento/visual-editor/s1?path=blog%2Fhello%20world")
})

test("SH1392: the address follows the frame within the site, and nowhere else", () => {
  const current = "https://shio.test/bento/visual-editor/s1?path=pricing"
  const base = "/preview/Console%20Check/"
  assert.equal(followedAddress("https://shio.test/preview/Console%20Check/about?editor=true", base, current),
    "https://shio.test/bento/visual-editor/s1?path=about")
  assert.equal(followedAddress("https://shio.test/preview/Console%20Check/?editor=true", base, current),
    "https://shio.test/bento/visual-editor/s1", "SH1392: back on the home, the parameter goes")
  assert.equal(followedAddress("https://elsewhere.test/preview/Console%20Check/about", base, current), null,
    "SH1392: a frame that left for another origin names no page here")
  assert.equal(followedAddress("https://shio.test/preview/Other/about", base, current), null,
    "SH1392: nor one that left the site")
})

test("SH1392: a search result is a page to open only when its site renders its type as one", () => {
  const sites = [
    { id: "s1", name: "Acme", postTypeLayout: "{\"Page\":\"MAIN\"}" },
    { id: "s2", name: "Acme Blog", pageType: "Post" },
  ]
  assert.deepEqual(pageOnSite("post:Acme/pricing", "Page", sites), { siteId: "s1", path: "/pricing" })
  assert.deepEqual(pageOnSite("post:Acme/", "Page", sites), { siteId: "s1", path: "/" })
  assert.deepEqual(pageOnSite("post:Acme Blog/hello", "Post", sites), { siteId: "s2", path: "/hello" },
    "SH1392: a site whose name another site's begins with is still told apart")
  assert.equal(pageOnSite("post:Acme/sections/pricing/hero", "SectionHero", sites), null,
    "SH1392: a section has no page of its own to open")
  assert.equal(pageOnSite("post:Elsewhere/x", "Page", sites), null)
})

test("SH1392: the editor frames the requested page, and the page entries offer it", () => {
  assert.match(read("app", "system", "bento.root.page.tsx"), /pageOnSite\(row\.address, row\.type, sites \?\? \[\]\)/u,
    "SH1392: the palette offers a page on itself, beside its form")
  const editor = read("app", "editor", "visual-editor.page.tsx")
  assert.match(editor, /siteDestination\(site\.data, framePath\(requestedPath\)\)/u,
    "SH1392: the frame's first src is the requested page, not the site's home")
  assert.match(editor, /followFrameRef\.current\(reported\)/u,
    "SH1392: each bridged load the frame reports moves the address with it")
  assert.match(editor, /history\.replaceState/u,
    "SH1392: by replacing the history entry, so following never reloads the page being edited")
  assert.match(read("app", "content", "post.edit.page.tsx"), /editorHref\(ROUTES\.VISUAL_EDITOR, siteId, post\.furl\)/u,
    "SH1392: a page's post editor opens it in the editor on itself")
  assert.match(read("app", "content", "content-browser", "site-pages.tsx"), /editorHref\(ROUTES\.VISUAL_EDITOR, siteId, node\.path\)/u,
    "SH1392: and so does each page row of the site's pages view")
})

test("SH1394: a page opens in the Visual Editor by default, and its form is a mode of it", () => {
  const palette = read("app", "system", "bento.root.page.tsx")
  assert.match(palette, /\[\{ \.\.\.form, route: editorHref\(ROUTES\.VISUAL_EDITOR, page\.siteId, page\.path\) \}/u,
    "SH1394: a page's own palette row opens the editor, not the form")
  assert.match(palette, /id: `\$\{row\.address\}\|fields`/u,
    "SH1394: and its form is the Edit fields row after it")
  const pages = read("app", "content", "content-browser", "site-pages.tsx")
  assert.match(pages, /<Link to=\{siteId \? editorHref\(ROUTES\.VISUAL_EDITOR, siteId, node\.path\) : /u,
    "SH1394: a page's title in the Pages view opens the editor")
  const editor = read("app", "editor", "visual-editor.page.tsx")
  assert.match(editor, /const fieldsTarget = selected\?\.postId \?\? selectedSection\?\.postId \?\? pageOfFrame/u,
    "SH1394: Fields is the form of the selection, else the section, else the page")
})

test("SH1394: the search page opens a page in the editor; notes, insights and home's drafts keep the form", () => {
  assert.match(read("app", "content", "search.page.tsx"),
    /if \(page\) navigate\(editorHref\(ROUTES\.VISUAL_EDITOR, page\.siteId, page\.path\)\)/u,
    "SH1394: a page found on the search page opens on itself, as it does from the palette")
  const home = read("app", "system", "home.page.tsx")
  // Deliberate: a note and a page's insights are drawn by the form (the note rail, the Insights
  // tab), and home reads no site list (SH1050), so its page drafts keep the form, one click from the page.
  assert.match(home, /content\/post\/\$\{note\.objectId\}/u, "SH1394: a note still opens where it is resolved")
  assert.match(home, /content\/post\/\$\{page\.id\}\?tab=insights/u, "SH1394: a failed check still opens its insights")
})

test("SH1399: Fields draws the form in the panel, saved with the page's own changes", () => {
  const editor = read("app", "editor", "visual-editor.page.tsx")
  assert.match(editor, /aria-pressed=\{fieldsMode \? "true" : "false"\} onClick=\{\(\) => setFieldsMode\(\(on\) => !on\)\}/u,
    "SH1399: Fields is a toggle in the editor, not a link to another screen")
  assert.match(editor, /onEdit=\{\(field, value\) => editWidgetField\(field, value, fieldsTarget\)\}/u,
    "SH1399: its edits are the editor's pending changes, so one Save and one Publish write both modes")
  assert.match(editor, /focusProp=\{selected\?\.postId === fieldsTarget \? selected\?\.prop : null\}/u,
    "SH1399: the field the curator had selected is the one the form opens on")
})

test("SH1400: the editor's Publish asks with the form's own confirmation, after saving", () => {
  const editor = read("app", "editor", "visual-editor.page.tsx")
  assert.doesNotMatch(editor, /onClick=\{\(\) => persist\(true\)\}/u,
    "SH1400: Publish went live at once again, with no plan and no question (P7)")
  assert.match(editor, /onClick=\{\(\) => \{ void requestPublish\(\) \}\}/u)
  assert.match(editor, /if \(!\(await persist\(false\)\)\) return/u,
    "SH1400: the pending changes are saved first, so the plan is of exactly what would go live")
  assert.match(editor, /<PublishPreviewDialog/u, "SH1400: the confirmation is the form's own dialog")
})
