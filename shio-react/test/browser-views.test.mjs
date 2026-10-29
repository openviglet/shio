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
import {
  breadcrumbFrom, browserView, editableTitleField, listboxClass, readTableLayout, searchBackState, searchBackTo, searchHereFor, soleRootFolder, stepFor, suggestedPostType,
  tableLayoutKey, thumbnailSrc, fullWindow, visibleWindow, WINDOW_THRESHOLD,
} from "../src/app/content/content-browser/browser-view.ts"
import { browserSource } from "./support/browser-source.mjs"
import { stripComments } from "./support/source-text.mjs"

/**
 * SH1026 — three view buttons that highlighted when pressed and rendered the identical list.
 *
 * `viewMode` was read only to colour the button that set it. What has to hold now is that the
 * choice reaches the render, that each view is a renderer of its own inside the one listbox (so
 * selection and the keyboard are shared, not copied), and that the URL's word is read safely.
 */
const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src")
const read = (...path) => readFileSync(join(SRC, ...path), "utf8")
/** SH1145 — the browser is a directory; a scan whose subject is the browser reads all of it. */
const browser = () => browserSource()

test("SH1026: the URL's view word is read as a view, and an unknown one is the default", () => {
  for (const view of ["list", "grid", "spreadsheet"]) assert.equal(browserView(view), view)
  assert.equal(browserView("kanban"), "spreadsheet", "SH1026: an unknown view word drew nothing")
  // SH1203 — the default is the table: the rows a curator compares by type, state and date.
  assert.equal(browserView(""), "spreadsheet")
})

test("SH1184: a root folder named after its site is not drawn twice, and no root is in the address", () => {
  const site = { id: "s", name: "sh8844001" }
  const blank = breadcrumbFrom({
    site,
    breadcrumb: [{ id: "r", name: "sh8844001", root: true }, { id: "m", name: "menu", root: false }],
  })
  assert.deepEqual(blank.map((crumb) => crumb.name), ["sh8844001", "menu"],
    "SH1184: the breadcrumb read sh8844001/sh8844001/menu — the site, then its root folder of the same name")
  assert.equal(new URLSearchParams(searchHereFor(site.name, blank, "/bento/content/search").split("?")[1]).get("folder"),
    "folder:sh8844001/menu")

  const home = breadcrumbFrom({
    site: { id: "s", name: "Stock" },
    breadcrumb: [{ id: "r", name: "Home", root: true }, { id: "n", name: "News", root: false }],
  })
  assert.deepEqual(home.map((crumb) => crumb.name), ["Stock", "Home", "News"],
    "SH1184: a root folder with a name of its own is a real step and stays on the path")
  assert.equal(new URLSearchParams(searchHereFor("Stock", home, "/bento/content/search").split("?")[1]).get("folder"),
    "folder:Stock/News",
    "SH1184: the address grammar stops below the root folder (ShAddressResolver.addressOf), so it is no segment")
  assert.deepEqual(breadcrumbFrom(undefined), [])
})

test("SH1274: a site whose only child is its root folder opens on that folder, and every path names the site once", () => {
  assert.equal(soleRootFolder({ folders: [{ id: "r", root: true }], posts: [] }), "r",
    "SH1274: opening quilhara showed one row, a folder called quilhara")
  assert.equal(soleRootFolder({ folders: [{ id: "r", root: true }, { id: "r2", root: true }], posts: [] }), null,
    "SH1274: two root folders are two places, and the site page shows both")
  assert.equal(soleRootFolder({ folders: [{ id: "f", root: false }], posts: [] }), null,
    "SH1274: a folder's single subfolder is a real step, not the site standing in")
  assert.equal(soleRootFolder({ folders: [{ id: "r", root: true }], posts: [{}] }), null)
  assert.equal(soleRootFolder(undefined), null)

  const widgets = ["file.widget.tsx", "content-select.widget.tsx", "content-browser-dialog.tsx"]
    .map((file) => stripComments(read("components", "widgets", file)))
  for (const source of widgets) {
    assert.ok(source.includes("breadcrumbFrom(data)"),
      "SH1274: a widget that builds a location from /path does it through breadcrumbFrom, which drops the root's copy of the site name")
    assert.ok(!/data\.breadcrumb\)/.test(source),
      "SH1274: and no widget walks data.breadcrumb itself, which is how the File field read quilhara > quilhara > images")
  }
})
test("SH1275: a folder's New button offers what the folder holds, not the first type the instance knows", () => {
  const types = [{ name: "Guide" }, { name: "Trail" }, { name: "File", system: true }, { name: "Story" }]
  const pick = (folder, here = null, anywhere = null) => suggestedPostType(types, folder, here, anywhere)?.name

  assert.equal(pick({ posts: [{ postType: "Trail" }, { postType: "Trail" }, { postType: "Guide" }] }), "Trail",
    "SH1275: /en/trails holds three Trails and the button said Novo Guide")
  assert.equal(pick({ counts: { Story: 7, Trail: 2 }, posts: [{ postType: "Trail" }] }), "Story",
    "SH1275: the server's counts cover every page of the listing, so they beat the rows on screen")
  assert.equal(pick({ posts: [{ postType: "File" }, { postType: "File" }] }, null, "Story"), "Story",
    "SH1275: a folder of images is filled by uploading, so a system type is never the suggestion")
  assert.equal(pick({ posts: [] }, "Story", "Guide"), "Story",
    "SH1275: an empty folder offers the type last created in it")
  assert.equal(pick({ posts: [] }, null, "Trail"), "Trail")
  assert.equal(pick({ posts: [] }), "Guide", "SH1275: and with nothing to go on, the first authorable type")
  assert.equal(suggestedPostType([], { posts: [] }, null, null), null)
})
test("SH1283: an empty folder offers the site's declared page type before guessing", () => {
  const types = [{ name: "Guide" }, { name: "InfoPage" }, { name: "Trail" }]
  assert.equal(suggestedPostType(types, { posts: [] }, null, "Guide", "InfoPage")?.name, "InfoPage",
    "SH1283: Quilhara's site root offered the catalogue's first type, 'How this was built'")
  assert.equal(suggestedPostType(types, { posts: [] }, "Trail", null, "InfoPage")?.name, "Trail",
    "SH1283: the type last made in this folder still wins, since it says more about the folder")
  assert.equal(suggestedPostType(types, { posts: [{ postType: "Guide" }] }, null, null, "InfoPage")?.name,
    "Guide", "SH1283: and what the folder already holds wins over both")
  assert.equal(suggestedPostType(types, { posts: [] }, null, null, "Gone")?.name, "Guide",
    "SH1283: a page type the catalogue no longer has is skipped, never offered")
})
test("SH1187: \"search in this folder\" opens the console's search route, not a bare /search", () => {
  const crumbs = [{ name: "favicons", type: "FOLDER" }]
  assert.ok(searchHereFor("VigR3", crumbs, "/bento/content/search").startsWith("/bento/content/search?"))
  const page = stripComments(read("app", "content", "object.children.page.tsx"))
  assert.match(page, /searchHereFor\([^)]*ROUTES\.SEARCH\)/,
    "SH1187: the browser linked to /search, which the router does not declare, and the button opened a 404")
})

test("SH1188: the search page's back link returns to the folder that opened it", () => {
  const from = searchBackState({ pathname: "/bento/content/object/abc", search: "?view=grid" })
  assert.equal(searchBackTo(from, "/bento/content"), "/bento/content/object/abc?view=grid",
    "SH1188: back from a folder search went to the site list, not to the folder being searched")
  assert.equal(searchBackTo(null, "/bento/content"), "/bento/content")
  assert.equal(searchBackTo({ from: "//evil.example" }, "/bento/content"), "/bento/content")
  const toolbar = stripComments(read("app", "content", "content-browser", "browser-toolbar.tsx"))
  assert.match(toolbar, /navigate\(searchHereHref, \{ state: searchBackState\(location\) \}\)/)
  const search = stripComments(read("app", "content", "search.page.tsx"))
  assert.match(search, /searchBackTo\(location\.state/)
  assert.match(search, /urlBase=\{backTo\}/)
})

test("SH1026: a grid steps across as well as down; the list and the table do not", () => {
  assert.equal(stepFor("grid", "ArrowRight"), 1)
  assert.equal(stepFor("grid", "ArrowLeft"), -1)
  assert.equal(stepFor("list", "ArrowRight"), 0,
    "SH1026: Right moved focus in a one-column list, which a screen reader does not announce")
  assert.equal(stepFor("spreadsheet", "ArrowDown"), 1)
  assert.notEqual(listboxClass("grid"), listboxClass("list"), "SH1026: the grid lays out as the list does")
})

test("SH1026: the chosen view reaches the render, through one renderer per view", () => {
  const page = browser()
  assert.match(page, /<BrowserItem[\s\S]*?view=\{viewMode\}/,
    "SH1026: the browser's rows are not drawn by the chosen view — the buttons colour themselves again")
  assert.match(page, /className=\{listboxClass\(viewMode\)\}/, "SH1026: the listbox ignores the view")
  // One listbox for every view, so selection, roving focus and Shift-extend are one implementation.
  // Attributes only: the keyboard handler's comment names the role in backticks.
  // SH1054 — the one list is a grid now (an option may not hold the row's controls).
  assert.equal(page.match(/role="grid"(?!`)/g)?.length, 1, "SH1026: a view grew a list of its own")
  const views = read("app", "content", "content-browser", "browser-views.tsx")
  for (const renderer of ["ListRow", "GridCard"]) {
    assert.match(views, new RegExp(`function ${renderer}\\(`), `SH1026: the ${renderer} renderer is gone`)
  }
  assert.match(views, /props\.view === "grid"/, "SH1026: BrowserItem does not dispatch on the view")
  assert.match(page, /viewMode === "spreadsheet" && sortedItems\.length > 0 && \(\s*<BrowserTable/,
    "SH1026: the table view is not the data table")
})

test("SH1026: the table is the design system's, with its layout kept and its selection reported", () => {
  const table = read("app", "content", "content-browser", "browser-table.tsx")
  assert.match(table, /<BentoDataTable/, "SH1026: the table view is hand-rolled again, so nothing virtualises it")
  assert.match(table, /layout=\{layout\}\s*\n\s*onLayoutChange=\{setLayout\}/,
    "SH1026: the column picker's choice is not kept, so every visit shows every column again")
  // The table makes rows selectable only when it has selection actions; without them the toolbar's
  // verbs would see an empty selection in this view.
  assert.match(table, /selectionActions=\{selectionActions\}/, "SH1026: the table's rows cannot be selected")
  assert.match(table, /rowActions=\{rowActions\}/, "SH1026: the table's rows lost the eight row actions")
  const page = browser()
  assert.match(page, /onSelectionChange=\{\(ids\) => setSelectedItems\(new Set\(ids\)\)\}/,
    "SH1026: the table's selection never reaches the page, so its bulk verbs act on nothing")
})

test("SH1026: a table cell edits the title only where one plain-text field is the title", () => {
  assert.equal(editableTitleField([{ name: "headline", isTitle: true, widget: "Text" }, { name: "body" }]),
    "headline")
  assert.equal(editableTitleField([{ name: "first", isTitle: true }, { name: "last", isTitle: true }]),
    undefined, "SH1026: a title joined from two fields would be written half by one cell")
  assert.equal(editableTitleField([{ name: "title", isTitle: true, widget: "HTML Editor" }]), undefined,
    "SH1026: a rich-text title flattened by a plain input loses its markup")
  assert.equal(editableTitleField([]), undefined)
})

test("SH1026: an inline title edit reads the draft first and merges under its version", () => {
  const cell = read("app", "content", "content-browser", "title-cell.tsx")
  // The read is what makes the write conditional: the service keeps the ETag it answered with and
  // sends it as If-Match on the merge, so a write in between is refused rather than overwritten.
  assert.match(cell, /await postService\.get\(row\.id, "DRAFT"\)/u,
    "SH1026: the edit opens on the listing's value, so the merge carries no version and overwrites")
  assert.match(cell, /await postService\.patch\(row\.id, \{ \[editing\.field\]: editing\.value \}\)/u,
    "SH1026: the inline edit is not the merge verb, so it writes the whole state")
  assert.match(cell, /postService\.forgetVersion\(row\.id\)/u,
    "SH1026: a refused edit keeps the stale version, so the next try is refused the same way")
  const table = read("app", "content", "content-browser", "browser-table.tsx")
  assert.match(table, /cell: \(row\) => <TitleCell row=\{row\} onSaved=\{afterEdit\} \/>/u,
    "SH1026: the table's title column is not the editable cell")
})

test("SH1026: a stored layout is read defensively, per user", () => {
  assert.deepEqual(readTableLayout(null), { hidden: [] })
  assert.deepEqual(readTableLayout("null"), { hidden: [] },
    "SH1026: the literal \"null\" parses to null and crashed the read, the widgetSettings trap")
  assert.deepEqual(readTableLayout("{not json"), { hidden: [] })
  assert.deepEqual(readTableLayout('{"hidden":["locale",3,"state"]}'), { hidden: ["locale", "state"] })
  assert.notEqual(tableLayoutKey("ana"), tableLayoutKey("bruno"),
    "SH1026: two curators on one browser hide each other's columns")
})

test("SH1026: the view buttons are named and say which is pressed", () => {
  const page = browser()
  for (const key of ["viewAsList", "viewAsGrid", "viewAsTable"]) {
    assert.match(page, new RegExp(`aria-label=\\{t\\("content\\.${key}"\\)\\} aria-pressed=`),
      `SH1026: the ${key} button is an unnamed icon again`)
  }
})

test("SH1026: a grid card shows the image the listing named, through the transform pipeline", () => {
  assert.equal(thumbnailSrc("/file_source/abc/hero%20image.png"),
    "/file_source/abc/hero%20image.png?w=320&h=192&crop=fill&format=webp",
    "SH1026: a card asks the pipeline for its own size, as the media library's thumbnails do")
  const views = read("app", "content", "content-browser", "browser-views.tsx")
  assert.match(views, /item\.thumbnail \? \(\s*<img src=\{thumbnailSrc\(item\.thumbnail\)\} alt="" loading="lazy"/,
    "SH1026: the card draws the kind's icon even when the listing names an image")
  assert.match(browser(), /thumbnail: post\.thumbnail,/,
    "SH1026: the row mapping drops the thumbnail the listing sent")
})

test("SH1026: past the threshold only the rows a viewport shows are mounted, with space for the rest", () => {
  assert.deepEqual(fullWindow(40), { start: 0, end: 40, before: 0, after: 0 })
  // 1000 rows of 50px, scrolled 5000px into the list: rows 100..~116 are on screen, 4 either side.
  const list = visibleWindow({ count: 1000, columns: 1, rowHeight: 50, top: -5000, viewportHeight: 800 })
  assert.deepEqual(list, { start: 96, end: 120, before: 4800, after: 44000 },
    "SH1026: a thousand-row list mounted a thousand rows, which is the cost virtualising removes")
  assert.equal(list.before + (list.end - list.start) * 50 + list.after, 1000 * 50,
    "SH1026: the spacers and the mounted rows must add up to the list, or the scrollbar lies")
  // A grid lays three to a row, so a window is whole rows of cards.
  const grid = visibleWindow({ count: 1000, columns: 3, rowHeight: 200, top: 0, viewportHeight: 800 })
  assert.deepEqual([grid.start, grid.end % 3, grid.before], [0, 0, 0])
  assert.equal(visibleWindow({ count: 10, columns: 1, rowHeight: 50, top: 2000, viewportHeight: 800 }).end, 4,
    "SH1026: a list below the fold still mounts its first rows, so it is there when scrolled to")
  assert.ok(WINDOW_THRESHOLD >= 100, "SH1026: a folder of a couple of pages mounts whole, as it did")

  const page = browser()
  assert.match(page, /sortedItems\.slice\(rowWindow\.start, rowWindow\.end\)/,
    "SH1026: the listbox maps every row, so the window the hook measured is never used")
  // SH1054 — the grid's own terms for the same two facts: the count on the grid, the place on the row.
  assert.match(page, /aria-rowcount=\{sortedItems\.length\}[\s\S]*?aria-rowindex=\{index \+ 1\}/,
    "SH1026: a windowed list must state its size, or a screen reader counts only the mounted rows")
  assert.match(page, /pendingFocus\.current = bounded\s*reveal\(bounded\)/,
    "SH1026: an arrow to a row outside the window focuses nothing, so the keyboard stops at the window's edge")
  assert.match(page, /tabIndex=\{index === tabStop \? 0 : -1\}/,
    "SH1026: with the focused row scrolled away the list must keep a tab stop, or Tab skips it")
})

test("SH1026: the listing's filters change only when the query does, or page 0 is re-read on every render", () => {
  const page = browser()
  // SH1145 — the memo moved into `use-browser-listing`, which takes the committed query as `listKey`.
  assert.match(page, /const filters = useMemo\(\s*\(\) => browserFilters\(JSON\.parse\(listKey\)[^)]*\),\s*\[listKey\]\)/,
    "SH1026: `filters` rebuilt per render makes `fetchPage` new per render, and the listing effect then "
      + "re-reads page 0 after every render — a stream of requests that also discards every load-more")
  assert.match(page, /\}, \[objectId, sortBy, filters\]\)/, "SH1026: fetchPage keys on the memoised filters")
})

/**
 * SH1148 — one selection in every view, because the table is controlled by the page's set.
 *
 * <p>SH1142 found the defect: `BentoDataTable` took no selection in, so the toolbar's Select menu
 * wrote `selectedItems` while the table went on ticking its own rows, and "Select all" then Delete
 * could delete the table's rows under a dialog counting the page's. Its correction was a
 * subtraction, the menu gone from the table view. VDS174 gave the table `selectedIds` beside
 * `onSelectionChange`, so the stronger property SH1142 could not state is now the one held: the
 * page's set IS what the table ticks, and so the menu is back in every view.</p>
 *
 * <p>Read at both ends of the flow, since the defect was a flow that ran one way: the page hands
 * its own set in, the wrapper hands it to the component, and nothing gates the Select menu on the
 * view any more. `console-check.mjs`'s `table-select-all` probe is the live half: it chooses the
 * restored "Select everything" in the table view and compares the ticked rows with the count.</p>
 */
test("SH1148: the table is controlled by the page's selection, and the Select menu is in every view", () => {
  const read = (...parts) => stripComments(readFileSync(join(SRC, "app", "content", ...parts), "utf8"))
  const table = read("content-browser", "browser-table.tsx")
  assert.match(table, /<BentoDataTable[\s\S]*?\bselectedIds=\{selectedIds\}[\s\S]*?\bonSelectionChange=\{onSelectionChange\}/,
    "SH1142/SH1148: the table is not handed the selection, so it keeps its own and the toolbar writes a "
      + "set nobody can see — the defect SH1142 found")

  const page = read("object.children.page.tsx")
  assert.match(page, /const selectedIdList = useMemo\(\(\) => \[\.\.\.selectedItems\], \[selectedItems\]\)/,
    "SH1148: the list the table is controlled by is not built from the page's own set")
  assert.match(page, /<BrowserTable[\s\S]*?selectedIds=\{selectedIdList\}/,
    "SH1148: the page does not pass its selection into the table")

  const toolbar = read("content-browser", "browser-toolbar.tsx")
  const menu = toolbar.indexOf('t("content.select")')
  assert.ok(menu > 0, "SH1148: the Select menu is gone from the toolbar; update this assertion")
  const before = toolbar.slice(0, menu)
  const guard = before.lastIndexOf('{viewMode !== "spreadsheet" &&')
  const closedSince = guard < 0 || /\n\s*\)\}\n/.test(before.slice(guard))
  assert.ok(closedSince,
    "SH1148: the Select menu is gated on the view again. It writes the set the table is controlled "
      + "by, so it belongs in every view; gating it is SH1142's subtraction, which this reversed")
})

/**
 * SH1145 — the browser stays a directory, and the page stays reviewable.
 *
 * <p>SH1026's design asked for this split "because at that size the next feature makes it
 * unreviewable", and moved what each view draws. The page kept three concerns tangled in one
 * component and stood at 1,593 lines. The cost was never only size: SH1026's own live check found
 * the listing effect re-reading page 0 after every render, from a value built in the component
 * body, and nothing in a file that size made it visible.</p>
 *
 * <p>A line ceiling is a blunt instrument and it is the one the design named, so it is asserted —
 * but the half that matters is the second: each concern is a module a reviewer can read whole, and
 * the page must not quietly take one back.</p>
 */
test("SH1145: the browser page is under 600 lines, and its concerns are modules", () => {
  const page = read("app", "content", "object.children.page.tsx").split("\n").length
  assert.ok(page < 600,
    `SH1145: the browser page is ${page} lines, over the 600 this task set. It was 1,593 when the `
    + "reads, the listbox, the verbs and four dialogs were all inside it, which is where a "
    + "re-read-per-render hid in plain sight. Put the new work in `content-browser/` beside the "
    + "rest, not back in the page.")

  // Each concern the design named, and what it is for — so a failure says which one came back.
  const OWNS = {
    "use-browser-listing.ts": "the listing's reads: the page, the next page, the re-read after a write",
    "use-browser-keyboard.ts": "the listbox: roving focus, ranges, Space, Enter, Delete",
    "use-bulk-verbs.ts": "the three verbs a selection takes, planned before they are carried out",
    "use-browser-open.ts": "duplicate a row, open the site, open a page",
    "browser-toolbar.tsx": "both toolbars",
    "folder-picker.dialog.tsx": "where a copy or a move is going",
    "bulk-plan.dialog.tsx": "the plan sheet",
    "row-actions.ts": "the seven verbs a row offers",
    "browser-listing.ts": "the envelope, its rows and the constants that shape a request",
  }
  const missing = Object.keys(OWNS).filter((name) => {
    try {
      return read("app", "content", "content-browser", name).length === 0
    } catch {
      return true
    }
  })
  assert.deepEqual(missing, [],
    "SH1145: a module the split created is gone. If it was renamed, rename it here; if its code "
      + "went back into the page, that is the defect — each of these is "
      + missing.map((name) => `${name} (${OWNS[name]})`).join(", "))

  // And the page does not simply re-export them: it renders them.
  const source = read("app", "content", "object.children.page.tsx")
  for (const hook of ["useBrowserListing", "useBrowserKeyboard", "useBulkVerbs", "useBrowserOpen"]) {
    assert.match(source, new RegExp(`${hook}\\(`),
      `SH1145: the page does not call ${hook}, so whatever that module holds is not what the `
        + "browser runs")
  }
})
