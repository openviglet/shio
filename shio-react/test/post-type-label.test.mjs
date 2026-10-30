/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"

import { distinctKey, labelOf } from "../src/lib/post-type-label.ts"
import { stripComments } from "./support/source-text.mjs"

/**
 * SH1310 — the post-type list drew "InfoPage" under "InfoPage" on nearly every row. The key is a
 * second line only where it says something the title does not.
 */
test("SH1310: a type whose title equals its name has no second line", () => {
  assert.equal(distinctKey({ name: "InfoPage", title: "InfoPage" }), undefined,
    "SH1310: the list repeats the name under itself again")
  assert.equal(distinctKey({ name: "File" }), undefined, "SH1310: an untitled type is labelled by its name")
  assert.equal(distinctKey({ name: "File", title: "  " }), undefined, "SH1310: a blank title is no title")
  assert.equal(distinctKey({ name: "BuildNote", title: "How this was built" }), "BuildNote",
    "SH1310: a key that differs is the word an agent and a file use, so it is shown")
  assert.equal(labelOf({ name: "MenuItem", title: "Menu Item" }), "Menu Item")
})

test("SH1310: the list reads the helper, has no System column, and sizes its rows to its content", () => {
  const page = stripComments(readFileSync(join(dirname(fileURLToPath(import.meta.url)), "..", "src",
    "app", "content", "post-type.list.page.tsx"), "utf8"))
  assert.match(page, /from "@\/lib\/post-type-label"/, "SH1310: the page labels types its own way again")
  assert.doesNotMatch(page, /detail=\{type\.name\}/, "SH1310: the key is the detail on every row again")
  assert.doesNotMatch(page, /id: "system"/, "SH1310: System is a column of mostly nothing again")
  assert.match(page, /lines: twoLines \? 2 : 1/, "SH1310: every row is two lines tall whatever it holds")
})
