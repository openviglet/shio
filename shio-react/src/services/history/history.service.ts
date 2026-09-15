import axios from "@/lib/axios"
import type { ShHistory } from "@/models/history/history.model"

/**
 * Read-only client for the SH51 audit / activity trail (`/api/v2/history`).
 */
export class ShHistoryService {
  /**
   * Recent activity across all content in the current tenant, newest first.
   *
   * @param who SH999 — `agent`, `human` or a session id. Sent only when set: an absent parameter is
   *            "everything", which is what the page asks for until a curator narrows it.
   */
  async recent(limit = 100, who?: string): Promise<ShHistory[]> {
    const response = await axios.get<ShHistory[]>("/v2/history",
      { params: { limit, ...(who ? { agent: who } : {}) } })
    return response.data
  }

  /** Activity for a single post, newest first. */
  async byObject(objectId: string, limit = 100): Promise<ShHistory[]> {
    const response = await axios.get<ShHistory[]>(`/v2/history/object/${objectId}`, { params: { limit } })
    return response.data
  }

  /** Activity for a single site, newest first. */
  async bySite(siteId: string, limit = 100): Promise<ShHistory[]> {
    const response = await axios.get<ShHistory[]>(`/v2/history/site/${siteId}`, { params: { limit } })
    return response.data
  }
}
