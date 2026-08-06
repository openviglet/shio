/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio publish` (SH546) — the rung of the ladder that could not publish.
 *
 * P1 orders every capability MCP → files/CLI → REST → console, and publication was present at
 * three of the four: `shio_publish` at MCP, `post.publish` on the batch surface, a button in the
 * console. `grep post.publish cli/src/` returned nothing. So the step immediately after the longest
 * command in the CLI dropped out of the tier and into raw HTTP — a real round published a
 * conversion by reading `/agent/find`, composing a batch document in Python and posting it with
 * curl: three tools and a hand-written document to perform the one op `shio convert` had just
 * printed an instruction to perform.
 *
 * It is not a convenience verb. `convert` ends by saying *every post lands in DRAFT — publishing is
 * a separate, explicit call*, which is correct (P7) and is the right thing to say; the binary that
 * printed it could not make the call. A conversion could not be finished on the tier P4 calls the
 * agent's cheapest surface.
 *
 * <h2>`--all` is a client-side enumeration, not a server-side publish-everything</h2>
 *
 * `shio_publish` refuses a call with no address and says so — *there is deliberately no
 * publish-everything form* — and that refusal is right where it is: it is about a **tool** that
 * would otherwise take a site and act on content the caller never saw. This does the thing the
 * curl round did by hand: `find` the site's posts, name every one of them in the ops, and print the
 * count before sending. The server still receives an explicit list, and `--dry-run` shows it.
 *
 * <h2>Chunking, and why the ceiling is not written here</h2>
 *
 * One batch is one transaction and is capped at `shio.agent.batch.max-ops` — 200 by default, which
 * is why a 201-page conversion is exactly the case that cannot be one call. The number is *not*
 * copied into this file: the run is sent whole, and a `batch-too-large` refusal carries the ceiling
 * as its `example`, so the split re-sends at the size the server named. That is P6 used as a
 * protocol rather than as a message — validation happens before any op runs, so the refused attempt
 * writes nothing. A split run is atomic per chunk and not across them, which is reported; publish
 * is idempotent (re-publishing unchanged content is a `noop`), so re-running after a failure is
 * cheap and safe, and that is what makes the trade acceptable.
 */
import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { retellUnknownSite } from "../unknown-site.mjs";

/**
 * What `shio publish --help` prints (SH435).
 *
 * Here rather than in `index.mjs` so the flags and their meanings are one edit apart.
 */
export const HELP = `shio publish [address...] — make drafts live, or take live pages down

The only call that changes what visitors see. Re-publishing unchanged content is a noop, so
running this twice is safe; shio verify and shio diff are what tell you it worked.

  --address <a,b>      Addresses to publish, comma-separated (or pass them as arguments)
  --site <name>        The site, required with --all
  --all                Every post the site holds, named explicitly in the ops after a find
  --type <name>        With --all: only posts of this post-type
  --unpublish          Take them down instead
  --dry-run            Report what would change and write nothing
  --ids                Put object ids on the change rows (off by default)

Examples:
  shio publish post:Acme/about post:Acme/contact
  shio publish --site Acme --all --dry-run
  shio publish --site Acme --all --type Page
  shio publish --unpublish post:Acme/old-campaign
`;

export async function run(flags, positionals, ctx) {
  const site = typeof flags.site === "string" ? flags.site : undefined;
  const unpublish = Boolean(flags.unpublish);
  const verb = unpublish ? "unpublish" : "publish";
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });

  const addresses = await targets(flags, positionals, site, client, ctx, verb);
  const ops = addresses.map((address) => ({ op: `post.${verb}`, address }));
  const dryRun = Boolean(flags.dryRun);

  const runs = await send(client, ops, { dryRun, ids: Boolean(flags.ids) }).catch((error) => {
    throw retellUnknownSite(error, { command: "publish", site: site ?? "<name>" });
  });

  ctx.stdout.write(render(runs, { verb, dryRun, addresses: addresses.length }));
  return merge(runs);
}

/**
 * The addresses to act on.
 *
 * `--all` needs a site because "publish everything readable" is not a thing anyone means: the
 * allow-list can hold several sites, and a run that swept them all would make what a caller sees
 * depend on which sites their credential happens to reach.
 */
async function targets(flags, positionals, site, client, ctx, verb) {
  const named = [
    ...positionals,
    ...String(flags.address ?? "")
      .split(",")
      .map((value) => value.trim()),
  ].filter(Boolean);

  if (!flags.all) {
    if (named.length) return named;
    throw new Error(
      `shio ${verb}: nothing to ${verb}. Name the addresses, or use --site <name> --all to ` +
        `${verb} everything the site holds. ` +
        `Example: shio ${verb} post:Acme/about  |  shio ${verb} --site Acme --all --dry-run`,
    );
  }
  if (named.length) {
    throw new Error(
      `shio ${verb}: --all and named addresses are two different requests. Drop --all to ` +
        `${verb} the ${named.length} address(es) you named.`,
    );
  }
  if (!site) {
    throw new Error(
      `shio ${verb}: --all needs --site <name>. There is no ${verb}-everything across sites — ` +
        "what it swept would depend on which sites your credential reaches.",
    );
  }

  const type = typeof flags.type === "string" ? flags.type : undefined;
  const found = await client
    .findAll(type ? { site, type } : { site })
    .catch((error) => {
      throw retellUnknownSite(error, { command: verb, site });
    });
  if (found.truncated) {
    // The same rule the content projection follows: a short list is not a smaller request, it is
    // one with holes, and holes here mean pages that silently stay dark.
    throw new Error(
      `shio ${verb}: could not read the whole site (${found.results.length} row(s) over ` +
        `${found.pages} page(s), and the walk was cut short). ${verb} the addresses you need by ` +
        "name, or narrow the run with --type.",
    );
  }
  const addresses = found.results.map((row) => row.address).filter(Boolean);
  if (!addresses.length) {
    throw new Error(
      `shio ${verb}: ${site} holds no posts${type ? ` of type ${type}` : ""} — nothing to ${verb}.`,
    );
  }
  ctx.stdout.write(
    `shio ${verb}: ${addresses.length} post(s) in ${site}${type ? ` of type ${type}` : ""}` +
      `${live(found.results)}\n`,
  );
  return addresses;
}

/**
 * How many of the found rows are already live (SH545).
 *
 * Printed because it is the one number that tells a caller whether this run is the publish or a
 * re-run of it, and it costs nothing: `find` reports `published` per row since SH545, which is the
 * field that existed nowhere when this command's absence was found.
 */
function live(rows) {
  const already = rows.filter((row) => row.published === true).length;
  return already ? ` — ${already} already live` : "";
}

/**
 * Sends the ops, splitting only if the server says they do not fit.
 *
 * The whole run goes first because that is the atomic outcome and the one worth having. A refusal
 * costs one request and no writes — the batch validates its size before it runs any op — and it
 * carries the ceiling, so the split is sized by the instance rather than by a constant copied out
 * of its configuration (P10: the server owns the number).
 */
async function send(client, ops, options) {
  try {
    return [await client.batch({ dryRun: options.dryRun, ops }, { ids: options.ids })];
  } catch (error) {
    const max = ceilingFrom(error);
    if (!max || ops.length <= max) throw error;
    const runs = [];
    for (let at = 0; at < ops.length; at += max) {
      runs.push(await client.batch(
        { dryRun: options.dryRun, ops: ops.slice(at, at + max) },
        { ids: options.ids },
      ));
    }
    return runs;
  }
}

/**
 * The op ceiling an SH80 `batch-too-large` body carries, or `undefined`.
 *
 * Read from `example`, which is the number itself rather than prose about it — the one field of
 * that problem a client can act on without parsing a sentence.
 */
function ceilingFrom(error) {
  if (typeof error?.body !== "string") return undefined;
  let problem;
  try {
    problem = JSON.parse(error.body);
  } catch {
    return undefined;
  }
  if (!String(problem?.type ?? "").endsWith("batch-too-large")) return undefined;
  const max = Number(problem.example);
  return Number.isInteger(max) && max > 0 ? max : undefined;
}

/** One result for the caller, whether it took one call or twelve. */
function merge(runs) {
  return {
    dryRun: runs.every((run) => run.dryRun),
    ops: runs.reduce((total, run) => total + (run.ops ?? 0), 0),
    changed: runs.reduce((total, run) => total + (run.changed ?? 0), 0),
    results: runs.flatMap((run) => run.results ?? []),
  };
}

/**
 * The report: what moved, what was already where it belongs, and — when it took more than one
 * call — that the run was atomic per chunk and not across them.
 *
 * Noops are counted and not listed. On a re-run they are every line, and a caller re-running this
 * after a failure is the case the idempotence exists for.
 */
function render(runs, { verb, dryRun, addresses }) {
  const result = merge(runs);
  const rows = result.results.filter((change) => change.action !== "noop");
  const noops = result.results.length - rows.length;
  const header =
    `shio ${verb}: ${result.changed} of ${addresses} post(s)` +
    (dryRun ? " would change — nothing written" : " changed") +
    (noops ? `, ${noops} already ${verb === "publish" ? "live" : "down"}` : "");
  const lines = rows.map((change) => {
    const detail = change.detail ? ` (${change.detail})` : "";
    return `  ${change.action} ${change.address}${change.id ? ` ${change.id}` : ""}${detail}`;
  });
  if (runs.length > 1) {
    lines.push(
      `  sent as ${runs.length} batches (the instance caps one transaction): atomic per batch, ` +
        `not across them — re-run to finish, ${verb}ing what is already done is a noop`,
    );
  }
  return `${[header, ...lines].join("\n")}\n`;
}
