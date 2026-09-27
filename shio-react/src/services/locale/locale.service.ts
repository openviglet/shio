import axios from "@/lib/axios"
import type { ShRead } from "@/lib/read-options"

/** A content locale from the install's catalog (SH53), mirroring `ShLocale`. */
export interface ShLocale {
  initials: string
  en: string
  pt: string
}

export class ShLocaleService {
  async list(read: ShRead = {}): Promise<ShLocale[]> {
    const response = await axios.get<ShLocale[]>("/v2/locale", { signal: read.signal })
    return response.data
  }
}
