/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio changes` (SH88) — what changed, and the cursor to resume from.
 *
 * Design law P1 puts the CLI between MCP and REST, and this is the third surface over one
 * service: `shio_changes`, `GET /api/v2/agent/changes` and this command all read the same
 * feed. The command exists because the cursor is a value a *pipeline* holds — a nightly
 * job that pulls content only when something moved needs it in a shell variable, not in a
 * model's context.
 */
import { ShioHttpError } from "@viglet/shio-client";

import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { BASE_FILE, readBase } from "../content-store.mjs";
import { helpText } from "../help.mjs";
import { retryCommand } from "../report.mjs";

/**
 * What `shio changes --help` prints (SH435).
 *
 * Here rather than in `index.mjs` so the flags and their meanings are one edit apart: the
 * global help described some commands' flags and no command's completely, and learning this
 * one's meant grepping the source for `flags.` — reading an implementation to discover an
 * interface, having already paid for a help text that answered something else.
 */
export const HELP = helpText("changes");

export async function run(flags, positionals, ctx) {
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });

  // `--since last` reads the position the last content pull reached, so "anything new
  // since my file tree was current?" needs no bookkeeping of its own.
  const since =
    flags.since === "last" || flags.sinceLast
      ? (await readBase(ctx, flags.baseFile ?? BASE_FILE)).cursor
      : flags.since ?? positionals[0] ?? undefined;

  if (flags.follow) {
    // SH989 — the event stream does not carry the names, so accepting both flags would drop one of
    // them without saying so. Refused before connecting, with the call that does answer.
    if (flags.fields) {
      throw new Error(
        "shio changes: --fields reads the names from a page of the feed, and --follow streams events " +
          "that do not carry them. Drop --follow to see them. Example: shio changes --since last --fields",
      );
    }
    return follow(client, { site: flags.site, since: since ?? undefined, json: flags.json }, ctx);
  }

  const changes = await client.changes({
    site: flags.site,
    since: since ?? undefined,
    limit: flags.limit,
    // SH989 — the fields each write changed, the one account of an approved run the approve leaves.
    include: flags.fields ? "fields" : undefined,
    // SH999 — `agent`, `curator` or a session. A pipeline that pulls when something moved wants
    // the half it did not write itself, and until now every row looked the same.
    by: flags.by,
  });

  if (flags.cursor) {
    // Just the position, for `SINCE=$(shio changes --cursor)`.
    ctx.stdout.write(`${changes.cursor}\n`);
    return changes;
  }
  if (flags.json) {
    ctx.stdout.write(`${JSON.stringify(changes, null, 2)}\n`);
    return changes;
  }

  ctx.stdout.write(render(changes));
  return changes;
}

/**
 * `shio changes --follow` (SH967) — the same feed, held open over `GET /api/v2/events`.
 *
 * Runs until the process is stopped. A dropped connection reconnects from the last event's id —
 * the server resumes after it exactly, so nothing written while the connection was down is lost —
 * waiting longer each time up to thirty seconds. A refusal (a 4xx: bad credentials, a malformed
 * cursor, a site the user cannot read) is not a dropped connection and ends the command, or it
 * would retry an answer that will not change.
 *
 * With `--json`, one JSON line per event (`{ "cursor", "changes", "incidents" }`), for a pipeline.
 */
async function follow(client, { site, since, json }, ctx) {
  const sleep = ctx.sleep ?? ((ms) => new Promise((resolve) => setTimeout(resolve, ms)));
  let position = since;
  let delay = 1000;
  for (;;) {
    try {
      for await (const event of client.events({ site, since: position })) {
        if (event.id) position = event.id;
        delay = 1000;
        const page = { cursor: event.id, ...event.data };
        ctx.stdout.write(json ? `${JSON.stringify(page)}\n` : renderPage(page));
      }
    } catch (error) {
      if (error instanceof ShioHttpError && error.status >= 400 && error.status < 500) throw error;
      ctx.stderr.write(
        `shio changes: the stream dropped (${error.message}); reconnecting in ${delay / 1000}s\n`,
      );
    }
    if (ctx.signal?.aborted) return { cursor: position };
    await sleep(delay);
    delay = Math.min(delay * 2, 30000);
    if (ctx.signal?.aborted) return { cursor: position };
  }
}

/** One pushed page, in the lines `render` uses for a polled one. */
function renderPage(page) {
  return render({
    found: page.changes?.length ?? 0,
    window: false,
    more: false,
    changes: page.changes,
    incidents: page.incidents,
    notes: [],
    cursor: page.cursor,
  });
}

function render(changes) {
  const lines = [
    `shio changes: ${changes.found} entr${changes.found === 1 ? "y" : "ies"}` +
      `${changes.window ? " (window — pass --since <cursor> for a delta)" : ""}` +
      `${changes.more ? ", more available" : ""}`,
  ];
  for (const change of changes.changes ?? []) {
    const address = change.address ?? "(gone)";
    // SH999 — the run beside the person, because both are true: an agent acts AS someone, and a
    // line naming only the credential was the same line for both.
    const who = change.agent
      ? `${change.by} via ${change.agentName ?? change.agent} at ${change.at}`
      : `${change.by} at ${change.at}`;
    lines.push(`  ${change.action} ${address} — ${change.what ?? ""} [${who}]`);
    // SH970 — an arrival from the site's form, said as one rather than left to the actor string.
    if (change.submission) {
      const summary = change.submission.summary ? `: ${change.submission.summary}` : "";
      lines.push(`      form submission${summary}`);
    }
    // SH989 — present only with --fields, and only on a row that recorded them. An empty list is a
    // write that changed nothing, which is worth saying rather than printing a blank.
    if (Array.isArray(change.fields)) {
      lines.push(`      fields: ${change.fields.length ? change.fields.join(", ") : "(none changed)"}`);
    }
  }
  // SH144 — asynchronous failures in the same range, under the same cursor. Marked `!` rather
  // than folded into the list above: a webhook that never arrived is not a content change, and
  // a reader scanning this output for what an agent did must not have to tell them apart by
  // squinting at the verb.
  for (const incident of changes.incidents ?? []) {
    const repeats = incident.count > 1 ? ` (x${incident.count})` : "";
    lines.push(
      `  ! ${incident.kind} ${incident.subject ?? ""} — ${incident.message ?? ""}${repeats}` +
        ` [at ${incident.at}]`,
    );
    if (incident.fix) lines.push(`      → ${incident.fix}`);
    const retry = retryCommand(incident.retry);
    if (retry) lines.push(`      retry: ${retry}`);
  }
  for (const note of changes.notes ?? []) lines.push(`  note: ${note}`);
  lines.push(`cursor: ${changes.cursor}`);
  return `${lines.join("\n")}\n`;
}
