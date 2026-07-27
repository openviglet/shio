/**
 * The review queue (SH109) — "the agent proposed N changes".
 *
 * Mirrors the backend's `ShReviewSession`/`ShReviewDetail`/`ShReviewOutcome`. There is no
 * proposal table behind these: a run's changes are the SH51 audit rows grouped by the SH106
 * agent session id, and the post's DRAFT row *is* the proposal.
 */
export interface ShReviewSession {
  session: string
  actor?: string
  changes: number
  byAction: Record<string, number>
  firstSeen?: string
  lastSeen?: string
  sites: string[]
  /** Changes that can still be acted on — a draft nobody has published yet. */
  pending: number
}

export interface ShReviewEntry {
  action: string
  postId?: string
  /** The SH79 address, absent when the object no longer exists. */
  address?: string
  title?: string
  siteId?: string
  date?: string
  draft: boolean
  published: boolean
  trashed: boolean
  /** Whether the queue can honestly undo this one; a button that fails is worse than none. */
  revertable: boolean
}

export interface ShReviewDetail {
  session: string
  actor?: string
  entries: ShReviewEntry[]
}

export interface ShReviewOutcome {
  session: string
  action: "approve" | "revert"
  changed: number
  posts: string[]
  /** What was left alone, with the reason — each one needs a different next step. */
  skipped: { postId: string; reason: string }[]
}
