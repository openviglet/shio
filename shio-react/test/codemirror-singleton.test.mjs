/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { before, test } from "node:test"
import { fileURLToPath } from "node:url"
import { JSDOM } from "jsdom"

/**
 * CodeMirror 6's core packages are singletons by construction: `Facet`, `StateField` and the
 * extension types are matched by class identity, so a value built against one copy of
 * `@codemirror/state` is unrecognised by another. `EditorState.create` then throws
 * `Unrecognized extension value in extension set ([object Object])` and the Ace Editor widgets
 * (HTML / Javascript / CSS) render nothing on a post edit page.
 *
 * Nothing declares that conflict, which is why it shipped. `@uiw/react-codemirror` resolved
 * state 6.7.1 / view 6.43.6 while `@codemirror/lang-html` and `lang-javascript` pulled state 6.6.0
 * / view 6.43.0 through language 6.12.3 — three copies of state, two of view, every semver range
 * satisfied, install clean. It surfaced only as a dead editor in a chunk named `vendor-graphiql`
 * (the manual-chunk rule in vite.config.ts claims every id containing "codemirror"), so it read as
 * a GraphiQL fault for as long as it did. `pnpm-workspace.yaml` > `overrides` pins one version of
 * each.
 *
 * This asserts the *mechanism*, not the version numbers: it builds the widget's own extensions and
 * feeds them to the same `EditorState` the widget's editor runs, so it fails for any future
 * regression that reintroduces a second copy — a transitive bump, a new lang package, a `pnpm up`
 * that drops the pin. On the tree before the fix all five cases below throw the user's exact
 * message; a version-comparison check would have been a restatement of the pin instead.
 *
 * `EditorState` is imported from `@uiw/react-codemirror` deliberately — reaching for
 * `@codemirror/state` directly would resolve *this file's* copy rather than the editor's, and would
 * pass while the console stayed broken.
 */
const HERE = dirname(fileURLToPath(import.meta.url))

let EditorState, html, javascript, githubDark, githubLight

before(async () => {
  // @uiw/react-codemirror reaches for the DOM at import time.
  const dom = new JSDOM("<!doctype html><html><body></body></html>")
  globalThis.window = dom.window
  globalThis.document = dom.window.document
  Object.defineProperty(globalThis, "navigator", { value: dom.window.navigator, configurable: true })

  ;({ EditorState } = await import("@uiw/react-codemirror"))
  ;({ html } = await import("@codemirror/lang-html"))
  ;({ javascript } = await import("@codemirror/lang-javascript"))
  ;({ githubDark, githubLight } = await import("@uiw/codemirror-theme-github"))
})

/** Every extension the Ace Editor widget hands to CodeMirror, alone and as the real set. */
function widgetExtensions() {
  return [
    ["html()", () => html()],
    ["javascript()", () => javascript()],
    ["githubDark", () => githubDark],
    ["githubLight", () => githubLight],
    ["the widget's own set: [html(), githubDark]", () => [html(), githubDark]],
  ]
}

for (const [label, build] of widgetExtensions()) {
  test(`EditorState accepts ${label}`, () => {
    assert.doesNotThrow(
      () => EditorState.create({ doc: "<p>x</p>", extensions: [build()] }),
      (error) =>
        error &&
        `A CodeMirror extension was rejected by EditorState: ${error.message}\n` +
          `That means more than one copy of @codemirror/state (or /view, or /language) is ` +
          `installed — extensions are matched by class identity. Check ` +
          `'overrides' in pnpm-workspace.yaml still pins one version of each.`,
    )
  })
}

test("the CodeMirror singletons are pinned, so a transitive bump cannot re-split them", () => {
  const workspace = readFileSync(join(HERE, "..", "..", "pnpm-workspace.yaml"), "utf8")
  for (const name of ["@codemirror/state", "@codemirror/view", "@codemirror/language"]) {
    assert.match(
      workspace,
      new RegExp(`^\\s*'?${name.replace("/", "\\/")}'?:\\s*'?\\d`, "m"),
      `${name} has no 'overrides' entry in pnpm-workspace.yaml. The behavioural checks above ` +
        `pass on today's tree by luck then; the pin is what keeps them passing.`,
    )
  }
})
