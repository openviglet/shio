import axios from "@/lib/axios"
import type { ShRead } from "@/lib/read-options"
import type { ShTenant } from "@/models/tenant/tenant.model"

/** The request's resolved tenant (GET /v2/tenant/current). */
export interface ShCurrentTenant {
  tenantId: string
  multiTenant: boolean
}

export interface ShProvisionResult {
  tenant: string
  postTypesCreated: number
}

export class ShTenantService {
  async current(): Promise<ShCurrentTenant> {
    const response = await axios.get<ShCurrentTenant>("/v2/tenant/current")
    return response.data
  }

  async list(): Promise<ShTenant[]> {
    const response = await axios.get<ShTenant[]>("/v2/tenant")
    return response.data
  }

  async get(id: string, read: ShRead = {}): Promise<ShTenant> {
    const response = await axios.get<ShTenant>(`/v2/tenant/${id}`, { signal: read.signal })
    return response.data
  }

  async create(tenant: ShTenant): Promise<ShTenant> {
    const response = await axios.post<ShTenant>("/v2/tenant", tenant)
    return response.data
  }

  async update(tenant: ShTenant): Promise<ShTenant> {
    const response = await axios.put<ShTenant>(`/v2/tenant/${tenant.id}`, tenant)
    return response.data
  }

  async delete(id: string): Promise<void> {
    await axios.delete(`/v2/tenant/${id}`)
  }

  /** Suspend a tenant — blocks its members with 403 (SH22/B). */
  async suspend(id: string): Promise<ShTenant> {
    const response = await axios.post<ShTenant>(`/v2/tenant/${id}/suspend`)
    return response.data
  }

  /** Re-activate a suspended tenant (SH22/B). */
  async activate(id: string): Promise<ShTenant> {
    const response = await axios.post<ShTenant>(`/v2/tenant/${id}/activate`)
    return response.data
  }

  /** Re-run post-type provisioning for the tenant (SH22 Part C). */
  async provision(id: string): Promise<ShProvisionResult> {
    const response = await axios.post<ShProvisionResult>(`/v2/tenant/${id}/provision`)
    return response.data
  }

  /** The tenants the current user actively belongs to (SH22/C). */
  async mine(): Promise<ShTenant[]> {
    const response = await axios.get<ShTenant[]>("/v2/tenant/mine")
    return response.data
  }

  /** Switch the session's active tenant by name (SH22/C). */
  async switchTo(name: string): Promise<ShTenant> {
    const response = await axios.post<ShTenant>(`/v2/tenant/${name}/switch`)
    return response.data
  }
}
