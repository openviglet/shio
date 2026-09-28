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
import { createRequire } from "node:module"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"

// @scans prose — reason: the subjects are pnpm's verdict and two package manifests read as data;
// no source text is read as a claim about a rule (SH740).

/**
 * SH1177 — the tree's peer ranges hold on every run, not only on a resolving install.
 *
 * `strictPeerDependencies` (SH242) fires only when pnpm resolves, which an unchanged lockfile never
 * makes it do. So SH1054 committed eslint-plugin-jsx-a11y, whose peer range stops at eslint ^9, into
 * a tree on eslint 10, and every gate stayed green; the next resolving install — the design-system
 * bump to 2026.3.13 — was the first thing to refuse it. `pnpm peers check` reads the lockfile and
 * answers the same question without resolving, so it runs here, where a red one is seen at once.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const ROOT = join(HERE, "..", "..")
const require_ = createRequire(join(HERE, "..", "package.json"))

test("SH1177: pnpm reports no unmet peer anywhere in the workspace", () => {
  const run = spawnSync("pnpm", ["peers", "check"], { cwd: ROOT, encoding: "utf8", shell: true })
  const out = `${run.stdout}${run.stderr}`.trim()
  assert.equal(run.error ?? null, null, `SH1177: could not run pnpm peers check: ${run.error?.message}`)
  assert.equal(run.status, 0,
    `SH1177: a dependency declares a peer range the installed tree does not satisfy:\n${out}\n`
      + "Fix the pairing, or name the one range this tree exceeds under peerDependencyRules in"
      + " pnpm-workspace.yaml with the reason it is safe, the way the jsx-a11y entry is named.")
})

test("SH1177: the named exception is still needed — its range still stops below the installed eslint", () => {
  // The exception is a debt, and a debt nobody checks outlives its reason. When the plugin's own
  // range admits the installed eslint, this fails and the entry goes.
  const workspace = readFileSync(join(ROOT, "pnpm-workspace.yaml"), "utf8")
  assert.match(workspace, /'eslint-plugin-jsx-a11y>eslint':/u,
    "SH1177: the jsx-a11y exception this test watches is not in pnpm-workspace.yaml")
  const peer = JSON.parse(readFileSync(require_.resolve("eslint-plugin-jsx-a11y/package.json"), "utf8"))
    .peerDependencies.eslint
  const installed = Number(JSON.parse(readFileSync(require_.resolve("eslint/package.json"), "utf8"))
    .version.split(".")[0])
  const admitted = peer.split("||").map((range) => Number(/\^(\d+)/u.exec(range)?.[1]))
  assert.ok(!admitted.includes(installed),
    `SH1177: eslint-plugin-jsx-a11y now admits eslint ${installed} (${peer}); delete its entry under`
      + " peerDependencyRules in pnpm-workspace.yaml.")
})
