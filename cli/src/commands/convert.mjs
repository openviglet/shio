/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * `shio convert` (SH417) - replication's apply phase: the accepted plan, as content.
 *
 * ## The verb the pipeline was one short of
 *
 * SH379 stopped at the proposal on purpose, because review and mutation in one command is the
 * surprise P7 forbids. This is the other half, and it is deliberately a **translation into existing
 * verbs rather than a new engine**: it builds `shio apply`'s desired-state document and sends it
 * down the same path, which is idempotent, `--dry-run`-able and the only write contract there is
 * (P10). What is new here is the reading, the two refusals, and the preconditions.
 *
 * ## What it refuses, and why each one is a refusal rather than a guess
 *
 * A **stale acceptance** (SH417's rule): an id names a block's structure, so one the current proposal
 * does not hold means the source changed after the review. Writing it would apply a decision nobody
 * made about the markup that is there now.
 *
 * A **missing field** (SH436): the theme goes into `Theme.TOKENS` and the scripts into
 * `SiteScripts.SCRIPTS`, and an instance whose render types predate those fields has nowhere to put
 * them. The proposal already states its preconditions; this is the first moment there is an instance
 * to check them against, so it asks `GET /api/v2/render/post-types/drift` before it writes and names
 * the reconcile call in the refusal. Skipped on a dry run and on `--out`, which touch no instance.
 *
 * An **absent site** is the third, and until SH439 it was the one refusal a reader could not act on:
 * the resolver's fix is to add a `site.upsert` op, and this command takes no ops. Converting into a
 * *new* site is the normal shape of a replication, so `--create-site` exists — the same flag `apply`
 * has, compiled to the same op — and the refusal now names it.
 */
import { join } from "node:path";

import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { buildConversion, describeConversion, staleMessage } from "../convert.mjs";
import { retellUnknownSite } from "../unknown-site.mjs";
import { proposalFor } from "./propose.mjs";

/**
 * What `shio convert --help` prints (SH435).
 *
 * Here rather than in `index.mjs` so the flags and their meanings are one edit apart.
 */
export const HELP = `shio convert --site <name> - apply the accepted conversion plan as content

Turns the blocks you accepted with shio propose --accept into posts, plus the captured
theme and scripts. It reads the capture and the plan, and writes through the same
desired-state path shio apply uses, so running it twice changes nothing the second time.

Every post lands in DRAFT. Publishing is a separate, explicit call.

  --site <name>        The site to write into (required)
  --create-site        Create the site (blank) if it does not exist, as the first step
  --dry-run            Print the plan the instance would apply, and write nothing
  --check              Drift guard: plan, and exit non-zero if anything would change
  --out <file>         Write the desired-state document to a file and apply nothing
  --ids                Put object ids on the change rows (off by default)
  --limit <n>          Rows to print
  --inventory <file>   Read a capture from somewhere else
  --json               The document as JSON on stdout instead of the report

Examples:
  shio convert --site Acme --dry-run
  shio convert --site Acme --create-site
  shio convert --site Acme --out conversion.json
`;

export async function run(flags, positionals, ctx) {
  const site = typeof flags.site === "string" && flags.site.trim()
    ? flags.site.trim()
    : String(positionals[0] ?? "").trim();
  if (!site) {
    throw new Error(
      "shio convert: pass the site to write into.\n"
        + "A conversion writes posts, and a post lives in a site; guessing which one from a capture\n"
        + "would put somebody else's content in a site nobody named.\n"
        + "  shio convert --site Acme --dry-run",
    );
  }

  // The plan file and the proposal computed from the capture NOW: the pair is the whole point, since
  // an acceptance is only checkable against the candidates the current markup produces.
  const { proposal, plan } = await proposalFor(flags, ctx, "convert");
  const { document, stale, sections, extras } = buildConversion({
    proposal,
    plan,
    site,
    createSite: Boolean(flags.createSite),
  });
  if (stale.length) {
    throw new Error(staleMessage(stale, typeof flags.out === "string" ? flags.out : null));
  }
  if (!document.posts.length) {
    throw new Error(
      "shio convert: nothing has been accepted, so there is nothing to write.\n"
        + `The proposal holds ${proposal.candidates.length} candidate(s); accepting one is what says a\n`
        + "block becomes content rather than staying verbatim markup:\n"
        + "  shio propose\n"
        + "  shio propose --accept <id>",
    );
  }

  if (typeof flags.out === "string" && flags.out) {
    await write(join(ctx.cwd, flags.out), `${JSON.stringify(document, null, 2)}\n`, ctx);
    ctx.stdout.write(describeConversion({ document, sections, extras, requires: proposal.requires },
      flags));
    ctx.stdout.write(`  document: ${flags.out} (nothing was applied)\n`);
    return { document, applied: false, exitCode: 0 };
  }

  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });
  const planOnly = Boolean(flags.check || flags.dryRun);

  if (!planOnly) {
    await requirePostTypeFields(proposal.requires ?? [], { baseUrl, user, password }, ctx);
  }

  ctx.stdout.write(describeConversion({ document, sections, extras, requires: proposal.requires },
    flags));
  // SH439 — the write resolver's "add a `site.upsert` op" is un-followable here, because `convert`
  // composes its own document and takes no ops. The flag above is the remedy; this makes the
  // refusal name it, for the caller who has not passed it yet.
  const result = await (planOnly
    ? client.plan(document, { ids: Boolean(flags.ids) })
    : client.apply(document, { ids: Boolean(flags.ids) })
  ).catch((error) => {
    throw retellUnknownSite(error, { command: "convert", site });
  });

  if (flags.json) {
    ctx.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
  } else {
    ctx.stdout.write(
      `  ${planOnly ? "would change" : "changed"}: ${result?.changed ?? 0} object(s)`
        + `${planOnly ? " (nothing was written)" : " - in DRAFT"}\n`,
    );
  }
  // `--check` is a drift guard, so a non-empty plan is the failure it exists to report.
  const drifted = Boolean(flags.check) && Number(result?.changed ?? 0) > 0;
  return { document, applied: !planOnly, changed: result?.changed ?? 0, exitCode: drifted ? 1 : 0 };
}

/**
 * SH436's precondition, checked at the one moment there is an instance to ask.
 *
 * <p>`GET /api/v2/render/post-types/drift` is the read that answers "does this instance declare the
 * fields the shipped model has" without writing anything, and the refusal names `reconcile`, which is
 * the one call that fixes it and changes nothing else. A theme written into a `Theme` that has no
 * `TOKENS` is the failure this exists to prevent: it would either fail somewhere unhelpful or write
 * the theme without its tokens.
 */
async function requirePostTypeFields(requires, admin, ctx) {
  if (!requires.length) return;
  const url = `${admin.baseUrl.replace(/\/+$/, "")}/api/v2/render/post-types/drift`;
  let drift;
  try {
    const response = await ctx.fetch(url, {
      headers: { Authorization: basic(admin), Accept: "application/json" },
    });
    if (!response.ok) return;
    drift = await response.json();
  } catch {
    // A drift read that could not run is not a reason to refuse a write: the apply itself will fail
    // with the server's own message, which is better than a guess made from a failed GET.
    return;
  }
  const missing = [];
  for (const row of requires) {
    const drifted = (drift ?? []).find((each) => each.postType === row.postType);
    if (drifted?.missingFields?.includes(row.field)) missing.push(row);
  }
  if (!missing.length) return;
  throw new Error(
    `shio convert: this instance's render post-types are missing `
      + `${missing.map((row) => `${row.postType}.${row.field}`).join(", ")}, so the conversion has\n`
      + "nowhere to put half of what it holds.\n"
      + `${missing.map((row) => `  ${row.postType}.${row.field} - ${row.why}`).join("\n")}\n`
      + "The repair is one call and changes nothing else:\n"
      + "  POST /api/v2/render/post-types/reconcile\n"
      + "GET /api/v2/render/post-types/drift answers the same question without writing.",
  );
}

function basic({ user, password }) {
  const raw = `${user ?? ""}:${password ?? ""}`;
  return `Basic ${Buffer.from(raw, "utf8").toString("base64")}`;
}

async function write(path, body, ctx) {
  await ctx.fs.mkdir(dirOf(path), { recursive: true });
  await ctx.fs.writeFile(path, body);
}

function dirOf(path) {
  const at = String(path).replace(/\\/g, "/").lastIndexOf("/");
  return at <= 0 ? "." : String(path).slice(0, at);
}
