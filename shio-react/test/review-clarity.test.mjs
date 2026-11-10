/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1416 — the review queue led with a run's id, showed two Approve buttons once a run was open, and
 * never said what Revert did. A curator reported it as confusing; these are the three claims of the fix.
 */
import { strict as assert } from "node:assert"
import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { stripComments } from "./support/source-text.mjs"

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const read = (...path) => stripComments(readFileSync(join(ROOT, ...path), "utf8"))
const PAGE = read("src", "app", "admin", "review", "review.list.page.tsx")
const TRIAGE = read("src", "components", "review-triage.tsx")

test("SH1416: a run is never titled by its id", () => {
  assert.doesNotMatch(PAGE, /session\.session\.slice\(0, 8\)/,
    "SH1416: the run's short id is back on screen, and it tells a curator nothing")
  assert.match(PAGE, /title=\{t\("review\.runId", \{ run: session\.session \}\)\}/,
    "SH1416: the id stays reachable as the row's tooltip, for whoever needs to quote it")
})

test("SH1416: an open run has one Publish per scope, and the bulk toolbar only with a selection", () => {
  assert.match(TRIAGE, /\{ticked\.length > 0 && \(/,
    "SH1416: the triage toolbar is drawn over every open run again, a second Approve beside the run's")
  assert.match(TRIAGE, /onClick=\{\(\) => void decide\("APPROVE", \[entry\.postId!\]\)\}/,
    "SH1416: each page carries its own Publish")
})

test("SH1416: every discard says what it will do before it does it", () => {
  assert.doesNotMatch(PAGE, /onClick=\{\(\) => act\(session\.session, "revert"\)\}/,
    "SH1416: a run's discard acts on the press, with no word on what it undoes")
  assert.doesNotMatch(PAGE, /onClick=\{\(\) => void actMany\(ticked, "revert"\)\}/,
    "SH1416: the selection's discard acts on the press")
  assert.match(PAGE, /<ReviewDiscardDialog/)
  assert.match(TRIAGE, /<ReviewDiscardDialog/)
  for (const locale of ["en", "pt"]) {
    const review = JSON.parse(readFileSync(join(ROOT, "src", "i18n", "locales", locale, "review.json"), "utf8"))
    for (const key of ["review.discardCreated", "review.discardEdited", "review.discardLead"]) {
      assert.ok(review[key], `SH1416: ${locale} is missing ${key}`)
    }
  }
  const pt = readFileSync(join(ROOT, "src", "i18n", "locales", "pt", "review.json"), "utf8")
  assert.doesNotMatch(pt, /Reverter|Reter|Rejeitar/,
    "SH1416: the queue speaks of publishing and discarding, not of three verbs for two acts")
})
