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

  // SH1158 — the permission is its own query now, and it takes the signal `useQuery` hands it: a
  // curator who leaves the form should not have the console still asking what they may do there.
  assert.match(page, /queryFn: \(\{ signal \}\) => aclService\.allowed\(postId, \{ signal \}\)/,
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
    /allowed\.data\.publishHint/,
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

test("SH998: the override appears in the refusal, and never before it", () => {
  const dialog = read("src", "components", "publish-preview.dialog.tsx")
  const page = read("src", "app", "content", "post.edit.page.tsx")
  const service = read("src", "services", "post-unified", "post-unified.service.ts")

  assert.match(dialog, /\{gateRefusal && \(/,
    "SH998: a reason box on every publish trains a curator to fill one in, which is how a waiver "
      + "stops meaning anything")
  assert.match(dialog, /disabled=\{loading \|\| \(!!gateRefusal && !override\.trim\(\)\)\}/,
    "SH998: the record is the whole of the override, so a blank one cannot be sent")
  // SH1053 — `publishing` moved to `loading`, which keeps the pressed button focused and busy.
  assert.match(dialog, /loading=\{publishing\}/u,
    "SH1053: the publish button's own work is its `loading`, not a `disabled` that drops focus")
  assert.match(page, /problem\.type\?\.endsWith\("publish-gate-failed"\)/,
    "SH998: branched on the problem's type, never on its sentence — the sentence is for the "
      + "curator and is meant to be improvable")
  // SH1064 gave this call a second optional parameter, so the ternary became a built object.
  // What SH998 is about is unchanged and is what is asserted: an empty override is not sent.
  assert.match(service, /if \(override\) params\.override = override/,
    "SH998: absent means no override; empty means an override with nothing written down, which "
      + "the server refuses on purpose")
  assert.match(service, /params: Object\.keys\(params\)\.length \? params : undefined/,
    "SH998: and a call with neither sends no params at all, rather than an empty object the "
      + "server would have to read as a blank override")

  for (const locale of ["en", "pt"]) {
    const content = JSON.parse(read("src", "i18n", "locales", locale, "content.json"))
    for (const key of ["content.publishGateStopped", "content.publishOverrideLabel",
      "content.publishOverrideHint", "content.publishOverrideConfirm"]) {
      assert.ok(content[key], `SH998: ${locale} is missing ${key}`)
    }
    assert.match(content["content.publishOverrideHint"], /hist|read/i,
      `SH998: ${locale} must say who will read the sentence — a waiver written in private is the `
        + "one thing this is not")
  }
})

test("SH996: Publish opens the server's plan before it writes", () => {
  const page = read("src", "app", "content", "post.edit.page.tsx")
  const dialog = read("src", "components", "publish-preview.dialog.tsx")
  const service = read("src", "services", "post-unified", "post-unified.service.ts")

  assert.match(page, /onClick=\{\(\) => setPublishPreviewOpen\(true\)\}/,
    "SH996: the button arms the preview, and the preview publishes")
  // SH1154 moved these two into a `queryFn` and gave each the signal it hands them, so the shape is
  // `service.publishPlan(postId, { signal })`. The claim is untouched: both halves come from the
  // server, and this file computes neither.
  assert.match(dialog, /service\.publishPlan\(postId, \{ signal \}\)/,
    "SH996: the plan comes from the server — nothing here decides what publishing would do")
  assert.match(dialog, /service\.publishRoutes\(postId, \{ signal \}\)/,
    "SH996: and so does which URLs it would move")
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
