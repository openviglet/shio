/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { createRequire } from "node:module"
import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"

/**
 * SH887 — the bento layer is installed here, and one page renders its whole vocabulary.
 *
 * ## Why a resolve check was not enough, and why a render is not available
 *
 * SH915 asserted that `@viglet/viglet-design-system/bento` **resolves**. That is a different
 * question from whether it *renders here*: the layer was extracted from one product, and the
 * failure this task exists to find is an assumption that extraction carried over — a context
 * that was always mounted, a custom property some global stylesheet always defined, a Tailwind
 * class the other preset happened to emit. A resolve check is blind to every one of those, and
 * so is `tsc`.
 *
 * The check that would catch them is a browser opening the page, which this console already has
 * in `e2e/console-check.mjs` — and which needs a dev server and a backend, so it is not part of
 * the `pnpm test` gate. What *is* cheap enough for the gate is the question that decides whether
 * the browser check has anything to look at: **does the page still compose every component the
 * package exports?** That is the one that rots. A design-system release adds a component, nobody
 * here notices, and the proving page silently proves less than it did — the same shape as SH886's
 * census, which is why this holds a named list rather than a count.
 *
 * ## Why a named list on both sides
 *
 * `RENDERED` is what the harness composes. `DEFERRED` is what it deliberately does not, each
 * entry naming the task that owns it. Their union must be exactly the package's runtime export
 * set: a new export lands in neither, and this fails with its name. A ceiling ("the page renders
 * at least 20 of them") would let one addition pay for the next, which is the failure mode this
 * repository has now hit often enough to have a rule about.
 *
 * The deferred twelve are all page-level singletons. The shell pieces have no page to sit on
 * until SH888 builds one; the nav-driven surfaces have no nav declaration until SH889 writes
 * one; and `BentoListPage`, `BentoEntityShell` and `BentoFormHero` each own a hero and the
 * save-bar morph, so putting one on this page means two heroes and two fixed save bars on the
 * same screen — precisely the drift `BENTO-AUTHORING.md` §1 warns against.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const CONSOLE_ROOT = join(HERE, "..")
const PAGE = join(CONSOLE_ROOT, "src", "app", "system", "bento-vocabulary.page.tsx")

const require_ = createRequire(join(CONSOLE_ROOT, "noop.js"))

/**
 * The layer's runtime exports, read from the shipped types rather than hardcoded, so a package
 * release moves this list without anybody editing it. Type-only re-exports are dropped: they are
 * not things a page can render, and holding them would make the deferred list meaningless.
 */
function bentoExports() {
  // Resolved through the exports map rather than by walking node_modules, so this reads whatever
  // pnpm actually laid down — the lesson SH915 wrote down after a local `dist` copy made one
  // machine green and left CI to find out.
  const entry = require_.resolve("@viglet/viglet-design-system/bento")
  const source = readFileSync(join(dirname(entry), "bento", "index.d.ts"), "utf8")
  const names = new Set()
  for (const [, clause] of source.matchAll(/export\s*\{([^}]*)\}/g)) {
    for (const raw of clause.split(",")) {
      const part = raw.trim()
      if (!part || /^type\s/.test(part)) continue
      names.add(part.split(/\s+as\s+/).pop().trim())
    }
  }
  return [...names].sort()
}

/** What the proving page composes. Every name here must appear in its source. */
const RENDERED = [
  "AdaptiveSectionCard",
  "BENTO_EMPHASIS_NEXT",
  "BENTO_EMPHASIS_SPAN",
  "BENTO_TONES",
  "BENTO_TONE_CLASS",
  "BentoActionsMenu",
  "BentoBackLink",
  "BentoCountTile",
  "BentoEmptyState",
  "BentoEntityTile",
  "BentoFormSection",
  "BentoPanel",
  "BentoHero",
  "BentoHeroIconPicker",
  "BentoInlineEdit",
  "BentoSection",
  "BentoStatusMarker",
  "BentoTile",
  "BentoTileGrid",
  "bentoChipClass",
  "resolveBentoLayout",
  "toBentoLayoutEntries",
]

/**
 * What the harness deliberately does not render, and the task that owns each. Moving a name out
 * of here is how a later task says "this now renders somewhere real"; leaving one here for ever
 * is visible, because the owning id is written beside it.
 */
const DEFERRED = {
  BentoNavRail: "SH888 — the shell's left rail; there is no bento route tree to mount it in yet.",
  BentoUserMenu: "SH888 — the shell's header slot, and it needs account and sign-out routes.",
  BentoBackToTop: "SH888 — a fixed corner control, which belongs to the shell and not to a page.",
  BentoCommandPalette: "SH889 — needs the nav declaration a rail and a palette share.",
  BentoShortcutsDialog: "SH889 — the palette's sibling, keyed to the same nav declaration.",
  bentoNavTarget: "SH889 — the helper that resolves a nav item to a route; no nav array exists.",
  BentoListPage:
    "SH899 — a whole list screen including its own hero. The mosaic half, BentoTileGrid, is"
    + " rendered here; the hero half would be a second h1 on a page that already has one.",
  BentoEntityShell:
    "SH901 — a whole detail screen: identity hero, save-bar morph and delete flow, over a form"
    + " it renders as a prop. It needs an entity to edit, and this harness fetches nothing.",
  BentoFormHero:
    "SH892 — the own-hero form, and both halves of the save-bar morph. Two morphs on one page"
    + " is the drift BENTO-AUTHORING.md §1 names by hand.",
  BentoSaveBar: "SH892 — the fixed half of the morph; BentoFormHero is what composes it.",
  BentoScrollSaveBar: "SH892 — the scroll-driven half, for the same reason.",
  useBentoScrollFade: "SH892 — the hook behind the morph, exercised by whatever renders it.",
  // SH1088 — 2026.3.11 and .12 ended eleven design-system waits, and these are what they shipped.
  // Each is a whole surface or a shell piece, so each belongs to the task that adopts it rather
  // than to a harness that fetches nothing.
  // SH1191 round — 2026.3.15 shipped VDS184's ancestor trail. It is a hero's way back, so it
  // belongs to the task that gives every route one page anatomy.
  BentoTrail: "SH1192 — rendered by page-hero.tsx: the folder browser's and the post editor's trail, through both package heroes.",
  BentoShell: "SH946 — rendered by bento.root.page.tsx as the console's shell; a harness has no route tree to wrap.",
  BentoPaletteTrigger: "SH946 — the shell's palette control, mounted with the shell.",
  BENTO_SHELL_SHORTCUTS: "SH946 — the shell's shortcut table, read by the shell it belongs to.",
  useBentoShellShortcuts: "SH946 — the shell's shortcut hook, exercised by the shell.",
  BentoDiff: "SH988 — the word-level diff the version dialog adopts over two revisions.",
  BentoVersionRail: "SH988 — the revision rail beside that diff.",
  BentoFilterBar: "SH1024 — the filter bar over the query vocabulary, on four surfaces.",
  EMPTY_FILTERS: "SH1024 — the bar's empty state, used with it.",
  BentoDataTable: "SH1026 — the table view the content browser offers and does not render.",
  // SH971 — 2026.3.13 answered four requests Shio filed (VDS171-VDS174) and VDS170; each whole
  // surface belongs to the line that adopts it.
  BentoCalendar: "SH1082 — rendered by scheduled.page.tsx as its calendar view; a harness has no schedule to draw.",
  BentoSplitPage: "SH1094 — rendered by post.edit.page.tsx and translate.page.tsx; a harness has no form to split.",
  BentoChangeMark: "SH1178 — rendered by bulk-plan.dialog.tsx per change; a harness has no plan to mark.",
}

test("SH887: the harness accounts for every runtime export the bento layer ships", () => {
  const exported = bentoExports()
  const accounted = [...RENDERED, ...Object.keys(DEFERRED)].sort()

  // Printed on every run, because the interesting number is the ratio and neither half says
  // anything alone — "23 rendered" is not progress if the package ships sixty.
  console.log(
    `SH887: ${exported.length} bento runtime exports — ${RENDERED.length} rendered by the`
      + ` proving page, ${Object.keys(DEFERRED).length} deferred to a named task`,
  )

  assert.deepEqual(accounted, exported,
    "SH887: the bento vocabulary moved.\n"
      + "A name in `+ actual` is exported by the package and rendered nowhere in this console —"
      + " add it to RENDERED and compose it in bento-vocabulary.page.tsx, or add it to DEFERRED"
      + " with the task that owns it. A name in `- expected` was removed from the package, so"
      + " delete it here and from whatever renders it.\n"
      + "This is the reader that keeps the proving page proving the whole layer rather than"
      + " whatever it happened to compose on the day it was written.")
})

test("SH887: every name the harness claims to render actually appears in the page", () => {
  const source = readFileSync(PAGE, "utf8")
  const missing = RENDERED.filter((name) => !new RegExp(`\\b${name}\\b`).test(source))

  assert.deepEqual(missing, [],
    "SH887: RENDERED names a component the proving page does not mention. The list is only worth"
      + " anything while it describes the page — a name that drifted out of the source and stayed"
      + " in the list turns this reader into a second place to keep a stale inventory.")
})

test("SH887: the page imports the bento subpath, which is what states its era", () => {
  const source = readFileSync(PAGE, "utf8")
  assert.match(source, /@viglet\/viglet-design-system\/bento/,
    "SH887: the proving page must import from the bento subpath. The design system ships bento"
      + " as a subpath rather than in the root barrel precisely so an import states which era a"
      + " page belongs to — SH886's census reads that import and nothing else.")
})

test("SH887: the console loads the bento stylesheet, or the layer mounts unstyled", () => {
  const css = readFileSync(join(CONSOLE_ROOT, "src", "index.css"), "utf8")
  assert.match(css, /@viglet\/viglet-design-system\/bento\.css/,
    "SH887: index.css does not import bento.css. The package ships the layout maths and the CSS"
      + " as two entries, so a consumer that imports only the components gets working, tested,"
      + " completely unstyled markup — a failure no unit test and no type check can see.")
})

test("SH887: the proving route's segment is reserved, or it reads as a tenant", () => {
  const consts = readFileSync(join(CONSOLE_ROOT, "src", "app", "routes.const.ts"), "utf8")
  const reserved = /RESERVED_TOP_SEGMENTS\s*=\s*new Set\(\[([\s\S]*?)\]\)/.exec(consts)

  assert.ok(reserved, "SH887: RESERVED_TOP_SEGMENTS is no longer a Set literal here")
  assert.match(reserved[1], /"bento-vocabulary"/,
    "SH887: the proving page is a top-level route, and readTenantSegment() treats every"
      + " unreserved first segment as a tenant — so /bento-vocabulary would set TENANT_SEGMENT to"
      + " itself and prefix every ROUTES.* link the page renders with a tenant that does not"
      + " exist. The page would still render, which is what makes this worth asserting.")
})

test("SH887: the reader is not vacuous — it reads a real export list and a real page", () => {
  const exported = bentoExports()
  assert.ok(exported.length > 20,
    `SH887: parsed ${exported.length} exports out of the layer's index.d.ts — the shipped types`
      + " changed shape and this reader is now comparing two lists against nothing")
  assert.ok(exported.includes("BentoHero"),
    "SH887: BentoHero is not in the parsed export set, so the parse is wrong rather than the page")
  assert.ok(readFileSync(PAGE, "utf8").length > 2000,
    "SH887: the proving page is too small to be composing the vocabulary it claims to")
})
