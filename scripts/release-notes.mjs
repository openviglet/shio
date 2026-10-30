#!/usr/bin/env node
/*
 * SH1339 — release notes written for people, from the commit subjects since the last release.
 *
 * Every commit here is a Conventional Commit whose scope says who it is for, so the grouping is a
 * reading of the subject and not a judgement: a console or editor change is the curator's, an agent,
 * MCP or CLI change is the agent's, a fix is a fix whoever it was for. Tests, docs and chores are
 * how the work was done, not what a release gives anybody, and are left out.
 *
 * Usage (what release.yml runs):
 *   git log <previous-tag>..HEAD --format=%s | node scripts/release-notes.mjs <version>
 */
import { readFileSync } from "node:fs"

const CURATOR = /^(console|editor|site|sites|render|preview|home|ui)$/u
const AGENT = /^(agent|mcp|cli|sdk|blueprint|blueprints|cda|api)$/u

/** One subject's `type(scope): text`, or null for a subject that is not a Conventional Commit. */
export function parse(subject) {
  const match = /^(\w+)(?:\(([^)]+)\))?!?:\s*(.+)$/u.exec(subject.trim())
  return match ? { type: match[1], scope: match[2] ?? "", text: match[3] } : null
}

/** The notes, as Markdown: the curator's half, the agent's half, then fixes; empty groups left out. */
export function notesFor(subjects, version) {
  const groups = { curator: [], agent: [], fixes: [] }
  for (const subject of subjects) {
    const commit = parse(subject)
    if (!commit) continue
    if (commit.type === "fix") groups.fixes.push(commit.text)
    else if (commit.type === "feat" && CURATOR.test(commit.scope)) groups.curator.push(commit.text)
    else if (commit.type === "feat" && AGENT.test(commit.scope)) groups.agent.push(commit.text)
    else if (commit.type === "feat") groups.curator.push(commit.text)
  }
  const section = (title, items) => (items.length ? [`## ${title}`, "", ...items.map((item) => `- ${item}`), ""] : [])
  return [
    `# Shio ${version}`,
    "",
    ...section("For curators", groups.curator),
    ...section("For agents", groups.agent),
    ...section("Fixes", groups.fixes),
  ].join("\n")
}

if (import.meta.url === `file://${process.argv[1]?.replace(/\\/gu, "/").replace(/^\/?/u, "/")}`) {
  const version = process.argv[2]
  if (!version) {
    console.error("usage: git log <tag>..HEAD --format=%s | node scripts/release-notes.mjs <version>")
    process.exit(2)
  }
  const subjects = readFileSync(0, "utf8").split(/\r?\n/u).filter(Boolean)
  process.stdout.write(notesFor(subjects, version))
}
