/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { readdirSync, readFileSync, statSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { stripComments } from "./support/source-text.mjs"

/**
 * SH1097 — the shortcuts sheet lists the keys this shell binds, because both read one list.
 *
 * `BentoShortcutsDialog` renders `BENTO_SHELL_SHORTCUTS`, and `useBentoShellShortcuts` binds that
 * same set (VDS149). The sheet is only true while the shell binds through the hook: a keydown
 * handler of Shio's own is a second keymap, which is exactly how the sheet came to advertise a `?`
 * nothing bound and to omit the `/` that was bound.
 */
const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src")
const read = (...path) => stripComments(readFileSync(join(SRC, ...path), "utf8"))

function sources(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name)
    if (statSync(path).isDirectory()) return sources(path)
    return /\.(ts|tsx)$/.test(name) ? [path] : []
  })
}

test("SH1097: the shell binds its keys through the hook the shortcuts sheet lists from", () => {
  const root = read("app", "system", "bento.root.page.tsx")
  assert.match(root, /useBentoShellShortcuts\(\{/,
    "SH1097: the shell's keys are the package's one binding set, or the sheet lists a keymap that is not this one")
  assert.match(root, /onShortcuts:/,
    "SH1097: the sheet names `?` as the key that opens it, so the shell must bind it")
  assert.doesNotMatch(root, /addEventListener\(\s*["']keydown["']/,
    "SH1097: a keydown handler here is a second keymap the sheet cannot see")
})

test("SH1097: no other file binds the palette's keys by hand", () => {
  const offenders = sources(SRC).filter((path) => {
    const text = stripComments(readFileSync(path, "utf8"))
    return /setPaletteOpen/.test(text) && /addEventListener\(\s*["']keydown["']/.test(text)
  })
  assert.deepEqual(offenders, [],
    "SH1097: the palette opens from the keys BENTO_SHELL_SHORTCUTS declares, bound once by useBentoShellShortcuts")
})
