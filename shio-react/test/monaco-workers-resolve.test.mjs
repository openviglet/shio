/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { readFileSync, readdirSync, statSync } from "node:fs"
import { createRequire } from "node:module"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"

/**
 * SH242. The GraphiQL tree reaches Monaco by *deep path* — `monaco-editor/esm/vs/…` — in
 * monaco-graphql's worker, in @graphiql/react's dist and in our own src/lib/monaco-workers.ts.
 * Those paths are a resolution contract, not an implementation detail, and monaco-editor 0.56.0
 * broke it: it added an `exports` map rooted at `esm/vs/` (`"./*": "./esm/vs/*.js"`), so every
 * `esm/vs/…` specifier now resolves to `esm/vs/esm/vs/…` and resolves to nothing. 0.52.2 has no
 * `exports` field at all, which is why the same specifiers worked for as long as they did.
 *
 * Nothing failed on that: a grouped dependabot bump moved monaco-editor 0.52.2 → 0.56.0, pnpm
 * resolved monaco-graphql against it although its peer range says `>= 0.20.0 < 0.53`, printed a
 * warning nobody reads, and left @graphiql/react's own 0.52.2 as a second copy in the store.
 * Typecheck was clean. `vite build` then died in the worker bundle — `Rolldown failed to resolve
 * import "monaco-editor/esm/vs/editor/editor.worker"` — with no line of ours in the trace.
 * `strictPeerDependencies: true` in pnpm-workspace.yaml now refuses that install; this file is the
 * other half, because that setting only fires when a manifest change makes pnpm resolve.
 *
 * This asserts the mechanism rather than the pin: it reads the specifiers actually imported by the
 * three sources above and resolves each one from shio-react, so it fails for any future tree where
 * a Monaco import stops resolving — another bump, a new graphiql version, a `pnpm up` that drops
 * the pin. On the 0.56.0 tree every `esm/vs/…` case below throws MODULE_NOT_FOUND. Resolution is
 * checked, not imported: these are browser/worker entry points and importing them under Node would
 * fail for reasons that have nothing to do with the bug.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const require = createRequire(join(HERE, "..", "package.json"))

/** Every `monaco-editor/…` or `monaco-graphql/…` specifier in a file, minus any `?worker` query. */
function monacoSpecifiers(source) {
  const found = new Set()
  for (const match of source.matchAll(/["'](monaco-(?:editor|graphql)\/[^"'?]+)/g)) found.add(match[1])
  return found
}

function filesUnder(dir) {
  const out = []
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) out.push(...filesUnder(full))
    else if (entry.endsWith(".js") || entry.endsWith(".mjs")) out.push(full)
  }
  return out
}

/**
 * @graphiql/react is a transitive dependency, so it is unreachable from shio-react under pnpm's
 * strict layout — reach it through graphiql, the way the bundler walks the graph.
 */
const graphiqlReactRequire = createRequire(require.resolve("graphiql/package.json"))

function packageDir(resolver, name) {
  return dirname(resolver.resolve(`${name}/package.json`))
}

/** The three sources that import Monaco by deep path, each read where the build reads it. */
function importSites() {
  return [
    ["src/lib/monaco-workers.ts", [join(HERE, "..", "src", "lib", "monaco-workers.ts")]],
    ["monaco-graphql", filesUnder(join(packageDir(require, "monaco-graphql"), "esm"))],
    ["@graphiql/react", filesUnder(join(packageDir(graphiqlReactRequire, "@graphiql/react"), "dist"))],
  ]
}

for (const [label, files] of importSites()) {
  const specifiers = new Set()
  for (const file of files) for (const s of monacoSpecifiers(readFileSync(file, "utf8"))) specifiers.add(s)

  test(`SH242: every Monaco specifier ${label} imports resolves (${specifiers.size})`, () => {
    assert.ok(specifiers.size > 0, `${label} imports no Monaco path — the scan found nothing to check, ` +
      `which means this test stopped guarding anything. Check the package layout moved.`)
    for (const specifier of [...specifiers].sort()) {
      assert.doesNotThrow(
        () => require.resolve(specifier),
        (error) =>
          error &&
          `${label} imports "${specifier}", which no longer resolves: ${error.code ?? error.message}.\n` +
            `monaco-editor >= 0.56 roots its 'exports' map at esm/vs/, so deep 'esm/vs/…' paths ` +
            `resolve to esm/vs/esm/vs/… and vite build fails in the worker bundle (spell them from ` +
            `the package root instead: 'monaco-editor/editor/…'). Keep shio-react's monaco-editor ` +
            `within the range @graphiql/react and monaco-graphql declare (SH1383).`,
      )
    }
  })
}

/** `[major, minor, patch]` of a plain `x.y.z` version. */
const parts = (version) => String(version).split(/[.-]/).slice(0, 3).map(Number)
const compare = (a, b) => {
  for (let i = 0; i < 3; i++) if (a[i] !== b[i]) return a[i] - b[i]
  return 0
}

/**
 * Whether `version` satisfies a range spelled the way the GraphiQL tree spells its Monaco ranges:
 * space-separated `>=`/`<`/`<=`/`>` comparators, or one exact version. Anything else throws, so a
 * new spelling fails here loudly rather than reading as satisfied.
 */
function satisfies(version, range) {
  const v = parts(version)
  return range.trim().split(/\s+/).every((comparator) => {
    const m = /^(>=|<=|>|<|=)?\s*(\d+\.\d+\.\d+)$/.exec(comparator)
    if (!m) throw new Error(`unrecognised range comparator "${comparator}" in "${range}" — extend satisfies()`)
    const c = compare(v, parts(m[2]))
    return { ">=": c >= 0, "<=": c <= 0, ">": c > 0, "<": c < 0, "=": c === 0, undefined: c === 0 }[m[1]]
  })
}

/** A package's own package.json, found by walking up from its entry: monaco-editor >= 0.56 does not export it. */
function manifestOf(resolver, name) {
  let dir = dirname(resolver.resolve(name))
  for (;;) {
    try {
      const manifest = JSON.parse(readFileSync(join(dir, "package.json"), "utf8"))
      if (manifest.name === name) return manifest
    } catch {
      // not this directory
    }
    const parent = dirname(dir)
    assert.notEqual(parent, dir, `no package.json named ${name} above its entry`)
    dir = parent
  }
}

test("SH1383/SH1384: the pinned Monaco is the one GraphiQL and monaco-graphql declare", () => {
  // SH1384 held GraphiQL at 0.x/5 because its 1.0 line imports Monaco paths 0.52.2 does not ship, and
  // a Dependabot group that took the major went red three ways at once with no line saying why.
  // SH1383 adopted that line; what has to stay true is the relation, not a version: the monaco-editor
  // shio-react pins sits inside the range @graphiql/react depends on and monaco-graphql peers on, so
  // the next GraphiQL major that moves Monaco fails on this one sentence and not in the worker bundle.
  const pinned = JSON.parse(readFileSync(join(HERE, "..", "package.json"), "utf8")).dependencies["monaco-editor"]
  assert.match(pinned, /^\d+\.\d+\.\d+$/, "SH1383: monaco-editor is pinned exactly, not ranged — the GraphiQL tree moves it.")
  const graphiqlReact = JSON.parse(readFileSync(graphiqlReactRequire.resolve("@graphiql/react/package.json"), "utf8"))
  const monacoGraphql = JSON.parse(readFileSync(require.resolve("monaco-graphql/package.json"), "utf8"))
  for (const [who, range] of [
    ["@graphiql/react's dependency", graphiqlReact.dependencies?.["monaco-editor"]],
    ["monaco-graphql's peer", monacoGraphql.peerDependencies?.["monaco-editor"]],
  ]) {
    assert.ok(range, `SH1383: ${who} on monaco-editor is gone — re-read how the GraphiQL tree reaches Monaco.`)
    assert.ok(
      satisfies(pinned, range),
      `SH1383: shio-react pins monaco-editor ${pinned}, outside ${who} range "${range}". Move the pin ` +
        `to what the GraphiQL tree was built against, then re-run SH242's resolution checks above.`,
    )
  }
})

test("SH242: monaco-editor is one copy, the one @graphiql/react and monaco-graphql were built against", () => {
  const versionOf = (resolver) => manifestOf(resolver, "monaco-editor").version
  assert.equal(
    versionOf(require),
    versionOf(createRequire(graphiqlReactRequire.resolve("@graphiql/react/package.json"))),
    `shio-react and @graphiql/react resolve different copies of monaco-editor. The workers ` +
      `src/lib/monaco-workers.ts registers would then belong to a different Monaco instance than ` +
      `the editor GraphiQL runs, which no build error reports.`,
  )
})
