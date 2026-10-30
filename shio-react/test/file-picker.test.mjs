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
 * SH1325 — the file picker moved under the pointer.
 *
 * One click selected a folder and added the path footer; the centred dialog grew and moved up 26px,
 * and the second click of the double-click opened the folder below. The live proof is a real
 * double-click on a clean instance; what runs here is the shape that keeps it from coming back.
 */
const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src")
const dialog = stripComments(readFileSync(join(SRC, "components", "widgets", "content-browser-dialog.tsx"), "utf8"))

test("SH1325: the selection footer holds its height before anything is selected", () => {
  assert.doesNotMatch(dialog, /\{selectedItem && \(\s*<div className="flex items-center gap-2 rounded-md/u,
    "SH1325: the footer appears on the first click again, so the dialog moves before the second")
  assert.match(dialog, /<div className=\{`flex h-9 items-center/u,
    "SH1325: the footer has no fixed height, so selecting a row resizes the centred dialog")
})

test("SH1325: in file mode a folder opens on one click, and is never a selectable answer", () => {
  assert.match(dialog, /onClick=\{\(\) => selectMode === "file"\s*\? navigateToFolder\(folder\)/u,
    "SH1325: a folder in the image picker is selected again, and Select accepts it for an image field")
})

test("SH1325: opening a folder is a control with a name, not a hover decoration", () => {
  assert.match(dialog, /aria-label=\{t\("content\.openFolderNamed", \{ name: folder\.name \}\)\}/u,
    "SH1325: the chevron that says open is decoration again, unreachable by keyboard")
})

test("SH1325: an image picker with no folder opens beside the post being edited", () => {
  assert.match(dialog, /`\/v2\/post-unified\/\$\{nearPostId\}`/u,
    "SH1325: the picker ignores the post it was opened from and starts on the list of every site")
  const widget = stripComments(readFileSync(join(SRC, "components", "widgets", "file.widget.tsx"), "utf8"))
  assert.match(widget, /selectMode="file"\s+nearPostId=\{postId\}/u,
    "SH1325: the File widget no longer tells its picker which post it belongs to")
})
