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
import { copyReference } from "../src/app/editor/section-reference.ts"

/**
 * SH1101 — the visual editor's Duplicate places an independent copy, not the same section twice.
 *
 * Two placements of one section share a heading, so editing either edits both; two copies do not.
 * The server half (the copy answers with the post it made) is ShFolderAPITest's; this holds the
 * editor half: the copy is what gets placed, and it is named the way its neighbour is.
 */
const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src")

test("SH1101: the copy is named in its neighbour's spelling", () => {
  const copy = { id: "c0ffee", furl: "hero-2" }
  assert.equal(copyReference("/sections/hero", copy), "/sections/hero-2",
    "SH1101: a path entry keeps its folder and takes the copy's own friendly URL")
  assert.equal(copyReference("a1b2c3", copy), "c0ffee",
    "SH1101: an id entry is replaced by the copy's id, never repeated")
  assert.equal(copyReference("/sections/hero", { id: "c0ffee" }), "c0ffee",
    "SH1101: with no friendly URL to spell, the id is the reference that still resolves")
})

test("SH1101: Duplicate copies the section before it places it, and never places the original twice", () => {
  const page = readFileSync(join(SRC, "app", "editor", "visual-editor.page.tsx"), "utf8")
  assert.match(page, /if \(op === "duplicate"\) \{\s*await duplicateSection\(\)/,
    "SH1101: the duplicate op still splices the same entry in again")
  assert.doesNotMatch(page, /next\.splice\(index \+ 1, 0, next\[index\]\)/,
    "SH1101: a second placement of one post is what Duplicate used to do, and editing one edited both")
  assert.match(page, /editorService\.copy\(section\.postId,[\s\S]*?copyReference\(entries\[section\.index\], copy\)/,
    "SH1101: the placed entry must be the copy the server just made")
})
