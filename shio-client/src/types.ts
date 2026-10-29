/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */

/**
 * TypeScript mirror of the Shio Content Delivery API (CDA) read contract (SH1,
 * `/api/v2/cda/**`). Field names track the backend DTO records
 * (`com.viglet.shio.api.cda.dto`) one-for-one — they are a frozen public
 * contract; do not rename.
 *
 * **Optional, not nullable (SH127).** A field the CMS has no value for is *absent*
 * from the JSON rather than present as `null`, so it is declared `field?: T` here.
 * These two halves shipped together and had to: the backend omits the null because
 * `"url": null` costs bytes and tokens to say nothing, and this file declares it
 * optional because a consumer written against `string | null` would otherwise start
 * receiving `undefined` from a comparison it believed was exhaustive. Narrow with a
 * falsy check (`if (post.title)`) rather than `=== null`.
 */

/** Configuration for {@link ShioClient}. */
export interface ShioClientConfig {
  /**
   * Base URL of the Shio instance, e.g. `"https://cms.example.com"`. A trailing
   * slash is tolerated. The `/api/v2/cda` prefix is appended automatically.
   */
  baseUrl: string;
  /** CDA token, sent as the `Key` request header on every call. */
  apiToken?: string;
  /**
   * Custom `fetch` implementation — for non-global-fetch runtimes, testing, or
   * instrumentation. Defaults to the global `fetch`.
   */
  fetch?: typeof fetch;
  /** Extra headers merged into every request (lowest precedence). */
  headers?: Record<string, string>;
  /**
   * Forwarded to `fetch` as `credentials`. Omitted by default — the CDA uses
   * token auth, not cookies. Set to `"include"` only for cookie-based setups.
   */
  credentials?: RequestCredentials;
}

/** Per-request options. */
export interface ShioRequestOptions {
  /** Abort signal for cancellation (e.g. React effect cleanup). */
  signal?: AbortSignal;
}

/**
 * The version of content a CDA read asks for (SH1081, over SH990's parameter).
 *
 * `"published"` is the default and any token may read it. `"draft"` and `"rev:<n>"` need a
 * preview token, and without one the read throws {@link ShioHttpError} with status 403 and the
 * problem slug `state-needs-preview-token`. A string rather than a union, so the server stays the
 * one place that says which values exist.
 */
export type ShioContentState = string;

/** Per-request options for a single-post read: {@link ShioRequestOptions} plus a state. */
export interface ShioReadOptions extends ShioRequestOptions {
  /** See {@link ShioContentState}; omitted, the published version. */
  state?: ShioContentState;
}

/** A published site. */
export interface ShioSite {
  id: string;
  name: string;
  description?: string;
  url?: string;
  rootFolderId?: string;
  /**
   * The languages this site is in (SH1123) — what a language switcher is built from.
   * Present on {@link ShioClient.getSite} and {@link ShioClient.getSites}; absent on the
   * `site` a {@link ShioPath} carries.
   */
  locales?: ShioSiteLocales;
}

/**
 * A site's locale policy (SH1123). Pair it with {@link ShioPost.availableLocales}: the
 * site says which languages it offers, the post says which of them this page exists in.
 */
export interface ShioSiteLocales {
  /** The locale the site is written in — always one of {@link enabled}. */
  defaultLocale: string;
  /** Every locale the site offers, in the order a switcher lists them. */
  enabled: string[];
  /**
   * Locale to show instead when a page is missing in the one asked for, e.g.
   * `{ "pt-br": "pt" }`. A locale with no entry falls back to {@link defaultLocale}.
   */
  fallback: Record<string, string>;
  /**
   * `true` when somebody chose this policy. `false` when it was read off the published
   * content, in which case {@link enabled} lists what has been translated so far rather
   * than what the site promises.
   */
  declared: boolean;
  /**
   * What the site's language switcher calls each locale (SH1287), e.g.
   * `{ "pt-br": "Português (Brasil)" }`. Absent when the site set no labels; a locale
   * with no entry keeps its usual name.
   */
  labels?: Record<string, string>;
}

/**
 * One row of a site's redirect table (SH1133), from {@link ShioClient.getRedirects}.
 *
 * A front end that serves the site's URLs itself consults this before answering 404: the
 * instance's own renderer honours these, and a headless site never reaches it.
 */
export interface ShioRedirect {
  /** The site-relative path that moved, e.g. `/pricing`. */
  from: string;
  /** Where it moved: a site-relative path, or an absolute URL as written. */
  to: string;
  /** `true` for a 301, `false` for a 302 — the default, because a wrong 301 is cached for ever. */
  permanent: boolean;
}

/** A folder (listing item / breadcrumb node). */
export interface ShioFolder {
  id: string;
  name: string;
  root: boolean;
}

/** A post listing item — no heavy attribute payload. */
export interface ShioPostSummary {
  id: string;
  title?: string;
  summary?: string;
  furl?: string;
  postType?: string;
  /** BCP-47 locale of this row (SH52), absent on single-language content. */
  locale?: string;
  date?: string;
  modifiedDate?: string;
}

/** A full post with its post-type attributes expanded into {@link attrs}. */
export interface ShioPost {
  id: string;
  title?: string;
  summary?: string;
  furl?: string;
  postType?: string;
  siteId?: string;
  folderId?: string;
  published: boolean;
  date?: string;
  modifiedDate?: string;
  publicationDate?: string;
  /** BCP-47 locale of this post (SH52), absent on single-language content. */
  locale?: string;
  /**
   * Every locale this post is translated into, including {@link locale} — the
   * list a language switcher renders from (SH52). Always present; `[]` on a
   * post with no translation group.
   */
  availableLocales: string[];
  /** Post-type attributes as a JSON object, keyed by attribute name. */
  attrs: Record<string, unknown>;
  /**
   * SH1016 — field name to rendered HTML, for this post's `Blocks` fields.
   *
   * Beside {@link attrs}, never instead of it: a site that maps block types to its own
   * components reads the array out of `attrs`, and one that has been dropping a body into a
   * div reads this. Absent for a post with no block field, which is every post written
   * before the widget existed.
   */
  rendered?: Record<string, string>;
}

/**
 * SH1016 — one block of a `Blocks` field, discriminated by {@link ShioBlock.type}.
 *
 * TypeScript narrows on `type`, so `switch (block.type)` gives each case its own fields with
 * no cast — which is the whole reason the body is an array of these rather than a string.
 * A `key` is the block's identity across a reorder, not its position.
 *
 * Marks are ranges over the block's own `text`, not nested spans: `<b><i>x</i></b>` and
 * `<i><b>x</b></i>` are one content with two spellings, and a range has one.
 */
export interface ShioBlockMark {
  from: number;
  to: number;
  type: "strong" | "em" | "code" | "link" | "strike";
  /** Present on a `link` mark. */
  href?: string;
}

interface ShioBlockBase {
  key: string;
}

export type ShioBlock =
  | (ShioBlockBase & { type: "heading"; level: number; text?: string; marks?: ShioBlockMark[] })
  | (ShioBlockBase & { type: "paragraph"; text?: string; marks?: ShioBlockMark[] })
  | (ShioBlockBase & { type: "quote"; text?: string; marks?: ShioBlockMark[] })
  | (ShioBlockBase & { type: "list"; ordered?: boolean; items?: string[] })
  | (ShioBlockBase & { type: "code"; language?: string; text?: string })
  | (ShioBlockBase & { type: "image"; src?: string; alt?: string })
  | (ShioBlockBase & { type: "embed"; url?: string })
  | (ShioBlockBase & { type: "reference"; address?: string });

/** The registered block types, as the server refuses anything outside them. */
export const SHIO_BLOCK_TYPES = [
  "heading", "paragraph", "quote", "list", "code", "image", "embed", "reference",
] as const;

/**
 * Reads a post's block field as blocks, or `null` when it is not one.
 *
 * A helper rather than a cast, because the honest answer for a field that holds a string is
 * "this is not a blocks field" — and a consumer that casts finds that out by rendering
 * nothing.
 */
export function shioBlocks(post: ShioPost, field: string): ShioBlock[] | null {
  const value = post.attrs?.[field];
  return Array.isArray(value) ? (value as ShioBlock[]) : null;
}

/** Folder children: subfolders plus a paged slice of published posts. */
export interface ShioListing {
  folderId?: string;
  folders: ShioFolder[];
  posts: ShioPostSummary[];
  page: number;
  size: number;
  totalPosts: number;
}

/** Breadcrumb path for a folder or post. */
export interface ShioPath {
  site?: ShioSite;
  /** Ordered root → ... → current. */
  breadcrumb: ShioFolder[];
  currentFolder?: ShioFolder;
}

/**
 * One field of a post-type's content model (SH11). Recursive: a Relator field
 * carries its item sub-fields in {@link fields}. Mirrors the backend
 * `ShCdaSchemaField` record one-for-one.
 */
export interface ShioFieldSchema {
  name: string;
  label?: string;
  description?: string;
  /** The Shio widget name (e.g. `"Text"`, `"Relator"`), or `null`. */
  widget?: string;
  required: boolean;
  /**
   * The delivered value is an array — a `Check Box` / `Multi Select` value, or a
   * Relator's item list (in which case {@link fields} is non-empty).
   */
  multiple: boolean;
  isTitle: boolean;
  isSummary: boolean;
  ordinal: number;
  /** Sub-fields, for a Relator; empty for scalar fields. */
  fields: ShioFieldSchema[];
}

/**
 * A post-type's content model (SH11): its API {@link name} plus the ordered
 * fields a post of this type exposes under {@link ShioPost.attrs}. Mirrors the
 * backend `ShCdaPostTypeSchema` record. Feed an array of these to
 * {@link generateShioTypes} to emit `.d.ts` declarations.
 */
export interface ShioPostTypeSchema {
  name: string;
  title?: string;
  namePlural?: string;
  description?: string;
  fields: ShioFieldSchema[];
}

/** Parameters for {@link ShioClient.query}. */
export interface ShioQueryParams {
  siteId?: string;
  folderId?: string;
  postType?: string;
  page?: number;
  size?: number;
  /** Only posts written in this locale (SH1057), e.g. `"pt-br"`; omitted, every locale. */
  locale?: string;
  /** SH1081 — which version of each post; see {@link ShioContentState}. */
  state?: ShioContentState;
}

/** Paging parameters for listings. */
export interface ShioPageParams {
  page?: number;
  size?: number;
  /** Only posts written in this locale (SH1057); a folder's subfolders are listed whatever it is. */
  locale?: string;
  /** SH1081 — which version of each post; see {@link ShioContentState}. */
  state?: ShioContentState;
}
