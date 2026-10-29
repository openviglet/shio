/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { readFileSync, readdirSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import i18next from "i18next"
import { registerVigTranslations } from "@viglet/viglet-design-system/i18n"

/**
 * SH891 — every string the design system's layer renders resolves, in English and in Portuguese.
 *
 * ## The roadmap line was nearly all wrong, and measuring is what said so
 *
 * §LIV.6 was filed as "the layer's strings have no Shio translations, so a shared rail and
 * palette would render raw keys", and the work it implied was a sweep: read the layer, find its
 * strings, write two locales' worth. The measurement says otherwise. The layer calls **54** keys.
 * **53 already resolved in both languages** — `registerVigTranslations` wires the package's own
 * bundle into this console's resources, so Shio inherits them. Exactly one did not: `home.title`,
 * the rail's Home button, which the package does not carry because a product's home is a
 * product's own word.
 *
 * And it would not have rendered a raw key either. The rail calls
 * `t("home.title", { defaultValue: "Home" })`, so a Portuguese console rendered the English word
 * **Home** — which is the failure mode worth naming, because a raw `home.title` on screen is
 * something anybody would report and an English word in a Portuguese UI is something most people
 * scroll past.
 *
 * ## So the sweep is not the deliverable; this reader is
 *
 * One key was added. What actually closes §LIV.6's concern is that the *next* design-system
 * release cannot add a string this console fails to translate: the package ships the
 * layer, so the set of keys it renders changes without anything in this repository changing, and
 * the only thing that would ever notice is somebody reading the console in Portuguese.
 *
 * ## Why it resolves through i18next instead of comparing objects
 *
 * The first draft flattened both bundles and compared key sets, and its model of the merge was
 * wrong. `registerVigTranslations` adds the package's bundle **per top-level namespace, and only
 * where the consumer does not already have that namespace** — and Shio's bundles are a mix:
 * `common.json` is flat dotted keys (`"common.cancel"`), while `home.json` nests under a `home`
 * object. Whether a flat `"common.cancel"` counts as owning the `common` namespace, and whether
 * i18next then finds a nested `common.cancel` underneath it, are questions about i18next's
 * `deepFind` that are not worth answering from memory. So this builds a real instance, registers
 * the real bundle with the real function, and asks `t()`.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const CONSOLE_ROOT = join(HERE, "..")
const SRC = join(CONSOLE_ROOT, "src")

/** The locales this console ships. Both, always, in the same commit that adds a key. */
const LOCALES = ["en", "pt"]

/**
 * Every dotted string literal in the built layer.
 *
 * Deliberately broad — `[a-z]…(\.[A-Za-z0-9_]+)+` — rather than anchored on the `bento.` prefix
 * or on a `t(` call. The bundle is minified, so `t` is a single renamed letter and matching the
 * call is not reliable; and anchoring on `bento.` would have missed 18 of the 54, including the
 * whole `forms.*` group and, as it happens, `home.title` — the one key that was actually missing.
 * Measured: the broad pattern yields **54 literals and no false positives**, because a bundle
 * this size simply has no other dotted lowercase strings in it.
 */
function bentoKeys() {
  const bundle = join(
    CONSOLE_ROOT, "node_modules", "@viglet", "viglet-design-system", "dist", "bento.es.js")
  return keysIn(readFileSync(bundle, "utf8"))
}

/**
 * The dotted literals a bundle renders as text, which is not every dotted literal in it.
 *
 * SH1088 — "no false positives" held until 2026.3.11, when `BentoActionsMenu` gave actions stable
 * dotted ids (`id: "entity.delete"`, rendered as `data-action-id`) and a warning that quotes one
 * inside a template string. Neither is a translation key. So a literal counts only when at least
 * one occurrence is neither an `id:` value nor inside a backtick template.
 */
export function keysIn(source) {
  const templates = [...source.matchAll(/`(?:\\.|[^`\\])*`/g)]
    .map((match) => [match.index, match.index + match[0].length])
  const insideTemplate = (at) => templates.some(([start, end]) => at > start && at < end)
  const rendered = new Set()
  for (const match of source.matchAll(/"([a-z][A-Za-z0-9]*(?:\.[A-Za-z0-9_]+)+)"/g)) {
    const isId = /\bid:\s*$/.test(source.slice(Math.max(0, match.index - 12), match.index))
    if (!isId && !insideTemplate(match.index)) rendered.add(match[1])
  }
  return [...rendered].sort()
}

test("SH1088: an action id and a quoted id in a warning are not translation keys", () => {
  const sample = 'a={id: "entity.delete"}; warn(`give it a name, such as "post.delete": x`);'
    + ' t("forms.save", {defaultValue: "Save"})'
  assert.deepEqual(keysIn(sample), ["forms.save"],
    "SH1088: the key reader counts an action id or a quoted example as a string to translate, "
      + "which is how 2026.3.11 made this suite demand translations for entity.delete")
})

/** Shio's own bundle for one locale, merged the way `src/i18n/index.ts` merges it. */
function shioBundle(locale) {
  const dir = join(SRC, "i18n", "locales", locale)
  const merged = {}
  for (const file of readdirSync(dir)) {
    Object.assign(merged, JSON.parse(readFileSync(join(dir, file), "utf8")))
  }
  return merged
}

/** A console-shaped i18next: Shio's resources, then the package's, through its own registrar. */
async function consoleI18n() {
  const instance = i18next.createInstance()
  await instance.init({
    resources: Object.fromEntries(
      LOCALES.map((locale) => [locale, { translation: shioBundle(locale) }])),
    lng: "en",
    fallbackLng: "en",
    interpolation: { escapeValue: false },
  })
  registerVigTranslations(instance)
  return instance
}

test("SH891: every key the bento chrome renders resolves in both locales", async () => {
  const i18n = await consoleI18n()
  const keys = bentoKeys()

  const unresolved = []
  for (const key of keys) {
    for (const lng of LOCALES) {
      if (i18n.t(key, { lng }) === key) unresolved.push(`${key} [${lng}]`)
    }
  }

  console.log(`SH891: ${keys.length} bento chrome keys, ${LOCALES.length} locales — `
    + `${unresolved.length} unresolved`)

  assert.deepEqual(unresolved, [],
    "SH891: the bento chrome renders a string this console cannot translate.\n"
      + "  If the package added the key, it usually ships the translation too — check that the\n"
      + "  installed version is current before writing one.\n"
      + "  If it is a key only Shio can answer (a product's own word, like home.title), add it to\n"
      + "  src/i18n/locales/{en,pt}/ in the SAME commit — both locales, always.\n"
      + "  Note a missing key here does not necessarily show as a raw key: most of these carry\n"
      + "  `defaultValue`, so the symptom is an English word in a Portuguese console, which is\n"
      + "  the kind of thing people scroll past rather than report.")
})

test("SH891: the reader is not vacuous — it reads the real bundle and a resolver that resolves", async () => {
  const keys = bentoKeys()

  assert.ok(keys.length > 40,
    `SH891: only ${keys.length} keys were extracted from the built layer. The bundle's shape`
      + " changed and this reader is now checking a handful of strings while the rest go"
      + " unwatched — which reads exactly like a console that translates everything.")
  for (const known of ["bento.palette.placeholder", "bento.shortcuts.title", "home.title"]) {
    assert.ok(keys.includes(known),
      `SH891: ${known} is no longer among the extracted keys, so the extraction is wrong rather`
        + " than the console being complete")
  }

  const i18n = await consoleI18n()
  assert.notEqual(i18n.t("bento.palette.placeholder", { lng: "pt" }), "bento.palette.placeholder",
    "SH891: a key known to be translated came back as itself — the merge is not happening and"
      + " every assertion above would be reporting the whole layer as broken, or none of it")
  assert.equal(i18n.t("sh891.a.key.nobody.declared", { lng: "en" }), "sh891.a.key.nobody.declared",
    "SH891: an undeclared key did NOT come back as itself, so `t()` is answering something for"
      + " everything and the check above can never fail")
})

test("SH891: home.title is Shio's word, and it is a different word in Portuguese", async () => {
  const i18n = await consoleI18n()
  const en = i18n.t("home.title", { lng: "en" })
  const pt = i18n.t("home.title", { lng: "pt" })

  // The one key the package does not carry, and the reason it does not: a product's home is a
  // product's own word. Asserting the two differ is what catches the copy-paste that "adds"
  // a Portuguese translation by duplicating the English one — which for this key, whose fallback
  // was already the English word, would leave the defect exactly where it was.
  assert.notEqual(en, "home.title", "SH891: home.title has no English value")
  assert.notEqual(pt, "home.title", "SH891: home.title has no Portuguese value")
  assert.notEqual(en, pt,
    "SH891: home.title reads the same in both locales. Its fallback was already the English word"
      + " `Home`, so a pt value identical to the en one restores the defect while looking fixed.")
})

test("SH891: the console still registers the package's bundle, or this reader models nothing", () => {
  const index = readFileSync(join(SRC, "i18n", "index.ts"), "utf8")

  assert.match(index, /registerVigTranslations\(i18n\)/,
    "SH891: src/i18n/index.ts no longer registers the package's translations. 53 of the 54 keys"
      + " the bento chrome renders come from that bundle, so without it the console falls back to"
      + " each call's defaultValue — English, everywhere, in every locale. This reader builds its"
      + " own instance the same way, so it would keep passing while the console did not.")
  assert.ok(index.includes("en: { translation:"),
    "SH891: src/i18n/index.ts no longer declares the en resource bundle in the shape this reader"
      + " reproduces")
  // SH1286 — Portuguese is no longer in the eager bundle: index.ts loads ./pt on demand, and that
  // module merges the same files this reader merges, so the model above still matches.
  assert.match(index, /import\("\.\/pt"\)/u,
    "SH1286: index.ts no longer loads the Portuguese bundle on demand")
  assert.ok(!/from "\.\/locales\/pt\//u.test(index),
    "SH1286: a static import of a Portuguese locale file puts it back in the eager bundle every"
      + " English reader downloads")
  const pt = readFileSync(join(SRC, "i18n", "pt.ts"), "utf8")
  for (const file of readdirSync(join(SRC, "i18n", "locales", "pt"))) {
    assert.ok(pt.includes(`./locales/pt/${file}`),
      `SH1286: src/i18n/pt.ts does not merge ${file}, so a Portuguese reader would miss its strings`)
  }
})
