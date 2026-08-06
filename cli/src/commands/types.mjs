/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio types` — generate a TypeScript `.d.ts` from the live post-types (SH11
 * codegen), so page code gets autocomplete on `post.attrs.*`. Uses a CDA token.
 */
import { dirname, join } from "node:path";

import { ShioClient, generateShioTypes } from "@viglet/shio-client";

import { resolveCda } from "../context.mjs";

/**
 * What `shio types --help` prints (SH435).
 *
 * Here rather than in `index.mjs` so the flags and their meanings are one edit apart: the
 * global help described some commands' flags and no command's completely, and learning this
 * one's meant grepping the source for `flags.` — reading an implementation to discover an
 * interface, having already paid for a help text that answered something else.
 */
export const HELP = `shio types — generate a TypeScript .d.ts from the live post-types

Uses a CDA token (--token, or SHIO_CDA_TOKEN), not the console credentials.

  --out <file>          Where the declarations go
  --import-from <spec>  The module the generated types import their helpers from

Example:
  shio types --out lib/shio-types.d.ts
`;

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
