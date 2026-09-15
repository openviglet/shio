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
  /**
   * SH989 — the names of the fields the write changed. `null` when the row recorded none (a
   * create, a delete, an older row); `[]` when it recorded that nothing changed.
   */
  changedFields: string[] | null
  /**
   * SH999 — the agent run that wrote this, or absent when a person did.
   *
   * A curator's own publishing carries a session id too (SH996) and is deliberately **not** here:
   * that is a person, and a chip reading "agent" on their own press is the page getting the one
   * question it exists to answer wrong.
   */
  agent?: string | null
  /** What that run called itself on `initialize`, e.g. `claude-code`; absent when it never said. */
  agentName?: string | null
  /** SH196 — `POST` or `FOLDER`: what the row is about, recorded since SH196 and never carried. */
  subjectKind?: string | null
}
