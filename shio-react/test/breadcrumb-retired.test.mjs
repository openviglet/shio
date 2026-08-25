/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { existsSync, readFileSync, readdirSync, statSync } from "node:fs"
import { dirname, join, relative } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"

import { stripComments } from "./support/source-text.mjs"

/**
 * SH933 — the trail is retired on both sides, not just the rendering one.
 *
 * <h2>What half a retirement looked like</h2>
 *
 * SH910 deleted the console page header, which was the only thing that ever drew a breadcrumb.
 * The **producer** side survived it: twenty-one pages went on calling `resetBreadcrumb` on every
 * mount, building label-and-href arrays and handing them to a provider with no consumer. Nothing
 * failed. The calls typechecked, the provider stored what it was given, and the next page copied
 * from any of them inherited the habit — which is the hazard SH910 was filed against, one layer
 * in: what a new page copies is what a grep for an existing pattern finds first.
 *
 * <h2>Why a lint and not just the deletion</h2>
 *
 * The deletion is a diff; the habit is a pattern. `useBreadcrumb` is still exported by the design
 * system, so an import of it compiles today and would restore exactly the state this closed —
 * silently, because a trail nothing renders costs nothing visible. SH322's rule: when the fix is
 * "and whatever a grep turns up", the grep is the deliverable.
 *
 * <h2>What is deliberately still allowed</h2>
 *
 * The content browser draws its own path in its sub-toolbar, and the folder-picker dialog keeps a
 * local `BreadcrumbItem` for the folder it is walking. Neither goes through the retired provider,
 * and both are the case where a trail earns its place — a reader moving up and down a tree. This
 * reader is about the console-wide trail, so it matches the provider and the hook by name rather
 * than the word "breadcrumb", which would forbid the two surfaces that legitimately use one.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")

function sources(dir, out = []) {
  for (const name of readdirSync(dir)) {
    const path = join(dir, name)
    if (statSync(path).isDirectory()) sources(path, out)
    else if (/\.(tsx?|jsx?)$/.test(path)) out.push(path)
  }
  return out
}

test("SH933: nothing feeds a breadcrumb the console does not render", () => {
  const offenders = []
  for (const file of sources(SRC)) {
    const body = stripComments(readFileSync(file, "utf8"))
    if (/\buseBreadcrumb\b|\bresetBreadcrumb\b|\bBreadcrumbProvider\b/.test(body)) {
      offenders.push(relative(SRC, file).replaceAll("\\", "/"))
    }
  }

  assert.deepEqual(offenders, [],
    "SH933: a page builds a breadcrumb trail again.\n"
      + "  Nothing renders one: the console page header went with the shell in SH910, and the bento\n"
      + "  era puts a page's identity in its own hero, which carries the way back.\n"
      + "  The call compiles, costs a render, and is what the page after this one will copy.\n"
      + "  If a trail is genuinely wanted, that is a design decision, and it brings the retired\n"
      + "  duplicate-key assertion back with it (see SH716) — the entry naming it is in\n"
      + "  cli/test/assertion-debt.test.mjs.")
})

test("SH933: the context module is gone, so the import cannot resolve by habit", () => {
  // The hook is still exported by the design system, so the local re-export was the path of least
  // resistance back into this. Deleting it makes the mistake fail at the import rather than at a
  // review nobody schedules.
  assert.equal(existsSync(join(SRC, "contexts", "breadcrumb.context.tsx")), false,
    "SH933: contexts/breadcrumb.context.tsx is back. It re-exported the design system's provider"
      + " and hook, and its whole effect now is to make `@/contexts/breadcrumb.context` resolve"
      + " for a page that should not be calling it.")
})

test("SH933: the reader walks real sources, and its pattern still matches one", () => {
  // A walk that found nothing would report a clean console for ever, which is the failure mode
  // every held list in this suite is written against.
  const files = sources(SRC)
  assert.ok(files.length > 150,
    `SH933: the walk yielded ${files.length} sources, so it is scanning something other than the`
      + " console and the assertion above is about nothing.")

  // And the pattern must still be able to fire. Asserted against synthetic text, because the
  // repository is now — correctly — empty of the thing it looks for.
  const planted = stripComments('const { resetBreadcrumb } = useBreadcrumb()\n')
  assert.match(planted, /\buseBreadcrumb\b|\bresetBreadcrumb\b|\bBreadcrumbProvider\b/,
    "SH933: the pattern no longer matches the call it forbids, so it would report a clean tree"
      + " whatever a page did.")
})
