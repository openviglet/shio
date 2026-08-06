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
import { execFile } from "node:child_process";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";

import { ShioHttpError } from "@viglet/shio-client";

const run = promisify(execFile);

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
 *
 * SH460 adds the third way, which is the one that cost a session: reachable, authenticated, and
 * running code older than the commits under test. That response now carries the build identity
 * to answer it — see `requireLiveBuild`.
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
  await requireLiveBuild(baseUrl, manifest, env);
  return { baseUrl, user, password, manifest };
}

/** `SHIO_CONFORMANCE_ALLOW_STALE=1` — for pointing this suite at an instance you did not build. */
function allowsStale(env) {
  return /^(1|true|yes)$/i.test(String(env.SHIO_CONFORMANCE_ALLOW_STALE ?? ""));
}

/**
 * SH460 — the first assertion of every run, and the only one that can name the *other* side.
 *
 * ## The run this exists to make readable
 *
 * Two of four conformance tests failed, both true statements about an instance that had been
 * running since 16:05:10 whose classes under test were compiled at 18:47:21 — after every Java
 * commit the run was validating. The CLI half was green in the same run, because it loads from
 * disk. Nothing said so. Telling a broken fix from a stale JVM took hand probes, `target/`
 * timestamps and the JVM's start time from the process table, and the cheaper reading — "SH453
 * and SH457 regressed" — was two defects that do not exist, which costs a whole session to
 * disprove.
 *
 * ## Why it asserts here rather than in a test
 *
 * A suite whose premise is the live boundary must fail on the boundary *first*. As a test it
 * would be one red line among several and the reader would still have to guess which failure
 * explains which; in `requireInstance` it fails before a single scenario builds anything, so
 * a stale instance produces one message instead of a plausible-looking defect report.
 *
 * ## What is compared: the commit first, and the timestamp only when it cannot answer (SH500)
 *
 * `builtAt` is when the *running* classes were produced (the instance captures it at startup, so
 * a recompile underneath a live JVM cannot flatter it) and HEAD's commit time is when the code
 * under test was written. Built-before-HEAD was the incident, and asserting it alone was SH500:
 * building precedes committing by seconds — compile, test, then commit — so on a clean tree at
 * HEAD the instance is dated *before* the commit it is running, and the gate cancelled the whole
 * suite on the ordinary case.
 *
 * A matching commit is proof, so it ends the question. `git.properties` names the commit the
 * artifact was built from, and on a clean tree HEAD's hash describes every byte the build could
 * have taken from git — same source, whatever the clocks say. The timestamp stays as the fallback
 * for the two cases a hash cannot cover:
 *
 * - the instance's commit is **not** HEAD — the incident, and still caught;
 * - the tree is **dirty**, where a matching hash no longer says what compiled. Note the normal
 *   dirty flow passes on the timestamp anyway: you edit, rebuild and restart, so `builtAt` is
 *   *after* the commit you started from.
 *
 * Dirty means a **tracked** modification (`git status --porcelain -uno`). An untracked file has no
 * version in HEAD for the hash to misdescribe, and counting one would put the gate back where
 * SH500 found it — a stray scratch file downgrading a current instance to the clock. The limit is
 * honest and narrow: an untracked `.java` *is* compiled by Maven, and this gate will not see it.
 *
 * An instance that reports **no** `builtAt` and no matching commit is stale by definition — both
 * fields ship with this assertion.
 */
async function requireLiveBuild(baseUrl, manifest, env) {
  if (allowsStale(env)) return;
  const head = await gitHead();
  if (!head) return; // No working tree to compare against — nothing to be stale *relative to*.

  const because = staleReason(manifest, head);
  if (because) {
    throw new Error(stale(baseUrl, head, because));
  }
}

/**
 * Why this instance is older than the code under test, or `null` when it is not (SH500).
 *
 * Exported for the test: the interesting cases are commit × tree, and only two of the four can be
 * produced by a real repository at the moment the suite happens to run.
 */
export function staleReason(manifest, head) {
  const atHead = commitMatchesHead(manifest?.commit, head);
  if (atHead && !head.dirty) return null;

  const builtAt = manifest?.builtAt ? Date.parse(manifest.builtAt) : Number.NaN;
  if (Number.isNaN(builtAt)) {
    return "it reports no build identity at all, so it predates SH460";
  }
  if (builtAt >= head.at) return null;

  const commit = manifest.commit ? ` (commit ${manifest.commit})` : "";
  const built = `it was built at ${manifest.builtAt}${commit}, before HEAD was committed`;
  return atHead
    ? `${built} — and the working tree has tracked changes, so a commit that matches HEAD`
      + ` no longer says what compiled`
    : built;
}

/**
 * Does the build's commit name HEAD?
 *
 * Both sides are abbreviated and not to the same width — `git.properties` writes seven characters
 * and `git log --format=%h` widens with the repository — so this compares the shorter prefix, and
 * refuses anything shorter than seven so a truncated field cannot match loosely.
 */
function commitMatchesHead(commit, head) {
  if (!commit || !head?.sha) return false;
  const build = String(commit).trim().toLowerCase();
  const sha = String(head.sha).trim().toLowerCase();
  const width = Math.min(build.length, sha.length);
  return width >= 7 && build.slice(0, width) === sha.slice(0, width);
}

/** HEAD's sha, commit time and whether the tree is dirty, or null when there is no git to ask. */
async function gitHead() {
  const repo = fileURLToPath(new URL("../../..", import.meta.url));
  try {
    const { stdout } = await run("git", ["-C", repo, "log", "-1", "--format=%h %cI"], {
      timeout: 10_000,
    });
    const [sha, iso] = stdout.trim().split(/\s+/);
    const at = Date.parse(iso);
    if (Number.isNaN(at)) return null;
    return { sha, iso, at, dirty: await gitDirty(repo) };
  } catch {
    return null;
  }
}

/** Tracked changes only — see `requireLiveBuild` for why untracked files do not count. */
async function gitDirty(repo) {
  try {
    const { stdout } = await run("git", ["-C", repo, "status", "--porcelain", "-uno"], {
      timeout: 10_000,
    });
    return stdout.trim().length > 0;
  } catch {
    return false; // Unaskable is not dirty: the commit comparison is the stronger signal anyway.
  }
}

function stale(baseUrl, head, because) {
  return (
    `shio conformance: the instance at ${baseUrl} is older than the code under test —\n`
    + `${because}.\n\n`
    + `  HEAD  ${head.sha}  ${head.iso}${head.dirty ? "  (tracked changes)" : ""}\n\n`
    + `This suite asserts two things at once — that the code is right, and that this\n`
    + `instance is running it — so a red run against a stale JVM reads as a defect in a\n`
    + `fix that already shipped. Rebuild and restart before drawing any conclusion:\n\n`
    + `  ./mvnw -pl shio-app -am clean install -Dskip.npm=true -DskipTests\n`
    + `  ./mvnw spring-boot:run -pl shio-app -Dskip.npm=true -DskipTests\n\n`
    + `Set SHIO_CONFORMANCE_ALLOW_STALE=1 if you meant to test an instance you did not build.`
  );
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
