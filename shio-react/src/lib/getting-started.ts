/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/** SH1423 — the five steps of Shio's loop, in the order a newcomer goes round it. */
export const START_STEPS = ["site", "edited", "published", "agent", "reviewed"] as const

export type StartStep = (typeof START_STEPS)[number]

export type StartState = Readonly<Record<StartStep, boolean>>

/**
 * The steps with whether each is done, in order, and whether the checklist has anything left to say.
 *
 * `null` while the summary has not answered: an unknown state draws nothing, rather than five
 * unticked steps that tick themselves a moment later.
 */
export function startChecklist(state: StartState | null | undefined): {
  steps: { step: StartStep; done: boolean }[]
  complete: boolean
  done: number
} | null {
  if (!state) return null
  const steps = START_STEPS.map((step) => ({ step, done: Boolean(state[step]) }))
  const done = steps.filter((each) => each.done).length
  return { steps, complete: done === steps.length, done }
}

/**
 * Where a person's "hide this" is kept: per user, in this browser.
 *
 * The ticks are the instance's state and never this; hiding the list is a preference about this
 * screen, the way the console keeps a table's columns, so it lives with the console's other ones.
 */
export function startDismissKey(username: string | undefined): string {
  return `shio:getting-started:dismissed:${username ?? ""}`
}
