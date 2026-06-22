import axios from "@/lib/axios"
import type {
  ShExchangeProviderInstance,
  ShExchangeProviderVendor,
} from "@/models/provider/exchange-provider.model"

export class ShExchangeProviderService {
  async list(): Promise<ShExchangeProviderInstance[]> {
    const response = await axios.get<ShExchangeProviderInstance[]>("/v2/provider/exchange")
    return response.data
  }

  async vendors(): Promise<ShExchangeProviderVendor[]> {
    const response = await axios.get<ShExchangeProviderVendor[]>("/v2/provider/exchange/vendor")
    return response.data
  }

  async model(): Promise<ShExchangeProviderInstance> {
    const response = await axios.get<ShExchangeProviderInstance>("/v2/provider/exchange/model")
    return response.data
  }

  async get(id: string): Promise<ShExchangeProviderInstance> {
    const response = await axios.get<ShExchangeProviderInstance>(`/v2/provider/exchange/${id}`)
    return response.data
  }

  async create(instance: ShExchangeProviderInstance): Promise<ShExchangeProviderInstance> {
    const response = await axios.post<ShExchangeProviderInstance>("/v2/provider/exchange", instance)
    return response.data
  }

  async update(instance: ShExchangeProviderInstance): Promise<ShExchangeProviderInstance> {
    const response = await axios.put<ShExchangeProviderInstance>(
      `/v2/provider/exchange/${instance.id}`,
      instance,
    )
    return response.data
  }

  async delete(id: string): Promise<void> {
    await axios.delete(`/v2/provider/exchange/${id}`)
  }
}
