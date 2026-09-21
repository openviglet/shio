/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import type { ShNote } from "@/models/note/note.model"
import type { ShPostTypeSchemaField } from "@/models/post-unified/post-unified"

/** SH1061 — the note kind a per-field review mark is written as. */
export const REVIEWED = "reviewed"

/**
 * SH1061 — what the two panes of the translation workspace agree on.
 *
 * The rules live here rather than in the page because they are the claims worth asserting: which
 * fields the two sides show and in what order, what "copy the source's value" does to the document
 * being edited, and how a value that is not a string reads on a pane that has no widgets. A page
 * can be looked at; these can be run.
 */

/**
 * The fields both panes show, in the post-type's own order.
 *
 * Aligned by `ordinal` because that is what makes the panes readable as a pair: the third row on
 * the left is the third row on the right, whatever either language put in it. `Tab` is dropped —
 * it is a divider in the editor's form, and a divider in one pane and not the other is exactly the
 * drift that pulls the two sides out of step.
 */
export function pairedFields(schema: ShPostTypeSchemaField[]): ShPostTypeSchemaField[] {
  return [...schema]
    .filter((field) => field.widget !== "Tab")
    .sort((a, b) => a.ordinal - b.ordinal)
}

/**
 * The target document with one field taken from the source.
 *
 * A new object rather than a mutation, and one field rather than a merge: copying the title of a
 * page must not quietly restore a paragraph the translator deleted. A source that does not carry
 * the field clears it, which is what "this field is the same in both languages" means when the
 * source's is empty.
 */
export function copyFromSource(
  data: Record<string, unknown>,
  source: Record<string, unknown> | undefined,
  field: string,
): Record<string, unknown> {
  return { ...data, [field]: source?.[field] }
}

/**
 * A source value as the left pane shows it: text, never a widget.
 *
 * The pane is read-only *by construction* — there is no input to click into — so every value has
 * to survive as text. An object or a list is shown as its JSON rather than as `[object Object]`,
 * which is what a reader needs in order to copy it across knowingly.
 */
export function sourceText(value: unknown): string {
  if (value === null || value === undefined) return ""
  if (typeof value === "string") return value
  if (typeof value === "number" || typeof value === "boolean") return String(value)
  return JSON.stringify(value, null, 2)
}

/**
 * SH1061 — the fields somebody has marked as checked against the source.
 *
 * Open marks only, and a mark is one note per field: resolving it is how a field stops being
 * reviewed, which is the same gesture every other note has. A mark with no field is about the
 * page rather than a row of it, and is not a row's mark.
 */
export function reviewedFields(notes: ShNote[]): Set<string> {
  const marked = new Set<string>()
  for (const note of notes) {
    if (!note.resolved && note.kind?.toLowerCase() === REVIEWED && note.fieldPath) {
      marked.add(note.fieldPath)
    }
  }
  return marked
}
