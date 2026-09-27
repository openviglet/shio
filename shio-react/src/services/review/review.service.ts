// SH1001 — the shared singleton, not the bare module. This file imported `axios` directly and
// worked only because `@/lib/axios` mutates the global defaults and some other service had already
// been imported: the CSRF header, the base URL and every shared interceptor were arriving by import
// order. That is the SH233 defect one step from happening again.
import axios from "@/lib/axios"
import type { ShRead } from "@/lib/read-options"
import type {
  ShReviewDecisionInput,
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
  async sessions(limit = 50, options: ShRead = {}): Promise<ShReviewSession[]> {
    const response = await axios.get<ShReviewSession[]>("/v2/review/sessions", {
      params: { limit },
      signal: options.signal,
    })
    return response.data
  }

  /**
   * How much is waiting for a human (SH207) — cheap enough for the navigation badge.
   *
   * Not `sessions()`: that scans 2000 audit rows and resolves every touched post, which is fine
   * for a page somebody opened and not for something asked on every console render.
   */
  async pending(options: ShRead = {}): Promise<ShReviewPending> {
    const response = await axios.get<ShReviewPending>("/v2/review/pending", { signal: options.signal })
    return response.data
  }

  async detail(session: string, options: ShRead = {}): Promise<ShReviewDetail> {
    const response = await axios.get<ShReviewDetail>(
      `/v2/review/sessions/${encodeURIComponent(session)}`,
      { signal: options.signal },
    )
    return response.data
  }

  /**
   * Decide this run's changes one at a time (SH1001).
   *
   * One call for the whole triage, because a curator deciding eleven pages means them together:
   * sent one at a time, a refusal halfway leaves the run in a state nobody chose and this page has
   * to work out which of its eleven requests landed.
   */
  async decide(session: string, decisions: ShReviewDecisionInput[]): Promise<ShReviewOutcome> {
    const response = await axios.post<ShReviewOutcome>(
      `/v2/review/sessions/${encodeURIComponent(session)}/decisions`,
      decisions,
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
