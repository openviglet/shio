/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio doctor` (SH397) — can I work here?
 *
 * ## The question this answers, and why it needed a command
 *
 * Every fact below already had a reader. None of them had a *caller*: the first minutes of a
 * session were five calls and a guess, and the guess was usually wrong in the same direction —
 * a change that is not in the running instance reads as a change that does not work, and the
 * diagnosis then happens in the wrong file. This is P2 pointed at the environment instead of at
 * the content, and it is CLI-shaped because half the inputs are files on disk.
 *
 * ## Three outcomes, not two
 *
 * A check is `ok`, a `problem`, or **`unknown`**. The third is the one that earns its place: an
 * instance that cannot be reached leaves every later check unanswerable, and reporting those as
 * problems would invent five defects out of one. It is the same rule SH141 applies to a page it
 * could not fetch and SH209 to a page it could not photograph — *unreachable is never the same
 * row as wrong* — arriving in a third place.
 *
 * ## The exit code
 *
 * 1 when any check is a problem, so this is usable as a gate in front of a longer job. An
 * `unknown` does not fail on its own: a doctor run with no instance is how somebody checks their
 * own tree, and failing it would make the command useless in the case it is most needed.
 */
import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { helpText } from "../help.mjs";

export const HELP = helpText("doctor");

/** Widest label, so the status column lines up without a table library. */
const LABEL = 12;

export async function run(flags, positionals, ctx) {
  const admin = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({
    baseUrl: admin.baseUrl,
    user: admin.user,
    password: admin.password,
    fetch: ctx.fetch,
  });

  const checks = [];
  const instance = await instanceCheck(client, admin, ctx);
  checks.push(instance);

  // Everything below needs the instance. Asked only when it answered, because five checks that
  // cannot run are one problem and not five.
  if (instance.status === "ok") {
    checks.push(await modelCheck(admin, ctx));
    checks.push(await sitesCheck(client));
  } else {
    checks.push(unknown("model", "the instance did not answer"));
    checks.push(unknown("sites", "the instance did not answer"));
  }
  checks.push(await contentCheck(ctx));

  if (flags.json) {
    ctx.stdout.write(`${JSON.stringify({ checks }, null, 2)}\n`);
  } else {
    for (const check of checks) {
      ctx.stdout.write(`${mark(check.status)} ${check.name.padEnd(LABEL)} ${check.detail}\n`);
    }
    const fixes = checks.filter((check) => check.fix).map((check) => check.fix);
    if (fixes.length) {
      ctx.stdout.write(`\n${fixes.join("\n")}\n`);
    }
  }

  const problems = checks.filter((check) => check.status === "problem");
  return { checks, problems: problems.length, exitCode: problems.length ? 1 : 0 };
}

const mark = (status) => (status === "ok" ? "ok  " : status === "problem" ? "FAIL" : "??  ");
const ok = (name, detail) => ({ name, status: "ok", detail });
const problem = (name, detail, fix) => ({ name, status: "problem", detail, fix });
const unknown = (name, detail, fix) => ({ name, status: "unknown", detail, fix });

/**
 * Up, authenticating, and which build (SH397/SH460).
 *
 * <p>The build half is the reason this task exists. `version` reads `dev` on any instance not run
 * from a packaged jar — the same string before and after a rebuild, so it distinguishes nothing —
 * and that is precisely the mode where the running instance can be several commits behind the tree
 * being edited. `builtAt` is the answer: SH460 stamps it from the newest class the JVM loaded, so
 * it is comparable rather than constant, and this prints it beside the version rather than making
 * a reader ask for the manifest and know which field to look at.</p>
 *
 * <p>A 401 is called out separately from an unreachable instance. They are one line apart in the
 * output and a long way apart in what the reader does next.</p>
 */
async function instanceCheck(client, admin, ctx) {
  try {
    const manifest = await client.getManifest();
    const version = manifest?.version ?? "?";
    const built = manifest?.builtAt;
    const commit = manifest?.commit;
    const stamp = [commit ? `commit ${commit}` : null, built ? `built ${built}` : null]
      .filter(Boolean)
      .join(", ");
    if (!built) {
      // Not a problem: a packaged jar whose timestamp could not be read still serves. But it is
      // the fact that makes "is this instance stale" unanswerable, so it is never silent.
      return unknown(
        "instance",
        `up at ${admin.baseUrl}, version ${version}${stamp ? ` (${stamp})` : ""} — no build`
          + " timestamp, so whether it is running your latest compile cannot be told from here",
      );
    }
    return ok("instance", `up at ${admin.baseUrl}, version ${version}${stamp ? ` (${stamp})` : ""}`);
  } catch (error) {
    const status = error?.status;
    if (status === 401 || status === 403) {
      return problem(
        "instance",
        `up at ${admin.baseUrl}, but the credentials were refused (${status})`,
        `  credentials: set SHIO_USER and SHIO_PASSWORD, or pass --user/--password.`,
      );
    }
    return problem(
      "instance",
      `no answer from ${admin.baseUrl} — ${error?.message ?? "unreachable"}`,
      `  instance: start it, or point --url at the right one. Everything below needs it.`,
    );
  }
}

/**
 * SH295 — the render model is provisioned, and does not drift from what this build ships.
 *
 * <p>Both halves are one read: `drift` answers per post-type, so an empty answer means the model
 * is there and current, and a non-empty one names the fields. An instance that never provisioned
 * the model at all reports every type as drifted, which is the same repair.</p>
 */
async function modelCheck(admin, ctx) {
  const url = `${admin.baseUrl.replace(/\/+$/, "")}/api/v2/render/post-types/drift`;
  try {
    const response = await ctx.fetch(url, {
      headers: { Authorization: basic(admin), Accept: "application/json" },
    });
    if (!response.ok) {
      return unknown("model", `GET /api/v2/render/post-types/drift answered ${response.status}`);
    }
    const drift = await response.json();
    if (!Array.isArray(drift) || !drift.length) {
      return ok("model", "the render post-types are provisioned and match this build");
    }
    const named = drift
      .map((row) => `${row.postType}(${(row.missingFields ?? []).join(",")})`)
      .join(" ");
    return problem(
      "model",
      `${drift.length} render post-type(s) are missing fields this build declares: ${named}`,
      "  model: POST /api/v2/render/post-types/reconcile — one call, and it changes nothing else.",
    );
  } catch (error) {
    return unknown("model", `could not read the drift — ${error?.message ?? "no answer"}`);
  }
}

/** Which sites exist, and whether each has a front end a page could be served from. */
async function sitesCheck(client) {
  try {
    const { body } = await client.getContext({ include: "sites", format: "json" });
    const sites = JSON.parse(body)?.sites ?? [];
    if (!sites.length) {
      return unknown("sites", "this instance holds no site yet — `shio init` or `shio apply --blueprint`");
    }
    const named = sites
      .map((site) => `${site.name}${site.url ? "" : " (no front-end URL)"}`)
      .join(", ");
    return ok("sites", `${sites.length}: ${named}`);
  } catch (error) {
    return unknown("sites", `could not read the context pack — ${error?.message ?? "no answer"}`);
  }
}

/**
 * Is there a content tree here, and does it belong to this checkout.
 *
 * <p>Deliberately *not* `push --content --check`, which the design named. That answers "does the
 * tree match the server", which needs the server, a full projection and a plan — seconds of work
 * and a second failure mode, inside a command whose whole promise is one quick answer. What
 * belongs here is the cheap half: whether a tree exists at all and where its cursor is, which is
 * what tells a reader that the expensive check is the next thing to run. The pointer says so.</p>
 */
async function contentCheck(ctx) {
  const path = "shio/.shio/content-base.json";
  try {
    const raw = await ctx.fs.readFile(join(ctx.cwd, path), "utf8");
    const base = JSON.parse(typeof raw === "string" ? raw : raw.toString("utf8"));
    const at = base?.cursor ? `cursor ${base.cursor}` : "no cursor recorded";
    return ok("content", `a local tree is present (${at}) — \`shio push --content --check\` compares it`);
  } catch {
    // Absent is the normal state for a checkout nobody has pulled content into, and for every
    // repository that authors through the API instead. Not a problem, and not silence either.
    return unknown("content", `no local content tree (${path} absent) — \`shio pull --content\` makes one`);
  }
}

function basic({ user, password }) {
  const raw = `${user ?? ""}:${password ?? ""}`;
  return `Basic ${Buffer.from(raw, "utf8").toString("base64")}`;
}

function join(cwd, path) {
  return `${String(cwd).replace(/[\\/]+$/, "")}/${path}`;
}
