import { FileText, Image as ImageIcon, Film, Music, FileArchive, FileType2, type LucideIcon } from "lucide-react"

/**
 * Media categories for the Media Library (SH62). These are *real* categories
 * derived from the file's content type — unlike the legacy "Shio Stock" mockup,
 * whose Photos/Illustration/Vectors/Videos/3D tabs were decorative only.
 */
export type ShMediaCategory = "image" | "video" | "audio" | "document" | "archive" | "other"

/** Order in which categories render as filter chips. */
export const SH_MEDIA_CATEGORIES: ShMediaCategory[] = [
  "image",
  "video",
  "audio",
  "document",
  "archive",
  "other",
]

const DOCUMENT_EXACT = new Set([
  "application/pdf",
  "application/msword",
  "application/vnd.ms-excel",
  "application/vnd.ms-powerpoint",
  "application/rtf",
  "text/csv",
  "text/plain",
  "text/html",
  "application/json",
  "application/xml",
  "text/xml",
])

const ARCHIVE_EXACT = new Set([
  "application/zip",
  "application/x-zip-compressed",
  "application/gzip",
  "application/x-tar",
  "application/x-7z-compressed",
  "application/x-rar-compressed",
  "application/vnd.rar",
])

/** Classify a content type (e.g. `image/png`) into a {@link ShMediaCategory}. */
export function mediaCategory(contentType: string | null | undefined): ShMediaCategory {
  if (!contentType) return "other"
  const ct = contentType.toLowerCase()
  if (ct.startsWith("image/")) return "image"
  if (ct.startsWith("video/")) return "video"
  if (ct.startsWith("audio/")) return "audio"
  if (ARCHIVE_EXACT.has(ct)) return "archive"
  if (
    DOCUMENT_EXACT.has(ct) ||
    ct.startsWith("text/") ||
    ct.includes("officedocument") ||
    ct.includes("opendocument")
  ) {
    return "document"
  }
  return "other"
}

/** Whether the content type is a raster image (thumbnail-able via the transform pipeline). */
export const isImageType = (ct: string | null | undefined): boolean =>
  !!ct && ct.toLowerCase().startsWith("image/")

/** Lucide icon for a category, used as a thumbnail fallback for non-images. */
export function mediaCategoryIcon(category: ShMediaCategory): LucideIcon {
  switch (category) {
    case "image":
      return ImageIcon
    case "video":
      return Film
    case "audio":
      return Music
    case "document":
      return FileType2
    case "archive":
      return FileArchive
    default:
      return FileText
  }
}

/** Human-readable size (binary units); `< 0` renders as an em dash. */
export function formatBytes(bytes: number): string {
  if (bytes < 0) return "—"
  if (bytes < 1024) return `${bytes} B`
  const units = ["KB", "MB", "GB", "TB"]
  let value = bytes / 1024
  let i = 0
  while (value >= 1024 && i < units.length - 1) {
    value /= 1024
    i++
  }
  return `${value.toFixed(1)} ${units[i]}`
}
