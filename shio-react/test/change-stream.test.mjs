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
 * SH1087 — the half of the stream's proof that needs no instance.
 *
 * `e2e/change-stream.check.mjs` is the proof by delivery: it opens the Activity page against a
 * live console and sees the line arrive. It needs a running backend and console, so it is not in
 * this gate. What this gate can hold is the contract that check depends on. The console listens
 * for an event NAME, and the server emits one. If the two spellings part, the stream still opens,
 * still connects, and delivers nothing the page listens for: SH1087's silent failure, exactly.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const read = (path) => readFileSync(join(ROOT, path), "utf8")

test("SH1087: the console listens for the event name the server emits", () => {
  const hook = stripComments(read("src/hooks/use-change-stream.ts"))
  const hub = stripComments(read(
    "../shio-app/src/main/java/com/viglet/shio/api/events/ShEventStreamHub.java"))

  const listened = /addEventListener\(\s*"([^"]+)"/.exec(hook)?.[1]
  const emitted = /String CHANGES\s*=\s*"([^"]+)"/.exec(hub)?.[1]

  assert.ok(listened, "SH1087: the hook listens for no named event, so the reader is not reading it")
  assert.ok(emitted, "SH1087: ShEventStreamHub.CHANGES was not found, so the reader is not reading it")
  assert.equal(listened, emitted,
    "SH1087: the page listens for one event name and the server sends another. The stream opens, "
    + "stays open and delivers nothing the page hears, which looks exactly like a quiet instance.")
})

test("SH1087: the delivery check is wired, and asserts both the arrival and the stillness", () => {
  const scripts = JSON.parse(read("package.json")).scripts
  assert.equal(scripts["console:stream"], "node e2e/change-stream.check.mjs",
    "SH1087: a check nobody can find by name is a check nobody runs")

  const check = stripComments(read("e2e/change-stream.check.mjs"))
  assert.match(check, /\/api\/v2\/agent\/batch/, "SH1087: it writes through the agent surface")
  assert.match(check, /waitFor\(\{ state: "visible"/, "SH1087: it waits for the line to appear")
  assert.match(check, /after !== before/,
    "SH1087: and it fails when the list moved, which is the promise the line exists for")
})
