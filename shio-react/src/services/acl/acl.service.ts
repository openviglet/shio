import axios from "@/lib/axios"
import type { ShRead } from "@/lib/read-options"
import type { ShAllowed, ShContentAcl } from "@/models/acl/acl.model"

/** Client for the SH54 content-ACL endpoints (`/api/v2/object/{id}/acl`). */
export class ShAclService {
  async get(objectId: string, read: ShRead = {}): Promise<ShContentAcl[]> {
    const response = await axios.get<ShContentAcl[]>(`/v2/object/${objectId}/acl`, { signal: read.signal })
    return response.data
  }

  async replace(objectId: string, entries: ShContentAcl[]): Promise<ShContentAcl[]> {
    const response = await axios.put<ShContentAcl[]>(`/v2/object/${objectId}/acl`, entries)
    return response.data
  }

  /**
   * SH995 — what the signed-in user may do with this object, and the server's own sentence for why
   * Publish is not offered. Asked so a control can be disabled with an explanation rather than
   * offered and answered with a 403.
   */
  async allowed(objectId: string): Promise<ShAllowed> {
    const response = await axios.get<ShAllowed>(`/v2/object/${objectId}/allowed`)
    return response.data
  }
}
