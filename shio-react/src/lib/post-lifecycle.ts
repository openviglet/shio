/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH1324 — the word a curator reads for a post's state: whether the site serves it.
 *
 * <p>`state` names the row being read, and a published post keeps an editable DRAFT row, so every
 * search hit and every editor header read "Draft" on a page the site was serving. What a curator
 * asks is whether it is live, and that is `published` (the find row's SH545 flag, the editor's
 * `isLive`). The i18n key is returned rather than the word, so this stays a pure function a test
 * can call.</p>
 */
export function lifecycleKey(live: boolean | undefined, changed?: boolean):
  "content.liveChanged" | "content.filterPublished" | "content.filterDraft" {
  if (live && changed) return "content.liveChanged"
  return live ? "content.filterPublished" : "content.filterDraft"
}

/**
 * Whether a draft says something its live row does not: title or document, the two a publish
 * copies across. The server's `changed` on a find row is the same comparison (SH1324).
 */
export function draftDiffers(draft: { title?: string; data?: unknown } | null | undefined,
  live: { title?: string; data?: unknown } | null | undefined): boolean {
  if (!draft || !live) return false
  return draft.title !== live.title || JSON.stringify(draft.data ?? {}) !== JSON.stringify(live.data ?? {})
}

/**
 * The muted half of a palette row: type, then state, each after a separator. The leading one is
 * there because the package joins the title and this into one accessible name with nothing
 * between them, which a screen reader announced as "PricingSectionHero".
 */
export function paletteDescription(type: string | undefined, state: string): string {
  return [type, state].filter(Boolean).map((part) => `· ${part}`).join(" ")
}
