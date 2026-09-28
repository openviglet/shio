/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1191 — nothing measured how the console looks, so fifteen button heights, ten radii and four
 * title offsets passed every gate here. The census that measures them is the design system's
 * (VDS180); what this file holds is that it is one command from this repository and that the
 * command points at this console, not at whatever `look:census` defaults to.
 */
// @scans prose — reason: the subject is package.json parsed as data and an imported module's exports.
import { test } from "node:test"
import assert from "node:assert/strict"
import { mkdirSync, mkdtempSync, readFileSync, writeFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { dirname, join } from "node:path"
import { fileURLToPath } from "node:url"

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..", "..")
const { CONSUMER, SHELL_ROUTE, censusArgs, designSystemCheckout } = await import(
  new URL("../../scripts/look-census.mjs", import.meta.url)
)

test("SH1191: `pnpm look` runs the design system's census from this repository", () => {
  const scripts = JSON.parse(readFileSync(join(ROOT, "package.json"), "utf8")).scripts
  assert.equal(scripts.look, "node scripts/look-census.mjs",
    "SH1191: the root `look` script is how a console task proves a figure dropped; without it the"
      + " census is a command in another repository that nobody here runs.")
})

test("SH1191: the census walks this console's shell, as the consumer the register names", () => {
  const args = censusArgs({ SHIO_CONSOLE_URL: "http://localhost:5173/", SHIO_USER: "u", SHIO_PASSWORD: "p" }, ["--json"])
  assert.equal(CONSUMER, "shio")
  assert.equal(SHELL_ROUTE, "/bento")
  assert.deepEqual(args, ["--url", "shio=http://localhost:5173/bento", "--auth", "shio=u:p", "--json"])
})

test("SH1191: the checkout is the design system's on the same version line, or an override", () => {
  const viglet = mkdtempSync(join(tmpdir(), "sh1191-"))
  const repo = join(viglet, "shio", "2026.9")
  const ds = join(viglet, "viglet-design-system", "2026.9")
  mkdirSync(repo, { recursive: true })
  assert.equal(designSystemCheckout(repo, {}), null,
    "SH1191: with no checkout beside it the wrapper must say so, not run a census that is not there")
  mkdirSync(join(ds, "scripts"), { recursive: true })
  writeFileSync(join(ds, "scripts", "look-census.mjs"), "")
  assert.equal(designSystemCheckout(repo, {}), ds)
  assert.equal(designSystemCheckout(repo, { VIGLET_DS_CHECKOUT: viglet }), viglet)
})
