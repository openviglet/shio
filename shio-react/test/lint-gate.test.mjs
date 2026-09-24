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
import { dirname, join, relative } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"

/**
 * SH1075 — the console lint, run by the suite every commit runs.
 *
 * ## What went wrong
 *
 * SH665 made `pnpm lint` a CI step: the findings that existed went into
 * `eslint-suppressions.json`, and anything new fails. Only CI ran it. `pnpm test` never did, and
 * `cli/test/lint-baseline.test.mjs` compares the suppressions file with its declared list, not
 * with a run. So the lint went red and stayed red for weeks, twenty errors over seventeen files,
 * while every local gate was green. Two of those errors were on pages SH701 had already paid off.
 *
 * ## What this does
 *
 * It runs ESLint over the console exactly as `pnpm lint` does, suppressions included, and fails
 * on any error. A new finding now fails the gate somebody runs before committing, not a CI job
 * they may not look at. It costs about thirteen seconds, measured when it landed.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const ESLINT = join(ROOT, "node_modules", "eslint", "bin", "eslint.js")

test("SH1075: the console lint has no error the suppressions file does not hold", () => {
  const run = spawnSync(process.execPath, [ESLINT, ".", "--format", "json"], {
    cwd: ROOT, encoding: "utf8", maxBuffer: 64 * 1024 * 1024,
  })
  assert.ok(run.stdout, `SH1075: ESLint printed nothing (exit ${run.status}): ${run.stderr}`)
  const results = JSON.parse(run.stdout)

  const errors = results.flatMap((file) => file.messages
    .filter((message) => message.severity === 2)
    .map((message) => `${relative(ROOT, file.filePath).replaceAll("\\", "/")}:${message.line}  `
      + `${message.ruleId ?? "(no rule)"}  ${message.message.split("\n")[0]}`))
  const warnings = results.reduce((sum, file) => sum + file.warningCount, 0)

  console.log(`SH1075: ESLint read ${results.length} file(s): ${errors.length} error(s), `
    + `${warnings} warning(s)`)

  // Not vacuous: a config that matched nothing would lint no files and report no errors.
  assert.ok(results.length > 100,
    `SH1075: ESLint read ${results.length} file(s), so the config is not reaching the console`)

  assert.deepEqual(errors, [],
    "SH1075: the console lint has an error that eslint-suppressions.json does not hold, which "
    + "fails CI's `pnpm lint` step as well. Fix it. A suppression is for a finding that cannot be "
    + "fixed yet, and it has to be declared by name in cli/test/lint-baseline.test.mjs with the "
    + "reason.")
})
