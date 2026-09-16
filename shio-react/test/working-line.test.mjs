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

/**
 * SH1008 — the console's half: somebody else is on this page, said before anyone types.
 *
 * What can go wrong here is a line that arrives after the curator has written the paragraph (which
 * is what SH985's conflict already does), one that waits for an event to clear and so goes on saying
 * somebody is here after they have gone, and a line that quietly turns into a lock by disabling the
 * things it sits above.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const read = (...path) => readFileSync(join(ROOT, ...path), "utf8")

test("SH1008: the line is above the fields, because it is read before anyone types", () => {
  const page = read("src", "app", "content", "post.edit.page.tsx")
  const fields = page.indexOf("AdaptiveSectionCard.StaticHeader icon={IconFileText}")
  const line = page.indexOf("<WorkingLine objectId={postId} />")

  assert.ok(line > 0, "SH1008: the post editor mounts it")
  assert.ok(line < fields,
    "SH1008: after the fields it would be one more thing to scroll past, and the whole point is "
      + "that it is read before the curator starts writing — SH985's conflict already covers after")
})

test("SH1008: it clears on its own timer, not only on an event", () => {
  const line = read("src", "components", "working-line.tsx")

  assert.match(line, /new Date\(mark\.until\)\.getTime\(\) - Date\.now\(\)/,
    "SH1008: the mark is derived from writes, so its END has no event — a run that stops writing "
      + "sends nothing, and a line waiting for that news is the stale-presence bug")
  assert.match(line, /setTimeout\(\(\) => setMark\(null\), Math\.max\(0, left\)\)/)
  assert.match(line, /useChangeStream\(ask\)/,
    "SH1008: the stream makes it re-ask; the server decides what working means, and a screen that "
      + "computed it from stream rows would be a second answer to one question")
})

test("SH1008: it is not a lock — the line disables nothing", () => {
  const line = read("src", "components", "working-line.tsx")

  assert.doesNotMatch(line, /disabled|readOnly|readonly/,
    "SH1008: presence derived from a timestamp and consulted by a write would be a lock held by "
      + "whoever wrote last and released by nobody. The concurrency contract stays SH985's expect")
  assert.match(line, /aria-live="polite"/,
    "SH1008: it appears without the curator doing anything, so it is announced — politely, "
      + "because it is context and not an interruption")
})

test("SH1008: the run is named, and what it said it was for", () => {
  const line = read("src", "components", "working-line.tsx")

  assert.match(line, /mark\.agentName \?\? mark\.session\.slice\(0, 8\)/,
    "SH1008: the name the run gave, falling back to the id it was minted as")
  assert.match(line, /mark\.intent[\s\S]{0,120}activity\.workingFor/,
    "SH1008: SH1000's intent is the half a person can act on")

  for (const locale of ["en", "pt"]) {
    const activity = JSON.parse(read("src", "i18n", "locales", locale, "activity.json"))
    for (const key of ["activity.working", "activity.workingFor"]) {
      assert.ok(activity[key], `SH1008: ${locale} is missing ${key}`)
    }
  }
})
