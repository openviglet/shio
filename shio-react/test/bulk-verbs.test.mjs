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
 * SH1021 — the curator's bulk verbs, and the one outcome that must not be smoothed over.
 *
 * The design names the assertion: **a selection where one item is refused publishes the rest and
 * names the refusal.** That is the whole reason the answer is a change set rather than a 200 — a
 * bulk verb that reports success or failure for the batch is one that either hides the refusal or
 * throws away the twelve that worked.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const read = (...path) => readFileSync(join(ROOT, ...path), "utf8")
const PAGE = read("src", "app", "content", "object.children.page.tsx")
const SERVICE = read("src", "services", "post-unified", "post-unified.service.ts")

test("SH1021: one refusal does not swallow the rest, and is named rather than counted", () => {
  // The handler, read as source: what it does with a partly-refused result is the claim.
  const apply = /const applyBulk = async \(\) => \{[\s\S]*?\n  \}\n/u.exec(PAGE)?.[0] ?? ""
  assert.ok(apply.length > 0, "SH1021: the apply handler moved and this scan lost its subject")

  assert.match(apply, /result\.results\.filter\(\(change\) => change\.problem\)/u,
    "SH1021: the refusals are picked out of the per-item results — a bulk verb that reported one "
      + "outcome for the batch would either hide them or throw away what worked")
  assert.match(apply, /t\("content\.bulkDone", \{ count: result\.changed \}\)/u,
    "SH1021: what succeeded is still reported when something did not")
  assert.match(apply, /names: refused\.map\(\(change\) => change\.address \?\? change\.id\)\.join\(", "\)/u,
    "SH1021: named, not counted — \"3 refused\" sends a curator to find out which three")
})

test("SH1021: every bulk run is planned first, and the plan is the server's", () => {
  assert.match(PAGE, /const result = await unifiedService\.bulk\(op, \[\.\.\.selectedItems\], \{ dryRun: true \}\)/u,
    "SH1021: a bulk verb is the one shape where a mistake is multiplied by the size of the "
      + "selection, so the menu opens a plan rather than acting")
  assert.match(PAGE, /confirm: bulkPlan\.result\.confirm/u,
    "SH1021: the token the plan returned, so a destructive run cannot be carried out against a "
      + "plan the curator never saw (SH108)")
  assert.match(PAGE, /dryRun: false/u)
})

test("SH1021: the sheet shows each item's outcome and each refusal's fix", () => {
  assert.match(PAGE, /\(bulkPlan\?\.result\.results \?\? \[\]\)\.map\(\(change\) =>/u,
    "SH1021: per item, because that is the shape the server answers with")
  assert.match(PAGE, /change\.problem\.title/u)
  assert.match(PAGE, /change\.problem\.fix/u,
    "SH1021: the fix its problem document came with — a refusal without one sends the curator to "
      + "guess, which is what P6 exists to prevent")
})

test("SH1021: the console calls the one bulk endpoint, not a loop of its own", () => {
  assert.match(SERVICE, /axios\.post<ShBulkResult>\("\/v2\/post-unified\/bulk"/u,
    "SH1021: one call to one writer, so the console and the agent cannot disagree about what "
      + "publishing means")
  // A loop would show up as the single-id publish being called from the page.
  const apply = /const applyBulk = async \(\) => \{[\s\S]*?\n  \}\n/u.exec(PAGE)?.[0] ?? ""
  assert.doesNotMatch(apply, /unifiedService\.publish\(/u,
    "SH1021: publishing one at a time is what this replaced — it is not atomic, and it is not "
      + "what an agent does")
})

test("SH1021: the menu offers the verbs, and every string exists in both locales", () => {
  assert.match(PAGE, /planBulk\("PUBLISH"\)/u)
  assert.match(PAGE, /planBulk\("UNPUBLISH"\)/u)

  const keys = ["content.publish", "content.unpublish", "content.bulkPlanTitle",
    "content.bulkApply", "content.bulkDone", "content.bulkRefused", "content.bulkRefusedBadge",
    "content.bulkOp.PUBLISH", "content.bulkOp.UNPUBLISH", "content.bulkOp.DELETE"]
  for (const locale of ["en", "pt"]) {
    const strings = JSON.parse(read("src", "i18n", "locales", locale, "content.json"))
    for (const key of keys) {
      assert.ok(strings[key], `SH1021: ${locale} is missing ${key}`)
    }
  }
})
