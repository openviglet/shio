/**
 * The agent review queue (SH109), as returned by `/api/v2/review`.
 *
 * Mirrors the backend `ShAgentReview` record. One entry per **agent session**, because
 * that is the unit a human thinks in: a run of an agent is one intention expressed as
 * thirty audit rows, and approving thirty rows one at a time means reconstructing the
 * intention from its fragments.
 */

/** One change an agent made. */
export interface ShReviewChange {
  /** Audit row id. */
  id: string
  /** CREATE | UPDATE | PUBLISH | UNPUBLISH | DELETE | RESTORE. */
  action: string
  /** SH79 address of the affected post, when it still resolves. */
  address?: string
  /** Human-readable description recorded at the time. */
  what?: string
  /** ISO-8601 instant. */
  at?: string
  /** Site name, when recorded. */
  site?: string
  /** Whether a human has accepted it. */
  reviewed: boolean
  reviewedBy?: string
  /** Whether revert can undo this change on its own (an edit cannot be). */
  revertible: boolean
  /** Affected object id; only when the caller asked for ids. */
  postId?: string
}

/** One agent run. */
export interface ShReviewSession {
  /** The session id — what approve and revert name. */
  session: string
  /** The human whose credential the run used. */
  actor?: string
  startedAt?: string
  endedAt?: string
  changes: number
  pending: number
  /** How many changes altered what visitors see. */
  published: number
  sites: string[]
  /** The first few changes, so the list reads without opening every session. */
  preview: ShReviewChange[]
}

export interface ShReview {
  sessions: number
  pending: number
  results: ShReviewSession[]
}

/** What an approve or a revert did. */
export interface ShReviewOutcome {
  session: string
  affected: number
  reverted: number
  /** Changes the revert could not undo, with the reason. Never silent. */
  skipped?: string[]
}
