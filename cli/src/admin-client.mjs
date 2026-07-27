/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
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
   * GET /api/v2/staticfile — the media inventory (SH62's listing, SH145's index).
   *
   * Each item carries the id, the file name and the containing folder's id, which is
   * exactly what an asset path is built from.
   */
  listStaticFiles({ siteId } = {}) {
    const query = siteId ? `?siteId=${encodeURIComponent(siteId)}` : "";
    return this.#at(`/api/v2/staticfile${query}`, "GET");
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

  /** DELETE /api/v2/post-type/{name}. */
  deletePostTypeDef(name) {
    return this.#request("DELETE", `/${encodeURIComponent(name)}`);
  }
}
