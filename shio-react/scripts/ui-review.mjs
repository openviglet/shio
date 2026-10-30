/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SH1321 — which console routes a design review looks at next, decided rather than chosen.
 *
 * ## Why the choice is code
 *
 * `console-check.mjs` screenshots every route on every run, and judging a screenshot costs an
 * agent a read of an image: looking at all of them every iteration is the budget P3 forbids, and
 * looking at whichever ones come to mind is how the home page gets reviewed five times and the
 * webhook editor never. So the `shio-console-review` skill asks this module, and two sessions on
 * two machines rotate through the same order (P5).
 *
 * ## The order
 *
 * 1. a family the check reported failing — a broken page is the first thing worth a look;
 * 2. a family never reviewed;
 * 3. the family reviewed longest ago;
 * 4. the family name, so a tie never depends on the report's order.
 *
 * A family is `familyOf` from `route-cost.mjs`, the same grouping the cost and a11y budgets use:
 * `post-edit:Page` and `post-edit:Article` are one form shape, and reviewing it once per type
 * would spend the budget on the same layout.
 */
import { familyOf } from "./route-cost.mjs"

/**
 * The reviewable families in a `console-check.json` report: every route probe that left a
 * screenshot, grouped by family. Probes with no screenshot (the drives, the keyboard probe) have
 * nothing to look at, and a failure among them is already the check's to report.
 */
export function reviewableFamilies(report) {
  const families = new Map()
  for (const probe of report?.probes ?? []) {
    if (!probe?.screenshot || !probe.name) continue
    const family = familyOf(probe.name)
    const entry = families.get(family) ?? { family, failing: false, routes: [] }
    const problems = [...(probe.errors ?? []), ...(probe.missing ?? []), ...(probe.broken ?? [])]
    if (probe.ok === false || problems.length) entry.failing = true
    entry.routes.push({
      name: probe.name,
      path: probe.path,
      title: probe.title ?? "",
      screenshot: probe.screenshot,
      problems: problems.map((p) => String(p).slice(0, 200)),
    })
    families.set(family, entry)
  }
  for (const entry of families.values()) entry.routes.sort((a, b) => a.name.localeCompare(b.name))
  return [...families.values()]
}

/** Why a family is picked, in the words the skill prints back to the reviewer. */
function reasonFor(entry, last) {
  if (entry.failing) return "the console check reported it failing"
  if (!last) return "never reviewed"
  return `last reviewed ${last.reviewed}`
}

/**
 * The next `count` families to review, most urgent first. Pure: the ledger and the report are
 * read, never written.
 */
export function pickNext(families, ledger, count = 3) {
  const seen = ledger?.families ?? {}
  return [...families]
    .sort((a, b) => {
      if (a.failing !== b.failing) return a.failing ? -1 : 1
      const la = seen[a.family]?.reviewed ?? ""
      const lb = seen[b.family]?.reviewed ?? ""
      if (la !== lb) return la < lb ? -1 : 1
      return a.family.localeCompare(b.family)
    })
    .slice(0, Math.max(0, count))
    .map((entry) => ({ ...entry, reason: reasonFor(entry, seen[entry.family]) }))
}

/**
 * The ledger after a review of `families`, with keys sorted so a rewrite is a minimal diff. The
 * findings are the task ids the review filed, empty when it found nothing — which is a result
 * worth recording, since it is what moves the family to the back of the rotation.
 */
export function recordReview(ledger, families, { reviewed, commit, findings = [] }) {
  if (!/^\d{4}-\d{2}-\d{2}$/u.test(reviewed ?? "")) {
    throw new Error(`SH1321: 'reviewed' must be a YYYY-MM-DD date, got '${reviewed}'`)
  }
  const bad = findings.filter((id) => !/^SH[1-9]\d*$/u.test(id))
  if (bad.length) throw new Error(`SH1321: findings must be task ids (SH<n>), got ${bad.join(", ")}`)
  const next = { ...(ledger?.families ?? {}) }
  for (const family of families) {
    next[family] = { reviewed, commit: commit ?? "", findings: [...findings].sort() }
  }
  return {
    ...ledger,
    families: Object.fromEntries(Object.keys(next).sort().map((k) => [k, next[k]])),
  }
}
