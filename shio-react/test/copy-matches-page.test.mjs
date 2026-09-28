/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1202 — copy that contradicted the page. Search said it covered published content and listed only
 * drafts (it searches both, with a State filter); the login page promised server-side rendering with
 * Nashorn, a script engine the renderer does not have (it is Handlebars.java); and every Activity row
 * was an English sentence under Portuguese headings, because the server stores the sentence at write
 * time and no i18n scan reads a server string.
 */
// @scans prose — reason: the subjects are locale catalogues parsed as JSON and two page sources' call sites.
import { strict as assert } from "node:assert"
import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { activitySentence } from "../src/lib/activity-text.ts"

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const locale = (lang, ns) => JSON.parse(readFileSync(join(ROOT, "src", "i18n", "locales", lang, `${ns}.json`), "utf8"))
const translator = (lang) => {
  const keys = locale(lang, "activity")
  return (key, options = {}) => {
    const template = keys[key]
    if (template === undefined) return options.defaultValue ?? key
    return template.replace(/\{\{(\w+)\}\}/g, (_, name) => String(options[name] ?? ""))
  }
}

// The sentences the 2026-09-28 capture showed, as the server wrote them.
const SAMPLES = [
  ['Updated "Home" (renamed from "VigR3")', "UPDATE"],
  ['Updated "About — Viglet"', "UPDATE"],
  ['Published "Acme&nbsp;"', "PUBLISH"],
  ['Created "menu" (in Home)', "CREATE"],
]

test("SH1202: an Activity row reads in the console's language, with no English verb left", () => {
  const pt = translator("pt")
  const out = SAMPLES.map(([sentence, action]) => activitySentence(sentence, action, pt))
  assert.deepEqual(out, [
    "Atualizou “Home” (renomeado de “VigR3”)",
    "Atualizou “About — Viglet”",
    "Publicou “Acme ”".replace(" ”", "”"),
    "Criou “menu” (em Home)",
  ])
  for (const line of out) {
    assert.doesNotMatch(line, /\b(Created|Updated|Published|Unpublished|Deleted|Restored|renamed from)\b/,
      `SH1202: an English verb survived in "${line}"`)
    assert.doesNotMatch(line, /&nbsp;/, "SH1202: a literal entity reached the reader")
  }
})

test("SH1202: a sentence of another shape is shown as it came, not mistranslated", () => {
  assert.equal(activitySentence("Imported 12 posts", "CREATE", translator("pt")), "Imported 12 posts")
})

test("SH1202: both surfaces that list activity go through the translator", () => {
  for (const file of [["app", "admin", "activity", "activity.list.page.tsx"], ["app", "system", "home.page.tsx"]]) {
    const code = readFileSync(join(ROOT, "src", ...file), "utf8")
    assert.match(code, /activitySentence\(item\.description, item\.action, t\)/, `SH1202: ${file.at(-1)} prints the raw sentence`)
  }
})

test("SH1202: Search and the login page say what the product does", () => {
  for (const lang of ["en", "pt"]) {
    assert.doesNotMatch(locale(lang, "login")["login.featureTemplates"], /Nashorn/i,
      "SH1202: the renderer is Handlebars.java; there is no script engine to name")
  }
  assert.match(locale("pt", "content")["content.searchDescription"], /rascunhos e publicados/,
    "SH1202: the search subtitle must not say published-only over a list of drafts")
})
