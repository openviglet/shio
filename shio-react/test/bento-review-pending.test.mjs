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
 * SH936 — an agent writes, and something says so.
 *
 * <h2>The hole the cutover left</h2>
 *
 * The console sidebar badged Agent Review when runs were waiting (SH207). SH910 deleted the
 * sidebar, the bento rail draws sections rather than screens, and `ReviewPendingProvider` was
 * deliberately left unmounted as "a fetch nothing reads". Correct about the rail, and it left the
 * curator's half of the agent-native design with no ambient signal at all: P7 says never surprise
 * the curator, and drafts an agent wrote sat until somebody thought to open a screen.
 *
 * <h2>Why the assertions are about placement and absence</h2>
 *
 * Two things can silently undo this, and neither is a rendering bug. The provider can come
 * unmounted — then the indicator reads a zero-and-no-op default, renders nothing, and looks
 * exactly like an empty queue. And the badge can start rendering a zero, which is the state SH207
 * chose against: a dimmed "0" on every screen forever is a number a reader learns to skip, and
 * skipping it is what it was there to prevent.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")

const BADGE = stripComments(
  readFileSync(join(SRC, "app", "system", "bento-review-pending.tsx"), "utf8"))
const SHELL = stripComments(readFileSync(join(SRC, "app", "system", "bento.root.page.tsx"), "utf8"))
const CONTEXT = stripComments(
  readFileSync(join(SRC, "contexts", "review-pending.context.tsx"), "utf8"))
const QUEUE = stripComments(
  readFileSync(join(SRC, "app", "admin", "review", "review.list.page.tsx"), "utf8"))

test("SH936: the shell mounts the provider, above both the reader and the writer", () => {
  assert.match(SHELL, /<ReviewPendingProvider>/,
    "SH936: the bento shell no longer mounts ReviewPendingProvider. The indicator then reads the"
      + " zero-and-no-op default and renders nothing, which is indistinguishable from an empty"
      + " queue — the exact silence this task closed.")
  assert.match(SHELL, /<BentoReviewPending \/>/,
    "SH936: the header no longer renders the indicator, so the count is fetched and shown to"
      + " nobody.")

  /*
   * The provider has to sit above BOTH the header that reads the count and the Outlet that renders
   * the queue which invalidates it. If it ever wraps only the header, an approve stops updating the
   * badge and the two disagree until a remount — the failure the context was written to prevent.
   */
  const provider = SHELL.indexOf("<ReviewPendingProvider>")
  const header = SHELL.indexOf("<BentoReviewPending />")
  const outlet = SHELL.indexOf("<Outlet />")
  assert.ok(provider !== -1 && header > provider && outlet > provider,
    "SH936: ReviewPendingProvider no longer encloses both the header indicator and the Outlet."
      + " The count is read in one subtree and invalidated in the other, so they can only meet at"
      + " a common ancestor.")
})

test("SH936: nothing pending renders nothing, which is SH207's rule", () => {
  assert.match(BADGE, /pending <= 0\)\s*return null/,
    "SH936: the indicator no longer disappears at zero. A dimmed '0' in the header of every screen"
      + " is a number a curator reads once and then stops seeing, and not seeing it is what the"
      + " badge existed to prevent. Absent, not zero.")
  assert.match(BADGE, /ROUTES\.ADMIN_REVIEW/,
    "SH936: the indicator no longer links to the review queue, so it reports a number and offers"
      + " no way to act on it.")
  assert.match(BADGE, /aria-label=\{t\(/,
    "SH936: the control is an icon and a bare numeral, so without an accessible name a screen"
      + " reader announces a digit with no subject.")
})

test("SH936: the count still has exactly one source and one invalidator", () => {
  // The badge must not fetch for itself. Two readers of the same number drift the moment one of
  // them is refreshed and the other is not, which is the arrangement the context replaced.
  assert.doesNotMatch(BADGE, /ShReviewService|service\.pending\(/,
    "SH936: the indicator fetches the count itself. It then cannot be invalidated by an approve on"
      + " the queue page, and a badge that disagrees with the page it opens is worse than none.")
  assert.match(BADGE, /useReviewPending\(\)/, "SH936: the indicator no longer reads the context")
  assert.match(CONTEXT, /catch\(\(\) => setPending\(0\)\)/,
    "SH936: the provider stopped swallowing the read error. A reader who may not open the queue"
      + " gets a 403 on the count, and that has to look like an empty queue rather than break the"
      + " shell.")
  assert.match(QUEUE, /useReviewPending\(\)/,
    "SH936: the review queue no longer invalidates the count, so approving a run leaves the header"
      + " claiming it is still waiting.")
})
