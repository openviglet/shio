/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1196 — the token, webhook, tenant and two provider editors each drew a second bar under the hero:
 * a hand-rolled `sticky top-0` div that repeated the record's name, stayed on screen for good and held
 * Delete beside Save. The contract's save bar is the morph (controls in the hero, a fixed bar only once
 * the hero scrolls away), destructive verbs sit in the hero's trailing menu, and nothing on those
 * pages said a field had been changed. Their help text hid behind an info icon as well.
 */
import { strict as assert } from "node:assert"
import { readdirSync, readFileSync } from "node:fs"
import { dirname, join, relative } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { stripComments } from "./support/source-text.mjs"

const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src")
const EDITORS = ["api-token", "site-webhook", "tenant", "auth-provider", "exchange-provider", "email-config"]
const editor = (name) => stripComments(readFileSync(join(SRC, "app", "admin", name, `${name}.page.tsx`), "utf8"))

test("SH1196: no page hand-rolls a sticky save bar", () => {
  // The visual editor owns the viewport outside the shell (SH907), so its toolbar is its own layout.
  const EXEMPT = new Set(["app/editor/visual-editor.page.tsx"])
  const offenders = []
  const walk = (dir) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const path = join(dir, entry.name)
      if (entry.isDirectory()) walk(path)
      else if (entry.name.endsWith(".page.tsx")) {
        const name = relative(SRC, path).replace(/\\/g, "/")
        if (!EXEMPT.has(name) && /className="[^"]*\bsticky\b[^"]*\btop-0\b/.test(stripComments(readFileSync(path, "utf8")))) {
          offenders.push(name)
        }
      }
    }
  }
  walk(join(SRC, "app"))
  assert.deepEqual(offenders, [], "SH1196: a page composes its own sticky bar; the save bar is BentoFormHero's morph")
})

test("SH1196: every infrastructure editor says when it has an unsaved change", () => {
  for (const name of EDITORS) {
    assert.match(editor(name), /<PageHero[\s\S]*\bdirty=\{[^}]*\bisDirty\(/, `SH1196: ${name} passes no dirty cue to its save bar`)
  }
})

test("SH1196: destructive verbs are in the hero's trailing menu, not beside Save", () => {
  for (const name of EDITORS.filter((one) => one !== "email-config")) {
    const code = editor(name)
    assert.match(code, /trailing=\{[\s\S]{0,120}<BentoActionsMenu[\s\S]{0,400}tone: "destructive"/,
      `SH1196: ${name}'s delete is not in the hero's trailing menu`)
    assert.doesNotMatch(code, /<Button[^>]*text-destructive[^>]*onClick=\{handleDelete\}/,
      `SH1196: ${name} draws Delete as a button again`)
  }
})

test("SH1196: a setting's help is on the page, not behind an info icon", () => {
  for (const name of EDITORS) {
    assert.doesNotMatch(editor(name), /common\.moreInfo/, `SH1196: ${name} hides a setting's help in a tooltip`)
  }
})
