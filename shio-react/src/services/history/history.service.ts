import axios from "@/lib/axios"
import type { ShHistoryPage, ShHistoryQuery, ShWorking } from "@/models/history/history.model"
import type { ShRead } from "@/lib/read-options"

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
  async recent(query: ShHistoryQuery = {}, read: ShRead = {}): Promise<ShHistoryPage> {
    const response = await axios.get<ShHistoryPage>("/v2/history",
      { params: params(query), signal: read.signal })
    return response.data
  }

  /** One object's own trail — what the post editor's history panel asks for. */
  async byObject(objectId: string, query: ShHistoryQuery = {}): Promise<ShHistoryPage> {
    const response = await axios.get<ShHistoryPage>(`/v2/history/object/${objectId}`,
      { params: params(query) })
    return response.data
  }

  /**
   * SH1008 — who else is on this page right now, or null.
   *
   * Derived from who wrote recently and expiring on its own, so there is nothing to release. It
   * refuses nothing: a save while it is showing meets the same SH985 guard it always did.
   */
  async working(objectId: string): Promise<ShWorking | null> {
    const response = await axios.get<ShWorking | "">(`/v2/history/working/${objectId}`)
    return response.data || null
  }
}
