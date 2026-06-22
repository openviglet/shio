export type ShAclPrincipalType = "USER" | "GROUP" | "ROLE"
export type ShAclPermission = "READ" | "WRITE"

/** A single content-access grant (SH54), mirroring the backend `ShContentAcl`. */
export interface ShContentAcl {
  id?: string
  objectId?: string
  principalType: ShAclPrincipalType
  principalName: string
  permission: ShAclPermission
}
