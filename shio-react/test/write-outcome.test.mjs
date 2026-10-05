/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs"
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
  "components/publish-preview.dialog.tsx#readPreview.publishPlan":
    "SH1154's dry run, and the one that wears a write's verb: `publishPlan` is a POST because the "
    + "plan is computed by the writer, and `dryRun=true` means it changes nothing — so there is no "
    + "outcome to report, and the plan itself renders in the dialog. False if `dryRun=true` ever "
    + "leaves `ShPostUnifiedService.publishPlan`, or if the dialog stops rendering the plan.",
  "app/content/content-browser/use-bulk-verbs.ts#planBulk.bulk":
    "a bulk plan, rendered for confirmation before anything is applied. False if it applies.",
  "components/post-rename.dialog.tsx#askForPlan.move":
    "SH1132's dry run: the plan renders in the dialog — live or not, the address vacated, the "
    + "inbound count — and is the step that makes a rename two steps. False if it stops rendering "
    + "the plan, or if `dryRun: true` ever leaves this call.",
  "hooks/use-site-thumbnails.ts#put.putThumbnail":
    "SH1218's background refresh of a site's home picture: nobody asked for it, so a toast would "
    + "report a write the curator never made, on every visit with a stale home. The outcome is the "
    + "card's picture appearing; a failure leaves the initials. False if a curator gesture ever "
    + "calls this, which would make it a write they are owed an answer for.",
  "app/content/content-browser/use-browser-open.ts#handleViewPage.mint":
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
  "components/created-page-panel.tsx#preview.mint":
    "the same preview mint as the editor: the new tab is the outcome, and failure reports.",
  "app/system/dock-chat-send.ts#sendRequest.add":
    "a question from the dock: it appears in the transcript the curator typed it into, and a"
    + " refusal reports through reportRefusal. False if the transcript stops showing sent questions.",
  "components/note-rail.tsx#add.add":
    "the note appears in the rail the curator is typing into, and failure reports through"
    + " reportProblem. False if the rail stops re-reading after an add. (SH988 surfaced it: the"
    + " lint had credited a same-named load() elsewhere that toasted.)",
  "components/note-rail.tsx#resolve.resolve":
    "the note moves between open and resolved in the same rail; failure reports.",
  "components/post-live-preview.tsx#render.html":
    "a POST that renders, not a write: the frame shows the result.",
  "components/publish-preview.dialog.tsx#readPreview.publishPlan":
    "the publish dry run, rendered in the dialog that asks for confirmation.",
}

/**
 * SH1139 — emptied, and it stays empty.
 *
 * <p>Sixteen write sites were held here: five that said nothing at all (the token, auth provider,
 * exchange provider, webhook and tenant editors awaited a delete and navigated away), and eleven
 * that toasted success and let the failure escape as an unhandled rejection. A refused create there
 * looked exactly like a button that did nothing.</p>
 *
 * <p>The two static-file deletes were the worst and are worth remembering: they swallowed each
 * failure with `.catch(() => {})`, removed the rows from local state, and then toasted success — so
 * a bulk pass in which <b>nothing</b> went reported all of it deleted, and the rows left the list
 * either way until a reload put them back. The bulk one now goes through `deleteEach`
 * (`lib/batch-delete.ts`), which SH953 wrote for this exact shape in the content browser; reusing it
 * rather than writing the counting again is the whole reason it lives in `lib/`.</p>
 *
 * <p>An entry added here needs a reason it cannot be fixed now, and the test below fails on an entry
 * that has stopped being silent — so this can only shrink, and it is at zero.</p>
 */
const WORKLIST = []

test("SH1154: the publish plan's exception is only true while its dry run is", () => {
  // The EXCEPTIONS entry above says what would falsify it, so this is that sentence run rather than
  // trusted: a `publishPlan` that stopped being a dry run would be a real write with no outcome
  // reported, excused by a line nobody re-read.
  const service = readFileSync(join(SRC, "services", "post-unified", "post-unified.service.ts"),
    "utf8")
  assert.match(service, /publish\?dryRun=true/u,
    "SH1154: `publishPlan` no longer carries `dryRun=true`, so it writes — and its EXCEPTIONS entry "
      + "is now excusing a write that reports nothing")
  const dialog = readFileSync(join(SRC, "components", "publish-preview.dialog.tsx"), "utf8")
  assert.match(dialog, /plan\?\.results/u,
    "SH1154: the dialog no longer renders the plan, which is the other half of why the call needs no "
      + "toast of its own")
})

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

/**
 * SH1139 — the sixteen, named, so a regression names itself.
 *
 * <p>Emptying the WORKLIST makes the test above cover these by default, and that is the weaker
 * claim: it says "nothing is silent", which passes just as well if one of these handlers stops
 * existing, gets renamed, or has its write moved behind a local function the reader cannot see
 * through. Each of the sixteen is listed here instead, so the failure says <em>which</em> outcome
 * went missing from <em>which</em> screen rather than that a count moved.</p>
 *
 * <p>The ids are the reader's own, `<file>#<handler>.<method>`, which is what the WORKLIST held.</p>
 */
const CLOSED = [
  // Five that said nothing at all: awaited a delete and navigated away.
  "app/admin/api-token/api-token.page.tsx#handleDelete.delete",
  "app/admin/auth-provider/auth-provider.page.tsx#handleDelete.delete",
  "app/admin/exchange-provider/exchange-provider.page.tsx#handleDelete.delete",
  "app/admin/site-webhook/site-webhook.page.tsx#handleDelete.delete",
  "app/admin/tenant/tenant.page.tsx#handleDelete.delete",
  // Eleven that toasted success and let the failure escape.
  "app/admin/admin.user.page.tsx#onSubmit.create",
  "app/admin/admin.user.page.tsx#onSubmit.update",
  "app/admin/identity-entity.page.tsx#onSubmit.create",
  "app/content/folder.new.page.tsx#onSubmit.create",
  "app/content/post-type.edit.page.tsx#save.update",
  "app/content/post-type.list.page.tsx#handleDelete.delete",
  "app/content/post-type.new.page.tsx#onSubmit.create",
  "app/content/site.import.page.tsx#onImport.import",
  "app/content/site.new.page.tsx#onSubmit.create",
  "app/content/static-file.list.page.tsx#handleDelete.delete",
  "app/content/static-file.list.page.tsx#bulkDelete.delete",
]

test("SH1139: every site the worklist held reports both outcomes, and still exists", () => {
  const { sites } = writeSites(SRC)
  const byId = new Map(sites.map((s) => [s.id, s]))

  const missing = CLOSED.filter((id) => !byId.has(id))
  assert.deepEqual(missing, [],
    "SH1139: a site this task fixed is no longer found by the reader. That is not automatically a "
      + "regression — a handler may have been renamed or a page rewritten — but it must not pass "
      + "silently, because the list above is the only thing keeping these sixteen from going quiet "
      + "again. Update the id, or delete it if the write is gone.\n  " + missing.join("\n  "))

  const silent = CLOSED.filter((id) => !byId.get(id).success || !byId.get(id).failure)
  assert.deepEqual(silent, [],
    "SH1139: one of the sixteen has gone silent again. The dock reads toasts, so this is a write "
      + "the curator, the mascot and a screen reader all miss — and five of these originally said "
      + "nothing at all while navigating away, which looks identical to a button that worked.\n  "
      + silent.map((id) => {
        const s = byId.get(id)
        return `  ${id}:${s.line} (${s.success ? "" : "no success report"}`
          + `${!s.success && !s.failure ? ", " : ""}${s.failure ? "" : "no failure report"})`
      }).join("\n  "))
})

test("SH1139: a batch delete that ignores its refusals is still silent", () => {
  /*
   * The exemption `deleteEach` earns is narrow, and this is what keeps it narrow. Calling the helper
   * is not the report — the report is saying something about `refusals`, which is the one fact the
   * outcome carries that the swallow it replaced did not. A caller that took the outcome and toasted
   * only `deleted` would be the original defect with more ceremony: SH953's content browser and
   * SH1139's static-file list were both exactly that, one success toast speaking for a selection in
   * which nothing may have gone.
   */
  const dir = mkdtempSync(join(tmpdir(), "sh1139-"))
  mkdirSync(join(dir, "services"))
  mkdirSync(join(dir, "app"))
  writeFileSync(join(dir, "services", "thing.service.ts"),
    "import axios from \"axios\"\nexport class ThingService {\n"
    + "  async delete(id: string) { await axios.delete(`/v2/thing/${id}`) }\n}\n")
  writeFileSync(join(dir, "app", "batch.page.tsx"),
    "import { toast } from \"@viglet/viglet-design-system\"\n"
    + "import { deleteEach } from \"@/lib/batch-delete\"\n"
    + "const thingService = new ThingService()\n"
    + "export function Page() {\n"
    + "  const ignores = async () => {\n"
    + "    const outcome = await deleteEach(targets, (t) => thingService.delete(t.id))\n"
    + "    toast.success(`${outcome.deleted} gone`)\n"
    + "  }\n"
    + "  const reports = async () => {\n"
    + "    const outcome = await deleteEach(targets, (t) => thingService.delete(t.id))\n"
    + "    if (outcome.refusals.length > 0) toast.error(`${outcome.refusals.length} refused`)\n"
    + "    if (outcome.deleted > 0) toast.success(`${outcome.deleted} gone`)\n"
    + "  }\n"
    + "  return null\n}\n")

  const planted = writeSites(dir).sites
  const ignores = planted.find((s) => s.id === "app/batch.page.tsx#ignores.delete")
  const reports = planted.find((s) => s.id === "app/batch.page.tsx#reports.delete")

  assert.ok(ignores && !ignores.failure,
    "SH1139: a handler that calls deleteEach and never mentions refusals was credited with "
      + "reporting failure. Then the exemption is `uses the helper`, which is not a report — and "
      + "the next bulk delete can swallow everything and toast a number again")
  assert.ok(reports && reports.failure && reports.success,
    "SH1139: and one that reports both counts is not silent, or the exemption does nothing and the "
      + "static-file list would have had to hide its write from this reader instead")
  rmSync(dir, { recursive: true, force: true })
})
