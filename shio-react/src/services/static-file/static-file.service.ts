import axios from "@/lib/axios"
import type { ShStaticFileItem, ShStaticFileFilter } from "@/models/static-file/static-file.model"

/** Options for {@link ShStaticFileService.thumbUrl} / transform URLs (SH39–43 pipeline). */
export interface ShTransformOptions {
  w?: number
  h?: number
  /** `fit` (default) | `fill` (cover + center-crop) | `crop` | `scale`. */
  crop?: "fit" | "fill" | "crop" | "scale"
  format?: "jpg" | "png" | "gif" | "webp" | "avif"
}

/**
 * Client for the SH38 static-file subsystem, used by the SH62 Media Library
 * page (rebuilt from the SH61 file manager). Listing + upload live here;
 * deletion reuses the unified-post DELETE (deleting the `File` post removes its
 * bytes via `ShStaticFileCleanupListener`).
 */
export class ShStaticFileService {
  async list(filter?: ShStaticFileFilter): Promise<ShStaticFileItem[]> {
    const params = new URLSearchParams()
    if (filter?.folderId) params.set("folderId", filter.folderId)
    if (filter?.siteId) params.set("siteId", filter.siteId)
    if (filter?.category) params.set("category", filter.category)
    if (filter?.q) params.set("q", filter.q)
    const qs = params.toString()
    const url = qs ? `/v2/staticfile?${qs}` : "/v2/staticfile"
    const response = await axios.get<ShStaticFileItem[]>(url)
    return response.data
  }

  /**
   * Upload a file. `containerId` may be a folder id or a site id (the backend
   * resolves a site to its root folder). Returns the created `File` post id.
   *
   * @param onProgress optional 0–100 progress callback (drives the upload-queue UI).
   */
  async upload(file: File, containerId: string, onProgress?: (percent: number) => void): Promise<string> {
    const formData = new FormData()
    formData.append("file", file)
    formData.append("folderId", containerId)
    formData.append("createPost", "true")
    const response = await axios.post<{ id: string }>("/v2/staticfile/upload", formData, {
      headers: { "Content-Type": "multipart/form-data" },
      onUploadProgress: (e) => {
        if (onProgress && e.total) onProgress(Math.round((e.loaded / e.total) * 100))
      },
    })
    return response.data.id
  }

  /** Move File posts into another folder (SH62 bulk action). Reuses the generic object move. */
  async moveTo(destFolderId: string, ids: string[]): Promise<void> {
    await axios.put(`/v2/object/moveto/${destFolderId}`, ids)
  }

  /** Copy File posts into another folder (SH62 bulk action). Reuses the generic object copy. */
  async copyTo(destFolderId: string, ids: string[]): Promise<void> {
    await axios.put(`/v2/object/copyto/${destFolderId}`, ids)
  }

  /** Public byte URL (permitAll `/file_source/**`) — usable as an `<img>` src or download href. */
  fileUrl(item: Pick<ShStaticFileItem, "id" | "fileName">): string {
    return `/file_source/${item.id}/${encodeURIComponent(item.fileName ?? "")}`
  }

  /**
   * A transformed-image URL on the public `/file_source/**` route (SH39–43:
   * resize/crop/format with disk-cached, ETag'd results). Only meaningful for
   * raster images; harmless for others (the pipeline declines → original bytes).
   */
  transformUrl(item: Pick<ShStaticFileItem, "id" | "fileName">, opts: ShTransformOptions): string {
    const params = new URLSearchParams()
    if (opts.w) params.set("w", String(opts.w))
    if (opts.h) params.set("h", String(opts.h))
    if (opts.crop) params.set("crop", opts.crop)
    if (opts.format) params.set("format", opts.format)
    const qs = params.toString()
    const suffix = qs ? `?${qs}` : ""
    return `${this.fileUrl(item)}${suffix}`
  }

  /** Square cover thumbnail (default 300px, webp) for the gallery grid. */
  thumbUrl(item: Pick<ShStaticFileItem, "id" | "fileName">, size = 300): string {
    return this.transformUrl(item, { w: size, h: size, crop: "fill", format: "webp" })
  }
}
