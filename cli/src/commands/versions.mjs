/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio versions` (SH987) — what a page has been, as a list somebody can act on.
 *
 * ## Why a command and not a flag on `shio diff`
 *
 * A diff answers "what is different between two things I can name". Before you can name them you
 * have to know what there is, and until SH986 there was nothing to know: a post had a draft row and
 * a live row, and publishing destroyed the second. This is the read that makes the other two verbs
 * usable — `shio diff --against revision --from <n>` and `shio rollback --to <n>` both take a number
 * that comes from here.
 *
 * ## It carries no bodies, deliberately
 *
 * `include=revisions` answers with numbers, dates, actors and the field names each revision changed.
 * Fifty revisions of a page with a long body is the page fifty times for a question the metadata
 * answers — and the one revision a caller then wants is one `--from` away.
 */
import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { helpText } from "../help.mjs";

export const HELP = helpText("versions");

export async function run(flags, positionals, ctx) {
  const address = typeof flags.address === "string" && flags.address.trim()
    ? flags.address.trim()
    : String(positionals?.[0] ?? "").trim();
  if (!address) {
    ctx.stderr.write(
      "shio versions: needs an address.\n"
        + "  shio versions post:<site>/<url>\n"
        + "  It lists what that page has been: a number per captured revision, when it was "
        + "captured, who by, and which fields it changed.\n",
    );
    return 2;
  }

  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });
  const [doc] = await client.read([address], { include: "revisions" });
  const revisions = doc?.revisions ?? [];

  if (flags.json) {
    ctx.stdout.write(`${JSON.stringify(revisions, null, 2)}\n`);
    return 0;
  }

  ctx.stdout.write(render(address, revisions));
  // Exit non-zero on none, the way the other read commands report an empty answer a caller was
  // about to act on: a rollback script that reads this and finds nothing should stop rather than
  // go on to guess a number.
  return revisions.length ? 0 : 1;
}

function render(address, revisions) {
  if (!revisions.length) {
    return `shio versions ${address}: none captured.\n`
      + "  A revision is written when content goes live, so a page that has never been published "
      + "has none.\n";
  }
  const lines = [`shio versions ${address}: ${revisions.length}`];
  for (const revision of revisions) {
    lines.push(`  ${String(revision.version).padStart(4)}  ${revision.reason ?? ""}`
      + `  ${revision.capturedAt ?? ""}  ${revision.actor ?? "—"}`);
    // SH998 — a waived gate is the one thing a revision has to say for itself, and the place a
    // reader who started at the content will look. Printed above `changed`, because "somebody
    // overrode a check" outranks which fields moved.
    if (revision.note) {
      lines.push(`        ${revision.note}`);
    }
    if (revision.changed?.length) {
      lines.push(`        changed: ${revision.changed.join(", ")}`);
    }
  }
  return `${lines.join("\n")}\n`;
}
