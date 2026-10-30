/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useQuery } from "@tanstack/react-query"
import axios from "@/lib/axios"
import { addressKey } from "@/lib/query-keys"
import { pagesAmong, usedOnParams } from "@/lib/section-urls"

export interface ShUsedOn {
  id: string
  address: string
  title?: string
  type?: string
}

/**
 * SH1323 — the pages that list this post, never the post itself. Asked only for a post with no layout.
 *
 * @param pageTypes SH1343 — the types the site renders as pages: a Menu also lists a menu item's URL,
 *                  and only a page can carry the preview
 */
export function useUsedOn(postId: string, site: string | undefined, furl: string | undefined, enabled: boolean,
  pageTypes: ReadonlySet<string>) {
  return useQuery({
    queryKey: addressKey(`post:${site ?? ""}${furl ?? ""}|usedOn`),
    queryFn: ({ signal }) => axios.get<{ results?: ShUsedOn[] }>("/v2/agent/find",
      { signal, params: usedOnParams(site ?? "", furl ?? "") })
      .then((response) => response.data.results ?? []),
    select: (rows) => pagesAmong(rows, pageTypes, postId),
    enabled: enabled && Boolean(site) && Boolean(furl),
  })
}
