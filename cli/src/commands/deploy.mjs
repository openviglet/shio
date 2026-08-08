/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio deploy` — a convenience composite: apply the content model (`push`) then
 * regenerate the TypeScript types (`types`). Not a separate transport; it just
 * chains the two so a CI step or "promote to env" is a single command.
 */
import * as push from "./push.mjs";
import * as types from "./types.mjs";
import { helpText } from "../help.mjs";

/**
 * What `shio deploy --help` prints (SH435).
 *
 * Here rather than in `index.mjs` so the flags and their meanings are one edit apart: the
 * global help described some commands' flags and no command's completely, and learning this
 * one's meant grepping the source for `flags.` — reading an implementation to discover an
 * interface, having already paid for a help text that answered something else.
 */
export const HELP = helpText("deploy");

export async function run(flags, positionals, ctx) {
  ctx.stdout.write("shio deploy: applying post-types...\n");
  await push.run({ ...flags, dryRun: false }, positionals, ctx);

  if (flags.skipTypes) {
    return;
  }
  ctx.stdout.write("shio deploy: regenerating types...\n");
  await types.run(flags, positionals, ctx);
}
