import axios from "axios"
import type {
  ShReviewDetail,
  ShReviewOutcome,
  ShReviewPending,
  ShReviewSession,
} from "@/models/review/review.model"

/**
 * The review queue (SH109). Console-authenticated, and deliberately not on the agent
 * namespace: this is the surface that reviews an agent, so an agent's own credential is not
 * what approves its work.
 */
export class ShReviewService {
  async sessions(limit = 50): Promise<ShReviewSession[]> {
    const response = await axios.get<ShReviewSession[]>("/v2/review/sessions", {
      params: { limit },
    })
    return response.data
  }

  /**
   * How much is waiting for a human (SH207) — cheap enough for the navigation badge.
   *
   * Not `sessions()`: that scans 2000 audit rows and resolves every touched post, which is fine
   * for a page somebody opened and not for something asked on every console render.
   */
  async pending(): Promise<ShReviewPending> {
    const response = await axios.get<ShReviewPending>("/v2/review/pending")
    return response.data
  }

  async detail(session: string): Promise<ShReviewDetail> {
    const response = await axios.get<ShReviewDetail>(
      `/v2/review/sessions/${encodeURIComponent(session)}`,
    )
    return response.data
  }

  /** Publish the drafts this run wrote — the step SH107 keeps the agent from taking. */
  async approve(session: string): Promise<ShReviewOutcome> {
    const response = await axios.post<ShReviewOutcome>(
      `/v2/review/sessions/${encodeURIComponent(session)}/approve`,
    )
    return response.data
  }

  /** Undo it, using SH14 restore and the SH55 trash. */
  async revert(session: string): Promise<ShReviewOutcome> {
    const response = await axios.post<ShReviewOutcome>(
      `/v2/review/sessions/${encodeURIComponent(session)}/revert`,
    )
    return response.data
  }
}
