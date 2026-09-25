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
import { scheduleReports } from "../src/lib/schedule-reports.ts"

/**
 * SH969 — a schedule's outcome reaches the dock, and a failure's retry is the incident's own op.
 *
 * The feed half is asserted in ShScheduleOutcomeFeedTest, including that the retry op publishes
 * exactly once. This holds the dock half: the incident becomes an error report whose "retry now"
 * sends that op unchanged, and the scheduler's own publish becomes a success naming the page.
 */
const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src")
const read = (...path) => stripComments(readFileSync(join(SRC, ...path), "utf8"))

function deps() {
  const calls = { opened: [], retried: [] }
  return {
    calls,
    t: (key, options = {}) => `${key} ${JSON.stringify(options)}`,
    when: (at) => `at(${at})`,
    openPost: (id) => calls.opened.push(id),
    retry: (op, address) => calls.retried.push([op, address]),
  }
}

test("SH969: a planted failing schedule becomes an error report whose retry sends the incident's op", () => {
  const d = deps()
  const reports = scheduleReports({
    incidents: [{
      kind: "schedule", subject: "id:post-1", message: "The publish gate refused: no title",
      fix: "Give the page a title", retry: { op: "post.publish", address: "post:acme/launch" },
    }, { kind: "request", subject: "/api/v2/x", message: "not a schedule" }],
  }, d)

  assert.equal(reports.length, 1, "SH969: only a schedule incident is a schedule report")
  const [report] = reports
  assert.equal(report.tone, "error")
  assert.match(report.text, /publish gate refused: no title.*Give the page a title/,
    "SH969: the report does not carry the server's message and fix")
  assert.deepEqual(report.actions.map((a) => a.label.split(" ")[0]), ["dock.openPage", "dock.retryNow"])

  report.actions[0].onSelect()
  report.actions[1].onSelect()
  assert.deepEqual(d.calls.opened, ["post-1"], "SH969: open does not go to the post the incident names")
  assert.deepEqual(d.calls.retried, [["post.publish", "post:acme/launch"]],
    "SH969: retry did not send the incident's own op, so it is not the publish the feed test proves")
})

test("SH969: the scheduler's own publish becomes a success naming the page and the time", () => {
  const reports = scheduleReports({
    changes: [
      { action: "PUBLISH", by: "scheduler", what: "Launch", address: "post:acme/launch", at: "2026-09-25T12:00:00Z" },
      { action: "PUBLISH", by: "curator", what: "Mine" },
      { action: "UPDATE", by: "scheduler", what: "Not a transition" },
    ],
  }, deps())
  assert.equal(reports.length, 1, "SH969: a curator's own publish, or a non-transition, is not a schedule outcome")
  assert.equal(reports[0].tone, "success")
  assert.match(reports[0].text, /dock\.schedulePublished .*"page":"Launch".*"time":"at\(2026-09-25T12:00:00Z\)"/)
})

test("SH969: the shell reports schedules, live and missed, and retries through the batch", () => {
  assert.match(read("app", "system", "bento.root.page.tsx"), /useScheduleReports\(\)/,
    "SH969: the shell no longer reports schedule outcomes, so a launch that failed is found by a customer")
  const hook = read("app", "system", "bento-schedule-reports.ts")
  assert.match(hook, /replayChanges\(since,/,
    "SH969: nothing reads the failures that happened while no screen was open")
  const stream = read("hooks", "use-change-stream.ts")
  assert.match(stream, /\/api\/v2\/events\?since=/,
    "SH969: the catch-up no longer replays through the stream, whose hub applies the reader's"
      + " content ACL to incidents; the agent feed does not, and a post's failure would reach a"
      + " curator the folder excludes")
  assert.match(hook, /subscribeChanges\(/, "SH969: live outcomes are not read off the stream")
  const service = read("services", "agent-feed", "agent-feed.service.ts")
  assert.match(service, /axios\.post\("\/v2\/agent\/batch", \{ ops: \[\{ op, address \}\] \}\)/,
    "SH969: a retry is no longer the one batch op the incident carries (P10)")
})
