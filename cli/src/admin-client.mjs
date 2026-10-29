/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * Minimal HTTP Basic-auth client for the Shio console post-type admin API
 * (SH16, `/api/v2/post-type`). Editing the content model is an admin act, so —
 * unlike the CDA delivery client (`@viglet/shio-client`, `Key` token) — this
 * authenticates with the console username/password. Zero-dep; uses global fetch.
 */
import { ShioHttpError } from "@viglet/shio-client";

/** A failed response's body, or "" — never the reason the error itself is lost. */
async function bodyOf(res) {
  try {
    return await res.text();
  } catch {
    return "";
  }
}

export class ShioAdminClient {
  /**
   * @param {object} config
   * @param {string} config.baseUrl   Shio base URL (trailing slash tolerated).
   * @param {string} config.user      Console username.
   * @param {string} config.password  Console password.
   * @param {typeof fetch} [config.fetch] Override (testing / non-global-fetch runtimes).
   */
  constructor({ baseUrl, user, password, fetch: fetchImpl } = {}) {
    if (!baseUrl) throw new Error("ShioAdminClient: baseUrl is required");
    this.baseUrl = String(baseUrl).replace(/\/+$/, "");
    this.user = user;
    this.password = password;
    this.fetch = fetchImpl ?? globalThis.fetch;
    if (typeof this.fetch !== "function") {
      throw new Error("ShioAdminClient: no fetch implementation available");
    }
  }

  #authHeader() {
    const raw = `${this.user ?? ""}:${this.password ?? ""}`;
    const encoded =
      typeof Buffer !== "undefined"
        ? Buffer.from(raw, "utf8").toString("base64")
        : btoa(raw);
    return `Basic ${encoded}`;
  }

  async #request(method, path, body) {
    return this.#at(`/api/v2/post-type${path}`, method, body);
  }

  /**
   * One call against any console path.
   *
   * CSRF is waived for this client because it carries no session cookie
   * (`ShStatelessRequestMatcher`), which is what lets the CLI and the browser share these
   * endpoints — so a bare Basic-auth POST is correct here and would not be from a browser.
   */
  async #at(path, method, body) {
    const headers = { Authorization: this.#authHeader(), Accept: "application/json" };
    const init = { method, headers };
    if (body !== undefined) {
      headers["Content-Type"] = "application/json";
      init.body = JSON.stringify(body);
    }
    const url = `${this.baseUrl}${path}`;
    const res = await this.fetch(url, init);
    if (res.status === 404) return null;
    if (res.status === 204) return null;
    if (!res.ok) {
      throw new ShioHttpError(res.status, url, await bodyOf(res));
    }
    return res.json();
  }

  /**
   * SH1018 — what a post-type's own content does not satisfy, counted.
   *
   * A read, so it needs nothing a `shio post-type` call does not already carry.
   */
  checkPostType(name) {
    return this.#at(`/api/v2/post-type/${encodeURIComponent(name)}/check`, "GET");
  }

  /**
   * SH1018 — apply a model change to the content it governs.
   *
   * `dryRun` defaults to true on the server, so a caller that forgets the flag plans rather than
   * writes. That is the direction a mistake should go for an op that rewrites every post of a type.
   */
  migratePostType(name, mappings, { dryRun = true } = {}) {
    return this.#at(
      `/api/v2/post-type/${encodeURIComponent(name)}/migrate?dryRun=${dryRun}`,
      "POST", mappings ?? {});
  }

  /**
   * GET /api/v2/staticfile — the media inventory (SH62's listing, SH145's index).
   *
   * Each item carries the id, the file name and the containing folder's id, which is
   * exactly what an asset path is built from.
   *
   * SH1030 — the endpoint answers a page in an envelope now, and this **walks it to the end**.
   *
   * Every caller here is resolving asset references, and a short answer is not a smaller
   * resolution but one with holes: an unresolved reference stays an id in a `File` field, and
   * `push --content --prune` reads a hole as "delete this". Same rule as `findAll` on the agent
   * client, and the same reason.
   *
   * It reads a bare array too, which is what an instance older than SH1030 answers with — the
   * conformance suite drives this CLI against instances it did not build.
   */
  async listStaticFiles(options = {}) {
    return (await this.listStaticFilesWithLoads(options)).items;
  }

  /**
   * The same walk, with `loads=true` when asked (SH1280): each row gains `loadedBy`, the pages
   * whose rendered output requests it, and the answer says how many pages the server rendered to
   * know (`pagesRendered` of `pagesTotal`). Both are null from an instance that predates it.
   */
  async listStaticFilesWithLoads({ siteId, folderId, size = 200, maxPages = 500, loads = false } = {}) {
    // SH213 — `folderId` is the server's own filter (SH62), and it is what turns "read the whole
    // media library to resolve one reference" into "read the folder the reference names".
    const items = [];
    let pagesRendered = null;
    let pagesTotal = null;
    for (let page = 0; page < maxPages; page += 1) {
      const params = new URLSearchParams();
      if (folderId) params.set("folderId", folderId);
      else if (siteId) params.set("siteId", siteId);
      params.set("page", String(page));
      params.set("size", String(size));
      if (loads) params.set("loads", "true");
      const answer = await this.#at(`/api/v2/staticfile?${params}`, "GET");
      if (Array.isArray(answer)) {
        // An instance with no paging answered everything it has; asking for page 1 would return
        // the same rows again, so this is the whole answer and the walk is over.
        return { items: answer, pagesRendered, pagesTotal };
      }
      const batch = answer?.items ?? [];
      items.push(...batch);
      // Each page of the listing renders the sites its own rows belong to, so the least complete
      // answer is the one that bounds what a zero means.
      if (typeof answer?.pagesRendered === "number" && typeof answer?.pagesTotal === "number") {
        const short = answer.pagesTotal - answer.pagesRendered;
        if (pagesRendered === null || short > pagesTotal - pagesRendered) {
          pagesRendered = answer.pagesRendered;
          pagesTotal = answer.pagesTotal;
        }
      }
      // The total is what says whether there is more, rather than a short page: a page shorter
      // than `size` is also what the LAST page looks like, and stopping on that alone would
      // stop one page early whenever the library divides evenly.
      if (!batch.length || items.length >= (answer?.totalFiles ?? items.length)) {
        break;
      }
    }
    return { items, pagesRendered, pagesTotal };
  }

  /**
   * GET /api/v2/console/summary — the instance in one answer (SH1050).
   *
   * Counts, what is waiting for a person, and the most recent trail rows. `shio status` prints the
   * same rows the console's home draws, off the same endpoint: an agent orienting itself and a
   * curator opening the dashboard are asking the same question, and two implementations of it
   * would drift within a release.
   */
  async consoleSummary() {
    return this.#at("/api/v2/console/summary", "GET");
  }

  /** GET /api/v2/staticfile/{id} — the bytes, as a `Uint8Array`. */
  async downloadStaticFile(id) {
    const url = `${this.baseUrl}/api/v2/staticfile/${encodeURIComponent(id)}`;
    const res = await this.fetch(url, {
      method: "GET",
      headers: { Authorization: this.#authHeader() },
    });
    if (!res.ok) throw new ShioHttpError(res.status, url, await bodyOf(res));
    return new Uint8Array(await res.arrayBuffer());
  }

  /**
   * POST /api/v2/staticfile/upload — create a `File` post and store its bytes (SH38).
   *
   * Multipart, because that is what the endpoint the console uses takes; sharing it is the
   * point (law P10) rather than adding a bytes-over-JSON path just for the CLI.
   */
  async uploadStaticFile({ folderId, fileName, bytes, contentType }) {
    const form = new FormData();
    form.append("file", new Blob([bytes], { type: contentType || "application/octet-stream" }), fileName);
    form.append("folderId", folderId);
    const url = `${this.baseUrl}/api/v2/staticfile/upload`;
    const res = await this.fetch(url, {
      method: "POST",
      // No Content-Type: fetch sets it with the multipart boundary, and overriding it with a
      // hand-written value is how a multipart upload silently arrives as an empty file.
      headers: { Authorization: this.#authHeader(), Accept: "application/json" },
      body: form,
    });
    if (!res.ok) throw new ShioHttpError(res.status, url, await bodyOf(res));
    return res.json();
  }

  /**
   * GET /api/v2/staticfile/{id}/digest — `{ id, fileName, size, sha256 }` (SH210).
   *
   * How `push --content` tells an edited asset from an untouched one. The server hashes its own
   * copy, so the answer is 64 characters rather than the file: comparing bytes here would mean
   * downloading every referenced asset on every push, because an *unchanged* asset is exactly the
   * one whose length matches.
   */
  staticFileDigest(id) {
    return this.#at(`/api/v2/staticfile/${encodeURIComponent(id)}/digest`, "GET");
  }

  /**
   * PUT /api/v2/staticfile/{id}/meta — an asset's own words (SH1032).
   *
   * The endpoint the console's Media Library dialog writes through and `asset.meta` merges
   * through, which is why `push --content` uses it rather than a document write: one writer means
   * a blank clears on every surface, and the audit row a curator sees is the same row whichever
   * of the three did it. The three keys are always sent, absent ones as empty, because that is
   * how a push removes a caption somebody deleted from the tree.
   */
  async updateStaticFileMeta(id, { alt = "", caption = "", tags = [] } = {}) {
    return this.#at(`/api/v2/staticfile/${encodeURIComponent(id)}/meta`, "PUT", { alt, caption, tags });
  }

  /**
   * POST /api/v2/staticfile/{id}/replace — new bytes for an existing asset (SH210).
   *
   * The one operation `push --content` was missing: an upload always creates a post, so a path
   * the server already had was skipped and editing an asset's bytes did nothing. Identity stays
   * the server's — id, folder and file name are not arguments, because the last two *are* the
   * asset's address and a replace must not move it.
   */
  async replaceStaticFile({ id, fileName, bytes, contentType }) {
    const form = new FormData();
    form.append("file", new Blob([bytes], { type: contentType || "application/octet-stream" }), fileName);
    const url = `${this.baseUrl}/api/v2/staticfile/${encodeURIComponent(id)}/replace`;
    const res = await this.fetch(url, {
      method: "POST",
      headers: { Authorization: this.#authHeader(), Accept: "application/json" },
      body: form,
    });
    if (!res.ok) throw new ShioHttpError(res.status, url, await bodyOf(res));
    return res.json();
  }

  /**
   * POST /api/v2/import — clone an SH68 exchange package into this instance (SH268).
   *
   * Multipart for `uploadStaticFile`'s reason (P10: the endpoint the console uses is the endpoint),
   * and reachable at all only since SH189 narrowed its CSRF to session-bearing callers. The failure
   * bodies are worth surfacing verbatim: SH189/SH267 made them say which of four things went wrong,
   * with a `fix`, and swallowing that here would put the CLI back where the console was.
   */
  async importPackage({ bytes, fileName }) {
    const form = new FormData();
    form.append("file", new Blob([bytes], { type: "application/zip" }), fileName);
    const url = `${this.baseUrl}/api/v2/import`;
    const res = await this.fetch(url, {
      method: "POST",
      headers: { Authorization: this.#authHeader(), Accept: "application/json" },
      body: form,
    });
    if (!res.ok) throw new ShioHttpError(res.status, url, await bodyOf(res));
    return res.json();
  }

  /**
   * GET /api/v2/site/{id}/export (SH1065) — the exchange zip, with the name the server chose.
   *
   * The filename comes off `Content-Disposition` rather than being derived here: the server slugs
   * the site's own furl to build it, and a second slugger in this file is how an export written by
   * the CLI and one downloaded from the console come to be called different things.
   */
  async exportSite(id) {
    const url = `${this.baseUrl}/api/v2/site/${encodeURIComponent(id)}/export`;
    const res = await this.fetch(url, {
      method: "GET",
      headers: { Authorization: this.#authHeader() },
    });
    if (!res.ok) throw new ShioHttpError(res.status, url, await bodyOf(res));
    const disposition = res.headers?.get?.("content-disposition") ?? "";
    const named = /filename="?([^";]+)"?/u.exec(disposition);
    return { bytes: new Uint8Array(await res.arrayBuffer()), fileName: named?.[1] ?? null };
  }

  /** GET /api/v2/post-type — all lossless post-type defs. */
  getPostTypeDefs() {
    return this.#request("GET", "");
  }

  /** GET /api/v2/post-type/{name} — one def, or null if unknown. */
  getPostTypeDef(name) {
    return this.#request("GET", `/${encodeURIComponent(name)}`);
  }

  /** PUT /api/v2/post-type/{name} — upsert (create or replace). */
  putPostTypeDef(name, def) {
    return this.#request("PUT", `/${encodeURIComponent(name)}`, def);
  }

  /**
   * POST /api/v2/post-type/{name}/rename (SH163) — the only way to rename a post-type.
   *
   * `managed` is omitted deliberately: absent claims code ownership, which is what a `push` is,
   * and it is the same claim `putPostTypeDef` makes by omitting the key.
   */
  renamePostTypeDef(name, to) {
    return this.#request("POST", `/${encodeURIComponent(name)}/rename`, { to });
  }

  /** DELETE /api/v2/post-type/{name}. */
  deletePostTypeDef(name) {
    return this.#request("DELETE", `/${encodeURIComponent(name)}`);
  }

  // --- the package marketplace (SH677) ---

  /**
   * One marketplace call, with a 404 that throws rather than becoming `null`.
   *
   * `#at` maps 404 to `null` because on the post-type surface it means "no such type", which the
   * callers there treat as an answer. Here it is the opposite: the marketplace's 404s are its two
   * most instructive refusals — the feature is not enabled, and no entry has that id — and each
   * carries `fix` and `allowed` (P6). Swallowing them would turn the most useful message on this
   * surface into an empty result, which is the shape SH513 was filed about.
   */
  async #marketplace(path, method = "GET") {
    const url = `${this.baseUrl}/api/v2/marketplace${path}`;
    const res = await this.fetch(url, {
      method,
      headers: { Authorization: this.#authHeader(), Accept: "application/json" },
    });
    if (!res.ok) {
      throw new ShioHttpError(res.status, url, await bodyOf(res));
    }
    return res.status === 204 ? null : res.json();
  }

  /** GET /api/v2/marketplace — the catalogue, and where the list came from. */
  marketplace() {
    return this.#marketplace("");
  }

  /** GET /api/v2/marketplace/readme — one entry's README, by the URL the catalogue declared. */
  async marketplaceReadme(readmeUrl) {
    const url = `${this.baseUrl}/api/v2/marketplace/readme?url=${encodeURIComponent(readmeUrl)}`;
    const res = await this.fetch(url, {
      method: "GET",
      headers: { Authorization: this.#authHeader(), Accept: "text/plain" },
    });
    if (!res.ok) {
      throw new ShioHttpError(res.status, url, await bodyOf(res));
    }
    return res.text();
  }

  /** POST /api/v2/marketplace/{id}/install — admin-gated (SH677). */
  installPackage(id, { dryRun = false } = {}) {
    const query = dryRun ? "?dryRun=true" : "";
    return this.#marketplace(`/${encodeURIComponent(id)}/install${query}`, "POST");
  }

  /** DELETE /api/v2/marketplace/{id} — removes a package installed from a catalogue. */
  uninstallPackage(id) {
    return this.#marketplace(`/${encodeURIComponent(id)}`, "DELETE");
  }

  /** SH1111 — POST /api/v2/preview-token: the mint the console's Share button calls. */
  mintShare(postId, ttlSeconds) {
    return this.#at("/api/v2/preview-token", "POST", { postId, ...(ttlSeconds ? { ttlSeconds } : {}) });
  }

  /** SH1111 — GET /api/v2/preview-token: the live share links on sites this caller may read. */
  async shareLinks() {
    return (await this.#at("/api/v2/preview-token", "GET")) ?? [];
  }

  /** SH1111 — DELETE /api/v2/preview-token/{id}: stop one; null when there is none to stop. */
  revokeShare(id) {
    return this.#at(`/api/v2/preview-token/${encodeURIComponent(id)}`, "DELETE");
  }

  /** SH1108 — GET /api/v2/agent/patterns: what this instance holds, with each copy's digest. */
  async patterns() {
    return (await this.#at("/api/v2/agent/patterns", "GET")) ?? [];
  }

  /**
   * SH1108 — PUT /api/v2/agent/patterns/{name}, admin-gated.
   *
   * The file's text is sent as it is on disk and never re-serialized: the server stores the bytes
   * it receives, and the digest it answers is then the digest of this file, which is the whole of
   * how a stale copy is told from a current one.
   */
  async pushPattern(name, text) {
    const url = `${this.baseUrl}/api/v2/agent/patterns/${encodeURIComponent(name)}`;
    const res = await this.fetch(url, {
      method: "PUT",
      headers: {
        Authorization: this.#authHeader(), Accept: "application/json", "Content-Type": "application/json",
      },
      body: text,
    });
    if (!res.ok) {
      throw new ShioHttpError(res.status, url, await bodyOf(res));
    }
    return res.json();
  }
}
