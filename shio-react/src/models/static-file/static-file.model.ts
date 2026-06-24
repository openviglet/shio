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
}

/** Editable metadata payload for `PUT /v2/staticfile/{id}/meta` (SH62). */
export type ShStaticFileMeta = {
  alt?: string | null
  caption?: string | null
  tags?: string[]
}

/** Optional server-side filters for the Media Library listing (SH62). */
export type ShStaticFileFilter = {
  folderId?: string
  siteId?: string
  /** Media category: image | video | audio | document | archive | other. */
  category?: string
  /** Case-insensitive file-name substring. */
  q?: string
}
