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
 * SH1167 — the console knows a type is not a page before asking the server to render it.
 *
 * `console-check.mjs` failed eleven post-edit routes on `400 POST /api/v2/agent/render`: the live
 * preview asked to render a menu, a section, a theme, and learned from the refusal
 * (`render-no-layout-binding`) — which the browser logs as an error — that the type is no page.
 * `boundPostTypes` mirrors `ShPageRenderService.bindingsOf`, so these cases are the server's.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const { boundPostTypes, isRenderable } = await import("../src/lib/layout-bindings.ts")

test("SH1167: a site's bindings are its postTypeLayout's string values, as the server reads them", () => {
  assert.deepEqual([...boundPostTypes('{"Page": "Default", "Article": "Blog"}')], ["Page", "Article"])
  // Each of these binds nothing on the server, so each binds nothing here.
  for (const raw of [undefined, null, "", "   ", "not json", "[]", "null", '"Page"']) {
    assert.equal(boundPostTypes(raw).size, 0, `SH1167: ${JSON.stringify(raw)} should bind nothing`)
  }
  // The retired array form is skipped, not half-read.
  assert.deepEqual([...boundPostTypes('{"Page": "Default", "Menu": ["a", "b"]}')], ["Page"])
})

test("SH1167: the preview asks only for a bound type, waits while unknown, and never hides on a failed read", () => {
  const layouts = '{"Page": "Default"}'
  assert.equal(isRenderable({ siteId: "s", failed: false, layouts, typeName: "Page" }), true)
  assert.equal(isRenderable({ siteId: "s", failed: false, layouts, typeName: "MenuItem" }), false)
  assert.equal(isRenderable({ siteId: "s", failed: false, layouts: undefined, typeName: "Page" }), undefined)
  assert.equal(isRenderable({ siteId: "s", failed: true, layouts: undefined, typeName: "MenuItem" }), true)
  assert.equal(isRenderable({ siteId: undefined, failed: false, layouts: undefined, typeName: "Page" }), true)
  // Before the post loads there is no type and no site: that is "not known yet", not "no site" —
  // measured, reading it as "no site" fired one render of a MenuItem before the answer came in.
  assert.equal(isRenderable({ siteId: undefined, failed: false, layouts: undefined, typeName: undefined }), undefined)
})

test("SH1167: the post editor hands the answer to the live preview", () => {
  const page = readFileSync(join(HERE, "..", "src", "app", "content", "post.edit.page.tsx"), "utf8")
  assert.match(page, /<PostLivePreview\b[^>]*\brenderable=\{renderable\}/,
    "SH1167: post.edit.page.tsx must pass renderable to <PostLivePreview>, or it asks for every type")
  const preview = readFileSync(join(HERE, "..", "src", "components", "post-live-preview.tsx"), "utf8")
  assert.match(preview, /if \(!asks\) return undefined/,
    "SH1167: the preview's mount render must not run for a type that is not a page")
  // Measured: `renderable = true` as a destructuring default made the caller's "not known yet"
  // (`undefined`) read as `true`, and every menu rendered once before its site had answered.
  assert.doesNotMatch(preview, /renderable\s*=\s*true/,
    "SH1167: renderable must not be defaulted — undefined means wait, and a default makes it true")
})
