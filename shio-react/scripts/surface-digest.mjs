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
 * Keys are now relative to `src/app`, so `console/home/home.page.tsx` and
 * `bento/home/home.page.tsx` are different surfaces and a saved survey says which era each is. A
 * baseline taken before this reports every surface `gone` and every surface `new`: re-take it once.
 */
export const BENTO_ROOT = join(HERE, "..", "src", "app", "bento")
export const APP_ROOT = join(HERE, "..", "src", "app")

/**
 * A page is bento-era when it imports the subpath — **except for the era-neutral adapter**.
 *
 * SH892 taught `console-chrome-census.test.mjs` this exact exclusion and did not teach it here,
 * and SH899 is where that cost something: the console's own `site.edit.page.tsx` was classified
 * `bento`, because SH892 gave it `AdaptiveSectionCard`. That component is the one export written
 * to be used *from a console page* — it renders console cards by default and bento sections under
 * a provider — and the package exports it from the bento subpath because that is where it lives.
 *
 * Two readers answering the same question, one taught and one not. Kept in step by name here
 * rather than by importing the census's copy: this file is a script the console can run on its
 * own, and a test that imports a script is a different dependency from a script that imports a
 * test. `bento-site-slice.test.mjs` asserts both agree.
 */
const ERA_NEUTRAL = ["AdaptiveSectionCard", "SectionCardChromeProvider", "useSectionChrome"]

const BENTO_CLAUSE = /import\s*\{([^}]*)\}\s*from\s*["']@viglet\/viglet-design-system\/bento["']/g

function isBentoEra(source) {
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
 * The chrome vocabulary, both eras. Only these are recorded, and that is what keeps the digest
 * stable under edits that are not about chrome: renaming a handler or adding a table column moves
 * nothing here, while swapping a Card for a BentoSection moves it immediately.
 */
const CHROME = new Set([
  // Console era.
  "PageHeader", "Card", "CardContent", "CardHeader", "CardTitle", "CardDescription", "CardFooter",
  "SidebarProvider", "SidebarInset", "AppSidebar", "AppFooter", "StickySaveBar",
  // Bento era.
  "BentoHero", "BentoFormHero", "BentoSection", "BentoFormSection", "BentoTile", "BentoTileGrid",
  "BentoEntityShell", "BentoEntityTile", "BentoListPage", "BentoCountTile", "BentoEmptyState",
  "BentoSaveBar", "BentoScrollSaveBar", "BentoNavRail", "BentoUserMenu", "BentoStatusMarker",
  "BentoActionsMenu", "BentoInlineEdit", "BentoBackToTop", "BentoCommandPalette",
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
    era: isBentoEra(source) ? "bento" : "console",
    // Order is kept: a page that renders a hero after its sections is a different page.
    chrome: composed,
    surface: surfaces(source),
  }
}

/** A short, stable hash over the canonical form. Same structure in, same twelve characters out. */
export function digestOf(source) {
  const structure = structureOf(source)
  const canonical = JSON.stringify([structure.era, structure.chrome, structure.surface])
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
      moved.push(`${name}: new (${now.era}, ${now.digest})`)
      continue
    }
    if (was.digest === now.digest) continue
    const fields = []
    if (was.era !== now.era) fields.push(`era ${was.era} -> ${now.era}`)
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
      console.log(`${s.digest}  ${s.era.padEnd(7)}  ${String(s.chrome.length).padStart(2)} chrome  ${name}`)
    }
    console.log(`\n${Object.keys(now).length} surfaces.`)
  }
}
