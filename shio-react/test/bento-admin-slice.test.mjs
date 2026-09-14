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
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { stripComments } from "./support/source-text.mjs"

/**
 * SH905 — the eleven infrastructure pages, and the one defect class worth an instrument.
 *
 * §LV.8 warned that "the risk in a slice this size is that it becomes a mechanical sweep with no
 * attention left over". The answer to that is not resolve, it is a reader: eleven pages is more
 * than anyone checks by eye twice.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")
const APP = stripComments(readFileSync(join(SRC, "App.tsx"), "utf8"))
const CONSOLE_ADMIN = join(SRC, "app", "admin")

/**
 * Every `<Route path="…/:param" element={<X />} />` under the bento tree, as `[param, component]`.
 *
 * Only the bento tree: the console's own routes are declared in `app/routes/*.tsx` beside the
 * components they mount, and they have matched for years. The new ones are the risk.
 */
function bentoParamRoutes() {
  const out = []
  /*
   * Whole `<Route …>` elements, not a fixed prop order. App.tsx declares these as one-liners and as
   * multi-line elements, and a pattern written against one shape silently reads half the routes.
   * That is how the first draft of this reader found 8 of 13 and reported a clean tree.
   */
  for (const [block] of APP.matchAll(/<Route\b[\s\S]*?\/>/g)) {
    const path = block.match(/path="([^"]*:(\w+))"/)
    if (!path) continue
    const component = block.match(/<(\w+)\s*\/>/)
    if (component) out.push({ path: path[1], param: path[2], component: component[1] })
  }
  return out
}

/** Every parameterised route the file declares, counted without parsing — the vacuity yardstick. */
function rawParamRouteCount() {
  return (APP.match(/path="[^"]*:\w+"/g) ?? []).length
}

/** Which route param each console admin page actually reads out of `useParams()`. */
function paramsReadBy() {
  const read = new Map()
  const walk = (dir) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const full = join(dir, entry.name)
      if (entry.isDirectory()) walk(full)
      else if (entry.name.endsWith(".tsx")) {
        const source = stripComments(readFileSync(full, "utf8"))
        const destructured = source.match(/const\s*\{([^}]*)\}\s*=\s*useParams\(\)/)
        if (!destructured) continue
        const names = destructured[1]
          .split(",")
          .map((n) => n.split("=")[0].split(":")[0].trim())
          .filter(Boolean)
        const component = source.match(/export default function (\w+)/)?.[1]
        if (component) read.set(component, names)
      }
    }
  }
  walk(CONSOLE_ADMIN)
  return read
}

test("SH905: a route's param is named what the component it mounts reads", () => {
  /*
   * The defect this exists for, which happened twice in two tasks and looks like nothing.
   *
   * `<Route path="admin/auth-providers/:providerId" element={<AuthProviderPage />} />` against a
   * component whose first line is `const { authProviderId } = useParams()`. React Router matches
   * the route happily; the component reads `undefined`; `isNew` is false, the load never runs and
   * the page renders `null`. **A blank page, no error, no failed request, and `tsc` is silent** —
   * `useParams()` returns `Record<string, string | undefined>`, so every param name type-checks.
   *
   * SH904 hit the same shape from the other end: a static `/new` route beside a `:id` one, which
   * matches first and carries no param at all. Same symptom, same silence.
   */
  const reads = paramsReadBy()
  const broken = []
  for (const route of bentoParamRoutes()) {
    const names = reads.get(route.component)
    if (!names) continue
    if (!names.includes(route.param)) {
      broken.push(`${route.path} mounts ${route.component}, which reads {${names.join(", ")}}`)
    }
  }

  assert.deepEqual(broken, [],
    "SH905: these routes declare a param the component they mount never reads. The component gets"
      + " `undefined`, its `isNew`/load branch never fires, and the page renders nothing at all —"
      + " with no error, no failed request and no type error, because `useParams()` is typed"
      + " `Record<string, string | undefined>` and every name checks out.")
})

test("SH905: the reader is not vacuous — it pairs real routes with real components", () => {
  /*
   * A reader that matched nothing would pass here for ever. The route pattern is fiddly (a
   * multi-line element puts line breaks between `element` and the component), so this asserts that
   * it still finds both sides rather than assuming.
   */
  const routes = bentoParamRoutes()
  const reads = paramsReadBy()
  const paired = routes.filter((r) => reads.has(r.component))
  const raw = rawParamRouteCount()

  console.log(`SH905: ${routes.length}/${raw} parameterised routes parsed, ${paired.length} paired`)

  assert.equal(routes.length, raw,
    `SH905: App.tsx declares ${raw} parameterised routes and this reader parsed ${routes.length}.`
      + " The ones it missed are invisible to the check above, which then reports a clean tree"
      + " while naming nothing — the exact failure this reader was written after.")
  assert.ok(paired.length >= 5,
    `SH905: ${routes.length} routes found but only ${paired.length} pair with a component that`
      + " calls useParams. Either the components changed how they read params, or this reader"
      + " stopped recognising the ones it is meant to guard.")
})

test("SH905: the eleven infrastructure pages are all reachable under bento", () => {
  /*
   * A migrated page nothing routes to is a page that was not migrated. This is cheap and it is
   * what makes the count in the changelog checkable — eleven is the number §LV.8 asked for, and
   * "eleven" written in prose is not evidence of anything.
   */
  const expected = [
    "admin/tokens", "admin/tokens/:tokenId",
    "admin/auth-providers", "admin/auth-providers/:authProviderId",
    "admin/exchange-providers", "admin/exchange-providers/:exchangeProviderId",
    "admin/webhooks", "admin/webhooks/:webhookId",
    "admin/tenants", "admin/tenants/:tenantId",
    "admin/email-config",
  ]
  const missing = expected.filter((path) => !APP.includes(`path="${path}"`))
  assert.deepEqual(missing, [],
    "SH905: these infrastructure routes are not mounted under /bento, so the pages they name are"
      + " migrated and unreachable — which is indistinguishable from not migrated at all.")
})

test("SH905: every card in the six editors has a header, so the adapter can re-skin it", () => {
  /*
   * SH902's defect, guarded where the sweep could have reintroduced it eleven times over.
   *
   * `AdaptiveSectionCard` renders its frosted form only when it finds a Header child, so a
   * header-less one renders a plain card and nothing says so. Every section in these editors has a
   * real header, which is why they take `AdaptiveSectionCard` where SH902's panels take `Panel`. If
   * a later edit drops a header, this is the difference between noticing and not.
   */
  const editors = [
    "api-token/api-token.page.tsx",
    "auth-provider/auth-provider.page.tsx",
    "exchange-provider/exchange-provider.page.tsx",
    "site-webhook/site-webhook.page.tsx",
    "tenant/tenant.page.tsx",
    "email-config/email-config.page.tsx",
  ]
  for (const rel of editors) {
    const source = stripComments(readFileSync(join(CONSOLE_ADMIN, rel), "utf8"))
    const opens = (source.match(/<AdaptiveSectionCard[\s>]/g) ?? []).length
    const headers = (source.match(/<AdaptiveSectionCard\.(Static)?Header/g) ?? []).length
    assert.equal(opens, headers,
      `SH905: ${rel} has ${opens} AdaptiveSectionCard(s) and ${headers} header(s). One without a`
        + " header renders a console card under the bento shell in silence (SH902) — give it a"
        + " header, or use Panel and mean it.")
    assert.doesNotMatch(source, /@\/components\/ui\/card/,
      `SH905: ${rel} imports Card again, so that panel cannot follow the chrome provider.`)
  }
})
