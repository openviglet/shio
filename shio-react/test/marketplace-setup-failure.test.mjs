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
 * SH1385 — a refused Marketplace plan says why inside the dialog.
 *
 * Quilhara's plan answered 400 on a clean instance and the curator saw "Install and apply" stay
 * disabled and nothing else: the reason went to a toast, if anywhere. Read from source, like the
 * other component guards here, because the claim is about where the refusal is drawn.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(join(HERE, "..", "src", "app", "admin", "marketplace", "marketplace.setup.tsx"), "utf8")

test("SH1385: a refused plan is kept and drawn as an alert in the dialog", () => {
  const caught = source.slice(source.indexOf(".catch((error) =>"), source.indexOf(".finally("))
  assert.match(caught, /setFailure\(reason\)/,
    "SH1385: the refusal's title and fix are kept in the dialog's state, not only toasted")
  assert.match(source, /\{failure && \(\s*<p role="alert"/,
    "SH1385: and drawn where the curator is looking, as an alert a screen reader announces")
})

test("SH1385: the reason clears when the inputs change, so it never describes another plan", () => {
  const change = source.slice(source.indexOf("const change = "), source.indexOf("const request = "))
  assert.match(change, /setFailure\(null\)/,
    "SH1385: a stale refusal beside new inputs would read as the answer to a plan nobody ran")
})
