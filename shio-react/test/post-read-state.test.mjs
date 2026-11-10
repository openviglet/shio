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
import { dirname, join, relative } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { stripComments } from "./support/source-text.mjs"

/**
 * SH1390 — a read of one post names the state it wants.
 *
 * `GET /v2/post-unified/{id}` with no `state` means PUBLISHED on the server. The Visual Editor
 * read a selected post's type that way, so a post an agent wrote and nobody published answered 404
 * with a teaching error meant for an agent, and the query cache toasted it to the curator; the
 * content picker did the same to find a post's folder and silently started at the site list. The
 * one place allowed to spell that URL is the service's `get`, which always sends a state; a caller
 * that wants "whichever row the post has" calls `current`.
 */
const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src")
const SERVICE = "services/post-unified/post-unified.service.ts"

function sources(dir) {
  const out = []
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) out.push(...sources(full))
    else if (/\.(ts|tsx)$/.test(entry)) out.push(full)
  }
  return out
}

/** Every `/v2/post-unified/${…}` URL that ends there: a read of the post itself, not a sub-resource. */
const SINGLE_POST = /`\/v2\/post-unified\/\$\{[^}]+\}`/g

test("SH1390: only the post service reads one post by id, and it sends the state", () => {
  const callers = []
  let scanned = 0
  for (const file of sources(SRC)) {
    const code = stripComments(readFileSync(file, "utf8"), file)
    const name = relative(SRC, file).replace(/\\/g, "/")
    for (const match of code.matchAll(SINGLE_POST)) {
      scanned++
      const call = code.slice(match.index, match.index + 200)
      if (/axios\.get|axios\s*\.get/.test(code.slice(Math.max(0, match.index - 60), match.index))) {
        callers.push(`${name}${/params:\s*\{\s*state\b/.test(call) ? "" : " (no state)"}`)
      }
    }
  }
  assert.ok(scanned > 0, "SH1390: the scan found no single-post URL at all — it stopped guarding anything")
  assert.deepEqual(callers, [SERVICE],
    "SH1390: a read of one post outside ShPostUnifiedService.get, or one with no state, which means "
      + "PUBLISHED and 404s on a draft nobody published; call get(id, state) or current(id)")
})

test("SH1390: the editor's type read and the picker's folder read take the row the post has", () => {
  const read = (path) => stripComments(readFileSync(join(SRC, path), "utf8"))
  const service = read(SERVICE)
  assert.match(service, /async current\([\s\S]*?get\(id, "DRAFT", read\)[\s\S]*?isCancelled\(error\)\) throw error[\s\S]*?get\(id, "PUBLISHED", read\)/,
    "SH1390: current() no longer reads the draft first and falls back to the live row, cancellation aside")
  const editor = read("services/visual-editor/visual-editor.service.ts")
  assert.match(editor, /async postTypeOf\([^)]*\)[^{]*\{[^}]*this\.posts\.current\(postId, read\)/,
    "SH1390: the Visual Editor reads a selected post's type from a state a draft may not have")
  assert.match(read("components/widgets/content-browser-dialog.tsx"), /postService\.current\(nearPostId, \{ signal \}\)/,
    "SH1390: the content picker reads the edited post's folder from a state a draft may not have")
})
