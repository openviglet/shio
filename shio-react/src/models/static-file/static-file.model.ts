/**
 * A static file (a `File`-post-type unified post, SH38) as listed by the
 * Media Library (SH61/SH62) page. Mirrors the backend `ShStaticFileItem` record.
 */
export type ShStaticFileItem = {
  id: string
  fileName: string | null
  contentType: string | null
  /** On-disk byte size; -1 when the stored file is missing. */
  size: number
  owner: string | null
  date: string | null
  folderId: string | null
  folderName: string | null
  siteId: string | null
  siteName: string | null
  /** Image pixel dimensions stamped at upload (SH62); null for non-images / pre-SH62 files. */
  width: number | null
  height: number | null
  /** Editable Media Library metadata (SH62). */
  alt: string | null
  caption: string | null
  tags: string[]
  /**
   * How many posts reference this asset (SH1034), or null when the response did not say.
   *
   * Null and 0 are different answers and the card renders them differently: 0 means nothing
   * references it, which is what a prune acts on, while null means this listing did not ask.
   */
  usageCount?: number | null
  /**
   * SH1280 — how many rendered pages request this asset, present only when the listing was asked
   * with `loads`. A theme's font is referenced by nothing and loaded by every page, so with
   * `usageCount` alone it read as unused.
   */
  loadedBy?: number | null
}

/** Editable metadata payload for `PUT /v2/staticfile/{id}/meta` (SH62). */
export type ShStaticFileMeta = {
  alt?: string | null
  caption?: string | null
  tags?: string[]
}

/** A post that references a file ("used in", SH62). Mirrors backend `ShStaticFileUsage`. */
export type ShStaticFileUsage = {
  postId: string
  title: string | null
  postTypeName: string | null
  folderName: string | null
  siteName: string | null
  published: boolean
}

/** Optional server-side filters for the Media Library listing (SH62). */
export type ShStaticFileFilter = {
  folderId?: string
  siteId?: string
  /** Media category: image | video | audio | document | archive | other. */
  category?: string
  /** Case-insensitive file-name substring. */
  q?: string
  /** SH1030 — zero-based page index; absent means the first. */
  page?: number
  /** SH1030 — assets per page; absent means the server's default. */
  size?: number
  /** SH1280 — also count the rendered pages that load each asset; renders the site's pages. */
  loads?: boolean
}

/**
 * One set of bytes an asset used to have (SH1035).
 *
 * Numbered rather than dated: the number is what a revert passes back, and two replaces in the
 * same second are two renditions. Numbers only increase, so a prune dropping the oldest never
 * renumbers what is left — a revert the dialog offered restores what it said it would.
 */
export type ShAssetRendition = {
  number: number
  fileName: string
  size: number
  storedAt: string
}
