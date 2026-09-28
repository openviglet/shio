/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1201 — under the Vite dev server every media thumbnail was broken: the console asked for
 * `/file_source/<id>/hero.png?w=300…`, the dev server proxied `/api` and a handful of page routes
 * but not that one, and answered the SPA shell with a 200. No request failed, so no console error
 * and no failed-response listener saw it, and every console e2e check runs against this server.
 */
// @scans code
import { strict as assert } from "node:assert"
import { readdirSync, readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { stripComments } from "./support/source-text.mjs"

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")

/** The backend's own top-level prefixes: what it serves outside the console's routes. */
const BACKEND = ["api", "file_source", "preview", "sites", "store", "graphql", "graphiql", "logout", "csrf", "oauth2"]

test("SH1201: every backend prefix the console builds a URL on is proxied by the dev server", () => {
  const proxy = stripComments(readFileSync(join(ROOT, "vite.config.ts"), "utf8"))
  const used = new Set()
  const walk = (dir) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const path = join(dir, entry.name)
      if (entry.isDirectory()) walk(path)
      else if (/\.(ts|tsx)$/.test(entry.name)) {
        const code = stripComments(readFileSync(path, "utf8"))
        for (const [, segment] of code.matchAll(/["'`]\/([a-z_]+)\//g)) {
          if (BACKEND.includes(segment)) used.add(segment)
        }
      }
    }
  }
  walk(join(ROOT, "src"))
  assert.ok(used.has("file_source"), "SH1201: the reader found no /file_source URL, so it reads nothing")
  const missing = [...used].filter((segment) => !proxy.includes(`"/${segment}": "http://localhost:2710"`))
  assert.deepEqual(missing, [],
    "SH1201: the console builds URLs on these backend prefixes and the dev server does not proxy them,"
      + " so it answers each with the SPA shell and a 200")
})
