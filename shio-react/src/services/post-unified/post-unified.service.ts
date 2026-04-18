import axios from "axios"
import type { ShPostState, ShPostUnified } from "@/models/post-unified/post-unified"

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
