export type ShAclPrincipalType = "USER" | "GROUP" | "ROLE"
/** SH995 — PUBLISH implies WRITE implies READ, the order the backend enum declares. */
export type ShAclPermission = "READ" | "WRITE" | "PUBLISH"

/** A single content-access grant (SH54), mirroring the backend `ShContentAcl`. */
export interface ShContentAcl {
  id?: string
  objectId?: string
  principalType: ShAclPrincipalType
  principalName: string
  permission: ShAclPermission
}
