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
/**
 * The same file **unstripped**, and this is not an oversight.
 *
 * `stripComments` says so itself: "block comments inside string literals would be stripped too".
 * SH909's legacy patterns are `` `${consolePath}/*` `` — a route wildcard, which is character-for-character
 * a block-comment opener — so the stripped copy loses everything from there to the next `*&#47;`,
 * which is the JSDoc under it. Read stripped, this file reports **one** backtick where there are
 * four and no `/console` anywhere. Patterns come from the raw text; prose-adjacent scans keep the
 * stripped one.
 */
const ROUTES_RAW = readFileSync(join(SRC, "app", "routes.const.ts"), "utf8")
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
 * SH909 — the legacy console patterns, which App.tsx mounts through a `.map()` over a constant.
 *
 * `/console/*` is a context like any other and **must stay served**: it is what a bookmark hits,
 * and the page it loads is the redirect. The tenant-prefixed twin, `/:tenant/console/*`, is not a
 * context at all — its first segment is a parameter — and the server serves it through
 * `ShIndexContext`'s `/*&#47;console/**` mapping instead, which the last test here asserts rather
 * than assumes.
 */
function legacyPatterns() {
  const declared = /LEGACY_CONSOLE_PATTERNS\s*=\s*\[([^\]]*)\]/.exec(ROUTES_RAW)
  if (!declared) return []
  const consolePath = consoleContextPath()
  return [...declared[1].matchAll(/`([^`]*)`/g)]
    .map(([, raw]) => raw.replaceAll("${consolePath}", consolePath))
}

/** The console's context path, which is a build-time setting rather than a literal. */
function consoleContextPath() {
  const env = readFileSync(join(SRC, "..", ".env"), "utf8")
  const configured = /^VITE_CONSOLE_CONTEXT_PATH=(.+)$/m.exec(env)
  assert.ok(configured, "SH926: shio-react/.env does not set VITE_CONSOLE_CONTEXT_PATH. The "
    + "console's context path is a build-time setting the Java REACT_CONTEXTS array has to match, "
    + "so this reader must resolve it rather than skip it.")
  return configured[1].trim()
}

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

  // SH909 — CONSOLE is `/bento` now and a plain literal, so the loop above has it. Before that it
  // was `scoped(consolePath)`, off the same env var the legacy patterns still read; this branch
  // stays because the setting did not go away, only the constant that was built from it.
  if (/CONSOLE:\s*scoped\(consolePath\)/.test(ROUTES_CONST)) out.set("CONSOLE", consoleContextPath())
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

  // SH909 — the legacy console redirect is mounted through a `.map()` over a constant, so its two
  // paths are `path={pattern}` and no literal reader can resolve them from App.tsx alone. They are
  // top-level routes like any other and `/console/*` is the one a bookmark hits.
  if (/LEGACY_CONSOLE_PATTERNS/.test(APP)) out.push(...legacyPatterns())
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

/**
 * A route whose **first segment is a parameter** has no context to declare: it matches any prefix.
 * The one this project has is SH909's `/:tenant/console/*`, and the server serves it through
 * `ShIndexContext`'s own `/*&#47;console/**` mapping rather than through the array — asserted in the
 * vacuity test below, so this exclusion cannot quietly become a hole.
 */
const wildcardPrefixed = (path) => path.startsWith("/:")

test("SH926: every top-level route tree the console mounts is an SPA context the app serves", () => {
  const declared = declaredContexts()
  const trees = topLevelRoutes()
  const needed = [...new Set(trees
    .filter((path) => !NOT_A_CONTEXT.has(path) && !wildcardPrefixed(path))
    .map(context))].sort()

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

  // And the legacy console prefix must be among the routes this reader *found*. Its paths are the
  // ones no literal reader resolves — before SH909 `ROUTES.CONSOLE` was `scoped(consolePath)`, and
  // since SH909 they are a mapped constant — so they are what a reader drops first, and dropping
  // them looks exactly like a clean run over the URLs every existing bookmark uses.
  const found = new Set(topLevelRoutes().map(context))
  assert.ok(found.has("/console"),
    `SH926: the console prefix is not among the ${trees.length} routes parsed, so the reader is `
      + "checking the bento trees and silently skipping the paths every existing bookmark uses")
  assert.ok(!declared.has("/definitely-not-a-context"),
    "SH926: the array reader accepts a string that is not in it, so it would accept anything")

  /*
   * SH909 — the tenant-prefixed twin is excluded from the context check above, and this is what
   * makes that exclusion honest rather than a hole: the server has to serve it somewhere, and the
   * somewhere is ShIndexContext's own mapping.
   */
  const indexContext = readFileSync(join(
    HERE, "..", "..", "shio-app", "src", "main", "java", "com", "viglet", "shio",
    "ShIndexContext.java"), "utf8")
  assert.match(indexContext, /"\/\*\/console\/\*\*"/,
    "SH909: ShIndexContext no longer forwards the tenant-prefixed console paths to the SPA shell. "
      + "They are excluded from the context array on the grounds that this mapping serves them — "
      + "without it, every /<tenant>/console/... bookmark 404s and the redirect that was supposed "
      + "to catch it never loads.")
})
