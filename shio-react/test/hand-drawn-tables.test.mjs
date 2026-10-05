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
  "app/admin/activity/activity.list.page.tsx": "SH1233",
  "app/admin/group-members.tsx": "SH1234",
  "app/content/forms.page.tsx": "SH1230",
  "app/content/languages.page.tsx": "SH1231",
  "app/content/notes.page.tsx": "SH1232",
  "app/content/post-type.list.page.tsx": "SH1225",
  "app/content/scheduled.page.tsx": "SH1229",
  "app/content/search.page.tsx": "SH1228",
  "app/content/static-file.list.page.tsx": "SH1226",
  "app/content/trash.page.tsx": "SH1227",
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
