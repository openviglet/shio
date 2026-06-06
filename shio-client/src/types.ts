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
  description: string | null;
  url: string | null;
  rootFolderId: string | null;
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
  title: string | null;
  summary: string | null;
  furl: string | null;
  postType: string | null;
  date: string | null;
  modifiedDate: string | null;
}

/** A full post with its post-type attributes expanded into {@link attrs}. */
export interface ShioPost {
  id: string;
  title: string | null;
  summary: string | null;
  furl: string | null;
  postType: string | null;
  siteId: string | null;
  folderId: string | null;
  published: boolean;
  date: string | null;
  modifiedDate: string | null;
  publicationDate: string | null;
  /** Post-type attributes as a JSON object, keyed by attribute name. */
  attrs: Record<string, unknown>;
}

/** Folder children: subfolders plus a paged slice of published posts. */
export interface ShioListing {
  folderId: string | null;
  folders: ShioFolder[];
  posts: ShioPostSummary[];
  page: number;
  size: number;
  totalPosts: number;
}

/** Breadcrumb path for a folder or post. */
export interface ShioPath {
  site: ShioSite | null;
  /** Ordered root → ... → current. */
  breadcrumb: ShioFolder[];
  currentFolder: ShioFolder | null;
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
