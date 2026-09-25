/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { mkdirSync, mkdtempSync, writeFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { writeSites } from "./support/write-outcome.mjs"

/**
 * SH965 — every write the console makes says how it ended, on both paths.
 *
 * SH964 made the toast list the dock's input: `useAssistantNotifications` reads it, and the dock's
 * caption is the console's live region. So a write that resolves without a toast, or fails into
 * nothing, is invisible to the mascot, to a screen reader and to the curator at the same moment.
 * `support/write-outcome.mjs` finds every call to a service method that posts, puts, patches or
 * deletes, and asks whether its handler reaches a report on success and on failure.
 *
 * Two named lists, never a count, because a ceiling lets one fix pay for the next regression:
 *
 * - **EXCEPTIONS**: writes whose outcome is on screen some other way, or deliberately quiet. Each
 *   carries its reason and the observation that would falsify it.
 * - **WORKLIST**: the silent paths the first run found, owned by SH1139. The list can only shrink:
 *   an entry that stops being silent fails here until it is deleted.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")

const EXCEPTIONS = {
  "app/admin/marketplace/marketplace.list.page.tsx#preview.install":
    "a dry run: the plan renders in the preview dialog. False if the dialog stops showing it.",
  "app/content/object.children.page.tsx#planBulk.bulk":
    "a bulk plan, rendered for confirmation before anything is applied. False if it applies.",
  "app/content/object.children.page.tsx#handleViewPage.mint":
    "mints a preview token and opens the page: the new tab is the outcome.",
  "app/content/post.edit.page.tsx#handlePreview.mint":
    "the same preview mint; the preview window is the outcome.",
  "app/content/post.edit.page.tsx#save.patch":
    "autosave on every pause; use-autosave's saved/stopped status line reports it, and a toast per"
    + " pause would flood the dock. False if the status line stops rendering.",
  "app/content/static-file.list.page.tsx#uploadOne.upload":
    "each file's progress row turns done, and failure toasts; the row is the success report.",
  "app/content/static-file.list.page.tsx#saveAlt.updateMeta":
    "inline alt text on a card; failure puts the field back, and a toast would fire once per card"
    + " on a bulk pass (the comment at the call says so).",
  "app/content/translate.page.tsx#toggleReviewed.resolve":
    "the reviewed checkbox flips on success; failure reports through reportProblem.",
  "app/content/translate.page.tsx#toggleReviewed.add":
    "the same toggle, the other direction.",
  "app/setup/setup.page.tsx#handleSubmit.setAdminPassword":
    "first-run setup, outside the shell and before any dock exists; it moves to sign-in.",
  "app/system/bento-tenant-link-notice.tsx#switchTo.switchTo":
    "switching tenant reloads the console into the other tenant; the reload is the outcome.",
  "app/system/bento-tenant-switcher.tsx#switchTo.switchTo":
    "the same switch from the header.",
  "components/post-live-preview.tsx#render.html":
    "a POST that renders, not a write: the frame shows the result.",
  "components/publish-preview.dialog.tsx#PublishPreviewDialog.publishPlan":
    "the publish dry run, rendered in the dialog that asks for confirmation.",
}

const WORKLIST = [
  "app/admin/admin.user.page.tsx#onSubmit.create",
  "app/admin/admin.user.page.tsx#onSubmit.update",
  "app/admin/api-token/api-token.page.tsx#handleDelete.delete",
  "app/admin/auth-provider/auth-provider.page.tsx#handleDelete.delete",
  "app/admin/exchange-provider/exchange-provider.page.tsx#handleDelete.delete",
  "app/admin/identity-entity.page.tsx#onSubmit.create",
  "app/admin/site-webhook/site-webhook.page.tsx#handleDelete.delete",
  "app/admin/tenant/tenant.page.tsx#handleDelete.delete",
  "app/content/folder.new.page.tsx#onSubmit.create",
  "app/content/post-type.edit.page.tsx#save.update",
  "app/content/post-type.list.page.tsx#handleDelete.delete",
  "app/content/post-type.new.page.tsx#onSubmit.create",
  "app/content/site.import.page.tsx#onImport.import",
  "app/content/site.new.page.tsx#onSubmit.create",
  "app/content/static-file.list.page.tsx#handleDelete.delete",
  "app/content/static-file.list.page.tsx#bulkDelete.delete",
]

test("SH965: every console write reports both outcomes, or is named here with its reason", () => {
  const { sites } = writeSites(SRC)
  const silent = [...new Set(sites.filter((s) => !s.success || !s.failure).map((s) => s.id))].sort()
  const held = [...Object.keys(EXCEPTIONS), ...WORKLIST].sort()

  console.log(`SH965: ${sites.length} write sites, ${silent.length} silent on a path —`
    + ` ${Object.keys(EXCEPTIONS).length} excepted with a reason, ${WORKLIST.length} on SH1139's worklist`)

  const unheld = silent.filter((id) => !held.includes(id))
  assert.deepEqual(unheld, [],
    "SH965: a console write does not say how it ended. The dock reads toasts, so this is a save the"
      + " curator, the mascot and a screen reader all miss. Give the handler a success toast and a"
      + " catch that calls reportProblem(error, t(\"common.error\")). If the outcome is on screen"
      + " some other way, add it to EXCEPTIONS with the reason and what would falsify it.\n"
      + unheld.map((id) => {
        const s = sites.find((x) => x.id === id)
        return `  ${id}:${s.line} (${s.success ? "" : "no success report"}${!s.success && !s.failure ? ", " : ""}${s.failure ? "" : "no failure report"})`
      }).join("\n"))

  const healed = held.filter((id) => !silent.includes(id))
  assert.deepEqual(healed, [],
    "SH965: these are held as silent and no longer are, or no longer exist. Delete them from"
      + " EXCEPTIONS or WORKLIST, so the list keeps describing the console.")
})

test("SH965: the reader is not vacuous, and a planted silent delete is named", () => {
  const { methods, sites } = writeSites(SRC)
  assert.ok(methods.size >= 25, `SH965: only ${methods.size} mutating service methods found`)
  assert.ok(sites.length >= 60, `SH965: only ${sites.length} write sites found; the call match broke`)
  assert.ok(sites.some((s) => s.success && s.failure),
    "SH965: no write reports both paths, so the path reading is broken, not the console")

  const dir = mkdtempSync(join(tmpdir(), "sh965-"))
  mkdirSync(join(dir, "services"))
  mkdirSync(join(dir, "app"))
  writeFileSync(join(dir, "services", "thing.service.ts"),
    "import axios from \"axios\"\nexport class ThingService {\n"
    + "  async delete(id: string) { await axios.delete(`/v2/thing/${id}`) }\n}\n")
  writeFileSync(join(dir, "app", "thing.page.tsx"),
    "import { toast } from \"@viglet/viglet-design-system\"\n"
    + "const thingService = new ThingService()\n"
    + "export function Page() {\n"
    + "  const silent = async () => { await thingService.delete(\"1\") }\n"
    + "  const reported = async () => {\n"
    + "    try { await thingService.delete(\"2\"); toast.success(\"gone\") }\n"
    + "    catch (e) { toast.error(\"not gone\") }\n"
    + "  }\n"
    + "  return null\n}\n")

  const planted = writeSites(dir).sites
  const silent = planted.find((s) => s.id === "app/thing.page.tsx#silent.delete")
  const reported = planted.find((s) => s.id === "app/thing.page.tsx#reported.delete")
  assert.ok(silent && !silent.success && !silent.failure,
    "SH965: a handler that awaits a delete and reports nothing was not named silent")
  assert.ok(reported && reported.success && reported.failure,
    "SH965: a handler with a success toast and a failing catch was named silent")
})
