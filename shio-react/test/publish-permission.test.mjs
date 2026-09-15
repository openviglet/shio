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
 * SH995 — the console does not offer a Publish button it knows the server will refuse.
 *
 * Read from source, like the other console surface tests. What can go wrong here is a control that
 * stays enabled (the curator learns they may not publish by being told no, after the click), and a
 * refusal the console phrases itself — which is two answers to one question, only one of which is
 * enforced.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const read = (...path) => readFileSync(join(ROOT, ...path), "utf8")

test("SH995: the editor asks what it may do, and disables Publish with the server's sentence", () => {
  const page = read("src", "app", "content", "post.edit.page.tsx")

  assert.match(page, /aclService\s*\n?\s*\.allowed\(postId\)/,
    "SH995: the permission is asked, not assumed")
  assert.match(page, /disabled=\{isLoading \|\| publishHint !== null\}/)
  assert.match(page, /disabled=\{isLoading \|\| isPublishing \|\| publishHint !== null\}/,
    "SH995: taking a page down is the same permission as putting it up")
  assert.match(page, /title=\{publishHint \?\? undefined\}/,
    "SH995: the disabled control carries the reason, or it is a button that stopped working")
})

test("SH995: the reason shown is the server's own, carried rather than reworded", () => {
  const service = read("src", "services", "acl", "acl.service.ts")
  const model = read("src", "models", "acl", "acl.model.ts")

  assert.match(service, /\/v2\/object\/\$\{objectId\}\/allowed/)
  assert.match(model, /publishHint\?: string \| null/)
  assert.match(read("src", "app", "content", "post.edit.page.tsx"),
    /allowed\.publishHint/,
    "SH995: the sentence comes from the response; a console that wrote its own would be a second "
      + "authorisation story")
})

test("SH995: the ACL editor offers all three levels, in both locales", () => {
  assert.match(read("src", "components", "permissions.dialog.tsx"),
    /\["READ", "WRITE", "PUBLISH"\]/)
  for (const locale of ["en", "pt"]) {
    const acl = JSON.parse(read("src", "i18n", "locales", locale, "acl.json"))
    assert.ok(acl["acl.permission.PUBLISH"], `SH995: ${locale} has no name for the new level`)
    const content = JSON.parse(read("src", "i18n", "locales", locale, "content.json"))
    assert.match(content["content.publishNotAllowed"] ?? "", /\{\{reason\}\}/,
      `SH995: ${locale} must interpolate the server's reason rather than restate it`)
  }
})

test("SH996: Publish opens the server's plan before it writes", () => {
  const page = read("src", "app", "content", "post.edit.page.tsx")
  const dialog = read("src", "components", "publish-preview.dialog.tsx")
  const service = read("src", "services", "post-unified", "post-unified.service.ts")

  assert.match(page, /onClick=\{\(\) => setPublishPreviewOpen\(true\)\}/,
    "SH996: the button arms the preview, and the preview publishes")
  assert.match(dialog, /service\.publishPlan\(postId\), service\.publishRoutes\(postId\)/,
    "SH996: both halves come from the server — nothing here decides what publishing would do")
  assert.match(service, /publish\?dryRun=true/)
  assert.match(service, /include: "impact"/,
    "SH996: the routes are read where they are computed, not recomputed in the console")

  for (const locale of ["en", "pt"]) {
    const content = JSON.parse(read("src", "i18n", "locales", locale, "content.json"))
    for (const key of ["content.publishGoesLive", "content.publishMoves", "content.publishInbound"]) {
      assert.ok(content[key], `SH996: ${locale} is missing ${key}`)
    }
  }
})
