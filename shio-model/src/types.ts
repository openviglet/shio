/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */

/**
 * The on-the-wire shapes — mirror the backend `ShPostTypeDef` /
 * `ShPostTypeAttrDef` records exactly (SH16). The DSL builders produce these;
 * the CLI pushes them verbatim. `widgetSettings` is the **opaque JSON string**
 * the backend stores — the DSL's job (see widget-settings.ts) is to never make a
 * human hand-write it.
 */
export interface ShPostTypeAttrDef {
  name: string;
  label: string | null;
  description: string | null;
  /** The widget **name** (e.g. "Text", "Combo Box", "Relator", "Tab"). */
  widget: string;
  required: boolean;
  isTitle: boolean;
  isSummary: boolean;
  ordinal: number;
  /** Verbatim backend JSON string, or `null`. Built by the codec, not by hand. */
  widgetSettings: string | null;
  /** Relator/Tab children. */
  fields: ShPostTypeAttrDef[];
}

export interface ShPostTypeDef {
  name: string;
  title: string | null;
  namePlural: string | null;
  description: string | null;
  workflowPublishEntity: string | null;
  /** Always `false` from the DSL — system types are backend-owned (push 409s). */
  system: boolean;
  fields: ShPostTypeAttrDef[];
}

/* ------------------------------------------------------------------ *
 * Authoring-side option types (ergonomic; the DSL surface)            *
 * ------------------------------------------------------------------ */

/** Flags shared by every field factory. `ordinal` is auto-assigned if omitted. */
export interface FieldOptions {
  label?: string;
  description?: string;
  required?: boolean;
  isTitle?: boolean;
  isSummary?: boolean;
  ordinal?: number;
}

/**
 * Check Box / Combo Box. `choices` is an **object** here — the codec serializes
 * it to the legacy `"id: Label\nid2: Label2"` string + `allowNull` as `1`/`0`.
 */
export interface ChoiceSettings {
  choices: Record<string, string>;
  allowNull?: boolean;
}

/** File / Content Select — a folder scope. */
export interface FolderSettings {
  folderId?: string;
}

/** Multi Select — a post-type + folder scope. */
export interface MultiSelectSettings {
  postType?: string;
  folderId?: string;
  /**
   * `"post"` declares that the values are **friendly URLs of posts**, not tags (SH154/SH169).
   *
   * A Multi Select is a free-form list of strings by design, and most of them are tags — so the
   * server cannot guess. Declaring it is what lets `shio verify` report a reference that resolves
   * to nothing and lets SH101's digest compose a page out of the sections it names. `postType`
   * may narrow which type is acceptable; the SH95 vocabulary leaves it open, because a page's
   * sections are deliberately heterogeneous.
   */
  references?: "post";
}

/** Relator — a titled container of nested fields. */
export interface RelatorSettings {
  title?: string;
  fields: ShPostTypeAttrDef[];
}

/** Top-level post-type options. */
export interface PostTypeOptions {
  title?: string;
  namePlural?: string;
  description?: string;
  workflowPublishEntity?: string;
  fields: ShPostTypeAttrDef[];
}
