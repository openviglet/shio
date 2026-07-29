/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH305 — a field's labels belong to the **selected post's** type, not the page's.
 *
 * SH303 sends the page's `data-shio-post-type` with each selection and the console loads that
 * type's labels. Right for the common case, and wrong for a listing: a `{{#query}}` or
 * `{{#getRelation}}` block renders rows of *other* posts, each owned via `sh-post="{{id}}"`, so a
 * row's field was labelled from the page's schema — and when that schema has no such field the panel
 * falls back to the field name, which looks exactly like a type that declares no labels. Silent, and
 * wrong in the direction that looks fine.
 *
 * ## Why the console resolves it instead of the page sending it
 *
 * Both candidates were measured rather than chosen by taste.
 *
 * - **Emit `data-shio-post-type` per row.** The renderer knows it, and it is ~31 bytes on every
 *   listing row of every page — ~620 on a twenty-row listing — paid by every visitor, forever, for a
 *   case only the editor uses. That is SH121's trade about per-row ids, which went the other way.
 * - **Resolve it in the console.** §XVI.52 called this "one fetch it is going to make anyway", which
 *   was wrong: the console never called `getEditable` at all — it patches by id at save time, and
 *   that method sat in the service with no caller. But the fetch it needs *is* the one that answers
 *   the question: `GET /api/v2/cda/post/{id}` returns `{ post, fields }`, and those fields are the
 *   declared fields **of that post's own type**, labels included. One call per selected post, made
 *   when a curator clicks — a moment that is already a human round trip — and **zero bytes** for
 *   every visitor who never opens the editor.
 *
 * The page keeps sending the page's type as the fallback, which is what answers when no WRITE token
 * has been chosen: the editor cannot save without one anyway, so labels degrade to SH303's behaviour
 * rather than the panel going blank.
 *
 * These two functions are the decision and the mapping, kept pure so they can be tested (SH306 gave
 * this package a test runner). The effect that calls them cannot be — there is no React harness here,
 * and pretending otherwise would be worse than saying so.
 */

/** A declared field, as both `GET /api/v2/cda/post/{id}` and the post-type API report one. */
export type DeclaredField = { name?: string | null; label?: string | null };

/** Where a selection's labels come from. */
export type LabelSource =
  | { kind: "post"; postId: string }
  | { kind: "post-type"; postType: string }
  | { kind: "none" };

/** What the bridge reports as selected — only the two fields this decision reads. */
export type Selection = { postId?: string | null; postType?: string | null };

/**
 * Field name → declared label, skipping fields that declare none.
 *
 * A blank label is *absent* rather than empty: the panel falls back to the field name, and an empty
 * string would render as a missing label instead.
 */
export function labelsFrom(fields: DeclaredField[] | null | undefined): Record<string, string> {
  const labels: Record<string, string> = {};
  for (const field of fields ?? []) {
    const name = field?.name?.trim();
    const label = field?.label?.trim();
    if (name && label) labels[name] = label;
  }
  return labels;
}

/**
 * Which fetch answers this selection's labels.
 *
 * The post wins when it can be read, because it is the only source that is right for a row of
 * another type. The page's type is the fallback and never the first choice — being right about the
 * common case is what made this defect invisible.
 */
export function labelSource(selection: Selection | null | undefined,
                            hasToken: boolean): LabelSource {
  const postId = selection?.postId?.trim();
  if (hasToken && postId) return { kind: "post", postId };
  const postType = selection?.postType?.trim();
  if (postType) return { kind: "post-type", postType };
  return { kind: "none" };
}

/** A stable cache key, so selecting a second field of the same post refetches nothing. */
export function labelSourceKey(source: LabelSource): string {
  return source.kind === "post" ? `post:${source.postId}`
    : source.kind === "post-type" ? `type:${source.postType}`
      : "none";
}
