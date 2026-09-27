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
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { createElement } from "react"
import { renderToStaticMarkup } from "react-dom/server"
import { ThemeProvider } from "next-themes"

/**
 * SH1164 — the theme script the console renders is a data block, so React's dev build stops
 * reporting it as an error on every route.
 *
 * `console-check.mjs` against the Vite dev server was red on all twenty routes, each on
 * "Encountered a script tag while rendering React component": `next-themes` renders a blocking
 * script for a server render's first paint, and React's development build logs any executable
 * `<script>` it inserts on the client. This console is never server-rendered and the first paint
 * is the boot-loader's (SH918), so the script is inert either way; a non-JavaScript `type` says so.
 *
 * Two halves, because either alone passes while the page stays red: `App.tsx` must pass the prop,
 * and the provider must carry it onto the element. The second is rendered, not read, against
 * `next-themes` itself: the design system's wrapper types its props as `next-themes`' own and
 * spreads the rest into it, so the element is `next-themes`' to shape. The live console check is
 * what proves the two together.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const APP = readFileSync(join(HERE, "..", "src", "App.tsx"), "utf8")

/** The MIME types React treats as JavaScript, plus the empty type — each would still warn. */
const EXECUTABLE = /^(|module|text\/javascript|application\/javascript|application\/ecmascript|text\/ecmascript)$/i

function inertType() {
  const found = APP.match(/const INERT_THEME_SCRIPT = \{ type: "([^"]*)" \}/)
  assert.ok(found, "SH1164: App.tsx no longer declares INERT_THEME_SCRIPT with a literal type")
  return found[1]
}

test("SH1164: the console's ThemeProvider passes a non-JavaScript type to its script", () => {
  assert.match(APP, /<ThemeProvider\b[^>]*\bscriptProps=\{INERT_THEME_SCRIPT\}/,
    "SH1164: App.tsx's <ThemeProvider> must pass scriptProps={INERT_THEME_SCRIPT}, or next-themes"
    + " renders an executable script and React's dev build reports it on every route")
  const type = inertType()
  assert.doesNotMatch(type, EXECUTABLE, `SH1164: "${type}" is a type React reads as JavaScript`)
})

test("SH1164: next-themes carries scriptProps onto the script it renders", () => {
  const type = inertType()
  const html = renderToStaticMarkup(createElement(ThemeProvider,
    { attribute: "class", scriptProps: { type } }, createElement("p", null, "x")))
  const scripts = html.match(/<script\b[^>]*>/g) ?? []
  assert.equal(scripts.length, 1, `SH1164: expected the one theme script, got ${scripts.length}`)
  assert.ok(scripts[0].includes(`type="${type}"`),
    `SH1164: the rendered script lost its type: ${scripts[0]}`)
})
