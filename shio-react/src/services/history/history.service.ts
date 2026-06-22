import axios from "@/lib/axios"
import type { ShHistory } from "@/models/history/history.model"

/**
 * Read-only client for the SH51 audit / activity trail (`/api/v2/history`).
 */
export class ShHistoryService {
  /** Recent activity across all content in the current tenant, newest first. */
  async recent(limit = 100): Promise<ShHistory[]> {
    const response = await axios.get<ShHistory[]>("/v2/history", { params: { limit } })
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
