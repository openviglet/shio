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
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"

/**
 * SH333 — "add the key to `en` *and* `pt`" was a convention with no instrument.
 *
 * The console's own guidance states the rule, and nothing checked it. The failure is quiet in the
 * worst way: i18next falls back to the key itself, so a missing `pt` entry renders
 * `universalEditor.bridgeAbsent` to a Portuguese curator — visible only to somebody reading the
 * console in that language, which is not the person who added the key.
 *
 * A lint rather than a translation check: this asserts the two files describe the same *set of
 * strings*, never that a translation is good. The second is a judgement and this is arithmetic.
 */
const LOCALES = join(dirname(fileURLToPath(import.meta.url)), "..", "src", "i18n", "locales")

function localeNames() {
  return readdirSync(LOCALES, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .sort()
}

function keysOf(locale, file) {
  return Object.keys(JSON.parse(readFileSync(join(LOCALES, locale, file), "utf8"))).sort()
}

test("every locale carries the same namespaces", () => {
  const locales = localeNames()
  assert.ok(locales.length >= 2, `expected at least two locales, found ${locales.join(", ")}`)

  const [reference, ...others] = locales
  const expected = readdirSync(join(LOCALES, reference)).sort()
  for (const locale of others) {
    assert.deepEqual(readdirSync(join(LOCALES, locale)).sort(), expected,
      `${locale} and ${reference} declare different namespace files`)
  }
})

test("every locale carries the same keys, namespace by namespace", () => {
  const [reference, ...others] = localeNames()
  const missing = []

  for (const file of readdirSync(join(LOCALES, reference)).sort()) {
    const expected = keysOf(reference, file)
    for (const locale of others) {
      const actual = keysOf(locale, file)
      for (const key of expected) {
        if (!actual.includes(key)) missing.push(`${locale}/${file}: ${key}`)
      }
      // Both directions: a key only the translation has is a key the source dropped and
      // somebody's file still renders, which is the same drift facing the other way.
      for (const key of actual) {
        if (!expected.includes(key)) missing.push(`${reference}/${file}: ${key}`)
      }
    }
  }

  assert.deepEqual(missing, [],
    `i18n keys must land in every locale at once. Missing:\n  ${missing.join("\n  ")}`)
})
