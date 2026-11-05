import axios from "@/lib/axios"
import type { ShRead } from "@/lib/read-options"

/** SH1351 — one of the signed-in person's devices, as the list shows it: never the secret. */
export interface ShDevice {
  id: string
  deviceName: string
  createdDate: string
  lastUsedAt: string | null
  revokedAt: string | null
  revokedBy: string | null
  current: boolean
}

/** The one answer that carries the secret; the server never shows it again. */
export interface ShMintedDevice {
  id: string
  deviceName: string
  token: string
  createdDate: string
}

/** SH1351 — the person's own device tokens: mint, list, revoke. */
export class ShDeviceTokenService {
  async list(read: ShRead = {}): Promise<ShDevice[]> {
    const response = await axios.get<ShDevice[]>("/v2/device-token", { signal: read.signal })
    return response.data
  }

  async mint(deviceName: string): Promise<ShMintedDevice> {
    const response = await axios.post<ShMintedDevice>("/v2/device-token", { deviceName })
    return response.data
  }

  async revoke(id: string): Promise<ShDevice> {
    const response = await axios.delete<ShDevice>(`/v2/device-token/${id}`)
    return response.data
  }
}
