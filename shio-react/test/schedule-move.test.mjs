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
import { entryKey, movedSchedule } from "../src/lib/schedule-move.ts"

// @scans code — the page is read for the calls it makes; comments are not what is asserted.
import { stripComments } from "./support/source-text.mjs"

/**
 * SH1082 — a drop on the Scheduled page's calendar sends the schedule write the right pair.
 *
 * The endpoint replaces both instants and null clears, so the failure worth a test is the quiet
 * one: moving a post's go-live and sending no come-down deletes the unpublish it had, and nothing
 * on the calendar says so until the day it fails to come down.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const both = {
  at: "2026-10-01T09:30:00Z", action: "publish", address: "post:site/launch", postId: "p1",
  publishAt: "2026-10-01T09:30:00Z", unpublishAt: "2026-10-31T18:00:00Z",
}

test("SH1082: moving the go-live keeps the come-down the post already had", () => {
  assert.deepEqual(movedSchedule(both, new Date("2026-10-03T09:30:00Z")),
    { publishAt: "2026-10-03T09:30:00.000Z", unpublishAt: "2026-10-31T18:00:00Z" },
    "SH1082: a moved publish must send the existing unpublish, or the write clears it")
})

test("SH1082: moving the come-down keeps the go-live", () => {
  const unpublish = { ...both, at: both.unpublishAt, action: "unpublish" }
  assert.deepEqual(movedSchedule(unpublish, new Date("2026-11-02T18:00:00Z")),
    { publishAt: "2026-10-01T09:30:00Z", unpublishAt: "2026-11-02T18:00:00.000Z" })
})

test("SH1082: a post with one transition sends null for the other, which is what it had", () => {
  const only = { ...both, unpublishAt: undefined }
  assert.deepEqual(movedSchedule(only, new Date("2026-10-05T09:30:00Z")),
    { publishAt: "2026-10-05T09:30:00.000Z", unpublishAt: null })
})

test("SH1082: a post's two transitions are two calendar entries with two ids", () => {
  assert.notEqual(entryKey(both), entryKey({ ...both, action: "unpublish" }),
    "SH1082: the go-live and the come-down share an id, so a drop moves whichever the map kept")
})

test("SH1082: the page draws the calendar in the stated zone and writes a drop through the schedule endpoint", () => {
  const page = stripComments(readFileSync(join(HERE, "..", "src", "app", "content", "scheduled.page.tsx"), "utf8"))
  assert.match(page, /<BentoCalendar[\s\S]*?timeZone=\{ZONE\}/,
    "SH1082: the calendar is not drawn in the zone the page states above it")
  assert.match(page, /onEntryMove=/, "SH1082: the calendar takes no move, so nothing can be rescheduled")
  assert.match(page, /unifiedService\.schedule\(entry\.postId, movedSchedule\(entry, start\)\)/,
    "SH1082: a drop does not go through the post's schedule write with the pair movedSchedule builds")
})
