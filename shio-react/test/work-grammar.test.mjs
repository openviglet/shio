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
 * SH1320 — the design system's work grammar (VDS235), chosen per route through Shio's own seam
 * rather than by each page composing the package's hero.
 */
const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src")
const read = (...parts) => stripComments(readFileSync(join(SRC, ...parts), "utf8"))

test("SH1320: PageHero takes a grammar and hands it to both of the package's heroes", () => {
  const hero = read("components", "page-hero.tsx")
  assert.equal((hero.match(/grammar=\{grammar\}/g) ?? []).length, 2,
    "SH1320: one of the two heroes ignores the route's grammar")
})

test("SH1320: the content browser is a work surface, its hero and its table", () => {
  assert.match(read("app", "content", "object.children.page.tsx"), /grammar="work"/,
    "SH1320: the content browser's hero is the showcase again")
  assert.match(read("app", "content", "content-browser", "browser-table.tsx"), /grammar="work"/,
    "SH1320: the content browser's table is a frosted card again")
})

test("SH1320: a site's id redirects to its root with the query it was opened with", () => {
  const root = read("app", "content", "content-browser", "use-sole-root.ts")
  assert.match(root, /object\/\$\{soleRoot\}\$\{search\}/,
    "SH1320: `?as=folders` or `?view=` on a site's id is dropped by the redirect to its root folder")
})
