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
 * SH1189 — a hero back link goes to the page's real parent, not to the nearest root constant.
 *
 * The rail's top-level pages pointed at `/bento/content` (the site list) or `/bento/admin` (a
 * redirect to users), so "Content" on the post-type list landed among sites. Editors one level
 * down pointed at those same roots instead of their own list. The fault is a literal in each page
 * and no render test visits all of them, so this reads each page's source for the route it passes.
 */
const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src", "app")
const read = (path) => stripComments(readFileSync(join(SRC, path), "utf8"))

/** Page → the back route its hero must carry. */
const EXPECTED = {
  // Rail surfaces: back to home.
  "content/post-type.list.page.tsx": "urlBase={ROUTES.HOME}",
  "content/static-file.list.page.tsx": "urlBase={ROUTES.HOME}",
  "content/trash.page.tsx": "urlBase={ROUTES.HOME}",
  "content/scheduled.page.tsx": "urlBase={ROUTES.HOME}",
  "content/forms.page.tsx": "urlBase={ROUTES.HOME}",
  "editor/editor-sites.page.tsx": "urlBase={ROUTES.HOME}",
  "admin/activity/activity.list.page.tsx": "urlBase={ROUTES.HOME}",
  "admin/review/review.list.page.tsx": "urlBase={ROUTES.HOME}",
  "admin/email-config/email-config.page.tsx": "urlBase={ROUTES.HOME}",
  "content/search.page.tsx": "searchBackTo(location.state, ROUTES.HOME)",
  // Editors: back to their own list.
  "content/post-type.new.page.tsx": "urlBase={ROUTES.POST_TYPE_ROOT}",
  "content/post-type.edit.page.tsx": "urlBase={ROUTES.POST_TYPE_ROOT}",
  "admin/admin.user.page.tsx": "urlBase={ROUTES.ADMIN_USERS}",
  "admin/api-token/api-token.page.tsx": "urlBase={ROUTES.ADMIN_TOKENS}",
  "admin/auth-provider/auth-provider.page.tsx": "urlBase={ROUTES.ADMIN_AUTH_PROVIDERS}",
  "admin/exchange-provider/exchange-provider.page.tsx": "urlBase={ROUTES.ADMIN_EXCHANGE_PROVIDERS}",
  "admin/site-webhook/site-webhook.page.tsx": "urlBase={ROUTES.ADMIN_WEBHOOKS}",
  "admin/tenant/tenant.page.tsx": "urlBase={ROUTES.ADMIN_TENANTS}",
  // Children of one object: back to that object.
  "content/languages.page.tsx": "urlBase={`${ROUTES.CONTENT_ROOT}/site/${siteId}`}",
  "content/translate.page.tsx": "urlBase={`${ROUTES.CONTENT_ROOT}/post/${postId}`}",
}

test("SH1189 — every hero back link names the page's real parent", () => {
  for (const [page, route] of Object.entries(EXPECTED)) {
    assert.ok(read(page).includes(route), `${page} must go back via ${route}`)
  }
})

test("SH1189 — no page outside the content tree goes back to a root it does not sit under", () => {
  const roots = /urlBase=\{(ROUTES\.(CONTENT_ROOT|ADMIN_ROOT)|`\$\{ROUTES\.CONSOLE\}\/content`)\}/
  for (const page of Object.keys(EXPECTED)) {
    assert.doesNotMatch(read(page), roots, `${page} points its back link at a root again`)
  }
})

test("SH1189 — a folder goes back to the listing it was edited from", () => {
  const source = read("content/folder.edit.page.tsx")
  assert.match(source, /folder\.parentFolder\?\.id \?\? folder\.shSite\?\.id/)
  assert.match(source, /listRoute=\{listRoute\}/)
})
