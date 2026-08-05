/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH448 — the harness for the conformance suite: an instance, a scratch site, and the
 * teaching failure you get instead of a skip.
 *
 * ## Why this is not a `test/` file
 *
 * Everything under `cli/test/` runs on `pnpm test` and must run with no instance, no
 * browser and no registry — that is what makes it fast enough to be run on every change.
 * This directory is the opposite trade and the glob is the whole opt-in: `npm test` is
 * `node --test "test/*.test.mjs"` and never sees `conformance/*.conformance.mjs`. Same
 * bargain as the ten `*IT.java` classes: you pay for it when the change is one only a live
 * boundary can falsify.
 *
 * ## Absent is a failure, never a skip
 *
 * `agents.md` records the shape this rule comes from: `ShPostgresSchemaIT` carries
 * `@Testcontainers(disabledWithoutDocker = true)`, so without a Docker daemon it reports
 * `0.00 s / Skipped` and "verify proved the schema on PostgreSQL" is simply false. A suite
 * whose whole purpose is to notice what the unit tests cannot must not have a mode in which
 * it notices nothing and says so quietly. So an unreachable instance is a **failed** run
 * carrying the command that fixes it — you asked for the conformance suite, and not running
 * it is not an answer to that request.
 */
import { ShioHttpError } from "@viglet/shio-client";

/** The scratch site's name prefix — grep-able, and what `stale()` looks for. */
export const SITE_PREFIX = "ShioConf";

/**
 * Where the instance is and who we are on it.
 *
 * The same three variables `shio` itself reads (`context.mjs`), because a conformance run is
 * a client of the product and inventing a fourth would test a path no user has.
 */
export function connection(env = process.env) {
  return {
    baseUrl: (env.SHIO_URL ?? "http://localhost:2710").replace(/\/+$/, ""),
    user: env.SHIO_USER ?? "admin",
    password: env.SHIO_PASSWORD ?? "admin",
  };
}

/** `SHIO_CONFORMANCE_KEEP=1` leaves the seeded site behind, for looking at what failed. */
export function keepsFixtures(env = process.env) {
  return /^(1|true|yes)$/i.test(String(env.SHIO_CONFORMANCE_KEEP ?? ""));
}

/**
 * Prove there is an instance before a test builds anything against it.
 *
 * The probe is `GET /api/v2/agent/manifest?include=identity` for two reasons: it is the
 * cheapest authenticated read on the surface this suite exercises, and its 401-vs-connection
 * -refused split is exactly the two ways the run can be misconfigured — so the message can
 * name the right fix instead of "something went wrong".
 */
export async function requireInstance(env = process.env) {
  const { baseUrl, user, password } = connection(env);
  let res;
  try {
    res = await fetch(`${baseUrl}/api/v2/agent/manifest?include=identity`, {
      headers: { Authorization: basic(user, password), Accept: "application/json" },
      signal: AbortSignal.timeout(10_000),
    });
  } catch (cause) {
    throw new Error(unreachable(baseUrl, cause), { cause });
  }
  if (res.status === 401 || res.status === 403) {
    throw new Error(
      `shio conformance: ${baseUrl} answered ${res.status} — the credentials are wrong.\n`
        + `  SHIO_USER=${user} SHIO_PASSWORD=*** (defaults: admin/admin)\n`
        + `This suite authenticates the way the CLI does, with console HTTP Basic.`,
    );
  }
  if (!res.ok) {
    throw new Error(`shio conformance: ${baseUrl} answered ${res.status} on the manifest probe.`);
  }
  const manifest = await res.json();
  return { baseUrl, user, password, manifest };
}

function unreachable(baseUrl, cause) {
  return (
    `shio conformance: no instance at ${baseUrl} (${cause?.message ?? cause}).\n\n`
    + `This suite is opt-in *because* it needs one — it asserts what the unit tests\n`
    + `structurally cannot see, which is the boundary between this code and a running\n`
    + `server. Start one and run it again:\n\n`
    + `  ./mvnw spring-boot:run -pl shio-app -Dskip.npm=true -DskipTests\n`
    + `  pnpm --filter @viglet/shio run conformance\n\n`
    + `Point it elsewhere with SHIO_URL / SHIO_USER / SHIO_PASSWORD.\n`
    + `It is a failure and not a skip on purpose: a run that asserts nothing must not\n`
    + `be reportable as a run that passed.`
  );
}

export function basic(user, password) {
  return `Basic ${Buffer.from(`${user ?? ""}:${password ?? ""}`, "utf8").toString("base64")}`;
}

/** A site name no other run owns, so two conformance runs never collide on one instance. */
export function scratchSite(tag) {
  const stamp = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;
  return `${SITE_PREFIX}${tag}${stamp}`;
}

/**
 * A console session that can do the two things the agent surface cannot: create a post-type
 * and delete a site.
 *
 * The post-type half is a plain Basic-auth call — `/api/v2/post-type` is CSRF-exempt for a
 * caller with no session cookie (`ShStatelessRequestMatcher`), which is the whole reason the
 * CLI can push a model. `/api/v2/site` is **not**, and there is no `site.delete` op, so
 * tearing down what this suite seeded means doing the browser's dance: read the token from
 * `GET /api/v2/csrf`, keep the `XSRF-TOKEN` cookie it sets, and echo it back on the write.
 *
 * That asymmetry is a finding this harness records rather than hides: an agent can create a
 * site (`site.upsert`) and cannot remove one. It is not this task's to fix.
 */
export class ConsoleSession {
  constructor({ baseUrl, user, password }) {
    this.baseUrl = String(baseUrl).replace(/\/+$/, "");
    this.auth = basic(user, password);
    this.cookies = new Map();
    this.token = null;
  }

  async #csrf() {
    if (this.token) return this.token;
    const res = await this.#fetch("/api/v2/csrf", { method: "GET" });
    if (!res.ok) throw new ShioHttpError(res.status, "/api/v2/csrf", await res.text());
    const body = await res.json();
    this.token = { value: body.token, header: body.headerName ?? "X-XSRF-TOKEN" };
    return this.token;
  }

  async #fetch(path, init = {}) {
    const headers = { ...(init.headers ?? {}), Authorization: this.auth, Accept: "application/json" };
    if (this.cookies.size) {
      headers.Cookie = [...this.cookies].map(([name, value]) => `${name}=${value}`).join("; ");
    }
    const res = await fetch(`${this.baseUrl}${path}`, { ...init, headers, redirect: "manual" });
    for (const line of res.headers.getSetCookie?.() ?? []) {
      const [pair] = line.split(";");
      const index = pair.indexOf("=");
      if (index > 0) this.cookies.set(pair.slice(0, index).trim(), pair.slice(index + 1).trim());
    }
    return res;
  }

  /** GET /api/v2/site/{key} — SH181's read resolves an id, a furl **or** a name. */
  async site(key) {
    const res = await this.#fetch(`/api/v2/site/${encodeURIComponent(key)}`, { method: "GET" });
    if (res.status === 404) return null;
    if (!res.ok) throw new ShioHttpError(res.status, `/api/v2/site/${key}`, await res.text());
    return res.json();
  }

  /**
   * Delete a site by name, and everything in it.
   *
   * Idempotent: a site that is not there is a clean teardown, not a failure — a test that
   * already failed must not fail a second time in its own `after`.
   *
   * **One call, and it was three — which is the second thing this suite found.**
   * `DELETE /api/v2/site/{id}` cascaded to the site's folders through `ShSite#shFolders` and
   * nothing cascaded to its posts, so on any site that held one it died inside the folder delete
   * with `Referential integrity constraint violation: sh_post_unified FOREIGN KEY (folder_id)`, a
   * body-less 500. That is every site a curator would actually delete, and no unit test saw it
   * because none of them deleted a site with content in it. This teardown worked around it by
   * trashing and purging the root folder itself and only then removing the site — three calls
   * standing in for the fix, and the reason the deletion was checkable at all.
   *
   * **SH454 moved those two steps into the site delete**, through `ShFolderWriteService#purge` —
   * the one verb that already gets the ordering right — so the teardown is one call again. Which
   * makes it the assertion rather than the workaround: if the cascade regresses, every scenario in
   * this suite fails in its own `after` with the 500 that started this.
   */
  async deleteSite(name) {
    const site = await this.site(name).catch(() => null);
    if (!site?.id) return false;
    const { value, header } = await this.#csrf();
    const res = await this.#fetch(`/api/v2/site/${encodeURIComponent(site.id)}`, {
      method: "DELETE",
      headers: { [header]: value },
    });
    return res.ok || res.status === 404;
  }
}
