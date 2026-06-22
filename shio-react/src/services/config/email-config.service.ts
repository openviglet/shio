import axios from "@/lib/axios"
import type { ShEmailConfig } from "@/models/config/email-config.model"

export class ShEmailConfigService {
  async get(): Promise<ShEmailConfig> {
    const response = await axios.get<ShEmailConfig>("/v2/config/email")
    return response.data
  }

  async update(config: ShEmailConfig): Promise<ShEmailConfig> {
    const response = await axios.put<ShEmailConfig>("/v2/config/email", config)
    return response.data
  }
}
