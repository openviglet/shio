export type ShAclPrincipalType = "USER" | "GROUP" | "ROLE"
/** SH995 — PUBLISH implies WRITE implies READ, the order the backend enum declares. */
export type ShAclPermission = "READ" | "WRITE" | "PUBLISH"

/**
 * SH995 — what the current user may do with one object.
 *
 * `publishHint` is the server's own refusal sentence, and it is present only when `publish` is false:
 * a hint beside a permitted action is something a reader has to work out is not about them.
 */
export interface ShAllowed {
  read: boolean
  write: boolean
  publish: boolean
  publishHint?: string | null
}

/** A single content-access grant (SH54), mirroring the backend `ShContentAcl`. */
export interface ShContentAcl {
  id?: string
  objectId?: string
  principalType: ShAclPrincipalType
  principalName: string
  permission: ShAclPermission
}

/**
 * SH1302 — who may read an object's delivered pages, as `folder.visibility` / `post.visibility` set
 * it: `public`, or `members` for these groups (none: any signed-in user). The object's own gate.
 */
export interface ShVisibility {
  visibility: "public" | "members"
  groups: string[]
}
