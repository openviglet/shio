/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import assert from "node:assert/strict"
import { readFileSync, readdirSync, statSync } from "node:fs"
import { dirname, join, relative, sep } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"

/**
 * SH1224 — the console lists that draw their own table instead of the design system's.
 *
 * Sites, the admin lists and the content browser render `BentoDataTable`, which sorts from its
 * headers, has a row keyboard model, a column menu and virtualizes. Ten screens render shadcn's
 * `Table` from `components/ui/table` directly and have none of that, so every change to the
 * package's table skips them, and a redraw of "the lists" would have to be done by hand ten times.
 *
 * This is the grep, landed before any screen moves (SH322): it names every source file that imports
 * the hand-drawn table, holds today's set as a debt with the task that removes each entry, fails on
 * a new one, and fails on an entry that has moved so the list can only shrink. The `ui/table`
 * primitive itself is not a screen and is not scanned.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const SRC = join(ROOT, "src")

/** Each hand-drawn table, and the open task that moves it onto `BentoDataTable`. */
const DEBT = {
}

/** An import of the hand-drawn table primitive, by alias or by relative path. */
const HAND_DRAWN = /import\s+[^;]*?from\s+["'](?:@\/|(?:\.\.?\/)+)components\/ui\/table["']/

export function drawsItsOwnTable(source) {
  return HAND_DRAWN.test(source)
}

function sources(dir) {
  const found = []
  for (const name of readdirSync(dir)) {
    const path = join(dir, name)
    if (statSync(path).isDirectory()) found.push(...sources(path))
    else if (/\.(ts|tsx)$/.test(name)) found.push(path)
  }
  return found
}

function handDrawn() {
  return sources(SRC)
    .map((path) => relative(SRC, path).split(sep).join("/"))
    .filter((path) => path !== "components/ui/table.tsx")
    .filter((path) => drawsItsOwnTable(readFileSync(join(SRC, path), "utf8")))
    .sort()
}

test("SH1224: every hand-drawn list table is named, with the task that removes it", () => {
  const found = handDrawn()
  console.log(`SH1224: ${found.length} console screen(s) draw their own table; `
    + `${Object.keys(DEBT).length} are held as debt.`)
  const added = found.filter((path) => !(path in DEBT))
  assert.deepEqual(added, [],
    "SH1224: a screen started drawing its own table. Render BentoDataTable (through BentoListPage's "
    + "`columns`, or directly) so it sorts, selects and reaches the keyboard like every other list.")
  const moved = Object.keys(DEBT).filter((path) => !found.includes(path))
  assert.deepEqual(moved, [],
    "SH1224: a screen on the debt list no longer imports components/ui/table. Delete its entry in "
    + "the same commit, so the list only shrinks and a moved screen cannot rejoin it unnoticed.")
})

test("SH1225: the post types list is the package's table, with its verbs in the row menu", () => {
  const page = readFileSync(join(SRC, "app", "content", "post-type.list.page.tsx"), "utf8")
  assert.match(page, /<BentoListPage\b/, "SH1225: the post types list no longer renders BentoListPage")
  assert.match(page, /rowActions=\{rowActions\}/,
    "SH1225: Edit and Delete belong in the row's actions menu, which a keyboard reaches, not in a column of buttons")
  assert.match(page, /<BentoIdentityCell\b/, "SH1225: the type is named by the package's identity cell")
})

test("SH1226: the media rows are the package's table, and no media action is hover-only", () => {
  const page = readFileSync(join(SRC, "app", "content", "static-file.list.page.tsx"), "utf8")
  assert.match(page, /<BentoDataTable\b[\s\S]*selectionActions=\{selectionActions\}/,
    "SH1226: the media rows view no longer renders the package's table with its selection bar")
  assert.match(page, /id: "download"[\s\S]*id: "delete"/, "SH1226: Download and Delete are the row's menu")
  for (const hidden of page.matchAll(/opacity-0 group-hover:opacity-100([^"]*)"/g)) {
    assert.match(hidden[1], /group-focus-within:opacity-100/,
      "SH1226: a control shown only on hover is one a keyboard user tabs onto and cannot see")
  }
})

test("SH1227: the trash restores and purges a selection, and says when each item went in", () => {
  const page = readFileSync(join(SRC, "app", "content", "trash.page.tsx"), "utf8")
  assert.match(page, /selectionActions=\{\[restoreAction, purgeAction\]\}/,
    "SH1227: restoring or purging many items needs the table's selection")
  assert.match(page, /selectionScope=\{`\$\{q\}\|\$\{type\}`\}/,
    "SH1227: a selection must not outlive the filter it was made under")
  assert.match(page, /t\("content\.trashPurgeManyConfirm"/, "SH1227: purging many asks once, naming how many")
  assert.match(page, /<BentoChangeCell at=\{row\.deleted\} \/>/, "SH1227: the deletion time is the change cell")
})

test("SH1228: search results sort and open from the keyboard on the package's table", () => {
  const page = readFileSync(join(SRC, "app", "content", "search.page.tsx"), "utf8")
  assert.match(page, /<BentoDataTable\b[\s\S]*onRowOpen=\{openRow\}/,
    "SH1228: a result no longer opens its post from the keyboard")
  assert.match(page, /<BentoIdentityCell name=\{row\.title \|\| "\(untitled\)"\} detail=\{row\.address\} \/>/,
    "SH1228: a result is named by its title, with the address under it")
})

test("SH1229: the scheduled list is the package's table beside its calendar, in time order", () => {
  const page = readFileSync(join(SRC, "app", "content", "scheduled.page.tsx"), "utf8")
  assert.match(page, /<BentoDataTable\b[\s\S]*rows=\{entries\}/,
    "SH1229: the agenda's list view no longer renders the package's table")
  assert.match(page, /<BentoCalendar\b/, "SH1229: the calendar view stays the package's calendar")
  assert.match(page, /id: "when"[\s\S]*sortValue: \(entry\) => new Date\(entry\.at\)/,
    "SH1229: the time column sorts by the instant, which is the page's order")
})

test("SH1230: form submissions virtualize and trash a selection on the package's table", () => {
  const page = readFileSync(join(SRC, "app", "content", "forms.page.tsx"), "utf8")
  assert.match(page, /<BentoDataTable\b[\s\S]*selectionActions=\{\[trashAction\]\}/,
    "SH1230: submissions grow without bound and need the table's selection to be cleared in bulk")
  assert.match(page, /id: "trash"[^}]*tone: "destructive"/, "SH1230: Trash stays the one, destructive, verb")
})

test("SH1231: the languages board sorts by coverage on the package's table", () => {
  const page = readFileSync(join(SRC, "app", "content", "languages.page.tsx"), "utf8")
  assert.match(page, /<BentoDataTable\b/, "SH1231: the languages board no longer renders the package's table")
  assert.match(page, /id: "coverage"[\s\S]*sortValue: \(row\) => \(locales\.length \? current\(row\) \/ locales\.length : 0\)/,
    "SH1231: coverage is a sortable column, the share of languages a page is current in")
  assert.match(page, /sortValue: \(row\) => rank\(row\.cells\[locale\]\)/,
    "SH1231: each language column sorts missing first")
})

test("SH1232: open notes resolve one at a time or as a selection, and name who left them", () => {
  const page = readFileSync(join(SRC, "app", "content", "notes.page.tsx"), "utf8")
  assert.match(page, /selectionActions=\{\[resolveAction\]\}/,
    "SH1232: several dealt-with notes should close in one gesture")
  assert.match(page, /<BentoChangeCell at=\{note\.createdAt\} actor=\{note\.author \?\? undefined\} \/>/,
    "SH1232: when a note was left is the change cell, naming who left it")
})

test("SH1233: the activity trail is the package's table, paged by the same cursor", () => {
  const page = readFileSync(join(SRC, "app", "admin", "activity", "activity.list.page.tsx"), "utf8")
  assert.match(page, /<BentoDataTable\b[\s\S]*rows=\{items\}/, "SH1233: the audit rows are not the package's table")
  // SH1314 — `inline` keeps the actor on the time's line, so a long log stays one line a row.
  assert.match(page, /<BentoChangeCell at=\{item\.date\} actor=\{item\.owner\} agent=\{Boolean\(item\.agent\)\}(?: inline)? \/>/,
    "SH1233: when and by whom is the change cell, marked when an agent did it")
  assert.match(page, /onSelect: \(\[item\]\) => narrow\("actor", item\.owner\)/,
    "SH1233: the name-as-filter is still one gesture away, in the row's menu")
})

test("SH1234: a group's members leave as a selection in one write", () => {
  const panel = readFileSync(join(SRC, "app", "admin", "group-members.tsx"), "utf8")
  assert.match(panel, /selectionActions=\{\[removeAction\]\}/,
    "SH1234: removing several members needs the table's selection")
  assert.match(panel, /return save\(members\.filter\(\(member\) => !leaving\.has\(member\.username\)\), message\)/,
    "SH1234: a selection is one membership write, not a save per person")
})

test("SH1224: the scan sees an alias import, a relative one, and nothing else", () => {
  assert.equal(drawsItsOwnTable(
    'import { Table, TableBody } from "@/components/ui/table"'), true)
  assert.equal(drawsItsOwnTable(
    "import {\n  Table,\n  TableRow,\n} from '../../components/ui/table'"), true)
  assert.equal(drawsItsOwnTable(
    'import { BentoDataTable } from "@viglet/viglet-design-system/bento"'), false,
    "SH1224: the package's table is the destination, not the debt")
  assert.equal(drawsItsOwnTable('import { tableKey } from "@/lib/table-keys"'), false)
})
