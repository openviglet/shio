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
import { usedOnParams } from "@/lib/section-urls"

export interface ShUsedOn {
  id: string
  address: string
  title?: string
}

/** SH1323 — the pages that list this post, never the post itself. Asked only for a post with no layout. */
export function useUsedOn(postId: string, site: string | undefined, furl: string | undefined, enabled: boolean) {
  return useQuery({
    queryKey: addressKey(`post:${site ?? ""}${furl ?? ""}|usedOn`),
    queryFn: ({ signal }) => axios.get<{ results?: ShUsedOn[] }>("/v2/agent/find",
      { signal, params: usedOnParams(site ?? "", furl ?? "") })
      .then((response) => (response.data.results ?? []).filter((row) => row.id !== postId)),
    enabled: enabled && Boolean(site) && Boolean(furl),
  })
}
