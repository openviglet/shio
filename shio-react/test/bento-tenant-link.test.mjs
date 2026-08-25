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

import { stripComments } from "./support/source-text.mjs"

/**
 * SH931 — a bento URL says which tenant it means, and saying is all it does.
 *
 * <h2>The half that is arithmetic and the half that is a design law</h2>
 *
 * `tenantFromSearch` and `linkWithTenant` are pure and imported here directly, so the parsing and
 * the idempotence are measured rather than grepped. The rule that matters most cannot be reached
 * that way: **opening a link must never change the session's tenant.** That is P7 — never surprise
 * the curator — and its failure is not a wrong page but a lost edit, because a curator carried out
 * of the tenant they were working in leaves a half-written draft behind in it.
 *
 * The shape that breaks it is one line: move `service.switchTo(claimed)` into the effect that
 * already reads `claimed`, and the parameter becomes an instruction. Nothing else would notice —
 * it type-checks, it renders, and on a single-tenant instance every existing test still passes.
 * So this reads the effect bodies and holds them empty of it. A comment saying "do not switch
 * here" is the version of this that survives exactly until someone is in a hurry.
 *
 * <h2>Why the gates are asserted too</h2>
 *
 * The notice and the copy action are inert on a single-tenant install, which is the whole answer
 * to "is this worth building": the population that pays for the feature is the population that has
 * the problem. That property lives in two `useMultiTenant()` calls, and it is one careless edit
 * from becoming a banner on every install that never had two tenants to confuse.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")
const BENTO = join(SRC, "app", "bento")

const { TENANT_PARAM, linkWithTenant, tenantFromSearch } =
  await import("../src/app/bento/bento-tenant-link.ts")

const NOTICE = stripComments(readFileSync(join(BENTO, "bento-tenant-link-notice.tsx"), "utf8"))
const SWITCHER = stripComments(readFileSync(join(BENTO, "bento-tenant-switcher.tsx"), "utf8"))
const SHELL = stripComments(readFileSync(join(BENTO, "bento.root.page.tsx"), "utf8"))

/**
 * Every `useEffect(...)` argument list in `source`, sliced by balancing parentheses.
 *
 * Not a parser, and it does not have to be: the input is comment-stripped TSX from one small file,
 * and a parenthesis inside a string literal there would make this over-report — a false failure
 * naming the effect, which is the direction a lint should fail in.
 */
function effectBodies(source) {
  const bodies = []
  const marker = "useEffect("
  for (let at = source.indexOf(marker); at !== -1; at = source.indexOf(marker, at + 1)) {
    let depth = 0
    for (let i = at + marker.length - 1; i < source.length; i += 1) {
      if (source[i] === "(") depth += 1
      else if (source[i] === ")") {
        depth -= 1
        if (depth === 0) {
          bodies.push(source.slice(at, i + 1))
          break
        }
      }
    }
  }
  return bodies
}

test("SH931: a URL states its tenant, and a blank claim is no claim", () => {
  assert.equal(TENANT_PARAM, "tenant",
    "SH931: the parameter name changed. Links already pasted into chat logs and tickets carry the"
      + " old one, and nothing anywhere will tell their readers why the banner stopped appearing.")

  assert.equal(tenantFromSearch("?tenant=acme"), "acme")
  assert.equal(tenantFromSearch("https://cms.example.com/bento/content?id=7&tenant=acme"), "acme")
  assert.equal(tenantFromSearch(new URLSearchParams({ tenant: "acme" })), "acme")
  assert.equal(tenantFromSearch("?tenant= acme "), "acme")

  assert.equal(tenantFromSearch("?id=7"), null, "SH931: an ordinary URL must make no claim")
  assert.equal(tenantFromSearch("?tenant="), null,
    "SH931: an empty value is a link builder's slip, not a claim about a tenant with no name."
      + " Reading it as a claim puts a curator in front of a banner comparing their tenant to \"\".")
  assert.equal(tenantFromSearch("?tenant=%20%20"), null)
  assert.equal(tenantFromSearch(""), null)
  assert.equal(tenantFromSearch(undefined), null)
})

test("SH931: stamping a tenant is idempotent and keeps the rest of the URL", () => {
  const base = "https://cms.example.com"

  assert.equal(linkWithTenant("/bento/content?id=7", "acme", base),
    "https://cms.example.com/bento/content?id=7&tenant=acme",
    "SH931: existing parameters must survive — the id is the whole reason the link is being shared")

  const once = linkWithTenant("/bento/content?id=7", "acme", base)
  assert.equal(linkWithTenant(once, "acme", base), once,
    "SH931: copying twice produced two different strings, so `tenant` is being appended rather than"
      + " set. P5: the second copy has to be byte-identical to the first.")

  assert.equal(linkWithTenant("/bento/content?tenant=old", "acme", base),
    "https://cms.example.com/bento/content?tenant=acme",
    "SH931: a link already naming another tenant must be re-stamped, not left carrying both")

  assert.ok(linkWithTenant("/bento/home", "acme", base).startsWith("https://"),
    "SH931: the product of this function is something a curator pastes to a colleague, and a"
      + " colleague cannot open a path")

  assert.equal(linkWithTenant("/bento/x#section", "acme", base),
    "https://cms.example.com/bento/x?tenant=acme#section",
    "SH931: the fragment is part of where the link points and has to come along")
})

test("SH931: opening a link never switches the session — the switch is a click", () => {
  const bodies = effectBodies(NOTICE)

  // The one failure a scan-and-filter cannot catch by itself: a walk that finds nothing reports a
  // clean file for the same reason a correct one does. The notice has two effects — the reads and
  // the dismissal reset — so a slicer that has lost the shape says so here rather than passing.
  assert.ok(bodies.length >= 2,
    `SH931: the effect scan found ${bodies.length} effects in a file that has two, so the assertion`
      + " below is measuring nothing. Check that `useEffect(` is still how they are written before"
      + " concluding no effect touches the switch.")

  const switching = bodies.filter((body) => /switchTo|\/switch/.test(body))
  assert.deepEqual(switching, [],
    "SH931: **an effect in the tenant-link notice reaches the switch.** That turns `?tenant=` from a"
      + " claim about intent into an instruction, and every pasted URL into a state change: a"
      + " curator following a colleague's link is carried out of the tenant they were working in,"
      + " silently, leaving a half-written draft behind in it. P7.\n"
      + "The switch belongs on a control the reader presses. If the reader genuinely asked to be"
      + " moved, that is `BentoTenantSwitcher`, not a URL somebody else wrote.\n"
      + `Found in: ${switching.join("\n---\n")}`)

  assert.match(NOTICE, /onClick=\{switchTo\}/,
    "SH931: nothing presses the switch, so a reader who lands on a colleague's link is told about"
      + " the mismatch and given no way out of it. The banner has to be actionable or it is a nag.")
  assert.match(NOTICE, /memberships\.includes\(claimed\)/,
    "SH931: the switch is offered without checking membership. A button that answers 403 reads as a"
      + " broken console rather than as a link this reader was not meant to receive.")
})

test("SH931: it is inert where a link cannot be ambiguous", () => {
  assert.match(NOTICE, /useMultiTenant\(\)/,
    "SH931: the notice no longer gates on multiTenant, so a single-tenant install — where every"
      + " link means the only tenant there is — can be shown a banner about which one was meant.")
  assert.match(NOTICE, /if \(!multiTenant \|\| !claimed/,
    "SH931: the early return changed. Both conditions are the silent path: an ordinary URL on any"
      + " install must render nothing at all, including for the curator who produced the link.")

  assert.match(SHELL, /<BentoTenantLinkNotice \/>/,
    "SH931: the shell no longer mounts the notice, so a link naming another tenant opens exactly as"
      + " ambiguously as it did before — which is the defect, not the fix.")
})

test("SH931: the switcher is where a tenant-naming link is produced", () => {
  assert.match(SWITCHER, /linkWithTenant\(/,
    "SH931: the copy action builds its URL some other way. One builder, or the parameter name and"
      + " the re-stamping rule live in two places and disagree on the first edit to either.")
  assert.match(SWITCHER, /clipboard\.writeText\(/,
    "SH931: nothing copies the link, so the only way to produce one is to type the parameter by"
      + " hand — and a feature reachable only that way is one nobody uses.")

  // Inherited, not re-derived: the copy action sits inside a control that already renders only on
  // multiTenant AND two memberships, so a curator who cannot switch cannot produce an ambiguous
  // paste either. Asserted here because moving the action to a page would silently lose both.
  assert.match(SWITCHER, /tenants\.length < 2/,
    "SH931: the copy action's gate came from the switcher's own. If this control stopped gating on"
      + " membership, the link affordance would appear for readers who have one tenant to name.")
})
