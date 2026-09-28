/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1200 — opening a group rendered the entity shell's hero over empty space, confirmed on
 * 2026-09-28 by reading the page's text after load: the title, the description and a Cancel, nothing
 * else. The API has always carried the membership (`GET /v2/group/{id}` answers with `shUsers`, `PUT`
 * replaces it), so the page lists the members and adds or removes one through that same call.
 */
import { strict as assert } from "node:assert"
import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { stripComments } from "./support/source-text.mjs"

const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src")
const read = (...parts) => stripComments(readFileSync(join(SRC, ...parts), "utf8"))

test("SH1200: a group's page lists its members under the hero", () => {
  const page = read("app", "admin", "group.edit.page.tsx")
  assert.match(page, /detail=\{\(group, save\) => \([\s\S]*<GroupMembers members=\{group\.shUsers \?\? \[\]\}/,
    "SH1200: the group page renders nothing under its hero again")
  const shell = read("app", "admin", "identity-entity.page.tsx")
  assert.match(shell, /\) : detail \? detail\(entity, async \(next\) => \{\s*await service\.update\(next\)/,
    "SH1200: an existing entity's detail is not rendered, or its save does not reach the API")
})

test("SH1200: a membership change is written through the group's own update", () => {
  const panel = read("app", "admin", "group-members.tsx")
  assert.match(panel, /await onChange\(next\)/, "SH1200: adding or removing a member saves nothing")
  assert.match(panel, /filter\(\(user\) => !inGroup\.has\(user\.username\)\)/,
    "SH1200: the picker offers users who are already members")
})
