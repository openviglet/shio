/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio export site:<name>` (SH1065) — hand a site to somebody else, or to another instance.
 *
 * ## The half that was missing
 *
 * `shio import` has shipped since SH268 and the console has had a Download action since Block AR,
 * so a site could be accepted and a curator could fetch one — and a pipeline could not. The route
 * is `GET /api/v2/site/{id}/export` and stays strictly id-keyed on purpose (a near-miss resolved
 * by name would export the wrong site), which meant the one export of an address-native product
 * could not be reached with an address. This resolves the address and streams the zip, so the
 * asymmetry import's own comment named is closed on the surface that asked for it.
 *
 * ## No MCP half, for import's reason and not a new one
 *
 * An SH68 package is a zip. SH268 settled that a client with no filesystem can neither read one nor
 * put one in a JSON-RPC frame, and that base64 in a message is a megabyte of tokens for something
 * one line of shell does — so the manifest's `elsewhere` map publishes where the capability lives
 * instead, which is how an MCP client learns it rather than discovering it by failing. The design
 * asked for a resource here; a resource returning the bytes is the same megabyte, and one returning
 * a pointer is the manifest entry written twice.
 */
import { writeFile } from "node:fs/promises";

import { ShioAdminClient } from "../admin-client.mjs";
import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { helpText } from "../help.mjs";

export const HELP = helpText("export");

/** `site:Acme`, `Acme` or `--site Acme` — one site, named the way the rest of the CLI names one. */
export function siteOf(flags, positionals) {
  const raw = String(positionals?.[0] ?? flags?.site ?? "").trim();
  if (!raw) return null;
  return raw.startsWith("site:") ? raw.slice("site:".length) : raw;
}

/** What the run wrote, as one line: the path, the size, and which site it came from. */
export function render({ file, bytes, site }) {
  return `wrote ${file} (${Math.max(1, Math.round(bytes / 1024))} KB) from site:${site}\n`;
}

export async function run(flags, positionals, ctx) {
  const site = siteOf(flags, positionals);
  if (!site) {
    throw new Error(
      "shio export: a site is required. Example: shio export site:Acme --out acme.zip",
    );
  }
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  // Two clients, because the two halves live on two surfaces: the address resolves on the agent
  // surface (which owns SH79) and the zip comes off the console route (which owns the exchange).
  const agent = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });
  const admin = new ShioAdminClient({ baseUrl, user, password, fetch: ctx.fetch });

  // The server's teaching refusal is the useful answer for a site that is not there — it names the
  // sites that are — so nothing here checks first.
  const resolved = await agent.resolve(`site:${site}`);
  const { bytes, fileName } = await admin.exportSite(resolved.id);
  const file = flags.out ? String(flags.out) : fileName ?? `${site}.zip`;
  await (ctx.writeFile ?? writeFile)(file, bytes);

  if (flags.json) {
    ctx.stdout.write(`${JSON.stringify({ site, file, bytes: bytes.length }, null, 2)}\n`);
    return { site, file, bytes: bytes.length };
  }
  ctx.stdout.write(render({ file, bytes: bytes.length, site }));
  return { site, file, bytes: bytes.length };
}
