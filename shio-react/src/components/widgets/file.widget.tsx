import { Button } from "@/components/ui/button"
import { ChevronRight, FileText, Folder, Loader2, Search, Upload, X } from "lucide-react"
import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import axios from "axios"
import { toast } from "@viglet/viglet-design-system"
import type { WidgetProps } from "./index"
import { ContentBrowserDialog, type ContentBrowserSelection } from "./content-browser-dialog"
import { isObjectReference } from "@/lib/object-reference"

interface PathSegment {
  id: string
  name: string
}

interface ResolvedFile {
  name: string
  path: PathSegment[]
  parentFolderId?: string
  type: "POST" | "FOLDER"
}

function parseSettings(raw?: string): { folderId?: string } {
  if (!raw) return {}
  try {
    const p = JSON.parse(raw)
    return p?.file ?? {}
  } catch {
    return {}
  }
}

async function resolveFullPath(fileId: string): Promise<ResolvedFile | null> {
  if (!fileId) return null
  // SH660 — this widget renders two different things. On another post's field the value is a
  // reference the browser wrote (`selection.id`); on the `File` type's own `FILE` field it is the
  // stored file NAME the uploader wrote. Resolving the second issued two 404s per opened file,
  // both swallowed by the `.catch` below, so the page returned 200 and only the network tab knew.
  if (!isObjectReference(fileId)) return null

  const postRes = await axios.get(`/v2/post/xp/${fileId}`).catch(() => null)
  if (postRes?.data?.shPost) {
    const post = postRes.data.shPost
    const folderId = post.shFolder?.id
    const pathSegments: PathSegment[] = []

    if (folderId) {
      const pathRes = await axios.get(`/v2/object/${folderId}/path`).catch(() => null)
      if (pathRes?.data) {
        const data = pathRes.data
        if (data.site) pathSegments.push({ id: data.site.id, name: data.site.name })
        if (Array.isArray(data.breadcrumb)) {
          for (const c of data.breadcrumb) pathSegments.push({ id: c.id, name: c.name })
        }
      } else {
        if (post.shFolder?.shSite?.name) pathSegments.push({ id: post.shFolder.shSite.id ?? "", name: post.shFolder.shSite.name })
        if (post.shFolder?.name) pathSegments.push({ id: post.shFolder.id, name: post.shFolder.name })
      }
    }

    return { name: post.title, path: pathSegments, parentFolderId: folderId, type: "POST" }
  }

  const folderRes = await axios.get(`/v2/folder/${fileId}`).catch(() => null)
  if (folderRes?.data) {
    const folder = folderRes.data
    const pathSegments: PathSegment[] = []
    const parentId = folder.parentFolder?.id ?? folder.shSite?.id

    if (parentId) {
      const pathRes = await axios.get(`/v2/object/${parentId}/path`).catch(() => null)
      if (pathRes?.data) {
        const data = pathRes.data
        if (data.site) pathSegments.push({ id: data.site.id, name: data.site.name })
        if (Array.isArray(data.breadcrumb)) {
          for (const c of data.breadcrumb) pathSegments.push({ id: c.id, name: c.name })
        }
      }
    }

    return { name: folder.name, path: pathSegments, parentFolderId: parentId, type: "FOLDER" }
  }

  return null
}

export function FileWidget({ attr, onChange }: WidgetProps) {
  const { t } = useTranslation()
  const [browserOpen, setBrowserOpen] = useState(false)
  const [browserStartId, setBrowserStartId] = useState<string | undefined>(undefined)
  /**
   * SH703 — the resolved path, tagged with the id it belongs to.
   *
   * <p>`resolved` and `resolving` are <b>computed</b> from this and from the attribute's current
   * value rather than set by hand. Two things fall out. The effect no longer raises state before
   * its request, which is the lint's objection; and a slow response for a file the curator has
   * already replaced can no longer land on the new one, because a tag that does not match is not
   * shown. Under the flag that was a real race — pick a file, pick another before the first
   * resolves, and the first answer wins the panel.</p>
   */
  const [entry, setEntry] = useState<{ id: string; file: ResolvedFile | null } | null>(null)
  const fileId = attr.strValue ?? ""
  const resolved = entry && entry.id === fileId ? entry.file : null
  const resolving = fileId !== "" && entry?.id !== fileId
  const [uploading, setUploading] = useState(false)
  const [dragOver, setDragOver] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const settings = useMemo(() => parseSettings(attr.shPostTypeAttr?.widgetSettings), [attr.shPostTypeAttr?.widgetSettings])

  const openBrowserAt = (folderId?: string) => {
    setBrowserStartId(folderId ?? resolved?.parentFolderId ?? settings.folderId)
    setBrowserOpen(true)
  }

  // SH703 — no state raised before the request, and the answer is tagged so a late one for a
  // replaced file is ignored rather than shown. `.then` and not `await`: the compiler rule does
  // not model the suspension, so a `setState` after an await reads to it as synchronous.
  useEffect(() => {
    if (!fileId) return
    let alive = true
    void resolveFullPath(fileId)
      .then((file) => { if (alive) setEntry({ id: fileId, file }) })
      .catch(() => { if (alive) setEntry({ id: fileId, file: null }) })
    return () => { alive = false }
  }, [fileId])

  const handleBrowserSelect = (selection: ContentBrowserSelection) => {
    onChange({ ...attr, strValue: selection.id })
    // Tagged with the id being selected, so it is shown the moment the attribute catches up and
    // the effect's own read for the same id is a no-op rather than a second flash.
    setEntry({
      id: selection.id,
      file: {
        name: selection.name,
        path: selection.pathSegments ?? [],
        parentFolderId: selection.parentFolderId,
        type: selection.type,
      },
    })
  }

  const handleClear = () => {
    onChange({ ...attr, strValue: "" })
  }

  // --- Upload logic ---

  const uploadFile = useCallback(async (file: File) => {
    const folderId = settings.folderId
    if (!folderId) {
      toast.error(t("content.noUploadFolder"))
      return
    }

    setUploading(true)
    try {
      const formData = new FormData()
      formData.append("file", file)
      formData.append("folderId", folderId)
      formData.append("createPost", "true")

      const res = await axios.post("/v2/staticfile/upload", formData, {
        headers: { "Content-Type": "multipart/form-data" },
      })

      const createdPost = res.data
      if (createdPost?.id) {
        onChange({ ...attr, strValue: createdPost.id })
        toast.success(t("content.fileUploaded", { name: file.name }))
      }
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
      toast.error(msg || t("common.error"))
    } finally {
      setUploading(false)
    }
  }, [settings.folderId, onChange, attr, t])

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    setDragOver(false)
    const file = e.dataTransfer.files[0]
    if (file) uploadFile(file)
  }, [uploadFile])

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    setDragOver(true)
  }, [])

  const handleDragLeave = useCallback(() => {
    setDragOver(false)
  }, [])

  const handleFileInput = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) uploadFile(file)
    // Reset so the same file can be selected again
    e.target.value = ""
  }, [uploadFile])

  return (
    <div className="space-y-2">
      {attr.strValue ? (
        <div className="rounded-lg border bg-muted/30 overflow-hidden">
          {/* Clickable breadcrumb path */}
          {resolved && resolved.path.length > 0 && (
            <div className="flex items-center gap-1 px-3 py-1.5 text-xs bg-muted/50 border-b flex-wrap">
              {resolved.path.map((seg, i) => (
                <span key={seg.id} className="flex items-center gap-1">
                  {i > 0 && <ChevronRight className="h-3 w-3 text-muted-foreground/50 shrink-0" />}
                  <button
                    type="button"
                    className="text-primary hover:underline hover:text-primary/80 transition-colors"
                    onClick={() => openBrowserAt(seg.id)}
                  >
                    {seg.name}
                  </button>
                </span>
              ))}
            </div>
          )}

          {/* Selected item */}
          <div className="flex items-center gap-3 px-3 py-2.5">
            {resolved?.type === "FOLDER"
              ? <Folder className="h-4.5 w-4.5 text-orange-500 shrink-0" />
              : <FileText className="h-4.5 w-4.5 text-blue-500 shrink-0" />
            }
            <div className="flex-1 min-w-0">
              {resolving ? (
                <p className="text-sm text-muted-foreground">{t("common.loading")}</p>
              ) : resolved ? (
                <p className="text-sm font-medium truncate">{resolved.name}</p>
              ) : (
                <p className="text-sm font-mono text-muted-foreground truncate">{attr.strValue}</p>
              )}
            </div>
            <div className="flex items-center gap-1 shrink-0">
              <Button
                type="button"
                variant="ghost"
                size="icon"
                aria-label={t("common.search")}
                className="h-7 w-7"
                onClick={() => openBrowserAt()}
              >
                <Search className="h-3.5 w-3.5" />
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="icon"
                aria-label={t("common.clear")}
                className="h-7 w-7 text-muted-foreground hover:text-destructive"
                onClick={handleClear}
              >
                <X className="h-3.5 w-3.5" />
              </Button>
            </div>
          </div>
        </div>
      ) : (
        <div className="space-y-2">
          {/* Drop zone */}
          <div
            className={`relative flex flex-col items-center justify-center rounded-lg border-2 border-dashed px-4 py-6 transition-colors cursor-pointer ${
              dragOver
                ? "border-primary bg-primary/5"
                : "border-muted-foreground/25 hover:border-muted-foreground/50"
            } ${uploading ? "pointer-events-none opacity-60" : ""}`}
            onDrop={handleDrop}
            onDragOver={handleDragOver}
            onDragLeave={handleDragLeave}
            onClick={() => fileInputRef.current?.click()}
          >
            {uploading ? (
              <>
                <Loader2 className="h-8 w-8 text-primary mb-2 animate-spin" />
                <p className="text-sm text-muted-foreground">{t("content.uploading")}</p>
              </>
            ) : (
              <>
                <Upload className="h-8 w-8 text-muted-foreground/50 mb-2" />
                <p className="text-sm text-muted-foreground">{t("content.dropFileHere")}</p>
                <p className="text-xs text-muted-foreground/60 mt-1">{t("content.orClickToUpload")}</p>
              </>
            )}
            <input
              ref={fileInputRef}
              type="file"
              className="hidden"
              aria-label={t("content.selectFile")}
              onChange={handleFileInput}
            />
          </div>

          {/* Or select existing */}
          <Button
            type="button"
            variant="outline"
            className="w-full justify-start gap-2 text-muted-foreground"
            onClick={() => openBrowserAt()}
          >
            <Search className="h-4 w-4" />
            {t("content.browseFile")}
          </Button>
        </div>
      )}

      <ContentBrowserDialog
        open={browserOpen}
        onOpenChange={setBrowserOpen}
        onSelect={handleBrowserSelect}
        startFolderId={browserStartId}
        selectMode="file"
      />
    </div>
  )
}
