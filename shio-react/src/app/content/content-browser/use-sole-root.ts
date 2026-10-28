/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useEffect } from "react"
import { useNavigate } from "react-router-dom"
import { ROUTES } from "@/app/routes.const"
import { soleRootFolder } from "./browser-view"

/**
 * SH1274 — a site page that holds only its root folder opens on that folder.
 *
 * <p>A site holds its content through one root folder (SH128), so its own listing was a single row
 * named like the site and every path read `quilhara > quilhara`. When the page is the site and that
 * is all it holds, it opens on the folder, replacing the history entry so Back leaves the site rather
 * than bouncing into it again. The trail already omits a root folder that repeats the site's name
 * (SH1184), so the page then reads as the site.</p>
 */
export function useSoleRootRedirect(objectId: string, siteId: string | null, listing: {
  folderId?: string
  folders?: readonly { id: string; root?: boolean }[] | null
  posts?: readonly unknown[] | null
}) {
  const navigate = useNavigate()
  const soleRoot = siteId === objectId && listing.folderId === objectId ? soleRootFolder(listing) : null
  useEffect(() => {
    if (soleRoot) navigate(`${ROUTES.CONTENT_ROOT}/object/${soleRoot}`, { replace: true })
  }, [soleRoot, navigate])
}
