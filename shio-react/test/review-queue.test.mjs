/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1197 — the review queue listed twenty-five runs under the same bold title, "no intent declared,
 * start the run with --intent", each with its own filled Approve: twenty-five primaries, and no way to
 * decide several runs at once. The look census counted the page as the most crowded in the console.
 */
import { strict as assert } from "node:assert"
import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { stripComments } from "./support/source-text.mjs"

const PAGE = stripComments(readFileSync(join(dirname(fileURLToPath(import.meta.url)), "..", "src", "app", "admin",
  "review", "review.list.page.tsx"), "utf8"))

test("SH1197: a row's Approve is a secondary, and the page's one primary decides the selection", () => {
  assert.match(PAGE, /variant="outline"\s+disabled=\{busy === session\.session \|\| session\.pending === 0\}\s+onClick=\{\(\) => act\(session\.session, "approve"\)\}/,
    "SH1197: each row's Approve is filled again, which made twenty-five primaries")
  assert.match(PAGE, /\{ticked\.length > 0 && \([\s\S]*?<Button size="sm" disabled=\{busy !== null\} onClick=\{\(\) => void actMany\(ticked, "approve"\)\}>/,
    "SH1197: no selection bar decides the ticked runs")
})

test("SH1197: the queue opens on what is waiting", () => {
  assert.match(PAGE, /show === "all" \|\| run\.pending > 0/,
    "SH1197: the default view lists resolved runs beside waiting ones")
})
