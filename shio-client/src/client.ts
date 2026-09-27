/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
import { ShioHttpError } from "./errors";
import type {
  ShioClientConfig,
  ShioListing,
  ShioPageParams,
  ShioPath,
  ShioPost,
  ShioPostTypeSchema,
  ShioQueryParams,
  ShioReadOptions,
  ShioRedirect,
  ShioRequestOptions,
  ShioSite,
} from "./types";

const CDA_PREFIX = "/api/v2/cda";

/**
 * Framework-agnostic client for the Shio Content Delivery API (CDA, SH1).
 *
 * <p>Zero runtime dependencies; uses the global `fetch` (override via
 * {@link ShioClientConfig.fetch} for other runtimes or testing). Single-resource
 * getters return `null` on 404 and throw {@link ShioHttpError} on any other
 * non-OK status. Safe to instantiate once and share (e.g. a module singleton in
 * a Next.js app).</p>
 *
 * @example
 * const shio = new ShioClient({ baseUrl: "https://cms.example.com", apiToken: "..." });
 * const post = await shio.getPostByUrl(siteId, "/blog/hello");
 */
export class ShioClient {
  private readonly baseUrl: string;
  private readonly headers: Record<string, string>;
  private readonly fetchFn: typeof fetch;
  private readonly credentials?: RequestCredentials;

  constructor(config: ShioClientConfig) {
    if (!config?.baseUrl) {
      throw new Error("ShioClient: `baseUrl` is required");
    }
    this.baseUrl = config.baseUrl.replace(/\/+$/, "");
    this.headers = { Accept: "application/json", ...(config.headers ?? {}) };
    if (config.apiToken) {
      this.headers["Key"] = config.apiToken;
    }
    const resolvedFetch = config.fetch ?? globalThis.fetch;
    if (typeof resolvedFetch !== "function") {
      throw new Error(
        "ShioClient: no global `fetch` available — pass `fetch` in the config"
      );
    }
    // Bind to avoid `Illegal invocation` when fetch is detached from its receiver.
    this.fetchFn = resolvedFetch.bind(globalThis);
    this.credentials = config.credentials;
  }

  // --- Sites ---

  /** List all published sites. */
  getSites(opts?: ShioRequestOptions): Promise<ShioSite[]> {
    return this.request<ShioSite[]>("/site", opts);
  }

  /** Get one site (with its root folder id), or `null` if not found. */
  getSite(siteId: string, opts?: ShioRequestOptions): Promise<ShioSite | null> {
    return this.requestOrNull<ShioSite>(`/site/${seg(siteId)}`, opts);
  }

  /**
   * A site's redirect table (SH1133), ordered by `from`, or `null` if the site is unknown.
   *
   * Small and site-scoped, so a front end can hold it and consult it before a 404 rather than
   * asking per request. A production token sees published redirects only.
   */
  getRedirects(siteId: string, opts?: ShioRequestOptions): Promise<ShioRedirect[] | null> {
    return this.requestOrNull<ShioRedirect[]>(`/site/${seg(siteId)}/redirects`, opts);
  }

  // --- Navigation ---

  /** List a folder's children (subfolders + published posts), or `null` if the folder is unknown. */
  listChildren(
    folderId: string,
    params?: ShioPageParams,
    opts?: ShioRequestOptions
  ): Promise<ShioListing | null> {
    const qs = buildQuery({
      page: params?.page, size: params?.size, locale: params?.locale, state: params?.state,
    });
    return this.requestOrNull<ShioListing>(
      `/object/${seg(folderId)}/list${qs}`,
      opts
    );
  }

  /** Get the breadcrumb path for a folder or post, or `null` if unknown. */
  getPath(objectId: string, opts?: ShioRequestOptions): Promise<ShioPath | null> {
    return this.requestOrNull<ShioPath>(`/object/${seg(objectId)}/path`, opts);
  }

  // --- Posts ---

  /**
   * Get a published post by id (attributes expanded), or `null` if not found / not published.
   *
   * SH1081 — `opts.state` asks for another version: `"draft"` or `"rev:<n>"` with a preview token.
   */
  getPost(postId: string, opts?: ShioReadOptions): Promise<ShioPost | null> {
    const qs = buildQuery({ state: opts?.state });
    return this.requestOrNull<ShioPost>(`/post/${seg(postId)}${qs}`, opts);
  }

  /**
   * Resolve a published post by site + friendly URL — the call behind catch-all
   * routing (e.g. Next.js `[...slug]`). Returns `null` if unresolved.
   *
   * SH1057 — pass a `locale` as the third argument to resolve only the translation
   * in that language: `getPostByUrl(siteId, "/about", "pt-br")` is `null` when the
   * post at `/about` is in another language, and its `availableLocales` says which.
   *
   * SH1081 — `opts.state` asks for another version, as on {@link getPost}: a draft preview of
   * one page is `getPostByUrl(siteId, "/about", { state: "draft" })` with a preview token.
   */
  getPostByUrl(siteId: string, url: string, opts?: ShioReadOptions): Promise<ShioPost | null>;
  getPostByUrl(
    siteId: string,
    url: string,
    locale: string,
    opts?: ShioReadOptions
  ): Promise<ShioPost | null>;
  getPostByUrl(
    siteId: string,
    url: string,
    localeOrOpts?: string | ShioReadOptions,
    opts?: ShioReadOptions
  ): Promise<ShioPost | null> {
    const locale = typeof localeOrOpts === "string" ? localeOrOpts : undefined;
    const options = typeof localeOrOpts === "string" ? opts : localeOrOpts;
    const qs = buildQuery({ siteId, url, locale, state: options?.state });
    return this.requestOrNull<ShioPost>(`/post/by-url${qs}`, options);
  }

  // --- Schema (SH11) ---

  /**
   * List every post-type's content-model schema — the input to
   * {@link generateShioTypes}. The schema is global (not site-scoped), so a
   * per-site token sees every post-type.
   */
  getPostTypes(opts?: ShioRequestOptions): Promise<ShioPostTypeSchema[]> {
    return this.request<ShioPostTypeSchema[]>("/post-type", opts);
  }

  /** Get one post-type's content-model schema by its API name, or `null` if unknown. */
  getPostType(
    name: string,
    opts?: ShioRequestOptions
  ): Promise<ShioPostTypeSchema | null> {
    return this.requestOrNull<ShioPostTypeSchema>(`/post-type/${seg(name)}`, opts);
  }

  /** Query published posts by site / folder / post type (always returns a listing). */
  query(params?: ShioQueryParams, opts?: ShioRequestOptions): Promise<ShioListing> {
    const qs = buildQuery({
      siteId: params?.siteId,
      folderId: params?.folderId,
      postType: params?.postType,
      page: params?.page,
      size: params?.size,
      locale: params?.locale,
      state: params?.state,
    });
    return this.request<ShioListing>(`/query${qs}`, opts);
  }

  // --- internals ---

  private async send(path: string, opts?: ShioRequestOptions): Promise<Response> {
    return this.fetchFn(`${this.baseUrl}${CDA_PREFIX}${path}`, {
      method: "GET",
      headers: this.headers,
      signal: opts?.signal,
      ...(this.credentials ? { credentials: this.credentials } : {}),
    });
  }

  private async request<T>(path: string, opts?: ShioRequestOptions): Promise<T> {
    const res = await this.send(path, opts);
    if (!res.ok) {
      throw new ShioHttpError(res.status, path, await safeText(res));
    }
    return (await res.json()) as T;
  }

  private async requestOrNull<T>(
    path: string,
    opts?: ShioRequestOptions
  ): Promise<T | null> {
    const res = await this.send(path, opts);
    if (res.status === 404) {
      return null;
    }
    if (!res.ok) {
      throw new ShioHttpError(res.status, path, await safeText(res));
    }
    return (await res.json()) as T;
  }
}

/** Convenience factory mirroring `new ShioClient(config)`. */
export function createShioClient(config: ShioClientConfig): ShioClient {
  return new ShioClient(config);
}

/** Encode a single path segment. */
function seg(value: string): string {
  return encodeURIComponent(value);
}

/** Build a `?a=b&c=d` string, skipping `undefined`/`null`/empty values. */
function buildQuery(
  params: Record<string, string | number | undefined | null>
): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== "") {
      search.append(key, String(value));
    }
  }
  const qs = search.toString();
  return qs ? `?${qs}` : "";
}

async function safeText(res: Response): Promise<string | undefined> {
  try {
    return await res.text();
  } catch {
    return undefined;
  }
}
