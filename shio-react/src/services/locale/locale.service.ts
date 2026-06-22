import axios from "@/lib/axios"

/** A content locale from the install's catalog (SH53), mirroring `ShLocale`. */
export interface ShLocale {
  initials: string
  en: string
  pt: string
}

export class ShLocaleService {
  async list(): Promise<ShLocale[]> {
    const response = await axios.get<ShLocale[]>("/v2/locale")
    return response.data
  }
}
