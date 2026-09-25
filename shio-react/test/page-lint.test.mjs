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
import { createRequire } from "node:module"
import { cpSync, existsSync, mkdtempSync, readFileSync, writeFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"

/**
 * SH947 — the design system's two named page rules run inside Shio's own test run.
 *
 * ## What they are, and why they were not checked
 *
 * The contract names two rules a page must keep: **`page.column`** — a routed page sets no width,
 * gutter or vertical rhythm on its outermost element, because BentoShell owns the column since
 * SH946 — and **`primary.direct`** — nothing writes `--vg-primary` directly. The package shipped
 * `viglet-ds-page-lint` for them (VDS132), and nothing here ran it, so both rules held exactly as
 * long as somebody remembered them.
 *
 * They were not holding. The first run found **29** outermost elements setting `p-6`, `p-4`,
 * `max-w-2xl` or `py-24` — a second gutter inside the one the shell already draws. SH946 moved the
 * column into the shell and cleaned the five pages that set a *width*; `bento-column.test.mjs`
 * looked for `mx-auto max-w-*` and so could not see a bare `p-6`. Twenty-seven were removed, the
 * six `max-w-2xl` editors moved to the shell's `narrow` column, and two carry a reasoned
 * exemption because they render outside the shell.
 *
 * ## Why it runs here
 *
 * The same reason `no-duplicate-components.test.mjs` does: `pnpm test` is a gate that runs on every
 * push, and a check reachable only by a second command runs when somebody remembers.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const CONSOLE_ROOT = join(HERE, "..")
const require_ = createRequire(join(CONSOLE_ROOT, "noop.js"))

/** Resolved through the package, so a release that moves the script is a failure here, not a skip. */
function gatePath() {
  const manifest = require_.resolve("@viglet/viglet-design-system/exports.json")
  return join(dirname(dirname(manifest)), "scripts", "page-lint.mjs")
}

function run(args, cwd = CONSOLE_ROOT) {
  return spawnSync(process.execPath, [gatePath(), ...args], { cwd, encoding: "utf8" })
}

test("SH947: no routed page sets its own column, and nothing sets --vg-primary", () => {
  const result = run(["src", "--check"])

  assert.equal(result.status, 0,
    "SH947: the design system's page lint found a page setting what BentoShell owns.\n\n"
      + `${result.stdout}${result.stderr}\n`
      + "Remove the classes it names. A page that genuinely needs another width names it in\n"
      + "src/app/system/bento-column.ts (narrow or wide), not on its own element. A page that\n"
      + "renders outside the shell says so in the file, with the reason:\n"
      + "  {/* viglet-ds-allow-page-column -- why nothing else sets this page's column */}")
})

test("SH947: the lint is reachable and reads pages, not an empty tree", () => {
  assert.ok(existsSync(gatePath()),
    `SH947: the page lint is not at ${gatePath()}. A design-system release moved or dropped it,`
      + " and this reader would otherwise be reporting a spawn failure as a clean tree.")

  const result = run(["src", "--check"])
  const pages = /(\d+) of them pages/.exec(result.stdout)
  assert.ok(pages && Number(pages[1]) >= 40,
    "SH947: the lint scanned fewer than forty pages, so its clean verdict covers a fraction of the"
      + ` console. It reported: ${result.stdout}${result.stderr}`)
})

test("SH947: the lint is not vacuous — a width planted on a routed page fails it, naming the file", () => {
  // A copy, never the tree: the planted class must not survive a crashed run.
  const dir = mkdtempSync(join(tmpdir(), "sh947-"))
  const page = join("src", "app", "content", "trash.page.tsx")
  cpSync(join(CONSOLE_ROOT, "src"), join(dir, "src"), { recursive: true })

  const planted = join(dir, page)
  const source = readFileSync(planted, "utf8")
  // The same shape as twenty-seven of the findings the first run reported.
  const dirty = source.replace(/(return \(\s*<>[\s\S]*?\/>\s*)<div>/, '$1<div className="mx-auto max-w-4xl px-6">')
  assert.notEqual(dirty, source, "SH947: trash.page.tsx changed shape; plant the width somewhere else")
  writeFileSync(planted, dirty)

  const failed = run(["src", "--check"], dir)
  assert.notEqual(failed.status, 0,
    "SH947: a max-w-4xl on a page's outermost element did not fail the lint, so the passing run"
      + " above proves nothing about this console.")
  assert.match(`${failed.stdout}${failed.stderr}`, /trash\.page\.tsx.*page\.column/,
    "SH947: the lint failed without naming the file and rule, so its output cannot be the fix")
})
