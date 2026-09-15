/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio schedule` (SH991) — what is due to go live or come down, in the order it will happen.
 *
 * ## A read over the listing, not a second endpoint
 *
 * `/agent/find?scheduled=` already answers "which posts have a schedule, and when" (SH991's first
 * slice). What it does not do is put them in time order: find sorts by address, because that is its
 * page cursor, and an agenda sorted by address is a list a person has to re-sort in their head. So
 * this walks every page and sorts here, where the order costs nothing and the cursor is not at stake.
 *
 * ## Times are printed in UTC and say so
 *
 * A schedule is stored as an instant. Printing it in the machine's local zone would make the same
 * agenda read differently on a laptop and on a CI runner, and the console dialog SH991 also fixes is
 * the proof of what an unstated zone costs. `--json` returns the rows as the server sent them.
 */
import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { helpText } from "../help.mjs";

export const HELP = helpText("schedule");

export async function run(flags, positionals, ctx) {
  const site = flags.site ?? positionals?.[0];
  const window = flags.from || flags.to ? `${flags.from ?? ""}..${flags.to ?? ""}` : "any";

  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });
  const { results, truncated } = await client.findAll({ site, scheduled: window, limit: 500 });

  const entries = agenda(results, flags.from, flags.to);
  if (flags.json) {
    ctx.stdout.write(`${JSON.stringify(entries, null, 2)}\n`);
    return truncated ? 1 : 0;
  }
  ctx.stdout.write(render(entries, site, window, truncated));
  // An agenda that stopped short must not read as a complete one (SH146's rule for a walk).
  return truncated ? 1 : 0;
}

/**
 * One entry per scheduled transition, earliest first.
 *
 * A post scheduled to go live and to come down is two entries, because they are two moments; a
 * window that holds only one of them shows only that one, which is what the range asked.
 */
export function agenda(rows, from, to) {
  const inWindow = (at) => (!from || at >= from) && (!to || at < to);
  const entries = [];
  for (const row of rows) {
    if (row.publishAt && inWindow(row.publishAt)) {
      entries.push({ at: row.publishAt, action: "publish", address: row.address, title: row.title });
    }
    if (row.unpublishAt && inWindow(row.unpublishAt)) {
      entries.push({ at: row.unpublishAt, action: "unpublish", address: row.address, title: row.title });
    }
  }
  // ISO-8601 instants in UTC compare as strings, and the address breaks a tie so two runs agree.
  return entries.sort((a, b) => a.at.localeCompare(b.at) || a.address.localeCompare(b.address));
}

function render(entries, site, window, truncated) {
  const scope = `${site ? ` ${site}` : ""}${window === "any" ? "" : ` ${window}`}`;
  if (!entries.length) {
    return `shio schedule${scope}: nothing scheduled (times are UTC)\n`;
  }
  const lines = [`shio schedule${scope}: ${entries.length} (times are UTC)`];
  for (const entry of entries) {
    lines.push(`  ${entry.at}  ${entry.action.padEnd(9)}  ${entry.address}`
      + `${entry.title ? `  ${entry.title}` : ""}`);
  }
  if (truncated) {
    lines.push("  note: the listing stopped short, so this agenda may be missing entries");
  }
  return `${lines.join("\n")}\n`;
}
