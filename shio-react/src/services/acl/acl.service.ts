import axios from "@/lib/axios"
import type { ShContentAcl } from "@/models/acl/acl.model"

/** Client for the SH54 content-ACL endpoints (`/api/v2/object/{id}/acl`). */
export class ShAclService {
  async get(objectId: string): Promise<ShContentAcl[]> {
    const response = await axios.get<ShContentAcl[]>(`/v2/object/${objectId}/acl`)
    return response.data
  }

  async replace(objectId: string, entries: ShContentAcl[]): Promise<ShContentAcl[]> {
    const response = await axios.put<ShContentAcl[]>(`/v2/object/${objectId}/acl`, entries)
    return response.data
  }
}
