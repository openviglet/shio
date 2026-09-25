/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import type { StreamChange } from "../hooks/use-change-stream.ts"

/**
 * SH968 — agent runs, read off the change stream as they happen.
 *
 * <p>The feed has no "run started" or "run ended" row. What it has is the run's session on every
 * write the run made (`agent`, SH999), so a run starts at the first write this screen sees from a
 * session, and ends when that session has been quiet for `quietMs`. Quiet rather than a declared
 * end because an agent never says it is done. It stops writing, and a dock that waited for a
 * declaration would wait forever.</p>
 *
 * <p>A curator's own writes carry no `agent` (SH996) and are ignored: the dock already hears those
 * through the toast their page raised.</p>
 *
 * <p>Pure on purpose, with the clock and the timer passed in, so `agent-runs.test.mjs` drives a
 * whole run without waiting and without a browser.</p>
 */
export interface AgentRun {
  session: string
  /** What the run called itself on `initialize`, when it did. */
  name?: string
  /** The sites it wrote to, in the order it first touched them. */
  sites: string[]
  writes: number
  /** Writes nobody has decided yet, which are what the review queue will hold. */
  pending: number
}

export interface RunTrackerOptions {
  quietMs: number
  schedule: (run: () => void, ms: number) => unknown
  cancel: (handle: unknown) => void
  onStart: (run: AgentRun) => void
  /** Every agent write in view, so the mascot reacts to smaller events than a state change. */
  onWrite: (run: AgentRun) => void
  onEnd: (run: AgentRun) => void
}

export function createRunTracker(options: RunTrackerOptions) {
  const live = new Map<string, { run: AgentRun; timer: unknown }>()

  function end(session: string) {
    const held = live.get(session)
    if (!held) return
    live.delete(session)
    options.onEnd(held.run)
  }

  return {
    observe(changes: readonly StreamChange[] | undefined) {
      for (const change of changes ?? []) {
        if (!change.agent) continue
        let held = live.get(change.agent)
        const started = !held
        if (!held) {
          held = { run: { session: change.agent, sites: [], writes: 0, pending: 0 }, timer: undefined }
          live.set(change.agent, held)
        } else {
          options.cancel(held.timer)
        }
        const { run } = held
        if (change.agentName) run.name = change.agentName
        if (change.site && !run.sites.includes(change.site)) run.sites.push(change.site)
        run.writes += 1
        if (!change.review) run.pending += 1
        if (started) options.onStart(run)
        options.onWrite(run)
        const session = change.agent
        held.timer = options.schedule(() => end(session), options.quietMs)
      }
    },
    /** The runs still writing, for a caption that has to say whether anything is. */
    running(): AgentRun[] {
      return [...live.values()].map((held) => held.run)
    },
    dispose() {
      for (const held of live.values()) options.cancel(held.timer)
      live.clear()
    },
  }
}
