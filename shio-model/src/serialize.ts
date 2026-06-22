/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */

/**
 * Canonical serialization — the SAME normalization the `shio` CLI uses
 * (`cli/src/post-types-io.mjs`, SH16): fixed key order, fields sorted by ordinal
 * (then name), stable defaults. Co-locating it here (and having the CLI import
 * it, per SH64) guarantees the DSL's compiled JSON is **byte-identical** to a
 * hand-written `shio/post-types/*.json` — there is exactly one serializer, so
 * the DSL can never disagree with the canonical bytes that drive `push`'s diff.
 */
import type { ShPostTypeAttrDef, ShPostTypeDef } from "./types";

function byOrdinalThenName(a: ShPostTypeAttrDef, b: ShPostTypeAttrDef): number {
  const o = (a.ordinal ?? 0) - (b.ordinal ?? 0);
  return o === 0 ? String(a.name ?? "").localeCompare(String(b.name ?? "")) : o;
}

function normalizeField(field: ShPostTypeAttrDef): ShPostTypeAttrDef {
  return {
    name: field.name,
    label: field.label ?? null,
    description: field.description ?? null,
    widget: field.widget,
    required: Boolean(field.required),
    isTitle: Boolean(field.isTitle),
    isSummary: Boolean(field.isSummary),
    ordinal: field.ordinal ?? 0,
    widgetSettings: field.widgetSettings ?? null,
    fields: (field.fields ?? []).slice().sort(byOrdinalThenName).map(normalizeField),
  };
}

/** Canonical, comparison-stable shape. Two defs are equal iff this is equal. */
export function normalizeDef(def: ShPostTypeDef): ShPostTypeDef {
  return {
    name: def.name,
    title: def.title ?? null,
    namePlural: def.namePlural ?? null,
    description: def.description ?? null,
    workflowPublishEntity: def.workflowPublishEntity ?? null,
    system: Boolean(def.system),
    fields: (def.fields ?? []).slice().sort(byOrdinalThenName).map(normalizeField),
  };
}

/**
 * Return the plain, canonical `ShPostTypeDef` a builder produced (§4.3) — a thin
 * alias for `normalizeDef` so callers that hold a DSL value can get the exact
 * object `serializeDef` would write, e.g. to feed SH64's `diffDefs` directly.
 */
export function toDef(def: ShPostTypeDef): ShPostTypeDef {
  return normalizeDef(def);
}

/** Deterministic, pretty JSON for a def — what `pull` writes / `build` emits. */
export function serializeDef(def: ShPostTypeDef): string {
  return `${JSON.stringify(normalizeDef(def), null, 2)}\n`;
}
