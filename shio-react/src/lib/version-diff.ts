/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import type { BentoDiffField, BentoVersion } from "@viglet/viglet-design-system/bento"
import type { ShPostDiff, ShPostTypeSchemaField, ShRevisionRow } from "../models/post-unified/post-unified.ts"

/**
 * SH988 — what the version dialog and the review queue hand `BentoDiff` and `BentoVersionRail`.
 *
 * <p>The comparison itself is the server's (`GET /post-unified/{id}/diff`). This only translates it
 * into the package's vocabulary, so the two surfaces cannot draw one diff two ways: the rail's
 * versions (the draft, the published page, then every captured revision), and each field's label
 * and how it is compared. Pure, so `version-diff.test.mjs` holds it without a browser.</p>
 */
export const DRAFT = "draft"
export const PUBLISHED = "published"

/** How the package compares a field: rich text by blocks, text by word, anything else whole. */
export function kindOf(widget: string | undefined): BentoDiffField["kind"] {
  const name = (widget ?? "").toLowerCase()
  if (name.includes("html") || name.includes("rich")) return "rich"
  if (name === "text" || name === "text area" || name === "textarea") return "text"
  return "value"
}

/**
 * The fields to draw, in the server's order, labelled from the schema. A name the schema does not
 * declare is still drawn under its own name: an undeclared field that changed is exactly what a
 * reviewer needs to see.
 */
export function diffFields(diff: ShPostDiff, schema: readonly ShPostTypeSchemaField[]): BentoDiffField[] {
  const byName = new Map(schema.map((field) => [field.name, field]))
  return diff.fields.map(({ name }) => {
    const declared = byName.get(name)
    return { id: name, label: declared?.label || name, kind: kindOf(declared?.widget) }
  })
}

/** What `BentoDiff` is handed: both sides and the rows to draw, every value a string or absent. */
export interface DiffView {
  before: Record<string, unknown> | null
  after: Record<string, unknown> | null
  fields: BentoDiffField[]
}

const isPlainObject = (value: unknown): value is Record<string, unknown> =>
  typeof value === "object" && value !== null && !Array.isArray(value)

/** A value holds structure when it is an object, or a list with an object in it. */
const isStructured = (value: unknown): boolean =>
  isPlainObject(value) || (Array.isArray(value) && value.some((item) => typeof item === "object" && item !== null))

/**
 * One value as leaf rows keyed by the editor's address (`facts.0.label`). A list of plain values
 * stays one row, its items joined, since the order is what a reader compares there.
 */
function leaves(value: unknown, path: string, out: Map<string, string>) {
  if (Array.isArray(value) && isStructured(value)) {
    value.forEach((item, index) => leaves(item, `${path}.${index}`, out))
  } else if (isPlainObject(value)) {
    for (const [key, inner] of Object.entries(value)) leaves(inner, `${path}.${key}`, out)
  } else if (Array.isArray(value)) {
    out.set(path, value.map((item) => String(item)).join(", "))
  } else if (value !== null && value !== undefined) {
    out.set(path, String(value))
  }
}

/**
 * SH1389 — the diff a reviewer reads, with a repeatable or object field drawn the way the editor
 * addresses it: one row per item and sub-field (`facts.0.label`), added, changed or removed like
 * any text field. `BentoDiff` draws a value whole, so a list of label and value pairs read as
 * "[object Object],[object Object]" in the one place an agent's structured mistake needs reading.
 * A row's label is the field's, the item's number from one, and the sub-field's own label.
 */
export function diffView(diff: ShPostDiff, schema: readonly ShPostTypeSchemaField[]): DiffView {
  const byName = new Map(schema.map((field) => [field.name, field]))
  const before: Record<string, unknown> | null = diff.before ? {} : null
  const after: Record<string, unknown> | null = diff.after ? {} : null
  const fields: BentoDiffField[] = []
  for (const { name } of diff.fields) {
    const declared = byName.get(name)
    const label = declared?.label || name
    const sides = [diff.before?.[name], diff.after?.[name]]
    if (!sides.some(isStructured)) {
      fields.push({ id: name, label, kind: kindOf(declared?.widget) })
      if (before) before[name] = diff.before?.[name]
      if (after) after[name] = diff.after?.[name]
      continue
    }
    const [was, now] = sides.map((value) => {
      const rows = new Map<string, string>()
      leaves(value, name, rows)
      return rows
    })
    const children = new Map((declared?.children ?? []).map((child) => [child.name, child]))
    const ids = [...new Set([...was.keys(), ...now.keys()])]
    for (const id of ids) {
      const parts = id.slice(name.length + 1).split(".")
      const leaf = children.get(parts.at(-1) ?? "")
      const steps = parts.map((part, at) => (/^\d+$/.test(part) ? String(Number(part) + 1)
        : at === parts.length - 1 && leaf ? leaf.label || part : part))
      fields.push({ id, label: [label, ...steps].join(" › "), kind: kindOf(leaf?.widget) })
      if (before && was.has(id)) before[id] = was.get(id)
      if (after && now.has(id)) after[id] = now.get(id)
    }
  }
  return { before, after, fields }
}

export interface RailLabels {
  draft: string
  published: string
  person: string
}

/** The rail, newest first: the working draft, the published page, then each revision. */
export function railVersions(revisions: readonly ShRevisionRow[], labels: RailLabels,
  times: { draft?: string; published?: string }): BentoVersion[] {
  const versions: BentoVersion[] = [
    { id: DRAFT, author: labels.draft, actor: "human", at: times.draft ?? Date.now() },
  ]
  if (times.published) versions.push({ id: PUBLISHED, author: labels.published, actor: "human", at: times.published })
  for (const row of [...revisions].sort((a, b) => b.version - a.version)) {
    versions.push({
      id: `rev:${row.version}`,
      author: row.actor || labels.person,
      actor: row.agentSession ? "agent" : "human",
      at: row.capturedAt,
      summary: row.changed?.length ? row.changed.join(", ") : row.reason,
    })
  }
  return versions
}

/** The revision number a side names, or null for the draft and the published page. */
export function revisionOf(side: string): number | null {
  const match = /^rev:(\d+)$/.exec(side)
  return match ? Number(match[1]) : null
}
