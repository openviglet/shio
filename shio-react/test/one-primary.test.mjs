/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1195 — screens carried several primary styles. The site browser showed two hardcoded green fills
 * beside the brand's orange (Ver Site, Baixar Site) and a disabled bulk menu before anything was
 * selected; a post type managed by code offered Save, Save and Close and Cancel on a read-only form;
 * and the trash put "delete permanently" in red on every row. The rule is one filled primary per
 * screen, secondaries outlined, and destructive or secondary verbs in the ⋯ menu.
 */
import { strict as assert } from "node:assert"
import { readdirSync, readFileSync } from "node:fs"
import { dirname, join, relative } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { stripComments } from "./support/source-text.mjs"

const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src")
const read = (...parts) => stripComments(readFileSync(join(SRC, ...parts), "utf8"))

test("SH1195: no button paints a saturated colour of its own", () => {
  // A filled colour on a button is a claim to be the primary. The primary is `--primary`, which the
  // default Button variant already reads, so a hand-picked green is a second primary by construction.
  const FILL = /<(Button|GradientButton)\b(?:(?!<\/?(?:Button|GradientButton)\b)[\s\S]){0,400}?\bbg-(green|emerald|red|rose|blue|sky|orange|amber|violet|purple|pink)-[4-9]00\b/
  const offenders = []
  const walk = (dir) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const path = join(dir, entry.name)
      if (entry.isDirectory()) walk(path)
      else if (entry.name.endsWith(".tsx") && FILL.test(stripComments(readFileSync(path, "utf8")))) {
        offenders.push(relative(SRC, path).replace(/\\/g, "/"))
      }
    }
  }
  walk(join(SRC, "app"))
  walk(join(SRC, "components"))
  assert.deepEqual(offenders, [], "SH1195: a button fills itself with a colour that is not --primary")
})

test("SH1195: the browser's bulk verbs appear with a selection, and site actions share the ⋯ menu", () => {
  const toolbar = read("app", "content", "content-browser", "browser-toolbar.tsx")
  assert.match(toolbar, /\{selectedItems\.size > 0 && <DropdownMenu>/,
    "SH1195: the bulk menu is drawn disabled before there is anything to act on")
  assert.doesNotMatch(toolbar, /downloadSite/, "SH1195: Download Site is back in the toolbar")
  const page = read("app", "content", "object.children.page.tsx")
  assert.match(page, /<BentoActionsMenu actions=\{\[[\s\S]*id: "site\.export"/,
    "SH1195: Download Site is not in the hero's overflow menu")
})

test("SH1195: a read-only post type offers no save", () => {
  const page = read("app", "content", "post-type.edit.page.tsx")
  assert.match(page, /onCancel=\{managed \? undefined :/, "SH1195: a managed type still gets the save-bar morph")
  assert.match(page, /actions=\{managed \? undefined :/, "SH1195: a managed type still shows save buttons")
})

test("SH1195: the trash keeps permanent delete in the row's menu, never a visible button", () => {
  const page = read("app", "content", "trash.page.tsx")
  // SH1227 moved both verbs into the package table's row menu and selection bar; Purge is the
  // menu's destructive item, and nothing on the page draws it as a button.
  assert.match(page, /id: "purge"[^}]*tone: "destructive"/,
    "SH1195: permanent delete is not the menu's destructive item")
  assert.match(page, /rowActions=\{\[restoreAction, purgeAction\]\}/,
    "SH1195: Restore and permanent delete are not the row's menu")
  assert.doesNotMatch(page, /<Button[^>]*text-destructive[\s\S]{0,120}purge\(/,
    "SH1195: permanent delete is a visible button on every row again")
})
