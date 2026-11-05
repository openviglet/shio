/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1351 — a person can see and revoke their own device tokens from the console, and add one from a
 * session, which is how somebody who signs in with single sign-on (and so has no password for the
 * app's Basic sign-in) gets a device at all. The server half is ShDeviceTokenTest; this holds the
 * console's reach to it: the surface is declared, routed and decorated, and the page calls the three
 * verbs the controller serves.
 */
// @scans prose — reason: the subjects are a JSON declaration and sources whose route strings and calls are read as data.
import { strict as assert } from "node:assert"
import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { stripComments } from "./support/source-text.mjs"

const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")
const read = (...parts) => stripComments(readFileSync(join(SRC, ...parts), "utf8"))
const SURFACES = JSON.parse(readFileSync(join(HERE, "..", "..", "shio-app", "src", "main", "resources", "console",
  "surfaces.json"), "utf8")).surfaces

test("SH1351: the devices surface is declared, routed and in the Access hub", () => {
  const surface = SURFACES.find((one) => one.id === "devices")
  assert.ok(surface, "SH1351: console/surfaces.json declares no `devices` surface")
  assert.equal(surface.route, "/bento/admin/devices")
  assert.equal(surface.exception?.reason, "operator-only",
    "SH1351: a device token is the person; an agent minting one is an escalation, not a gap")
  assert.match(read("App.tsx"), /path="admin\/devices"\s+element=\{<BentoDeviceTokenListPage \/>\}/)
  assert.match(read("app", "system", "bento-nav.ts"), /"nav\.hub\.administration\.access", ids: \[[^\]]*"devices"/)
})

test("SH1351: the page mints, lists and revokes through the device-token endpoint", () => {
  const service = read("services", "token", "device-token.service.ts")
  assert.match(service, /axios\.get<ShDevice\[\]>\("\/v2\/device-token"/)
  assert.match(service, /axios\.post<ShMintedDevice>\("\/v2\/device-token"/)
  assert.match(service, /axios\.delete<ShDevice>\(`\/v2\/device-token\/\$\{id\}`\)/)
  const page = read("app", "admin", "device-token.list.page.tsx")
  assert.match(page, /service\.revoke\(/, "SH1351: the list must be able to revoke a device")
  assert.match(page, /minted\.token/, "SH1351: the secret is shown once, right after it is minted")
})
