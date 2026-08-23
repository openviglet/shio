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
import { dirname, join, relative } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"

/**
 * SH886 — nothing counted how much console chrome the console still renders, so Blocks AQ and AR
 * had no size and no end.
 *
 * ## Why the count had to become code
 *
 * §LIV.1 opened with a measurement: "40 of the 43 console pages import PageHeader, 42 import Card,
 * three import the console sticky save bar." Two of those three numbers are wrong. The real figures
 * the first run of this reader produced are **39** and **38** — the hand-run grep counted files it
 * had already stopped matching, and nothing re-ran it, so a number written once drifted quietly for
 * as long as it took somebody to check. That is the whole argument for this file: a migration
 * scoped by a number nobody recomputes is a migration with no end, and the wrong number is worse
 * than none because it reads as measured.
 *
 * ## Why a list and not a ceiling
 *
 * A ceiling ("no more than 43 pages on console chrome") lets one migrated slice pay for the next:
 * migrate two pages, and the budget quietly funds leaving two others un-migrated for ever. The map
 * below names **every page and the chrome it renders**, so a migration fails this reader *by name*
 * and the diff that fixes it is a diff somebody reviews. That is the contract
 * `cli/test/assertion-debt.test.mjs` uses for the same reason.
 *
 * ## What decides the era
 *
 * The design system's own `src/bento/index.ts` states it: bento is a subpath rather than part of
 * the root barrel, so "an import that says `@viglet/viglet-design-system/bento` states which era a
 * page belongs to, which is something a reviewer and a lint can both see." This is that lint. It
 * reads imports rather than rendering anything, so it stays cheap enough to run in the same gate as
 * everything else — no browser, no bundler, 43 files.
 *
 * ## The three pages that render no chrome at all
 *
 * `admin.root.page.tsx` is a bare `Outlet`, `not-found.page.tsx` is a `Button`, and
 * `visual-editor.page.tsx` builds its own frame. They are console-era by absence, not by import,
 * and an empty marker list is how that is spelled — a page that renders nothing this reader knows
 * still has to be accounted for, or the census silently shrinks by three.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const CONSOLE_ROOT = join(HERE, "..", "src", "app", "console")

/** A page belongs to the bento era when, and only when, it imports from the bento subpath. */
const BENTO_IMPORT = /@viglet\/viglet-design-system\/bento/

/**
 * The console-era chrome this migration is retiring, each probe naming the thing a bento page
 * replaces it with. Adding a probe here is how a newly-noticed piece of console chrome enters the
 * census; the map below then has to absorb it, which is the point.
 */
const CONSOLE_CHROME = [
  ["PageHeader", /@\/components\/page-header/],
  ["Card", /@\/components\/ui\/card/],
  ["StickySaveBar", /\bStickySaveBar\b/],
  ["Sidebar", /@\/components\/ui\/sidebar|@\/components\/app-sidebar/],
]

/**
 * Every console page still on console-era chrome, and what it renders. **This is the debt.**
 *
 * A page migrated to bento is deleted from here, not decremented from a total. A new console page
 * added on the old chrome has to be written in here, in a diff somebody reads, which is the only
 * moment anybody will ask whether it should have been bento to begin with.
 */
const STILL_CONSOLE = {
  "admin/activity/activity.list.page.tsx": ["PageHeader", "Card"],
  "admin/admin.group.page.tsx": ["PageHeader", "Card"],
  "admin/admin.groups.list.page.tsx": ["PageHeader", "Card"],
  "admin/admin.role.page.tsx": ["PageHeader", "Card"],
  "admin/admin.roles.list.page.tsx": ["PageHeader", "Card"],
  "admin/admin.root.page.tsx": [],
  "admin/admin.user.page.tsx": ["PageHeader", "Card"],
  "admin/admin.users.list.page.tsx": ["PageHeader", "Card"],
  "admin/api-token/api-token.list.page.tsx": ["PageHeader", "Card"],
  "admin/api-token/api-token.page.tsx": ["PageHeader", "Card"],
  "admin/auth-provider/auth-provider.list.page.tsx": ["PageHeader", "Card"],
  "admin/auth-provider/auth-provider.page.tsx": ["PageHeader", "Card"],
  "admin/email-config/email-config.page.tsx": ["PageHeader", "Card"],
  "admin/exchange-provider/exchange-provider.list.page.tsx": ["PageHeader", "Card"],
  "admin/exchange-provider/exchange-provider.page.tsx": ["PageHeader", "Card"],
  "admin/marketplace/marketplace.list.page.tsx": ["PageHeader", "Card"],
  "admin/review/review.list.page.tsx": ["PageHeader", "Card"],
  "admin/site-webhook/site-webhook.list.page.tsx": ["PageHeader", "Card"],
  "admin/site-webhook/site-webhook.page.tsx": ["PageHeader", "Card"],
  "admin/tenant/tenant.list.page.tsx": ["PageHeader", "Card"],
  "admin/tenant/tenant.page.tsx": ["PageHeader", "Card"],
  "console.root.page.tsx": ["Sidebar"],
  "content/folder.edit.page.tsx": ["PageHeader", "Card"],
  "content/folder.new.page.tsx": ["PageHeader", "Card"],
  "content/object.children.page.tsx": ["PageHeader", "Card"],
  "content/post-type-select.page.tsx": ["PageHeader", "Card"],
  "content/post-type.edit.page.tsx": ["PageHeader", "Card", "StickySaveBar"],
  "content/post-type.list.page.tsx": ["PageHeader", "Card"],
  "content/post-type.new.page.tsx": ["PageHeader", "Card"],
  "content/post.edit.page.tsx": ["PageHeader", "Card", "StickySaveBar"],
  "content/post.new.page.tsx": ["PageHeader", "Card", "StickySaveBar"],
  "content/search.page.tsx": ["PageHeader", "Card"],
  "content/site.edit.page.tsx": ["PageHeader", "Card"],
  "content/site.import.page.tsx": ["PageHeader", "Card"],
  "content/site.list.page.tsx": ["PageHeader", "Card"],
  "content/site.new.page.tsx": ["PageHeader", "Card"],
  "content/static-file.list.page.tsx": ["PageHeader", "Card"],
  "content/trash.page.tsx": ["PageHeader", "Card"],
  "graphql/graphql.page.tsx": ["PageHeader"],
  "home/home.page.tsx": ["PageHeader", "Card"],
  "not-found.page.tsx": [],
  "universal-editor/editor-sites.page.tsx": ["PageHeader", "Card"],
  "universal-editor/visual-editor.page.tsx": [],
}

/** Pages already on the bento shell. Empty when this shipped; Blocks AQ and AR move names here. */
const MIGRATED = []

function pages(dir, out = []) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name)
    if (entry.isDirectory()) pages(path, out)
    else if (entry.name.endsWith(".page.tsx")) out.push(path)
  }
  return out
}

function census() {
  const bento = []
  const still = {}
  for (const file of pages(CONSOLE_ROOT).sort()) {
    const source = readFileSync(file, "utf8")
    const name = relative(CONSOLE_ROOT, file).replace(/\\/g, "/")
    if (BENTO_IMPORT.test(source)) bento.push(name)
    else still[name] = CONSOLE_CHROME.filter(([, probe]) => probe.test(source)).map(([id]) => id)
  }
  return { bento: bento.sort(), still }
}

test("SH886: every console page is accounted for, by name and by the chrome it renders", () => {
  const { bento, still } = census()
  const total = bento.length + Object.keys(still).length

  // Printed on every run, because a count somebody has to open a file to learn is a count that
  // drifts — which is the defect this closed. Both numbers, never one: "43 pages" says nothing
  // about how far the migration got, and "0 migrated" says nothing about how far it has to go.
  const chrome = {}
  for (const marks of Object.values(still)) {
    for (const mark of marks) chrome[mark] = (chrome[mark] ?? 0) + 1
  }
  console.log(
    `SH886: ${total} console pages — ${bento.length} on bento, ${Object.keys(still).length} on `
      + `console chrome (${Object.entries(chrome).sort().map(([k, v]) => `${k} ${v}`).join(", ")}`
      + `, no chrome ${Object.values(still).filter((m) => m.length === 0).length})`,
  )

  assert.deepEqual(
    still,
    STILL_CONSOLE,
    "SH886: the console-chrome census moved.\n"
      + "If a page is in `+ actual` with chrome it did not have, console chrome was added to it — "
      + "Blocks AQ and AR are retiring that layer, so a new page should be starting on bento.\n"
      + "If a page is in `- expected`, you migrated it: delete its line here and add its name to "
      + "MIGRATED. A ceiling would have let that deletion pay for leaving the next page alone; a "
      + "named list makes each migration its own diff.\n"
      + "If a page appears or vanishes entirely, the console grew or lost a route and the census "
      + "has to say so.",
  )

  assert.deepEqual(bento, [...MIGRATED].sort(),
    "SH886: a page imports @viglet/viglet-design-system/bento and is not declared migrated. That "
      + "subpath is what states a page's era — add it to MIGRATED and remove it from STILL_CONSOLE.")
})

test("SH886: the reader is not vacuous — it reads real pages and a classifier that classifies", () => {
  const { still } = census()
  const all = { ...still }

  // A walk that found nothing would report an empty census and pass against an empty declaration,
  // which is the one failure mode a held list cannot catch by itself.
  assert.ok(Object.keys(all).length + MIGRATED.length > 40,
    `SH886: the walk yielded ${Object.keys(all).length} pages — the tree moved and this reader is `
      + "now measuring an empty directory, which would read as a finished migration")

  // And every probe must still match something. A renamed import path turns a probe into a
  // permanent zero, which silently retires that chrome from the census rather than from the
  // console — the count would fall and nobody would have migrated anything.
  for (const [id] of CONSOLE_CHROME) {
    const hits = Object.values(all).filter((marks) => marks.includes(id)).length
    assert.ok(hits > 0,
      `SH886: the ${id} probe matches no page. Either every page carrying it was migrated — then `
        + "delete the probe in the same diff — or its import path was renamed and this reader has "
        + "gone blind to a whole layer of chrome while its count went down.")
  }
})
