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
import { fillFromDefaults, placementDocument, unfilled } from "../src/app/editor/pattern-placement.ts"
import * as cli from "../../cli/src/patterns-store.mjs"
import { stripComments } from "./support/source-text.mjs"

/**
 * SH1108 — a pattern placed from the rail writes the section array `shio patterns apply` writes.
 *
 * The console cannot import the CLI, so it carries a mirror of `placementDocument`; this holds the
 * mirror to the definition on the same page and the same pattern, at every position. Equal
 * documents are equal writes: both go to the one `POST /agent/apply`.
 */
const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src")

const PATTERN = {
  formatVersion: 1,
  name: "promo",
  params: { headline: { default: "Plans" } },
  sections: [
    { type: "SectionHero", data: { heading: "{{headline}}", subheading: "Fixed" } },
    { type: "SectionCta", data: { heading: "Go" } },
  ],
}

const PAGES = [
  { address: "post:Acme/pricing", type: "Page",
    data: { title: "Pricing", sections: ["/sections/hero-1", "/sections/cta-1"] } },
  // A page with no sections yet: the folder falls back, and the placement is also the first.
  { address: "post:Acme/about", type: "Page", data: { title: "About" } },
  // The home page, whose url is the one the grammar does not spell (SH433).
  { address: "post:Acme/", type: "Page", data: { title: "Home", sections: ["/blocks/intro"] } },
]

test("SH1108: the rail's placement document is the CLI's, at every position", () => {
  const filledByCli = cli.fillPattern(cli.parsePattern(JSON.stringify(PATTERN)), {})
  const filledByRail = fillFromDefaults(PATTERN)
  assert.deepEqual(filledByRail.sections, filledByCli.sections,
    "SH1108: filling from the declared defaults is the CLI's fill with no --param")

  for (const page of PAGES) {
    const held = Array.isArray(page.data.sections) ? page.data.sections.length : 0
    for (const after of [undefined, 0, 1, held, held + 5]) {
      const rail = placementDocument({ pattern: filledByRail, page, instance: "promo", after })
      const shell = cli.placementDocument({ pattern: filledByCli, page, at: page.address, instance: "promo", after })
      assert.deepEqual(rail.document, shell.document,
        `SH1108: ${page.address} after ${after}: a pattern placed from the rail writes the array the CLI writes`)
      assert.deepEqual(rail.urls, shell.urls)
      assert.equal(rail.where, shell.where)
    }
  }
})

test("SH1108: a placeholder with no default is not placed from the rail, and says which", () => {
  const needsInput = { ...PATTERN, params: {}, sections: [{ type: "SectionHero", data: { heading: "{{headline}}" } }] }
  assert.deepEqual(unfilled(needsInput), ["headline"],
    "SH1108: the rail has no form for a value only the person placing it knows, so it names it")
  assert.deepEqual(unfilled(PATTERN), [])
})

test("SH1108: the rail places through the agent apply, not a write path of its own", () => {
  const page = stripComments(readFileSync(join(SRC, "app", "editor", "visual-editor.page.tsx"), "utf8"))
  assert.match(page, /placementDocument\(/, "SH1108: the rail builds the CLI's document")
  const service = stripComments(readFileSync(join(SRC, "services", "universal-editor", "universal-editor.service.ts"), "utf8"))
  assert.match(service, /"\/v2\/agent\/apply"/, "SH1108: and sends it where the CLI sends it")
})
