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
import { stripComments } from "./support/source-text.mjs"
import { createRunTracker } from "../src/lib/agent-runs.ts"

/**
 * SH968 — what an agent run did reaches the dock, and the header count SH936 put there is gone.
 *
 * SH936's worry survives the move and is asserted here. An agent writing drafts and nothing
 * telling the curator is P7's surprise, and the one silent way back to it is a signal that stops
 * being mounted. So the shell must call the hook, and the pending count must still be read once at
 * load, or a curator who opens the console after the run hears nothing.
 *
 * The run logic is pure (`lib/agent-runs.ts`), with the clock passed in, so a whole run is driven
 * here without waiting and without a browser: first write, more writes, quiet, end.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")
const read = (...path) => stripComments(readFileSync(join(SRC, ...path), "utf8"))

function harness() {
  const timers = new Map()
  let nextTimer = 0
  const events = []
  const tracker = createRunTracker({
    quietMs: 1000,
    schedule: (run) => { nextTimer += 1; timers.set(nextTimer, run); return nextTimer },
    cancel: (handle) => { timers.delete(handle) },
    onStart: (run) => events.push(["start", run.session, run.name]),
    onWrite: (run) => events.push(["write", run.session, run.writes]),
    onEnd: (run) => events.push(["end", run.session, { ...run }]),
  })
  const elapse = () => { const due = [...timers.values()]; timers.clear(); for (const run of due) run() }
  return { tracker, events, elapse }
}

test("SH968: a run starts at its first write, bumps on each, and ends after quiet with what it left", () => {
  const { tracker, events, elapse } = harness()
  tracker.observe([
    { action: "CREATE", agent: "run-1", agentName: "claude-code", site: "acme", address: "post:acme/a" },
    { action: "UPDATE", by: "curator", site: "acme", address: "post:acme/b" },
  ])
  tracker.observe([
    { action: "UPDATE", agent: "run-1", site: "acme", address: "post:acme/a", review: { decision: "APPROVE" } },
    { action: "CREATE", agent: "run-1", site: "docs", address: "post:docs/c" },
  ])
  assert.deepEqual(events.filter(([kind]) => kind !== "end").map(([kind]) => kind),
    ["start", "write", "write", "write"],
    "SH968: one start for the run, one bump per agent write, and nothing for the curator's own")
  assert.equal(events[0][2], "claude-code", "SH968: the run's own name is in the caption")
  assert.deepEqual(tracker.running().map((run) => run.session), ["run-1"])

  elapse()
  const [, session, run] = events.at(-1)
  assert.equal(session, "run-1")
  assert.deepEqual(run.sites, ["acme", "docs"])
  assert.equal(run.writes, 3)
  assert.equal(run.pending, 2,
    "SH968: a write a curator already decided does not count toward what waits for review")
  assert.deepEqual(tracker.running(), [], "SH968: an ended run is still counted as writing")
})

test("SH968: a write inside the quiet window keeps the run open instead of ending it twice", () => {
  const { tracker, events, elapse } = harness()
  tracker.observe([{ action: "CREATE", agent: "run-2", site: "acme" }])
  tracker.observe([{ action: "CREATE", agent: "run-2", site: "acme" }])
  elapse()
  assert.equal(events.filter(([kind]) => kind === "start").length, 1)
  assert.equal(events.filter(([kind]) => kind === "end").length, 1,
    "SH968: the first write's timer was not cancelled, so one run reported two endings")
})

test("SH936/SH968: the shell reads agent runs into the dock, and the header holds no count", () => {
  const shell = read("app", "system", "bento.root.page.tsx")
  assert.match(shell, /useAgentRunReports\(\)/,
    "SH968: the shell no longer listens for agent runs, so an agent writes and nothing says so,"
      + " which is the P7 surprise SH936 existed to end")
  assert.doesNotMatch(shell, /ReviewPending/,
    "SH968: the header count is back. The contract gives a count to the surface that owns it, and"
      + " the dock reports what happened instead of a number")

  const hook = read("app", "system", "bento-agent-runs.ts")
  assert.match(hook, /subscribeChanges\(/, "SH968: the runs are not read off the change stream")
  assert.match(hook, /\.pending\(\{ signal: abort\.signal \}\)/,
    "SH936: the pending count is no longer read at load, so a curator who arrives after the run"
      + " hears nothing about what it left")
  assert.match(hook, /ROUTES\.ADMIN_REVIEW\}\?session=/,
    "SH968: a finished run's report does not open that run in the review queue")
})

test("SH968: the Activity page and the shell share one connection per scope", () => {
  const stream = read("hooks", "use-change-stream.ts")
  assert.match(stream, /const pool = new Map/,
    "SH968: each caller opens its own EventSource again, and each is a subscription the server"
      + " polls every second, now on every screen the shell renders")
  assert.match(stream, /listeners\.size === 0[\s\S]*?source\.close\(\)/,
    "SH968: the shared connection is never closed when its last listener leaves")
})
