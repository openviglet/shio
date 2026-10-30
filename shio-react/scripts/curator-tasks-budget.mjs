/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SH1315 — what a curator's task costs in the console, and the rule a change is judged by.
 *
 * ## Why this exists
 *
 * The console was redrawn three times, and each round closed on a structural criterion: the
 * surface composes the bento seams, its panels are frosted, the digest moved. None of them asked
 * whether a person finishes a job with less effort. The project measures tokens (P3), a route's
 * reads (SH1113), contrast and accessibility, and until this file it measured nothing about the
 * curator. `e2e/curator-tasks.mjs` drives six jobs through the UI from `/bento`; this module holds
 * what a job is allowed to cost and decides whether a run kept to it.
 *
 * ## The four numbers, and which of them gate
 *
 * - **`clicks`** — trusted pointer clicks. Discrete and deterministic for a scripted path.
 * - **`keys`** — trusted keystrokes outside a text field: a shortcut, an arrow through a list, an
 *   Enter that confirms. Typing a title is the content of the task, not its cost, so it is not one.
 * - **`screens`** — route changes in the console's own window. A dialog is not a screen; a tab the
 *   task opens is counted by the task that needs it.
 * - **`ms`** — wall-clock, recorded and **never gated**. It moves with the machine and the instance,
 *   and a ceiling nobody can reproduce is one that gets raised until it means nothing (the argument
 *   `route-cost.mjs` already made for the first row). It is printed so a reader sees it move.
 *
 * The first three are held at the measurement with no headroom, the way SH1113 holds `requests`:
 * a scripted path either takes the extra click or it does not, and a ceiling with room in it would
 * let the first extra one through.
 *
 * ## The rule, both ways
 *
 * A run **regresses** a task when any gated number is above its budget, and that fails. A run
 * **improves** one when a gated number is below it, and that also fails until the budget is
 * re-recorded: an improvement nobody writes down is a ceiling that quietly stays high, and the next
 * regression spends it. Re-recording is `--record`, which keeps every entry's `why`.
 */

export const GATED = ["clicks", "keys", "screens"]

/**
 * Compare one run against the budget. Returns the verdict per task and the lists a caller prints.
 *
 * @param {Record<string, {clicks:number, keys:number, screens:number, ms:number}>} measured
 * @param {{tasks: Record<string, {clicks:number, keys:number, screens:number, why?:string}>}} budget
 */
export function compare(measured, budget) {
  const regressions = []
  const improvements = []
  const unbudgeted = []
  const unmeasured = []
  for (const [task, run] of Object.entries(measured)) {
    const ceiling = budget.tasks[task]
    if (!ceiling) {
      unbudgeted.push(task)
      continue
    }
    // A task a filed defect stops is held as `blocked`: still blocked is the expected answer, a
    // blocked task that now completes is an improvement, and one that newly stops is a regression.
    if (ceiling.blocked || run.blocked) {
      if (ceiling.blocked && !run.blocked) improvements.push(`${task}: completes, no longer blocked by ${ceiling.blocked}`)
      if (!ceiling.blocked && run.blocked) regressions.push(`${task}: blocked (${run.blocked}), was completing`)
      continue
    }
    for (const field of GATED) {
      if (run[field] > ceiling[field]) regressions.push(`${task}: ${field} ${run[field]} > ${ceiling[field]}`)
      else if (run[field] < ceiling[field]) improvements.push(`${task}: ${field} ${run[field]} < ${ceiling[field]}`)
    }
  }
  for (const task of Object.keys(budget.tasks)) if (!(task in measured)) unmeasured.push(task)
  return { regressions, improvements, unbudgeted, unmeasured,
    ok: !regressions.length && !improvements.length && !unbudgeted.length && !unmeasured.length }
}

/** The budget a `--record` run writes: the measurement, with each entry's `why` kept. */
export function record(measured, previous, { filtered = false } = {}) {
  // SH1347 — a filtered run measured some tasks, not the set: it keeps every task it did not run.
  // A full run starts empty, so a task deleted from the runner leaves the budget with it.
  const tasks = filtered ? { ...(previous?.tasks ?? {}) } : {}
  for (const [task, run] of Object.entries(measured)) {
    const why = previous?.tasks?.[task]?.why ?? ""
    tasks[task] = run.blocked
      ? { blocked: run.blocked, why }
      : { clicks: run.clicks, keys: run.keys, screens: run.screens, ms: run.ms, why }
  }
  return { ...(previous ?? {}), tasks }
}
