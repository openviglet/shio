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

import { stripComments } from "./support/source-text.mjs"

/**
 * SH1312 — the media library had three control rows above its grid, and drew a font as a 290px
 * grey square beside the photos.
 */
const code = stripComments(readFileSync(join(dirname(fileURLToPath(import.meta.url)), "..", "src",
  "app", "content", "static-file.list.page.tsx"), "utf8"))

test("SH1312: Upload and where it goes are the hero's actions, not a row of their own", () => {
  const hero = code.slice(code.indexOf("<PageHero"), code.indexOf('className="relative"'))
  assert.match(hero, /actions=\{/, "SH1312: the hero has no actions, so Upload is a row again")
  assert.match(hero, /aria-label=\{t\("staticFile\.uploadTarget"\)\}/,
    "SH1312: the upload destination is not beside Upload in the hero")
  assert.match(hero, /\{t\("staticFile\.upload"\)\}/, "SH1312: Upload is not in the hero")
  assert.equal(code.match(/t\("staticFile\.uploadTarget"\)/g)?.length, 2,
    "SH1312: the site picker is drawn twice, in the hero and in a row below it")
})

test("SH1312: a file with no preview gets a short band, and only an image gets a square", () => {
  assert.match(code, /\{image \? \(\s*<div className="aspect-square/u,
    "SH1312: the square is drawn whatever the file is again")
  assert.match(code, /<div className="bg-muted flex h-16 items-center gap-2 px-3">/u,
    "SH1312: a non-image tile is not the compact band")
  assert.match(code, /\{extensionOf\(item\)\}/u, "SH1312: the band does not name the file's kind")
})
