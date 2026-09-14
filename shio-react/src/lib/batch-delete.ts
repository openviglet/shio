/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH953 — a count is the number acted on, not the number selected.
 *
 * The batch delete looped the selection, awaited each `DELETE` with `.catch(() => {})`, and then
 * announced `itemsDeleted` with `selectedItems.size`. The select-all cap is a thousand, so a
 * thousand sequential round trips that all failed still reported **a thousand deleted** — and the
 * folder write service has real refusals, restore conflicts and 409s, none of which could reach the
 * curator through that loop.
 *
 * It lives here rather than in the page because an outcome is the one part worth asserting: the
 * page is a dialog, a toast and a refresh, and a test that renders all three to find out whether a
 * number is right is a test nobody keeps. `deleteEach` takes the call as an argument, so a refusal
 * is arranged in one line.
 *
 * This does not wait for Block BD's bulk endpoint. When that lands the loop becomes one call and
 * this reporting is what stays.
 */
// Relative and extension-bearing, not the `@/` alias every page uses: this module is imported by
// a `node --test` suite that resolves no Vite alias, and the alias is what took that suite from
// green to ERR_MODULE_NOT_FOUND. Vite resolves this form too, so one spelling serves both.
import { problemOf as readProblem } from "./problem.ts"

/** What a row is, as far as deleting it goes. */
export interface DeleteTarget {
  id: string
  name?: string
  objectType: string
}

/** One refusal, in the words the server used (P6). */
export interface Refusal {
  id: string
  name: string
  title: string
  detail?: string
}

export interface BatchOutcome {
  /** How many actually went. The number a toast may say out loud. */
  deleted: number
  /** How many were attempted — the selection's size, kept so the two can be compared. */
  attempted: number
  refusals: Refusal[]
}

/**
 * The server's problem document, or the nearest honest thing (RFC 9457).
 *
 * SH955 moved the reading itself into `problem.ts`, which is the whole point of that task: two
 * readers of one document is two answers to "what did the server say". This keeps the shape a
 * refusal has here — a `title` that is never empty, because a refusal with no words at all still
 * has to be nameable in a list of them.
 */
export function problemOf(error: unknown): { title: string; detail?: string } {
  const problem = readProblem(error)
  const title = problem.title || "Request failed"
  return problem.detail ? { title, detail: problem.detail } : { title }
}

/**
 * Delete each target, and report what happened to each.
 *
 * Sequential on purpose, and unchanged from the loop this replaces: a thousand parallel deletes is
 * a thousand simultaneous writes against one folder tree, and the endpoint is per-object until
 * Block BD. What changes is that a rejection is *recorded* rather than dropped on the floor.
 */
export async function deleteEach(
  targets: DeleteTarget[],
  deleteOne: (target: DeleteTarget) => Promise<unknown>,
): Promise<BatchOutcome> {
  const refusals: Refusal[] = []
  let deleted = 0

  for (const target of targets) {
    try {
      await deleteOne(target)
      deleted += 1
    } catch (error) {
      const { title, detail } = problemOf(error)
      refusals.push({ id: target.id, name: target.name || target.id, title, detail })
    }
  }

  return { deleted, attempted: targets.length, refusals }
}

/**
 * One line naming what was refused and why — the curator's whole account of a partial batch.
 *
 * Capped, because a thousand refusals is a toast nobody can read and the cap is what keeps the
 * first few legible. The count is always stated, so a truncated list never reads as the whole of
 * what went wrong.
 */
export function refusalSummary(refusals: Refusal[], limit = 3): string {
  const named = refusals
    .slice(0, limit)
    .map((refusal) => `${refusal.name}: ${refusal.detail || refusal.title}`)
    .join("; ")
  const rest = refusals.length - Math.min(limit, refusals.length)
  return rest > 0 ? `${named}; +${rest} more` : named
}
