/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { existsSync, readdirSync, readFileSync } from "node:fs"
import { dirname, join, relative } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { stripComments } from "./support/source-text.mjs"

/**
 * SH908 — console chrome mounted outside the console shell does not look wrong, it **throws**.
 *
 * ## The hazard, which has no type and no test until it is one
 *
 * `PageHeader` renders `SidebarTrigger`, which calls `useSidebar()`, which is
 * `throw new Error("useSidebar must be used within a SidebarProvider.")` when there is no provider
 * above it. Only `console.root.page.tsx` mounts one. So *any* component that renders the console
 * header from a route outside that shell — the bento tree, which ships no provider at all by
 * design (§LIV.3), or App.tsx's bare `*` — blanks the whole application behind the error boundary.
 *
 * Nothing catches it: `tsc` is happy, the census reads the page as console-era and correct, and
 * every source scan in this directory is about which era a page belongs to rather than where it is
 * allowed to be mounted. It surfaces as a white screen, once, on whichever route somebody added
 * last.
 *
 * ## Why it took SH908 to notice
 *
 * `not-found.page.tsx` is mounted in **three** places — inside the console shell, inside the bento
 * shell (this task), and at the bare `*` for a path under neither. It rendered no chrome at all
 * from SH180 until now, and the census recorded that as "console-era by absence" without a word
 * about the reason. So the obvious migration — give it an `PageHero` like the other
 * thirteen surfaces in Block AR — is a crash on the one route that matters most, and the
 * information that would have stopped it existed nowhere.
 *
 * It renders the header only under bento chrome now. This reader is what makes that a rule rather
 * than a comment somebody removes, and it asks the question of **every** component App.tsx mounts
 * outside the console shell, which is the SH338 shape: the instrument covers the class, not the
 * one instance that was found.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")
const APP = stripComments(readFileSync(join(SRC, "App.tsx"), "utf8"))

/*
 * SH910 — the hazard moved one provider along, and the rule is the same rule.
 *
 * This was written about `SidebarProvider`: only `console.root.page.tsx` mounted one, and
 * `PageHeader` → `SidebarTrigger` → `useSidebar()` throws without it. Both of those files are now
 * deleted, so that particular crash cannot happen again — and a reader whose probes name deleted
 * modules is a reader that reports a clean tree for ever.
 *
 * The **class** of defect outlived the instance, which is the SH338 shape this file was written
 * in. `bento.root.page.tsx` mounts `UserProvider`, and `useCurrentUser` throws with
 * "useCurrentUser must be used within UserProvider" where nothing above it does — the same white
 * screen behind the error boundary, on the same routes: App.tsx's bare `*`, `/login`, `/setup`,
 * the vocabulary harness and the visual editor, none of which is inside the shell.
 *
 * SH888 already paid for this once. The shell came up entirely blank because it mounted the
 * *package's* `UserProvider` rather than Shio's, and `tsc` was silent because both are called
 * `UserProvider` and both take children. Nothing catches it but a render or this.
 */
const NEEDS_SHELL_PROVIDER = [
  ["@/contexts/user.context", "useCurrentUser → UserProvider"],
]

/** The bento shell's own subtree, `<Route path={ROUTES.BENTO}>` to its closing tag. */
function bentoShellBlock() {
  const open = APP.indexOf("<Route path={ROUTES.BENTO}")
  if (open < 0) return null
  const close = APP.lastIndexOf("</Route>")
  if (close < 0 || close < open) return null
  return APP.slice(open, close)
}

/** Every `import X from "…"` in App.tsx, as `component → module specifier`. */
function defaultImports() {
  const out = new Map()
  for (const [, name, from] of APP.matchAll(/import\s+(\w+)\s+from\s+["']([^"']+)["']/g)) {
    out.set(name, from)
  }
  return out
}

/** `./app/console/x.page` → the file on disk, or null when it is a package rather than a page. */
function resolve(specifier) {
  if (!specifier.startsWith(".")) return null
  const base = join(SRC, specifier.replace(/^\.\//, ""))
  for (const candidate of [`${base}.tsx`, `${base}.ts`, join(base, "index.tsx"), join(base, "index.ts")]) {
    if (existsSync(candidate)) return candidate
  }
  return null
}

/**
 * Every component App.tsx mounts at a route that is **not** inside the console shell, with the
 * chrome provider that wraps it where there is one.
 */
function mountedOutsideTheShell() {
  const shell = bentoShellBlock()
  const outside = shell === null ? APP : APP.replace(shell, "")
  const imports = defaultImports()
  const found = new Map()
  // SH958 — the provider moved above <Routes>, so an element is provided when the root is, and a
  // per-route wrapper is the older spelling of the same fact rather than the only one.
  const root = /<SectionCardChromeProvider\s+chrome="bento"\s*>\s*<Routes>/.test(APP)

  for (const [element] of outside.matchAll(/element=\{[\s\S]*?\}\s*\/?>/g)) {
    const bento = root || /SectionCardChromeProvider\s+chrome="bento"/.test(element)
    for (const [, name] of element.matchAll(/<(\w+)\s*\/>/g)) {
      const file = resolve(imports.get(name) ?? "")
      if (!file) continue
      // A component mounted twice is bento-provided only if *every* mount provides it.
      const prior = found.get(name)
      found.set(name, { file, bento: prior ? prior.bento && bento : bento })
    }
  }
  return found
}

test("SH908: nothing mounted outside the shell needs a provider only the shell mounts", () => {
  const mounted = mountedOutsideTheShell()
  const offenders = []

  for (const [name, { file }] of mounted) {
    const source = stripComments(readFileSync(file, "utf8"))
    for (const [specifier, renders] of NEEDS_SHELL_PROVIDER) {
      if (new RegExp(`from\\s*["']${specifier}["']`).test(source)) {
        offenders.push(`${name} imports ${specifier} (${renders})`)
      }
    }
  }

  console.log(`SH908: ${mounted.size} components mounted outside the bento shell`)

  assert.deepEqual(offenders, [],
    "SH908: a component mounted outside the bento shell imports something that needs a provider"
      + " only that shell mounts. `useCurrentUser()` THROWS when no `UserProvider` is above it —"
      + " and only bento.root.page.tsx mounts one — so this is a white screen behind the error"
      + " boundary rather than a page that looks wrong.\n"
      + "  Take what you need as a prop, or render the surface inside the shell.")
})

/*
 * SH959 — what SH932 recorded, asserted the other way round.
 *
 * SH932 was written when `EmptyState` still read `useSectionChrome()` and rendered a plain block
 * wherever no provider was mounted — which was every page outside the shell: login, setup, the
 * catch-all 404. It required such a page to *decide* rather than take the console default by
 * accident, and it recorded that pages outside the shell took the console look.
 *
 * SH958 put one provider above the router and SH959 deleted every branch that read it, so the
 * question has no subject left. What holds now is stronger and simpler: **nothing in this console
 * reads the chrome**, so a page outside the shell renders exactly what one inside does, and every
 * mount outside the shell is under the root provider.
 */
const READS_CHROME = /useSectionChrome\s*\(/

/** Every `.ts`/`.tsx` under `src/` whose code — not its comments — reads the chrome. */
function chromeReaders() {
  const out = []
  const walk = (dir) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const full = join(dir, entry.name)
      if (entry.isDirectory()) walk(full)
      else if (/\.tsx?$/.test(entry.name)
        && READS_CHROME.test(stripComments(readFileSync(full, "utf8")))) {
        out.push(relative(SRC, full).replace(/\\/g, "/"))
      }
    }
  }
  walk(SRC)
  return out.sort()
}

test("SH959: nothing in the console branches on chrome, so outside the shell renders bento", () => {
  assert.deepEqual(chromeReaders(), [],
    "SH959: a component reads useSectionChrome() again. Every surface is under one bento provider"
      + " (SH958), so the answer is always the same, and a branch on it is a second rendering"
      + " nothing exercises — the console half this block deleted. Render the bento form.")

  const outside = mountedOutsideTheShell()
  const unprovided = [...outside].filter(([, { bento }]) => !bento).map(([name]) => name)
  assert.deepEqual(unprovided, [],
    "SH932: a component mounted outside the bento shell is not under the root chrome provider, so"
      + " anything it composes from the package takes the console default without deciding to —"
      + " the case SH932 made a page answer for, which SH958's root provider closes for all of them.")

  const notFound = stripComments(readFileSync(outside.get("NotFoundPage").file, "utf8"))
  assert.match(notFound, /<PageHero\b/,
    "SH959: the catch-all 404 no longer renders the bento hero. It is the page a stale link lands"
      + " on outside every shell, which is where the plain console block used to show.")
})

test("SH959: the chrome-reader walk is not vacuous", () => {
  // A walk that found no files would report no readers for the wrong reason; a pattern that could
  // not match the hook would do the same while reading every file.
  assert.ok(READS_CHROME.test('const chrome = useSectionChrome()'),
    "SH959: the pattern no longer matches a call to the hook, so the walk reports a clean tree for"
      + " ever.")
  assert.ok(!READS_CHROME.test(stripComments('/** reads useSectionChrome() */\nconst x = 1')),
    "SH959: a comment naming the hook counts as a read, so this console's own explanations would"
      + " fail the rule they describe.")
  const walked = []
  const count = (dir) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      if (entry.isDirectory()) count(join(dir, entry.name))
      else if (/\.tsx?$/.test(entry.name)) walked.push(entry.name)
    }
  }
  count(SRC)
  // 174 when this was written; a floor well under that still catches a walk that lost the tree.
  assert.ok(walked.length > 100, `SH959: the walk found ${walked.length} source files under src/`)
})

test("SH908: the reader is not vacuous — it finds the routes and the chrome it forbids", () => {
  /*
   * SH909 — this used to assert the console shell's block was findable, on the grounds that a
   * reader which cannot tell inside from outside is checking nothing. That premise is gone: there
   * is no console shell any more, so **everything** is outside one and the first test applies to
   * the whole application rather than to part of it. `consoleShellBlock()` returning null is now
   * the expected answer, and the strictness moved in the right direction.
   *
   * What has to stay true is the other half: the route walk must still find routes. A pattern that
   * matches nothing reports a clean tree for ever, which is the failure mode SH905's own vacuity
   * check was written for.
   */
  /*
   * SH910 — a floor became a named list, because the set got small enough to be worth reading.
   *
   * While "outside" meant "outside the *console* shell" this was `>= 8` and the bento tree made
   * that trivially true. Outside the **bento** shell there are six, and they are six on purpose:
   * two pre-auth pages, the vocabulary harness, the redirect, the editor that owns the viewport
   * (SH907), and the page nobody navigates to. A seventh is a route that left the shell, which is
   * the moment somebody should be asked whether it meant to.
   */
  const mounted = mountedOutsideTheShell()
  assert.deepEqual([...mounted.keys()].sort(), [
    "BentoVocabularyPage",
    "LegacyConsoleRedirect",
    "LoginPage",
    "NotFoundPage",
    "SetupPage",
    "VisualEditorPage",
  ], "SH908: the set of components mounted outside the bento shell changed.\n"
      + "  In `+ actual`: a surface now renders without the shell's providers, its rail and its"
      + " chrome — check it is one of the few things that should.\n"
      + "  In `- expected`: it moved inside, or the element pattern stopped matching and this"
      + " reader is checking almost nothing, which is the failure mode this test exists for.")

  // And the forbidden import must still exist, or the probe is looking for a name nothing uses.
  // It is fine for it to be used *inside* the shell — that is the only place it is allowed.
  const context = join(SRC, "contexts", "user.context.tsx")
  assert.ok(existsSync(context),
    "SH908: @/contexts/user.context is gone. Either it moved — repoint the probe in the same diff"
      + " — or the provider was retired and this reader has gone blind to the crash it exists for.")
  assert.match(readFileSync(context, "utf8"), /must be used within UserProvider/,
    "SH908: useCurrentUser no longer throws without a provider, so the hazard this file asserts"
      + " may be gone. Re-derive the rule from what it does now rather than deleting it on sight.")

  // The other half of the same fact: the shell has to be the thing that mounts it. If nothing
  // does, every route is 'outside the shell' and the rule above is about nobody.
  const shell = readFileSync(join(SRC, "app", "system", "bento.root.page.tsx"), "utf8")
  assert.match(shell, /<UserProvider>/,
    "SH908: the bento shell no longer mounts UserProvider, so the inside/outside distinction this"
      + " reader draws no longer corresponds to anything.")
})
