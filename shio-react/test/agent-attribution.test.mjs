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

  assert.match(service, /\.\.\.\(who \? \{ agent: who \} : \{\}\)/,
    "SH999: sent only when set — an absent parameter is everything, which is what the page asks "
      + "for until a curator narrows it")
  assert.match(page, /service\.recent\(200, who \|\| undefined\)/,
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
      "activity.filterHuman"]) {
      assert.ok(activity[key], `SH999: ${locale} is missing ${key}`)
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
