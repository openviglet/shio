import axios from "@/lib/axios"
import type { ShRead } from "@/lib/read-options"
import type { ShApiToken } from "@/models/token/api-token.model"

export class ShApiTokenService {
  async list(read: ShRead = {}): Promise<ShApiToken[]> {
    const response = await axios.get<ShApiToken[]>("/v2/api-token", { signal: read.signal })
    return response.data
  }

  async get(id: string, read: ShRead = {}): Promise<ShApiToken> {
    const response = await axios.get<ShApiToken>(`/v2/api-token/${id}`, { signal: read.signal })
    return response.data
  }

  async create(token: ShApiToken): Promise<ShApiToken> {
    const response = await axios.post<ShApiToken>("/v2/api-token", token)
    return response.data
  }

  async update(token: ShApiToken): Promise<ShApiToken> {
    const response = await axios.put<ShApiToken>(`/v2/api-token/${token.id}`, token)
    return response.data
  }

  async delete(id: string): Promise<void> {
    await axios.delete(`/v2/api-token/${id}`)
  }
}
