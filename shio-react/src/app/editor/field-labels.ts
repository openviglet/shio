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
 * The page keeps sending the page's type as the fallback, which is what answers when the selection
 * names no post at all: labels degrade to SH303's behaviour rather than the panel going blank.
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
export function labelSource(selection: Selection | null | undefined): LabelSource {
  // SH335 — no `hasToken` argument any more. The post was readable only with a WRITE token, so the
  // right source was chosen for whoever had minted one and silently not for everyone else. The
  // console reads its own post on the session, so the first choice is now always available.
  const postId = selection?.postId?.trim();
  if (postId) return { kind: "post", postId };
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

/**
 * A declared field, as an `ShPostAttr` the console's widget map can render (SH1037).
 *
 * The panel drew every field as a bare `<Input>` for an attribute field and read-only text for the
 * rest, so a `Date` was a string, a `Check Box` was `"1"` and a `Content Select` was a UUID. The
 * console already owns a widget per kind — `getWidget(name)` — and already has the declared field in
 * hand, because it fetches the type to resolve labels. What was missing is the shape those widgets
 * take.
 *
 * Synthesised rather than fetched: the bridge sends a field's VALUE and the type declares its SHAPE,
 * and asking the server for the post as well would be a third read of something both halves already
 * know. The ids are the field name, which is what the widgets key on and what makes two renders of
 * the same field the same element.
 *
 * `strValue` carries the value for every kind. A widget that means something else — a date, a
 * number, an array — reads the string it is given and parses it, which is what the post form's own
 * widgets already do with a fresh attribute.
 */
export function attrFromDeclared(field: DeclaredWidgetField, value: string): SynthesisedAttr {
  return {
    id: field.name,
    strValue: value ?? "",
    arrayValue: [],
    dateValue: "",
    intValue: 0,
    shPostTypeAttr: {
      id: field.name,
      name: field.name,
      label: field.label ?? field.name,
      description: field.description ?? "",
      ordinal: field.ordinal ?? 0,
      required: field.required ? 1 : 0,
      isSummary: 0,
      isTitle: 0,
      shWidget: { name: field.widget ?? "Text" },
      widgetSettings: field.widgetSettings,
    },
  };
}

/** The half of a declared field this synthesis reads. */
export type DeclaredWidgetField = {
  name: string;
  label?: string | null;
  description?: string | null;
  widget?: string | null;
  required?: boolean | null;
  ordinal?: number | null;
  widgetSettings?: string;
};

/** What {@link attrFromDeclared} produces — structurally an `ShPostAttr`. */
export type SynthesisedAttr = {
  id: string;
  strValue: string;
  arrayValue: string[];
  dateValue: string;
  intValue: number;
  shPostTypeAttr: {
    id: string;
    name: string;
    label: string;
    description: string;
    ordinal: number;
    required: number;
    isSummary: number;
    isTitle: number;
    shWidget: { name: string };
    widgetSettings?: string;
  };
};
