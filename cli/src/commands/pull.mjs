/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * `shio pull` — fetch the lossless post-type defs (console Basic auth) and write
 * one deterministic JSON file per type under `shio/post-types/`. The other half
 * of "post-types as code": commit these, edit them, `shio push` them back.
 *
 * With `--content` (SH87) it pulls *content* instead — the same verb over the other
 * resource, projected to `shio/content/**` (SH86). Two resources, one pair of verbs: an
 * agent that learned pull/push for the model does not have to learn a second vocabulary
 * for the pages.
 */
import { join } from "node:path";

import { ShioAdminClient } from "../admin-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { fileNameFor, serializeDef } from "../post-types-io.mjs";
import { defFileCollisions } from "../content-files.mjs";

export async function run(flags, positionals, ctx) {
  if (flags.content) {
    const content = await import("./content-pull.mjs");
    return content.run(flags, positionals, ctx);
  }
  return pullPostTypes(flags, ctx);
}

async function pullPostTypes(flags, ctx) {
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const dir = flags.out ?? flags.dir ?? "shio/post-types";

  const client = new ShioAdminClient({ baseUrl, user, password, fetch: ctx.fetch });
  const defs = (await client.getPostTypeDefs()) ?? [];

  const target = join(ctx.cwd, dir);
  await ctx.fs.mkdir(target, { recursive: true });

  // SH409 — here, because this is the loop that writes. `Article` and `article` are two types and
  // one `.json` on Windows and macOS; the disk keeps the last write, `diffDefs` then reports the
  // other as removed, and `push --prune` deletes the post type every post of it is validated
  // against. Neither is written — there is no merging two models into one file.
  const clashes = defFileCollisions(defs.map((def) => def.name));
  for (const group of new Set(clashes.values())) {
    ctx.stdout.write(
      `shio pull: post-type ${group.addresses.join(" and ")} project to one file `
        + `(${group.paths.join(", ")}) — neither was written. A checkout on Windows or macOS keeps `
        + "one of them, and `push --prune` would then delete the other's type. Rename one of them "
        + "on the server.\n",
    );
  }

  const written = defs.filter((def) => !clashes.has(def.name));
  for (const def of written) {
    await ctx.fs.writeFile(join(target, fileNameFor(def)), serializeDef(def), "utf8");
  }
  ctx.stdout.write(`shio pull: wrote ${written.length} post-type(s) to ${dir}\n`);
  return written;
}
