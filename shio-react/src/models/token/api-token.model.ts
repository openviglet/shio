/** SH106 added `AGENT`: writes on the agent surface, never on the CDA write path. */
export type ShApiTokenScope = "READ" | "PREVIEW" | "WRITE" | "AGENT"
export type ShApiTokenEnv = "PROD" | "PREVIEW"

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
  env: ShApiTokenEnv
  /** SH107 — whether this credential may publish on the agent surface. Off unless granted. */
  mayPublish?: boolean
}
