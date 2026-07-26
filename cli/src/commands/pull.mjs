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
  for (const def of defs) {
    await ctx.fs.writeFile(join(target, fileNameFor(def)), serializeDef(def), "utf8");
  }
  ctx.stdout.write(`shio pull: wrote ${defs.length} post-type(s) to ${dir}\n`);
  return defs;
}
