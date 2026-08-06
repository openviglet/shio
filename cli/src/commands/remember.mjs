/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio remember` (SH114) — write down what the next session would otherwise
 * re-derive, and read back what earlier ones wrote.
 *
 * This is the command form of the only feature in the token-economy block that
 * compounds. Everything else makes one session cheaper; a note makes every session
 * after it cheaper, because the context pack an agent already reads first carries it.
 *
 * Why a command and not an MCP tool: a tool's schema is re-sent on every turn of
 * every session (P3), and `shio remember` costs nothing per turn — a coding agent
 * has a shell, and the context pack tells it this command exists. Paying ~110 tokens
 * a turn forever to save the same agent one `Bash` call is the wrong trade for a
 * capability used a handful of times per session.
 */
import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";

/**
 * What `shio remember --help` prints (SH435).
 *
 * Here rather than in `index.mjs` so the flags and their meanings are one edit apart: the
 * global help described some commands' flags and no command's completely, and learning this
 * one's meant grepping the source for `flags.` — reading an implementation to discover an
 * interface, having already paid for a help text that answered something else.
 */
export const HELP = `shio remember [key] [note] — write down a decision the next session should not re-derive

Bare, it lists what this instance has been told. With a key and a note, it writes one
(idempotently, by key).

  --list               List the notes (the default with no positional)
  --forget             Drop the note this key names
  --site <name>        Scope to one site's notes (instance-wide when omitted)
  --limit <n>          Notes to print

Examples:
  shio remember
  shio remember frontend-routes "/tags/* /search" --site Acme
  shio remember frontend-routes --forget --site Acme
`;

export async function run(flags, positionals, ctx) {
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });

  if (flags.list || positionals.length === 0) {
    return list(client, flags, ctx);
  }

  const [key, ...rest] = positionals;
  if (flags.forget) {
    await client.forget({ key, scope: flags.site });
    ctx.stdout.write(`shio remember: forgot ${label(key, flags.site)}\n`);
    return { forgot: key, scope: flags.site ?? null };
  }

  const note = rest.join(" ").trim();
  if (!note) {
    throw new Error(
      `shio remember: nothing to remember for "${key}". ` +
        `Pass the note as the remaining arguments, or --forget to delete it. ` +
        `Example: shio remember blog.post-type "This site's blog uses Article, not Post."`,
    );
  }

  const saved = await client.remember({ key, note, scope: flags.site });
  ctx.stdout.write(`shio remember: ${label(key, flags.site)} — ${saved.note}\n`);
  return saved;
}

/**
 * The listing. Printed as `key<tab>note` rather than JSON because the common use is
 * a human or an agent scanning what is already known before adding to it, and a
 * table of two columns is the cheapest form of that.
 */
async function list(client, flags, ctx) {
  const { found, notes } = await client.memory({ site: flags.site, limit: flags.limit });
  if (!notes || notes.length === 0) {
    ctx.stdout.write(
      "shio remember: nothing remembered yet. " +
        'Add one: shio remember <key> "<note>"\n',
    );
    return { found: 0, notes: [] };
  }
  for (const note of notes) {
    ctx.stdout.write(`${label(note.key, note.scope)}\t${note.note}\n`);
  }
  // A cut has to be visible, or the reader treats a page as the whole set (SH138).
  if (found > notes.length) {
    ctx.stdout.write(
      `shio remember: showing ${notes.length} of ${found}; pass --limit ${found} for all\n`,
    );
  }
  return { found, notes };
}

/** `key` for an instance-wide note, `key@site` for a scoped one. */
function label(key, scope) {
  return scope ? `${key}@${scope}` : String(key);
}
