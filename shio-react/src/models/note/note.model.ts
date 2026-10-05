/**
 * A correction anchored to a page, a field or a revision (SH1003).
 *
 * Mirrors the backend's `ShNoteAPI.ShNoteResponse`. A note is exactly as visible as the object it
 * hangs on — the server asks the SH54 content ACL for every read and write — so there is no
 * visibility field here to get wrong.
 */
export interface ShNote {
  id: string
  objectId: string
  /** The field it is about, when it is about one. */
  fieldPath?: string | null
  /** The revision it is about, when it is about one rather than the page now. */
  revisionNo?: number | null
  /** The note this replies to; absent on a root. */
  parentId?: string | null
  /** COMMENT | REQUEST | REPLY | REVIEWED (SH1061, a field checked against its source). */
  kind: string
  body: string
  author?: string | null
  /** SH106 — the run that wrote it, when an agent did. */
  agentSession?: string | null
  resolved: boolean
  resolvedBy?: string | null
  resolvedAt?: string | null
  createdAt?: string | null
}

/**
 * SH1214 — one row of the open-notes work list. Mirrors `ShNoteService.OpenNote`: the page is named
 * by address, and `address` is null when the page is gone.
 */
export interface ShOpenNote {
  id: string
  objectId: string
  address?: string | null
  site?: string | null
  field?: string | null
  revision?: number | null
  kind: string
  body: string
  author?: string | null
  createdAt?: string | null
}

/** What the console sends to leave one. */
export interface ShNoteInput {
  objectId: string
  fieldPath?: string
  revisionNo?: number
  parentId?: string
  kind?: string
  body: string
}
