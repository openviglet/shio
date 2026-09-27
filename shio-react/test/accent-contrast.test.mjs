/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { spawnSync } from "node:child_process"
import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { stripComments } from "./support/source-text.mjs"

/**
 * SH1051 — Shio's accent and primary, measured rather than asserted present.
 *
 * SH890's gate checked the four accent tokens existed and never what they measured. Measured with
 * the design system's own arithmetic (VDS137's `viglet-ds-page-lint --contrast`), the accent text
 * was 3.59:1 on the light ground and white on the button's gradient 3.89:1 and 3.00:1 — all under
 * AA's 4.5. And nothing set the four `--vg-primary-*-base` inputs, so every surface the authoring
 * contract says reads `--primary` — the rail's you-are-here marker among them — rendered the preset's
 * neutral. This runs the measurement on every `pnpm test`, and holds the claim to the preset.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
// The package exports no `package.json`, so its directory is found where pnpm links it.
const DS = join(ROOT, "node_modules", "@viglet", "viglet-design-system")
const CSS = readFileSync(join(ROOT, "src", "index.css"), "utf8")

test("SH1051: every accent and primary pair Shio re-keys clears 4.5:1 on both grounds", () => {
  const lint = spawnSync(process.execPath,
    [join(DS, "scripts", "page-lint.mjs"), "--contrast", join(ROOT, "src", "index.css"), join(ROOT, "src")],
    { encoding: "utf8" })
  const out = `${lint.stdout}${lint.stderr}`.trim()
  // Printed on every run: a gate whose numbers nobody sees is a gate nobody notices moving.
  console.log(`SH1051: ${out.split("\n")[0]}`)
  assert.equal(lint.status, 0, `SH1051: the design system's contrast measurement refused Shio's tokens:\n${out}`)
  assert.doesNotMatch(out, /contrast\.(pair|unread)/u, `SH1051: a pair under AA:\n${out}`)
})

test("SH1051: Shio claims every family the preset lets a product re-key, through its inputs", () => {
  const preset = readFileSync(join(DS, "dist", "preset.css"), "utf8")
  // The claimable inputs, read from the preset rather than listed here: a family the package adds
  // fails this build by name instead of rendering its neutral in Shio unnoticed.
  const inputs = [...new Set([...preset.matchAll(/(--vg-(?:[a-z]+-)*base(?:-dark)?)\s*:/gu)].map((m) => m[1]))]
  const accent = [...new Set([...preset.matchAll(/(--vg-accent-(?:from|to|text|text-dark))\s*:/gu)].map((m) => m[1]))]
  assert.ok(inputs.length >= 4, `SH1051: the preset's inputs were not read (${inputs.length})`)
  const root = /:root\s*\{([\s\S]*?)\}/u.exec(CSS)?.[1] ?? ""
  const unclaimed = [...inputs, ...accent].filter((token) => !root.includes(`${token}:`))
  assert.deepEqual(unclaimed, [],
    "SH1051: a family the preset lets a product claim is left at the package's neutral in Shio")
  assert.doesNotMatch(stripComments(CSS), /--vg-primary\s*:/u,
    "SH1051: primary is claimed through the base inputs, never by setting the compiled token")
})
