/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio dev` — generate types once, start the site's dev server, and refresh the
 * types on an interval so content-model edits flow into `post.attrs.*`
 * autocomplete while you develop.
 */
import { spawn } from "node:child_process";

import * as types from "./types.mjs";

/** Pick the package manager from the invoking agent (pnpm/yarn/bun/npm). */
export function detectPackageManager(env = process.env) {
  const ua = env.npm_config_user_agent || "";
  if (ua.startsWith("pnpm")) return "pnpm";
  if (ua.startsWith("yarn")) return "yarn";
  if (ua.startsWith("bun")) return "bun";
  return "npm";
}

/**
 * What `shio dev --help` prints (SH435).
 *
 * Here rather than in `index.mjs` so the flags and their meanings are one edit apart: the
 * global help described some commands' flags and no command's completely, and learning this
 * one's meant grepping the source for `flags.` — reading an implementation to discover an
 * interface, having already paid for a help text that answered something else.
 */
export const HELP = `shio dev — generate types, start the site's dev server, and watch the model

  --content            Sync shio/content/** both ways and log events, instead of running
                       the site's dev server
  --interval <ms>      How often to poll for changes

Example:
  shio dev --content
`;

export async function run(flags, positionals, ctx) {
  if (flags.content) {
    // SH90: the content loop instead of the site's dev server. It is a different job —
    // no app to serve, a file tree and an instance to keep in step — and spawning
    // `run dev` in a repo that only holds content would fail on a script that is not there.
    const watch = await import("./content-watch.mjs");
    return watch.run(flags, positionals, ctx);
  }
  const intervalMs = Math.max(1, Number(flags.interval ?? 5)) * 1000;

  // Initial generation (don't abort the dev server if the model isn't reachable yet).
  await types.run(flags, positionals, ctx).catch((err) =>
    ctx.stderr.write(`shio dev: initial type generation failed: ${err.message}\n`)
  );

  const pm = detectPackageManager(ctx.env);
  ctx.stdout.write(`shio dev: starting "${pm} run dev" + type watch (every ${intervalMs / 1000}s)\n`);

  const spawnImpl = ctx.spawn ?? spawn;
  const child = spawnImpl(pm, ["run", "dev"], {
    stdio: "inherit",
    cwd: ctx.cwd,
    shell: process.platform === "win32",
  });

  const timer = setInterval(() => {
    types.run(flags, positionals, ctx).catch((err) =>
      ctx.stderr.write(`shio dev: type refresh failed: ${err.message}\n`)
    );
  }, intervalMs);

  child.on("exit", (code) => {
    clearInterval(timer);
    if (typeof process.exitCode === "undefined") process.exitCode = code ?? 0;
  });
  return child;
}
