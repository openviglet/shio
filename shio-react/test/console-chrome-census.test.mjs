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

/**
 * The three exports that are **era-neutral**, and the one hole this classifier had (SH892).
 *
 * The rule below — a page is bento-era when it imports the bento subpath — was right about every
 * export the layer had when SH886 shipped, and wrong about the one whose entire purpose is to be
 * imported from a console-era page. `AdaptiveSectionCard` renders one form's fields as console
 * cards *or* as frosted bento sections, decided by a `SectionCardChromeProvider` above it, and
 * the console branch is the default. That is what lets the post, post-type and site editors — the
 * three heaviest forms in the product, one of which builds its schema at runtime and renders
 * fifteen dynamic widgets — be written once instead of forked by a parallel route.
 *
 * The package exports it from the bento subpath and nowhere else, because that is where it lives.
 * So a page adopting the adapter starts matching a pattern that means "migrated" while it is
 * still a console page in every visible respect: SH892 made all three read as migrated in one
 * commit, which would have shrunk the debt by three pages nobody had touched.
 *
 * Excluded by **name** rather than by loosening the pattern. A page that imports the adapter
 * *and* `BentoHero` is migrated, and this still says so.
 */
const ERA_NEUTRAL = ["AdaptiveSectionCard", "SectionCardChromeProvider", "useSectionChrome"]

/** Every `import … from "@viglet/viglet-design-system/bento"` clause in a source file. */
const BENTO_CLAUSE = /import\s*\{([^}]*)\}\s*from\s*["']@viglet\/viglet-design-system\/bento["']/g

/**
 * A page belongs to the bento era when it imports from the bento subpath **something other than
 * the era-neutral adapter**. A bare `import "…/bento"` with no clause counts as bento: it can
 * only be there for a side effect the console era does not have.
 */
function isBento(source) {
  if (!/@viglet\/viglet-design-system\/bento/.test(source)) return false
  const clauses = [...source.matchAll(BENTO_CLAUSE)]
  if (!clauses.length) return true
  return clauses.some(([, names]) => names
    .split(",")
    .map((name) => name.trim().replace(/^type\s+/, "").split(/\s+as\s+/)[0].trim())
    .filter(Boolean)
    .some((name) => !ERA_NEUTRAL.includes(name)))
}

/**
 * The console-era chrome this migration is retiring, each probe naming the thing a bento page
 * replaces it with. Adding a probe here is how a newly-noticed piece of console chrome enters the
 * census; the map below then has to absorb it, which is the point.
 */
/*
 * SH902 — the StickySaveBar probe is gone, on this reader's own instruction.
 *
 * Its vacuity check says: "the {id} probe matches no page. Either every page carrying it was
 * migrated — then delete the probe in the same diff — or its import path was renamed and this
 * reader has gone blind to a whole layer of chrome while its count went down." It is the first of
 * the four to reach zero, and it got there by migration: the three pages that rendered a console
 * sticky save bar pass their save props through `AdaptivePageHeader` now, which renders the pair
 * in console chrome and the single `BentoFormHero` morph in bento.
 *
 * Deleting it is not losing coverage. `chrome-adapter.test.mjs` asserts that no page mounts
 * `StickySaveBar` directly, which is the same rule from the other side and a stricter one.
 */
const CONSOLE_CHROME = [
  ["PageHeader", /@\/components\/page-header/],
  ["Card", /@\/components\/ui\/card/],
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
  // SH904 — empty by migration. The user *editor* stays an adapter page rather than taking
  // the entity shell: a user has no title and no description, its identity is a login key and
  // its name is two columns, so a hero that inline-edits `title` renames the login on blur.
  "admin/admin.user.page.tsx": [],
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
  /*
   * SH900 — an empty list, and the third way to reach one. `admin.root.page.tsx`,
   * `not-found.page.tsx` and `visual-editor.page.tsx` render no chrome this reader knows
   * because they never did. The content browser reached the same state by **migrating**:
   * its header and its three panels go through the adapters now, so the page composes no
   * console chrome of its own while still being a console-era page — the same component
   * renders bento under the provider at /bento/content/object/:objectId.
   */
  "content/object.children.page.tsx": [],
  "content/post-type-select.page.tsx": ["PageHeader", "Card"],
  // SH892 — the Card marker is gone because the section chrome is the adapter now, not
  // @/components/ui/card. The page is still console-era: PageHeader and the console sticky
  // save bar are what SH902 replaces.
  // SH902 — all three empty by migration: one AdaptivePageHeader call replaces the header
  // and the save bar, and the panel is an AdaptivePanel.
  "content/post-type.edit.page.tsx": [],
  "content/post-type.list.page.tsx": [],
  "content/post-type.new.page.tsx": [],
  // SH892 — same swap, on the form that builds its schema at runtime. SH901 owns the rest.
  /*
   * SH901 — both empty by migration, the way the content browser is (SH900). The header and
   * the save bar are one AdaptivePageHeader call and the panel is an AdaptiveSectionCard,
   * so the page composes no console chrome of its own while still being console-era.
   */
  "content/post.edit.page.tsx": [],
  "content/post.new.page.tsx": [],
  // SH903 — all three empty by migration: AdaptivePageHeader, AdaptivePanel and the shared
  // AdaptiveEmptyState replace the header, the card and three copies of the "nothing here" void.
  "content/search.page.tsx": [],
  // SH892 — same swap; SH899 owns the hero and the list shape around it.
  "content/site.edit.page.tsx": ["PageHeader"],
  "content/site.import.page.tsx": ["PageHeader", "Card"],
  "content/site.list.page.tsx": ["PageHeader", "Card"],
  "content/site.new.page.tsx": ["PageHeader", "Card"],
  "content/static-file.list.page.tsx": [],
  "content/trash.page.tsx": [],
  "graphql/graphql.page.tsx": ["PageHeader"],
  "home/home.page.tsx": ["PageHeader", "Card"],
  "not-found.page.tsx": [],
  "universal-editor/editor-sites.page.tsx": ["PageHeader", "Card"],
  "universal-editor/visual-editor.page.tsx": [],
}

/**
 * Console pages that themselves import bento. **Expected to stay empty**, and SH898 is why.
 *
 * SH886 wrote this list expecting Blocks AQ and AR to move names into it, one per migrated page.
 * SH888 then chose a different shape: the bento route tree is mounted **beside** the console one,
 * because `console.root.page.tsx` wraps everything in a `SidebarProvider` that no bento component
 * can live under. So a migration writes `src/app/bento/<surface>` and leaves the console page
 * where it is, untouched, until SH909 removes that tree wholesale.
 *
 * This reader therefore measures **the console tree's own chrome**, which is a real question and
 * not the migration's progress. Two other instruments carry that: `surface-digest.mjs` surveys
 * both trees since SH898 and names each surface's era, and `bento-nav.test.mjs` prints how many of
 * the seventeen declared surfaces point at a bento route.
 *
 * A name would appear here only if somebody imported bento **into a console page**, which is the
 * arrangement §LIV.3 says cannot work — so this staying empty is the assertion, not a placeholder.
 */
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
    if (isBento(source)) bento.push(name)
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

test("SH892: the era-neutral exclusion excludes one thing and not the layer", () => {
  const importing = (names) => `import { ${names} } from "@viglet/viglet-design-system/bento"`

  // The whole point: a page may adopt the adapter and stay console-era.
  assert.equal(isBento(importing("AdaptiveSectionCard")), false,
    "SH892: the adapter must not mark a page migrated — it is the one export written to be used"
      + " from a console page, and the three heaviest forms adopted it while still rendering"
      + " PageHeader and the console save bar.")
  assert.equal(isBento(importing("AdaptiveSectionCard, SectionCardChromeProvider")), false,
    "SH892: the provider that chooses the chrome is era-neutral for the same reason")

  // And the exclusion must not have swallowed the classifier. This is the direction that fails
  // silently: an over-broad exclusion reports every migrated page as console-era for ever, and
  // the census then reads as a migration that never started rather than one that never finished.
  assert.equal(isBento(importing("BentoHero")), true,
    "SH892: a real bento import must still mark a page migrated, or ERA_NEUTRAL has grown into a"
      + " blanket exemption and this census is measuring nothing")
  assert.equal(isBento(importing("AdaptiveSectionCard, BentoHero")), true,
    "SH892: a page that imports the adapter AND real bento chrome is migrated — the exclusion is"
      + " per name, not per import statement")
  assert.equal(isBento('import "@viglet/viglet-design-system/bento.css"'), true,
    "SH892: a clause-less bento import can only be there for a side effect the console era does"
      + " not have, so it counts")
  assert.equal(isBento('import { Card } from "@/components/ui/card"'), false,
    "SH892: a page with no bento import at all is console-era, which is the control")
})
