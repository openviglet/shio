/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { readdirSync, readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"

/**
 * SH1419 — the editor is the Visual Editor. "Universal Editor" is Adobe AEM's product name, and
 * Shio used it for its own editor in the same market. What a curator reads (every string the
 * console ships, in both languages, and every source file) must not carry the old name, and the
 * old route must still land, because bookmarks from before the rename point at it.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")
const OLD = /universal[-_ ]?editor|editor universal/i

function walk(dir) {
  return readdirSync(dir, { withFileTypes: true }).flatMap(e =>
    e.isDirectory() ? walk(join(dir, e.name)) : [join(dir, e.name)])
}

test("SH1419: no console source or string carries the old name outside the legacy redirect", () => {
  const offenders = walk(SRC)
    .filter(f => /\.(tsx?|json)$/.test(f))
    .flatMap(f => readFileSync(f, "utf8").split("\n")
      .map((line, i) => ({ f, i: i + 1, line }))
      .filter(({ line }) => OLD.test(line) && !line.includes("/bento/universal-editor/:siteId")))
    .map(({ f, i }) => `${f.slice(SRC.length + 1)}:${i}`)
  assert.deepEqual(offenders, [],
    "SH1419: the console names its editor 'Universal Editor' again, Adobe AEM's product name")
})

test("SH1419: the old editor route redirects to the Visual Editor", () => {
  const app = readFileSync(join(SRC, "App.tsx"), "utf8")
  assert.match(app, /path="\/bento\/universal-editor\/:siteId" element=\{<LegacyEditorRedirect \/>\}/,
    "SH1419: the old /bento/universal-editor route is gone, so a bookmark from before the rename 404s")
  assert.match(app, /<Navigate to=\{`\/bento\/visual-editor\/\$\{siteId/,
    "SH1419: the legacy route no longer lands on the Visual Editor")
})
