export type ShTenantStatus = "ACTIVE" | "SUSPENDED"

export type ShTenant = {
  id: string
  name: string
  description: string
  enabled: boolean
  status?: ShTenantStatus
  owner: string
  createdDate: string
}
