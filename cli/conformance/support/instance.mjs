/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
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
 *
 * <h2>SH764 — and then the net was too wide in two directions at once</h2>
 *
 * Both surviving triggers fired on a tree that could not possibly have changed the JVM. Measured:
 * the instance reported `commit 4b4b20e`, `git rev-parse HEAD` was `4b04b20e0`, and the only two
 * tracked changes were `docs/ROADMAP.md` and `docs/IMPROVEMENTS.md`, both written by roadkeep
 * minutes earlier. The gate cancelled the run.
 *
 * That is not a nuisance, it is a **rule collision**: findings are filed as roadkeep tasks and a red
 * suite is a stop (SH579), so the act of recording a finding disabled the gate that would confirm
 * it — and the only way past was to commit first and verify after, which is precisely the order the
 * gate exists to prevent. So both triggers are narrowed, and neither is loosened:
 *
 * - **Dirty is scoped to paths that compile** ({@link compiles}), so a docs edit is not a fact about
 *   the JVM. Conservative by construction: a path is inert only when it is provably inert.
 * - **The clock is the last resort, not the second one.** `builtAt` vs. the commit *time* fails on a
 *   build taken two minutes before a merge commit even where the two trees are byte-identical, which
 *   is a question about clocks and not about code. So the build's own commit is diffed against HEAD
 *   over the compiling paths first: no difference means the running classes are the classes under
 *   test, whatever the timestamps say.
 */
async function requireLiveBuild(baseUrl, manifest, env) {
  if (allowsStale(env)) return;
  const head = await gitHead();
  if (!head) return; // No working tree to compare against — nothing to be stale *relative to*.

  // SH764 — asked here rather than inside `staleReason` so that function stays pure and the four
  // commit × tree combinations remain assertable over a HEAD the test controls.
  const unchangedSince = await compiledPathsUnchanged(manifest?.commit);

  const because = staleReason(manifest, { ...head, unchangedSince });
  if (because) {
    throw new Error(stale(baseUrl, head, because));
  }
}

/**
 * Can a change to this path reach the running JVM? (SH764)
 *
 * <h3>Why this is a deny-list and a short one</h3>
 *
 * The two ways to be wrong are not symmetric. Too wide is the defect above — a false red, loud and
 * self-correcting. Too narrow is a **false green**: the gate silently stops noticing the one thing
 * it exists to notice, and every red run after it reads as a defect in a fix that already shipped.
 * So a path counts as compiling unless it is provably inert, and the list of provably-inert things
 * stays exactly as long as the evidence for it.
 *
 * <h3>The rule is a directory, not an extension — and that distinction is load-bearing</h3>
 *
 * `*.md` reads as the obvious spelling of "documentation" and it is wrong here: <b>seven markdown
 * files ship inside the jar</b> — `shio-app/src/main/resources/blueprints/&#42;/files/README.md` and
 * `js/server-side/README.md` — as blueprint carried files, asserted by
 * `ShBlueprintCarriedFilesTest`. An extension rule would exempt a packaged resource from the
 * staleness gate, which is the false green in its purest form. So: the `docs/` tree, and markdown
 * at the repository root (`README.md`, `agents.md`, `CLAUDE.md`). Nothing that lives under a
 * module.
 *
 * @param path a repository-relative path, as `git status --porcelain` and `git diff --name-only`
 *             both spell it (forward slashes on every platform)
 * @returns true when a change to it could have produced different classes
 */
export function compiles(path) {
  const at = String(path ?? "").trim().replace(/^"|"$/g, "");
  if (!at) return false;
  if (at === "docs" || at.startsWith("docs/")) return false;
  // Root-level markdown only: no slash before the extension, so a packaged README is untouched.
  if (/^[^/]+\.md$/i.test(at)) return false;
  return true;
}

/**
 * Are the compiling paths identical between the build's commit and HEAD? (SH764)
 *
 * <p>This is the "content rather than clock" half. A `null` means the question could not be asked —
 * no commit reported, or a commit this clone does not have — and the caller then falls back to the
 * timestamp, which is the behaviour every release before this one had.</p>
 *
 * @param commit the abbreviated commit the running artifact was built from, per `git.properties`
 * @returns true / false / null for unknown
 */
async function compiledPathsUnchanged(commit) {
  const from = String(commit ?? "").trim();
  if (!/^[0-9a-f]{7,40}$/i.test(from)) return null;
  const repo = fileURLToPath(new URL("../../..", import.meta.url));
  try {
    const { stdout } = await run("git", ["-C", repo, "diff", "--name-only", from, "HEAD"], {
      timeout: 10_000,
    });
    return !stdout.split("\n").map((line) => line.trim()).filter(Boolean).some(compiles);
  } catch {
    return null; // A commit we do not have says nothing either way.
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

  // SH764 — content before clock. The build's commit is not HEAD (or the tree is dirty), but every
  // path that could produce a class is identical between the two, so the running classes ARE the
  // classes under test. A merge commit that moved only documentation is the ordinary case here, and
  // judging it on `builtAt` cancelled the suite over two minutes of wall clock.
  if (head.unchangedSince && !head.dirty) return null;

  const builtAt = manifest?.builtAt ? Date.parse(manifest.builtAt) : Number.NaN;
  if (Number.isNaN(builtAt)) {
    return "it reports no build identity at all, so it predates SH460";
  }
  if (builtAt >= head.at) return null;

  const commit = manifest.commit ? ` (commit ${manifest.commit})` : "";
  const built = `it was built at ${manifest.builtAt}${commit}, before HEAD was committed`;
  return atHead
    ? `${built} — and the working tree has tracked changes under paths that compile, so a commit`
      + ` that matches HEAD no longer says what compiled`
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

/**
 * Tracked changes **to paths that compile** — see `requireLiveBuild` for both halves of why.
 *
 * <p>Untracked files do not count (SH500) and neither does documentation (SH764): the question is
 * whether the working tree could have produced classes other than the ones the instance is running,
 * and a roadmap line cannot.</p>
 */
async function gitDirty(repo) {
  try {
    const { stdout } = await run("git", ["-C", repo, "status", "--porcelain", "-uno"], {
      timeout: 10_000,
    });
    return changedPaths(stdout).some(compiles);
  } catch {
    return false; // Unaskable is not dirty: the commit comparison is the stronger signal anyway.
  }
}

/**
 * The paths out of `git status --porcelain` output (SH764).
 *
 * <p>Exported because the parsing is the part that can be quietly wrong: a rename is
 * `R  old -> new` and a path with a space is quoted, and either one mis-read turns a real code
 * change into a path that matches nothing — the false green {@link compiles} is written to avoid.
 * A rename yields <b>both</b> sides, since moving a `.java` out of a module changes what compiles
 * just as much as moving one in.</p>
 */
export function changedPaths(porcelain) {
  return String(porcelain ?? "")
    .split("\n")
    .map((line) => line.replace(/\r$/, ""))
    .filter((line) => line.trim().length > 0)
    // `XY ` — two status columns and a space, fixed width, before the path.
    .flatMap((line) => (line.length > 3 ? line.slice(3) : line.trim()).split(" -> "))
    .map((path) => path.trim().replace(/^"|"$/g, ""))
    .filter(Boolean);
}

function stale(baseUrl, head, because) {
  return (
    `shio conformance: the instance at ${baseUrl} is older than the code under test —\n`
    + `${because}.\n\n`
    + `  HEAD  ${head.sha}  ${head.iso}`
    + `${head.dirty ? "  (tracked changes under paths that compile)" : ""}\n\n`
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
   * The site's root folder id — the one folder that carries `shSite`, and the only handle the
   * console's own listing offers into a site's content.
   *
   * <p>SH713 needs it because the curator half of a concurrent scenario is addressed the way the
   * console addresses things — by id — while the agent half speaks SH79 addresses. Nothing on
   * `/api/v2/agent/**` returns an id for a post (that is the point of an address), so the bridge
   * between the two callers is this listing.</p>
   */
  async rootFolderId(siteName) {
    const site = await this.site(siteName);
    if (!site?.id) return null;
    const res = await this.#fetch(`/api/v2/object/${encodeURIComponent(site.id)}/list`, { method: "GET" });
    if (!res.ok) throw new ShioHttpError(res.status, `/api/v2/object/${site.id}/list`, await res.text());
    const body = await res.json();
    return body.folders?.find((folder) => folder.root)?.id ?? body.folders?.[0]?.id ?? null;
  }

  /** The posts directly in a folder, as the content browser lists them — `id` and `furl` included. */
  async postsIn(folderId) {
    const res = await this.#fetch(`/api/v2/object/${encodeURIComponent(folderId)}/list`, { method: "GET" });
    if (!res.ok) throw new ShioHttpError(res.status, `/api/v2/object/${folderId}/list`, await res.text());
    return (await res.json()).posts ?? [];
  }

  /**
   * `PATCH /api/v2/post-unified/{id}` — the curator's edit, as the Universal Editor makes it.
   *
   * <p>SH335's verb, and the one a concurrency scenario wants: it merges a few fields into the
   * draft rather than declaring the whole state, so a curator changing one field while an agent
   * rewrites another is a real interleaving rather than two whole-document writes. It needs the
   * browser's dance — CSRF token plus the cookie — because it is not on `ShStatelessRequestMatcher`.
   * A caller that sends Basic alone gets a bare 403, which is how the first draft of SH713's probe
   * mistook an authentication failure for a concurrency refusal.</p>
   *
   * @returns `{ status, body }` — the status matters, because on this surface a refusal is a result
   */
  async patchPost(id, attrs, { publish = false } = {}) {
    const { value, header } = await this.#csrf();
    const res = await this.#fetch(`/api/v2/post-unified/${encodeURIComponent(id)}`, {
      method: "PATCH",
      headers: { [header]: value, "Content-Type": "application/json" },
      body: JSON.stringify({ attrs, publish }),
    });
    const text = await res.text();
    let body;
    try {
      body = JSON.parse(text);
    } catch {
      body = text;
    }
    return { status: res.status, body };
  }

  /**
   * `POST /api/v2/staticfile/upload` — a real asset, with the stamps only an upload writes (SH1032).
   *
   * <p>The one fixture `asset.meta` genuinely needs an instance for. An in-memory `File` post can
   * be given a `data` document that *looks* like an upload's, and every merge assertion against it
   * is then a claim about a string the test wrote. Here `stampBytes` writes SHA256, SIZE and
   * CONTENT_TYPE from bytes on disk and the dimension reader writes WIDTH and HEIGHT from the PNG
   * header — so "the stamps survive a metadata merge" is asserted about stamps the product
   * produced, which is the only version of that claim worth making.</p>
   *
   * <p>Multipart, and therefore not something MCP can do: tool arguments are JSON, which is exactly
   * why `shio_assets` says uploading is not a tool. So the console credential does this half and
   * the agent surface does the rest, which is also the honest shape of the workflow it models.</p>
   *
   * @returns the created `File` post's id — the `id:` address `asset.meta` takes
   */
  async uploadAsset(folderId, fileName, bytes, contentType = "image/png") {
    const { value, header } = await this.#csrf();
    const form = new FormData();
    form.append("file", new Blob([bytes], { type: contentType }), fileName);
    form.append("folderId", folderId);
    const res = await this.#fetch("/api/v2/staticfile/upload", {
      method: "POST", body: form, headers: { [header]: value },
    });
    if (!res.ok) {
      throw new ShioHttpError(res.status, "/api/v2/staticfile/upload", await res.text());
    }
    return (await res.json()).id;
  }

  /**
   * One asset's row as the Media Library reads it, found in the folder listing (SH1032).
   *
   * <p>Through the listing because there is no single-asset read: `GET /api/v2/staticfile/{id}`
   * serves the **bytes**, which is what the `<img>` points at. A first draft of this helper called
   * it and got `Unexpected token '�', "�PNG` out of `res.json()` — the honest failure of
   * asking a download for a document, and worth the comment because the path reads like a row.</p>
   */
  async asset(folderId, id) {
    const res = await this.#fetch(
      `/api/v2/staticfile?folderId=${encodeURIComponent(folderId)}`, { method: "GET" });
    if (!res.ok) throw new ShioHttpError(res.status, "/api/v2/staticfile", await res.text());
    const body = await res.json();
    // SH1030 made this an envelope; older instances answered a bare array, and both are accepted
    // for the reason `convert.mjs` accepts both — this suite runs against an instance it did not
    // build.
    return (Array.isArray(body) ? body : body.items ?? []).find((row) => row.id === id) ?? null;
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
   *
   * **SH648 gave the site delete an undo, so this asks for the other verb by name.** `DELETE
   * /api/v2/site/{id}` now trashes, and a trashed site keeps holding its unique name — so a
   * teardown that used it would leave the next run of the same scenario unable to create the site
   * it seeds, refused by `ShSiteNameHeldByTrashException`. `/purge` takes a live site as well as a
   * trashed one precisely so this stays one call.
   */
  async deleteSite(name) {
    const site = await this.site(name).catch(() => null);
    if (!site?.id) return false;
    const { value, header } = await this.#csrf();
    const res = await this.#fetch(`/api/v2/site/${encodeURIComponent(site.id)}/purge`, {
      method: "DELETE",
      headers: { [header]: value },
    });
    return res.ok || res.status === 404;
  }

  /**
   * SH1091 — an API token of one scope, the way the console's Admin > API tokens makes one. The
   * CDA's conditional merge takes a `WRITE` token, which no agent credential is, so a scenario that
   * drives `shio post set` has to mint one and {@link deleteToken} it afterwards.
   */
  async createToken(title, scope) {
    const { value, header } = await this.#csrf();
    const res = await this.#fetch("/api/v2/api-token", {
      method: "POST",
      headers: { [header]: value, "Content-Type": "application/json" },
      body: JSON.stringify({ title, scope, siteIds: [], env: "PROD", mayPublish: false }),
    });
    if (!res.ok) throw new ShioHttpError(res.status, "/api/v2/api-token", await res.text());
    return res.json();
  }

  async deleteToken(id) {
    const { value, header } = await this.#csrf();
    const res = await this.#fetch(`/api/v2/api-token/${encodeURIComponent(id)}`, {
      method: "DELETE",
      headers: { [header]: value },
    });
    return res.ok || res.status === 404;
  }
}

/**
 * `POST /api/v2/agent/batch` — the write surface, as a client speaks it.
 *
 * <p>Not on `ShioAgentClient`: the CLI has no command that sends ops, so a suite that needs to
 * seed content has to frame the request itself. Lived in `render.conformance.mjs` alone until
 * SH708 needed the same thing, which is the point at which a private copy becomes the shape
 * SH245 is about — four times the second copy of a shared verb was the careless one.</p>
 *
 * @param connection `{ baseUrl, user, password }`, as {@link connection} returns
 * @param ops        the op array; `dryRun` is false, because a seed that did not happen is not one
 * @returns the parsed body, or `{ status, ... }` when the call was refused — the teaching body
 *          travels intact, because on this surface it is the useful half of a failure (SH80/P6)
 */
export async function batch({ baseUrl, user, password }, ops) {
  const res = await fetch(`${baseUrl}/api/v2/agent/batch`, {
    method: "POST",
    headers: {
      Authorization: basic(user, password),
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify({ dryRun: false, ops }),
  });
  const body = await res.text();
  let parsed;
  try {
    parsed = JSON.parse(body);
  } catch {
    parsed = { status: res.status, body };
  }
  return res.ok ? parsed : { status: res.status, ...parsed };
}

/**
 * Install the SH95 vocabulary types a fixture is about to write against (SH724).
 *
 * <h2>The instance was carrying the tests</h2>
 *
 * <p>A clean instance seeds thirteen post-types and `Page` is not one of them — it is vocabulary,
 * installed on demand by `render.provision`. Seven suites here wrote `type: "Page"` and none of
 * them asked for it, so they were green on any workstation that had ever applied a blueprint and
 * refused the seed on a runner: <em>"No post-type named 'Page'"</em>, 5 failed and 46 cancelled.</p>
 *
 * <p>Which is the same defect `requireInstance` exists to catch one level up, arriving from inside:
 * a live tier whose green means the instance is right rather than the code. `ShVocabularyPostTypes`
 * names the shape in its own header, about a different caller, and Block V walked into it anyway.</p>
 *
 * <h2>A precondition, asserted, not a best-effort call</h2>
 *
 * <p>Idempotent — `render.provision` answers `noop` when everything is already there — and it fails
 * loudly, because a fixture that seeds against a model it could not install produces failures about
 * the tools rather than about the model. `render.conformance.mjs` has done it this way since SH325;
 * this is that, shared, so the next suite needs an argument rather than a rediscovery.</p>
 *
 * @param connection `{ baseUrl, user, password }`, as {@link connection} returns
 * @param types      vocabulary type names, e.g. `["Page"]`
 */
export async function requireVocabulary(connection, types = ["Page"]) {
  const provisioned = await batch(connection, [
    { op: "render.provision", data: { types } },
  ]);
  if (errorsIn(provisioned)) {
    throw new Error(
      `shio conformance: could not provision ${types.join(", ")} on ${connection.baseUrl}.\n\n`
      + `${JSON.stringify(provisioned).slice(0, 500)}\n\n`
      + `These are SH95 vocabulary types, which a fresh instance does not carry — nothing seeds\n`
      + `them at startup and \`render.provision\` is what installs them. A suite that skipped this\n`
      + `step used to pass anyway on a development instance that had accumulated the type from an\n`
      + `earlier blueprint or conversion, which is SH724: the instance carrying the test.`,
    );
  }
  return provisioned;
}

/** Whether a batch response reports anything that did not happen. */
export function errorsIn(response) {
  if (response?.status && response.status >= 400) return true;
  return (response?.changes ?? []).some((row) => row.error || row.action === "ERROR");
}
