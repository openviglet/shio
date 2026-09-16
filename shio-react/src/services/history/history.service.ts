import axios from "@/lib/axios"
import type { ShHistoryPage, ShHistoryQuery } from "@/models/history/history.model"

/** Only the keys that were actually set: an absent parameter is "everything". */
function params(query: ShHistoryQuery): Record<string, string | number> {
  const sent: Record<string, string | number> = { limit: query.limit ?? 100 }
  for (const key of ["before", "since", "agent", "actor", "action", "subject", "site"] as const) {
    const value = query[key]
    if (value) sent[key] = value
  }
  return sent
}

/**
 * Read-only client for the SH51 audit / activity trail (`/api/v2/history`).
 *
 * SH1005 — a page with a cursor, not a list. Both methods return the same shape, because they are
 * the same read narrowed differently; `bySite` is gone, since a site is one narrowing among six and
 * a method per narrowing is how a client grows a second reader.
 */
export class ShHistoryService {
  /** Recent activity across all content in the current tenant, newest first. */
  async recent(query: ShHistoryQuery = {}): Promise<ShHistoryPage> {
    const response = await axios.get<ShHistoryPage>("/v2/history", { params: params(query) })
    return response.data
  }

  /** One object's own trail — what the post editor's history panel asks for. */
  async byObject(objectId: string, query: ShHistoryQuery = {}): Promise<ShHistoryPage> {
    const response = await axios.get<ShHistoryPage>(`/v2/history/object/${objectId}`,
      { params: params(query) })
    return response.data
  }
}
