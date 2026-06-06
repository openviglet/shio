export type ShApiTokenScope = "READ" | "PREVIEW" | "WRITE"

export type ShApiToken = {
  id: string
  title: string
  description: string
  token: string
  owner: string
  createdDate: string
  enabled: boolean
  scope: ShApiTokenScope
  siteIds: string[]
}
