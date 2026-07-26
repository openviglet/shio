import axios from "@/lib/axios"
import type { ShReview, ShReviewChange, ShReviewOutcome } from "@/models/review/review.model"

/**
 * Client for the agent review queue (SH109, `/api/v2/review`).
 *
 * Note the path: the queue deliberately does **not** live under `/api/v2/agent`. Token
 * authentication is confined by prefix, so being outside it is what makes "an agent
 * cannot approve its own changes" a property of the routing table rather than a check
 * somebody has to remember to write.
 */
export class ShReviewService {
  /** Agent sessions, newest first. Pending-only by default — a queue showing finished work stops being read. */
  async queue(pendingOnly = true, limit = 50): Promise<ShReview> {
    const response = await axios.get<ShReview>("/v2/review", { params: { pendingOnly, limit } })
    return response.data
  }

  /** Everything one session did, oldest first — the order it happened in. */
  async changes(session: string): Promise<ShReviewChange[]> {
    const response = await axios.get<ShReviewChange[]>(`/v2/review/${encodeURIComponent(session)}`)
    return response.data
  }

  /** Accept a session's changes. Writes no content; it records that a person looked. */
  async approve(session: string): Promise<ShReviewOutcome> {
    const response = await axios.post<ShReviewOutcome>(
      `/v2/review/${encodeURIComponent(session)}/approve`,
    )
    return response.data
  }

  /** Undo a session's changes, newest first. The outcome names anything it could not undo. */
  async revert(session: string): Promise<ShReviewOutcome> {
    const response = await axios.post<ShReviewOutcome>(
      `/v2/review/${encodeURIComponent(session)}/revert`,
    )
    return response.data
  }
}
