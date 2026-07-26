import axios from "axios"
import type { ShPostState, ShPostUnified } from "@/models/post-unified/post-unified"

/**
 * The create body. The post-type is named either way: `postType` is its API name
 * (SH117 — what the console has after reading the name-keyed `/api/v2/post-type`),
 * `postTypeId` its id, and the server prefers the id when both are sent. Exactly
 * one is required; neither is a teaching 400 listing the installed types.
 */
export interface CreateUnifiedPostRequest {
  folderId: string
  postType?: string
  postTypeId?: string
  data: Record<string, unknown>
  state?: ShPostState
  locale?: string
  translationGroupId?: string
}

export interface UpdateUnifiedPostRequest {
  data: Record<string, unknown>
  state?: ShPostState
}

export class ShPostUnifiedService {
  async get(id: string, state: ShPostState = "PUBLISHED"): Promise<ShPostUnified> {
    const response = await axios.get<ShPostUnified>(`/v2/post-unified/${id}`, {
      params: { state },
    })
    return response.data
  }

  async versions(id: string): Promise<ShPostUnified[]> {
    const response = await axios.get<ShPostUnified[]>(`/v2/post-unified/${id}/versions`)
    return response.data
  }

  async listByFolder(folderId: string, state: ShPostState = "PUBLISHED"): Promise<ShPostUnified[]> {
    const response = await axios.get<ShPostUnified[]>(`/v2/post-unified/folder/${folderId}`, {
      params: { state },
    })
    return response.data
  }

  async create(req: CreateUnifiedPostRequest): Promise<ShPostUnified> {
    const response = await axios.post<ShPostUnified>("/v2/post-unified", req)
    return response.data
  }

  async update(id: string, req: UpdateUnifiedPostRequest): Promise<ShPostUnified> {
    const response = await axios.put<ShPostUnified>(`/v2/post-unified/${id}`, req)
    return response.data
  }

  async delete(id: string): Promise<void> {
    await axios.delete(`/v2/post-unified/${id}`)
  }

  /** Revert the draft to the last published snapshot (SH14). */
  async restore(id: string): Promise<ShPostUnified> {
    const response = await axios.post<ShPostUnified>(`/v2/post-unified/${id}/restore`)
    return response.data
  }

  /** Set or clear the scheduled publish/unpublish timestamps (SH15). null clears. */
  async schedule(
    id: string,
    req: { publishAt: string | null; unpublishAt: string | null }
  ): Promise<ShPostUnified> {
    const response = await axios.post<ShPostUnified>(`/v2/post-unified/${id}/schedule`, req)
    return response.data
  }

  /** Sibling translations of this post's group (SH53), DRAFT-preferred. */
  async translations(id: string): Promise<ShPostUnified[]> {
    const response = await axios.get<ShPostUnified[]>(`/v2/post-unified/${id}/translations`)
    return response.data
  }

  /** Create a linked translation of this post in another locale (SH53). */
  async translate(id: string, locale: string, data?: Record<string, unknown>): Promise<ShPostUnified> {
    const response = await axios.post<ShPostUnified>(`/v2/post-unified/${id}/translate`, { locale, data })
    return response.data
  }

  /** Posts currently in the trash for the active tenant (SH55). */
  async trash(): Promise<ShPostUnified[]> {
    const response = await axios.get<ShPostUnified[]>("/v2/post-unified/trash")
    return response.data
  }

  /** Restore a trashed post (SH55). */
  async untrash(id: string): Promise<void> {
    await axios.post(`/v2/post-unified/${id}/untrash`)
  }

  /** Permanently delete a trashed post (SH55). */
  async purge(id: string): Promise<void> {
    await axios.delete(`/v2/post-unified/${id}/purge`)
  }
}

export class ShPostUnifiedMigrationService {
  async run(): Promise<{ postTypesMigrated: number; publishedMigrated: number; draftMigrated: number }> {
    const response = await axios.post("/v2/migration/unified/run")
    return response.data
  }

  async status(): Promise<{ unifiedRowCount: number }> {
    const response = await axios.get("/v2/migration/unified/status")
    return response.data
  }
}
