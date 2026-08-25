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

/** Imports that only work under a `SidebarProvider`, and the component that renders one. */
const NEEDS_SIDEBAR = [
  ["@/components/page-header", "PageHeader"],
  ["@/components/ui/sidebar", "SidebarTrigger/SidebarInset"],
]

/** The console shell's own subtree, `<Route path={ROUTES.CONSOLE}>` to its closing tag. */
function consoleShellBlock() {
  const open = APP.indexOf("<Route path={ROUTES.CONSOLE}")
  if (open < 0) return null
  const close = APP.indexOf("</Route>", open)
  if (close < 0) return null
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
  const shell = consoleShellBlock()
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

test("SH908: nothing mounted outside the console shell renders console chrome", () => {
  const mounted = mountedOutsideTheShell()
  const offenders = []

  for (const [name, { file }] of mounted) {
    const source = stripComments(readFileSync(file, "utf8"))
    for (const [specifier, renders] of NEEDS_SIDEBAR) {
      if (new RegExp(`from\\s*["']${specifier}["']`).test(source)) {
        offenders.push(`${name} imports ${specifier} (${renders})`)
      }
    }
  }

  console.log(`SH908: ${mounted.size} components mounted outside the console shell`)

  assert.deepEqual(offenders, [],
    "SH908: a component mounted outside the console shell imports chrome that needs a"
      + " SidebarProvider. `PageHeader` renders `SidebarTrigger` → `useSidebar()`, which THROWS"
      + " when no provider is above it — and only console.root.page.tsx mounts one, so this is a"
      + " white screen behind the error boundary rather than a page that looks wrong.\n"
      + "  Use `AdaptivePageHeader` and let the chrome decide, or render no header at all.")
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

test("SH908: the reader is not vacuous — it finds the shell, the routes and the chrome it forbids", () => {
  // If the console shell block stops being found, every console route reads as "outside" and the
  // first test fails loudly. The opposite is the dangerous one: a route pattern that matches
  // nothing reports a clean tree for ever, which is the failure mode SH905's own vacuity check
  // was written for.
  assert.ok(consoleShellBlock(), "SH908: the console shell's Route block is no longer recognisable"
    + " in App.tsx, so this reader cannot tell inside from outside")

  const mounted = mountedOutsideTheShell()
  assert.ok(mounted.size >= 8,
    `SH908: only ${mounted.size} components resolve to a file — the bento tree mounts far more`
      + " than that, so the element pattern has stopped matching and this reader is checking"
      + " almost nothing")

  // And the forbidden import must still exist somewhere, or the probe is looking for a name the
  // console no longer uses. It is fine for that to be *inside* the shell — that is the only place
  // it is allowed.
  const header = join(SRC, "components", "page-header.tsx")
  assert.ok(existsSync(header),
    "SH908: @/components/page-header is gone. Either SH910 deleted it — then delete this probe in"
      + " the same diff — or it moved and this reader has gone blind to the crash it exists for.")
  assert.match(readFileSync(header, "utf8"), /SidebarTrigger/,
    "SH908: PageHeader no longer renders SidebarTrigger, so the hazard this file asserts may be"
      + " gone. Re-derive the rule from what it renders now rather than deleting it on sight.")
})
