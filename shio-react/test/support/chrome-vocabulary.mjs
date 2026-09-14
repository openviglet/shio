/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { readdirSync, readFileSync } from "node:fs"
import { join, relative } from "node:path"

// @scans both — reason: four of the five probes are code (an import, a hook call, a comparison, a
// prop) and the fifth is prose by definition — a comment explaining two eras is vocabulary the
// block retires just as surely as the provider is, and SH962 is the line that deletes it (SH740).

/**
 * SH957 — the two-chrome vocabulary left in the console, counted rather than grepped.
 *
 * <p>Where the walk goes: `src`, `test`, `scripts` and `e2e`. §LXII.1 named the first three; `e2e`
 * is here because its console check names a chrome eight times, and a census that stops one
 * directory short is how the last reference survives the sweep it was written for.</p>
 */
export const AREAS = ["src", "test", "scripts", "e2e"]

const SKIP = new Set(["node_modules", "dist", "build", "coverage"])
const EXTENSIONS = /\.(?:[cm]?[jt]sx?|json)$/

/**
 * The five probes, each named so a finding says which kind of reference it is.
 *
 * <p>The first four are exact. The fifth is the one §LXII.1 describes as "the word chrome in a
 * comment that explains two eras", and it is a heuristic said out loud: a comment line counts when
 * it names a chrome <b>and</b> an era or the machinery that chose between them. "The panel carries
 * the chrome" does not count, because it is about a panel and would be true in a one-chrome
 * console; "renders in console chrome" does. A line matching several probes is one line.</p>
 */
export const PROBES = [
  ["provider", (line) => /SectionCardChromeProvider/.test(line)],
  ["hook", (line) => /useSectionChrome/.test(line)],
  ["comparison", (line, isComment) => !isComment
    && /\bchrome\s*[!=]==?\s*["'](?:bento|console)["']|["'](?:bento|console)["']\s*[!=]==?\s*chrome\b/.test(line)],
  ["prop", (line, isComment) => !isComment && /\bchrome=(?:["'{])/.test(line)],
  ["comment", (line, isComment) => isComment
    && /\bchromes?\b/i.test(line)
    && /\b(?:console|bento|eras?|both|two|either|neither|adapter|provider)\b/i.test(line)],
]

/** Every file under the areas this reads, as paths relative to the package root. */
function files(root) {
  const out = []
  const walk = (dir) => {
    let entries
    try {
      entries = readdirSync(dir, { withFileTypes: true })
    } catch {
      return // an area this package does not have is not a finding
    }
    for (const entry of entries) {
      if (SKIP.has(entry.name)) continue
      const path = join(dir, entry.name)
      if (entry.isDirectory()) walk(path)
      else if (EXTENSIONS.test(entry.name)) out.push(path)
    }
  }
  for (const area of AREAS) walk(join(root, area))
  return out.map((path) => relative(root, path).replace(/\\/g, "/")).sort()
}

/**
 * Which lines of a source are comment lines. Not a parser — the same bargain
 * `support/source-text.mjs` makes: block comments, JSX comments and `//` lines, tracked per line so
 * a probe can ask whether the line it matched is prose.
 */
function commentLines(source) {
  let open = false
  return source.split("\n").map((line) => {
    const text = line.trim()
    if (open) {
      if (text.includes("*/")) open = false
      return true
    }
    if (text.startsWith("//") || text.startsWith("*")) return true
    if (text.startsWith("/*") || text.startsWith("{/*")) {
      open = !text.includes("*/")
      return true
    }
    return false
  })
}

/**
 * Lines naming the vocabulary, per file, keyed by path; files with none are absent.
 *
 * @param root the package root to walk
 * @param exclude package-relative paths not to read — the census's own files, whose explanation
 *   of the probes is the vocabulary by construction
 */
export function census(root, exclude = []) {
  const found = {}
  const byProbe = Object.fromEntries(PROBES.map(([name]) => [name, 0]))
  /** Which probes fired in each file — SH962 asks whether a file's only hits are prose. */
  const probesByFile = {}
  for (const path of files(root)) {
    if (exclude.includes(path)) continue
    const source = readFileSync(join(root, path), "utf8")
    const comments = commentLines(source)
    let lines = 0
    const fired = new Set()
    source.split("\n").forEach((line, at) => {
      const hits = PROBES.filter(([, probe]) => probe(line, comments[at])).map(([name]) => name)
      if (!hits.length) return
      lines++
      for (const name of hits) {
        byProbe[name]++
        fired.add(name)
      }
    })
    if (lines) {
      found[path] = lines
      probesByFile[path] = [...fired].sort()
    }
  }
  return { found, byProbe, probesByFile }
}
