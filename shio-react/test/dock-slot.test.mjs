/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1198 — the mascot, the console's one notice since SH1182, floated at the viewport's bottom-right
 * over page content: in the 2026-09-28 captures it covered the last delete in the trash, a delete in
 * the post-type table, a revert in the review queue and the editor's preview corner. The design system
 * now rests the dock at the rail's foot and, on a phone, in the header (VDS185), and the look census
 * went from twenty routes with a covered control to eight, none of them covered by the dock. That
 * holds only while the console hands the dock to the shell rather than placing it itself.
 */
// @scans code
import { strict as assert } from "node:assert"
import { readdirSync, readFileSync } from "node:fs"
import { dirname, join, relative } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { stripComments } from "./support/source-text.mjs"

const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src")

test("SH1198: the dock reaches the screen through the shell's slot, or inline, and nowhere else", () => {
  const mounts = []
  const walk = (dir) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const path = join(dir, entry.name)
      if (entry.isDirectory()) walk(path)
      else if (entry.name.endsWith(".tsx")) {
        const code = stripComments(readFileSync(path, "utf8"))
        for (const match of code.matchAll(/(dock=\{)?<(BentoStatusDock|VigletAssistant)\b([^>]*)>/g)) {
          mounts.push({ file: relative(SRC, path).replace(/\\/g, "/"), slot: Boolean(match[1]), inline: /\binline\b/.test(match[3]) })
        }
      }
    }
  }
  walk(join(SRC, "app"))
  assert.ok(mounts.some((mount) => mount.slot && mount.file === "app/system/bento.root.page.tsx"),
    "SH1198: the shell no longer receives the dock, so nothing reserves a place for it")
  assert.deepEqual(mounts.filter((mount) => !mount.slot && !mount.inline), [],
    "SH1198: a page mounts the floating dock itself, which is how it came to cover a page's controls")
})
