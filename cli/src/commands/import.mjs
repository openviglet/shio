/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio import <file.zip>` (SH268) — accept a site somebody hands you.
 *
 * ## Why this is the CLI's half, and why there is no MCP half
 *
 * An agent could build a site from nothing and could not accept one, which is the cheapest possible
 * starting point and the form every first-party template ships in. SH189 made the endpoint reachable
 * by a credential client (its CSRF is narrowed to session-bearing callers), so this half is a
 * multipart POST and a line of output.
 *
 * The MCP half is **deliberately absent**, and SH186 is what settles it rather than taste. That task
 * closed the same shape for the render model: a REST-only capability whose input is *JSON* can become
 * an op (`render.provision`) and reach an MCP client through an existing tool, with no thirteenth
 * entry on `tools/list`. An SH68 package is a **zip**. A client with no filesystem can neither read
 * one nor put one in a JSON-RPC message, and the two ways to pretend otherwise are both worse than
 * the honest answer: a URL the server fetches is SH101's request-forgery surface argued a second time,
 * and a base64 body is a megabyte of tokens for something `shio import` does in one line. So the
 * capability is **CLI-shaped**, the P1 inventory says so as a decision rather than a gap, and an MCP
 * client learns it from the manifest instead of discovering it by failing.
 */
import { readFile } from "node:fs/promises";
import { basename } from "node:path";

import { ShioAdminClient } from "../admin-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { helpText } from "../help.mjs";

/**
 * What `shio import --help` prints (SH435).
 *
 * Here rather than in `index.mjs` so the flags and their meanings are one edit apart: the
 * global help described some commands' flags and no command's completely, and learning this
 * one's meant grepping the source for `flags.` — reading an implementation to discover an
 * interface, having already paid for a help text that answered something else.
 */
export const HELP = helpText("import");

export async function run(flags, positionals, ctx) {
  const file = positionals[0] ?? flags.file;
  if (!file) {
    throw new Error(
      "shio import: a package file is required. " +
        "Example: shio import site.zip (an export from GET /api/v2/site/{id}/export)",
    );
  }
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAdminClient({ baseUrl, user, password, fetch: ctx.fetch });

  const bytes = await (ctx.readFile ?? readFile)(file);
  const answer = await client.importPackage({ bytes, fileName: basename(file) });
  // SH1079 — the answer is `{site, arrived, problems}`. An instance from before it answers with the
  // bare site, and reading either is cheaper than a version check.
  const site = answer?.site ?? answer;

  // Terse, like every other agent-facing output: the site's name is what the next command addresses
  // it by (SH79), so it is what this prints rather than the id the response leads with.
  ctx.stdout.write(`imported site:${site.name ?? site.id}\n`);
  const arrived = answer?.arrived;
  if (arrived) {
    // SH1134 — `files` counts the File posts whose bytes were restored; an older instance omits it.
    ctx.stdout.write(`arrived: ${arrived.folders} folder(s), ${arrived.posts} post(s), `
      + `${arrived.postTypesCreated} new post type(s)`
      + `${arrived.files === undefined ? "" : `, ${arrived.files} file(s)`}\n`);
  }
  // One line each, so a skipped post or a missing type is something the caller sees rather than
  // something it finds later with a verify run.
  for (const problem of answer?.problems ?? []) {
    ctx.stdout.write(`problem: ${problem.code} ${problem.subject ?? ""} — ${problem.message}\n`);
  }
  return answer;
}
