import axios from "@/lib/axios"
import type { ShRead } from "@/lib/read-options"
import type {
  ShAuthProviderInstance,
  ShAuthProviderVendor,
} from "@/models/provider/auth-provider.model"

export class ShAuthProviderService {
  async list(): Promise<ShAuthProviderInstance[]> {
    const response = await axios.get<ShAuthProviderInstance[]>("/v2/provider/auth")
    return response.data
  }

  async vendors(read: ShRead = {}): Promise<ShAuthProviderVendor[]> {
    const response = await axios.get<ShAuthProviderVendor[]>("/v2/provider/auth/vendor",
      { signal: read.signal })
    return response.data
  }

  async model(read: ShRead = {}): Promise<ShAuthProviderInstance> {
    const response = await axios.get<ShAuthProviderInstance>("/v2/provider/auth/model",
      { signal: read.signal })
    return response.data
  }

  async get(id: string, read: ShRead = {}): Promise<ShAuthProviderInstance> {
    const response = await axios.get<ShAuthProviderInstance>(`/v2/provider/auth/${id}`,
      { signal: read.signal })
    return response.data
  }

  async create(instance: ShAuthProviderInstance): Promise<ShAuthProviderInstance> {
    const response = await axios.post<ShAuthProviderInstance>("/v2/provider/auth", instance)
    return response.data
  }

  async update(instance: ShAuthProviderInstance): Promise<ShAuthProviderInstance> {
    const response = await axios.put<ShAuthProviderInstance>(
      `/v2/provider/auth/${instance.id}`,
      instance,
    )
    return response.data
  }

  async delete(id: string): Promise<void> {
    await axios.delete(`/v2/provider/auth/${id}`)
  }
}
