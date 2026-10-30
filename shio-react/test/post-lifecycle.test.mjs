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
import { stripComments } from "./support/source-text.mjs"
import { lifecycleKey, paletteDescription } from "../src/lib/post-lifecycle.ts"

/**
 * SH1324 — what the command palette and the editor header say about a post.
 *
 * Ctrl+K "Pricing" listed "PricingSectionHero Rascunho" for a page the site was serving: the title
 * glued to the type, the state read off the working row (DRAFT on every live post), and the group
 * headed "Acoes e conteudo" in a Portuguese console.
 */
const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src")
const read = (...parts) => stripComments(readFileSync(join(SRC, ...parts), "utf8"))

test("SH1324: a live post reads Published, whatever row is open", () => {
  assert.equal(lifecycleKey(true), "content.filterPublished")
  assert.equal(lifecycleKey(false), "content.filterDraft")
  assert.equal(lifecycleKey(undefined), "content.filterDraft")
})

test("SH1324: a palette row's type and state are set apart from its title", () => {
  assert.equal(paletteDescription("SectionHero", "Publicado"), "· SectionHero · Publicado",
    "SH1324: the package joins title and description with nothing between, so the type ran into the title")
  assert.equal(paletteDescription(undefined, "Rascunho"), "· Rascunho")
})

test("SH1324: the palette and the header ask whether the post is live, not which row they read", () => {
  assert.match(read("app", "system", "bento.root.page.tsx"),
    /description: paletteDescription\(row\.type, t\(lifecycleKey\(row\.published\)\)\)/u,
    "SH1324: the palette reads the working row's state again, which is DRAFT on every live post")
  assert.match(read("app", "content", "post.edit.page.tsx"), /t\(lifecycleKey\(isLive\)\)\]/u,
    "SH1324: the editor's header says Draft above a badge saying the page is live")
})

/**
 * Words Portuguese never writes without their accent. Not "esta" or "e", which are words as they
 * stand; only forms with no unaccented reading, so a hit is always a typo. A URL is skipped.
 */
const ALWAYS_ACCENTED = /\b(conteudos?|paginas?|acoes|acao|revisao|midias?|nao|voce|tambem|configurac(ao|oes)|informac(ao|oes)|publicac(ao|oes)|sessao|vers(ao|oes)|historico|codigo|titulos?|usuarios?|ultim[oa]s?|possivel|disponivel)\b/iu

test("SH1324: no Portuguese string drops an accent its word always has", () => {
  const dir = join(SRC, "i18n", "locales", "pt")
  const found = []
  for (const file of readdirSync(dir).filter((name) => name.endsWith(".json"))) {
    for (const [key, value] of Object.entries(JSON.parse(readFileSync(join(dir, file), "utf8")))) {
      if (typeof value !== "string" || /^https?:\/\//u.test(value)) continue
      const hit = ALWAYS_ACCENTED.exec(value)
      if (hit) found.push(`${file} ${key}: "${hit[0]}"`)
    }
  }
  assert.deepEqual(found, [], "SH1324: a pt string is missing its accent")
})
