/**
 * A single content-audit entry (SH51), as returned by the read-only
 * `/api/v2/history` API. Mirrors the backend `ShHistoryAPI.ShHistoryResponse`.
 */
export interface ShHistory {
  id: string
  /** Transition kind: CREATE | UPDATE | PUBLISH | UNPUBLISH | DELETE | RESTORE. */
  action: string
  /** Human-readable summary, e.g. `Published "My Page"`. */
  description: string
  /** Who performed it (username, or "system"/"scheduler"). */
  owner: string
  /** ISO-8601 instant. */
  date: string
  /** The affected post id. */
  shObject: string
  /** The post's site id (may be empty). */
  shSite: string
}
