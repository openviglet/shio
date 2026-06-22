/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * `shio build` (SH64) — compile the local post-type sources (`*.ts`/`*.mjs` DSL
 * and `*.json`) to canonical JSON artifacts under `shio/.out/` without touching
 * the server. The output is byte-identical to what `shio pull` writes, so it is
 * exactly what `push`'s diff compares against — useful for inspection and CI.
 */
import { join } from "node:path";

import { readLocalDefs } from "../post-types-compile.mjs";
import { fileNameFor, serializeDef } from "../post-types-io.mjs";

export async function run(flags, _positionals, ctx) {
  const dir = flags.dir ?? "shio/post-types";
  const out = flags.out ?? "shio/.out";

  const defs = await readLocalDefs(ctx, dir);

  const target = join(ctx.cwd, out);
  await ctx.fs.mkdir(target, { recursive: true });
  for (const def of defs) {
    await ctx.fs.writeFile(join(target, fileNameFor(def)), serializeDef(def), "utf8");
  }

  ctx.stdout.write(`shio build: compiled ${defs.length} post-type(s) from ${dir} to ${out}\n`);
  return defs;
}
