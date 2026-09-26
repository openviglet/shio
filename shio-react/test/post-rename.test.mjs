/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH1132 — the console can change a page's address, and says what that costs first.
 *
 * <p>A post's friendly URL is its identity (SH81) and `post.move` was its only writer, so a rename
 * was an agent-only act: a curator either asked an agent or created a second post and deleted the
 * first — which takes the original's id, its audit trail, its version history and every inbound
 * reference with it.</p>
 *
 * <p>The behaviour is asserted on the Java side (`ShConsoleRenameTest`, driven against real rows).
 * What is asserted here is the half that lives in the console: that the dialog cannot skip the
 * plan, and that the plan is the sentence a curator needs.</p>
 */
import { strict as assert } from "node:assert"
import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"

import { stripComments } from "./support/source-text.mjs"

const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src")
const read = (...path) => readFileSync(join(SRC, ...path), "utf8")

const DIALOG = "components/post-rename.dialog.tsx"

test("SH1132: the rename composes post.move rather than defining a move twice", () => {
  const service = stripComments(read("services", "post-unified", "post-unified.service.ts"))
  assert.match(service, /\/v2\/post-unified\/\$\{id\}\/move/u,
    "SH1132: the console's rename does not go through the move endpoint. A console writer for a "
      + "friendly URL would define what a move means twice, and the two would drift about the part "
      + "that is easy to get wrong — which rows move, and what happens to the address left behind.")
  assert.match(service, /const dryRun = options\.dryRun \?\? true/u,
    "SH1132: dryRun must default to TRUE. A rename of a published page moves the live URL at once, "
      + "so the request sent by accident has to be the harmless one (SH1021's rule).")
  // SH985 — and the real run is conditional, which is where the lost update actually is: a curator
  // renaming a page an agent has already moved would otherwise take the address back.
  assert.match(service, /headers: dryRun \? \{\} : this\.ifMatchEither\(id\)/u,
    "SH1132: the real run sends no If-Match, so two writers can each rename the page and the "
      + "second silently wins — which is the whole of what SH985's guard is for")
})

test("SH1132: the dialog cannot rename without showing the plan first", () => {
  const dialog = stripComments(read(...DIALOG.split("/")))

  // Two buttons, and which one renders is decided by whether a plan is in hand. A single button
  // that planned and applied in one press would put the sentence below on screen after the fact.
  assert.match(dialog, /\{plan \? \(/u,
    "SH1132: the confirm button is not gated on a plan, so a curator can rename without reading "
      + "what it does — which for a published page is: the live URL changes now.")
  assert.match(dialog, /setUrl\(event\.target\.value\); setPlan\(null\)/u,
    "SH1132: editing the URL must drop the plan. A plan answers a question about one address, and "
      + "showing it beside a different one is worse than showing none.")
  assert.match(dialog, /dryRun: true/u)
  assert.match(dialog, /dryRun: false/u,
    "SH1132: and the second step is the real run — a dialog that only ever plans renames nothing")
})

test("SH1132: the plan says the three things a curator cannot otherwise know", () => {
  const dialog = stripComments(read(...DIALOG.split("/")))
  for (const [what, key] of [
    ["that the live URL moves now", "content.renameGoesLiveNow"],
    ["which address stops answering", "content.renameVacates"],
    ["how many pages link to it", "content.renameInbound"],
  ]) {
    assert.ok(dialog.includes(key),
      `SH1132: the plan does not say ${what} (${key}). Both state rows move together, so renaming `
        + "a published page changes the live URL with no publish step — a curator shown a text "
        + "field and a Save button has been surprised, which is what P7 forbids.")
  }

  // Both locales, or the sentence the dialog exists for is a key on screen for half its readers.
  for (const locale of ["en", "pt"]) {
    const content = JSON.parse(read("i18n", "locales", locale, "content.json"))
    for (const key of ["content.renameTitle", "content.renameGoesLiveNow", "content.renameVacates",
      "content.renameInbound_one", "content.renameInbound_other", "content.renamed"]) {
      assert.ok(content[key], `SH1132: ${locale} is missing ${key}`)
    }
  }
})

test("SH1132: the editor offers it only where there is an address to change", () => {
  const page = stripComments(read("app", "content", "post.edit.page.tsx"))
  assert.match(page, /\{post\.furl && \(/u,
    "SH1132: a post with no friendly URL has nothing to move FROM, and the service refuses rather "
      + "than guessing — so the button that cannot work is not offered")
  assert.match(page, /<PostRenameDialog/u, "SH1132: the dialog is not mounted")
})
