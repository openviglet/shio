import axios from "@/lib/axios"

/**
 * SH969 — the agent-surface write the dock needs, made with the curator's session.
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
}
