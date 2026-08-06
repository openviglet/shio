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
            `resolve to esm/vs/esm/vs/… and vite build fails in the worker bundle. Keep ` +
            `shio-react's monaco-editor within monaco-graphql's peer range (>= 0.20.0 < 0.53).`,
      )
    }
  })
}

test("SH242: monaco-editor is one copy, the one @graphiql/react and monaco-graphql were built against", () => {
  const versionOf = (resolver) => JSON.parse(readFileSync(resolver.resolve("monaco-editor/package.json"), "utf8")).version
  assert.equal(
    versionOf(require),
    versionOf(createRequire(graphiqlReactRequire.resolve("@graphiql/react/package.json"))),
    `shio-react and @graphiql/react resolve different copies of monaco-editor. The workers ` +
      `src/lib/monaco-workers.ts registers would then belong to a different Monaco instance than ` +
      `the editor GraphiQL runs, which no build error reports.`,
  )
})
