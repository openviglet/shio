/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio report` (SH104) — the human-facing handoff summary: what changed, where to look at
 * it, and what is still open. Ready to paste into a PR body.
 *
 * ## Why this composes three existing reads instead of adding an endpoint
 *
 * Everything it needs already exists: the SH88 feed knows what changed, the SH77 context pack
 * knows each site's public URL, and the SH99 report knows what is unresolved. A
 * `GET /agent/report` would be a fourth contract restating three (law P10) — and it would put
 * *presentation* on the server, where a Markdown template would then have to be versioned
 * alongside the protocol. The rendering is the CLI's job; the facts are the server's.
 *
 * ## The cursor is not advanced as a side effect of reading
 *
 * A handoff report has to start where the last one ended — that is the whole reason it waited
 * for SH88. But a read that silently moves a cursor means running it twice gives two different
 * answers, and the second one says "nothing changed": for a *review* artifact that is the
 * dangerous direction, because an empty report reads as approval. So the position moves only
 * on `--mark`, and `--mark` refuses when the range was incomplete — the same refusal
 * `pull --content` makes about a truncated projection, and for the same reason: a cursor
 * advanced past changes nobody saw is a permanent hole in the review trail.
 */
import { dirname, join } from "node:path";

import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { BASE_FILE, readBase } from "../content-store.mjs";
import { buildReport } from "../report.mjs";
import { helpText } from "../help.mjs";

/** Where `--mark` records the position the last handoff covered. */
export const REPORT_STATE_FILE = "shio/.shio/report-state.json";
const STATE_VERSION = 1;

/**
 * What `shio report --help` prints (SH435).
 *
 * Here rather than in `index.mjs` so the flags and their meanings are one edit apart: the
 * global help described some commands' flags and no command's completely, and learning this
 * one's meant grepping the source for `flags.` — reading an implementation to discover an
 * interface, having already paid for a help text that answered something else.
 */
export const HELP = helpText("report");

export async function run(flags, positionals, ctx) {
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });
  const statePath = typeof flags.state === "string" ? flags.state : REPORT_STATE_FILE;

  const since = await resolveSince(flags, positionals, ctx, statePath);
  const changes = await client.changes({
    site: flags.site,
    since: since ?? undefined,
    limit: flags.limit,
  });

  // Only the sites section: the pack's model and sitemap are large and this needs one field
  // from it (P3 — a read that fetches nine sections to use one is a token bill nobody asked
  // for). `ids` stays off, since a preview link is built from names and URLs.
  const context = await readSites(client, flags);
  const verify = flags.verify === false || flags.noVerify
    ? undefined
    : await client.verify({ site: flags.site, limit: flags.limit });

  const report = buildReport({
    changes,
    verify,
    context,
    title: typeof flags.title === "string" ? flags.title : undefined,
  });

  if (flags.json) {
    ctx.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
  } else if (flags.out) {
    const target = join(ctx.cwd, flags.out);
    await ctx.fs.mkdir(dirname(target), { recursive: true });
    await ctx.fs.writeFile(target, report.markdown, "utf8");
    ctx.stdout.write(
      `shio report: wrote ${flags.out} — ${report.changed} change(s)` +
        `${report.caveats.length ? `, ${report.caveats.length} caveat(s)` : ""}\n`,
    );
  } else {
    ctx.stdout.write(report.markdown);
  }

  if (flags.mark) {
    return mark(report, statePath, ctx);
  }
  return report;
}

/**
 * `--since <cursor>` · `--since last` (where the last `--mark` left off) · nothing (the feed's
 * window, which reports itself as such).
 *
 * `--since content` reads the content tree's base instead, for the project that pulls files
 * and wants "everything since my tree was current" without keeping a second position.
 */
async function resolveSince(flags, positionals, ctx, statePath) {
  const raw = flags.since ?? positionals[0];
  if (raw === "last") {
    const state = await readState(ctx, statePath);
    if (!state.cursor) {
      // Not an error: the first handoff in a repository has no previous one, and the report
      // says it is a window rather than pretending to be a delta.
      ctx.stderr.write(
        `shio report: no previous handoff recorded in ${statePath} — ` +
          `reporting the most recent activity instead. Pass --mark to record this position.\n`,
      );
      return undefined;
    }
    return state.cursor;
  }
  if (raw === "content") {
    return (await readBase(ctx, flags.baseFile ?? BASE_FILE)).cursor ?? undefined;
  }
  return raw ?? undefined;
}

/**
 * The `sites` section of the context pack, parsed.
 *
 * A failure here is not fatal: the report degrades to naming why each preview link is missing,
 * which is strictly better than refusing to produce the summary at all. The one thing it must
 * not do is emit links built from a guess.
 */
async function readSites(client, flags) {
  try {
    const { body } = await client.getContext({
      site: flags.site,
      include: "sites",
      format: "json",
    });
    return JSON.parse(body);
  } catch {
    return undefined;
  }
}

/**
 * Records the position this report covered.
 *
 * Refused when the report carried caveats about its own completeness: `--mark` says "this
 * range has been reviewed", and a range that was cut short has not been.
 */
async function mark(report, statePath, ctx) {
  if (report.caveats.length > 0) {
    ctx.stderr.write(
      `shio report: not recording the cursor — this report does not cover its whole range ` +
        `(${report.caveats.length} caveat(s) above). Re-run with a higher --limit, or from ` +
        `the cursor, until the range is complete.\n`,
    );
    return { ...report, exitCode: 1 };
  }
  if (!report.cursor) {
    return report;
  }
  const target = join(ctx.cwd, statePath);
  await ctx.fs.mkdir(dirname(target), { recursive: true });
  await ctx.fs.writeFile(
    target,
    `${JSON.stringify({ version: STATE_VERSION, cursor: report.cursor }, null, 2)}\n`,
    "utf8",
  );
  ctx.stdout.write(`shio report: recorded cursor ${report.cursor} in ${statePath}\n`);
  return report;
}

async function readState(ctx, path) {
  try {
    const raw = await ctx.fs.readFile(join(ctx.cwd, path), "utf8");
    const parsed = JSON.parse(raw);
    return { version: parsed.version ?? STATE_VERSION, cursor: parsed.cursor ?? null };
  } catch (error) {
    if (error?.code === "ENOENT") return { version: STATE_VERSION, cursor: null };
    throw error;
  }
}
