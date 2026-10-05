/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import type { ShSite } from "@/models/site/site"

/**
 * SH1218 — the console's half of a site's home picture: deciding which sites to ask about, and
 * sending what it captured.
 *
 * The instance does not run a browser, so the console is the one that takes the picture. It does
 * not decide whether one is needed: `GET /v2/site/{id}/thumbnail/status` does, from the published
 * home's appearance digest. What this file decides is only when to *ask*, and it asks as little as
 * it can, because each status read is a server render:
 *
 * - a site with no published page has no home to picture;
 * - a site whose picture is newer than its last change is current without asking;
 * - a change already asked about in this browser is not asked about again, so opening the list
 *   twice with nothing republished sends nothing.
 *
 * The capture itself is in `site-thumbnail-capture.ts`, behind a dynamic import, so the
 * rasteriser stays out of the eager bundle SH1043 measures.
 */

/** What `GET /v2/site/{id}/thumbnail/status` answers. */
export type ThumbnailStatus = { home: string | null; stored: string | null; fresh: boolean }

/** The four effects, injected so the decisions can be driven without a browser or a server. */
export type ThumbnailDeps = {
  status: (siteId: string) => Promise<ThumbnailStatus>
  capture: (site: ShSite) => Promise<Blob>
  put: (siteId: string, digest: string, picture: Blob) => Promise<{ stored: boolean }>
  /** The change this browser last asked about for a site, or undefined. */
  asked: (siteId: string) => string | undefined
  remember: (siteId: string, change: string) => void
}

/** How many pictures one visit to the list may take: each is a frame and a render. */
export const PER_VISIT = 3

/** The change a site is at, as the mark remembered once it has been asked about. */
export function changeMark(site: ShSite): string {
  return site.lastChange?.at ?? ""
}

/** Whether a site is worth a status read on this visit. */
export function needsCheck(site: ShSite, asked: string | undefined): boolean {
  if (!site.pages || site.pages.published === 0) return false
  const mark = changeMark(site)
  if (site.thumbnail && (!mark || Date.parse(mark) <= Date.parse(site.thumbnail.at))) return false
  return asked !== mark
}

/**
 * Asks about the sites that need it, captures the stale ones and sends them.
 *
 * @returns how many pictures were stored, so the caller re-reads the list only when one was
 */
export async function refreshThumbnails(
  sites: ShSite[], deps: ThumbnailDeps, limit = PER_VISIT,
): Promise<number> {
  let stored = 0
  let taken = 0
  for (const site of sites) {
    if (taken >= limit) break
    if (!needsCheck(site, deps.asked(site.id))) continue
    try {
      const status = await deps.status(site.id)
      if (status.fresh || !status.home) {
        deps.remember(site.id, changeMark(site))
        continue
      }
      taken++
      const picture = await deps.capture(site)
      const outcome = await deps.put(site.id, status.home, picture)
      deps.remember(site.id, changeMark(site))
      if (outcome.stored) stored++
    } catch {
      // A picture is a convenience: a site that will not render or a home published again in
      // between leaves the card on its initials, and the next visit asks again.
    }
  }
  return stored
}

/** The localStorage half of `asked` and `remember`, which survives a reload and nothing more. */
export function browserMemory(): Pick<ThumbnailDeps, "asked" | "remember"> {
  const key = (siteId: string) => `shio.siteThumbnail.asked.${siteId}`
  return {
    asked: (siteId) => {
      try {
        return localStorage.getItem(key(siteId)) ?? undefined
      } catch {
        return undefined
      }
    },
    remember: (siteId, change) => {
      try {
        localStorage.setItem(key(siteId), change)
      } catch {
        // Private mode or storage full: the next visit asks again, which is the safe direction.
      }
    },
  }
}
