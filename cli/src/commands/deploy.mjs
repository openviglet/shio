/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * `shio deploy` — a convenience composite: apply the content model (`push`) then
 * regenerate the TypeScript types (`types`). Not a separate transport; it just
 * chains the two so a CI step or "promote to env" is a single command.
 */
import * as push from "./push.mjs";
import * as types from "./types.mjs";

export async function run(flags, positionals, ctx) {
  ctx.stdout.write("shio deploy: applying post-types...\n");
  await push.run({ ...flags, dryRun: false }, positionals, ctx);

  if (flags.skipTypes) {
    return;
  }
  ctx.stdout.write("shio deploy: regenerating types...\n");
  await types.run(flags, positionals, ctx);
}
