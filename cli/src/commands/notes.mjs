/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio notes` (SH1003) — what a curator is still asking for.
 *
 * ## Why this is a work list and not a thread reader
 *
 * A thread belongs to a page, and the page's own read carries it (`include=notes`). What a terminal
 * is for is the other direction: everything still open, across every site, so a session can pick up
 * where a person left off and a pipeline can refuse to ship with requests outstanding. Each row
 * names its page by address, because a note names something somebody has to go and fix and a uuid
 * names nothing anybody can act on.
 */
import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { helpText } from "../help.mjs";

export const HELP = helpText("notes");

export async function run(flags, positionals, ctx) {
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });

  const notes = await client.openNotes({
    site: flags.site,
    kind: flags.kind,
    limit: flags.limit,
  });

  if (flags.json) {
    ctx.stdout.write(`${JSON.stringify(notes, null, 2)}\n`);
    return { notes, exitCode: flags.strict && notes.length > 0 ? 1 : 0 };
  }

  const lines = [`shio notes: ${notes.length} open`];
  for (const note of notes) {
    lines.push(`  ${note.kind} ${note.address ?? "(gone)"}${note.field ? ` ${note.field}` : ""}` +
      `${note.author ? ` — ${note.author}` : ""}`);
    // The body on its own line: it is the only unbounded field, and the one being read for.
    lines.push(`      ${note.body}`);
  }
  ctx.stdout.write(`${lines.join("\n")}\n`);
  // `--strict` is the CI shape the rest of this CLI uses: a pipeline that will not ship with
  // requests outstanding needs one exit code, not a grep over prose.
  return { notes, exitCode: flags.strict && notes.length > 0 ? 1 : 0 };
}
