/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio build` (SH64) — compile the local post-type sources (`*.ts`/`*.mjs` DSL
 * and `*.json`) to canonical JSON artifacts under `shio/.out/` without touching
 * the server. The output is byte-identical to what `shio pull` writes, so it is
 * exactly what `push`'s diff compares against — useful for inspection and CI.
 */
import { join } from "node:path";

import { readLocalDefs } from "../post-types-compile.mjs";
import { fileNameFor, serializeDef } from "../post-types-io.mjs";

/**
 * What `shio build --help` prints (SH435).
 *
 * Here rather than in `index.mjs` so the flags and their meanings are one edit apart: the
 * global help described some commands' flags and no command's completely, and learning this
 * one's meant grepping the source for `flags.` — reading an implementation to discover an
 * interface, having already paid for a help text that answered something else.
 */
export const HELP = `shio build — compile local DSL/JSON post-types to canonical JSON

shio/post-types/ may hold *.json or *.ts/*.mjs DSL sources (@viglet/shio-model); this is
the compile step push runs before diffing, made available on its own.

  --dir <dir>          Sources (default shio/post-types)
  --out <dir>          Where the canonical JSON goes (default shio/.out)

Example:
  shio build --out shio/.out
`;

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
