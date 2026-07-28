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

export async function run(flags, positionals, ctx) {
  if (flags.content) {
    // SH87: the same verb over content instead of the model. It compiles the file tree to
    // a desired-state document and applies it, so `--dry-run`/`--check`/`--prune` keep the
    // meanings they already have here.
    const content = await import("./content-push.mjs");
    return content.run(flags, positionals, ctx);
  }
  return pushPostTypes(flags, ctx);
}

/** `movedFrom` is an instruction to this command, not a field of the model — never sent. */
function withoutInstructions(def) {
  const { movedFrom: _movedFrom, ...rest } = def;
  return rest;
}

async function pushPostTypes(flags, ctx) {
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const dir = flags.dir ?? "shio/post-types";
  const dryRun = Boolean(flags.dryRun);
  const prune = Boolean(flags.prune);
  const check = Boolean(flags.check);

  const client = new ShioAdminClient({ baseUrl, user, password, fetch: ctx.fetch });
  const localDefs = await readLocalDefs(ctx, dir);
  const remoteDefs = (await client.getPostTypeDefs()) ?? [];

  const { added, changed, unchanged, removed } = diffDefs(localDefs, remoteDefs);
  const { toPut, toDelete, renames, ambiguousRenames, probableRenames } =
    planPush(localDefs, remoteDefs, { prune });

  // SH235: a renamed file is a rename, not a create plus a delete. Refused before anything is
  // written, because the wrong guess here moves a content model's posts to the wrong type.
  if (ambiguousRenames.length) {
    for (const item of ambiguousRenames) {
      ctx.stderr.write(`shio push: cannot tell what "${item.to}" is — ${item.reason}.\n`
        + `  candidates: ${item.candidates.join(", ")}\n`);
    }
    ctx.stderr.write("Add `\"movedFrom\": \"<old name>\"` to the definition that was renamed, "
      + "or remove the ambiguity, then push again.\n");
    return { toPut: [], toDelete: [], renames: [], ambiguousRenames, exitCode: 1 };
  }

  const renamedFrom = new Set(renames.map((rename) => rename.from));
  ctx.stdout.write(
    `shio push: ${added.length - renames.length} added, ${changed.length} changed, ` +
      `${unchanged.length} unchanged` +
      (renames.length ? `, ${renames.length} renamed` : "") +
      `${prune ? `, ${toDelete.length} to delete` : ""}\n`
  );
  for (const rename of renames) ctx.stdout.write(`  → ${rename.from} renamed to ${rename.to}\n`);
  for (const name of added) if (!renames.some((r) => r.to === name)) ctx.stdout.write(`  + ${name}\n`);
  for (const name of changed) ctx.stdout.write(`  ~ ${name}\n`);
  for (const name of toDelete) ctx.stdout.write(`  - ${name}\n`);
  // A pair that is plausible and unprovable. Silence here is how a delete-plus-create destroys a
  // post-type's content without mentioning it, so it is printed even though nothing acts on it.
  for (const item of probableRenames) {
    ctx.stdout.write(`  ? "${item.to}" may be "${item.from}" renamed — if it is, add `
      + `\`"movedFrom": "${item.from}"\` to it so the posts come with it.\n`);
  }
  if (prune && removed.length > toDelete.length) {
    ctx.stdout.write(`  (skipped ${removed.length - toDelete.length} system type(s) — read-only)\n`);
  }

  if (check) {
    const drift = toPut.length + toDelete.length + renames.length;
    if (drift === 0) {
      ctx.stdout.write("shio push --check: in sync — git matches server.\n");
      return { toPut, toDelete, check: true, drift: false, exitCode: 0 };
    }
    const deletePart = prune ? `, ${toDelete.length} to delete` : "";
    const renamePart = renames.length ? `, ${renames.length} to rename` : "";
    ctx.stdout.write(
      `shio push --check: drift detected — ${toPut.length} to upsert${renamePart}${deletePart} `
        + "(no changes applied).\n"
    );
    return { toPut, toDelete, renames, check: true, drift: true, exitCode: 1 };
  }

  if (dryRun) {
    ctx.stdout.write("shio push: dry run — no changes applied.\n");
    return { toPut, toDelete, renames, dryRun: true };
  }

  // Renames first: an upsert against the new name before the rename lands would create the very
  // second post-type this exists to prevent.
  for (const rename of renames) await client.renamePostTypeDef(rename.from, rename.to);
  for (const def of toPut) await client.putPostTypeDef(def.name, withoutInstructions(def));
  for (const name of toDelete) await client.deletePostTypeDef(name);
  ctx.stdout.write(
    `shio push: applied ${renames.length ? `${renames.length} rename(s), ` : ""}`
      + `${toPut.length} upsert(s)${prune ? `, ${toDelete.length} delete(s)` : ""}.\n`
  );
  // The half the server cannot do: a content file's `type:` and a document's `type` name the
  // post-type too, and the server never writes a project file (SH163's `notes` say so). Reporting
  // is not fixing, and it is the honest stopping point for a command whose subject is the model.
  if (renames.length && renamedFrom.size) {
    ctx.stdout.write(
      `shio push: content that still declares ${[...renamedFrom].join(", ")} needs its \`type:\` `
        + "updated — check shio/content/** and any desired-state document, then run "
        + "`shio push --content --check`.\n"
    );
  }
  return { toPut, toDelete, renames, dryRun: false };
}
