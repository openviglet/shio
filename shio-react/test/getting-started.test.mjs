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
import { START_STEPS, startChecklist, startDismissKey } from "../src/lib/getting-started.ts"
import { stripComments } from "./support/source-text.mjs"

/**
 * SH1423 — the home page walks a newcomer round Shio's loop once.
 *
 * Nothing on home said there was more to do after creating a site. A checklist now names the five
 * steps, ticked from the summary's `start` (the instance's state, not this browser's clicks), with
 * each unticked step linking to where it is done.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")
const read = (...parts) => stripComments(readFileSync(join(SRC, ...parts), "utf8"))

test("SH1423: the loop's steps, in the order a newcomer goes round it", () => {
  assert.deepEqual([...START_STEPS], ["site", "edited", "published", "agent", "reviewed"])
})

test("SH1423: the checklist reads the instance's state and says when it has nothing left", () => {
  assert.equal(startChecklist(undefined), null, "SH1423: an unanswered summary draws five unticked steps")
  const partway = startChecklist({ site: true, edited: false, published: true, agent: false, reviewed: false })
  assert.equal(partway.done, 2)
  assert.equal(partway.complete, false)
  assert.deepEqual(partway.steps.map((each) => each.done), [true, false, true, false, false])
  const finished = startChecklist({ site: true, edited: true, published: true, agent: true, reviewed: true })
  assert.equal(finished.complete, true, "SH1423: a finished loop still shows its checklist")
})

test("SH1423: hiding it is per user", () => {
  assert.notEqual(startDismissKey("ana"), startDismissKey("rui"))
})

test("SH1423: home draws the checklist from the summary, and it never ticks from the browser", () => {
  const home = read("app", "system", "home.page.tsx")
  assert.match(home, /<GettingStarted state=\{summary\?\.start\}/, "SH1423: home no longer draws the checklist from the summary")
  const list = read("app", "system", "getting-started.tsx")
  assert.match(list, /if \(!checklist \|\| checklist\.complete \|\| dismissed\) return null/,
    "SH1423: the checklist stays after every step is done, or cannot be hidden")
  assert.doesNotMatch(list, /localStorage\.setItem\([^)]*(site|edited|published|agent|reviewed)/,
    "SH1423: a step is ticked from the browser, so it is false on every other device and for every colleague")
  assert.match(list, /admin \? \(\s*<button[^>]*onClick=\{\(\) => setConnecting\(true\)\}/,
    "SH1423: the agent step no longer opens the connect panel for an administrator")
})

test("SH1423: every checklist string exists in English and Portuguese", () => {
  for (const lang of ["en", "pt"]) {
    const home = JSON.parse(readFileSync(join(SRC, "i18n", "locales", lang, "home.json"), "utf8")).home
    for (const key of ["startTitle", "startProgress", "startDismiss",
      ...START_STEPS.flatMap((step) => [`startStep_${step}`, `startHint_${step}`])]) {
      assert.ok(home?.[key], `SH1423: home.${key} is missing in ${lang}`)
    }
  }
})
