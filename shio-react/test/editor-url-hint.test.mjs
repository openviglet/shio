// @scans prose reason: the subject is the console's translated strings, read as the text a curator sees.
/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { readFileSync, readdirSync, statSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"

/**
 * SH1410 — the Universal Editor's Open External URL hint names what a page needs.
 *
 * It told a curator to instrument a page "with data-sh-* attributes", the prefix of the second,
 * unspecified bridge SH304 deleted. A page following it carried nothing the one bridge reads. An
 * external front end needs the `@viglet/shio-editor-cors` package and the editor-origin meta tag,
 * and the hint now names both. The retired prefix has no other guard, so this scans every locale.
 */
const LOCALES = join(dirname(fileURLToPath(import.meta.url)), "..", "src", "i18n", "locales")
const files = (dir) => readdirSync(dir).flatMap((name) => {
  const path = join(dir, name)
  return statSync(path).isDirectory() ? files(path) : [path]
})

test("SH1410: no console string tells a curator to use the retired data-sh-* attributes", () => {
  const all = files(LOCALES).filter((path) => path.endsWith(".json"))
  assert.ok(all.length > 20, `SH1410: only ${all.length} locale files read`)
  const offenders = all.filter((path) => /data-sh-(?!o)/u.test(readFileSync(path, "utf8")))
  assert.deepEqual(offenders, [], "SH1410: a locale string names the data-sh-* prefix SH304 retired")
})

test("SH1410: the hint names the bridge package and the editor-origin meta tag, in both languages", () => {
  for (const lang of ["en", "pt"]) {
    const hint = JSON.parse(readFileSync(join(LOCALES, lang, "universal-editor.json"), "utf8"))["universalEditor.urlHint"]
    assert.match(hint, /@viglet\/shio-editor-cors/u, `SH1410: the ${lang} hint does not name the bridge`)
    assert.match(hint, /urn:shio:system:editor-origin/u, `SH1410: the ${lang} hint does not name the meta tag`)
  }
})
