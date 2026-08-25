/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { existsSync, readFileSync } from "node:fs"
import { dirname, join } from "node:path"
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
 * about the reason. So the obvious migration — give it an `AdaptivePageHeader` like the other
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

  for (const [element] of outside.matchAll(/element=\{[\s\S]*?\}\s*\/?>/g)) {
    const bento = /SectionCardChromeProvider\s+chrome="bento"/.test(element)
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

/**
 * The transitive half, which the import check above cannot see.
 *
 * `AdaptivePageHeader`'s console branch *is* `PageHeader`, so a page that composes the adapter
 * carries the same crash — it just carries it one file away, where a grep for the import finds
 * nothing. Under a `SectionCardChromeProvider chrome="bento"` that branch is unreachable and the
 * adapter is exactly the right thing to use. Mounted with no provider — App.tsx's bare `*`, which
 * is a path under neither shell — console is the default and the branch is the one that runs.
 *
 * So: outside the shell and outside a bento provider, a page composing the adapter has to decide
 * for itself, which means reading `useSectionChrome()`.
 */
test("SH908: an unprovided page composing the adapter reads the chrome rather than assuming it", () => {
  const undecided = []

  for (const [name, { file, bento }] of mountedOutsideTheShell()) {
    if (bento) continue
    const source = stripComments(readFileSync(file, "utf8"))
    if (!/adaptive-page-header/.test(source)) continue
    if (/useSectionChrome\s*\(/.test(source)) continue
    undecided.push(name)
  }

  assert.deepEqual(undecided, [],
    "SH908: this component composes AdaptivePageHeader, is mounted outside the console shell, and"
      + " is not wrapped in SectionCardChromeProvider chrome=\"bento\" — so it gets the console"
      + " default, whose branch renders PageHeader → SidebarTrigger → useSidebar(), which throws"
      + " with no provider above it.\n"
      + "  Either wrap the route in the bento provider, or read `useSectionChrome()` and render the"
      + " header only when it answers \"bento\", as not-found.page.tsx does.")
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
  const shell = readFileSync(join(SRC, "app", "bento", "bento.root.page.tsx"), "utf8")
  assert.match(shell, /<UserProvider>/,
    "SH908: the bento shell no longer mounts UserProvider, so the inside/outside distinction this"
      + " reader draws no longer corresponds to anything.")
})
