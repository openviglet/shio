/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"

/**
 * SH1010 — the console's half: the rules a field declares, edited and shown.
 *
 * What can go wrong here is an editor that writes an empty block on every field (which would push a
 * change to every field of every type the first time anybody opened one), a form that stays silent
 * about a rule until a save refuses it, and a hint that quotes a regular expression at the person
 * filling the box.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const read = (...path) => readFileSync(join(ROOT, ...path), "utf8")

test("SH1010: a cleared constraint is deleted, and an empty block is no block", () => {
  const editor = read("src", "components", "field-constraints.tsx")

  assert.match(editor, /delete next\[key\]/,
    "SH1010: a cleared key written as null is a key, and the server stores no block when one says "
      + "nothing — the two spellings would differ on the next pull")
  assert.match(editor, /constraints: Object\.keys\(next\)\.length > 0 \? next : undefined/,
    "SH1010: and a block with nothing left in it becomes no block at all (P5)")
})

test("SH1010: the pattern and its message are one thing on screen", () => {
  const editor = read("src", "components", "field-constraints.tsx")
  const pattern = editor.indexOf("content.constraintPattern\"")
  const message = editor.indexOf("content.constraintPatternMessage")

  assert.ok(pattern > 0 && message > pattern,
    "SH1010: a regular expression quoted back at a curator is a refusal only its author can act "
      + "on (P6), and the way to make that unlikely is for the two boxes to sit together")
})

test("SH1010: the form says the rule before a save does, and never quotes the expression", () => {
  const form = read("src", "components", "widgets", "post-form-fields.tsx")
  const editor = read("src", "components", "field-constraints.tsx")

  // SH1052 gave the paragraph an id so the control can point at it — the hint was prose nobody
  // using a screen reader was told about. The claim is unchanged and the spelling moved, so this
  // asserts the two halves rather than one line of markup.
  assert.match(form, /\{hint\(field\) && \(/u,
    "SH1010: the whole point of the vocabulary reaching this form is that a rule stops being a "
      + "discovery on save")
  assert.match(form, /id=\{`\$\{ids\.description\}-hint`\}/u,
    "SH1052: and the hint carries an id the field's control names in `aria-describedby`, or the "
      + "rule is shown to a sighted reader and to nobody else")
  assert.match(editor, /if \(rules\.patternMessage\) parts\.push\(rules\.patternMessage\)/)
  assert.doesNotMatch(editor, /parts\.push\([^)]*rules\.pattern\b[^M]/,
    "SH1010: the hint shows the message, never the expression")
})

test("SH1010: advice is shown as advice", () => {
  const editor = read("src", "components", "field-constraints.tsx")

  assert.match(editor, /content\.hintRecommended/)
  const en = JSON.parse(read("src", "i18n", "locales", "en", "content.json"))
  assert.match(en["content.hintRecommended"], /never refused/,
    "SH1010: a recommendation the form presented as a rule would teach people to raise the number "
      + "rather than shorten the text")
})

test("SH1010: both locales name every control and every hint", () => {
  const keys = ["content.constraints", "content.constraintMinLength", "content.constraintMaxLength",
    "content.constraintMin", "content.constraintMax", "content.constraintMinItems",
    "content.constraintMaxItems", "content.constraintRecommendedLength",
    "content.constraintPattern", "content.constraintPatternMessage", "content.constraintValues",
    "content.constraintDefaultValue", "content.constraintUnique", "content.hintMinLength",
    "content.hintMaxLength", "content.hintRange", "content.hintValues", "content.hintMinItems",
    "content.hintMaxItems", "content.hintUnique", "content.hintRecommended"]

  for (const locale of ["en", "pt"]) {
    const content = JSON.parse(read("src", "i18n", "locales", locale, "content.json"))
    for (const key of keys) {
      assert.ok(content[key], `SH1010: ${locale} is missing ${key}`)
    }
  }
})
