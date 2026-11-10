/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH1429 — where a review row's page is seen: the draft preview of its address.
 *
 * <p>`/preview/{site}/{path}` renders the draft by default (SH183) and the curator's console session
 * already reaches it, so no token is minted per row. `null` for anything that is not a page
 * (a section, a page that no longer exists).</p>
 */
export function draftPreviewUrl(address?: string): string | null {
  if (!address?.startsWith("post:")) return null
  const path = address.slice("post:".length)
  const slash = path.indexOf("/")
  if (slash <= 0) return null
  const site = path.slice(0, slash)
  const page = path.slice(slash + 1)
  return `/preview/${encodeURIComponent(site)}/${page.split("/").map(encodeURIComponent).join("/")}`
}
