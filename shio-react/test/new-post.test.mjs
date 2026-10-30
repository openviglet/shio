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

/**
 * SH1326 — writing a page and publishing it. The live measure is SH1315's `create-page`, which
 * this slice took from nine clicks over seven screens to eight over six.
 */
const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src")
const page = stripComments(readFileSync(join(SRC, "app", "content", "post.new.page.tsx"), "utf8"))

test("SH1326: Save on a new post opens its editor, not the folder it was made in", () => {
  assert.match(page, /const created = await unifiedService\.create\(/u)
  assert.match(page, /navigate\(created\?\.id \? `\$\{ROUTES\.CONTENT_ROOT\}\/post\/\$\{created\.id\}[^`]*` : backUrl\)/u,
    "SH1326: Save returns to the folder again, so the curator has to find the page to publish it")
})

test("SH1326: the form publishes through the editor's own confirmation, asked once", () => {
  assert.match(page, /onClick=\{\(\) => handleSave\(true\)\}/u, "SH1326: the new-post form only saves again")
  assert.match(page, /\$\{publish \? "\?publish=1" : ""\}/u)
  const editor = stripComments(readFileSync(join(SRC, "app", "content", "post.edit.page.tsx"), "utf8"))
  assert.match(editor, /if \(wantsPublish && !publishAsked && post && allowed\.data\) \{\s*setPublishAsked\(true\)\s*if \(allowed\.data\.publish\) setPublishPreviewOpen\(true\)/u,
    "SH1326: the editor ignores ?publish=1, or opens the confirmation for a curator who may not publish")
  assert.match(editor, /next\.delete\("publish"\)/u, "SH1326: a reload of the editor asks to publish again")
})

test("SH1326: the new-post header names the type and no internal word", () => {
  assert.match(page, /title=\{t\("content\.newPostOfType", \{ type: postTypeLabel \}\)\}/u,
    "SH1326: a new Page is headed \"New Post\" again")
  assert.doesNotMatch(page, /text="unified"/u, "SH1326: the header shows the model's internal name again")
})
