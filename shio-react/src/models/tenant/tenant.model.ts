export type ShTenantStatus = "ACTIVE" | "SUSPENDED"

export type ShTenant = {
  id: string
  name: string
  description: string
  enabled: boolean
  status?: ShTenantStatus
  plan?: string
  owner: string
  createdDate: string
}
