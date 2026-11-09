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
import { diffFields, diffView, kindOf, railVersions, revisionOf } from "../src/lib/version-diff.ts"

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

/** A Quilhara trail's shape: a repeatable list of label and value pairs, beside plain fields. */
const TRAIL_SCHEMA = [
  { name: "name", label: "Name", widget: "Text" },
  { name: "facts", label: "Facts", widget: "Relator", children: [
    { name: "label", label: "Label", widget: "Text" }, { name: "value", label: "Value", widget: "Text" }] },
  { name: "guide", label: "Guide", widget: "Multi Select" },
]

test("SH1389: a repeatable field is one row per item and sub-field, never [object Object]", () => {
  const view = diffView({ from: "published", to: "draft", before: null,
    after: { name: "Pedra do Vigia", guide: ["/guides/a", "/guides/b"],
      facts: [{ label: "Distância", value: "6,5 km" }, { label: "Subida", value: "420 m" }] },
    fields: [{ name: "name", changed: true }, { name: "facts", changed: true }, { name: "guide", changed: true }] },
  TRAIL_SCHEMA)
  assert.deepEqual(view.fields.map((f) => [f.id, f.label]), [
    ["name", "Name"],
    ["facts.0.label", "Facts › 1 › Label"], ["facts.0.value", "Facts › 1 › Value"],
    ["facts.1.label", "Facts › 2 › Label"], ["facts.1.value", "Facts › 2 › Value"],
    ["guide", "Guide"],
  ], "SH1389: the diff no longer addresses a fact the way the editor does")
  assert.equal(view.before, null, "SH1389: a create grew a before side")
  assert.equal(view.after["facts.0.value"], "6,5 km")
  assert.deepEqual(view.after.guide, ["/guides/a", "/guides/b"], "SH1389: a plain list is still one value")
  const drawn = view.fields.map((f) => String(view.after[f.id] ?? ""))
  assert.ok(!drawn.some((each) => each.includes("[object Object]")), `SH1389: a row still draws ${drawn}`)
})

test("SH1389: an edit to one fact changes that row, and an item only one side has is added or removed", () => {
  const view = diffView({ from: "published", to: "draft",
    before: { facts: [{ label: "Distância", value: "6 km" }, { label: "Subida", value: "420 m" }] },
    after: { facts: [{ label: "Distância", value: "6,5 km" }] },
    fields: [{ name: "facts", changed: true }] }, TRAIL_SCHEMA)
  assert.deepEqual(view.fields.map((f) => f.id), ["facts.0.label", "facts.0.value", "facts.1.label", "facts.1.value"])
  assert.equal(view.before["facts.0.value"], "6 km")
  assert.equal(view.after["facts.0.value"], "6,5 km")
  assert.equal(view.before["facts.1.label"], "Subida")
  assert.equal(view.after["facts.1.label"], undefined, "SH1389: a removed fact still reads as present")
})

test("SH1389: both diff surfaces hand BentoDiff the flattened view", () => {
  const dialog = read("components", "post-versions.dialog.tsx")
  assert.match(dialog, /<BentoDiff before=\{view\.before\} after=\{view\.after\} fields=\{view\.fields\}/,
    "SH1389: the version dialog hands BentoDiff raw values, so a repeatable field reads [object Object]")
  assert.match(read("components", "review-entry-diff.tsx"),
    /<BentoDiff before=\{answer\.view\.before\} after=\{answer\.view\.after\} fields=\{answer\.view\.fields\}/,
    "SH1389: the review queue hands BentoDiff raw values, so a repeatable field reads [object Object]")
})
