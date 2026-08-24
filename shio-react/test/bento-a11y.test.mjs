/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { readdirSync, readFileSync } from "node:fs"
import { dirname, join, relative } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { findElements } from "./support/jsx-elements.mjs"
import { stripComments } from "./support/source-text.mjs"

/**
 * SH893 — the bento layer's accessibility rules, checked here rather than remembered.
 *
 * ## Why a consumer needs its own checks
 *
 * The package runs its stories under axe on every push, and that covers **the package's**
 * components. It says nothing about how a consumer composes them: an icon-only `Button` with no
 * label, an `aria-selected={active}` where a string was required, a keyframe added to the
 * consumer's own CSS outside the reduced-motion guard. Those rules held across 118 pages in one
 * product because that product's team read one file. Shio has its own lint suite and its own
 * conventions, and none of them knew these rules.
 *
 * ## What the first measurement actually found — and the bug in the measurement
 *
 * The first pass reported **3** unnamed icon-only buttons out of 50 and read as almost clean. It
 * was wrong by an order of magnitude: the real number is **34**. The reason is worth more than
 * the number. The scan matched an element with `/<Button\b([^>]*?)>/`, and an arrow function in
 * a prop — `onClick={() => openBrowserAt()}` — contains a `>`, so the opening tag ended inside
 * the prop, the "body" began mid-expression, and the check "does the body contain readable
 * text?" answered *yes* because `openBrowserAt` is letters. Two adjacent unnamed buttons in the
 * same file, one reported and one not, differing only in whether their handler was an arrow.
 *
 * That is why `test/support/jsx-elements.mjs` exists and why every rule here goes through it. A
 * gate that under-reports is worse than no gate: it is a green run that reads as coverage.
 *
 * ## Six fixed, twenty-eight declared
 *
 * The six in **shared components** are fixed, because the migration keeps them: the permissions
 * dialog and three of the fifteen form widgets, which SH892 exists precisely to avoid rewriting.
 * (`date.widget.tsx` had no i18n at all and gained the hook.) The remaining 28 are on console
 * pages Block AR rewrites, held below **by name**: each AR task deletes its own line as part of
 * the commit that migrates the page, and anything new fails immediately.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")

function sources(dir = SRC, found = []) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name)
    if (entry.isDirectory()) sources(path, found)
    else if (entry.name.endsWith(".tsx")) found.push(path)
  }
  return found
}

const rel = (path) => relative(SRC, path).replace(/\\/g, "/")

/**
 * Every icon-only `Button` with no accessible name, as `file → sorted glyph names`.
 *
 * Keyed on the **glyph** rather than a line number: a line number moves when anything above it
 * is edited, so a debt list keyed on one is a list that fails for reasons unrelated to
 * accessibility. `["Pencil", "Trash2"]` is stable and still says which control is unnamed.
 */
function unnamedIconButtons() {
  const map = {}
  for (const path of sources().sort()) {
    const source = readFileSync(path, "utf8")
    for (const element of findElements(source, "Button")) {
      if (!/size="icon"/.test(element.attrs)) continue
      const named = /aria-label=/.test(element.attrs) || /\btitle=/.test(element.attrs)
      const srOnly = /sr-only/.test(element.body)
      const text = /\{t\(/.test(element.body)
        || /[A-Za-z]{2,}/.test(element.body.replace(/<[^>]*>/g, "").trim())
      if (named || srOnly || text) continue
      const glyph = (/<([A-Z][A-Za-z0-9]*)/.exec(element.body) ?? [null, "?"])[1]
      ;(map[rel(path)] ??= []).push(glyph)
    }
  }
  for (const list of Object.values(map)) list.sort()
  return map
}

/**
 * The debt. **Console pages only** — a shared component may not appear here, because the
 * migration keeps those and a name missing from one is missing on every page that renders it.
 */
const UNNAMED_DEBT = {
  "app/console/admin/admin.groups.list.page.tsx": ["Pencil", "Trash2"],
  "app/console/admin/admin.roles.list.page.tsx": ["Pencil", "Trash2"],
  "app/console/admin/admin.users.list.page.tsx": ["Pencil", "Trash2"],
  "app/console/admin/api-token/api-token.list.page.tsx": ["Pencil", "Trash2"],
  "app/console/admin/auth-provider/auth-provider.list.page.tsx": ["Pencil", "Trash2"],
  "app/console/admin/auth-provider/auth-provider.page.tsx": ["X"],
  "app/console/admin/exchange-provider/exchange-provider.list.page.tsx": ["Pencil", "Trash2"],
  "app/console/admin/exchange-provider/exchange-provider.page.tsx": ["X"],
  "app/console/admin/site-webhook/site-webhook.list.page.tsx": ["Pencil", "Trash2"],
  "app/console/admin/tenant/tenant.list.page.tsx": ["Pencil", "Trash2"],
  "app/console/content/object.children.page.tsx": ["LayoutGrid", "LayoutGrid", "List", "Search", "Sheet"],
  "app/console/content/post-type.edit.page.tsx": ["Trash2"],
  "app/console/content/post-type.list.page.tsx": ["Pencil", "Trash2"],
  "app/console/content/static-file.list.page.tsx": ["Download", "Trash2"],
}

/**
 * ARIA attributes whose value is a **state**, which the authoring contract requires to be a
 * string literal: `aria-selected={active ? "true" : "false"}`, never `{active}`. A boolean
 * expression renders `aria-selected="true"` / omits it entirely depending on the framework's
 * mood, and the difference is invisible until somebody uses a screen reader.
 *
 * `aria-label` and friends are excluded: their value is *content*, so an expression is correct
 * and is what every one of this console's eleven labels is.
 */
const STATE_ARIA = [
  "hidden", "expanded", "selected", "checked", "pressed", "current",
  "disabled", "busy", "invalid", "modal", "multiselectable", "readonly", "required",
]

test("SH893: every icon-only control has a name, and the ones that do not are named here", () => {
  const found = unnamedIconButtons()
  const total = Object.values(found).reduce((n, list) => n + list.length, 0)

  console.log(`SH893: ${total} icon-only Button(s) with no accessible name, in `
    + `${Object.keys(found).length} file(s)`)

  assert.deepEqual(found, UNNAMED_DEBT,
    "SH893: the unnamed-icon-control census moved.\n"
      + "A file or glyph in `+ actual` is an icon-only button a screen reader announces as"
      + " nothing. Give it `aria-label={t(...)}`, or a `sr-only` span beside the glyph — a bare"
      + " `<svg>`'s own label is not reliably announced and getByLabelText will not match it"
      + " either.\n"
      + "A file in `- expected` was fixed or migrated: delete its line, in the same diff.\n"
      + "A **shared component** must never appear here at all — the migration keeps those, and a"
      + " name missing from one is missing on every page that renders it.")

  for (const file of Object.keys(found)) {
    assert.ok(file.startsWith("app/console/"),
      `SH893: ${file} is not a console page and has an unnamed icon control. Block AR rewrites`
        + " the console pages, which is why their debt is tolerated and declared; a shared"
        + " component or a bento surface is not rewritten and has to be fixed now.")
  }
})

test("SH893: a state ARIA attribute is a string literal, never a boolean expression", () => {
  const offenders = []
  for (const path of sources().sort()) {
    const source = stripComments(readFileSync(path, "utf8"))
    for (const state of STATE_ARIA) {
      // `aria-x={...}` where the expression is not a quoted string.
      const pattern = new RegExp(`aria-${state}=\\{(?!\\s*")([^}]*)\\}`, "g")
      for (const [, expression] of source.matchAll(pattern)) {
        offenders.push(`${rel(path)}  aria-${state}={${expression.trim().slice(0, 40)}}`)
      }
      // A bare attribute with no value at all — `aria-hidden` — which is also not a literal.
      const bare = new RegExp(`aria-${state}(?=[\\s/>])`, "g")
      for (const _ of source.matchAll(bare)) {
        offenders.push(`${rel(path)}  aria-${state} (no value)`)
      }
    }
  }

  assert.deepEqual(offenders, [],
    "SH893: an ARIA state attribute is not a string literal.\n"
      + "  Write `aria-selected={active ? \"true\" : \"false\"}`, not `{active}` and not a bare"
      + " `aria-hidden`.\n"
      + "  This console had zero of these when the rule arrived, which is the easiest state to"
      + " keep and the easiest to lose — the first one will be written by somebody who has never"
      + " read BENTO-AUTHORING §5.")
})

/**
 * Keyframes a stylesheet declares, and which of them the reduced-motion guard does not name.
 *
 * Split out from the test because the honest state of this console is **zero keyframes** — every
 * animation on a bento surface comes from `bento.css`, whose own guard the package asserts. A
 * rule whose entire body is `if (nothing) return` is a rule nobody has ever seen run, so the
 * logic is exercised against synthetic input below and pointed at the real file above.
 */
function unguardedKeyframes(css) {
  const declared = [...css.matchAll(/@keyframes\s+([A-Za-z0-9_-]+)/g)].map(([, name]) => name)
  if (declared.length === 0) return { declared, unguarded: [], guarded: false }
  const guard = /@media\s*\(\s*prefers-reduced-motion\s*:\s*reduce\s*\)\s*\{([\s\S]*)\}/.exec(css)
  if (!guard) return { declared, unguarded: declared, guarded: false }
  return {
    declared,
    guarded: true,
    unguarded: declared.filter((name) => !guard[1].includes(name)),
  }
}

test("SH893: an animation Shio adds is inside the reduced-motion guard", () => {
  const css = readFileSync(join(SRC, "index.css"), "utf8")
  const { declared, unguarded } = unguardedKeyframes(css)

  console.log(`SH893: ${declared.length} keyframe(s) declared in this console's own CSS`)

  assert.deepEqual(unguarded, [],
    `SH893: index.css declares keyframe(s) (${unguarded.join(", ")}) the reduced-motion guard`
      + " does not name. Every animation in the bento layer sits inside one and a test in the"
      + " package asserts it; a consumer adding its own outside the guard reintroduces exactly"
      + " what that test prevents — for a reader who has asked their operating system to stop"
      + " things moving.")
})

test("SH893: the reduced-motion rule works, which the console's own CSS cannot show", () => {
  // Zero keyframes here today, so the rule above passes without executing anything interesting.
  // These are the three states it has to tell apart.
  assert.deepEqual(unguardedKeyframes("body { color: red }").unguarded, [],
    "SH893: a stylesheet with no animation must not be reported as unguarded")

  assert.deepEqual(
    unguardedKeyframes("@keyframes shioSpin { from { opacity: 0 } }").unguarded,
    ["shioSpin"],
    "SH893: a keyframe with no guard at all must be reported — this is the case the rule exists"
      + " for, and it is the one the real file cannot demonstrate")

  assert.deepEqual(
    unguardedKeyframes(
      "@keyframes shioSpin { from { opacity: 0 } }\n"
      + "@media (prefers-reduced-motion: reduce) { .x { animation: shioSpin 0s !important } }",
    ).unguarded,
    [],
    "SH893: a keyframe the guard names must pass, or the rule refuses every animation and gets"
      + " deleted by the first person who needs one")
})

test("SH893: the reader is not vacuous — and specifically not in the way it was", () => {
  // The regression this file exists because of. A naive `[^>]*?` tag match ends the opening tag
  // at the `>` in `=>`, and the element's "body" then carries the handler's identifier — so a
  // check for readable text passes and the unnamed button is never reported.
  const arrowProp = `
    <Button
      variant="ghost"
      size="icon"
      onClick={() => openBrowserAt()}
    >
      <Search className="h-4 w-4" />
    </Button>`

  const [element] = findElements(arrowProp, "Button")
  assert.ok(element, "SH893: the scanner found no Button in a sample that plainly contains one")
  assert.match(element.attrs, /onClick=/,
    "SH893: the scanner ended the opening tag before the arrow-function prop, which is the exact"
      + " false negative it was written to remove — 34 unnamed buttons reported as 3.")
  assert.doesNotMatch(element.body, /openBrowserAt/,
    "SH893: the handler leaked into the element body. Any 'does this contain text' check then"
      + " passes on an icon-only control and the whole census under-reports.")
  assert.match(element.body, /<Search/, "SH893: the scanner lost the element's actual children")

  // And the walk has to be reading real files.
  assert.ok(sources().length > 100,
    `SH893: the walk yielded ${sources().length} files — the tree moved and every census above is`
      + " measuring an empty directory, which reads as a console with no violations at all")
})
