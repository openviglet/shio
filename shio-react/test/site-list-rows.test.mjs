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
 * SH1219 — the sites list, redrawn as rows that say whether a site is alive.
 *
 * It was five columns, one of them a Description that held half the width for one site in
 * twenty-three, and a date repeated down the list with nobody named. Read out of the page's source,
 * because the columns are declared data and a render test would need the package's virtual table.
 */
const PAGE = stripComments(readFileSync(join(dirname(fileURLToPath(import.meta.url)), "..", "src",
  "app", "content", "site.list.page.tsx"), "utf8"))

/** The ids an array literal of `{ id: "…", … }` declares, in order, between two markers. */
function idsBetween(start, end) {
  const from = PAGE.indexOf(start)
  assert.ok(from >= 0, `SH1219: ${start} is gone from the sites list`)
  const block = PAGE.slice(from, PAGE.indexOf(end, from))
  return [...block.matchAll(/\bid: "([\w-]+)"/g)].map(([, id]) => id)
}

test("SH1219: three columns, the facts the listing carries, and no Description", () => {
  assert.deepEqual(idsBetween("const columns", "], [t, twoLines])"), ["name", "pages", "changed"],
    "SH1219: the sites list should be identity, pages and last changed — the description is the "
    + "identity cell's second line now")
  assert.match(PAGE, /<BentoIdentityCell\b[^>]*detail=\{site\.description\?\.trim\(\) \|\| undefined\}/,
    "SH1309: a site with no description shows nothing under its name")
  assert.doesNotMatch(PAGE, /`site:\$\{site\.name\}`/,
    "SH1309: the agent's address is back under every name, doubling each row's text for no reader")
  assert.match(PAGE, /lines: twoLines \? 2 : 1/,
    "SH1309: the name column asks for a two-line row even when no site has a second line")
  assert.doesNotMatch(PAGE, /className="bg-primary"/,
    "SH1309: the pages bar fills with the accent again, which reads as a warning on every row")
  // Measured in a browser: `bg-muted-foreground/40` computed to transparent, because the design
  // system's sheet does not ship it (SH1328). These two it ships.
  assert.match(PAGE, /className="bg-muted-foreground\/60" style=\{\{ width: `\$\{\(published/,
    "SH1309: the published share uses a class nothing generates, so it paints nothing")
  assert.match(PAGE, /className="bg-border" style=\{\{ width: `\$\{\(draft/,
    "SH1309: the draft share uses a class nothing generates, so it paints nothing")
  assert.match(PAGE, /subtitle=\{sites \? t\("content\.siteCount"/,
    "SH1309: the subtitle should count the sites, not tell the curator to pick one")
  assert.match(PAGE, /<BentoChangeCell\b[^>]*actor=\{actor\}[^>]*agent=\{agent\}/,
    "SH1219: the change cell says who, and marks an agent")
  assert.match(PAGE, /t\("content\.noPagesYet"\)/, "SH1219: an empty site says so instead of a zero bar")
})

test("SH1219: the row's verbs are a focusable menu, and the default order is newest change first", () => {
  assert.deepEqual(idsBetween("const rowActions", "]\n"), ["preview", "published", "open", "export", "delete"],
    "SH1219: Preview, Open, Export and Delete are the row's menu, never hover-only buttons; SH1304: "
    + "View published site sits beside Preview, so the draft and the visitor's page are both one click")
  assert.match(PAGE, /rowActions=\{rowActions\}/)
  assert.match(PAGE, /\.sort\(\(a, b\) => changedAt\(b\) - changedAt\(a\)\)/,
    "SH1219: the list opens on the site that changed last")
})
