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
 * SH999 — the Activity page says which changes were a machine's.
 *
 * Read from source, like the other console surface tests. What can go wrong here is a column that
 * shows only the credential — which is the state this task found, and it reads as a person having
 * done everything an agent did — and a filter the console computes for itself, which would be a
 * second answer to a question the server already answers for the agent feed.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const read = (...path) => readFileSync(join(ROOT, ...path), "utf8")

test("SH999: the Who column carries the run beside the person, linked to the queue", () => {
  const page = read("src", "app", "admin", "activity", "activity.list.page.tsx")

  assert.match(page, /\{item\.agent && \(/,
    "SH999: absent on a person's row — a chip everybody carries says nothing about anybody")
  assert.match(page, /item\.agentName \?\? item\.agent\.slice\(0, 8\)/,
    "SH999: the name the run gave, falling back to the id it was minted as")
  assert.match(page, /ROUTES\.ADMIN_REVIEW\}\?session=/,
    "SH999: reading a run and undoing it is the next thing a curator who noticed this wants")
})

test("SH999: the three-way filter is the server's, not the console's", () => {
  const page = read("src", "app", "admin", "activity", "activity.list.page.tsx")
  const service = read("src", "services", "history", "history.service.ts")

  // SH1005 moved this from one hand-written spread to a loop over every narrowing, because there
  // are six of them now. The claim is the same one: a parameter that was not set is not sent.
  assert.match(service, /if \(value\) sent\[key\] = value/,
    "SH999: sent only when set — an absent parameter is everything, which is what the page asks "
      + "for until a curator narrows it")
  assert.match(page, /agent: who \|\| undefined/,
    "SH999: a console that decided for itself which rows were an agent's would be a second answer "
      + "to the question /agent/changes already answers")
  assert.doesNotMatch(page, /agentSessionId|startsWith\("console:"\)/,
    "SH999: a curator's own publishing carries a session id (SH996), so a console-side test for "
      + "one would file their own press under the machines")
})

test("SH999: both locales name the chip and the three filters", () => {
  for (const locale of ["en", "pt"]) {
    const activity = JSON.parse(read("src", "i18n", "locales", locale, "activity.json"))
    for (const key of ["activity.byAgent", "activity.filterAll", "activity.filterAgent",
      "activity.filterCurator"]) {
      assert.ok(activity[key], `SH999: ${locale} is missing ${key}`)
    }
  }
})

test("SH1000: the review queue leads with the premise, and names the flag when there is none", () => {
  const page = read("src", "app", "admin", "review", "review.list.page.tsx")

  assert.match(page, /\{session\.intent \?\? \(/,
    "SH1000: the premise leads, because it is what decides whether the list underneath is the "
      + "right list — a reviewer without it reads the diff to find out why")
  // SH1197 — with no premise, the run's short id leads and the absence is a muted note; the flag
  // that would have supplied one is named once under the list rather than in bold on every row.
  assert.match(page, /t\("review\.noIntentShort"\)/, "SH1197: a run with no intent says so on its row")
  assert.match(page, /t\("review\.intentHint"\)/, "SH1197: the --intent instruction is no longer said at all")
  assert.match(page, /session\.agentName \?\? session\.session/,
    "SH1000: the name the run gave, falling back to the uuid this task exists to stop leading with")

  for (const locale of ["en", "pt"]) {
    const review = JSON.parse(read("src", "i18n", "locales", locale, "review.json"))
    assert.ok(review["review.intentHint"], `SH1000: ${locale} is missing review.intentHint`)
    assert.match(review["review.intentHint"], /--intent/,
      `SH1000: ${locale} must name the argument that would have supplied one — an absent premise `
        + "is a fact about the run, and a blank line is not the same statement")
  }
})

test("SH1000: the summary is beside the premise, and the look report is behind the open", () => {
  const page = read("src", "app", "admin", "review", "review.list.page.tsx")

  assert.match(page, /Object\.keys\(session\.bySite \?\? \{\}\)\.length > 1/,
    "SH1000: by site only when a run touched more than one — on a single-site run the badge "
      + "repeats what the addresses already say, and a badge that is never news stops being read")
  assert.match(page, /detail\.appearance && detail\.appearance\.checked > 0/,
    "SH1000: shown only when something was comparable, because '0 of 0 pages' is a sentence about "
      + "the instrument rather than about the run")
  assert.doesNotMatch(page, /session\.appearance/,
    "SH1000: each comparison is two real renders (SH249), so it belongs to the one run somebody "
      + "opened and not to a listing the console asks for on every visit")

  for (const locale of ["en", "pt"]) {
    const review = JSON.parse(read("src", "i18n", "locales", locale, "review.json"))
    for (const key of ["review.appearanceSteady", "review.appearanceMoved"]) {
      assert.ok(review[key], `SH1000: ${locale} is missing ${key}`)
      assert.match(review[key], /\{\{checked\}\}/,
        `SH1000: ${locale} must carry the denominator — without it an empty list reads as "nothing `
          + 'moved" when it may mean "nothing was comparable"')
    }
  }
})

test("SH999: the model carries what the response carries, and says what it omits", () => {
  const model = read("src", "models", "history", "history.model.ts")

  assert.match(model, /agent\?: string \| null/)
  assert.match(model, /agentName\?: string \| null/)
  assert.match(model, /subjectKind\?: string \| null/,
    "SH999: recorded on every row since see SH196 and carried by no reader, so the Activity page "
      + "called a folder rename a post")
})
