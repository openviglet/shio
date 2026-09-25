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
