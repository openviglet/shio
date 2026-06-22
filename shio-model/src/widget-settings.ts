/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */

/**
 * The widgetSettings codec — the heart of SH62. Translates the DSL's ergonomic,
 * typed settings to/from the exact opaque JSON string the backend stores, hiding
 * the legacy on-the-wire shapes (the `"id: Label\n…"` choices string, the `1`/`0`
 * ints, the single-key wrapper objects).
 *
 * Encoders are deterministic (stable key order) so the compiled JSON is
 * byte-stable for the SH16 diff. Every encoder has a symmetric decoder so a
 * starter `.ts` can be scaffolded from `pull`ed JSON (best-effort, SH64).
 */
import type {
  ChoiceSettings,
  FolderSettings,
  MultiSelectSettings,
} from "./types";

/** Object → JSON string, or `null` for empty settings (never the string "null"). */
function emit(obj: Record<string, unknown> | null): string | null {
  return obj === null ? null : JSON.stringify(obj);
}

/** Safe parse: `JSON.parse("null")` is `null`, not `{}` — guard it. */
function parse(raw: string | null): Record<string, unknown> {
  if (!raw) return {};
  try {
    const p = JSON.parse(raw);
    return p && typeof p === "object" ? (p as Record<string, unknown>) : {};
  } catch {
    return {};
  }
}

/* ------------------------------- choices ------------------------------- */

export function encodeChoices(s: ChoiceSettings): string {
  const choices = Object.entries(s.choices)
    .map(([id, label]) => `${id}: ${label}`)
    .join("\n");
  const out: Record<string, unknown> = { choices };
  if (s.allowNull !== undefined) out.allowNull = s.allowNull ? 1 : 0;
  return JSON.stringify(out);
}

export function decodeChoices(raw: string | null): ChoiceSettings {
  const p = parse(raw);
  const choices: Record<string, string> = {};
  for (const line of String(p.choices ?? "").split("\n")) {
    if (!line.trim()) continue;
    const idx = line.indexOf(":");
    if (idx < 0) continue;
    choices[line.slice(0, idx).trim()] = line.slice(idx + 1).trim();
  }
  const out: ChoiceSettings = { choices };
  if (p.allowNull !== undefined) out.allowNull = Boolean(p.allowNull);
  return out;
}

/* ------------------------- single-key wrappers ------------------------- */
// File / Content Select / Multi Select / Relator nest their settings under one
// key. `undefined` fields are dropped so equivalent inputs encode identically.

function compact<T extends Record<string, unknown>>(obj: T): Partial<T> {
  return Object.fromEntries(
    Object.entries(obj).filter(([, v]) => v !== undefined)
  ) as Partial<T>;
}

export function encodeFile(s: FolderSettings): string | null {
  const inner = compact({ folderId: s.folderId });
  return Object.keys(inner).length ? emit({ file: inner }) : null;
}

export function encodeContentSelect(s: FolderSettings): string | null {
  const inner = compact({ folderId: s.folderId });
  return Object.keys(inner).length ? emit({ contentSelect: inner }) : null;
}

export function encodeMultiSelect(s: MultiSelectSettings): string | null {
  const inner = compact({ postType: s.postType, folderId: s.folderId });
  return Object.keys(inner).length ? emit({ multiselect: inner }) : null;
}

export function encodeRelator(title: string | undefined): string | null {
  return title ? emit({ relator: { title } }) : null;
}

/* --------------------------- wrapper decoders -------------------------- */
// Symmetric to the encoders above: read one nested wrapper key back into the
// ergonomic settings shape. `undefined` is preserved (the field was absent) so
// re-encoding is byte-identical. Used to scaffold a starter `.ts` from `pull`ed
// JSON and to let SH64's diff compare apples to apples.

/** Read the inner object under a single wrapper key (e.g. `file`, `relator`). */
function inner(raw: string | null, key: string): Record<string, unknown> {
  const wrapper = parse(raw)[key];
  return wrapper && typeof wrapper === "object"
    ? (wrapper as Record<string, unknown>)
    : {};
}

/** Read a string field from a wrapper, or `undefined` when absent/non-string. */
function str(obj: Record<string, unknown>, key: string): string | undefined {
  const v = obj[key];
  return typeof v === "string" ? v : undefined;
}

export function decodeFile(raw: string | null): FolderSettings {
  return { folderId: str(inner(raw, "file"), "folderId") };
}

export function decodeContentSelect(raw: string | null): FolderSettings {
  return { folderId: str(inner(raw, "contentSelect"), "folderId") };
}

export function decodeMultiSelect(raw: string | null): MultiSelectSettings {
  const o = inner(raw, "multiselect");
  return { postType: str(o, "postType"), folderId: str(o, "folderId") };
}

export function decodeRelator(raw: string | null): { title?: string } {
  return { title: str(inner(raw, "relator"), "title") };
}
