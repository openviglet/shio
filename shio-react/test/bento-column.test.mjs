/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { readdirSync, readFileSync } from "node:fs"
import { dirname, join, relative } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { stripComments } from "./support/source-text.mjs"

/**
 * SH946 — the reading column is the shell's, and no page inside it sets one.
 *
 * <p>Six pages set their own `mx-auto max-w-* px-6 pb-12` at three widths, a defect that exists
 * only between screens. `BentoShell` (VDS131) owns the column now, `columnFor` names the routes that
 * want `narrow`, and this holds both halves: the rule, and the absence of a width on any page the
 * shell renders.</p>
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")

async function columnFor() {
  // The module is TypeScript; its logic is a table of patterns, read here the same way.
  const source = readFileSync(join(SRC, "app", "system", "bento-column.ts"), "utf8")
  // SH1169 — two tables now, so each is read from its own declaration rather than the whole file.
  const table = (name) => {
    const body = source.match(new RegExp(`const ${name}: readonly RegExp\\[\\] = \\[([\\s\\S]*?)\\n\\]`))
    assert.ok(body, `the ${name} table was not found in bento-column.ts`)
    return [...body[1].matchAll(/^\s*(\/\^.*\$\/),?$/gm)].map(([, re]) => eval(re))
  }
  const narrow = table("NARROW")
  const full = table("FULL")
  assert.ok(narrow.length >= 3, "SH946: the narrow table was not read, so the rule below is vacuous")
  assert.ok(full.length >= 1, "SH1169: the full table was not read, so the rule below is vacuous")
  return (path) => {
    if (full.some((pattern) => pattern.test(path))) return "full"
    return narrow.some((pattern) => pattern.test(path)) ? "narrow" : "default"
  }
}

test("SH1169: the GraphQL explorer owns the viewport, and computes no height of its own", async () => {
  const column = await columnFor()
  assert.equal(column("/bento/graphql"), "full",
    "SH1169: the explorer ran 21px past the window sizing itself as 100vh minus a stale 4rem")
  const page = stripComments(readFileSync(join(SRC, "app", "admin", "graphql", "graphql.page.tsx"), "utf8"))
  assert.doesNotMatch(page, /100vh|100dvh|h-screen/,
    "SH1169: the explorer sums the shell's chrome again; the full column already gives it the height")
})

test("SH946: the single-question forms read narrow, and everything else the default column", async () => {
  const column = await columnFor()
  assert.equal(column("/bento/content/site/new"), "narrow")
  assert.equal(column("/bento/content/site/import"), "narrow")
  assert.equal(column("/bento/content/folder/abc-123/new-folder"), "narrow")
  assert.equal(column("/bento/home"), "default",
    "SH946: home set max-w-7xl by hand, which is the default column's 80rem")
  assert.equal(column("/bento/content/folder/abc-123"), "default")
})

test("SH947: the infrastructure editors that set max-w-2xl read narrow, and their lists do not", async () => {
  const column = await columnFor()
  for (const editor of ["tokens/t-1", "auth-providers/new", "exchange-providers/x", "webhooks/w", "tenants/acme"]) {
    assert.equal(column(`/bento/admin/${editor}`), "narrow", `SH947: /bento/admin/${editor}`)
  }
  assert.equal(column("/bento/admin/email-config"), "narrow")
  assert.equal(column("/bento/admin/tokens"), "default",
    "SH947: the list is a mosaic and wants the default column; only its editor is a form")
  assert.equal(column("/bento/admin/users/u-1"), "default")
})

test("SH946: the root composes the package's shell and hands it the column", () => {
  const root = stripComments(readFileSync(join(SRC, "app", "system", "bento.root.page.tsx"), "utf8"))
  assert.match(root, /<BentoShell\b/, "SH946: the root no longer composes BentoShell")
  assert.match(root, /column=\{columnFor\(/, "SH946: the shell is not told which column a route wants")
  assert.doesNotMatch(root, /bento-rail-gutter/,
    "SH946: the root reserves the rail's gutter by hand again, beside a shell that already does")
})

test("SH946: no page the shell renders sets its own reading column", () => {
  const offenders = []
  const walk = (dir) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const path = join(dir, entry.name)
      if (entry.isDirectory()) walk(path)
      else if (entry.name.endsWith(".page.tsx")) {
        const code = stripComments(readFileSync(path, "utf8"))
        if (/\bmx-auto\b[^"]*\bmax-w-(?:[3-7]xl|screen)/.test(code)) {
          offenders.push(relative(SRC, path).replace(/\\/g, "/"))
        }
      }
    }
  }
  walk(join(SRC, "app"))
  // The vocabulary harness is mounted outside the shell on purpose, so it keeps its own layout.
  assert.deepEqual(offenders.filter((path) => path !== "app/system/bento-vocabulary.page.tsx"), [],
    "SH946: a page sets its own width with mx-auto and max-w-*. The shell's main owns the column; "
      + "name a different one in bento-column.ts instead of repeating a class.")
})
