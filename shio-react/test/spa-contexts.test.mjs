/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { endOfOpeningTag } from "./support/jsx-elements.mjs"
import { stripComments } from "./support/source-text.mjs"

/**
 * SH926 — a route tree this app mounts and the application does not serve is a set of pages that
 * exist only on a dev server.
 *
 * ## What happened
 *
 * `ShStaticResourceConfiguration.REACT_CONTEXTS` was `{"/login", "/setup", "/console"}`. Block AR's
 * bento tree is a **sibling** of `/console` (SH888), so a packaged instance matched none of it:
 * `/console` 200, `/bento` **404**, measured on a running jar in the same second. Fourteen migrated
 * surfaces were unreachable by URL.
 *
 * Every live probe in that block ran against the Vite dev server, which serves `index.html` for any
 * path that is not a file — so nothing anybody ran could have noticed. "The surface renders" and
 * "the surface is reachable" are different claims, and only the first was ever checked.
 *
 * ## Why this reader is on this side
 *
 * `ShSpaContextIsServedTest` asks the question from the server: every declared context really is
 * served, proved by a request rather than by reading the array back. This asks it from the client,
 * which is the side that *changes* — a new route tree is added here, in `App.tsx`, by somebody who
 * has no reason to be thinking about a Java configuration file two modules away. That is exactly
 * how SH888 added one, and the pairing is deliberate: a reader on the server side alone would have
 * gone on passing, because the server had nothing missing that it knew about.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")
const APP = stripComments(readFileSync(join(SRC, "App.tsx"), "utf8"))
const ROUTES_CONST = stripComments(readFileSync(join(SRC, "app", "routes.const.ts"), "utf8"))
const JAVA_CONFIG = join(
  HERE, "..", "..", "shio-app", "src", "main", "java", "com", "viglet", "shio", "spring",
  "ShStaticResourceConfiguration.java")

/**
 * Paths that are the SPA's own concern and never reach the server as a navigation.
 *
 * `/` is a controller redirect (`ShIndexContext`), and `*` is React Router's catch-all — a path
 * that matches it has, by definition, no context to declare.
 */
const NOT_A_CONTEXT = new Set(["/", "*"])

/**
 * `ROUTES.FOO` → its literal, read from the one file that spells them.
 *
 * `CONSOLE` is not a literal: it is `scoped(consolePath)`, and `consolePath` is
 * `import.meta.env.VITE_CONSOLE_CONTEXT_PATH` — the console's context path is a **build-time
 * setting**, which is a real coupling to the Java array rather than a quirk of this reader. So it
 * is resolved from `.env` and, when that fails, this **throws** rather than returning a Map
 * without it. The first draft returned quietly and the run printed four contexts with `/console`
 * silently absent: a reader that drops the entry it cannot parse reports a clean tree for the one
 * route it failed to look at.
 */
function routeConstants() {
  const out = new Map()
  for (const [, name, value] of ROUTES_CONST.matchAll(/(\w+)\s*:\s*["'](\/[^"']*)["']/g)) {
    out.set(name, value)
  }

  if (/CONSOLE:\s*scoped\(consolePath\)/.test(ROUTES_CONST)) {
    const env = readFileSync(join(SRC, "..", ".env"), "utf8")
    const configured = /^VITE_CONSOLE_CONTEXT_PATH=(.+)$/m.exec(env)
    assert.ok(configured, "SH926: ROUTES.CONSOLE is built from VITE_CONSOLE_CONTEXT_PATH and "
      + "shio-react/.env does not set it. The console's context path is a build-time setting the "
      + "Java REACT_CONTEXTS array has to match, so this reader must resolve it rather than skip it.")
    out.set("CONSOLE", configured[1].trim())
  }
  return out
}

/**
 * Every top-level `<Route path=…>` in `App.tsx` — the trees, not their children.
 *
 * Depth is counted over opening and closing tags in source order, with each opening tag's end
 * found by the brace-aware scanner in `support/jsx-elements.mjs`. Both simpler versions were
 * tried and both were wrong:
 *
 * - A lazy `<Route[\s\S]*?(\/>|>)` reads `<Route path={ROUTES.BENTO} element={<BentoRootPage />}>`
 *   as **self-closing** — the match ends at the `/>` belonging to the element prop — so every
 *   child of the bento tree came back as a top-level route and the reader demanded an SPA context
 *   for `/content`.
 * - `findElements(APP, "Route")` returns nothing for those trees at all: its `bodyOf` counts every
 *   `<Route` before the next `</Route>` as a nested open, self-closing children included, so a
 *   parent with thirty of them never balances and is dropped. That direction is the dangerous one
 *   — the parent vanishes and its children read as top-level.
 */
function topLevelRoutes() {
  const constants = routeConstants()
  const events = []

  for (const match of APP.matchAll(/<Route(?=[\s/>])/g)) {
    const from = match.index + "<Route".length
    const tagEnd = endOfOpeningTag(APP, from)
    if (tagEnd === -1) continue
    events.push({
      at: match.index,
      open: true,
      selfClosing: tagEnd.selfClosing,
      attrs: APP.slice(from, tagEnd.index),
    })
  }
  for (const match of APP.matchAll(/<\/Route>/g)) events.push({ at: match.index, open: false })
  events.sort((a, b) => a.at - b.at)

  const out = []
  let depth = 0
  for (const event of events) {
    if (!event.open) {
      depth -= 1
      continue
    }
    if (depth === 0) {
      const literal = event.attrs.match(/path="([^"]*)"/)
      const constant = event.attrs.match(/path=\{ROUTES\.(\w+)\}/)
      const path = literal ? literal[1] : constants.get(constant?.[1] ?? "")
      if (path) out.push(path)
    }
    if (!event.selfClosing) depth += 1
  }
  return out
}

/** The first segment of a path — the context a server has to know about. */
const context = (path) => `/${path.replace(/^\//, "").split("/")[0]}`

/** Every string in the Java array, read from the source it is declared in. */
function declaredContexts() {
  const source = readFileSync(JAVA_CONFIG, "utf8")
  const array = /REACT_CONTEXTS\s*=\s*\{([^}]*)\}/.exec(source)
  assert.ok(array, "SH926: REACT_CONTEXTS is no longer an array literal in "
    + "ShStaticResourceConfiguration.java. It moved or changed shape — re-point this reader at it "
    + "rather than deleting it, or the next route tree ships unreachable the way the bento one did.")
  return new Set([...array[1].matchAll(/"([^"]*)"/g)].map(([, value]) => value))
}

test("SH926: every top-level route tree the console mounts is an SPA context the app serves", () => {
  const declared = declaredContexts()
  const trees = topLevelRoutes()
  const needed = [...new Set(trees.filter((path) => !NOT_A_CONTEXT.has(path)).map(context))].sort()

  console.log(`SH926: ${trees.length} top-level routes → ${needed.length} context(s): ${needed.join(" ")}`)

  assert.deepEqual(
    needed.filter((ctx) => !declared.has(ctx)),
    [],
    "SH926: this route tree is mounted by App.tsx and served by nothing.\n"
      + "  On the Vite dev server it works — index.html is served for any path — and on a packaged\n"
      + "  instance every URL under it answers 404, so the pages exist only where nobody runs them.\n"
      + "  Add it to REACT_CONTEXTS in ShStaticResourceConfiguration.java, and to the permitAll\n"
      + "  block in ShSecurityConfig beside /console/**.")
})

test("SH926: the reader is not vacuous — it parses real routes and a real array", () => {
  const trees = topLevelRoutes()
  const declared = declaredContexts()

  // A regex that stops matching would report an empty tree list and pass against any array at all,
  // which is the failure mode every reader in this directory is written against.
  assert.ok(trees.length >= 5,
    `SH926: only ${trees.length} top-level routes parsed out of App.tsx — the route pattern has `
      + "stopped matching, and this reader now compares nothing against the server's list")
  assert.ok(declared.size >= 3,
    `SH926: REACT_CONTEXTS parsed as ${declared.size} entr(ies) — the array reader is reading `
      + "something that is not the array")

  // And the two sides must genuinely be able to disagree: /console is the control, and a path
  // nobody declares must not be found in the array.
  assert.ok(declared.has("/console"),
    "SH926: /console is not in REACT_CONTEXTS, which cannot be true of a working instance — the "
      + "array reader is matching the wrong thing")

  // And the console tree must be among the routes this reader *found*. It is the one whose path
  // is not a literal — `scoped(consolePath)`, off a build-time env var — so it is the entry a
  // constant reader drops first, and dropping it looks exactly like a clean run.
  const needed = new Set(topLevelRoutes().map(context))
  assert.ok(needed.has("/console"),
    `SH926: the console tree is not among the ${trees.length} routes parsed — ROUTES.CONSOLE `
      + "resolved to nothing, so the reader is checking the bento trees and silently skipping the "
      + "one every curator uses")
  assert.ok(!declared.has("/definitely-not-a-context"),
    "SH926: the array reader accepts a string that is not in it, so it would accept anything")
})
