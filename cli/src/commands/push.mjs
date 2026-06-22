/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * `shio push` — apply local post-type defs back to Shio. Computes a diff against
 * the live model, then PUTs added/changed types (and, with `--prune`, DELETEs
 * remote types absent locally — never a system type). `--dry-run` prints the
 * plan without writing. `--check` (SH65) is the CI drift guard: same compile +
 * diff, writes nothing, but exits non-zero when the plan is non-empty so a
 * pipeline can assert "git == server". Local sources may be `*.json` or
 * `*.ts`/`*.mjs` DSL modules (SH64) — both are compiled to canonical defs first.
 */
import { ShioAdminClient } from "../admin-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { diffDefs, planPush } from "../post-types-io.mjs";
import { readLocalDefs } from "../post-types-compile.mjs";

export async function run(flags, _positionals, ctx) {
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const dir = flags.dir ?? "shio/post-types";
  const dryRun = Boolean(flags.dryRun);
  const prune = Boolean(flags.prune);
  const check = Boolean(flags.check);

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

  if (check) {
    const drift = toPut.length + toDelete.length;
    if (drift === 0) {
      ctx.stdout.write("shio push --check: in sync — git matches server.\n");
      return { toPut, toDelete, check: true, drift: false, exitCode: 0 };
    }
    const deletePart = prune ? `, ${toDelete.length} to delete` : "";
    ctx.stdout.write(
      `shio push --check: drift detected — ${toPut.length} to upsert${deletePart} (no changes applied).\n`
    );
    return { toPut, toDelete, check: true, drift: true, exitCode: 1 };
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
