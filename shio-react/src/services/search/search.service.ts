import axios from "@/lib/axios"
import type { ShCdaListing } from "@/models/search/search.model"

/**
 * Client for the SH52 full-text search CDA endpoint (`/api/v2/cda/search`).
 */
export class ShSearchService {
  async search(q: string, siteId?: string, page = 0, size = 50): Promise<ShCdaListing> {
    const response = await axios.get<ShCdaListing>("/v2/cda/search", {
      params: { q, siteId: siteId || undefined, page, size },
    })
    return response.data
  }
}
