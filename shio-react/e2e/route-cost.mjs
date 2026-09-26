/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SH1113 — what one console route costs, held route by route.
 *
 * SH1043's gate measures the bytes every route pays before the first row. That is right for a
 * budget and blind to a page: a route that then fetches three collections to draw two figures costs
 * the same eager bytes as one that fetches nothing. `console-check.mjs` already opens every route in
 * a browser against a live instance, which is the one place a route's own requests are observable,
 * so it records three numbers per route and this judges them against `route-budget.json`:
 *
 * - `requests` — calls to the instance's `/api/` the route made before it went quiet,
 * - `bytes` — the bodies of those responses,
 * - `ratio` — the route's time to its shell over the baseline route's, in the same run. A
 *   millisecond ceiling would move with the machine; a ratio moves only when this route got slower
 *   relative to its neighbours.
 *
 * Per route, because a total would let a new screen's cost hide inside the sum.
 * Pure: the numbers in, the findings out, so the rule is testable without a browser.
 */

/** The ceilings, and a sentence each, for the routes a run measured. */
export const BUDGET_FILE = "route-budget.json"

/**
 * One finding per ceiling a route crossed, keyed by route name.
 *
 * @param {Record<string, {requests: number, bytes: number, ms: number}>} measured
 * @param {{baseline: string, routes: Record<string, {requests: number, bytes: number, ratio: number}>}} budget
 * @returns {{findings: Record<string, string[]>, unbudgeted: string[], ratios: Record<string, number>}}
 */
export function judgeRouteCost(measured, budget) {
  const findings = {}
  const unbudgeted = []
  const ratios = {}
  const baseline = measured[budget.baseline]?.ms
  for (const [name, cost] of Object.entries(measured)) {
    const ratio = baseline ? cost.ms / baseline : null
    if (ratio !== null) ratios[name] = Number(ratio.toFixed(2))
    const ceiling = budget.routes?.[name]
    if (!ceiling) {
      unbudgeted.push(name)
      continue
    }
    const crossed = []
    if (cost.requests > ceiling.requests) {
      crossed.push(`SH1113: ${cost.requests} API requests, ceiling ${ceiling.requests}`)
    }
    if (cost.bytes > ceiling.bytes) {
      crossed.push(`SH1113: ${cost.bytes} API response bytes, ceiling ${ceiling.bytes}`)
    }
    if (ratio !== null && ceiling.ratio && ratio > ceiling.ratio) {
      crossed.push(`SH1113: ${ratio.toFixed(2)}x the baseline route's time, ceiling ${ceiling.ratio}x`)
    }
    if (crossed.length) findings[name] = crossed
  }
  return { findings, unbudgeted: unbudgeted.sort(), ratios }
}

/**
 * Ceilings seeded from one measured run: room for noise, none for a new fetch.
 *
 * Requests get **no** headroom, which is the point of the instrument: one more call is the
 * regression it exists to catch, and a ceiling of measured + 1 would let exactly that through.
 * Bytes get 25%, because a response grows with the content a fixture happens to hold. The time
 * ratio gets 2x and a floor of 3, because it is the noisiest of the three and the least specific.
 */
export function seedBudget(measured, baseline, why) {
  const baselineMs = measured[baseline]?.ms
  const routes = {}
  for (const [name, cost] of Object.entries(measured).sort(([a], [b]) => (a < b ? -1 : 1))) {
    const ratio = baselineMs ? cost.ms / baselineMs : 1
    routes[name] = {
      requests: cost.requests,
      bytes: Math.ceil(cost.bytes * 1.25),
      ratio: Number(Math.max(3, ratio * 2).toFixed(1)),
    }
  }
  return { why, baseline, routes }
}
