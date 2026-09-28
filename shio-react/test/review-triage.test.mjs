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
 * SH1001 — the review queue decides one change at a time.
 *
 * Read from source, like the other console surface tests. What can go wrong here is a control the
 * server would refuse (a rejection with no reason), a keyboard that fires while somebody is typing
 * that reason, and a console that decides for itself what approving would do.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const read = (...path) => readFileSync(join(ROOT, ...path), "utf8")

test("SH1001: reject is disabled until there is a reason, and the server refuses it too", () => {
  const triage = read("src", "components", "review-triage.tsx")

  assert.match(triage, /disabled=\{busy \|\| !note\.trim\(\)\}/,
    "SH1001: the server refuses a rejection with nothing written down, and the button knows it — a "
      + "curator should not learn the rule from a refusal")
  assert.match(triage, /decision === "REJECT" && !note\.trim\(\)/,
    "SH1001: and the keyboard path refuses it too, or the rule is a property of one button")
  assert.match(triage, /t\("review\.rejectNeedsNote"\)/,
    "SH1001: with the reason, not a dead control")
})

test("SH1001: the keyboard never fires while the reason is being typed", () => {
  const triage = read("src", "components", "review-triage.tsx")

  assert.match(triage,
    /event\.target instanceof HTMLInputElement \|\| event\.target instanceof HTMLTextAreaElement/,
    "SH1001: `a` in \"the prices are wrong\" must not approve the page")
  assert.match(triage, /r: \(\) => noteBox\.current\?\.focus\(\)/,
    "SH1001: r focuses the box rather than rejecting — the one destructive key on this list must "
      + "not be a keystroke away from acting")
})

test("SH1001: shift-click takes a range, because a run is often one sweep", () => {
  const triage = read("src", "components", "review-triage.tsx")

  assert.match(triage, /toggle\(index, \(event as React\.MouseEvent\)\.shiftKey\)/)
  assert.match(triage, /const from = range && anchor\.current !== null \? anchor\.current : index/,
    "SH1001: from the last box touched — eleven pages of the same change should not be eleven clicks")
})

test("SH1001: the page sends one call and re-reads the run", () => {
  const page = read("src", "app", "admin", "review", "review.list.page.tsx")
  const service = read("src", "services", "review", "review.service.ts")

  assert.match(service, /\/decisions`,\s*\n\s*decisions,/,
    "SH1001: one call for the whole triage — sent one at a time, a refusal halfway leaves the run "
      + "in a state nobody chose")
  assert.match(service, /import axios from "@\/lib\/axios"/,
    "SH1001: the shared singleton, or CSRF and the base URL arrive by import order")
  /*
   * SH1158 moved the run's detail onto the query layer, so the re-read this asserts is now an
   * invalidation of that run's key rather than a second request written out by hand. The claim is the
   * same one and it is checked in both places a decision is taken — the per-change triage and the two
   * whole-run verbs — because a page that showed the old answer after an action is the one number a
   * curator learns to distrust (SH207).
   */
  // SH1197 added the third: a decision over several ticked runs re-reads each of them.
  assert.equal((page.match(/await invalidate\(`summary:reviewRun\|\$\{session\}`\)/gu) ?? []).length, 3,
    "SH207: a decision has to say the run it changed is stale, and both the triage and the whole-run "
      + "verbs take decisions")
  // SH968 removed the header count this used to refresh: the dock reports what a run did, so an
  // approval here has no second number to keep in step, and a refresh would be a fetch nobody reads.
  assert.doesNotMatch(page, /refreshPending\(\)/,
    "SH968: the review page refreshes a pending count again, and nothing displays one")
})

test("SH1001: both locales name the three decisions and the keys", () => {
  for (const locale of ["en", "pt"]) {
    const review = JSON.parse(read("src", "i18n", "locales", locale, "review.json"))
    for (const key of ["review.notePlaceholder", "review.approveCount", "review.holdCount",
      "review.rejectCount", "review.rejectNeedsNote", "review.keys", "review.decided"]) {
      assert.ok(review[key], `SH1001: ${locale} is missing ${key}`)
    }
  }
})
