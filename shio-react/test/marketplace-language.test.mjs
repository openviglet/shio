/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1431 — a Portuguese console listed every package in English.
 */
import { strict as assert } from "node:assert"
import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { stripComments } from "./support/source-text.mjs"

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const read = (...path) => stripComments(readFileSync(join(ROOT, ...path), "utf8"))
const PAGE = read("src", "app", "admin", "marketplace", "marketplace.list.page.tsx")
const MODEL = read("src", "models", "marketplace", "marketplace.model.ts")

test("SH1431: the listing is put in the reader's language before anything reads its words", () => {
  assert.match(PAGE, /\.map\(\(item\) => localizedItem\(item, i18n\.language\)\)/,
    "SH1431: the cards, the dialog and the search read an item the console did not localize")
})

test("SH1431: an exact locale wins, then its language, and a missing field falls back alone", () => {
  assert.match(MODEL, /locale\.toLowerCase\(\) === wanted/, "SH1431: pt-BR is matched as pt-BR first")
  assert.match(MODEL, /split\("-"\)\[0\] === base/, "SH1431: then a pt console reads the pt-BR entry")
  assert.match(MODEL, /title: localized\.title \|\| item\.title/,
    "SH1431: a package that translated only its description keeps its title")
})
