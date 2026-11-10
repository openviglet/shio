// @scans code
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
 * SH1415 — the post editor reads only the rows a post has.
 *
 * It asked for the DRAFT and the PUBLISHED row blind, so a draft nobody had published (a visitor's
 * enquiry) answered the live read with a 404, and every such post opened with an error in the
 * browser console. The editor now asks the post's states first and reads a row only when the post
 * has it.
 */
const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src")
const page = readFileSync(join(SRC, "app", "content", "post.edit.page.tsx"), "utf8")
const service = readFileSync(join(SRC, "services", "post-unified", "post-unified.service.ts"), "utf8")

test("SH1415: the editor asks the post's states before it reads a row", () => {
  const load = page.slice(page.indexOf("queryKey: addressKey(`id:${postId}`)"), page.indexOf("const allowed = useQuery"))
  const asked = load.indexOf("unifiedService.states(postId")
  assert.ok(asked > 0, "SH1415: the editor no longer asks which rows the post has")
  for (const state of ["DRAFT", "PUBLISHED"]) {
    const read = load.indexOf(`unifiedService.get(postId, "${state}"`)
    assert.ok(read > asked, `SH1415: the ${state} read happens before the states are known`)
    assert.match(load, new RegExp(`states\\.includes\\("${state}"\\) \\? unifiedService\\.get\\(postId, "${state}"`),
      `SH1415: the ${state} row is read without checking the post has one, which is the 404 this fixed`)
  }
})

test("SH1415: the service reads the states from the endpoint that answers them", () => {
  assert.match(service, /async states\(id: string[^)]*\): Promise<ShPostState\[\]>/u)
  assert.match(service, /`\/v2\/post-unified\/\$\{id\}\/states`/u)
})
