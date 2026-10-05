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
      // `icon-sm` and `icon-lg` too: SH1199 moved forty icon buttons onto the dense size, and a
      // scan that matched only `icon` would have stopped seeing every one of them.
      if (!/size="icon(?:-sm|-lg)?"/.test(element.attrs)) continue
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
/*
 * SH910 — eight rows left by deletion rather than by naming, which is the one way this debt is
 * allowed to shrink without anybody fixing an icon.
 *
 * All eight were the console **list** pages — the `Pencil`/`Trash2` row actions on groups, roles,
 * users, tokens, auth providers, exchange providers, webhooks and tenants. Each of them had a
 * bento replacement already carrying named controls, and SH910 deleted the console originals with
 * the route tree. The debt these rows recorded was never paid; the pages that owed it stopped
 * existing, and the surfaces a curator reaches now are the ones that were always named.
 */
const UNNAMED_DEBT = {
  "app/admin/auth-provider/auth-provider.page.tsx": ["X"],
  "app/admin/exchange-provider/exchange-provider.page.tsx": ["X"],
  // SH922 removed the Download entry: that button lived inside the anchor that already
  // named it, and the fix was to delete the button rather than to label it.
  "app/content/static-file.list.page.tsx": ["Trash2"],
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

  // SH961 — "a console page" was a directory, `app/console/`, and the pages in it now live in their
  // domain trees. What the rule protects is unchanged: a debt row may name a page, never a shared
  // component, because a name missing from a component is missing on every page that renders it.
  for (const file of Object.keys(found)) {
    assert.ok(/^app\/.+\.page\.tsx$/.test(file),
      `SH893: ${file} is not a page and has an unnamed icon control. A page's debt is tolerated and`
        + " declared; a shared component's is on every page that renders it, and has to be fixed now.")
  }
})

/**
 * Whether an expression is a string literal, or a choice between two of them.
 *
 * The first draft of this rule required the expression to *begin* with a quote, which refused the
 * very form its own failure message prescribes — `aria-selected={active ? "true" : "false"}` — and
 * nothing noticed, because the console had no state ARIA attribute at all to try it on. SH985's
 * conflict view was the first, and it hit the contradiction on its first run. A rule that refuses
 * its own instruction teaches the reader to work around the rule.
 *
 * A ternary is the shape this attribute takes in a React component, so it is accepted when **both**
 * branches are quoted. What stays refused is what the rule is about: a bare boolean, which React
 * either renders as the wrong thing or drops, so the control silently loses its state.
 */
function isStringLiteral(expression) {
  const text = expression.trim()
  if (/^"[^"]*"$/u.test(text) || /^'[^']*'$/u.test(text)) return true
  return /^[^?]+\?\s*("[^"]*"|'[^']*')\s*:\s*("[^"]*"|'[^']*')$/u.test(text)
}

test("SH893: a state ARIA attribute is a string literal, never a boolean expression", () => {
  const offenders = []
  for (const path of sources().sort()) {
    const source = stripComments(readFileSync(path, "utf8"))
    for (const state of STATE_ARIA) {
      // `aria-x={...}` where the expression does not resolve to a string literal.
      const pattern = new RegExp(`aria-${state}=\\{([^}]*)\\}`, "g")
      for (const [, expression] of source.matchAll(pattern)) {
        if (isStringLiteral(expression)) continue
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

/**
 * SH922 — nothing interactive inside anything else interactive.
 *
 * <a><button></a> is invalid HTML, and browsers resolve it however they like. What it costs a
 * reader is concrete: on the static-file list it was **two tab stops on every one of 169 rows**,
 * the second announcing nothing, because only the anchor carried a name.
 *
 * SH893's census could not see it and the reason is the interesting part. That reader asks
 * whether an icon-only control has an accessible name; here the anchor *had* one. A scan of
 * either element on its own finds nothing wrong — the defect is in the relationship.
 *
 * ## Interactivity is decided by the import, not by the name
 *
 * The first draft listed `Link` as interactive and reported a second finding in
 * `html-editor.widget.tsx` — which was **lucide-react's `Link` icon**, sitting inside a toolbar
 * button exactly as it should. `Link` is a router element in one file and a glyph in the next,
 * and so is anything else a product happens to name twice. So each conditional name is only
 * interactive when *this file* imports it from somewhere that makes it so.
 *
 * `asChild` is excluded: it means the wrapper delegates its element to the child, so there is one
 * element, not two. Missing that would report every correct shadcn link-button in the console.
 */
const ALWAYS_INTERACTIVE = ["a", "button"]

/** Interactive only where the file imports them from something that makes them so. */
const CONDITIONALLY_INTERACTIVE = {
  Button: /from "@\/components\/ui\/button"|from "@viglet\/viglet-design-system"/,
  GradientButton: /from "@viglet\/viglet-design-system"/,
  Link: /from "react-router-dom"/,
  NavLink: /from "react-router-dom"/,
}

function interactiveNames(source) {
  const names = [...ALWAYS_INTERACTIVE]
  for (const [name, from] of Object.entries(CONDITIONALLY_INTERACTIVE)) {
    if (new RegExp(`import\\s*\\{[^}]*\\b${name}\\b[^}]*\\}\\s*${from.source}`).test(source)) {
      names.push(name)
    }
  }
  return names
}

function nestedInteractive() {
  const map = {}
  for (const path of sources().sort()) {
    const source = readFileSync(path, "utf8")
    const names = interactiveNames(source)
    const inner = new RegExp(`<(${names.join("|")})[\\s/>]`)
    for (const tag of names) {
      for (const element of findElements(source, tag)) {
        if (element.selfClosing || !element.body) continue
        if (/asChild/.test(element.attrs)) continue
        const hit = inner.exec(element.body)
        if (!hit) continue
        ;(map[rel(path)] ??= []).push(`${hit[1]} in ${tag}`)
      }
    }
  }
  for (const list of Object.values(map)) list.sort()
  return map
}

test("SH922: no interactive element is nested inside another", () => {
  const found = nestedInteractive()
  const total = Object.values(found).reduce((n, list) => n + list.length, 0)

  console.log(`SH922: ${total} nested-interactive shape(s) in ${Object.keys(found).length} file(s)`)

  assert.deepEqual(found, {},
    "SH922: an interactive element is nested inside another.\n"
      + "  `<a><button>…</button></a>` is invalid HTML and gives a keyboard reader two tab stops"
      + " where the page offers one action — the inner one usually announcing nothing, because the"
      + " outer is the half that got the label.\n"
      + "  Pick which one is the control. If the destination is a URL, the anchor is the control:"
      + " give it the button's classes and delete the button. If it needs an onClick, it is a"
      + " button and there should be no anchor.\n"
      + "  If the wrapper is delegating its element with `asChild`, this reader already skips it —"
      + " so a finding here is a real pair of elements.")
})

test("SH922: interactivity is read from the import, not guessed from the name", () => {
  // The false positive that shaped this rule: lucide-react exports an icon called `Link`, and a
  // toolbar button containing one is correct. Reported as a finding, it would have taught the
  // next reader that the rule is noise.
  const iconLink = 'import { Link } from "lucide-react"\n'
  assert.ok(!interactiveNames(iconLink).includes("Link"),
    "SH922: `Link` imported from lucide-react is an icon and must not count as interactive, or"
      + " every rich-text toolbar in this console becomes a finding.")

  const routerLink = 'import { Link } from "react-router-dom"\n'
  assert.ok(interactiveNames(routerLink).includes("Link"),
    "SH922: `Link` imported from react-router-dom renders an anchor and must count — without it"
      + " the rule misses the nesting most likely to be written by accident.")

  // And the always-interactive pair is not import-dependent.
  for (const tag of ALWAYS_INTERACTIVE) {
    assert.ok(interactiveNames("").includes(tag),
      `SH922: the plain <${tag}> element must always count as interactive`)
  }
})

test("SH922: the rule catches the shape it was written for", () => {
  // Proved against a sample rather than only against a clean tree, because the population is now
  // empty — and a rule with nothing left to find is a rule whose next failure is its first.
  const sample = 'import { Button } from "@/components/ui/button"\n'
    + '<a href="/x" aria-label="Download"><Button size="icon"><Download /></Button></a>'
  const names = interactiveNames(sample)
  const inner = new RegExp(`<(${names.join("|")})[\\s/>]`)
  const [anchor] = findElements(sample, "a")

  assert.ok(anchor, "SH922: the scanner found no anchor in a sample that plainly contains one")
  assert.match(inner.exec(anchor.body)?.[1] ?? "", /Button/,
    "SH922: a Button inside an anchor is no longer detected, which is the exact shape this rule"
      + " was written for — 169 instances of it on one page.")

  // And the correct alternative must NOT be reported, or the fix has nowhere to go.
  const fixed = '<a href="/x" aria-label="Download" className="inline-flex"><Download /></a>'
  const [fixedAnchor] = findElements(fixed, "a")
  assert.equal(inner.exec(fixedAnchor.body), null,
    "SH922: an anchor that IS the control is reported as a nesting, so the rule refuses its own"
      + " remedy")
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

  // SH985 — the state-ARIA rule, driven against the form its own message prescribes and against
  // the form it exists to refuse. It accepted neither and refused nothing for as long as no page
  // had one of these attributes: a rule with no instance is a rule nobody has run.
  assert.ok(isStringLiteral('"true"'), "a literal is a literal")
  // SH1074 filed the same contradiction from SH955's field pin, after SH985 had already fixed it;
  // this is the assertion that would have caught it for either.
  assert.ok(isStringLiteral('active ? "true" : "false"'),
    "SH1074: SH893's own failure message prescribes this exact form, so the rule has to accept it")
  assert.ok(!isStringLiteral("active"),
    "a bare boolean is what the rule is about — React drops it and the control loses its state")
  assert.ok(!isStringLiteral('active ? "true" : other'),
    "a half-literal choice still renders a boolean on one of its two branches")
})
