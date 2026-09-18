/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio find` (SH1022) — the posts matching a query, as addresses.
 *
 * ## Why the CLI gets a listing at all
 *
 * It already had one, four times over, and none of them was this. `pull --content` lists to
 * project a whole site to disk, `publish --all` lists to pick targets, `verify` lists to check —
 * each walks `/agent/find` for its own purpose and none of them will answer a question. So the one
 * thing a person or an agent at a terminal wants first — *which pages are drafts somebody changed
 * this week* — had no spelling here, and the answer was a `curl` with a hand-built query string.
 *
 * ## One vocabulary
 *
 * Every flag below is a word `/agent/find`, `shio_find` and the console listing spell the same
 * way, and the argument parser's kebab-to-camel rule means `--modified-since` arrives as exactly
 * the key the server takes. That is not a coincidence to preserve by care:
 * `cli/test/content-query.test.mjs` reads the vocabulary out of the Java record and fails when a
 * word exists on one side and not the other.
 *
 * ## It walks to the end
 *
 * `--limit` caps the rows a page returns, not the answer. A truncated result carries a cursor and
 * `findAll` follows it, because a short list is not a smaller answer — it is one with holes, and a
 * caller acting on "these are the drafts" would act on some of them.
 */
import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { helpText } from "../help.mjs";

export const HELP = helpText("find");

/**
 * The words this command passes through, which are the server's own (SH1022).
 *
 * `site` and `folder` are addresses, `subtree` scopes the second, and the rest are the filters.
 * Spelled here rather than forwarded wholesale so a typo is a flag this command ignores and the
 * server never sees — `--modifed-since 2026-09-01` would otherwise be sent as a parameter the
 * server refuses, which is a worse message than the one the usage text gives.
 */
export const QUERY_FLAGS = [
  "site", "folder", "subtree", "type", "state", "locale",
  "modifiedSince", "modifiedBefore", "author", "modifier", "q",
];

export async function run(flags, positionals, ctx) {
  const query = {};
  for (const name of QUERY_FLAGS) {
    const value = flags[name];
    if (value === undefined || value === "" || value === false) continue;
    query[name] = value === true ? "true" : String(value);
  }
  // A bare positional is the folder, because that is what somebody types first: `shio find /blog`.
  if (!query.folder && positionals?.[0]) {
    query.folder = String(positionals[0]);
  }
  const limit = Number.parseInt(String(flags.limit ?? ""), 10);
  if (Number.isFinite(limit) && limit > 0) {
    query.limit = limit;
  }

  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });
  const { results, truncated } = await client.findAll(query);

  if (flags.json) {
    ctx.stdout.write(`${JSON.stringify({
      count: results.length, truncated, results, console: consoleUrl(baseUrl, query),
    }, null, 2)}\n`);
    return 0;
  }

  ctx.stdout.write(render(results, truncated, query));
  ctx.stdout.write(`  ${consoleUrl(baseUrl, query)}\n`);
  // Non-zero on none, the way the other read commands report an answer somebody was about to act
  // on: a script that finds nothing should stop rather than go on to publish an empty selection.
  return results.length ? 0 : 1;
}

function render(results, truncated, query) {
  const asked = Object.entries(query)
    .filter(([key]) => key !== "limit")
    .map(([key, value]) => `${key}=${value}`)
    .join(" ");
  if (!results.length) {
    return `shio find${asked ? ` ${asked}` : ""}: no posts.\n`
      + "  Every filter is a predicate, so an empty answer means nothing matched — not that the "
      + "filter was dropped. An unknown one is refused with the list of words that exist.\n";
  }
  const lines = [`shio find${asked ? ` ${asked}` : ""}: ${results.length}`];
  for (const row of results) {
    const state = row.state ? row.state.toLowerCase() : "";
    const live = row.live ? " live" : "";
    lines.push(`  ${row.address}  [${row.type ?? "?"} ${state}${live}]  ${row.title ?? ""}`.trimEnd());
  }
  if (truncated) {
    lines.push("  (truncated: this instance offered no cursor to continue with)");
  }
  return `${lines.join("\n")}\n`;
}

/**
 * The console address that shows this same answer (SH1023).
 *
 * The search page rather than the content browser, and the reason is the addressing: the browser's
 * route names a folder by **id**, which this command does not have and would have to resolve; the
 * search page is scoped by the same words this query is written in. So the URL is exact for every
 * filter that page serves and a starting point for the rest — the honest shape while the two lists
 * serve different halves of the vocabulary, rather than a link that silently drops a filter and
 * shows more rows than the run did.
 *
 * Printed rather than put behind a flag: it is one line, it costs no request, and the point of a
 * shared vocabulary is that handing the answer to a person is cheap (P9).
 */
export function consoleUrl(baseUrl, query) {
  const url = new URL("/bento/content/search", baseUrl);
  for (const key of ["q", "site"]) {
    if (query[key]) url.searchParams.set(key, String(query[key]));
  }
  return url.toString();
}
