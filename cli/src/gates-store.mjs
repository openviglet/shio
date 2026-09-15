/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH997 — a publish gate as a file: `shio/gates/<site>/<name>.yaml`.
 *
 * The format half is pure (no fs, no network), like `content-files.mjs` and `releases-store.mjs`, so
 * what a gate looks like on disk is testable without an instance.
 *
 * **The file is the whole checklist; the instance stores the half it can enforce.** `audit` drives a
 * browser per viewport and the instance does not run one — a standing non-goal — so a gate naming it
 * is pushed without that step and `shio gate run` runs it here, where the browser already is. The
 * split is stated in the file's own header, because a curator reading the tree should not have to
 * learn from a refusal which of their checks the server keeps.
 */

/** Where a gate lands in the tree. */
export const GATES_DIR = "shio/gates";

/** The checks this instance evaluates at publish time; anything else runs in CI. */
export const SERVER_CHECKS = ["verify", "links-resolve", "render-digest"]

/** The checks `shio gate run` runs itself, because the instance cannot. */
export const CI_CHECKS = ["audit"]

export function gatePathFor(site, name) {
  return `${site}/${name}.yaml`
}

/** The file a gate projects to — hand-written and byte-stable, so a re-pull is an empty diff (P5). */
export function renderGate(gate) {
  const lines = [
    "# Written by `shio pull --content`. The checks below run at publish time on the",
    "# instance, except `audit`, which needs a browser and runs in `shio gate run`.",
    `name: ${gate.name}`,
    `site: ${gate.site}`,
    `enabled: ${gate.enabled === false ? "false" : "true"}`,
    "checks:",
  ]
  for (const check of gate.checks ?? []) {
    lines.push(`  - check: ${check.check}`)
    lines.push(`    severity: ${check.severity ?? "error"}`)
  }
  return `${lines.join("\n")}\n`
}

/**
 * Reads one back.
 *
 * Refuses what it cannot read rather than guessing: a gate file that parses to half its checks is a
 * gate that passes what nobody looked at, which is the failure this whole task is about.
 */
export function parseGate(text, path = "<gate>") {
  const gate = { checks: [] }
  let current = null
  for (const raw of String(text).split(/\r?\n/)) {
    const line = raw.trimEnd()
    if (!line || line.trimStart().startsWith("#")) continue
    const item = /^ {2}- check:\s*(.+)$/.exec(line)
    if (item) {
      current = { check: item[1].trim(), severity: "error" }
      gate.checks.push(current)
      continue
    }
    const attr = /^ {4}severity:\s*(.+)$/.exec(line)
    if (attr && current) {
      current.severity = attr[1].trim()
      continue
    }
    const pair = /^([A-Za-z]+):\s*(.*)$/.exec(line)
    if (!pair) {
      throw new Error(
        `${path}: cannot read line ${JSON.stringify(line)}. A gate file is \`name\`, \`site\`, ` +
          "`enabled` and a `checks:` list of `  - check: <name>` / `    severity: error|warning`.",
      )
    }
    if (pair[1] !== "checks") gate[pair[1]] = pair[2].trim()
  }
  if (!gate.name || !gate.site) {
    throw new Error(`${path}: a gate file needs \`name\` and \`site\`.`)
  }
  gate.enabled = gate.enabled !== "false"
  return gate
}

/** What of a gate the instance is asked to hold: everything it can evaluate at publish time. */
export function serverSide(gate) {
  return {
    name: gate.name,
    enabled: gate.enabled !== false,
    checks: (gate.checks ?? []).filter((check) => SERVER_CHECKS.includes(check.check)),
  }
}

/** The steps `shio gate run` has to run itself, because no instance can. */
export function ciSide(gate) {
  return (gate.checks ?? []).filter((check) => CI_CHECKS.includes(check.check))
}
