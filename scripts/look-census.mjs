#!/usr/bin/env node
/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1191 — the console's look, measured from this repository.
 *
 * The instrument is the design system's (VDS180): `look:census` loads a bento consumer's running
 * dev server, walks its routes in three views, attributes every figure to package or product code,
 * and fails when a figure grows past the allowance it keeps for that consumer. It is not shipped in
 * the npm package, and it should not be copied here — a second census is a second allowance, and
 * two allowances disagree (P10: compose, don't fork). What was missing was a way to run it against
 * *this* console without knowing where the other checkout lives or which flags it wants.
 *
 *   pnpm look                  # measure http://localhost:5173/bento against the allowance
 *   pnpm look -- --json        # the reading, route by route
 *   pnpm look -- --write       # record a lowered reading (in the design system's checkout)
 *
 * The checkout is found the way the Turing and Shio worktrees are laid out on a developer's
 * machine: `<viglet>/viglet-design-system/<line>`, where `<line>` is this worktree's own version
 * line. `VIGLET_DS_CHECKOUT` overrides it. The console URL is `SHIO_CONSOLE_URL`'s origin plus
 * `/bento`, and the credentials are `SHIO_USER` / `SHIO_PASSWORD`, the same pair every e2e check
 * here reads.
 */
import { spawnSync } from "node:child_process"
import { existsSync, realpathSync } from "node:fs"
import { basename, dirname, join, resolve } from "node:path"
import { fileURLToPath } from "node:url"

/** The consumer id this repository has in the design system's `consumers.json`. */
export const CONSUMER = "shio"

/** Where the bento shell is mounted; the census walks same-origin links under it. */
export const SHELL_ROUTE = "/bento"

/** The design-system checkout for this worktree's version line, or null when there is none. */
export function designSystemCheckout(repoRoot, env = process.env) {
  if (env.VIGLET_DS_CHECKOUT) return resolve(env.VIGLET_DS_CHECKOUT)
  // `latest` is a junction; the line is the directory it resolves to (2026.3, 2026.4, …).
  const real = realpathSync(repoRoot)
  const line = basename(real)
  const viglet = dirname(dirname(real))
  const candidate = join(viglet, "viglet-design-system", line)
  return existsSync(join(candidate, "scripts", "look-census.mjs")) ? candidate : null
}

/** The census arguments for this console, with the caller's own flags passed through. */
export function censusArgs(env = process.env, extra = []) {
  const origin = (env.SHIO_CONSOLE_URL || "http://localhost:5173").replace(/\/+$/, "")
  const user = env.SHIO_USER || "admin"
  const password = env.SHIO_PASSWORD || "admin"
  return ["--url", `${CONSUMER}=${origin}${SHELL_ROUTE}`, "--auth", `${CONSUMER}=${user}:${password}`, ...extra]
}

function main() {
  const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..")
  const checkout = designSystemCheckout(repoRoot)
  if (!checkout) {
    console.error(
      "look-census: no viglet-design-system checkout for this version line. Clone it beside this"
        + " repository (<viglet>/viglet-design-system/<line>) or set VIGLET_DS_CHECKOUT.",
    )
    process.exit(2)
  }
  const passthrough = process.argv.slice(2).filter((arg) => arg !== "--")
  const result = spawnSync(process.execPath, [join(checkout, "scripts", "look-census.mjs"), ...censusArgs(process.env, passthrough)], {
    cwd: checkout,
    stdio: "inherit",
  })
  process.exit(result.status ?? 1)
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main()
