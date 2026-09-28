/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1204 — the post editor titled every post "Editar Post", never said the post was a draft or what
 * type it was, drew Preview, the language menu and Actions all in orange, and labelled every field
 * with the post type's widget names (Title, Text, Ace Editor - HTML) in coloured pills that took the
 * curator's eye before the field did.
 */
import { strict as assert } from "node:assert"
import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { stripComments } from "./support/source-text.mjs"

const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src")
const read = (...parts) => stripComments(readFileSync(join(SRC, ...parts), "utf8"))

test("SH1204: the editor's title is the post, and its line says type, language and row", () => {
  const page = read("app", "content", "post.edit.page.tsx")
  assert.match(page, /title=\{post\.title \|\| t\("content\.editPost"\)\}/, "SH1204: the hero titles the page, not the post")
  assert.match(page, /subtitle=\{\[postTypeName, post\.locale\?\.toUpperCase\(\),/,
    "SH1204: the hero no longer says the post's type, language and state")
})

test("SH1204: Preview and the language menu are secondaries; Actions is the one primary", () => {
  const page = read("app", "content", "post.edit.page.tsx")
  assert.doesNotMatch(page, /<GradientButton[^>]*variant="outline"[\s\S]{0,200}handlePreview/,
    "SH1204: Preview is an orange GradientButton again")
  assert.doesNotMatch(read("components", "post-translations.menu.tsx"), /<GradientButton\b/,
    "SH1204: the language menu's trigger is an orange GradientButton again")
})

test("SH1204: a field shows no widget pills; the widget's name is the label's hint", () => {
  const fields = read("components", "widgets", "post-form-fields.tsx")
  assert.doesNotMatch(fields, /<BadgeColorful text=\{field\.widget\}/, "SH1204: the widget pill is back on every field")
  assert.match(fields, /title=\{\[field\.widget,/, "SH1204: the widget's name is no longer reachable at all")
})
