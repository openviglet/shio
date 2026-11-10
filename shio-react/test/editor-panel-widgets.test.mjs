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
import { attrFromDeclared, valueFromWidget } from "../src/app/editor/field-labels.ts"

/**
 * SH1393 — the Visual Editor's panel writes every field kind.
 *
 * Selecting the hero's image drew the post form's image widget under `pointer-events-none` with an
 * `onChange` that did nothing, so a curator saw the file and had to leave for the form to replace it.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const editor = readFileSync(join(HERE, "..", "src", "app", "editor", "visual-editor.page.tsx"), "utf8")

test("SH1393: the panel's widget is live and its change is a pending change", () => {
  const panel = editor.slice(editor.indexOf(") : declaredWidgetFor(f.prop) ? ("), editor.indexOf("break-words"))
  assert.doesNotMatch(panel, /pointer-events-none/u, "SH1393: the widget is drawn inert again")
  assert.doesNotMatch(panel, /onChange=\{\(\) => undefined\}/u, "SH1393: the widget's change goes nowhere again")
  assert.match(panel, /editWidgetField\(\{ \.\.\.f, value: storedValueOf\(f\.prop\) \?\? f\.value \}, valueFromWidget\(declared, next\)\)/u,
    "SH1393: a widget's change joins the pending set, remembering the stored value for Revert")
  assert.match(panel, /pendingFor\(f\.prop\)\?\.value \?\? storedValueOf\(f\.prop\) \?\? f\.value/u,
    "SH1393: the widget shows the value about to be saved, else the stored one: the page draws an image's element, not its file id")
  assert.match(editor, /type: "widget", value, label: field\.label, was/u,
    "SH1393: and keeps what the field held, so Revert can put it back")
  assert.match(editor, /saved\.some\(\(change\) => change\.type === "widget"\)\) refresh\(\)/u,
    "SH1393: the template draws these, not the bridge, so a save that wrote one shows the page again")
})

test("SH1393: a date is read and written as a date, everything else as its string", () => {
  const date = { name: "eventDate", widget: "Date" }
  assert.equal(attrFromDeclared(date, "2026-11-09T00:00:00.000Z").dateValue, "2026-11-09T00:00:00.000Z",
    "SH1393: a Date widget reads dateValue, so a date seeded only as a string drew empty")
  assert.equal(valueFromWidget(date, { dateValue: "2026-12-01T00:00:00.000Z", strValue: "x" }), "2026-12-01T00:00:00.000Z")
  assert.equal(valueFromWidget({ name: "image", widget: "File" }, { strValue: "file-id" }), "file-id")
})
