/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * `shio changes` (SH88) — what changed, and the cursor to resume from.
 *
 * Design law P1 puts the CLI between MCP and REST, and this is the third surface over one
 * service: `shio_changes`, `GET /api/v2/agent/changes` and this command all read the same
 * feed. The command exists because the cursor is a value a *pipeline* holds — a nightly
 * job that pulls content only when something moved needs it in a shell variable, not in a
 * model's context.
 */
import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { BASE_FILE, readBase } from "../content-store.mjs";

export async function run(flags, positionals, ctx) {
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });

  // `--since last` reads the position the last content pull reached, so "anything new
  // since my file tree was current?" needs no bookkeeping of its own.
  const since =
    flags.since === "last" || flags.sinceLast
      ? (await readBase(ctx, flags.baseFile ?? BASE_FILE)).cursor
      : flags.since ?? positionals[0] ?? undefined;

  const changes = await client.changes({
    site: flags.site,
    since: since ?? undefined,
    limit: flags.limit,
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

function render(changes) {
  const lines = [
    `shio changes: ${changes.found} entr${changes.found === 1 ? "y" : "ies"}` +
      `${changes.window ? " (window — pass --since <cursor> for a delta)" : ""}` +
      `${changes.more ? ", more available" : ""}`,
  ];
  for (const change of changes.changes ?? []) {
    const address = change.address ?? "(gone)";
    lines.push(`  ${change.action} ${address} — ${change.what ?? ""} [${change.by} at ${change.at}]`);
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
  }
  for (const note of changes.notes ?? []) lines.push(`  note: ${note}`);
  lines.push(`cursor: ${changes.cursor}`);
  return `${lines.join("\n")}\n`;
}
