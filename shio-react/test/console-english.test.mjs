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
 * SH823 — the console's own English, and the sweep that left no instrument.
 *
 * ## What this catches that `i18n-parity` cannot
 *
 * SH50 swept the remaining hardcoded English out of the console and routed it through i18n, by
 * hand, and nothing has looked since. `i18n-parity.test.mjs` is the obvious guard and asks a
 * different question: it asserts `en` and `pt` describe the same *set of keys*, which is arithmetic
 * and correct, and is blind to the failure SH50 actually fixed. **A string that is not a key cannot
 * be missing from a locale file.** A page that never calls `t()` at all passes parity for ever, and
 * the person who would notice — a curator reading the console in Portuguese — is not the person who
 * wrote the line.
 *
 * ## Two shapes, because those are the two a user reads
 *
 * A JSX **text node** (`>Save changes<`) and a user-visible **string prop** (`title=`,
 * `placeholder=`, `label=`, `description=`, `aria-label=`). Anything interpolated is already
 * `t(...)` or a value, so `{`/`}` in the span disqualifies it — which is why this needs no
 * whitelist of the hundreds of correct call sites.
 *
 * The text pattern demands a capital, a space and a lowercase word after it: `Save changes` and
 * `What would you like to post?` match, while `PROD`, `URL`, a className and a lone `Save` do not.
 * That is deliberately narrower than "any English" — the wide version is unreadable noise, and a
 * lint people route around catches nothing. Measured over 114 files: 15 hits, two of them the
 * product name.
 *
 * ## Comments are stripped, and not with the shared stripper
 *
 * A JSDoc `<h2>Why the install dialog is a dialog</h2>` is a heading in a comment and matched the
 * text pattern; three of the first fifteen hits were that. `cli/test/support/source-text.mjs`
 * exports exactly this, and importing it would reach across a workspace package boundary from one
 * package's tests into another's test support. SH822's rule applies in the direction it usually
 * does not: check what each is handed before merging or splitting. Here the input is TSX rather
 * than Markdown and the strip is three lines, so it is local and says why.
 */
const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src")

/**
 * Strings a user reads that must NOT be translated, each with the reason.
 *
 * <p>A named list rather than a count, which is this repository's contract for every population a
 * lint cannot decide: a ceiling lets one change pay it and the next spend it, and an entry added
 * here is a diff somebody reviews with the reason beside it.</p>
 */
const UNTRANSLATED = new Map(Object.entries({
  "app/login/login.page.tsx | text | Viglet Shio":
    "The product's name. Translating it would rename the product per locale, and the login page is "
    + "the one screen where a reader has no other confirmation of what they have connected to.",
  "app/setup/setup.page.tsx | text | Viglet Shio":
    "The same name on the first-run screen, and the same reason.",
  "app/admin/tenant/tenant.page.tsx | prop | FREE":
    "A plan identifier the backend stores and compares (`plan: \"FREE\"`), shown as the field's "
    + "placeholder so the curator types the value the API expects. Translating the placeholder "
    + "would teach a Portuguese operator to type a word the server rejects.",
}))

/** Every `.tsx` under `src`, repo-relative and separator-normalised. */
function sources(dir = SRC, found = []) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name)
    if (entry.isDirectory()) sources(path, found)
    else if (entry.name.endsWith(".tsx")) found.push(path)
  }
  return found
}

/** A whole-line comment: the JSDoc bodies this repository writes are prose about the component. */
const isProse = (line) => {
  const text = line.trim()
  return text.startsWith("//") || text.startsWith("*") || text.startsWith("/*")
}

const TEXT = />([A-Z][A-Za-z][^<>{}]{4,60})</g
const PROP = /\b(?:placeholder|title|label|description|aria-label)=\{?"([A-Z][^"]{2,60})"/g
/** A capital, a space, then more words — a sentence a person reads, not an identifier. */
const SENTENCE = /^[A-Z][a-z]+(?: [A-Za-z][\w'’,.!?-]*)+$/

function hardcoded() {
  const found = new Map()
  for (const path of sources()) {
    const body = readFileSync(path, "utf8").split("\n").filter((line) => !isProse(line)).join("\n")
    const file = relative(SRC, path).replace(/\\/g, "/")
    for (const [, text] of body.matchAll(TEXT)) {
      const trimmed = text.trim()
      if (SENTENCE.test(trimmed)) found.set(`${file} | text | ${trimmed}`, trimmed)
    }
    for (const [, text] of body.matchAll(PROP)) {
      found.set(`${file} | prop | ${text.trim()}`, text.trim())
    }
  }
  return found
}

test("SH823: every string a curator reads goes through i18n, or is on the untranslated list", () => {
  const found = [...hardcoded().keys()].sort()
  const declared = [...UNTRANSLATED.keys()].sort()

  assert.deepEqual(found, declared,
    "SH50/SH823: this string is rendered to a curator in English whatever locale they chose, and i18n "
    + "parity cannot see it — a string that is not a key cannot be missing from a locale file.\n"
    + "In `+ actual`: route it through `t(\"namespace.key\")` and add the key to EVERY locale "
    + "(i18n-parity.test.mjs enforces the second half). If it genuinely must not be translated — a "
    + "product name, a value the API compares — add it to UNTRANSLATED with the reason.\n"
    + "In `- expected`: the string moved or was translated, so drop the entry.")
})

test("SH823: the scan is not vacuous — it reads real components and finds the shapes it claims", () => {
  // A lint that matched nothing would pass for ever, which is the failure mode of every scan in
  // this repository that has ever been wrong (SH354's family). Two halves: the corpus is real, and
  // the patterns fire on the constructions the header describes.
  assert.ok(sources().length > 100,
    `expected the console's component tree, found ${sources().length} .tsx files`)

  const sample = '<Button title="Bold" />\n<p>What would you like to post?</p>\n'
    + '<span>{t("content.save")}</span>\n<div className="flex gap-2">PROD</div>'
  const text = [...sample.matchAll(TEXT)].map(([, s]) => s.trim()).filter((s) => SENTENCE.test(s))
  const prop = [...sample.matchAll(PROP)].map(([, s]) => s.trim())

  assert.deepEqual(prop, ["Bold"], "a user-visible prop with a literal is a finding")
  assert.deepEqual(text, ["What would you like to post?"],
    "and a JSX sentence is — while an interpolated `{t(...)}` is not, which is what makes the "
    + "hundreds of correct call sites cost no whitelist, and `PROD` is not, being an identifier")
})
