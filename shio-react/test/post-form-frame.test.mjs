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
 * SH1308 — a post's fields are labels and controls, not cards with a stripe inside a card.
 *
 * Measured at 1920px: the Fields panel was a frosted card, each field a second card with a 4px left
 * border in its widget's colour, and the input a third border inside that. Five fields took 680px
 * and the eye read the stripes before the labels. The widget colour belongs to the post-type editor,
 * where a row is a definition; in the post editor the control says what kind of field it is.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const read = (path) => stripComments(readFileSync(join(ROOT, path), "utf8"))

test("SH1308: the post form frames no field and paints no widget colour", () => {
  const form = read("src/components/widgets/post-form-fields.tsx")
  assert.doesNotMatch(form, /widget-colors/, "SH1308: the post form reads the widget colours again")
  assert.doesNotMatch(form, /border-l-4|rounded-lg border/,
    "SH1308: a field is a framed card again, inside the panel's own card")
  assert.match(form, /data-shio-field=\{field\.name\}/,
    "SH1308: the field wrapper lost the attribute the console check counts fields by")
  assert.match(read("src/app/content/post-type.edit.page.tsx"), /WIDGET_ACCENT_COLORS/,
    "SH1308: the colours stay where a row is a definition, in the post-type editor")
})

test("SH1308: the console check counts fields by their attribute, not by the old frame", () => {
  const check = readFileSync(join(ROOT, "e2e", "console-check.mjs"), "utf8")
  const counter = check.slice(check.indexOf("async function countFields"), check.indexOf("async function countFields") + 600)
  assert.match(counter, /\[data-shio-field\]/)
  assert.doesNotMatch(counter, /border-l-4/, "SH1308: countFields counts a frame that no longer exists")
})
