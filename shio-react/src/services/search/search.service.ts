import axios from "@/lib/axios"
import type { ShRead } from "@/lib/read-options"

/**
 * One row of a console search — the agent's `find` row, unchanged (SH1027).
 *
 * It is not re-projected into a console shape on purpose: this listing *is* `/agent/find` with a
 * permission filter over it, and a third spelling of a post's address and state is the one that
 * drifts.
 */
export interface ShConsoleSearchRow {
  address: string
  type?: string
  state?: string
  title?: string
  id?: string
  locale?: string
  /**
   * SH1240 — whether the post has a live version, as the server names it (`ShAgentFound.Row`,
   * SH545). The model called it `live`, a field the server never sent.
   */
  published?: boolean
}

export interface ShConsoleSearchResult {
  results: ShConsoleSearchRow[]
  count: number
  /**
   * How many matched and were removed because this reader may not open them.
   *
   * The number rather than silence: a filtered listing that says nothing is indistinguishable
   * from a search that found less, and the curator's next move is to conclude the content is not
   * there — which sends them to write the page again rather than to ask for access.
   */
  withheld: number
  truncated: boolean
  cursor?: string
}

/**
 * The console's content search (SH1027).
 *
 * It used to call `/v2/cda/search` — the **delivery** API, published-only unless the caller holds
 * a preview token, which the console has never sent. So an agent could find the drafts it had
 * just written and the curator reviewing them could not, which is exactly backwards: searching
 * for what a run made is the first thing a curator does after it.
 *
 * The filter names are `ShContentQuery`'s (SH1022), so a search here is the same question
 * `shio find` asks with the same words.
 */
export class ShSearchService {
  async search(params: Record<string, string>, limit = 50, read: ShRead = {}): Promise<ShConsoleSearchResult> {
    const query = new URLSearchParams()
    for (const [key, value] of Object.entries(params)) {
      if (value) query.set(key, value)
    }
    query.set("limit", String(limit))
    const response = await axios.get<ShConsoleSearchResult>(`/v2/content/search?${query}`,
      { signal: read.signal })
    return response.data
  }
}
