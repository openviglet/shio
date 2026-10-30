/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH1145 — the three verbs that leave the browser: duplicate a row, open the site, open a page.
 *
 * <p>Grouped because each ends somewhere other than this list — a copy in this folder, a new tab on
 * the front end, a preview behind a short-lived token — and because each answers a failure with a
 * different sentence. On the page they sat between the bulk plan and the folder picker.</p>
 */
import axios from "axios"
import { toast } from "@viglet/viglet-design-system"

import { publishedDestination, siteDestination } from "@/lib/site-front-end"
import { ShPreviewTokenService } from "@/services/preview-token/preview-token.service"
import { ShSiteService } from "@/services/site/site.service"

const siteService = new ShSiteService()
const previewService = new ShPreviewTokenService()

export interface BrowserOpen {
  objectId: string
  siteId: string | null
  refreshList: () => Promise<void>
  t: (key: string, options?: Record<string, unknown>) => string
}

export function useBrowserOpen({ objectId, siteId, refreshList, t }: BrowserOpen) {
  const handleClone = async (itemId: string) => {
    await axios.put(`/v2/object/copyto/${objectId}`, [itemId])
    toast.success(t("content.clonedSuccess"))
    await refreshList()
  }

  /**
   * SH179 — "View Site" opens the site's front end.
   *
   * It used to navigate to `ROUTES.PREVIEW`, a path declared in `routes.const.ts` and
   * registered by nothing, so the button rendered an empty page. Where the published site
   * lives is the front end's business and the CMS may not have one configured — saying so is
   * a legitimate answer; a blank page is not.
   */
  const handleViewSite = async () => {
    if (!siteId) return
    try {
      const site = await siteService.get(siteId)
      // SH184: a site with no deployed front end falls back to Shio's own renderer, which
      // is the same rule the site list and the Universal Editor follow — one function, so
      // the three cannot drift.
      window.open(siteDestination(site).url, "_blank", "noopener")
    } catch {
      toast.error(t("content.previewError"))
    }
  }

  /** SH1304 — and what a visitor sees: `/sites/<furl>/`, or the deployed front end. */
  const handleViewPublishedSite = async () => {
    if (!siteId) return
    try {
      window.open(publishedDestination(await siteService.get(siteId)).url, "_blank", "noopener")
    } catch {
      toast.error(t("content.previewError"))
    }
  }

  /** SH179 — "View Page" opens the post's draft through an SH13 preview token. */
  const handleViewPage = async (postId: string) => {
    try {
      const { previewUrl } = await previewService.mint(postId)
      window.open(previewUrl, "_blank", "noopener")
    } catch (error) {
      // 400 = the site has no front-end url configured; anything else = preview unavailable.
      const noUrl = axios.isAxiosError(error) && error.response?.status === 400
      toast.error(t(noUrl ? "content.previewNoUrl" : "content.previewError"))
    }
  }


  return { handleClone, handleViewSite, handleViewPublishedSite, handleViewPage }
}
