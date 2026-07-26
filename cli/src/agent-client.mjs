/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * Minimal client for the Shio agent surface (Block H, `/api/v2/agent/**`).
 *
 * Console Basic auth, like `ShioAdminClient` and for the same reason: the agent
 * surface reports the *authoring* view of an instance (drafts, the full model,
 * managed flags), so it is an admin read, not a delivery read. A CDA `Key` token
 * also authenticates there, but the CLI already carries console credentials for
 * pull/push and reusing them keeps `shio context` a one-flag command.
 *
 * Deliberately not folded into ShioAdminClient: that client hard-codes the
 * `/api/v2/post-type` prefix into its request path, and widening it to an
 * arbitrary namespace would make a post-type client pretend to be a general one.
 */
import { ShioHttpError } from "@viglet/shio-client";

export class ShioAgentClient {
  /**
   * @param {object} config
   * @param {string} config.baseUrl   Shio base URL (trailing slash tolerated).
   * @param {string} config.user      Console username.
   * @param {string} config.password  Console password.
   * @param {typeof fetch} [config.fetch] Override (testing / non-global-fetch runtimes).
   */
  constructor({ baseUrl, user, password, fetch: fetchImpl } = {}) {
    if (!baseUrl) throw new Error("ShioAgentClient: baseUrl is required");
    this.baseUrl = String(baseUrl).replace(/\/+$/, "");
    this.user = user;
    this.password = password;
    this.fetch = fetchImpl ?? globalThis.fetch;
    if (typeof this.fetch !== "function") {
      throw new Error("ShioAgentClient: no fetch implementation available");
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

  /**
   * GET /api/v2/agent/context.
   *
   * @param {object} [query] site / depth / budget / include / from / format
   * @returns {Promise<{ body: string, terse: boolean, estTokens: number|null }>}
   *   The body is returned as **text**, not parsed: `format=terse` is not JSON, and
   *   for the JSON form the CLI's job is to hand the bytes to a file or a pipe
   *   unchanged. Parsing and re-serializing would reformat a payload whose exact
   *   bytes are the point (its digest is computed over them).
   */
  async getContext(query = {}) {
    const params = new URLSearchParams();
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== null && value !== "") {
        params.set(key, String(value));
      }
    }
    const search = params.toString();
    const terse = query.format === "terse";
    const url = `${this.baseUrl}/api/v2/agent/context${search ? `?${search}` : ""}`;
    const res = await this.fetch(url, {
      method: "GET",
      headers: {
        Authorization: this.#authHeader(),
        Accept: terse ? "text/plain" : "application/json",
      },
    });
    if (!res.ok) {
      let detail = "";
      try {
        detail = await res.text();
      } catch {
        /* ignore */
      }
      throw new ShioHttpError(res.status, url, detail);
    }
    const body = await res.text();
    const header = res.headers?.get?.("x-shio-est-tokens");
    return { body, terse, estTokens: header ? Number(header) : null };
  }

  /** GET /api/v2/agent/manifest. */
  async getManifest() {
    const url = `${this.baseUrl}/api/v2/agent/manifest`;
    const res = await this.fetch(url, {
      method: "GET",
      headers: { Authorization: this.#authHeader(), Accept: "application/json" },
    });
    if (!res.ok) {
      throw new ShioHttpError(res.status, url, "");
    }
    return res.json();
  }
}
