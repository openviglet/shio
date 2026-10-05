/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useQueryClient } from "@tanstack/react-query"
import { useEffect } from "react"
import { addressKey } from "@/lib/query-keys"
import { browserMemory, refreshThumbnails } from "@/lib/site-thumbnail"
import type { ShSite } from "@/models/site/site"
import { ShSiteService } from "@/services/site/site.service"

const siteService = new ShSiteService()

/**
 * SH1218 — once per listing read, brings the home pictures up to date in the background.
 *
 * It renders nothing and blocks nothing: the list is drawn from the read it already has, and when a
 * picture is stored the catalogue is re-read so the card shows it. Leaving the list aborts what is
 * still in flight (SH1047), and the next visit picks up where this one stopped, because what was
 * already asked about is remembered per change.
 */
export function useSiteThumbnails(sites: ShSite[] | undefined) {
  // The client itself is stable across renders, unlike `useInvalidate`'s closure, so the effect
  // runs once per listing and not once per render.
  const client = useQueryClient()
  useEffect(() => {
    if (!sites) return
    const controller = new AbortController()
    const { signal } = controller
    void refreshThumbnails(sites, {
      status: (id) => siteService.thumbnailStatus(id, { signal }),
      capture: async (site) => {
        signal.throwIfAborted()
        return (await import("@/lib/site-thumbnail-capture")).captureHome(site)
      },
      put: (id, digest, picture) => siteService.putThumbnail(id, digest, picture, { signal }),
      ...browserMemory(),
    }).then((stored) => {
      if (stored > 0 && !signal.aborted) {
        void client.invalidateQueries({ queryKey: addressKey("site:") })
      }
    })
    return () => controller.abort()
  }, [sites, client])
}
