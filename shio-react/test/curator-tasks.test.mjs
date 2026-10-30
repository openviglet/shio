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
import { compare, GATED, record } from "../scripts/curator-tasks-budget.mjs"

/**
 * SH1315 — the curator's tasks are measured, and the measurement is held.
 *
 * The harness drives a live console (`e2e/curator-tasks.mjs`), so it runs with the other e2e
 * checks and not here. What runs here is everything that can be wrong without a browser: the rule a
 * run is judged by, and the agreement between the tasks the harness drives and the budget that
 * holds them, so a task added to one and not the other fails the build rather than a run.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const BUDGET = JSON.parse(readFileSync(join(ROOT, "curator-task-budget.json"), "utf8"))
const HARNESS = readFileSync(join(ROOT, "e2e", "curator-tasks.mjs"), "utf8")
const run = (clicks, keys = 0, screens = 1) => ({ clicks, keys, screens, ms: 1000 })

test("SH1315: a task that costs more than its budget is a regression, and one that costs less must be recorded", () => {
  const budget = { tasks: { a: { ...run(4, 1, 1), why: "" } } }
  assert.equal(compare({ a: run(4, 1, 1) }, budget).ok, true, "SH1315: the budget itself passes")
  const worse = compare({ a: run(5, 1, 1) }, budget)
  assert.deepEqual(worse.regressions, ["a: clicks 5 > 4"], "SH1315: an extra click is a regression")
  assert.equal(worse.ok, false)
  const better = compare({ a: run(4, 1, 0) }, budget)
  assert.deepEqual(better.improvements, ["a: screens 0 < 1"])
  assert.equal(better.ok, false, "SH1315: an improvement left unrecorded is a ceiling that stays high")
  assert.equal(compare({ a: { ...run(4, 1, 1), ms: 99_999 } }, budget).ok, true, "SH1315: time is never gated")
})

test("SH1315: a task blocked by a filed defect stays blocked, and completing it is news", () => {
  const budget = { tasks: { p: { blocked: "SH1322", why: "" }, q: { ...run(2), why: "" } } }
  assert.equal(compare({ p: { blocked: "SH1322" }, q: run(2) }, budget).ok, true, "SH1315: still blocked is expected")
  assert.match(compare({ p: run(3), q: run(2) }, budget).improvements[0], /no longer blocked by SH1322/u)
  assert.match(compare({ p: { blocked: "SH1322" }, q: { blocked: "new" } }, budget).regressions[0], /q: blocked/u)
})

test("SH1315: a task with no budget, or a budget with no task, fails", () => {
  const budget = { tasks: { a: { ...run(1), why: "" } } }
  assert.deepEqual(compare({ a: run(1), b: run(1) }, budget).unbudgeted, ["b"])
  assert.deepEqual(compare({}, budget).unmeasured, ["a"])
})

test("SH1315: recording keeps every task's reason and its blocker", () => {
  const previous = { _comment: "kept", tasks: { a: { ...run(9), why: "because" }, p: { blocked: "SH1322", why: "draft" } } }
  const next = record({ a: run(3), p: { blocked: "SH1322" } }, previous)
  assert.equal(next._comment, "kept")
  assert.deepEqual(next.tasks.a, { clicks: 3, keys: 0, screens: 1, ms: 1000, why: "because" })
  assert.deepEqual(next.tasks.p, { blocked: "SH1322", why: "draft" })
})

test("SH1315: the harness drives exactly the tasks the budget holds, each from /bento, each with a reason", () => {
  const driven = [...HARNESS.matchAll(/^\s{4}name: "([a-z-]+)",$/gmu)].map(([, name]) => name).sort()
  assert.deepEqual(driven, Object.keys(BUDGET.tasks).sort(),
    "SH1315: a task the harness drives and the budget does not hold (or the reverse) is a measurement nobody judges")
  assert.equal(driven.length, 6, "SH1315: the six curator jobs §XI.81 names")
  for (const [name, entry] of Object.entries(BUDGET.tasks)) {
    assert.ok(entry.why?.length > 40, `SH1315: ${name} carries no reason, so a regression in it reads as noise`)
    if (!entry.blocked) for (const field of GATED) assert.equal(typeof entry[field], "number", `SH1315: ${name}.${field}`)
  }
  // Finding the place is part of the job: the meter starts on /bento, never on a deep link.
  assert.match(HARNESS, /page\.goto\(`\$\{BASE\}\/bento`[^)]*\)[\s\S]{0,200}m\.start\(\)/u,
    "SH1315: the meter no longer starts on /bento, so a task could skip finding where its work is")
})

test("SH1337: approve-run presses its own run's row on home, which names a run by its intent", () => {
  assert.doesNotMatch(HARNESS, /fila de revisão|review queue/u,
    "SH1337: home has no link by that name since SH1318, so approve-run timed out on every run")
  assert.match(HARNESS, /ctx\.intent = `[^`]*\$\{RUN\}`/u,
    "SH1337: an intent shared between runs would press a run some earlier task left waiting")
  assert.match(HARNESS, /getByRole\("link", \{ name: ctx\.intent \}\)/u,
    "SH1337: approve-run no longer clicks the row its own setup created")
})
