import { Button } from "@/components/ui/button"
import { ChevronRight, FileText, Folder, Search, Upload, X } from "lucide-react"
import { useQuery } from "@tanstack/react-query"
import { addressKey } from "@/lib/query-keys"
import type { ShRead } from "@/lib/read-options"
import { useCallback, useMemo, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import axios from "axios"
import { toast } from "@viglet/viglet-design-system"
import type { WidgetProps } from "./index"
import { ContentBrowserDialog, type ContentBrowserSelection } from "./content-browser-dialog"
import { isObjectReference } from "@/lib/object-reference"
import { Spinner } from "@/components/spinner"

interface PathSegment {
  id: string
  name: string
}

/**
 * Where an unconfigured File field uploaded to, for the rest of this session (SH1035).
 *
 * Module-level, so it outlives the widget unmounting when the form navigates and dies with the
 * tab — which is what "for the session" means to the person doing it. Keyed by the post-type
 * attribute, because two File fields on one type are two questions: a hero image and a press kit
 * do not belong in the same folder, and remembering one answer for both would silently file the
 * second in the wrong place.
 *
 * NOT persisted. The durable answer is the field's own `widgetSettings.folderId`, which an
 * administrator sets once for everybody; this is the stopgap that stops a curator being unable to
 * finish a page, and writing it to storage would let a session's guess outlive the session that
 * made it and quietly become the configuration.
 */
const REMEMBERED_UPLOAD_FOLDER = new Map<string, { id: string; name: string }>()

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

async function resolveFullPath(fileId: string, read: ShRead = {}): Promise<ResolvedFile | null> {
  if (!fileId) return null
  // SH660 — this widget renders two different things. On another post's field the value is a
  // reference the browser wrote (`selection.id`); on the `File` type's own `FILE` field it is the
  // stored file NAME the uploader wrote. Resolving the second issued two 404s per opened file,
  // both swallowed by the `.catch` below, so the page returned 200 and only the network tab knew.
  if (!isObjectReference(fileId)) return null

  const postRes = await axios.get(`/v2/post/xp/${fileId}`, { signal: read.signal }).catch(() => null)
  if (postRes?.data?.shPost) {
    const post = postRes.data.shPost
    const folderId = post.shFolder?.id
    const pathSegments: PathSegment[] = []

    if (folderId) {
      const pathRes = await axios.get(`/v2/object/${folderId}/path`, { signal: read.signal }).catch(() => null)
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

export function FileWidget({ attr, onChange, labelledBy, describedBy }: WidgetProps) {
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
  // SH1035 — the file the curator chose before we knew where to put it. Held rather than dropped:
  // asking them to pick the folder and then pick the file again is the dead end with extra steps.
  const [pendingUpload, setPendingUpload] = useState<File | null>(null)
  const [folderPickerOpen, setFolderPickerOpen] = useState(false)

  const settings = useMemo(() => parseSettings(attr.shPostTypeAttr?.widgetSettings), [attr.shPostTypeAttr?.widgetSettings])

  const openBrowserAt = (folderId?: string) => {
    setBrowserStartId(folderId ?? resolved?.parentFolderId ?? settings.folderId)
    setBrowserOpen(true)
  }

  /*
   * SH1153 — one read per file, cancelled by the signal `useQuery` hands it.
   *
   * The `let alive = true` flag this replaced is what SH1047 says the signal is for: it *ignored* a
   * superseded answer, where the signal stops the server being asked for it. The flag also could not
   * tell a cancellation from a failure, so both landed on `{ file: null }` — "this reference resolves
   * to nothing" and "we stopped asking" rendered identically, and the first is what the widget draws
   * a missing-file state for.
   */
  const resolvedPath = useQuery<ResolvedFile | null>({
    queryKey: addressKey(`summary:file|${fileId}`),
    queryFn: ({ signal }) => resolveFullPath(fileId, { signal }),
    enabled: Boolean(fileId),
  })

  // Set during render rather than in an effect (`react-hooks/set-state-in-effect`), once per file.
  const [seeded, setSeeded] = useState<string | null>(null)
  if (resolvedPath.isSuccess && seeded !== fileId) {
    setSeeded(fileId)
    setEntry({ id: fileId, file: resolvedPath.data })
  }

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

  /** The attribute this widget is, as a key for the remembered destination. */
  const fieldKey = attr.shPostTypeAttr?.id ?? attr.shPostTypeAttr?.name ?? "unkeyed-file-field";

  const uploadFile = useCallback(async (file: File, toFolderId?: string) => {
    const folderId = toFolderId
      ?? settings.folderId
      ?? REMEMBERED_UPLOAD_FOLDER.get(fieldKey)?.id;
    if (!folderId) {
      // SH1035 — this used to be `toast.error(t("content.noUploadFolder"))` and a return, which is
      // a dead end: the field is optional by design, so an unconfigured widget told the curator
      // what was wrong and gave them nothing they could do about it from where they were. The
      // folder setting is an administrator's job and the curator is mid-page.
      //
      // The chosen file is HELD, so answering the question finishes the upload rather than
      // restarting it.
      setPendingUpload(file);
      setFolderPickerOpen(true);
      return;
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
  }, [settings.folderId, fieldKey, onChange, attr, t])

  /**
   * The destination the curator just picked: remembered, then used for the held file.
   *
   * Remembered before the upload rather than after it, so a failed upload does not ask the
   * question again — the answer was about where things go, and it is still the answer whether or
   * not these particular bytes landed.
   */
  const handleUploadFolderSelect = useCallback((selection: ContentBrowserSelection) => {
    REMEMBERED_UPLOAD_FOLDER.set(fieldKey, { id: selection.id, name: selection.name });
    setFolderPickerOpen(false);
    toast.info(t("content.uploadFolderRemembered", { name: selection.name }));
    const held = pendingUpload;
    setPendingUpload(null);
    if (held) void uploadFile(held, selection.id);
  }, [fieldKey, pendingUpload, uploadFile, t])

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
    // SH1052 — a group, not a control: this widget renders several, so the field's `htmlFor` has
    // nothing single to own and the set names itself by the visible label instead.
    <div
      role="group"
      aria-labelledby={labelledBy}
      aria-describedby={describedBy}
      // SH1054 — no aria-required: a group may not carry it, and its name (the label) already says it
      className="space-y-2"
    >
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
          {/*
            SH1052 — the drop zone is a button now, and that is the whole of the fix.

            It was a `div` with an `onClick` over an `<input type="file" className="hidden">`, so a
            keyboard reader had no way to attach a file at all: the div was not focusable, and
            `display: none` — which is what Tailwind's `hidden` is — takes the input out of the
            accessibility tree along with the focus order. Dragging is a pointer gesture and stays
            one; what was missing was the other path, and a native button is it. The label the field
            already has names it through `aria-labelledby`.
          */}
          <button
            type="button"
            aria-labelledby={labelledBy}
            aria-describedby={describedBy}
            disabled={uploading}
            className={`relative flex w-full flex-col items-center justify-center rounded-lg border-2 border-dashed px-4 py-6 transition-colors cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-1 ${
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
                <Spinner labelled className="h-8 w-8 text-primary mb-2" />
                <p className="text-sm text-muted-foreground">{t("content.uploading")}</p>
              </>
            ) : (
              <>
                <Upload className="h-8 w-8 text-muted-foreground/50 mb-2" />
                <p className="text-sm text-muted-foreground">{t("content.dropFileHere")}</p>
                <p className="text-xs text-muted-foreground/60 mt-1">{t("content.orClickToUpload")}</p>
              </>
            )}
          </button>
          {/*
            SH1054 — outside the button, and out of the tab order. Inside it, the input was a focusable
            control nested in another (axe: nested-interactive) and a second tab stop for the same act.
            The button is the keyboard path (SH1052); `sr-only` rather than `hidden` keeps `.click()` working.
          */}
          <input
            ref={fileInputRef}
            type="file"
            className="sr-only"
            tabIndex={-1}
            aria-hidden="true"
            onChange={handleFileInput}
          />

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
      {/*
        SH1035 — the destination picker, folders only. Its own dialog rather than a mode on the one
        above: that one is open when the curator is choosing an existing file, this one when they
        have chosen new bytes and we do not know where to put them, and the two can be reached from
        the same click in sequence.
      */}
      <ContentBrowserDialog
        open={folderPickerOpen}
        onOpenChange={(open) => {
          setFolderPickerOpen(open)
          // Dismissed without choosing: drop the held file rather than leaving it to be uploaded
          // by the next unrelated pick.
          if (!open) setPendingUpload(null)
        }}
        onSelect={handleUploadFolderSelect}
        startFolderId={settings.folderId}
        selectMode="folder"
      />
    </div>
  )
}
