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

/**
 * SH1166 — no form is reset during render.
 *
 * SH1150 moved the post-type editor's seeding out of an effect and into the render phase, which is
 * right for the page's own `useState` and wrong for `form.reset(...)`: react-hook-form notifies every
 * subscribed `Controller`, and each notification is a setState on *another* component while this one
 * renders. React reports it as "Cannot update a component (`Controller`) while rendering a different
 * component", and `console-check.mjs` failed three post-type routes on it. The user editor had the
 * same block and no route in the check reaches it, which is why this is a scan and not only a probe.
 *
 * The render phase, read by position: a function component's body is indented two spaces, so an
 * `if (` at exactly that indent runs during render, while one inside an effect or a callback sits
 * deeper. Its brace-matched block may not call a form's `reset`, `resetField` or `setValue`; seed
 * the form with `useForm({ values })`, which resets in react-hook-form's own effect.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")
const FORM_WRITE = /\b\w+\.(reset|resetField|setValue)\(/

function sources(dir) {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name)
    if (entry.isDirectory()) return sources(path)
    return entry.name.endsWith(".tsx") ? [path] : []
  })
}

/** The text of the block an `if` opens on `start`, by counting braces from its first `{`. */
function blockFrom(lines, start) {
  let depth = 0
  let opened = false
  const body = []
  for (let i = start; i < lines.length; i++) {
    for (const ch of lines[i]) {
      if (ch === "{") { depth++; opened = true }
      if (ch === "}") depth--
    }
    body.push(lines[i])
    if (opened && depth <= 0) break
  }
  return body.join("\n")
}

export function renderPhaseFormWrites(text) {
  const lines = text.split(/\r?\n/)
  const found = []
  lines.forEach((line, i) => {
    if (!/^ {2}if \(/.test(line)) return
    const block = blockFrom(lines, i)
    const hit = block.match(FORM_WRITE)
    if (hit) found.push({ line: i + 1, call: hit[0] })
  })
  return found
}

test("SH1166: no function component writes to a form during render", () => {
  const offenders = sources(SRC).flatMap((file) =>
    renderPhaseFormWrites(readFileSync(file, "utf8")).map(
      (hit) => `${relative(SRC, file).replaceAll("\\", "/")}:${hit.line} ${hit.call}`))
  assert.deepEqual(offenders, [],
    "SH1166: a form written during render updates every Controller mid-render; seed it with"
    + " useForm({ values }) instead:\n  " + offenders.join("\n  "))
})

test("SH1166: the scan finds a render-phase reset and ignores one inside an effect", () => {
  const planted = [
    "export default function Page() {",
    "  const form = useForm()",
    "  if (loaded.data && seeded !== id) {",
    "    setSeeded(id)",
    "    form.reset(loaded.data)",
    "  }",
    "  useEffect(() => {",
    "    if (other) {",
    "      form.reset(other)",
    "    }",
    "  }, [other])",
    "}",
  ].join("\n")
  assert.deepEqual(renderPhaseFormWrites(planted), [{ line: 3, call: "form.reset(" }])
})
