/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"

/**
 * SH1317 — Review is the curator's main job (P7), and it was filed under Administration, beside
 * users, tokens and providers. It now leads the Content hub with the other things that wait.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..", "..")

test("SH1317: Review is a content surface, first in the Content hub's waiting group", () => {
  const surfaces = JSON.parse(readFileSync(join(ROOT, "shio-app", "src", "main", "resources", "console",
    "surfaces.json"), "utf8")).surfaces
  assert.equal(surfaces.find((surface) => surface.id === "review")?.section, "content",
    "SH1317: Review is back under Administration, where a curator does not look")
  const nav = readFileSync(join(ROOT, "shio-react", "src", "app", "system", "bento-nav.ts"), "utf8")
  assert.match(nav, /content: \[\s*(\/\/[^\n]*\n\s*)*\{ labelKey: "nav\.hub\.content\.waiting", ids: \["review", "notes", "scheduled"\] \}/,
    "SH1317: the Content hub no longer opens on what waits for the curator")
})
