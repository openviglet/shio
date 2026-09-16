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
 * SH1013 — the editor asks the instance what it already knows about this page.
 *
 * The rules that can be executed are, because they are pure: which images count as missing alt, and
 * what a word count becomes in minutes. The rest is asserted against the source, since the panel's
 * other claims are about *which endpoint is called with which scope* — and those are exactly the
 * facts a screenshot cannot show and a mocked render would let drift.
 *
 * @scans code
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const read = (...path) => readFileSync(join(ROOT, ...path), "utf8")

// The rules live apart from the HTTP client precisely so this line can exist: Node strips the
// types and runs the module the console runs, rather than a copy this file reconstructed.
const { imagesWithoutAlt, readingMinutes } = await import("../src/lib/insights-rules.ts")

test("SH1013: an absent alt is a finding and an empty one is a decision", () => {
  const digest = {
    images: [
      { src: "/none.png" },
      { src: "/decorative.png", alt: "" },
      { src: "/described.png", alt: "A chart" },
    ],
  }

  assert.deepEqual(imagesWithoutAlt(digest).map((image) => image.src), ["/none.png"],
    "SH1013: alt=\"\" declares an image decorative and an absent attribute declares nothing — the "
      + "two mean different things to a screen reader, so reporting both would train the curator "
      + "to ignore the group")
  assert.deepEqual(imagesWithoutAlt(null), [],
    "SH1013: a page that does not render has no images to judge, which is not the same as a page "
      + "whose images are fine")
})

test("SH1013: reading time never rounds a real page down to nothing", () => {
  assert.equal(readingMinutes(0), 1)
  assert.equal(readingMinutes(17), 1)
  assert.equal(readingMinutes(400), 2)
})

test("SH1013: both reads are scoped to the draft of this one page", () => {
  const service = read("src", "services", "insights", "insights.service.ts")

  assert.match(service, /"\/v2\/agent\/verify",\s*\{\s*params: \{ address, state: "draft" \}/u,
    "SH1013: the lint is about the page being edited, in the state it is being edited in — a "
      + "site-wide verify would report other people's pages into this curator's panel")
  assert.match(service, /"\/v2\/agent\/render",\s*\{\s*params: \{ address, state: "draft" \}/u)
  assert.match(service, /\.catch\(\(\) => null\)/u,
    "SH1013: a post with no layout has no digest, which is a fact about the page rather than an "
      + "error the curator has to dismiss")
})

test("SH1013: the panel reads on demand and on the curator's own save, not on autosave's", () => {
  const page = read("src", "app", "content", "post.edit.page.tsx")
  const panel = read("src", "components", "post-insights.panel.tsx")

  assert.match(page, /setInsightsAt\(\(n\) => n \+ 1\)/u)
  // Autosave fires every three seconds of typing and both reads render the page server-side. The
  // assertion is that autosave's own save path does NOT bump it.
  const autosaveBlock = /save: async \(attrs\) => \{[\s\S]*?\n    \},/u.exec(page)?.[0] ?? ""
  assert.ok(autosaveBlock.length > 0, "SH1013: the autosave block moved and this scan lost its subject")
  assert.doesNotMatch(autosaveBlock, /setInsightsAt/u,
    "SH1013: re-linting on every autosave is a page render every three seconds of typing, for an "
      + "answer nobody asked for — the same argument P3 makes about tokens, one layer out")
  assert.match(panel, /onClick=\{\(\) => void load\(\)\}/u, "SH1013: and on demand")
})

test("SH1013: a finding that knows its field offers a route to it", () => {
  const panel = read("src", "components", "post-insights.panel.tsx")
  const form = read("src", "components", "widgets", "post-form-fields.tsx")
  const page = read("src", "app", "content", "post.edit.page.tsx")

  assert.match(panel, /\{finding\.field && \(/u,
    "SH1013: a rule that knows no field — a broken link belongs to the rendered page, not to one "
      + "box — offers no button rather than one that lands somewhere arbitrary")
  assert.match(form, /export function fieldAnchorId/u)
  assert.match(form, /id=\{fieldAnchorId\(field\.name\)\}/u)
  assert.match(page, /document\.getElementById\(fieldAnchorId\(name\)\)/u,
    "SH1013: one spelling of the anchor, shared — written twice it drifts, and the failure is "
      + "silent because the button simply scrolls nowhere")
})

test("SH1013: the three groups stay three, and the digest is not a judgement", () => {
  const panel = read("src", "components", "post-insights.panel.tsx")

  assert.match(panel, /t\("insights\.blockers"\)/u)
  assert.match(panel, /t\("insights\.suggestions"\)/u)
  assert.match(panel, /t\("insights\.pageShape"\)/u,
    "SH1013: folding the digest into the findings would make \"this page is 340 words\" look like "
      + "something to fix")
  assert.match(panel, /severity === "error"/u)

  const keys = [...panel.matchAll(/t\("(insights\.[\w.]+)"/gu)].map(([, key]) => key)
  assert.ok(keys.length >= 10, `SH1013: only ${keys.length} key(s) found — the scan stopped matching`)
  for (const locale of ["en", "pt"]) {
    const strings = JSON.parse(read("src", "i18n", "locales", locale, "insights.json"))
    for (const key of keys) {
      assert.ok(strings[key], `SH1013: ${locale} is missing ${key}`)
    }
  }
})
