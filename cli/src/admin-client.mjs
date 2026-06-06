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
    const headers = { Authorization: this.#authHeader(), Accept: "application/json" };
    const init = { method, headers };
    if (body !== undefined) {
      headers["Content-Type"] = "application/json";
      init.body = JSON.stringify(body);
    }
    const url = `${this.baseUrl}/api/v2/post-type${path}`;
    const res = await this.fetch(url, init);
    if (res.status === 404) return null;
    if (res.status === 204) return null;
    if (!res.ok) {
      let detail = "";
      try {
        detail = await res.text();
      } catch {
        /* ignore */
      }
      throw new ShioHttpError(res.status, url, detail);
    }
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
