/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio rollback` (SH987) — put a captured revision back, from the terminal.
 *
 * ## The dry run is the default, and that is the whole design
 *
 * A rollback is the one write whose subject the caller cannot see: a number names bytes that are
 * not on screen. So this plans first and prints what it would do, and `--apply` is the second word
 * that makes it happen — the same shape `shio apply` uses for a destructive document, and for the
 * same reason. `shio diff --against revision --from <n>` is the fuller answer to "what am I about
 * to bring back", and the plan line names it.
 *
 * ## It restores into the draft
 *
 * Never straight to publication: the caller asked to go back to a version, and putting that version
 * in front of visitors is the separate explicit act (P7). What lands is a draft, and `shio publish`
 * is what makes it live — which is also what gives somebody a chance to look at it first.
 */
import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { helpText } from "../help.mjs";

export const HELP = helpText("rollback");

export async function run(flags, positionals, ctx) {
  const address = typeof flags.address === "string" && flags.address.trim()
    ? flags.address.trim()
    : String(positionals?.[0] ?? "").trim();
  const to = Number.parseInt(String(flags.to ?? ""), 10);

  if (!address || !Number.isInteger(to)) {
    ctx.stderr.write(
      "shio rollback: needs an address and a revision.\n"
        + "  shio rollback post:<site>/<url> --to <n>        plans it\n"
        + "  shio rollback post:<site>/<url> --to <n> --apply  writes it\n"
        + "  `shio versions post:<site>/<url>` lists the numbers.\n",
    );
    return 2;
  }

  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });
  const op = { op: "post.rollback", address, revision: to };
  const dryRun = flags.apply !== true;

  const result = await client.batch({ dryRun, ops: [op] });
  if (flags.json) {
    ctx.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
    return 0;
  }

  const change = result?.results?.[0];
  ctx.stdout.write(
    `shio rollback ${address}\n`
      + `  ${dryRun ? "would" : "did"}: ${change?.detail ?? change?.action ?? "nothing"}\n`
      + (dryRun
        ? `  See what changes: shio diff --against revision --from ${to} --address ${address}\n`
          + `  Then: shio rollback ${address} --to ${to} --apply\n`
        : "  It landed in the draft. `shio publish` is what makes it live.\n"),
  );
  return 0;
}
