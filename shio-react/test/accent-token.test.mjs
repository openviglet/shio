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
import { stripComments as strip } from "./support/source-text.mjs"

/**
 * SH890 — Shio's accent is a token, and every place that still spells it as a hue is named.
 *
 * ## What the roadmap line said, and what the grep actually found
 *
 * The symptom was one component: "the console's orange is spelled as Tailwind gradient classes
 * inside a header component it no longer owns." That is true and it was two chips in
 * `page-header.tsx`, a 150-character class string written out twice. The grep behind it found
 * **97 orange utilities across 24 files** — because orange is not a decoration here, it is
 * Shio's brand, and every page that wanted to look like Shio copied a class string somebody
 * else had typed. `amber` is a different thing and is deliberately not counted: that is the
 * semantic warning colour, and it means "careful", not "Shio".
 *
 * SH322's rule is why this file exists at all and why it exists *before* the remaining fixes:
 * when a task is "the header, and whatever a grep turns up", the grep is the deliverable. A
 * fix list written from one reading of one component would have closed the task with 85 call
 * sites nobody had looked at, and the next person to want the accent would have copied one of
 * them.
 *
 * ## Why a named map rather than a count
 *
 * A ceiling ("no more than 61 orange utilities") lets one migrated page fund leaving the next
 * alone. The map below names **every file and every distinct utility in it**, so a page that
 * adds an orange fails by name in a diff somebody reviews, and a page that stops respelling the hue
 * deletes its line in the same commit. Same contract as `assertion-debt`, for the same reason.
 *
 * ## What this task actually converted
 *
 * The two header chips, which were the symptom, and the six admin save-bar tints — six files
 * carrying the byte-identical string `bg-orange-50 dark:bg-orange-950/30`, which is the exact
 * shape the token exists to stop. The rest stays declared: those pages are Block AR's to
 * migrate, and re-keying a page that is about to be rewritten is work that gets thrown away.
 * What matters is that the token is now the only *findable* way to spell the accent, which is
 * §LIV.5's own argument — the alternative is 43 pages of per-call-site overrides written by
 * somebody who could not find the token.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const CONSOLE_ROOT = join(HERE, "..")
const SRC = join(CONSOLE_ROOT, "src")

/**
 * A Tailwind orange utility, with any variant prefix (`dark:`, `hover:`, `md:`, or a chain of
 * them). Deliberately anchored on the colour family rather than on "any colour": this reader is
 * about **the brand hue**, and a lint that flagged every colour in the console would be noise
 * nobody reads.
 */
const ORANGE = new RegExp(
  String.raw`\b(?:[a-z-]+:)*`
  + String.raw`(?:from|via|to|bg|text|border|ring|fill|stroke|shadow|decoration|outline|accent|caret|divide|placeholder)`
  + String.raw`-orange-[0-9]{2,3}(?:\/[0-9]+)?\b`,
  "g",
)


function sources(dir = SRC, found = []) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name)
    if (entry.isDirectory()) sources(path, found)
    else if (/\.(tsx|ts|css)$/.test(entry.name)) found.push(path)
  }
  return found
}

function census() {
  const map = {}
  for (const path of sources().sort()) {
    const hits = [...strip(readFileSync(path, "utf8")).matchAll(ORANGE)].map((m) => m[0])
    if (!hits.length) continue
    map[relative(SRC, path).replace(/\\/g, "/")] = [...new Set(hits)].sort()
  }
  return map
}

/**
 * Every file that still spells the accent as a hue, and which utilities it uses. **This is the
 * debt.** Delete a line when you migrate the file; never edit a line to make room for a new one.
 */
const ORANGE_DEBT = {
  // SH1026 — moved with the rows it colours; the page itself spells no hue any more.
  "app/content/content-browser/browser-views.tsx": ["bg-orange-100","bg-orange-400", "dark:bg-orange-900/30", "dark:text-orange-400", "text-orange-700"],
  "app/content/static-file.list.page.tsx": ["bg-orange-50", "bg-orange-50/80", "bg-orange-500", "border-orange-400", "dark:bg-orange-950/30", "dark:bg-orange-950/40", "dark:text-orange-300", "hover:border-orange-300", "ring-orange-300", "text-orange-500", "text-orange-700"],
  "app/content/trash.page.tsx": ["text-orange-500"],
  "app/editor/visual-editor.page.tsx": ["bg-orange-50", "dark:bg-orange-950/30", "text-orange-600"],
  "app/login/login-settings.tsx": ["bg-orange-600"],
  "app/login/login.page.tsx": ["text-orange-500"],
  "app/setup/setup.page.tsx": ["from-orange-500", "hover:from-orange-600", "text-orange-500"],
  "components/login/login-form.tsx": ["from-orange-500", "hover:from-orange-600"],
  "components/post-schedule.dialog.tsx": ["text-orange-500"],
  "components/widgets/content-browser-dialog.tsx": ["dark:text-orange-400", "text-orange-500"],
  "components/widgets/content-select.widget.tsx": ["text-orange-500"],
  "components/widgets/file.widget.tsx": ["text-orange-500"],
  "lib/widget-colors.ts": ["bg-orange-100", "border-orange-300", "dark:bg-orange-900/30", "dark:border-orange-800", "dark:text-orange-300", "text-orange-800"],
}

/** The four source tokens VDS23 made the whole re-keying contract. Everything else derives. */
const ACCENT_TOKENS = [
  "--vg-accent-from",
  "--vg-accent-to",
  "--vg-accent-text",
  "--vg-accent-text-dark",
]

test("SH890: the accent is declared once, on :root, and all four source tokens are there", () => {
  const css = readFileSync(join(SRC, "index.css"), "utf8")
  const root = /:root\s*\{([\s\S]*?)\}/.exec(css)

  assert.ok(root, "SH890: src/index.css declares no :root block, so nothing re-keys the accent")
  const missing = ACCENT_TOKENS.filter((token) => !root[1].includes(`${token}:`))

  assert.deepEqual(missing, [],
    "SH890: the accent re-key is partial. The preset derives the chip tint, the ring, the"
      + " accented label, the solid fill, the focus ring and both hover steps from these four —"
      + " declaring three of them leaves the rest at the package's blue, which reads as a broken"
      + " component rather than as a half-finished override.")
})

/*
 * SH910 — the header-chip test is gone, because its subject is.
 *
 * It asserted that `page-header.tsx` composed `vg-accent-chip` and `vg-accent-text` rather than
 * respelling the hue. That file is deleted, and no Shio source uses either class now: both are
 * defined in the design system's own stylesheet, and the components that compose them are the
 * package's, outside this repository.
 *
 * Deleting it loses nothing this file still covers. The four **tokens** are what Shio owns, and
 * the first test holds them; the census below holds every page that respells the hue instead of
 * reading them; and the vacuity test holds the pattern that finds them. A test kept alive by
 * re-pointing it at whichever file happens to be shared this month would assert less than any of
 * those, while looking like it asserted more.
 */

test("SH890: every remaining hardcoded accent is named, and the list only shrinks", () => {
  const found = census()
  const files = Object.keys(found).length
  const utilities = Object.values(found).reduce((n, list) => n + list.length, 0)

  // Both numbers on every run: "17 files" says nothing about how much is left inside them, and
  // a utility count alone hides a page quietly acquiring three more.
  console.log(`SH890: ${files} files still spell the accent as a hue, ${utilities} distinct utilities`)

  assert.deepEqual(found, ORANGE_DEBT,
    "SH890: the accent debt moved.\n"
      + "A file or utility in `+ actual` hardcodes Shio's orange. Use the token instead:"
      + " `vg-accent-chip` + `ring-1` for the icon chip, `vg-accent-text` for accented text,"
      + " `bg-[var(--vg-accent-surface)]` for a flat tint, `vg-accent-solid` for a filled"
      + " gradient. All of them derive from the four tokens in index.css, so a re-key reaches"
      + " them and reaches nothing spelled as a utility.\n"
      + "A file in `- expected` was migrated: delete its line here, in the same diff.\n"
      + "Note `amber` is not counted — that is the semantic warning colour, not the brand.")
})

test("SH890: the reader is not vacuous — it walks real files and its pattern matches", () => {
  assert.ok(sources().length > 100,
    `SH890: the walk yielded ${sources().length} files — the tree moved and this reader is`
      + " scanning an empty directory, which would read as a finished migration")
  assert.deepEqual(
    [...strip('x = "bg-orange-50 dark:text-orange-400 amber-500 orangeish"').matchAll(ORANGE)]
      .map((m) => m[0]),
    ["bg-orange-50", "dark:text-orange-400"],
    "SH890: the pattern must catch a variant-prefixed utility, and must not catch amber or a"
      + " word that merely contains 'orange' — both directions, because a pattern that matches"
      + " everything and a pattern that matches nothing both read as a clean tree")
})
