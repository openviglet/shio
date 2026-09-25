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
import { arrivalReport, arrivalsIn } from "../src/lib/arrival-reports.ts"

/**
 * SH970 — a form submission arriving becomes a dock report a curator can open.
 *
 * The feed half is ShFormSubmissionAPITest: a submission's CREATE row carries its caption, and a
 * tripped honeypot puts nothing on the feed, so it can never reach this code. This holds the dock
 * half on the row shape that test proves.
 */
const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src")
const read = (...path) => stripComments(readFileSync(join(SRC, ...path), "utf8"))

function deps() {
  const calls = { opened: [], trashed: [] }
  return {
    calls,
    t: (key, options = {}) => `${key} ${JSON.stringify(options)}`,
    open: (address) => calls.opened.push(address),
    trash: (address) => calls.trashed.push(address),
  }
}

const lead = (n) => ({
  action: "CREATE", address: `post:acme/leads/lead-${n}`, site: "acme", by: "form-token:site",
  submission: { title: `Ada ${n}`, summary: "Acme Corp, from the pricing page" },
})

test("SH970: only a form's CREATE is an arrival", () => {
  const arrivals = arrivalsIn({ changes: [
    lead(1),
    { action: "CREATE", address: "post:acme/news/x", site: "acme", by: "curator" },
    { action: "UPDATE", address: "post:acme/leads/lead-1", submission: { title: "edited" } },
  ] })
  assert.deepEqual(arrivals.map((a) => a.address), ["post:acme/leads/lead-1"],
    "SH970: a curator's own post, or an edit to a submission, reported as an arrival")
})

test("SH970: one arrival is an attention report with its caption, open and move to trash", () => {
  const d = deps()
  const report = arrivalReport(arrivalsIn({ changes: [lead(1)] }), d)
  assert.equal(report.tone, "attention")
  assert.match(report.text, /dock\.arrivedWithSummary .*"title":"Ada 1".*"summary":"Acme Corp, from the pricing page"/,
    "SH970: the caption is not the fields the form marks as title and summary")
  report.actions[0].onSelect()
  report.actions[1].onSelect()
  assert.deepEqual(d.calls.opened, ["post:acme/leads/lead-1"], "SH970: open does not go to the submission")
  assert.deepEqual(d.calls.trashed, ["post:acme/leads/lead-1"])
})

test("SH970: several arrivals together are one report, with no bulk trash", () => {
  const d = deps()
  const report = arrivalReport(arrivalsIn({ changes: [lead(1), lead(2), lead(3), lead(4)] }), d)
  assert.match(report.text, /dock\.arrivedMany .*"count":4.*"titles":"Ada 4, Ada 3, Ada 2"/,
    "SH970: a campaign became something other than one report naming the newest")
  assert.equal(report.actions.length, 1,
    "SH970: a grouped report offers more than 'open the newest'. One click in the corner must not"
      + " be what discards a campaign's leads (P7)")
  report.actions[0].onSelect()
  assert.deepEqual(d.calls.opened, ["post:acme/leads/lead-4"])
  assert.equal(arrivalReport([], d), undefined)
})

test("SH970: the shell reports arrivals, and trashing one reports how it ended", () => {
  assert.match(read("app", "system", "bento.root.page.tsx"), /useArrivalReports\(\)/,
    "SH970: nothing tells anyone a submission arrived")
  const actions = read("app", "system", "submission-actions.ts")
  assert.match(actions, /postService\.delete\(resolved\.id\)/,
    "SH970: move to trash no longer moves the post to the trash")
})
