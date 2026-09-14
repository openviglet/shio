/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SH896 — cheap textual proof that a console surface renders what it rendered before.
 *
 * P9 asks for textual proof instead of asking somebody to look, and this project already does that
 * for the site renderer with SH101's render digest. The console had nothing equivalent, so the only
 * evidence a migrated page was still correct was a person opening it — forty-three of those
 * judgements, each by eye, each unrepeatable, across the largest single change the console has had.
 *
 * ## Why this reads source and not a rendered page
 *
 * "The instance does not run a browser" is a standing non-goal, and it binds here. A console page
 * resolves a router, three contexts and a service call before it paints, so rendering one to prove
 * it composes correctly would mean standing up most of the app — expensive, and flaky in exactly
 * the way a proof must not be. The digest is therefore computed from the page's own JSX: which
 * chrome it composes, in what order, and which surface classes carry the frosted panel and its
 * lift. That is the part a migration changes and the part a reviewer squints at.
 *
 * It is deliberately **not** a pixel claim. A digest that matched says the same chrome was composed
 * in the same order with the same surface vocabulary; it does not say the page looks right. What it
 * buys is the other direction, which is the one that matters across 43 surfaces: when a digest
 * moves, something structural moved, and the diff names it.
 *
 * ## Usage
 *
 *   node scripts/surface-digest.mjs                  # one line per console surface
 *   node scripts/surface-digest.mjs --json           # the full structure, for a diff
 *   node scripts/surface-digest.mjs --json > before.json
 *   node scripts/surface-digest.mjs --against before.json   # what moved, and nothing else
 *
 * `--against` is the whole point: it exits non-zero when a digest moved, so a migrated slice is
 * proven by a command rather than by a person, and the report names the page and the field.
 */
import { createHash } from "node:crypto"
import { readFileSync, readdirSync } from "node:fs"
import { dirname, join, relative } from "node:path"
import { fileURLToPath, pathToFileURL } from "node:url"

/**
 * Codepoint order, stated rather than defaulted — and deliberately **not** `localeCompare`.
 * The digest is a hash over this order, so a comparator that consults the host locale would hash
 * the same page to two different values on two machines, which is the one thing a digest may not
 * do. The lint that asks for `localeCompare` is right about display lists and wrong about this.
 */
const byCodepoint = (a, b) => (a < b ? -1 : a > b ? 1 : 0)

const HERE = dirname(fileURLToPath(import.meta.url))
export const CONSOLE_ROOT = join(HERE, "..", "src", "app", "console")

/**
 * SH898 — the bento tree is surveyed too, and both are keyed under `src/app`.
 *
 * The first migrated surface is what found this. SH888 built the bento route tree **beside** the
 * console one, so a migration adds `src/app/bento/<surface>` and leaves the console page in place
 * until SH909 removes that tree wholesale. This survey walked `src/app/console` alone, so the
 * whole of SH898 produced `No surface moved (43 compared)` — a correct answer about a directory
 * nothing had changed, and a useless one about the migration.
 *
 * Keys are relative to `src/app`, so `console/content/post.edit.page.tsx` and
 * `bento/content/site.edit.page.tsx` are different surfaces however the tree is later reshaped.
 */
export const BENTO_ROOT = join(HERE, "..", "src", "app", "bento")
export const APP_ROOT = join(HERE, "..", "src", "app")

/*
 * SH960 — the `era` field is gone, and it was worse than redundant.
 *
 * It classified a page `bento` when it imported the bento subpath and `console` otherwise, which
 * was the right question while two shells were mounted. After SH909 it had no subject, and it was
 * also wrong: measured on the day it was removed, **33 of 45 surfaces reported `console`** —
 * including every bento admin list, because they compose this console's shared `AdminListPage`
 * and import nothing from the package themselves. A field that labels a bento page `console` is
 * the kind of log line SH938 filed a defect off. Removing it moved every digest once; re-take any
 * saved baseline with `--json` before comparing against it.
 */

/**
 * The chrome vocabulary, and what keeps the digest stable under edits that are not about chrome:
 * renaming a handler or adding a table column moves nothing here, while swapping a panel for a
 * section moves it immediately.
 *
 * SH960 re-derived it by measuring what the pages compose. The old list named the package's
 * components and the retired console ones, and **33 of 45 surfaces digested nothing**: most pages
 * compose this console's seams over the package, not the package itself, so the proof was blind to
 * the pages it most needed to see.
 */
const CHROME = new Set([
  // The package's components, where a page composes them directly.
  "BentoHero", "BentoFormHero", "BentoSection", "BentoFormSection", "BentoTile", "BentoTileGrid",
  "BentoEntityShell", "BentoEntityTile", "BentoListPage", "BentoCountTile", "BentoEmptyState",
  "BentoSaveBar", "BentoScrollSaveBar", "BentoNavRail", "BentoUserMenu", "BentoStatusMarker",
  "BentoActionsMenu", "BentoInlineEdit", "BentoBackToTop", "BentoCommandPalette", "BentoPanel",
  "AdaptiveSectionCard",
  // This console's seams over them, which is what most pages actually compose (counted: PageHero
  // on 22 surfaces, Panel on 12, EmptyState on 9, AdminListPage on 8).
  "PageHero", "Panel", "EmptyState", "AdminListPage", "IdentityEntityPage",
  /*
   * And one retired family whose module still resolves. `@/components/ui/card` is live — the login
   * form imports it — so a page composing a Card again is possible, and it is the non-bento surface
   * `surface-digest.test.mjs` refuses on every page. PageHeader, the sidebar pieces and the sticky
   * save bar left this list with the modules SH910 deleted: a name nothing can import is a name
   * nothing can compose.
   */
  "Card", "CardContent", "CardHeader", "CardTitle", "CardDescription", "CardFooter",
])

/**
 * The classes that carry the frosted surface and the lift — §LIV.11 names these specifically,
 * because they are what a reviewer is actually looking at when they judge a panel by eye. A prefix
 * match, since every one of them is a Tailwind family with an open-ended tail.
 */
const SURFACE = [
  "backdrop-blur", "bg-card", "bg-background", "bg-muted", "bg-popover",
  "shadow", "rounded", "border", "ring", "supports-[backdrop-filter]",
]

/**
 * Strip comments, so a commented-out `<Card>` cannot contribute to a digest of what renders.
 *
 * String literals are deliberately left in. Stripping them would need a real JS lexer to avoid
 * eating a `className` value — which is the one string this file is here to read — and the shapes
 * that would slip through (a component name inside a quoted string, followed by whitespace or `>`)
 * do not occur in these 43 pages. Stated rather than assumed, so the next person knows it is a
 * bounded shortcut and not an oversight.
 */
function code(source) {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, " ")
    .replace(/(^|[^:])\/\/[^\n]*/g, "$1 ")
}

/** Every JSX opening tag, in source order. `<Card ` and `<Card>` but not `</Card>` or `<div`. */
function elements(source) {
  return [...code(source).matchAll(/<([A-Z][A-Za-z0-9]*)[\s/>]/g)].map((m) => m[1])
}

/** The surface vocabulary present, sorted and deduplicated — a set, because order means nothing. */
function surfaces(source) {
  const found = new Set()
  for (const match of code(source).matchAll(/className=(?:"([^"]*)"|\{`([^`]*)`\})/g)) {
    for (const token of (match[1] ?? match[2] ?? "").split(/\s+/)) {
      const bare = token.replace(/^[a-z-]+:/, "")
      const family = SURFACE.find((s) => bare === s || bare.startsWith(`${s}-`) || bare.startsWith(`${s}/`))
      if (family) found.add(family)
    }
  }
  return [...found].sort(byCodepoint)
}

/** The structure of one surface, as the thing a digest is taken over. */
export function structureOf(source) {
  const composed = elements(source).filter((name) => CHROME.has(name))
  return {
    // Order is kept: a page that renders a hero after its sections is a different page.
    chrome: composed,
    surface: surfaces(source),
  }
}

/** A short, stable hash over the canonical form. Same structure in, same twelve characters out. */
export function digestOf(source) {
  const structure = structureOf(source)
  const canonical = JSON.stringify([structure.chrome, structure.surface])
  return createHash("sha256").update(canonical).digest("hex").slice(0, 12)
}

export function pages(dir = CONSOLE_ROOT, out = []) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name)
    if (entry.isDirectory()) pages(path, out)
    else if (entry.name.endsWith(".page.tsx")) out.push(path)
  }
  return out
}

/** Every surface in both trees, keyed by its path relative to `src/app`, in a stable order. */
export function survey() {
  const out = {}
  const files = [...pages(CONSOLE_ROOT), ...pages(BENTO_ROOT)]
  for (const file of files.sort(byCodepoint)) {
    const source = readFileSync(file, "utf8")
    const name = relative(APP_ROOT, file).replace(/\\/g, "/")
    out[name] = { digest: digestOf(source), ...structureOf(source) }
  }
  return out
}

/** What moved between two surveys, as a list of sentences a person can act on. */
export function compare(before, after) {
  const moved = []
  for (const name of Object.keys(before).sort(byCodepoint)) {
    if (!(name in after)) moved.push(`${name}: gone`)
  }
  for (const name of Object.keys(after).sort(byCodepoint)) {
    const was = before[name]
    const now = after[name]
    if (!was) {
      moved.push(`${name}: new (${now.digest})`)
      continue
    }
    if (was.digest === now.digest) continue
    const fields = []
    if (was.chrome.join() !== now.chrome.join()) {
      fields.push(`chrome [${was.chrome.join(" ")}] -> [${now.chrome.join(" ")}]`)
    }
    if (was.surface.join() !== now.surface.join()) {
      fields.push(`surface [${was.surface.join(" ")}] -> [${now.surface.join(" ")}]`)
    }
    moved.push(`${name}: ${was.digest} -> ${now.digest}\n    ${fields.join("\n    ")}`)
  }
  return moved
}

// `file://${argv[1]}` does not round-trip on Windows — a drive letter needs the third slash, and
// getting that wrong makes the CLI silently print nothing. pathToFileURL is the spelling that works
// on both platforms.
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const args = process.argv.slice(2)
  const against = args.indexOf("--against")
  const now = survey()

  if (against !== -1) {
    const before = JSON.parse(readFileSync(args[against + 1], "utf8"))
    const moved = compare(before, now)
    if (moved.length === 0) {
      console.log(`No surface moved (${Object.keys(now).length} compared).`)
      process.exit(0)
    }
    console.log(`${moved.length} surface(s) moved:\n`)
    for (const line of moved) console.log(`  ${line}`)
    process.exit(1)
  }

  if (args.includes("--json")) {
    console.log(JSON.stringify(now, null, 2))
  } else {
    for (const [name, s] of Object.entries(now)) {
      console.log(`${s.digest}  ${String(s.chrome.length).padStart(2)} chrome  ${name}`)
    }
    console.log(`\n${Object.keys(now).length} surfaces.`)
  }
}
