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
    // Three renderings, three media types (SH115 added the third). Sending
    // application/json for a Markdown rendering would be a lie the server is
    // entitled to act on with a 406.
    const accept =
      { terse: "text/plain", "agents-md": "text/markdown" }[query.format] ?? "application/json";
    const url = `${this.baseUrl}/api/v2/agent/context${search ? `?${search}` : ""}`;
    const res = await this.fetch(url, {
      method: "GET",
      headers: { Authorization: this.#authHeader(), Accept: accept },
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

  /**
   * GET /api/v2/agent/resolve — turn a content address into an object.
   *
   * @param {string} address e.g. `post:Blog/blog/hello-world`
   */
  async resolve(address) {
    const url = `${this.baseUrl}/api/v2/agent/resolve?address=${encodeURIComponent(address)}`;
    const res = await this.fetch(url, {
      method: "GET",
      headers: { Authorization: this.#authHeader(), Accept: "application/json" },
    });
    if (!res.ok) {
      // The server's teaching problem (SH80) is the useful part of a failure here —
      // it names the allowed schemes or the sibling paths — so pass the body through
      // rather than collapsing it to a status code.
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

  /**
   * POST /api/v2/agent/plan — what applying this document would change. Writes nothing.
   *
   * @param {object} document a desired-state content document (site/folders/posts/prune)
   * @param {{ ids?: boolean }} [options]
   */
  async plan(document, options = {}) {
    return this.#post("plan", document, options);
  }

  /** POST /api/v2/agent/apply — make the document true, atomically. */
  async apply(document, options = {}) {
    return this.#post("apply", document, options);
  }

  async #post(verb, document, { ids = false } = {}) {
    const url = `${this.baseUrl}/api/v2/agent/${verb}${ids ? "?ids=true" : ""}`;
    const res = await this.fetch(url, {
      method: "POST",
      headers: {
        Authorization: this.#authHeader(),
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      body: JSON.stringify(document),
    });
    if (!res.ok) {
      // Same reasoning as `resolve`: the teaching problem (SH80) *is* the useful part
      // of a failure here — it names the op that broke and the fix — so the body
      // travels intact rather than being collapsed to a status code.
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

  /**
   * GET /api/v2/agent/verify (SH99/SH100) — the content lint report.
   *
   * Returns the parsed JSON, unlike `getContext`: the caller needs `ok` and the counts
   * to pick an exit code, and a text body would mean parsing prose to decide whether
   * the build fails. The terse rendering is for the model; the exit code is for CI.
   *
   * @param {{ site?: string, checks?: string, limit?: number|string }} [query]
   */
  async verify(query = {}) {
    return this.#get("verify", query);
  }

  /**
   * GET /api/v2/agent/render (SH101) — a page's structural digest.
   *
   * @param {{ address?: string, url?: string }} query exactly one of the two
   */
  async digest(query = {}) {
    return this.#get("render", query);
  }

  /** GET /api/v2/agent/diagnostics (SH103) — recent asynchronous failures. */
  async diagnostics(query = {}) {
    return this.#get("diagnostics", query);
  }

  /**
   * GET /api/v2/agent/find (SH83) — post addresses, never bodies.
   *
   * @param {{ site?: string, type?: string, folder?: string, q?: string, limit?: number }} [query]
   * @returns {Promise<{count: number, truncated: boolean, results: object[]}>}
   */
  async find(query = {}) {
    return this.#get("find", query);
  }

  /**
   * GET /api/v2/agent/read (SH83/SH123) — post content by address.
   *
   * Several addresses in one call, because the projection reads a whole site and one
   * request per post would make `pull --content` cost as much as the API-shaped authoring
   * it exists to replace.
   *
   * @param {string[]} addresses
   */
  async read(addresses) {
    const params = new URLSearchParams();
    for (const address of addresses) params.append("address", address);
    const url = `${this.baseUrl}/api/v2/agent/read?${params.toString()}`;
    const res = await this.fetch(url, {
      method: "GET",
      headers: { Authorization: this.#authHeader(), Accept: "application/json" },
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
    return res.json();
  }

  /**
   * GET /api/v2/agent/changes (SH88) — content transitions and the cursor to resume from.
   *
   * @param {{ site?: string, since?: string, limit?: number }} [query]
   */
  async changes(query = {}) {
    return this.#get("changes", query);
  }

  async #get(path, query) {
    const params = new URLSearchParams();
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== null && value !== "") {
        params.set(key, String(value));
      }
    }
    const search = params.toString();
    const url = `${this.baseUrl}/api/v2/agent/${path}${search ? `?${search}` : ""}`;
    const res = await this.fetch(url, {
      method: "GET",
      headers: { Authorization: this.#authHeader(), Accept: "application/json" },
    });
    if (!res.ok) {
      // Same reasoning as `resolve`: the SH80 teaching problem is the useful part of a
      // failure — it names the allowed check groups, or the property that enables
      // fetching — so the body travels intact.
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

  /**
   * GET /api/v2/agent/blueprints (SH92) — the appliable starting points.
   *
   * @returns {Promise<Array<{name: string, title: string, description: string,
   *   version: string, postTypes: number, posts: number, files: number, params: object}>>}
   */
  async blueprints() {
    return this.#get("blueprints", {});
  }

  /**
   * GET /api/v2/agent/blueprints/{name} — one package, with its `params` schema.
   *
   * @param {string} name
   * @param {{ files?: boolean }} [options] include the carried front-end file bodies
   */
  async blueprint(name, { files = false } = {}) {
    return this.#get(`blueprints/${encodeURIComponent(name)}`, files ? { include: "files" } : {});
  }

  /**
   * POST /api/v2/agent/blueprints/{name}/apply (SH93).
   *
   * @param {string} name
   * @param {{ site: string, params?: object, dryRun?: boolean }} body
   */
  async applyBlueprint(name, body) {
    return this.#post(`blueprints/${encodeURIComponent(name)}/apply`, body, {});
  }

  /**
   * GET /api/v2/agent/memory (SH114) — what this instance has been told to remember.
   *
   * @param {{ site?: string, limit?: number }} [query]
   * @returns {Promise<{found: number, notes: object[]}>}
   */
  async memory(query = {}) {
    return this.#get("memory", query);
  }

  /**
   * PUT /api/v2/agent/memory (SH114) — remember one decision, idempotently by key.
   *
   * The author is not sent: the server takes it from the credential. A caller that
   * could name the author could make its own inference look like a human's
   * instruction, and the attribution exists precisely to keep those apart.
   *
   * @param {{ key: string, note: string, scope?: string }} note
   */
  async remember(note) {
    return this.#send("PUT", "memory", note);
  }

  /**
   * DELETE /api/v2/agent/memory (SH114). Idempotent — forgetting what was never
   * remembered succeeds, so a retry is never a failure.
   *
   * @param {{ key: string, scope?: string }} target
   */
  async forget({ key, scope } = {}) {
    const params = new URLSearchParams({ key: String(key ?? "") });
    if (scope) params.set("scope", scope);
    return this.#send("DELETE", `memory?${params.toString()}`);
  }

  /** PUT/DELETE against the agent namespace, with the teaching body preserved. */
  async #send(method, path, body) {
    const url = `${this.baseUrl}/api/v2/agent/${path}`;
    const res = await this.fetch(url, {
      method,
      headers: {
        Authorization: this.#authHeader(),
        Accept: "application/json",
        ...(body === undefined ? {} : { "Content-Type": "application/json" }),
      },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
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
    // 204 on delete: there is no document to parse, and inventing one would make the
    // caller branch on a shape the server never promised.
    if (res.status === 204) return null;
    return res.json();
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
