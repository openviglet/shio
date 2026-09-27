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

/**
 * SH1168 — the user editor's inputs are strings from the first render to the last.
 *
 * Probed live: "A component is changing an uncontrolled input to be controlled". The form had no
 * default values, so each input rendered `undefined` until the user arrived, and `password` — which
 * the server never returns — stayed `undefined` after it. A sixth field added without a default
 * would bring the error back for that input alone, so this holds the defaults to the fields rendered.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const PAGE = readFileSync(join(HERE, "..", "src", "app", "admin", "admin.user.page.tsx"), "utf8")

test("SH1168: every field the user form renders has an empty-string default", () => {
  const defaults = PAGE.match(/const EMPTY_USER: ShUser = \{([^}]*)\}/)
  assert.ok(defaults, "SH1168: admin.user.page.tsx no longer declares EMPTY_USER")
  const declared = new Map([...defaults[1].matchAll(/(\w+):\s*("[^"]*")/g)].map((m) => [m[1], m[2]]))
  const rendered = [...PAGE.matchAll(/<FormField[\s\S]*?name="(\w+)"/g)].map((m) => m[1])
  assert.ok(rendered.length >= 5, `SH1168: expected the user form's fields, found ${rendered.length}`)
  for (const name of rendered) {
    assert.equal(declared.get(name), '""',
      `SH1168: field "${name}" has no "" default, so it renders uncontrolled until the user loads`)
  }
})

test("SH1168: the form starts from the defaults and fills in what the server omits", () => {
  assert.match(PAGE, /useForm<ShUser>\(\{ defaultValues: EMPTY_USER, values,/)
  assert.match(PAGE, /password: loaded\.data\.password \?\? ""/)
})
