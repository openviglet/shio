/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1208 — no console element passes a prop the design system ignores.
 *
 * VDS182 turned `tone` into a no-op on five bento components: an area has no colour of its own, and
 * a list's one colour is a record's state. The console went on passing it at dozens of call sites,
 * and nothing failed, because an ignored prop still type-checks — the package keeps it, marked
 * `@deprecated Ignored since VDS182`, so no consumer breaks on the release. That is right for the
 * package and wrong for a reader here, who is told a section is amber when nothing renders amber.
 *
 * So this reads the installed package's own declarations rather than a list: every prop whose
 * comment says `@deprecated Ignored since`, on the component whose props interface carries it. The
 * next release that retires a prop that way fails here by name, without anybody grepping for it.
 */
import { strict as assert } from "node:assert"
import { readdirSync, readFileSync } from "node:fs"
import { createRequire } from "node:module"
import { dirname, join, relative } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { findElements } from "./support/jsx-elements.mjs"
import { stripComments } from "./support/source-text.mjs"

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const SRC = join(ROOT, "src")
const require_ = createRequire(join(ROOT, "noop.js"))
const DIST = join(dirname(dirname(require_.resolve("@viglet/viglet-design-system/exports.json"))), "dist")

function files(dir, ext) {
  const out = []
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name)
    if (entry.isDirectory()) out.push(...files(path, ext))
    else if (entry.name.endsWith(ext)) out.push(path)
  }
  return out
}

/** `component → [prop, …]` for every prop the package declares as ignored. */
function ignoredProps() {
  const ignored = new Map()
  for (const path of files(DIST, ".d.ts")) {
    let component = null
    let marked = false
    for (const line of readFileSync(path, "utf8").split("\n")) {
      const iface = line.match(/^export (?:interface|type) (\w+)Props\b/)
      if (iface) { component = iface[1]; marked = false; continue }
      if (/@deprecated Ignored since/.test(line)) { marked = true; continue }
      const prop = line.match(/^\s+(\w+)\??:/)
      if (prop && marked && component) {
        ignored.set(component, [...(ignored.get(component) ?? []), prop[1]])
        marked = false
      }
    }
  }
  return ignored
}

test("SH1208: the reader finds the props VDS182 retired, so its silence means something", () => {
  // The proof the next test reads something: if the declarations moved or the phrase changed, an
  // empty map would pass every console as clean.
  const ignored = ignoredProps()
  for (const component of ["BentoEmptyState", "BentoEntityTile", "BentoFormSection", "BentoListPage", "BentoTileGrid"]) {
    assert.ok(ignored.get(component)?.includes("tone"),
      `SH1208: the installed package no longer marks ${component}'s tone as ignored, or this reader lost it`)
  }
})

test("SH1208: no console element passes a prop the design system ignores", () => {
  const ignored = ignoredProps()
  const offenders = []
  for (const path of files(SRC, ".tsx")) {
    const name = relative(SRC, path).replace(/\\/g, "/")
    const code = stripComments(readFileSync(path, "utf8"), name)
    for (const [component, props] of ignored) {
      for (const element of findElements(code, component)) {
        for (const prop of props) {
          if (new RegExp(`(?:^|\\s)${prop}=`).test(element.attrs)) offenders.push(`${name} <${component} ${prop}>`)
        }
      }
    }
  }
  assert.deepEqual(offenders, [],
    "SH1208: these pass a prop the installed design system declares as ignored, so the page says "
      + "something that does not render. Delete the prop, and any field that existed only to feed it.")
})
