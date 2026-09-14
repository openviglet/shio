/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { readdirSync, readFileSync, statSync } from "node:fs"
import { dirname, join, relative } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"

/**
 * SH46 — a single-tenant install does not advertise the tenant admin.
 *
 * `shio.multi-tenant` is **off by default**, so almost every install is single-tenant. The page
 * header had gated the tenant switcher on `discovery.multiTenant` since SH22, and the sidebar's
 * Tenants item was never gated at all — so the default install offered a curator a page for
 * managing tenants it does not have, and `GET /api/v2/tenant/current` answers `shio` there
 * whatever they do on it.
 *
 * ## Where this moved, and why the reader survived the move (SH910)
 *
 * Both surfaces this was written against are gone: `page-header.tsx` and `app-sidebar.tsx` went
 * with the console shell. The **defect** did not go anywhere — the rail and the command palette
 * offer the same entry, off `bento-nav.ts` — so this reader follows the rule to its new home
 * rather than being deleted with the files it happened to name. A reader deleted alongside the
 * surface it guarded is how a closed defect reopens.
 *
 * `bento-nav.test.mjs` asserts the **declaration**: that `tenants` is the one gated id and that
 * the filter arithmetic drops exactly one entry. This asserts the **hook** — that the gate reads
 * discovery at all, that one source answers it, and that the memo can change its mind. Those are
 * the three ways this comes back, and none of them is visible in the declaration.
 *
 * Read here rather than rendered, for `marketplace-surface.test.mjs`'s reason: the defect is what
 * the navigation *offers*, and a registry read is what can see that. The rendering half is
 * `shio-react/e2e`, which needs a running instance.
 */
const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src")
const NAV = join(SRC, "app", "system", "bento-nav.ts")

test("SH46: the Tenants nav entry is gated on multiTenant, and the gate is not decoration", () => {
  const nav = readFileSync(NAV, "utf8")

  assert.match(nav, /useDiscovery/,
    "SH46: the nav no longer reads discovery, so it cannot know whether this install is "
      + "multi-tenant and the gate below is decoration")
  assert.match(nav, /discovery\?\.multiTenant/,
    "SH46: the nav reads some other flag for tenancy — one fact, one source")

  // The entry and the flag on one line each is not enough: the assertion is that the entry is
  // gated. A file carrying both, with the item still listed unconditionally, would pass a pair of
  // `match` calls and ship exactly the defect.
  // SH935 moved the route onto `...at(ROUTES.ADMIN_TENANTS)`, so the closing paren is optional
  // here. What must not become optional is the *adjacency*: the gate has to sit on the entry that
  // names the route, not merely somewhere in the same file.
  assert.match(nav, /ROUTES\.ADMIN_TENANTS\)?,\s*requires:\s*"multiTenant"/,
    "SH46: the tenants entry is declared without `requires: \"multiTenant\"`, so a single-tenant "
      + "install advertises it again")
  assert.match(nav, /\.filter\(\([^)]*\) => [^)]*requires !== "multiTenant" \|\| multiTenant\)/,
    "SH46: the filter that applies `requires` is gone or changed shape, so every declared entry "
      + "is visible whatever it asks for — the gate would be a field nothing reads")
})

test("SH46: one surface decides tenancy, so there is nothing to drift against", () => {
  /*
   * The original defect was two surfaces answering "is this a SaaS install" by different means —
   * SH583's shape — which is how the sidebar came to disagree with the header. After SH910 there
   * is one navigation declaration, and this is what keeps it that way: a second consumer of
   * `useDiscovery` is a second answer waiting to differ from this one.
   *
   * `use-discovery.ts` and `discovery-store.ts` are the hook and its cache, not consumers.
   */
  const files = []
  const walk = (dir) => {
    for (const name of readdirSync(dir)) {
      const path = join(dir, name)
      if (statSync(path).isDirectory()) walk(path)
      else if (/\.(tsx?|jsx?)$/.test(path)) files.push(path)
    }
  }
  walk(SRC)

  const consumers = files
    .filter((file) => /\buseDiscovery\b/.test(readFileSync(file, "utf8")))
    .map((file) => relative(SRC, file).replaceAll("\\", "/"))
    .filter((name) => name !== "hooks/use-discovery.ts" && name !== "lib/discovery-store.ts")
    .sort()

  assert.deepEqual(consumers, ["app/system/bento-nav.ts"],
    "SH46: discovery is read from more than one place. Two surfaces deciding tenancy separately "
      + "is the arrangement that produced this defect — the second one is where the gate will be "
      + "forgotten. Route the new caller through the nav declaration instead.")
})

test("SH46: the dependency list carries the flag, or the gate is computed once and frozen", () => {
  // `useBentoNav` is a useMemo. Reading `multiTenant` inside it without listing it means the nav is
  // built from whatever discovery said on first paint — which, for an async hook, is `undefined`,
  // so the item would stay hidden on a multi-tenant install instead. The gate has to be able to
  // change its mind when discovery resolves.
  const nav = readFileSync(NAV, "utf8")
  const deps = /\},\s*\[[^\]]*multiTenant[^\]]*\]\)/
  assert.match(nav, deps,
    "SH46: multiTenant is read inside the memo but missing from its dependency list, so the nav "
      + "keeps whatever the flag was before discovery resolved")
})
