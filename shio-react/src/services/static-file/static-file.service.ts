import axios from "@/lib/axios"
import type { ShStaticFileItem } from "@/models/static-file/static-file.model"

/**
 * Client for the SH38 static-file subsystem, used by the SH61 file-manager
 * page. Listing + upload live here; deletion reuses the unified-post DELETE
 * (deleting the `File` post removes its bytes via `ShStaticFileCleanupListener`).
 */
export class ShStaticFileService {
  async list(): Promise<ShStaticFileItem[]> {
    const response = await axios.get<ShStaticFileItem[]>("/v2/staticfile")
    return response.data
  }

  /**
   * Upload a file. `containerId` may be a folder id or a site id (the backend
   * resolves a site to its root folder). Returns the created `File` post id.
   */
  async upload(file: File, containerId: string): Promise<string> {
    const formData = new FormData()
    formData.append("file", file)
    formData.append("folderId", containerId)
    formData.append("createPost", "true")
    const response = await axios.post<{ id: string }>("/v2/staticfile/upload", formData, {
      headers: { "Content-Type": "multipart/form-data" },
    })
    return response.data.id
  }

  /** Public byte URL (permitAll `/file_source/**`) — usable as an `<img>` src or download href. */
  fileUrl(item: ShStaticFileItem): string {
    return `/file_source/${item.id}/${encodeURIComponent(item.fileName ?? "")}`
  }
}
