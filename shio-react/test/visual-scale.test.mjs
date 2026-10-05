/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1199 — the console on the design system's closed scale.
 *
 * The look census (SH1191) measured fifteen control heights, ten radii and three monospace stacks
 * in this console. Design system 2026.3.17 closed the scale: two control heights and a touch step
 * (VDS197, VDS200), three radii (VDS198), one mono (VDS199). A closed scale in the package is only
 * half of it, because a consumer can still pass `h-7` to a Button and override the token, which is
 * what eighty-four call sites here did. These hold the console's own source to the scale, so a
 * regression fails here in milliseconds instead of waiting for somebody to run the census.
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

function sources(ext) {
  const out = []
  const walk = (dir) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const path = join(dir, entry.name)
      if (entry.isDirectory()) walk(path)
      else if (entry.name.endsWith(ext)) out.push(path)
    }
  }
  walk(SRC)
  return out.map((path) => ({ name: relative(SRC, path).replace(/\\/g, "/"), text: readFileSync(path, "utf8") }))
}

const TSX = sources(".tsx").map((file) => ({ ...file, code: stripComments(file.text, file.name) }))

/** The controls whose height the package owns, through `size` or a control token. */
const CONTROLS = ["Button", "GradientButton", "Input", "SelectTrigger", "Toggle", "ToggleGroupItem"]

/** A fixed height or square size: `h-7`, `size-8`, `min-h-10`, `h-[30px]`. A token (`h-(--vg-…)`) is not one. */
const FIXED_HEIGHT = /(?<=^|[\s"'`{(])(?:[a-z0-9-]+:)*(?:min-)?(?:h|size)-(?:\d+(?:\.\d+)?|\[[^\]]+\])(?=[\s"'`})]|$)/g

test("SH1199: no control in the console sets its own height", () => {
  const offenders = []
  for (const file of TSX) {
    for (const tag of CONTROLS) {
      for (const element of findElements(file.code, tag)) {
        // `[&_svg]:size-4` sizes the icon inside, not the control.
        const fixed = (element.attrs.match(FIXED_HEIGHT) ?? []).filter((cls) => !cls.includes("[&"))
        // No line number: the scan reads comment-stripped source, whose lines do not match the file's.
        if (fixed.length) offenders.push(`${file.name} <${tag}> ${fixed.join(" ")}`)
      }
    }
  }
  assert.deepEqual(offenders, [],
    "SH1199: these controls set a fixed height, which overrides the design system's control tokens "
      + "(VDS197) — and the touch step a coarse pointer raises them to. Pass `size` instead: `sm` or "
      + "`icon-sm` for dense (32 px), the default for form (36 px). An Input has no size prop, so a "
      + "dense one takes `h-(--vg-control-dense)`.")
})

test("SH1199: every radius in the console is one the scale names", () => {
  // Bare `rounded` is a fixed 4 px the theme never sees, and an arbitrary `rounded-[…]` is a radius
  // the scale does not hold (VDS198). The named keys all land on control, panel or full.
  const BARE = /(?<=^|[\s"'`{(])(?:[a-z0-9-]+:)*rounded(?:-[trblse]{1,2})?(?:-\[[^\]]+\])?(?=[\s"'`})]|$)/g
  const offenders = []
  for (const file of TSX) {
    for (const match of file.code.matchAll(BARE)) offenders.push(`${file.name}: ${match[0]}`)
    if (/borderRadius\s*:/.test(file.code)) offenders.push(`${file.name}: an inline borderRadius`)
  }
  for (const file of sources(".css")) {
    const css = stripComments(file.text, file.name)
    if (/border-radius\s*:\s*\d/.test(css)) offenders.push(`${file.name}: a literal border-radius`)
  }
  assert.deepEqual(offenders, [],
    "SH1199: a radius outside the closed scale. Use rounded-sm or rounded-md for a control, "
      + "rounded-lg and up for a panel, rounded-full for a pill or a dot.")
})

test("SH1199: the console names no font family of its own", () => {
  // The logo is an SVG wordmark, and its face is the brand's rather than the console's.
  const EXEMPT = new Map([["components/logo/sh-logo.tsx", "the wordmark's own face"]])
  const offenders = []
  const scripts = [...TSX.map(({ name, code }) => ({ name, text: code })),
    ...sources(".ts").map(({ name, text }) => ({ name, text: stripComments(text, name) }))]
  const sheets = sources(".css").map(({ name, text }) => ({ name, text: stripComments(text, name) }))
  for (const { name, text } of [...scripts, ...sheets]) {
    if (EXEMPT.has(name)) continue
    // `(?<![-\w])` so GraphiQL's `--font-family` variable, which index.css points at a token, is not a family.
    if (/(?<![-\w])font-family\s*:\s*["'A-Za-z]/.test(text) || /fontFamily\s*:\s*["'`]/.test(text) || /\bfont-\[/.test(text)) {
      offenders.push(name)
    }
  }
  assert.deepEqual(offenders, [],
    "SH1199: these name a font family, so the console draws a face the design system does not "
      + "(VDS199). Read --font-sans or --vg-font-mono instead.")
})

test("SH1199: GraphiQL's chrome and editors draw the console's families", () => {
  const css = readFileSync(join(SRC, "index.css"), "utf8")
  assert.match(css, /--font-family:\s*var\(--font-sans\)/, "SH1199: GraphiQL's sans is its own Roboto again")
  assert.match(css, /--font-family-mono:\s*var\(--font-mono\)/, "SH1199: GraphiQL's mono is its own Fira Code again")
  const page = stripComments(readFileSync(join(SRC, "app", "admin", "graphql", "graphql.page.tsx"), "utf8"))
  assert.match(page, /\buseConsoleMonoInGraphiQL\(\)/,
    "SH1199: the explorer no longer calls useConsoleMonoInGraphiQL, so its Monaco editors draw Fira Code")
  const font = stripComments(readFileSync(join(SRC, "lib", "graphiql-font.ts"), "utf8"))
  assert.match(font, /--vg-font-mono/, "SH1199: graphiql-font no longer reads the design system's mono token")
  assert.match(font, /onDidCreateEditor/, "SH1199: graphiql-font misses the editors GraphiQL creates after it loads")
})

test("SH1199: the console's theme maps every radius and mono key the way the installed preset does", () => {
  // The console compiles its own utilities, so without these keys `rounded-xl` is Tailwind's 12 px
  // and outranks the package's panel radius. The block is a copy of the preset's, and a copy is
  // only safe while something compares it: this reads both and fails on any key that differs.
  const require_ = createRequire(join(ROOT, "noop.js"))
  const root = dirname(dirname(require_.resolve("@viglet/viglet-design-system/exports.json")))
  const keys = (css) => new Map([...stripComments(css).matchAll(/(--(?:radius|font-mono|text-mono)[\w-]*):\s*([^;]+);/g)]
    .map(([, key, value]) => [key, value.trim()]))
  const preset = keys(readFileSync(join(root, "dist", "preset.css"), "utf8"))
  const console_ = keys(readFileSync(join(SRC, "index.css"), "utf8"))
  const wanted = [...preset].filter(([key]) => key !== "--radius" && !key.startsWith("--radius-full"))
  assert.ok(wanted.length >= 9, `SH1199: read only ${wanted.length} radius and mono keys from the preset`)
  const differ = wanted.filter(([key, value]) => console_.get(key) !== value)
    .map(([key, value]) => `${key}: preset ${value}, console ${console_.get(key) ?? "absent"}`)
  assert.deepEqual(differ, [],
    "SH1199: index.css's @theme block no longer matches the design system's preset, so a radius or "
      + "mono the console writes lands somewhere the package's own components do not.")
})

test("SH1199: the installed design system declares the tokens these checks assume", () => {
  // The proof the other four read something real: if the package stopped declaring the scale, a
  // console with no fixed heights would still be one with no scale.
  const require_ = createRequire(join(ROOT, "noop.js"))
  const root = dirname(dirname(require_.resolve("@viglet/viglet-design-system/exports.json")))
  const preset = readFileSync(join(root, "dist", "preset.css"), "utf8")
  for (const token of ["--vg-control-dense", "--vg-control-form", "--vg-control-touch",
    "--vg-radius-control", "--vg-radius-panel", "--vg-font-mono"]) {
    assert.ok(preset.includes(`${token}:`), `SH1199: the installed design system no longer declares ${token}`)
  }
})
