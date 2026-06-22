/**
 * A static file (a `File`-post-type unified post, SH38) as listed by the SH61
 * file-manager page. Mirrors the backend `ShStaticFileItem` record.
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
}
