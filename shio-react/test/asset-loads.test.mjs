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

/**
 * SH1280 — the media card's half of one meaning of "used".
 *
 * The card spelled a zero reference count as *unused*, and a theme's font is referenced by no
 * field and loaded by every page, so Quilhara's three fonts read as clutter while `verify` (SH1268)
 * and `shio assets prune` said they were in use. A zero is now three answers, and only one of them
 * says "unused": pages load it, page loads were not counted, or neither.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const PAGE = readFileSync(
  join(ROOT, "src", "app", "content", "static-file.list.page.tsx"), "utf8")
const SERVICE = readFileSync(
  join(ROOT, "src", "services", "static-file", "static-file.service.ts"), "utf8")
const code = PAGE.split("\n").filter((line) => !/^\s*(\/\/|\*|\/\*)/u.test(line)).join("\n")

test("SH1280: the listing asks for page loads only when the curator does", () => {
  assert.match(SERVICE, /if \(filter\?\.loads\) params\.set\("loads", "true"\)/u)
  assert.match(code, /loads: countLoads \|\| undefined,/u,
    "SH1280: counting renders every page of the sites on screen, so it is the chip's to ask for")
})

test("SH1280: a font pages load is not shown as unused", () => {
  assert.match(code, /item\.usageCount === 0 && typeof item\.loadedBy === "number" && item\.loadedBy > 0/u)
  assert.match(code, /t\("staticFile\.loadedBadge", \{ count: item\.loadedBy \}\)/u)
})

test("SH1280: without a page-load count a zero says unreferenced, never unused", () => {
  assert.match(code, /typeof item\.loadedBy === "number"\s*\n\s*\? t\("staticFile\.usageBadge", \{ count: 0 \}\)\s*\n\s*: t\("staticFile\.unreferencedBadge"\)/u,
    "SH1280: 'unused' is the word a deletion acts on, so it needs both counts at zero")
})

test("SH1280: both locales carry every string the badge and the chip use", () => {
  for (const locale of ["en", "pt"]) {
    const strings = JSON.parse(
      readFileSync(join(ROOT, "src", "i18n", "locales", locale, "staticFile.json"), "utf8"))
    for (const key of ["unreferencedBadge", "unreferencedHint", "loadedBadge_one", "loadedBadge_other",
      "loadedHint", "countLoads"]) {
      assert.ok(strings.staticFile?.[key], `SH1280: ${locale} is missing staticFile.${key}`)
    }
  }
})
