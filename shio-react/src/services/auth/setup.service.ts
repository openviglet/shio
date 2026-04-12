import axios from "@/lib/axios"

export interface SetupStatus {
  required: boolean
}

export class ShSetupService {
  async status(): Promise<SetupStatus> {
    const response = await axios.get<SetupStatus>("/v2/setup")
    return response.data
  }

  async setAdminPassword(password: string): Promise<void> {
    await axios.post("/v2/setup", { password })
  }
}
