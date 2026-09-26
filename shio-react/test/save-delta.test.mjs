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
import { saveDelta } from "../src/lib/save-delta.ts"
import { stripComments } from "./support/source-text.mjs"

/**
 * SH1109 — a save in the visual editor says what moved, instead of the word saved.
 */
const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src")
const BEFORE = { digest: "d:aaa", appearanceDigest: "a:111" }

test("SH1109: a save's delta names the field that moved", () => {
  const delta = saveDelta({
    changes: [
      { postId: "hero", prop: "heading", type: "text", label: "Heading" },
      { postId: "hero", prop: "subheading", type: "text", label: "Subheading" },
      { postId: "cta", prop: "ctaHref", type: "attr", label: "Call-to-action link" },
    ],
    before: BEFORE,
    after: { digest: "d:bbb", appearanceDigest: "a:111" },
  })
  assert.deepEqual(delta.fields, ["Heading", "Subheading", "Call-to-action link"],
    "SH1109: the delta names the fields written, which two hashes side by side never could")
  assert.equal(delta.structureMoved, true)
  assert.equal(delta.appearanceMoved, false)
  assert.deepEqual(delta.changeSet, { changed: [
    { id: "hero", fields: ["heading", "subheading"] },
    { id: "cta", fields: ["ctaHref"] },
  ] }, "SH1109: and the log reads in the change-set shape the agent path answers with")
})

test("SH1109: a text-only edit is reported as leaving the appearance alone", () => {
  const delta = saveDelta({
    changes: [{ postId: "hero", prop: "heading", type: "text", label: "Heading" }],
    before: BEFORE,
    after: { digest: "d:aaa", appearanceDigest: "a:111" },
  })
  assert.equal(delta.textOnly, true)
  assert.equal(delta.appearanceMoved, false, "SH1109: the appearance did not move")
  assert.equal(delta.unexpected, false, "SH1109: so nothing asks the curator to look")
})

test("SH1109: a text-only edit that moved the appearance is the reflow worth flagging", () => {
  const delta = saveDelta({
    changes: [{ postId: "hero", prop: "heading", type: "text", label: "Heading" }],
    before: BEFORE,
    after: { digest: "d:aaa", appearanceDigest: "a:222" },
  })
  assert.equal(delta.unexpected, true,
    "SH1109: text alone moved the appearance - the defect this editor is most likely to produce")
  const attrs = saveDelta({
    changes: [{ postId: "cta", prop: "ctaHref", type: "attr" }],
    before: BEFORE,
    after: { digest: "d:aaa", appearanceDigest: "a:222" },
  })
  assert.equal(attrs.unexpected, false, "an attribute edit may move what draws it, and is not flagged")
})

test("SH1109: a digest that could not be read is 'not checked', never 'unchanged'", () => {
  const delta = saveDelta({
    changes: [{ postId: "hero", prop: "heading", type: "text" }],
    before: null,
    after: { digest: "d:aaa", appearanceDigest: "a:111" },
  })
  assert.equal(delta.structureMoved, null)
  assert.equal(delta.appearanceMoved, null)
  assert.equal(delta.unexpected, false)
})

test("SH1109: the save takes the digests before and after the write, and reports to the dock", () => {
  const page = stripComments(readFileSync(join(SRC, "app", "editor", "visual-editor.page.tsx"), "utf8"))
  const before = page.indexOf("const before = pageId")
  const write = page.indexOf("await editorService.patch(postId, attrs, publish)")
  const after = page.indexOf("after: await editorService.digests(pageId)")
  assert.ok(before > 0 && write > before && after > write,
    "SH1109: before, then the write, then after - a pair taken on one side of the write proves nothing")
  assert.match(page, /pushReport\(\{[\s\S]*?\}, "saves"\)/, "SH1109: reported under the dock's saves kind")
})
