import axios from "@/lib/axios"
import type {
  ShAssetRendition, ShStaticFileItem, ShStaticFileFilter, ShStaticFileMeta, ShStaticFileUsage,
} from "@/models/static-file/static-file.model"
import type { ShRead } from "@/lib/read-options"

/** Options for {@link ShStaticFileService.thumbUrl} / transform URLs (SH39–43 pipeline). */
export interface ShTransformOptions {
  w?: number
  h?: number
  /** `fit` (default) | `fill` (cover + center-crop) | `crop` | `scale`. */
  crop?: "fit" | "fill" | "crop" | "scale"
  format?: "jpg" | "png" | "gif" | "webp" | "avif"
}

/**
 * One page of the media library, with the counts a filter bar needs (SH1030).
 *
 * `page`, `size` and a total under the object listing's own names, so a reader who knows one
 * listing knows this one. `facets` are the server's because they have to be: counted in the
 * browser over a page, a library of two thousand images reports fifty, and the chip a curator
 * presses to find the rest is the one telling them there is nothing to find.
 */
export interface ShStaticFileListing {
  items: ShStaticFileItem[]
  page: number
  size: number
  totalFiles: number
  /** Per category, plus `all`, counted ignoring the category filter itself. */
  facets: Record<string, number>
  /** SH1280 — on `loads`, how many pages were rendered to count `loadedBy`, of how many. */
  pagesRendered?: number
  pagesTotal?: number
}

/**
 * Client for the SH38 static-file subsystem, used by the SH62 Media Library
 * page (rebuilt from the SH61 file manager). Listing + upload live here;
 * deletion reuses the unified-post DELETE (deleting the `File` post removes its
 * bytes via `ShStaticFileCleanupListener`).
 */
export class ShStaticFileService {
  /**
   * One page of the library (SH1030).
   *
   * It used to answer with the whole array, and the page then filtered it and counted its own
   * category chips. That worked exactly because it had all of it — and a library big enough to
   * need chips is one where downloading it to draw them is the problem.
   */
  async list(filter?: ShStaticFileFilter, read: ShRead = {}): Promise<ShStaticFileListing> {
    const params = new URLSearchParams()
    if (filter?.folderId) params.set("folderId", filter.folderId)
    if (filter?.siteId) params.set("siteId", filter.siteId)
    if (filter?.category) params.set("category", filter.category)
    if (filter?.q) params.set("q", filter.q)
    if (filter?.page !== undefined) params.set("page", String(filter.page))
    if (filter?.size !== undefined) params.set("size", String(filter.size))
    if (filter?.loads) params.set("loads", "true")
    const qs = params.toString()
    const url = qs ? `/v2/staticfile?${qs}` : "/v2/staticfile"
    const response = await axios.get<ShStaticFileListing>(url, { signal: read.signal })
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

  /** Update editable metadata (alt/caption/tags) of a File post (SH62). Returns the refreshed item. */
  async updateMeta(id: string, meta: ShStaticFileMeta): Promise<ShStaticFileItem> {
    const response = await axios.put<ShStaticFileItem>(`/v2/staticfile/${id}/meta`, meta)
    return response.data
  }

  /**
   * Put new bytes behind an existing asset id (SH1035).
   *
   * The endpoint has existed since SH210 with only a CLI caller; the console's route to new bytes
   * was delete-and-re-upload, which mints a new id and breaks every field pointing at the old one.
   * The file name the multipart carries is ignored by the server — the name is half the asset's
   * address, so a replace cannot move it.
   */
  async replace(id: string, file: File, onProgress?: (percent: number) => void): Promise<ShStaticFileItem> {
    const formData = new FormData()
    formData.append("file", file)
    const response = await axios.post<ShStaticFileItem>(`/v2/staticfile/${id}/replace`, formData, {
      headers: { "Content-Type": "multipart/form-data" },
      onUploadProgress: (e) => {
        if (onProgress && e.total) onProgress(Math.round((e.loaded / e.total) * 100))
      },
    })
    return response.data
  }

  /** The bytes this asset used to have, newest first (SH1035). */
  async renditions(id: string): Promise<ShAssetRendition[]> {
    const response = await axios.get<ShAssetRendition[]>(`/v2/staticfile/${id}/renditions`)
    return response.data
  }

  /** Put a superseded set of bytes back. Reverting captures the current ones first (SH1035). */
  async revert(id: string, number: number): Promise<ShStaticFileItem> {
    const response = await axios.post<ShStaticFileItem>(
      `/v2/staticfile/${id}/renditions/${number}/revert`)
    return response.data
  }

  /** List the posts that reference this file ("used in", SH62). */
  async usage(id: string): Promise<ShStaticFileUsage[]> {
    const response = await axios.get<ShStaticFileUsage[]>(`/v2/staticfile/${id}/usage`)
    return response.data
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
