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
  /** SH1005 — the site as a name, resolved from the page's scope; absent when the row named none. */
  siteName?: string | null
}

/**
 * SH1005 — one page of the trail, and the two positions either side of it.
 *
 * Every read of this API used to be page zero, so the five-hundredth row was the last one the
 * console could show on the one table that only grows. It is the change feed's own keyset cursor,
 * read from the other end: an agent walks it forward from where it stopped, a person scrolls back
 * from the newest row.
 */
export interface ShHistoryPage {
  rows: ShHistory[]
  /** The newest row's position — pass as `since` to ask what has arrived since this page. */
  latest: string | null
  /** The oldest row's position — pass as `before` for the page behind this one. */
  cursor: string | null
  /** There are rows beyond this page, as a fact rather than a guess from a full one. */
  more: boolean
}

/** What a reader can narrow the trail by (SH1005). Every one of these is a query predicate. */
export interface ShHistoryQuery {
  limit?: number
  /** A `cursor` from a previous page: the next page back. */
  before?: string
  /** A `latest` from a previous page: what has been written since it was drawn. */
  since?: string
  /** `agent`, `curator`, or one run's session id (SH999). */
  agent?: string
  /** One principal's rows — a different question from `agent`, because an agent acts as someone. */
  actor?: string
  /** One transition kind, e.g. `PUBLISH`. */
  action?: string
  /** `post`, `folder` or `site` (SH196). */
  subject?: string
  /** One site, by name or id. */
  site?: string
}
