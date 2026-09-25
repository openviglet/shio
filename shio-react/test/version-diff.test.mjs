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
import { diffFields, kindOf, railVersions, revisionOf } from "../src/lib/version-diff.ts"

/**
 * SH988 — one comparison, answered by the server and drawn by the package, on both surfaces.
 *
 * The comparison itself is ShPostDiffServiceTest's (a create has no before, an edit names its
 * field, any two revisions compare). This holds the console half: how the answer is handed to
 * BentoDiff and BentoVersionRail, and that neither surface computes a diff of its own again.
 */
const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src")
const read = (...path) => stripComments(readFileSync(join(SRC, ...path), "utf8"))

test("SH988: rich text compares by blocks, text by word, and anything else whole", () => {
  assert.equal(kindOf("HTML Editor"), "rich")
  assert.equal(kindOf("Text"), "text")
  assert.equal(kindOf("Text Area"), "text")
  assert.equal(kindOf("Date"), "value")
  assert.equal(kindOf(undefined), "value")
})

test("SH988: fields keep the server's order, take the schema's labels, and an undeclared one still shows", () => {
  const fields = diffFields(
    { from: "published", to: "draft", before: null, after: {}, fields: [
      { name: "body", changed: true }, { name: "legacy", changed: true }, { name: "title", changed: true }] },
    [{ name: "title", label: "Title", widget: "Text" }, { name: "body", label: "Body", widget: "HTML Editor" }])
  assert.deepEqual(fields.map((f) => [f.id, f.label, f.kind]),
    [["body", "Body", "rich"], ["legacy", "legacy", "value"], ["title", "Title", "text"]],
    "SH988: a field the schema does not declare disappeared, and an undeclared change is what a reviewer most needs")
})

test("SH988: the rail is the draft, the published page, then each revision newest first, agents marked", () => {
  const versions = railVersions(
    [{ version: 1, state: "PUBLISHED", capturedAt: "2026-09-01T00:00:00Z", actor: "ana" },
      { version: 2, state: "DRAFT", capturedAt: "2026-09-02T00:00:00Z", actor: "admin", agentSession: "run-1",
        changed: ["title"] }],
    { draft: "Draft", published: "Live", person: "—" },
    { draft: "2026-09-03T00:00:00Z", published: "2026-09-02T12:00:00Z" })
  assert.deepEqual(versions.map((v) => v.id), ["draft", "published", "rev:2", "rev:1"])
  assert.equal(versions[2].actor, "agent", "SH988: a revision an agent wrote reads as a person's")
  assert.equal(versions[2].summary, "title")
  assert.deepEqual(railVersions([], { draft: "Draft", published: "Live", person: "—" }, {}).map((v) => v.id),
    ["draft"], "SH988: a page never published offered a published version to compare against")
  assert.equal(revisionOf("rev:12"), 12)
  assert.equal(revisionOf("published"), null)
})

test("SH988: the dialog and the review queue draw the server's diff, and the queue offers it for a create", () => {
  const dialog = read("components", "post-versions.dialog.tsx")
  assert.match(dialog, /service\.diff\(postId, from, to/, "SH988: the dialog computes its own diff again")
  assert.match(dialog, /<BentoDiff /)
  assert.match(dialog, /<BentoVersionRail/)
  assert.doesNotMatch(dialog, /buildDiff/, "SH988: the client-side field comparison is back")

  const triage = read("components", "review-triage.tsx")
  assert.doesNotMatch(triage, /entry\.published && entry\.draft/,
    "SH988: the diff is gated on a published twin again, so a created page has none")
  assert.match(triage, /\(entry\.draft \|\| entry\.published\)/)
  assert.match(triage, /<ReviewEntryDiff postId=/, "SH988: the queue's diff is not inline")
  assert.match(triage, /review\.changedFields/, "SH988: an approved entry no longer names what it changed")
})

test("SH988: a create shows its rendered outline and a preview, and one field restores alone", () => {
  const dialog = read("components", "post-versions.dialog.tsx")
  assert.match(dialog, /\{!diff\.before && <CreatedPagePanel postId=\{postId\} \/>\}/,
    "SH988: a created page's diff is only additions again, with no outline and no preview")
  assert.match(dialog, /service\.patch\(postId, \{ \[name\]: diff\.before\[name\] \?\? null \}\)/,
    "SH988: restoring one field no longer writes just that field's older value into the draft")
  assert.match(dialog, /confirmingField !== name/, "SH988: a field restore acts on the first press")

  const panel = read("components", "created-page-panel.tsx")
  assert.match(panel, /insightsService\.digest\(`id:\$\{postId\}`, \{ signal: abort\.signal \}\)/,
    "SH988: the panel does not read the render digest, or reads it without a signal (SH1047)")
  assert.match(panel, /previewService\.mint\(postId\)/, "SH988: the panel offers no preview")
  assert.match(read("components", "review-entry-diff.tsx"), /<CreatedPagePanel postId=/,
    "SH988: the review queue's inline diff of a create has no outline")
})
