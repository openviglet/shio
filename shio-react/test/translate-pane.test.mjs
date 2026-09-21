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

import { copyFromSource, pairedFields, sourceText } from "../src/lib/translate-pane.ts"

/**
 * SH1061 — the two panes of the translation workspace, as rules rather than as JSX.
 *
 * The screen's claim is that the source and the translation are readable as a pair: the same
 * fields, in the same order, with a copy that moves one value and disturbs nothing else. Those are
 * the three things a translator would notice being wrong, and the three asserted here.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const PAGE = readFileSync(join(ROOT, "src", "app", "content", "translate.page.tsx"), "utf8")

const field = (name, ordinal, widget = "Text") => ({ name, ordinal, widget, label: name })

test("SH1061: both panes show the post-type's fields in its own order, without the dividers", () => {
  const schema = [field("body", 3), field("tab", 2, "Tab"), field("title", 1)]

  assert.deepEqual(pairedFields(schema).map((one) => one.name), ["title", "body"],
    "SH1061: the panes are aligned by ordinal — the third row on the left has to be the third row "
      + "on the right, or reading them as a pair is reading two lists")
  assert.deepEqual(schema.map((one) => one.name), ["body", "tab", "title"],
    "SH1061: and the caller's schema is not reordered under it")
})

test("SH1061: copying a field from the source moves that value and nothing else", () => {
  const target = { title: "Sobre", body: "texto", keep: "intact" }

  const copied = copyFromSource(target, { title: "About", body: "text" }, "title")

  assert.equal(copied.title, "About")
  assert.equal(copied.body, "texto",
    "SH1061: copying the title must not restore a paragraph the translator deleted — a merge here "
      + "would undo work the page is showing")
  assert.equal(copied.keep, "intact")
  assert.equal(target.title, "Sobre", "SH1061: and the document in hand is not mutated")
  assert.equal(copyFromSource(target, undefined, "title").title, undefined,
    "SH1061: a source that carries nothing clears the field rather than leaving a stale value")
})

test("SH1061: the source pane is text, so every value survives being shown", () => {
  assert.equal(sourceText("hello"), "hello")
  assert.equal(sourceText(null), "")
  assert.equal(sourceText(3), "3")
  assert.equal(sourceText([{ type: "paragraph" }]).includes("paragraph"), true,
    "SH1061: a blocks body has to read as its content, not as [object Object]")
})

test("SH1061: the target saves as a draft, and the source pane renders no widget", () => {
  assert.match(PAGE, /state: "DRAFT"/u,
    "SH1061: publishing stays the separate explicit gesture it is everywhere else (P7)")
  assert.equal(/<PostFormFields[\s\S]*<PostFormFields/u.test(PAGE), false,
    "SH1061: one widget pane, not two — the left side is read-only BY CONSTRUCTION, and a second "
      + "set of inputs is the editable-looking source this layout exists to prevent")
})
