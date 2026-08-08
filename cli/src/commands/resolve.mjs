/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio resolve <address>` (SH79) — print what a content address names.
 *
 * The companion to `shio context`: the pack tells you a post lives at
 * `/blog/hello-world` in site `Blog`, and this turns `post:Blog/blog/hello-world`
 * into the id that today's id-keyed endpoints still want. `--id` prints the bare id
 * so it composes in a shell pipeline without a JSON parser.
 */
import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { helpText } from "../help.mjs";

/**
 * What `shio resolve --help` prints (SH435).
 *
 * Here rather than in `index.mjs` so the flags and their meanings are one edit apart: the
 * global help described some commands' flags and no command's completely, and learning this
 * one's meant grepping the source for `flags.` — reading an implementation to discover an
 * interface, having already paid for a help text that answered something else.
 */
export const HELP = helpText("resolve");

export async function run(flags, positionals, ctx) {
  const address = positionals[0] ?? flags.address;
  if (!address) {
    throw new Error(
      "shio resolve: an address is required. " +
        "Example: shio resolve post:Blog/blog/hello-world",
    );
  }
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });
  const resolved = await client.resolve(address);

  if (flags.id) {
    ctx.stdout.write(`${resolved.id}\n`);
  } else {
    // Terse by default, like every other agent-facing output: one line, no JSON
    // punctuation to pay for when four fields is the whole answer.
    const parts = [resolved.kind, resolved.id, resolved.address];
    if (resolved.type) parts.push(`type=${resolved.type}`);
    if (resolved.state) parts.push(`state=${resolved.state}`);
    ctx.stdout.write(`${parts.join(" ")}\n`);
  }
  return resolved;
}
