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
  browserView, editableTitleField, listboxClass, readTableLayout, stepFor, tableLayoutKey, thumbnailSrc,
  fullWindow, visibleWindow, WINDOW_THRESHOLD,
} from "../src/app/content/content-browser/browser-view.ts"
import { browserSource } from "./support/browser-source.mjs"

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

test("SH1026: the URL's view word is read as a view, and an unknown one is the list", () => {
  for (const view of ["list", "grid", "spreadsheet"]) assert.equal(browserView(view), view)
  assert.equal(browserView("kanban"), "list", "SH1026: an unknown view word drew nothing")
  assert.equal(browserView(""), "list")
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
 * SH1142 — in the table view, the table is the only thing that writes the selection.
 *
 * <h2>What was wrong, and why the assertion is not the one the design named</h2>
 *
 * <p>`BentoDataTable` owns its selection and reports it through `onSelectionChange`, which the page
 * copies into `selectedItems`. It takes none in: its props have no `selectedIds`, where `layout`
 * <em>is</em> controlled beside `onLayoutChange`. So the toolbar's Select menu and the selection
 * bar's Clear wrote the page's set, nothing on screen changed, and the next click in the table
 * reported its own set and overwrote the choice. Pick "Select all", then Delete, and the rows
 * deleted were whatever the table last had ticked while the confirm dialog counted the page's set.</p>
 *
 * <p>§LXIX.11's assertion was to choose "Select all" and compare the table's checked rows with the
 * page's selection. That describes the <b>design system's</b> fix — a controlled `selectedIds` — and
 * it cannot be written against the correction available on this side, because after it there is no
 * "Select all" in the toolbar to choose. Restated to the property that correction actually
 * establishes: <b>no control outside the table writes the selection in the table view.</b></p>
 *
 * <p>Held as a rule over every writer rather than as two spot-checks on the two controls that were
 * wrong, because the recurrence is a <i>third</i> control added to that toolbar by somebody who never
 * read this. The two known ones are also named, so the failure says which.</p>
 */
test("SH1142: no control outside the table writes the selection in the table view", () => {
  // SH1145 — the browser is a directory: the Select menu is in `browser-toolbar.tsx` and the
  // selection bar is still on the page, so the subject of this rule is both files.
  const raw = browser()

  /*
   * Comment lines blanked in place — equal length, so the prose above (which quotes
   * `setSelectedItems` and the guard) is not read as code and every offset below is still an offset
   * into the real file. `stripComments` would be wrong here for SH786's reason: it deletes lines, so
   * an index into its result means nothing in the original, and this reader needs to know what
   * *encloses* what.
   *
   * `{/*` is in the list, and leaving it out is what the first draft did. A JSX comment's closing
   * `*&#47;}` line starts with `*`, so it was blanked and took its `}` with it, while the opening
   * `{/*` line was not — three comments in this page, three unbalanced braces, and the brace walk
   * below ran to the end of the file. Blanking both ends keeps the depth honest. (The shared
   * stripper gets this right by another route: SH1118's `opensALine` accepts `{/*` as an opener.)
   */
  const source = raw.split("\n")
    .map((line) => {
      const text = line.trim()
      return text.startsWith("//") || text.startsWith("*") || text.startsWith("/*")
        || text.startsWith("{/*")
        ? " ".repeat(line.length)
        : line
    })
    .join("\n")

  const GUARD = /\{viewMode !== "spreadsheet" &&/g
  /** Every `{viewMode !== "spreadsheet" && … }` region, as [start, end) offsets. */
  const guarded = []
  for (const match of source.matchAll(GUARD)) {
    let depth = 0
    let at = match.index
    for (; at < source.length; at += 1) {
      if (source[at] === "{") depth += 1
      else if (source[at] === "}") {
        depth -= 1
        if (depth === 0) break
      }
    }
    assert.ok(depth === 0, "SH1142: a viewMode guard's braces do not balance, so this reader cannot "
      + "tell what it covers — which would make every assertion below vacuous")
    guarded.push([match.index, at])
  }
  assert.ok(guarded.length >= 3,
    `SH1142: ${guarded.length} table-view guard(s) found. The listbox is one and the two selection `
    + "controls are the others, so fewer than three means this reader stopped matching and would "
    + "pass by finding nothing to check")

  const inside = (at) => guarded.some(([from, to]) => at > from && at < to)

  /*
   * A JSX control that writes the selection: an `onClick` naming any of the three ways to do it.
   *
   * SH1145 — `onSelectAction` is the third, and leaving it out is what this reader did for one run
   * after the toolbar moved into its own component: the menu items call the prop there, so five of
   * the six controls became invisible and only the page's own Clear was left. The count floor below
   * is what said so rather than a green run over one control.
   */
  const WRITER = /onClick=\{[^}]*\b(?:setSelectedItems|handleSelectAction|onSelectAction)\b[^}]*\}/g
  const writers = [...source.matchAll(WRITER)]
  assert.ok(writers.length >= 6,
    `SH1142: ${writers.length} selection-writing control(s) found; the Select menu alone has five `
    + "items and Clear is the sixth, so a smaller number means the pattern no longer matches")

  const unguarded = writers
    .filter((match) => !inside(match.index))
    .map((match) => `${source.slice(0, match.index).split("\n").length}: ${match[0].slice(0, 90)}`)

  assert.deepEqual(unguarded, [],
    "SH1142: this control writes the page's selection and renders in the table view, where the "
      + "table will go on showing whatever it had ticked. The two sets then differ under a bulk "
      + "verb: the confirm dialog counts this one and the delete takes the table's. Either gate it "
      + "on `viewMode !== \"spreadsheet\"`, or — better — pass the selection INTO BentoDataTable "
      + "once the design system ships a controlled `selectedIds` beside `onSelectionChange`, and "
      + "then every view can have these controls again.\n  " + unguarded.join("\n  "))

  // And the two that were wrong, by name, so a regression is not merely "a rule broke".
  for (const [label, key] of [["the Select menu", "content.select"],
    ["the selection bar's Clear", "content.clearSelection"]]) {
    const at = source.indexOf(`t("${key}")`)
    assert.ok(at > 0, `SH1142: ${label} is gone from this page; update this assertion or the id it names`)
    assert.ok(inside(at), `SH1142: ${label} renders in the table view again — the control SH1142 `
      + "removed from it, because it writes a selection the table never hears about")
  }
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
