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
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"

/**
 * SH943 — the design system's register of what Shio imports agrees with what Shio imports.
 *
 * <p>`consumers.json` is what the package's own guards read to decide which consumers a change
 * reaches. Shio's entry listed three of the seven entries the console's source imports, so every
 * guard checked a console four entries smaller than the real one. The package corrected the entry
 * and shipped `viglet-ds-consumer-entries` (VDS129), which re-derives a consumer's imports from its
 * source and fails when the two disagree. This runs it inside `pnpm test`, so the next subpath the
 * console takes, or stops taking, fails here the day it happens rather than drifting again.</p>
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const CONSOLE_ROOT = join(HERE, "..")
const require_ = createRequire(join(CONSOLE_ROOT, "noop.js"))

/** Resolved through the package, so a release that moves the script fails here instead of skipping. */
function bin() {
  const manifest = require_.resolve("@viglet/viglet-design-system/exports.json")
  return join(dirname(dirname(manifest)), "scripts", "consumer-entries.mjs")
}

function check(cwd) {
  return spawnSync(process.execPath, [bin(), "--consumer", "shio", "--check"], {
    cwd, encoding: "utf8",
  })
}

test("SH943: Shio imports exactly the entries the design system's register declares for it", () => {
  const run = check(CONSOLE_ROOT)
  console.log(`SH943: ${(run.stdout || run.stderr).trim()}`)
  assert.equal(run.status, 0,
    "SH943: the register and the console's source disagree about which design-system entries Shio "
      + "imports, so the package's guards check a console that does not exist. Correct Shio's entry "
      + `in @viglet/viglet-design-system's consumers.json.\n${run.stdout}${run.stderr}`)
})

test("SH943: the check fails on a tree whose imports do not match, naming what is missing", () => {
  const scratch = mkdtempSync(join(tmpdir(), "sh943-"))
  try {
    mkdirSync(join(scratch, "src"))
    writeFileSync(join(scratch, "src", "a.tsx"),
      'import { BentoTile } from "@viglet/viglet-design-system/bento"\n')
    writeFileSync(join(scratch, "vite.config.ts"), "export default {}\n")
    writeFileSync(join(scratch, "package.json"), '{"name":"shio-react"}\n')

    const run = check(scratch)
    assert.notEqual(run.status, 0,
      "SH943: a tree importing one of seven entries passed, so the check is not reading the source")
    // The disagreement is reported on stderr; read both so a change of stream is not a false pass.
    const said = `${run.stdout}${run.stderr}`
    assert.match(said, /declared, and imported nowhere/)
    assert.match(said, /\.\/router/,
      "SH943: the failure has to name the entry that went missing, or it is not an instruction")
  } finally {
    rmSync(scratch, { recursive: true, force: true })
  }
})
