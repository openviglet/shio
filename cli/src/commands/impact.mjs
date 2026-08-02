/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * `shio impact <address>` (SH288) — what a change to this template would affect.
 *
 * ## Why this and not `shio read`
 *
 * The gap the task was filed against was "the CLI cannot show one post". For a post's
 * *content* that gap is deliberate: `shio pull --content` puts every post on disk, where
 * `Grep` and `Edit` are the cheapest tools there are (P4), and a `read` command would be a
 * worse version of a file — one that costs a request, cannot be diffed and goes stale the
 * moment it is printed.
 *
 * The blast radius is the one question the files genuinely cannot answer. It is **computed**
 * from the site's `postTypeLayout` bindings and the slots in the layout's HTML (SH193), not
 * stored in the post, so no projected file holds it and no `grep` can find it. Before this
 * a terminal user had no path to it at all — `include=impact` (SH262) existed only for a
 * caller writing HTTP by hand.
 *
 * So: one command, one question, and `read` stays unbuilt on purpose.
 */
import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";

export async function run(flags, positionals, ctx) {
  const address = positionals[0] ?? flags.address;
  if (!address) {
    throw new Error(
      "shio impact: an address is required. " +
        "Example: shio impact post:Blog/regions/nav",
    );
  }
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });
  // `read` answers with a LIST — it takes several addresses so `pull --content` costs one
  // request per slice rather than per post. One address in, one doc out.
  const docs = await client.read([address], { include: "impact" });
  const doc = Array.isArray(docs) ? docs[0] : docs;

  if (flags.json) {
    ctx.stdout.write(`${JSON.stringify(doc?.impact ?? null)}\n`);
    return doc?.impact ?? null;
  }

  const impact = doc?.impact;
  if (!impact) {
    // Not an error: a caller sweeping a mixed list of addresses should not have to know
    // which are templates before it asks (SH262's rule for the HTTP surface, kept here).
    // The sentence says which of the two silences this is.
    ctx.stdout.write(
      `${doc?.address ?? address} is not a template ` +
        "(only PageLayout, Region and Theme have a blast radius)\n",
    );
    return null;
  }

  // Terse by default, like `resolve`: the whole answer is six fields, and JSON punctuation
  // is tokens an agent pays for and a human reads around.
  const layouts = impact.layouts?.length ? impact.layouts.join(", ") : "none";
  const types = impact.postTypes?.length ? impact.postTypes.join(", ") : "none";
  ctx.stdout.write(
    `${impact.kind} ${impact.title}\n` +
      `  layouts: ${layouts}\n` +
      `  types:   ${types}\n` +
      `  pages:   ${impact.pages ?? 0} draft, ${impact.published ?? 0} published\n`,
  );
  return impact;
}
