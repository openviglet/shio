/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * `shio types` — generate a TypeScript `.d.ts` from the live post-types (SH11
 * codegen), so page code gets autocomplete on `post.attrs.*`. Uses a CDA token.
 */
import { dirname, join } from "node:path";

import { ShioClient, generateShioTypes } from "@viglet/shio-client";

import { resolveCda } from "../context.mjs";

export async function run(flags, _positionals, ctx) {
  const { baseUrl, apiToken } = resolveCda(flags, ctx.env);
  const out = flags.out ?? "lib/shio-types.d.ts";

  const client = new ShioClient({ baseUrl, apiToken, fetch: ctx.fetch });
  const schemas = await client.getPostTypes();
  const source = generateShioTypes(schemas, { importFrom: flags.importFrom });

  if (out === "-") {
    ctx.stdout.write(source);
    return schemas;
  }
  const target = join(ctx.cwd, out);
  await ctx.fs.mkdir(dirname(target), { recursive: true });
  await ctx.fs.writeFile(target, source, "utf8");
  ctx.stdout.write(`shio types: wrote ${schemas.length} post-type(s) to ${out}\n`);
  return schemas;
}
