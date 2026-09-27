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

import {
  BYTES_HEADROOM, belowFloor, compare, familyOf, FLOOR_ROUTE, foldFamilies, headroom, seedCeilings,
  staleEntries,
} from "../scripts/route-cost.mjs"
import { stripComments } from "./support/source-text.mjs"

/**
 * SH1113 — a budget per console route, and the one assertion the design names.
 *
 * <h2>Where the halves live, and why this one is here</h2>
 *
 * The measurement needs a browser and a live instance, so it runs with conformance
 * (`console:check`). What a `pnpm test` can hold is the half that decides — given measurements and
 * a budget, which routes are over — and that is where the claim worth guarding lives anyway. The
 * design states it in one sentence: **a planted extra fetch on one route fails that route's entry
 * and no other.** Both halves of that sentence are load-bearing. A comparator that failed
 * everything would satisfy "fails that route" and be useless, and one that failed nothing would
 * satisfy nothing at all.
 *
 * <h2>The fixture is a real recording, trimmed</h2>
 *
 * Six families off the first run against a live instance rather than round numbers, because the
 * shape of real data is what refuted the first implementation: counting every same-origin request
 * put the 1.73 MB eager bundle into every route's total and buried a post editor's own reads under
 * it. Numbers invented for a test would have agreed with that implementation forever.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const BUDGET = JSON.parse(readFileSync(join(ROOT, "console-route-budget.json"), "utf8"))

/** Six families as the probe records them: `{ requests, bytes, ms }` per route name. */
const MEASURED = {
  sites: { requests: 2, bytes: 1900, ms: 620 },
  "site-children": { requests: 5, bytes: 7400, ms: 700 },
  trash: { requests: 5, bytes: 4100, ms: 640 },
  "post-edit:Page": { requests: 8, bytes: 9600, ms: 810 },
  "post-edit:Article": { requests: 8, bytes: 9100, ms: 790 },
  "post-type-list": { requests: 3, bytes: 5200, ms: 650 },
}

/** A budget that fits MEASURED exactly, seeded the way `--record-cost` seeds one. */
function budgetFor(measured = MEASURED, baseline = "sites") {
  const families = foldFamilies(measured)
  const baselineMs = families[baseline].ms
  const routes = {}
  for (const [family, row] of Object.entries(families)) {
    routes[family] = { ...seedCeilings(row, baselineMs), why: `the ${family} route` }
  }
  return { baseline, routes }
}

const clone = (o) => JSON.parse(JSON.stringify(o))

test("SH1172: a ceiling below the shell's own floor is refused, and the committed budget has none", () => {
  // The first seed held eleven byte ceilings at 1–2 KB against a 404 that reads 3 KB: taken from a
  // run the pool was failing, and unmeetable by any page, since every page pays the shell first.
  const planted = {
    routes: {
      [FLOOR_ROUTE]: { requests: 7, bytes: 4000, firstRow: 2, why: "the floor" },
      "bento-review": { requests: 9, bytes: 1000, firstRow: 2, why: "seeded from a failing run" },
      sites: { requests: 8, bytes: 5000, firstRow: 3, why: "fine" },
    },
  }
  const findings = belowFloor(planted)
  assert.equal(findings.length, 1, `SH1172: expected one finding, got:\n${findings.join("\n")}`)
  assert.match(findings[0], /^bento-review: the bytes ceiling 1000 is below the shell's own floor/u)
  assert.deepEqual(belowFloor(BUDGET), [],
    "SH1172: the committed budget holds a ceiling no page can meet — re-seed it on a healthy instance")
})

test("SH1113: a planted fetch fails that route's entry and no other", () => {
  const budget = budgetFor()
  assert.deepEqual(compare(budget, MEASURED), [],
    "SH1113: the recording must agree with the budget it seeded, or the planted fetch below is "
      + "proved by a comparator that was already failing")

  const withExtra = clone(MEASURED)
  withExtra["site-children"].requests += 1

  const findings = compare(budget, withExtra)
  assert.equal(findings.length, 1,
    `SH1113: one extra fetch on one route must produce exactly one finding, got ${findings.length}`
      + `:\n${findings.join("\n")}`)
  assert.match(findings[0], /^site-children: 6 requests/u,
    "SH1113: and it names the route that gained the fetch. A budget that reported a total would "
      + "have moved 1/23rd on this and said nothing — which is the whole reason the entry is per "
      + "route (§LXXII.19)")
})

test("SH1113: the requests ceiling has no room in it, by construction", () => {
  // The design's number is discrete and deterministic, so the ceiling is the measurement. A
  // seeding rule that added even one would let the first extra fetch through — and the first one
  // is the only one anybody would notice.
  for (const [family, row] of Object.entries(foldFamilies(MEASURED))) {
    assert.equal(seedCeilings(row, 620).requests, row.requests,
      `SH1113: ${family}'s request ceiling was seeded above its measurement`)
  }
  // …while bytes and time are seeded with room, for the opposite reason: both move with the
  // instance and the machine, and a ceiling at the measurement refuses the next change rather
  // than measuring it (SH736).
  const seeded = seedCeilings({ requests: 8, bytes: 9600, ms: 810 }, 620)
  assert.ok(seeded.bytes > 9600 * (1 + BYTES_HEADROOM), "SH1113: the byte ceiling carries headroom")
  assert.ok(seeded.firstRow >= 2, "SH1113: the time ceiling is coarse on purpose — one run cannot "
    + "establish a tight one, and a tight one seeded from a single run is a flaky gate")
})

test("SH1113: a family is compared at its costliest member, not its cheapest", () => {
  // Two post editors, one heavier. A fold that took the first, or the cheapest, would hold a
  // ceiling the expensive page walks straight past — the same "a total hides a page" mistake the
  // whole task is about, one level down.
  const heavier = clone(MEASURED)
  heavier["post-edit:Article"].requests = 12

  const folded = foldFamilies(heavier)
  assert.equal(folded["post-edit"].requests, 12)
  assert.equal(folded["post-edit"].name, "post-edit:Article",
    "SH1113: the finding has to name the member that cost this, not the family in the abstract")
  assert.equal(familyOf("post-edit:Page"), "post-edit")
  assert.equal(familyOf("trash"), "trash")

  const findings = compare(budgetFor(), heavier)
  assert.equal(findings.length, 1)
  assert.match(findings[0], /post-edit: 12 requests on post-edit:Article/u)
})

test("SH1113: a new screen with no ceiling is a finding, and a fixture gap is only a note", () => {
  // The asymmetry is what makes this runnable on a thin instance without being toothless on a full
  // one. A route list built from the instance means "no post editor" is a missing fixture; a route
  // that exists and is uncosted is a screen that slipped in without declaring what it costs.
  const budget = budgetFor()
  const withNewScreen = { ...clone(MEASURED), "bento-insights": { requests: 9, bytes: 8000, ms: 700 } }
  const findings = compare(budget, withNewScreen)
  assert.equal(findings.length, 1)
  assert.match(findings[0], /^bento-insights: this route has no entry in the budget/u)
  assert.match(findings[0], /--record-cost/u,
    "SH1113: P6 — the finding says what to run, not just that something is wrong")

  const thin = clone(MEASURED)
  delete thin["post-edit:Page"]
  delete thin["post-edit:Article"]
  assert.deepEqual(compare(budget, thin), [],
    "SH1113: an instance with no posts must not fail the gate for having no post editor")
  const notes = staleEntries(budget, thin)
  assert.equal(notes.length, 1)
  assert.match(notes[0], /^post-edit: the budget holds a ceiling this run never walked/u,
    "SH1113: reported all the same — a ceiling that outlives its route is a number nobody is "
      + "measuring that reads as coverage (SH1044 deleted four of those from the bundle budget)")
})

test("SH1113: the reader refuses the shapes that would make a pass meaningless", () => {
  const budget = budgetFor()
  assert.throws(() => compare(budget, {}), /no route was measured/u,
    "SH1113: nothing to compare must throw, not return no findings — the report-zero shape")
  assert.throws(() => compare(budget, { trash: MEASURED.trash }), /baseline route 'sites'/u,
    "SH1113: without the baseline every firstRow ratio is against nothing")
  assert.throws(
    () => compare(budget, { ...MEASURED, sites: { requests: 2, bytes: 1900, ms: 0 } }),
    /baseline route 'sites'/u,
    "SH1113: a baseline that measured 0ms makes every ratio infinite")
})

test("SH1113: an entry without a sentence is refused, and a tight one is warned about", () => {
  const budget = budgetFor()
  budget.routes.trash.why = "   "
  const findings = compare(budget, MEASURED)
  assert.equal(findings.length, 1)
  assert.match(findings[0], /^trash: the entry carries no `why`/u,
    "SH1113: the bundle budget's rule — a ceiling without a sentence is a number the next person "
      + "raises without knowing what it protected")

  // Headroom is a note, never a failure: a ceiling grown into is not a regression, it is a warning
  // that the NEXT change will be refused instead of measured.
  const tight = budgetFor()
  tight.routes.trash.bytes = MEASURED.trash.bytes + 1
  assert.deepEqual(compare(tight, MEASURED), [])
  assert.match(headroom(tight, MEASURED)[0], /^trash: .* of byte room left/u)
})

test("SH1113: the committed budget is real, and every entry in it argues for itself", () => {
  assert.ok(BUDGET.baseline, "SH1113: the budget names no baseline route")
  const families = Object.entries(BUDGET.routes ?? {})
  assert.ok(families.length >= 20,
    `SH1113: the committed budget holds ${families.length} families. It was recorded against a `
      + "live instance walking the whole console; a handful means the file was hand-written or a "
      + "recording run half-failed, and a budget covering six routes reads as coverage of all of "
      + "them")
  assert.ok(BUDGET.routes[BUDGET.baseline],
    `SH1113: the baseline route '${BUDGET.baseline}' has no entry of its own`)
  assert.ok(BUDGET.measured, "SH1113: the budget does not say when it was measured")

  for (const [family, entry] of families) {
    assert.ok(entry.why?.trim(), `SH1113: ${family} carries no \`why\` — the gate refuses it, so `
      + "this would be a committed budget that fails on the next run")
    assert.ok(Number.isInteger(entry.requests) && entry.requests >= 0,
      `SH1113: ${family}'s request ceiling is not a count`)
    assert.ok(entry.bytes > 0, `SH1113: ${family}'s byte ceiling is not positive`)
    assert.ok(entry.firstRow == null || entry.firstRow <= 10,
      `SH1113: ${family} holds a firstRow ceiling of ${entry.firstRow}x the baseline. A ceiling `
        + "that high governs nothing, and it does not come from a slow page — a recording run "
        + "seeded 27.1x and 25.2x for two routes that had rendered at ~15s against a 15s deadline, "
        + "which is a page that barely rendered rather than one that is merely slow. "
        + "`FIRST_ROW_TRUST` in the probe records those as unobserved instead, so a number this "
        + "size means that guard went away");
  }

  // The guard against the eager bundle getting back into the measurement, and it is aimed at one
  // route rather than applied as a blanket ceiling. SH1172 — the reason a blanket one was refused,
  // "`graphql` legitimately reads 1.13 MB of schema introspection", was itself the bundle: the old
  // filter counted the explorer's document and four of its chunks as reads. A 404 route is the case
  // that cannot have data of its own, so if it is ever megabyte-sized the filter is gone.
  const floor = BUDGET.routes["unknown-route"]
  assert.ok(floor, "SH1113: the 404 route is this file's own guard and it has no entry")
  assert.ok(floor.bytes < 100_000,
    `SH1113: the 404 route's byte ceiling is ${floor.bytes}. A route that renders "not found" has `
      + "no data of its own, so anything near the eager bundle's size means DATA_READ stopped "
      + "filtering and every entry here is measuring the bundle again — which buries the signal and "
      + "duplicates the gate console-size-budget.json already is (P10)")
  assert.ok(floor.requests <= 10,
    `SH1113: the 404 route makes ${floor.requests} calls. That is the shell's floor every other `
      + "entry is measured on top of; if it has grown, the console gained a read it makes on every "
      + "route including the ones that do not exist")
})

test("SH1113: the probe measures the data plane, and says the bundle is not its subject", () => {
  const probe = stripComments(readFileSync(join(ROOT, "e2e", "console-check.mjs"), "utf8"))
  assert.match(probe, /function isDataRead\(url\)/u,
    "SH1113: the filter that keeps the eager bundle out of a per-route cost")
  // SH1172 — by path, exactly: a substring match counted `/bento/graphql` and `/assets/graphql-*.js`.
  assert.match(probe, /path\.startsWith\("\/api\/v2\/"\) \|\| path === "\/graphql"/u,
    "SH1172: a read is decided by its pathname; a substring of the URL counted the explorer's"
      + " document and bundle chunks as reads")
  assert.match(probe, /mine = \(url\) => \(url\.startsWith\(BASE\) \|\| url\.startsWith\(API\)\) && isDataRead\(url\)/u,
    "SH1113: both halves — this instance's own origins, and only its data reads. Dropping the "
      + "second put 1.73 MB of bundle into every route and made a post editor's extra reads a 1% "
      + "difference")
  assert.match(probe, /\.filter\(\(p\) => p\.path && p\.cost\?\.ms != null\)/u,
    "SH1113: costed when the page put a first row on screen, not when its probe is `ok`. The dead "
      + "page still has to be excluded — it made whatever requests it got to before it died, and "
      + "costing it teaches the budget that a broken page is a cheap one — but `p.ok` excluded far "
      + "more than that: which chrome assertions fire moved across three runs on one instance, so "
      + "the costed set moved with them and families came and went from the comparison")
  assert.match(probe, /const RECORD_COST = has\("record-cost"\)/u,
    "SH1113: seeding is an explicit flag. A gate that seeds itself when it finds no budget to "
      + "compare against is a gate that passes on the day somebody deletes its budget")
})
