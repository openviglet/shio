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

/**
 * SH46 — a single-tenant install does not advertise the tenant admin.
 *
 * `shio.multi-tenant` is **off by default**, so almost every install is single-tenant. The page
 * header had gated the tenant switcher on `discovery.multiTenant` since SH22, and the sidebar's
 * Tenants item was never gated at all — so the default install offered a curator a page for
 * managing tenants it does not have, and `GET /api/v2/tenant/current` answers `shio` there
 * whatever they do on it.
 *
 * Read here rather than rendered, for `marketplace-surface.test.mjs`'s reason: the defect is what
 * the navigation *offers*, and a registry read is what can see that. The rendering half is
 * `shio-react/e2e`, which needs a running instance.
 */
const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src")
const SIDEBAR = join(SRC, "components", "app-sidebar.tsx")

test("SH46: the Tenants nav item is gated on multiTenant, like the switcher beside it", () => {
  const sidebar = readFileSync(SIDEBAR, "utf8")

  assert.match(sidebar, /useDiscovery/,
    "SH46: the sidebar no longer reads discovery, so it cannot know whether this install is "
      + "multi-tenant and the gate below is decoration")
  assert.match(sidebar, /multiTenant/,
    "SH46: nothing in the sidebar mentions multiTenant — the Tenants item is unconditional again, "
      + "which is the state this closed")

  // The item and the flag on one line each is not enough: the assertion is that the item is
  // INSIDE the conditional. A file carrying both, with the item still listed unconditionally,
  // would pass a pair of `match` calls and ship exactly the defect.
  const gated = /\.\.\.\(multiTenant[\s\S]{0,240}?ROUTES\.ADMIN_TENANTS/
  assert.match(sidebar, gated,
    "SH46: ROUTES.ADMIN_TENANTS is in the sidebar but not inside the multiTenant conditional, so "
      + "a single-tenant install still advertises it")
})

test("SH46: the header and the sidebar gate on the same fact", () => {
  // Two surfaces deciding 'is this a SaaS install' by different means is SH583's shape, and it is
  // how the sidebar came to disagree with the header in the first place.
  const header = readFileSync(join(SRC, "components", "page-header.tsx"), "utf8")
  const sidebar = readFileSync(SIDEBAR, "utf8")

  for (const [name, source] of [["page-header", header], ["app-sidebar", sidebar]]) {
    assert.match(source, /useDiscovery/,
      `SH46: ${name} decides tenancy without reading discovery, so the two can drift`)
    assert.match(source, /discovery\?\.multiTenant/,
      `SH46: ${name} reads some other flag for tenancy — one fact, one source`)
  }
})

test("SH46: the dependency list carries the flag, or the gate is computed once and frozen", () => {
  // `navGroups` is a useMemo. Reading `multiTenant` inside it without listing it means the nav is
  // built from whatever discovery said on first paint — which, for an async hook, is `undefined`,
  // so the item would stay hidden on a multi-tenant install instead. The gate has to be able to
  // change its mind when discovery resolves.
  const sidebar = readFileSync(SIDEBAR, "utf8")
  const deps = /\],\s*\[[^\]]*multiTenant[^\]]*\]\)/
  assert.match(sidebar, deps,
    "SH46: multiTenant is read inside the memo but missing from its dependency list, so the nav "
      + "keeps whatever the flag was before discovery resolved")
})
