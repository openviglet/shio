/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useSearchParams } from "react-router-dom"

export type SiteRootMode = "pages" | "folders"

/**
 * SH1316 — which of the two views a site's root shows, in its URL so it is a link: pages unless the
 * address says `as=folders`. Anywhere below the root it is folders, which is all there is there.
 */
export function useSiteRootMode(atRoot: boolean): [SiteRootMode, (mode: SiteRootMode) => void] {
  const [params, setParams] = useSearchParams()
  const mode: SiteRootMode = atRoot && params.get("as") !== "folders" ? "pages" : "folders"
  const set = (next: SiteRootMode) => {
    const copy = new URLSearchParams(params)
    if (next === "folders") copy.set("as", "folders"); else copy.delete("as")
    setParams(copy, { replace: true })
  }
  return [mode, set]
}
