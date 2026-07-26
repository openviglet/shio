/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
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

/** A published site. */
export interface ShioSite {
  id: string;
  name: string;
  description?: string;
  url?: string;
  rootFolderId?: string;
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
  /** Post-type attributes as a JSON object, keyed by attribute name. */
  attrs: Record<string, unknown>;
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
}

/** Paging parameters for listings. */
export interface ShioPageParams {
  page?: number;
  size?: number;
}
