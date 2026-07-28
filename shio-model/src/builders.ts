/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */

/**
 * The DSL surface (SH62/SH63): `postType()` plus one factory per the 14 Shio
 * widgets. Each factory returns a plain `ShPostTypeAttrDef`; `postType()` wraps
 * fields into a `ShPostTypeDef`, auto-assigning ordinals by array position.
 *
 *   import { postType, text, comboBox, relator } from "@viglet/shio-model";
 *
 *   export default postType("News", {
 *     title: "News",
 *     fields: [
 *       text("title", { isTitle: true, required: true }),
 *       comboBox("category", { choices: { tech: "Tech", biz: "Business" } }),
 *       relator("authors", { title: "Authors", fields: [ text("name") ] }),
 *     ],
 *   });
 */
import type {
  ChoiceSettings,
  FieldOptions,
  FolderSettings,
  MultiSelectSettings,
  PostTypeOptions,
  ShPostTypeAttrDef,
  ShPostTypeDef,
} from "./types";
import {
  encodeChoices,
  encodeContentSelect,
  encodeFile,
  encodeMultiSelect,
  encodeRelator,
} from "./widget-settings";

/** Widget **names** exactly as stored in the DB / expected by the admin API. */
export const WIDGET = {
  TEXT: "Text",
  TEXT_AREA: "Text Area",
  HTML_EDITOR: "HTML Editor",
  CHECK_BOX: "Check Box",
  COMBO_BOX: "Combo Box",
  DATE: "Date",
  HIDDEN: "Hidden",
  MULTI_SELECT: "Multi Select",
  ACE_JS: "Ace Editor - Javascript",
  ACE_HTML: "Ace Editor - HTML",
  ACE_CSS: "Ace Editor - CSS",
  CONTENT_SELECT: "Content Select",
  FILE: "File",
  RELATOR: "Relator",
  TAB: "Tab",
} as const;

/** Fold the shared `FieldOptions` flags + widget + settings into an attr def. */
function attr(
  widget: string,
  name: string,
  opts: FieldOptions = {},
  widgetSettings: string | null = null,
  fields: ShPostTypeAttrDef[] = []
): ShPostTypeAttrDef {
  return {
    name,
    label: opts.label ?? null,
    description: opts.description ?? null,
    widget,
    required: Boolean(opts.required),
    isTitle: Boolean(opts.isTitle),
    isSummary: Boolean(opts.isSummary),
    // 0 is a sentinel for "unset" — postType() reassigns by position.
    ordinal: opts.ordinal ?? 0,
    widgetSettings,
    fields,
  };
}

/* ----------------------------- scalar widgets ---------------------------- */

export const text = (name: string, opts?: FieldOptions) =>
  attr(WIDGET.TEXT, name, opts);

export const textArea = (name: string, opts?: FieldOptions) =>
  attr(WIDGET.TEXT_AREA, name, opts);

export const htmlEditor = (name: string, opts?: FieldOptions) =>
  attr(WIDGET.HTML_EDITOR, name, opts);

export const date = (name: string, opts?: FieldOptions) =>
  attr(WIDGET.DATE, name, opts);

export const hidden = (name: string, opts?: FieldOptions) =>
  attr(WIDGET.HIDDEN, name, opts);

export const aceJs = (name: string, opts?: FieldOptions) =>
  attr(WIDGET.ACE_JS, name, opts);

export const aceHtml = (name: string, opts?: FieldOptions) =>
  attr(WIDGET.ACE_HTML, name, opts);

/* --------------------------- settings-bearing ---------------------------- */

export const checkBox = (name: string, opts: FieldOptions & ChoiceSettings) =>
  attr(WIDGET.CHECK_BOX, name, opts, encodeChoices(opts));

export const comboBox = (name: string, opts: FieldOptions & ChoiceSettings) =>
  attr(WIDGET.COMBO_BOX, name, opts, encodeChoices(opts));

export const file = (name: string, opts: FieldOptions & FolderSettings = {}) =>
  attr(WIDGET.FILE, name, opts, encodeFile(opts));

export const contentSelect = (
  name: string,
  opts: FieldOptions & FolderSettings = {}
) => attr(WIDGET.CONTENT_SELECT, name, opts, encodeContentSelect(opts));

export const multiSelect = (
  name: string,
  opts: FieldOptions & MultiSelectSettings = {}
) => attr(WIDGET.MULTI_SELECT, name, opts, encodeMultiSelect(opts));

/* ------------------------------- containers ------------------------------ */

/** Relator — a titled container; children go in `opts.fields` (recurses). */
export const relator = (
  name: string,
  opts: FieldOptions & { title?: string; fields: ShPostTypeAttrDef[] }
) =>
  attr(
    WIDGET.RELATOR,
    name,
    opts,
    encodeRelator(opts.title),
    withOrdinals(opts.fields)
  );

/** Tab — a UI grouping container; no settings, no data. */
export const tab = (name: string, opts?: FieldOptions) =>
  attr(WIDGET.TAB, name, opts);

/* ------------------------------- assembly -------------------------------- */

/** Assign 1-based ordinals by array position where unset (0). */
function withOrdinals(fields: ShPostTypeAttrDef[]): ShPostTypeAttrDef[] {
  return fields.map((f, i) => ({
    ...f,
    ordinal: f.ordinal || i + 1,
    fields: withOrdinals(f.fields),
  }));
}

/** Build a `ShPostTypeDef` from a name + options. `system` is always false. */
export function postType(name: string, opts: PostTypeOptions): ShPostTypeDef {
  return {
    name,
    title: opts.title ?? null,
    namePlural: opts.namePlural ?? null,
    description: opts.description ?? null,
    workflowPublishEntity: opts.workflowPublishEntity ?? null,
    system: false,
    fields: withOrdinals(opts.fields),
  };
}
