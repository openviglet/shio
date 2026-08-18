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
 * SH178 — one schema entry point, held by an assertion instead of by the comment saying so.
 *
 * ## The defect, which is worth restating because it was invisible
 *
 * The module exported `parseSchema(schemaJson)` — no fallback — and `buildSchemaFromPostType`
 * — with one — at equal prominence. The create page called the second and worked. The edit page
 * called the first and rendered **nothing**. That one difference was the whole distance between
 * "creating a post is fine" and "editing the post I just created is impossible", and it presented
 * as a content problem rather than a missing line, because an empty form looks like a post-type
 * with no fields.
 *
 * ## Why an export count and not a behaviour test
 *
 * The behaviour is already covered — `postFormSchema` returning the projected fields is what
 * every page depends on and what breaks loudly. What has **nothing** watching it is the shape:
 * a second exported function that also builds a schema, added for a good local reason, puts the
 * next reader back in front of the same choice. This is on the silent debt (SH827) for that
 * reason: a consolidation, like a removal, has nobody looking at it.
 *
 * ## Read as source, deliberately
 *
 * The module is TypeScript and this suite runs on plain node, so importing it would need the
 * build. The question here is about the export *surface*, which the text answers exactly — and
 * a build step to count exports would make the cheapest guard in the tree one of the dearest.
 */
const MODULE = join(dirname(fileURLToPath(import.meta.url)),
  "..", "src", "models", "post-unified", "post-unified.ts")

/** Exported function names, comments stripped: a `parseSchema` named in prose is not an export. */
function exportedFunctions() {
  const source = readFileSync(MODULE, "utf8")
    .split("\n")
    .filter((line) => {
      const text = line.trim()
      return !text.startsWith("//") && !text.startsWith("*") && !text.startsWith("/*")
    })
    .join("\n")
  return [...source.matchAll(/^export function (\w+)/gm)].map(([, name]) => name).sort()
}

test("SH178: the form schema has exactly one exported entry point", () => {
  assert.deepEqual(exportedFunctions(), ["postFormSchema"],
    "SH178: two exported schema builders is how the edit page rendered nothing while the create "
    + "page worked — one had a fallback and the other did not, and which page called which was "
    + "the entire bug. It read as a content problem, because an empty form and a post-type with "
    + "no fields look the same.\n"
    + "In `+ actual`: if the new export genuinely does something `postFormSchema` cannot, say so "
    + "here and name which pages must call it. A second builder at equal prominence is the shape "
    + "this refuses, not a second function.")
})

test("SH178: the guard reads exports and not the prose that explains them", () => {
  // The only failure mode an assertion about a surface has is matching nothing, and this one has
  // a second: the module's own javadoc names `parseSchema` and `buildSchemaFromPostType` while
  // explaining their removal, so a scan over raw text would report the explanation as the defect
  // (SH825's family, and the reason the comment strip is above rather than assumed).
  const raw = readFileSync(MODULE, "utf8")
  assert.ok(raw.includes("parseSchema"),
    "the fixture for this check is the module's own comment, which still names the retired "
    + "export — if that sentence goes, this assertion is testing nothing and should go with it")
  assert.ok(!exportedFunctions().includes("parseSchema"),
    "SH178: and it is named in prose only. A guard that could not tell the two apart would have "
    + "been red on the commit that shipped the fix")
  assert.ok(exportedFunctions().length > 0,
    "the export scan finds something at all, or the assertion above passes on an empty list")
})
