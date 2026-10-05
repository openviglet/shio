/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1194 — the admin lists and the site list were tile mosaics: one user filled a 390 x 300 card
 * with a glow and two thirds of the screen stayed empty, and the first of fifteen sites got a double
 * cell for no reason the data gave. Records a reader compares are rows (VDS183), and the look census
 * counted nine routes still drawing them as tiles. These hold that none do.
 */
import { strict as assert } from "node:assert"
import { readdirSync, readFileSync } from "node:fs"
import { dirname, join, relative } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { stripComments } from "./support/source-text.mjs"

const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src")
const read = (...parts) => stripComments(readFileSync(join(SRC, ...parts), "utf8"))

test("SH1194: the shared admin list and the site list render a table, not a mosaic", () => {
  for (const file of [["app", "admin", "admin-list.page.tsx"], ["app", "content", "site.list.page.tsx"]]) {
    const code = read(...file)
    assert.match(code, /<BentoListPage[\s\S]*\bcolumns=\{columns\}/, `SH1194: ${file.at(-1)} passes no columns`)
    // SH1220 — the site list offers tiles beside its rows, and rows stay one switch away.
    if (file.at(-1) !== "site.list.page.tsx") {
      assert.doesNotMatch(code, /\brenderTile=/,
        `SH1194: ${file.at(-1)} passes renderTile, which asks the package for the mosaic again`)
    }
    assert.match(code, /\bonRowOpen=/, `SH1194: ${file.at(-1)}'s rows do not open their record from the keyboard`)
  }
})

test("SH1222: the admin lists carry the description under the name, and a date where the model has one", () => {
  const code = read("app", "admin", "admin-list.page.tsx")
  assert.doesNotMatch(code, /\bid: "description"/,
    "SH1222: a Description column is back, at the width the useful columns need, empty for most rows")
  assert.match(code, /<BentoIdentityCell name=\{tile\.title\} detail=/,
    "SH1222: the record is named by the package's identity cell, its description the second line")
  assert.match(code, /id: "details"/, "SH1222: Details keeps its column")
  assert.match(code, /id: "status"/, "SH1222: Status keeps its column")
  assert.match(code, /id: "dated"[\s\S]*<BentoChangeCell at=\{at\} \/>/)
  for (const page of ["api-token.list.page.tsx", "site-webhook.list.page.tsx", "tenant.list.page.tsx"]) {
    assert.match(read("app", "admin", page), /dated: item\.createdDate/,
      `SH1222: ${page}'s model has a creation date and the list does not show it`)
  }
})

test("SH1238: the users list shows the last sign-in, labelled as what it is", () => {
  // SH1222 left it out because only the startup seed wrote lastLogin; SH1238 records each sign-in.
  const users = read("app", "admin", "user.list.page.tsx")
  assert.match(users, /dated: user\.lastLogin/, "SH1238: the users list does not show when anyone signed in")
  assert.match(users, /dateHeader=\{t\("admin\.listLastSignIn"\)\}/,
    "SH1238: a sign-in date under a Created header would say the account is newer than it is")
})

test("SH1221: no list passes the deprecated headerAction; a list's controls are in its one toolbar", () => {
  const left = []
  const walk = (dir) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const path = join(dir, entry.name)
      if (entry.isDirectory()) walk(path)
      else if (/\.tsx?$/.test(entry.name)
        && /\bheaderAction\b/.test(stripComments(readFileSync(path, "utf8")))) {
        left.push(relative(SRC, path).replace(/\\/g, "/"))
      }
    }
  }
  walk(SRC)
  assert.deepEqual(left, [],
    "SH1221: headerAction draws a list's controls in the hero, apart from the rows they act on. "
    + "Pass them as `toolbar`, which the package draws in the bar over the table; New stays the hero's primary.")
})

test("SH1194: no list page asks for tiles without saying why", () => {
  // The vocabulary harness renders the mosaic on purpose; a real list that wants tiles (media,
  // blueprints) names itself here with its reason, so the exception is a line and not a habit.
  const VISUAL = new Map([
    ["app/system/bento-vocabulary.page.tsx", "the harness proves the mosaic itself"],
    // SH1220 — a site is recognised by its home page faster than by its name, so up to thirty of
    // them open as cards; the rows are the other view, and the default past thirty.
    ["app/content/site.list.page.tsx", "a site is picked by the picture of its home page"],
  ])
  const offenders = []
  const walk = (dir) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const path = join(dir, entry.name)
      if (entry.isDirectory()) walk(path)
      else if (entry.name.endsWith(".tsx")) {
        const name = relative(SRC, path).replace(/\\/g, "/")
        const code = stripComments(readFileSync(path, "utf8"))
        if (/<BentoListPage\b[\s\S]*?\brenderTile=/.test(code) && !VISUAL.has(name)) offenders.push(name)
      }
    }
  }
  walk(join(SRC, "app"))
  assert.deepEqual(offenders, [], "SH1194: a list of records renders as tiles again")
})
