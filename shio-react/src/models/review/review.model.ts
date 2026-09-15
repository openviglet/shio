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
  /**
   * SH1000 — what the run said it was for, declared once by whoever started it.
   *
   * Absent when nothing declared one, and the page says so in those words rather than leaving a
   * blank: a reviewer reading a count of changes over a uuid has to reconstruct the premise from
   * the diff, which is the one thing the agent knew and never said.
   */
  intent?: string | null
  /** SH999 — what the run called itself on `initialize`; absent when it never said. */
  agentName?: string | null
  changes: number
  byAction: Record<string, number>
  /**
   * SH1000 — counts per site name. The half `sites` could not answer: an id tells a reviewer
   * nothing, and eleven changes over four sites is a different decision from eleven in one.
   */
  bySite?: Record<string, number>
  firstSeen?: string
  lastSeen?: string
  sites: string[]
  /** Changes that can still be acted on — a draft nobody has published yet. */
  pending: number
}

/**
 * How much agent work is waiting for a human (SH207) — the navigation badge's read.
 *
 * `sessions` is what the badge shows, because the page it opens lists sessions. `changes` counts
 * distinct posts, which is deliberately not the sum of each session's `pending`: that one counts
 * audit entries, so a run that edited one page four times reports four.
 */
export interface ShReviewPending {
  sessions: number
  changes: number
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

/**
 * SH1000 — which of a run's pages look different than they did before it.
 *
 * On the detail and not on the listing because each comparison is two real renders (SH249): the
 * listing is asked for on every visit and holds every open run, and a detail is one run somebody
 * opened. `checked` is the denominator — without it an empty `moved` reads as "nothing moved" when
 * it may mean "nothing was comparable".
 */
export interface ShReviewAppearance {
  moved: string[]
  checked: number
  /** What was left out, when the ceiling bit. */
  note?: string | null
}

export interface ShReviewDetail {
  session: string
  actor?: string
  appearance?: ShReviewAppearance | null
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
