import axios from "@/lib/axios"
import type { ShRead } from "@/lib/read-options"

/** What `GET /agent/resolve` answers about an address: the object's id, and what it is. */
export interface ShResolved {
  kind: string
  id: string
  address?: string
  title?: string
  /** A post's type name, e.g. `SectionHero`, which SH1205 shows beside a section's title. */
  type?: string
}

/**
 * SH969 — the agent-surface calls the dock needs, made with the curator's session.
 *
 * <p>Not a second contract (P10): a retry is exactly one entry of `POST /agent/batch`, the op and
 * address the incident itself carries. So retrying a failed scheduled publish is the publish anyone
 * else would make, with the same ACL and gate.</p>
 */
export class ShAgentFeedService {
  /** Run one op the feed handed back, as its own batch. */
  async retry(op: string, address: string): Promise<void> {
    await axios.post("/v2/agent/batch", { ops: [{ op, address }] })
  }

  /**
   * SH970 — the id behind an address. The stream carries addresses and withholds ids (SH121), and
   * the console's routes are keyed by id, so a report's "open" resolves on the click.
   */
  async locate(address: string, read: ShRead = {}): Promise<ShResolved> {
    const response = await axios.get<ShResolved>("/v2/agent/resolve",
      { params: { address }, signal: read.signal })
    return response.data
  }
}
