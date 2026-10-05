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
import { stripComments } from "./support/source-text.mjs"

/**
 * SH1214 — home's Open notes tile leads to the notes it counts.
 *
 * The tile counted open notes and linked to the content browser, which lists no notes, so the click
 * landed on a screen that could not say what was owed. Read from source, like the other surface
 * tests: what goes wrong is an absence — a tile pointing somewhere generic, a page wired into
 * neither the router nor the nav — and a second answer to "what is open" (a page reading something
 * other than the endpoint the count and an agent both read).
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const read = (...path) => readFileSync(join(ROOT, ...path), "utf8")

test("SH1214: home's Open notes tile links to the open-notes list, not the content browser", () => {
  const home = stripComments(read("src", "app", "system", "home.page.tsx"))
  const tile = /label:\s*t\("home\.attentionNotes"\)[\s\S]*?url:\s*([^,\n]+)/.exec(home)
  assert.ok(tile, "SH1214: home no longer draws an Open notes tile with a url")
  assert.equal(tile[1].trim(), "ROUTES.NOTES",
    "SH1214: the Open notes tile leads somewhere other than the notes it counts")
})

test("SH1214: the Open notes page is declared, decorated and routed", () => {
  const surfaces = JSON.parse(readFileSync(
    join(ROOT, "..", "shio-app", "src", "main", "resources", "console", "surfaces.json"), "utf8"))
  const declared = surfaces.surfaces.find((surface) => surface.id === "notes")
  assert.ok(declared, "SH1214: console/surfaces.json declares no `notes` surface")
  // SH1215 — the P1 leg is the shio://notes resource, whose HTTP twin is the GET /note/open this
  // page reads. ShConsoleSurfaceP1Test resolves the target against the running server.
  assert.deepEqual(declared.agent, { kind: "resource", target: "shio://notes" },
    "SH1215: the notes surface no longer names shio://notes as its agent equivalent")

  assert.match(read("src", "app", "system", "bento-nav.ts"), /"notes":\s*\{[^}]*route:\s*ROUTES\.NOTES/)
  assert.match(read("src", "App.tsx"), /path="content\/notes"/)
})

test("SH1214: the page reads the endpoint the count and the agent read", () => {
  assert.match(read("src", "services", "note", "note.service.ts"), /"\/v2\/note\/open"/)
  assert.match(stripComments(read("src", "app", "content", "notes.page.tsx")), /service\.open\(/)
})
