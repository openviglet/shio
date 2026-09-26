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
import { judgeRouteCost, seedBudget } from "../e2e/route-cost.mjs"

/**
 * SH1113 — a route's own cost is held route by route, so a new fetch cannot hide in a total.
 */
const E2E = join(dirname(fileURLToPath(import.meta.url)), "..", "e2e")

const MEASURED = {
  home: { requests: 4, bytes: 9_000, ms: 400 },
  "content-browser": { requests: 6, bytes: 22_000, ms: 700 },
  "post-editor": { requests: 5, bytes: 14_000, ms: 600 },
}

test("SH1113: a planted extra fetch on one route fails that route's entry and no other", () => {
  const budget = seedBudget(MEASURED, "home", "seeded in the test")
  assert.deepEqual(judgeRouteCost(MEASURED, budget).findings, {}, "the run it was seeded from passes")

  const planted = structuredClone(MEASURED)
  planted["content-browser"].requests += 1
  planted["content-browser"].bytes += 300
  const { findings } = judgeRouteCost(planted, budget)

  assert.deepEqual(Object.keys(findings), ["content-browser"],
    "SH1113: one more request on one route is that route's finding, and no other route's")
  assert.match(findings["content-browser"][0], /7 API requests, ceiling 6/)
})

test("SH1113: time is judged as a ratio to the baseline route, so a slower machine moves nothing", () => {
  const budget = seedBudget(MEASURED, "home", "seeded in the test")
  const slowerMachine = Object.fromEntries(Object.entries(MEASURED)
    .map(([name, cost]) => [name, { ...cost, ms: cost.ms * 3 }]))
  assert.deepEqual(judgeRouteCost(slowerMachine, budget).findings, {},
    "every route three times slower is the same ratios, and no finding")

  const oneSlowRoute = structuredClone(MEASURED)
  oneSlowRoute["post-editor"].ms = 400 * 10
  assert.deepEqual(Object.keys(judgeRouteCost(oneSlowRoute, budget).findings), ["post-editor"])
})

test("SH1113: a route with no ceiling is named, not waved through as a pass", () => {
  const budget = seedBudget({ home: MEASURED.home }, "home", "seeded in the test")
  assert.deepEqual(judgeRouteCost(MEASURED, budget).unbudgeted, ["content-browser", "post-editor"])
})

test("SH1113: requests are seeded with no headroom, because one more is the regression", () => {
  const budget = seedBudget(MEASURED, "home", "seeded in the test")
  assert.equal(budget.routes["content-browser"].requests, 6)
  assert.ok(budget.routes["content-browser"].bytes > 22_000, "bytes carry room for content that grows")
})

test("SH1113: the committed budget names its baseline and gives a reason", () => {
  const budget = JSON.parse(readFileSync(join(E2E, "route-budget.json"), "utf8"))
  assert.ok(budget.why && budget.why.length > 40, "a ceiling nobody can argue with is a number, not a budget")
  assert.ok(budget.routes[budget.baseline], "the baseline route is itself budgeted")
  assert.ok(Object.keys(budget.routes).length >= 10, "seeded from a real walk of the console's routes")
})
