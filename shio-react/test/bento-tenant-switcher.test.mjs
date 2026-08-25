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
 * SH934 — the console can change which tenant it is working in again.
 *
 * <h2>What was actually lost, and how</h2>
 *
 * SH910 deleted `components/tenant-switcher.tsx` because a reachability walk found nothing
 * rendering it. That was true. It was also the only caller of `POST /tenant/{name}/switch`, and
 * `ShVigletTenantResolver` reads the tenant from the session and **never** from the URL — so a
 * curator with two memberships was left in whichever tenant their session already held, with
 * logging in again as the workaround. "Unreferenced" and "unneeded" are different findings, and a
 * deletion pass can only see the first.
 *
 * <h2>Why this reads source instead of rendering</h2>
 *
 * The behaviour worth asserting is *when the control appears*, and it appears on the intersection
 * of a feature flag and a membership list — two async reads, a design-system dropdown and a shell.
 * Rendering that needs the whole provider stack to answer a question the source states plainly.
 * `shio-react/e2e` is where the rendered half belongs, and it needs a running multi-tenant
 * instance, which is exactly the environment nobody has locally.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")

const SWITCHER = stripComments(
  readFileSync(join(SRC, "app", "bento", "bento-tenant-switcher.tsx"), "utf8"))
const SHELL = stripComments(readFileSync(join(SRC, "app", "bento", "bento.root.page.tsx"), "utf8"))
const NAV = stripComments(readFileSync(join(SRC, "app", "bento", "bento-nav.ts"), "utf8"))

test("SH934: the switcher calls the endpoint that changes the session's tenant", () => {
  assert.match(SWITCHER, /switchTo\(/,
    "SH934: nothing calls ShTenantService.switchTo, so this control cannot change the tenant. The"
      + " session is the only thing that decides it — the URL never did.")
  assert.match(SWITCHER, /service\.mine\(\)/,
    "SH934: the switcher no longer reads /tenant/mine, so it cannot know which tenants this reader"
      + " may switch to. Listing every tenant on the instance would offer a curator ones they are"
      + " not a member of.")
  assert.match(SWITCHER, /location\.reload\(\)/,
    "SH934: switching no longer reloads. The tenant is a session property and everything on screen"
      + " was fetched under the old one, so leaving the page up shows another tenant's data until"
      + " something happens to re-fetch it.")
})

test("SH934: it appears only where there is somewhere to switch to", () => {
  /*
   * Both halves, and the second is what the deleted control got wrong: it rendered on
   * `tenants.length === 0` being false, so a curator with exactly one membership was offered a
   * menu whose only entry was the tenant they were already in.
   */
  assert.match(SWITCHER, /useMultiTenant\(\)/,
    "SH934: the switcher no longer reads the multiTenant flag, so a single-tenant install is"
      + " offered a control for something it does not have — SH46's finding, on a new surface.")
  assert.match(SWITCHER, /tenants\.length < 2/,
    "SH934: the membership condition changed. A reader belonging to ONE tenant has nowhere to go,"
      + " and a dropdown whose only row is the current tenant is noise that looks like a feature.")

  assert.match(SHELL, /<BentoTenantSwitcher \/>/,
    "SH934: the shell no longer mounts the switcher, so it is a component nothing renders — which"
      + " is precisely the state that got its predecessor deleted.")
})

test("SH934: one surface still decides tenancy, so the gate cannot drift", () => {
  // SH46's defect was two surfaces answering "is this a SaaS install" differently. The switcher
  // asks through the nav declaration rather than calling useDiscovery itself, and
  // `tenant-nav-gating.test.mjs` is what fails if a second consumer ever appears.
  assert.match(NAV, /export function useMultiTenant\(\)/,
    "SH934: bento-nav no longer exports useMultiTenant, so the switcher has to read discovery"
      + " itself and the two gates can disagree about whether to appear.")
  assert.doesNotMatch(SWITCHER, /useDiscovery/,
    "SH934: the switcher reads discovery directly. That is the second consumer SH46 exists to"
      + " prevent — route it through useMultiTenant() in bento-nav.ts.")
})
