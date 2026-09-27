/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH1054 — what an axe run against a console route is allowed to find.
 *
 * <p>The four source rules scan for four shapes and are blind outside them; axe reads the page a
 * reader actually gets, composed across components. So `console-check.mjs` runs it on every route
 * and this decides what a result means, the way `route-cost.mjs` decides what a cost means — pure,
 * so `pnpm test` can hold the rule without a browser.</p>
 *
 * <ul>
 *   <li><b>Serious and critical fail, always.</b> They are not debt: an unnamed button is a control
 *       a screen reader cannot use, and a list of them is a list of things not to ship.</li>
 *   <li><b>Everything else is held in `a11y-debt.json`, per route family, by rule id.</b> A rule the
 *       file does not list for that family fails, so a new violation cannot arrive silently.</li>
 *   <li><b>The map shrinks only by deletion.</b> An entry no route produced any more is a note that
 *       names it, so the file cannot carry a debt that was paid.</li>
 * </ul>
 */
export const GATED_IMPACTS = new Set(["serious", "critical"])

/**
 * @param family     the route family (`post-edit`, not `post-edit:Page`), as the cost budget keys it
 * @param violations axe's violations, reduced to `{ id, impact, nodes, target }`
 * @param debt       the committed `a11y-debt.json`
 * @return findings (each a sentence naming the route, the rule and the element) and the rule ids
 *         this run observed for the family, for {@link staleDebt}
 */
export function a11yFindings(route, family, violations, debt) {
  const held = new Set(debt?.routes?.[family] ?? [])
  const findings = []
  const observed = []
  for (const v of violations ?? []) {
    observed.push(v.id)
    const where = `${v.nodes} element${v.nodes === 1 ? "" : "s"}, first ${v.target ?? "?"}`
    if (GATED_IMPACTS.has(v.impact)) {
      findings.push(`${route}: ${v.impact} ${v.id} (${where}) — serious and critical are never debt`)
    } else if (!held.has(v.id)) {
      findings.push(`${route}: ${v.impact} ${v.id} (${where}) — new, and not in a11y-debt.json for`
        + ` '${family}'`)
    }
  }
  return { findings, observed }
}

/** Entries the committed map holds that no route produced in this run: delete them. */
export function staleDebt(debt, observedByFamily) {
  const notes = []
  for (const [family, rules] of Object.entries(debt?.routes ?? {})) {
    const seen = observedByFamily.get(family)
    if (!seen) continue
    for (const rule of rules) {
      if (!seen.has(rule)) notes.push(`${family}: ${rule} is no longer found — delete it from a11y-debt.json`)
    }
  }
  return notes
}
