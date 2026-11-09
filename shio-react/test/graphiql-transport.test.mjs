/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { readdirSync, readFileSync, statSync } from "node:fs"
import { createRequire } from "node:module"
import { dirname, join, relative } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { stripComments } from "./support/source-text.mjs"

/**
 * SH1388 — the explorer talks to `/graphql` through a toolkit API the toolkit still supports.
 *
 * SH1383 moved the console to `@graphiql/toolkit` 1.0, where `createGraphiQLFetcher` is deprecated
 * in favour of `createTransport`; the explorer kept the old call, which typechecks today and is the
 * likeliest thing the next major removes. This reads the deprecations from the installed toolkit's
 * own declarations, so a later release that deprecates something else fails here by name too.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const SRC = join(ROOT, "src")
const require = createRequire(join(ROOT, "package.json"))
const graphiqlRequire = createRequire(require.resolve("graphiql/package.json"))

function filesUnder(dir, keep) {
  const out = []
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) out.push(...filesUnder(full, keep))
    else if (keep(entry)) out.push(full)
  }
  return out
}

/** Names the installed toolkit's declarations mark `@deprecated`, read from the doc comment above each. */
function deprecatedExports() {
  const toolkit = dirname(graphiqlRequire.resolve("@graphiql/toolkit/package.json"))
  const names = new Set()
  for (const file of filesUnder(join(toolkit, "dist", "esm"), (name) => name.endsWith(".d.mts"))) {
    const text = readFileSync(file, "utf8")
    for (const [, name] of text.matchAll(/@deprecated[\s\S]*?\*\/\s*(?:export\s+)?declare\s+(?:function|const)\s+(\w+)/g)) {
      names.add(name)
    }
  }
  return names
}

test("SH1388: the toolkit's deprecations were read, so an empty read cannot agree with every import", () => {
  assert.ok(deprecatedExports().has("createGraphiQLFetcher"),
    "SH1388: the scan found no deprecated createGraphiQLFetcher in @graphiql/toolkit — the layout moved, or it was removed; re-read the toolkit")
})

test("SH1388: no console source imports a @graphiql/toolkit export the toolkit deprecates", () => {
  const deprecated = deprecatedExports()
  const offenders = []
  for (const file of filesUnder(SRC, (name) => /\.(ts|tsx)$/.test(name))) {
    const code = stripComments(readFileSync(file, "utf8"), file)
    for (const [, names] of code.matchAll(/import\s*\{([^}]*)\}\s*from\s*["']@graphiql\/toolkit["']/g)) {
      for (const name of names.split(",").map((each) => each.replace(/\btype\b/, "").trim().split(/\s+as\s+/)[0])) {
        if (deprecated.has(name)) offenders.push(`${relative(SRC, file).replace(/\\/g, "/")}: ${name}`)
      }
    }
  }
  assert.deepEqual(offenders, [], "SH1388: build on the Transport API (createTransport) instead")
})

test("SH1388: the explorer's transport stays token-only, POST-only and without incremental delivery", () => {
  const page = stripComments(readFileSync(join(SRC, "app", "admin", "graphql", "graphql.page.tsx"), "utf8"))
  const call = page.slice(page.indexOf("const transport = useMemo"))
  assert.ok(page.includes("const transport = useMemo") && call.includes("createTransport("),
    "SH1388: the explorer no longer builds its transport in one memo — re-read the page")
  assert.match(page, /<GraphiQL\s+transport=\{transport\}/, "SH1388: GraphiQL is not given the transport")
  // SH4: the session cookie would load a context with no ROLE_CDA and turn the Key header into a 403.
  assert.match(call, /credentials:\s*"omit"/, "SH1388: the transport sends the session cookie again")
  assert.match(call, /Key:\s*selectedToken\.token/, "SH1388: the transport no longer sends the CDA token")
  assert.match(call, /enableIncrementalDelivery:\s*false/, "SH1388: /graphql serves no @defer/@stream")
  assert.match(call, /supportedMethods:\s*\["POST"\]/,
    "SH1388: without a pinned method the transport probes /graphql with OPTIONS first")
})
