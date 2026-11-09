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
 * SH1387 — the Marketplace dialog offers "Accept enquiries now" for a package that proposes forms,
 * off unless the administrator ticks it, and only on a plan that showed where submissions would go.
 * Read from source, like the other dialog guards, because the claim is about what the dialog sends.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(join(HERE, "..", "src", "app", "admin", "marketplace", "marketplace.setup.tsx"), "utf8")
const en = JSON.parse(readFileSync(join(HERE, "..", "src", "i18n", "locales", "en", "marketplace.json"), "utf8"))
const pt = JSON.parse(readFileSync(join(HERE, "..", "src", "i18n", "locales", "pt", "marketplace.json"), "utf8"))

test("SH1387: opening the forms is off by default and sent only for a plan that proposed them", () => {
  assert.match(source, /const \[openForms, setOpenForms\] = useState\(false\)/u,
    "SH1387: a package's forms ship closed; the dialog must not open them unless asked")
  assert.match(source, /openForms: openForms && Boolean\(plan\?\.forms\)/u,
    "SH1387: the consent is sent only when the plan on screen named the forms step")
})

test("SH1387: the offer is drawn with the plan, naming the folder, the types and the origins", () => {
  const offer = source.slice(source.indexOf("{plan?.forms && ("), source.indexOf("{done && ("))
  assert.match(offer, /id="marketplace-setup-forms"/u)
  assert.match(offer, /formsPlan/u, "SH1387: the admin sees where submissions go before saying yes")
  assert.match(offer, /configuration === "kept"/u,
    "SH1387: and is told when the site's own form configuration is left as it is")
})

test("SH1387: every string the offer draws exists in both languages", () => {
  for (const key of ["formsOffer", "formsPlan", "formsSections", "formsKept", "formsOpened", "formsOpenedDisabled"]) {
    const enValue = en.marketplace?.[key] ?? en[key]
    const ptValue = pt.marketplace?.[key] ?? pt[key]
    assert.ok(enValue, `SH1387: en has no marketplace.${key}`)
    assert.ok(ptValue, `SH1387: pt has no marketplace.${key}`)
  }
})
