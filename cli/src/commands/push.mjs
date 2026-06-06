/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * `shio push` — apply local post-type defs back to Shio. Computes a diff against
 * the live model, then PUTs added/changed types (and, with `--prune`, DELETEs
 * remote types absent locally — never a system type). `--dry-run` prints the
 * plan without writing.
 */
import { join } from "node:path";

import { ShioAdminClient } from "../admin-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { diffDefs, planPush } from "../post-types-io.mjs";

async function readLocalDefs(ctx, dir) {
  const target = join(ctx.cwd, dir);
  let entries;
  try {
    entries = await ctx.fs.readdir(target);
  } catch {
    throw new Error(`shio push: no post-type directory at "${dir}" — run "shio pull" first.`);
  }
  const defs = [];
  for (const name of entries.filter((f) => f.endsWith(".json")).sort()) {
    const raw = await ctx.fs.readFile(join(target, name), "utf8");
    defs.push(JSON.parse(raw));
  }
  return defs;
}

export async function run(flags, _positionals, ctx) {
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const dir = flags.dir ?? "shio/post-types";
  const dryRun = Boolean(flags.dryRun);
  const prune = Boolean(flags.prune);

  const client = new ShioAdminClient({ baseUrl, user, password, fetch: ctx.fetch });
  const localDefs = await readLocalDefs(ctx, dir);
  const remoteDefs = (await client.getPostTypeDefs()) ?? [];

  const { added, changed, unchanged, removed } = diffDefs(localDefs, remoteDefs);
  const { toPut, toDelete } = planPush(localDefs, remoteDefs, { prune });

  ctx.stdout.write(
    `shio push: ${added.length} added, ${changed.length} changed, ` +
      `${unchanged.length} unchanged${prune ? `, ${toDelete.length} to delete` : ""}\n`
  );
  for (const name of added) ctx.stdout.write(`  + ${name}\n`);
  for (const name of changed) ctx.stdout.write(`  ~ ${name}\n`);
  for (const name of toDelete) ctx.stdout.write(`  - ${name}\n`);
  if (prune && removed.length > toDelete.length) {
    ctx.stdout.write(`  (skipped ${removed.length - toDelete.length} system type(s) — read-only)\n`);
  }

  if (dryRun) {
    ctx.stdout.write("shio push: dry run — no changes applied.\n");
    return { toPut, toDelete, dryRun: true };
  }

  for (const def of toPut) await client.putPostTypeDef(def.name, def);
  for (const name of toDelete) await client.deletePostTypeDef(name);
  ctx.stdout.write(`shio push: applied ${toPut.length} upsert(s)${prune ? `, ${toDelete.length} delete(s)` : ""}.\n`);
  return { toPut, toDelete, dryRun: false };
}
