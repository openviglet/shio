/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio status` (SH1050) — the instance in one call, for an agent that has just arrived.
 *
 * ## Why this is not `shio context`
 *
 * The context pack (SH114) answers "what can I do here": the model, the conventions, the sites.
 * This answers "what is going on here", and the difference is what a number means. A pack tells an
 * agent that a review queue exists; this tells it that two sessions are sitting in one. The first
 * is a capability and the second is a fact about right now, and a pack that carried facts about
 * right now would be a pack nobody could cache.
 *
 * ## One endpoint, two readers
 *
 * `/api/v2/console/summary` is what the console's home draws. Printing the same rows here rather
 * than composing a second set of reads is P10 applied to a dashboard: two implementations of "how
 * is this instance doing" drift within a release, and the one nobody looks at drifts first.
 */
import { ShioAdminClient } from "../admin-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { helpText } from "../help.mjs";

export const HELP = helpText("status");

export async function run(flags, positionals, ctx) {
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAdminClient({ baseUrl, user, password, fetch: ctx.fetch });
  const summary = await client.consoleSummary();

  if (!summary) {
    ctx.stderr.write(
      `shio status: ${baseUrl} has no /api/v2/console/summary.\n`
        + "  That endpoint arrived with SH1050. An older instance answers 404 here, and there is "
        + "no second way to ask — `shio context` is the capability read, not this one.\n",
    );
    return 1;
  }

  if (flags.json) {
    ctx.stdout.write(`${JSON.stringify(summary, null, 2)}\n`);
    return 0;
  }

  ctx.stdout.write(render(baseUrl, summary));
  return 0;
}

/** How many things are owed, which is what decides whether the section is printed at all. */
export function owed(attention) {
  if (!attention) return 0;
  return (attention.reviewSessions ?? 0)
    + (attention.scheduledSoon ?? 0)
    + (attention.openNotes ?? 0);
}

/**
 * The report, and the one formatting decision worth stating.
 *
 * <p>A row is printed only when its number is not zero. A block of zeroes reads as "nothing is
 * wrong" to a person and as three more lines to an agent paying for them, and neither reader
 * gains anything from being told that no notes are open. When everything is clear it says so, in
 * one line.</p>
 */
export function render(baseUrl, summary) {
  const lines = [`${baseUrl} — Shio ${summary.version ?? "unknown"}`];
  lines.push(`  ${summary.sites ?? 0} site(s), ${summary.postTypes ?? 0} post type(s)`);

  const attention = summary.attention ?? {};
  if (owed(attention) === 0) {
    lines.push("  nothing waiting");
  } else {
    lines.push("  waiting:");
    if (attention.reviewSessions) {
      lines.push(`    review    ${attention.reviewSessions} session(s), `
        + `${attention.reviewChanges ?? 0} change(s)`);
    }
    if (attention.scheduledSoon) {
      lines.push(`    scheduled ${attention.scheduledSoon} in the next 7 days`);
    }
    if (attention.openNotes) {
      lines.push(`    notes     ${attention.openNotes} open`);
    }
  }

  const runs = summary.runs ?? [];
  if (runs.length) {
    lines.push("  runs:");
    for (const run of runs.slice(0, 4)) {
      // The intent first, because it is the thing a reader can act on; the session id is what is
      // always there. `waiting` is how much of the run nobody has decided on yet.
      const what = run.intent || run.agent || run.session;
      const waiting = run.pending ? `, ${run.pending} waiting` : "";
      lines.push(`    ${String(what).slice(0, 60)}  (${run.changes ?? 0} change(s)${waiting})`);
    }
  }

  const health = summary.diagnostics ?? {};
  if (health.incidents || health.unpersisted) {
    // A window and not a total: the log starts empty at boot, so the sentence says since when.
    lines.push(`  incidents: ${health.incidents ?? 0} since this process started`
      + (health.unpersisted ? `, ${health.unpersisted} that could not be written down` : ""));
  }

  const activity = summary.activity ?? [];
  if (activity.length) {
    lines.push("  recent:");
    for (const row of activity.slice(0, 5)) {
      const when = String(row.date ?? "").slice(0, 16).replace("T", " ");
      lines.push(`    ${when}  ${row.action ?? "?"}  ${row.description ?? ""}`.trimEnd());
    }
  }
  return `${lines.join("\n")}\n`;
}
