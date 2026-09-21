/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio index status` and `shio index reindex` (SH1063) — is what this instance publishes
 * findable, and push it again when it is not.
 *
 * The question search exists for had no surface at all: indexing was a properties class, a
 * listener and an incident log entry. `status` prints the routing table — every site with the
 * search site it resolves to — because the failure that costs most is a site that maps to
 * nothing: the listener builds a document, finds no route, sends nothing, and raises no error.
 *
 * `reindex` rides `site.upsert data.reindex` (SH413), the path `ShAgentSearchReindexTest` already
 * covers, so this command adds a way in rather than a second way to do it. `--dry-run` is the
 * apply path's own word and answers "how many would be sent" without sending any.
 */
import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { helpText } from "../help.mjs";

export const HELP = helpText("index");

export async function run(flags, positionals, ctx) {
  const action = String(positionals?.[0] ?? "status").toLowerCase();
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });

  if (action === "status") {
    return status(client, flags, ctx);
  }
  if (action === "reindex") {
    return reindex(client, flags, ctx);
  }
  ctx.stderr.write(`shio index: no action called '${action}'. Try: status, reindex\n`);
  return 2;
}

/**
 * The sites that publish into nothing: indexing on, pages published, no search site.
 *
 * The exit code is built from this rather than from the text, so "a script can act on it" and
 * "a person can read it" cannot come apart.
 */
export function unroutable(answer) {
  if (!answer?.enabled) return [];
  return (answer.sites ?? []).filter((route) => !route.searchSite && route.published > 0);
}

/** The routing table as lines — the whole of what this command prints. */
export function render(answer) {
  const lines = [answer?.enabled
    ? `indexing on -> ${answer.target ?? "(no target)"}`
    : "indexing off"];
  for (const route of answer?.sites ?? []) {
    // A site that routes nowhere is spelled out rather than left blank: a blank column reads as
    // a rendering problem, and this is the row the reader came for.
    const target = route.searchSite ?? "(nowhere)";
    const adrift = answer.enabled && !route.searchSite && route.published > 0;
    lines.push(`  ${route.site} -> ${target}  ${route.published} published`
      + `${adrift ? "  UNROUTABLE" : ""}`);
  }
  if (answer?.failures > 0) {
    lines.push(`${answer.failures} indexing failure(s) in the recent window `
      + "(shio report, or shio://diagnostics)");
  }
  return `${lines.join("\n")}\n`;
}

async function status(client, flags, ctx) {
  const answer = await client.indexStatus();
  if (flags.json) {
    ctx.stdout.write(`${JSON.stringify(answer, null, 2)}\n`);
    return 0;
  }
  ctx.stdout.write(render(answer));
  // Exit 1 when something published cannot reach the index: a status command that always exits 0
  // is one a script cannot act on, and this is the state worth acting on.
  return unroutable(answer).length > 0 ? 1 : 0;
}

async function reindex(client, flags, ctx) {
  const site = flags.site ? String(flags.site) : null;
  if (!site) {
    ctx.stderr.write("shio index reindex: --site <name> is required\n");
    return 2;
  }
  const dryRun = flags["dry-run"] === true || flags.dryRun === true;
  // The op surface and not `apply`: a reindex is an act, not a state to converge to, and
  // expressing it as a desired-state document would restate a site this run is not changing.
  const answer = await client.batch({
    dryRun,
    ops: [{ op: "site.upsert", address: `site:${site}`, data: { reindex: true } }],
  });
  if (flags.json) {
    ctx.stdout.write(`${JSON.stringify(answer, null, 2)}\n`);
    return 0;
  }
  const changes = answer?.changes ?? [];
  const line = changes.map((change) => change.note ?? change.change).filter(Boolean).join("; ");
  ctx.stdout.write(`${dryRun ? "would reindex" : "reindexed"} ${site}: ${line || "nothing to send"}\n`);
  return 0;
}
