import axios from "@/lib/axios"
import type { ShRead } from "@/lib/read-options"
import type { ShAllowed, ShContentAcl, ShVisibility } from "@/models/acl/acl.model"

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
  async allowed(objectId: string, read: ShRead = {}): Promise<ShAllowed> {
    const response = await axios.get<ShAllowed>(`/v2/object/${objectId}/allowed`,
      { signal: read.signal })
    return response.data
  }

  /** SH1302 — the object's own members-only gate (`/api/v2/object/{id}/visibility`). */
  async visibility(objectId: string, read: ShRead = {}): Promise<ShVisibility> {
    const response = await axios.get<ShVisibility>(`/v2/object/${objectId}/visibility`, { signal: read.signal })
    return response.data
  }

  /** SH1302 — replace it; the server refuses a group it does not have, with the reason. */
  async setVisibility(objectId: string, visibility: ShVisibility): Promise<ShVisibility> {
    const response = await axios.put<ShVisibility>(`/v2/object/${objectId}/visibility`, visibility)
    return response.data
  }
}
