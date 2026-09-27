import axios from "@/lib/axios"
import type { ShRead } from "@/lib/read-options"

export interface SetupStatus {
  required: boolean
}

export class ShSetupService {
  async status(read: ShRead = {}): Promise<SetupStatus> {
    const response = await axios.get<SetupStatus>("/v2/setup", { signal: read.signal })
    return response.data
  }

  async setAdminPassword(password: string): Promise<void> {
    await axios.post("/v2/setup", { password })
  }
}
