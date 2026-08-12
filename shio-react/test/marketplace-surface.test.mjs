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

/**
 * SH679 — the console had no blueprint surface at all, and nothing said so.
 *
 * The defect was an absence: `grep -ril blueprint shio-react/src` returned nothing, so a curator
 * could not see what the instance can build from. An absence is the one shape a behavioural test
 * cannot catch, because there is no component to render — which is why this reads the registries
 * instead. A page that exists but is wired into neither the router nor the sidebar is the same
 * defect wearing a file name.
 *
 * The third assertion is the one that will actually fire one day. i18next falls back to the key
 * itself, so a `t("marketplace.install")` with no entry renders the literal string
 * `marketplace.install` to a curator — visible only to whoever reads the console in that language,
 * which is never the person who added the call. `i18n-parity.test.mjs` holds the two locales to the
 * same *set*; this holds the page to the set.
 */
const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src")
const PAGE = join(SRC, "app", "console", "admin", "marketplace", "marketplace.list.page.tsx")

test("SH679: the marketplace page exists and is reachable", () => {
  assert.ok(existsSync(PAGE), "SH679: the console's marketplace page is gone")

  const routes = readFileSync(join(SRC, "app", "routes", "admin.routes.tsx"), "utf8")
  assert.match(routes, /path="marketplace"/,
    "SH679: the page is not registered as a route, so nothing can reach it — which is the same "
      + "absence this task closed, one level in")

  const constants = readFileSync(join(SRC, "app", "routes.const.ts"), "utf8")
  assert.match(constants, /ADMIN_MARKETPLACE:/, "SH679: no ROUTES entry")

  const sidebar = readFileSync(join(SRC, "components", "app-sidebar.tsx"), "utf8")
  assert.match(sidebar, /ROUTES\.ADMIN_MARKETPLACE/,
    "SH679: the page is not in the sidebar. A route a curator cannot find is the defect this "
      + "closed — 'nothing in the console mentions a blueprint' was true of the navigation first.")
})

test("SH679: every marketplace key the page uses exists in both locales", () => {
  const page = readFileSync(PAGE, "utf8")
  const used = [...page.matchAll(/t\("(marketplace\.[A-Za-z]+)"/g)].map((match) => match[1])
  assert.ok(used.length > 5,
    `only ${used.length} marketplace key(s) read from the page — the t() form changed and this `
      + "reader is now checking nothing, which is worse than not checking")

  for (const locale of ["en", "pt"]) {
    const bundle = JSON.parse(
      readFileSync(join(SRC, "i18n", "locales", locale, "marketplace.json"), "utf8"),
    )
    for (const key of used) {
      const leaf = key.slice("marketplace.".length)
      assert.ok(bundle.marketplace?.[leaf] !== undefined,
        `SH679: ${locale} has no ${key}. i18next falls back to the key itself, so this renders the `
          + `literal string "${key}" to a curator reading the console in ${locale}.`)
    }
  }
})
