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
export type DeclaredField = {
  name?: string | null
  label?: string | null
  widget?: string | null
  widgetSettings?: string | null
  /** A Relator's own fields, which is what labels one of its items' leaves (SH1417). */
  fields?: DeclaredField[] | null
};

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

/**
 * SH1417 — a Relator's leaf, as a template binds it: `facts.{{@index}}.label` reaches the bridge as
 * `facts.0.label`. Every other prop is not one, and parses to null.
 */
export type RelatorPath = { relator: string; index: number; child: string };

export function relatorPath(prop: string | null | undefined): RelatorPath | null {
  const match = /^([^.]+)\.(\d+)\.(.+)$/.exec(prop?.trim() ?? "");
  return match ? { relator: match[1], index: Number(match[2]), child: match[3] } : null;
}

/** What the panel needs to title a Relator's group, its items and their leaves. */
export type RelatorLabels = { label: string; itemTitle: string; children: Record<string, string> };

/**
 * The labels of a declared Relator, or null when the name declares none.
 *
 * The item title is `widgetSettings.relator.title`, which the post form already uses; the backend may
 * send the literal string `"null"`, so the parse is guarded the way every widgetSettings parse is. A
 * Relator that names no item title falls back to its own label, so an item still reads "Facts 1"
 * rather than "1".
 */
export function relatorLabels(fields: DeclaredField[] | null | undefined, relator: string): RelatorLabels | null {
  const field = (fields ?? []).find((candidate) => candidate?.name === relator);
  if (!field || field.widget !== "Relator") return null;
  const label = field.label?.trim() || relator;
  let itemTitle = "";
  try {
    const parsed = JSON.parse(field.widgetSettings ?? "null");
    const title = parsed && typeof parsed === "object" ? parsed.relator?.title : undefined;
    if (typeof title === "string") itemTitle = title.trim();
  } catch {
    // A settings string that is not JSON names no item title; the label stands in for it.
  }
  return { label, itemTitle: itemTitle || label, children: labelsFrom(field.fields) };
}

/** "Fact 2": the item's title and its 1-based position, which is what the page shows a reader. */
export function relatorItemTitle(labels: RelatorLabels, index: number): string {
  return `${labels.itemTitle} ${index + 1}`;
}

/**
 * The panel's label for any prop: the declared label for a top-level field, and
 * "Facts · Fact 1 · Label" for a Relator's leaf. Empty when nothing is declared, so the caller's
 * fallback (the bridge's label, then the prop) still answers.
 */
export function labelForProp(fields: DeclaredField[] | null | undefined, prop: string | null | undefined): string {
  if (!prop) return "";
  const path = relatorPath(prop);
  const relator = path ? relatorLabels(fields, path.relator) : null;
  if (path && relator) {
    const child = relator.children[path.child] ?? path.child;
    return `${relator.label} · ${relatorItemTitle(relator, path.index)} · ${child}`;
  }
  return labelsFrom(fields)[prop] ?? "";
}

/** One entry of the panel's list: a lone field, or a Relator's leaves gathered under it. */
export type PanelEntry<F> =
  | { kind: "field"; field: F }
  | { kind: "relator"; relator: string; labels: RelatorLabels; items: { index: number; fields: { child: string; field: F }[] }[] };

/**
 * SH1417 — the bridge's flat field list, with every Relator's leaves gathered into one entry.
 *
 * The group sits where its first leaf was, items are in index order and leaves keep the order the
 * page drew them in. A leaf whose head is not a declared Relator stays a lone field: guessing a group
 * from a dotted name alone would title it with an internal name, which is the defect.
 */
export function panelEntries<F extends { prop: string }>(
  fields: F[] | null | undefined, declared: DeclaredField[] | null | undefined,
): PanelEntry<F>[] {
  const entries: PanelEntry<F>[] = [];
  const groups = new Map<string, Extract<PanelEntry<F>, { kind: "relator" }>>();
  for (const field of fields ?? []) {
    const path = relatorPath(field.prop);
    const labels = path ? relatorLabels(declared, path.relator) : null;
    if (!path || !labels) {
      entries.push({ kind: "field", field });
      continue;
    }
    let group = groups.get(path.relator);
    if (!group) {
      group = { kind: "relator", relator: path.relator, labels, items: [] };
      groups.set(path.relator, group);
      entries.push(group);
    }
    let item = group.items.find((candidate) => candidate.index === path.index);
    if (!item) {
      item = { index: path.index, fields: [] };
      group.items.push(item);
    }
    item.fields.push({ child: path.child, field });
  }
  for (const group of groups.values()) group.items.sort((a, b) => a.index - b.index);
  return entries;
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
    // SH1393 — a Date widget reads `dateValue`, so a date seeded only as a string drew empty.
    dateValue: field.widget === "Date" ? value ?? "" : "",
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

/**
 * SH1393 — what a widget's change means as the field's value: the date for a Date widget, which
 * writes `dateValue`, and the string every other widget writes (an image's file id, a select's
 * choice, a reference's post id).
 */
export function valueFromWidget(field: DeclaredWidgetField, attr: { strValue?: string; dateValue?: string }): string {
  return field.widget === "Date" ? attr.dateValue ?? "" : attr.strValue ?? ""
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
