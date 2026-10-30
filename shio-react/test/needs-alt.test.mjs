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
 * SH1031 — the console's half of alt text as a publishing fact.
 *
 * `verify` reports `asset-no-alt` and the media library is where it gets fixed. The claim worth
 * guarding is not that a chip renders: it is that **the chip and the finding ask the same
 * question**. A chip that narrowed to "alt is blank" would offer fonts and PDFs, a curator would
 * work through a list that is partly noise, and the two surfaces would disagree about what needs
 * doing — quietly, because both look right.
 *
 * And that the fix is **one line in place**. The detail dialog has had an alt field since SH62 and
 * it is three clicks per image; that is fine for one and is why nobody fixes fifty.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const PAGE = readFileSync(
  join(ROOT, "src", "app", "content", "static-file.list.page.tsx"), "utf8")
const code = PAGE.split("\n").filter((line) => !/^\s*(\/\/|\*|\/\*)/u.test(line)).join("\n")

test("SH1031: the chip asks what the finding asks — an image with no alt", () => {
  assert.match(code, /function undescribed\(item: ShStaticFileItem\): boolean \{\s*\n\s*return isImageType\(item\.contentType\) && !\(item\.alt \?\? ""\)\.trim\(\)/u,
    "SH1031: `isImageType` and not merely a blank alt. A font has no alt to miss, and a chip that "
      + "offered one would send a curator through a list that is partly noise — while `verify` "
      + "reported a different set.")
})

test("SH1031: the chip narrows the page and says so by counting the page", () => {
  assert.match(code, /const needsAltCount = items\.filter\(undescribed\)\.length/u)
  assert.match(code, /const filtered = needsAlt \? items\.filter\(undescribed\) : items/u,
    "SH1031: a narrowing of what is on screen. A library-wide count would have to come from the "
      + "server, and the honest version of that is a facet beside the category ones rather than a "
      + "number computed here and presented as if it were one.")
})

test("SH1031: alt is editable on the card, and only on an image", () => {
  assert.match(code, /const needsAlt = image && !\(item\.alt \?\? ""\)\.trim\(\)/u)
  assert.match(code, /\{image && \(\s*\n(\s*\/\/[^\n]*\n)?\s*<div className=\{cn\([^)]*\)\}\s*onClick=\{\(event\) => event\.stopPropagation\(\)\}>/u,
    "SH1031: outside the button and stopping the click, or typing in the field opens the dialog "
      + "over the thing being typed into")
  // SH1312 — at rest the field is collapsed, not removed: `max-h-0`, so Tab still reaches it and
  // focus opens it, and open for good while the needs-alt chip is narrowing the grid to this work.
  assert.match(code, /altAlways \? "pb-2" : "max-h-0 overflow-hidden group-hover:max-h-12[^"]*group-focus-within:max-h-12/u,
    "SH1312: the alt field shows on every tile at rest again, or is hidden in a way focus cannot open")
  assert.match(code, /onBlur=\{saveAlt\}/u)
  assert.match(code, /if \(event\.key === "Enter"\) event\.currentTarget\.blur\(\)/u,
    "SH1031: Enter commits, which is what makes fifty images fifty lines rather than 150 clicks")
})

test("SH1031: a refused save puts the field back to what the instance still says", () => {
  assert.match(code, /catch \{\s*\n(\s*\/\/[^\n]*\n)*\s*setAlt\(item\.alt \?\? ""\)/u,
    "SH1031: the card must never show a value the instance did not take — a field that kept the "
      + "rejected text reads as saved, and the curator moves on")
})

test("SH1031: both locales carry every string the card and the chip use", () => {
  for (const locale of ["en", "pt"]) {
    const strings = JSON.parse(
      readFileSync(join(ROOT, "src", "i18n", "locales", locale, "staticFile.json"), "utf8"))
    for (const key of ["needsAlt", "altText", "altPlaceholder"]) {
      assert.ok(strings.staticFile?.[key], `SH1031: ${locale} is missing staticFile.${key}`)
    }
  }
})
