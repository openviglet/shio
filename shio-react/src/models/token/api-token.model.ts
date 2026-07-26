/** `AGENT` (SH106) is the credential a coding agent runs on: it writes, but bounded. */
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
  /**
   * The agent grant (SH106/SH107/SH110), meaningful only on an `AGENT` token.
   *
   * Optional rather than defaulted, and sent only when the form shows them: over JSON an
   * omitted field and an explicit `null` are indistinguishable to the server, and of the
   * two readings it takes the safe one — "leave it alone" — so a token being renamed
   * cannot silently lose its publish permission.
   */
  mayPublish?: boolean
  /** Per-session call ceiling. 0 = the instance default, negative = unlimited. */
  maxCalls?: number
  /** Per-session content-write ceiling, same convention. */
  maxWrites?: number
}
