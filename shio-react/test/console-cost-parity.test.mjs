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
import { measure } from "../scripts/console-size.mjs"

/**
 * SH1112 — the build gate's reader and the instance's reader agree, through one committed answer.
 *
 * `shio-app/src/test/resources/console-cost/` is a small built console. This holds the script to
 * `expected.json` beside it, and `ShConsoleCostTest` holds the server to the same file, so a change
 * to either reading fails one of them instead of drifting quietly away from the other.
 */
const FIXTURE = join(dirname(fileURLToPath(import.meta.url)), "..", "..", "shio-app", "src", "test", "resources",
  "console-cost")

test("SH1112: the script measures the shared fixture as expected.json records", async () => {
  const expected = JSON.parse(readFileSync(join(FIXTURE, "expected.json"), "utf8"))
  const measured = await measure(FIXTURE)
  assert.equal(measured.raw, expected.raw)
  assert.equal(measured.gzip, expected.gzip)
  assert.deepEqual(measured.assets, expected.assets,
    "SH1112: the same assets and sizes the instance reports — change a reader, regenerate the fixture's answer")
})
