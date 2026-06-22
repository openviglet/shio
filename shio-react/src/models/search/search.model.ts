/**
 * Subset of the frozen CDA contract DTOs (SH1) consumed by the SH52 search page.
 */
export interface ShCdaPostSummary {
  id: string
  title: string
  summary: string
  furl: string
  postType: string
  date: string
  modifiedDate: string
}

export interface ShCdaListing {
  folderId: string | null
  folders: unknown[]
  posts: ShCdaPostSummary[]
  page: number
  size: number
  totalPosts: number
}
