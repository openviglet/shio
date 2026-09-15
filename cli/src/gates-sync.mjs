/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH997 — the disk and wire ends of the gate projection, kept apart from the format the way
 * `releases-sync.mjs` is kept apart from `releases-store.mjs`.
 */
import { join } from "node:path"

import { GATES_DIR, gatePathFor, parseGate, renderGate, serverSide } from "./gates-store.mjs"

/** The gate files a tree holds, parsed, in path order. */
export async function readLocalGates(ctx, { dir = GATES_DIR, site = null } = {}) {
  const root = join(ctx.cwd, dir)
  const gates = []
  for (const siteDir of await entries(ctx, root)) {
    if (site && siteDir !== site) continue
    for (const file of await entries(ctx, join(root, siteDir))) {
      if (!file.endsWith(".yaml")) continue
      const path = `${dir}/${siteDir}/${file}`
      const gate = parseGate(await ctx.fs.readFile(join(root, siteDir, file), "utf8"), path)
      if (gate.site !== siteDir) {
        throw new Error(
          `${path}: this file says site ${gate.site} and sits under ${siteDir}. The directory is ` +
            "the site, so one of the two is a rename that did not finish.",
        )
      }
      gates.push(gate)
    }
  }
  return gates.sort((left, right) => `${left.site}/${left.name}`.localeCompare(`${right.site}/${right.name}`))
}

/** Every gate the named sites declare on the instance. */
export async function readRemoteGates(agent, sites) {
  const gates = []
  for (const site of sites) {
    // An instance that answers nothing — or anything but a list — about gates is one that has none,
    // and a projection of content must not fail over a section of the tree that is empty.
    const answered = await agent.gates(site)
    for (const gate of Array.isArray(answered) ? answered : []) {
      gates.push({ ...gate, site })
    }
  }
  return gates
}

/** Writes the projection, and removes the file of a gate the instance no longer has. */
export async function writeGates(ctx, gates, { dir = GATES_DIR, site = null } = {}) {
  const written = []
  for (const gate of gates) {
    const path = `${dir}/${gatePathFor(gate.site, gate.name)}`
    await ctx.fs.mkdir(join(ctx.cwd, dir, gate.site), { recursive: true })
    await ctx.fs.writeFile(join(ctx.cwd, path), renderGate(gate), "utf8")
    written.push(path)
  }
  const keep = new Set(written)
  const removed = []
  const root = join(ctx.cwd, dir)
  for (const siteDir of await entries(ctx, root)) {
    if (site && siteDir !== site) continue
    for (const file of await entries(ctx, join(root, siteDir))) {
      const path = `${dir}/${siteDir}/${file}`
      if (!file.endsWith(".yaml") || keep.has(path)) continue
      await ctx.fs.rm(join(root, siteDir, file))
      removed.push(path)
    }
  }
  return { written, removed }
}

/**
 * Declares every local gate on the instance, and says what it left to CI.
 *
 * A gate goes up one PUT at a time, idempotently by name, the way a note does: the tree is the
 * declaration, and a push is how often it is stated.
 */
export async function pushGates(agent, gates) {
  const pushed = []
  const ciOnly = []
  for (const gate of gates) {
    const body = serverSide(gate)
    const dropped = (gate.checks ?? []).length - body.checks.length
    if (dropped > 0) {
      ciOnly.push({ gate: `${gate.site}/${gate.name}`, dropped })
    }
    await agent.putGate(gate.site, gate.name, body)
    pushed.push(`${gate.site}/${gate.name}`)
  }
  return { pushed, ciOnly }
}

async function entries(ctx, path) {
  try {
    const found = await ctx.fs.readdir(path)
    return [...found].map((entry) => (typeof entry === "string" ? entry : entry.name)).sort()
  } catch {
    // A tree with no gates is the ordinary case, not a failure.
    return []
  }
}
