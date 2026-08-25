import { IconPhoto } from "@tabler/icons-react"
import { AdaptiveEmptyState } from "@/components/adaptive-empty-state"
import { AdaptivePageHeader } from "@/components/adaptive-page-header"
import { AdaptivePanel } from "@/components/adaptive-panel"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Checkbox } from "@/components/ui/checkbox"
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter,
} from "@/components/ui/dialog"
import { ContentBrowserDialog, type ContentBrowserSelection } from "@/components/widgets/content-browser-dialog"
import { ROUTES } from "@/app/routes.const"
import {
  Image as ImageIcon, Download, Trash2, Upload, Loader2, Search, LayoutGrid, List as ListIcon,
  X, Link as LinkIcon, CheckCircle2, AlertCircle, FolderInput, Copy, FileText,
} from "lucide-react"
import { useEffect, useMemo, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "@viglet/viglet-design-system"
import { useSectionChrome } from "@viglet/viglet-design-system/bento"
import { cn } from "@/lib/utils"
import { ShStaticFileService } from "@/services/static-file/static-file.service"
import type { ShStaticFileItem, ShStaticFileUsage } from "@/models/static-file/static-file.model"
import { ShSiteService } from "@/services/site/site.service"
import type { ShSite } from "@/models/site/site"
import { ShPostUnifiedService } from "@/services/post-unified/post-unified.service"
import {
  mediaCategory, mediaCategoryIcon, isImageType, formatBytes,
  SH_MEDIA_CATEGORIES, type ShMediaCategory,
} from "@/lib/media-type"

const service = new ShStaticFileService()
const siteService = new ShSiteService()
const postService = new ShPostUnifiedService()

type UploadStatus = "uploading" | "done" | "error"
interface UploadEntry {
  id: string
  name: string
  percent: number
  status: UploadStatus
}

export default function StaticFileListPage() {
  const { t } = useTranslation()
  const [items, setItems] = useState<ShStaticFileItem[]>([])
  const [sites, setSites] = useState<ShSite[]>([])
  const [targetSite, setTargetSite] = useState<string>("")
  const [loading, setLoading] = useState(true)
  const [view, setView] = useState<"grid" | "list">("grid")
  const [query, setQuery] = useState("")
  const [category, setCategory] = useState<ShMediaCategory | "all">("all")
  const [uploads, setUploads] = useState<UploadEntry[]>([])
  const [dragging, setDragging] = useState(false)
  const [detail, setDetail] = useState<ShStaticFileItem | null>(null)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [folderDialog, setFolderDialog] = useState<"move" | "copy" | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const dragDepth = useRef(0)

  /** The read. It raises no state before the request, which is what lets the mount effect call it. */
  const fetchList = () =>
    service.list().then(setItems).catch(() => setItems([])).finally(() => setLoading(false))

  /** The curator asked — upload, delete, move — so the spinner is a response to them (SH701). */
  const load = () => {
    setLoading(true)
    void fetchList()
  }

  useEffect(() => {
    void fetchList()
    siteService.query().then((s) => {
      setSites(s)
      if (s.length > 0) setTargetSite((prev) => prev || s[0].id)
    }).catch(() => setSites([]))
  }, [t])

  // Counts per category over the full (unfiltered) set, for the filter chips.
  const counts = useMemo(() => {
    const map: Record<string, number> = { all: items.length }
    for (const it of items) {
      const c = mediaCategory(it.contentType)
      map[c] = (map[c] ?? 0) + 1
    }
    return map
  }, [items])

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    return items.filter((it) => {
      if (category !== "all" && mediaCategory(it.contentType) !== category) return false
      if (q && !(it.fileName ?? "").toLowerCase().includes(q)) return false
      return true
    })
  }, [items, query, category])

  const uploadOne = async (file: File) => {
    const entryId = `${file.name}-${file.size}-${file.lastModified}`
    setUploads((prev) => [
      ...prev.filter((u) => u.id !== entryId),
      { id: entryId, name: file.name, percent: 0, status: "uploading" },
    ])
    try {
      await service.upload(file, targetSite, (percent) =>
        setUploads((prev) => prev.map((u) => (u.id === entryId ? { ...u, percent } : u))))
      setUploads((prev) => prev.map((u) => (u.id === entryId ? { ...u, percent: 100, status: "done" } : u)))
    } catch {
      setUploads((prev) => prev.map((u) => (u.id === entryId ? { ...u, status: "error" } : u)))
      toast.error(t("staticFile.uploadFailed", { name: file.name }))
    }
  }

  const uploadFiles = async (files: FileList | File[]) => {
    if (!targetSite) {
      toast.error(t("staticFile.pickSiteFirst"))
      return
    }
    const list = Array.from(files)
    if (list.length === 0) return
    for (const file of list) await uploadOne(file)
    toast.success(t("staticFile.uploadedCount", { count: list.length }))
    load()
  }

  const handleFileInput = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files) uploadFiles(e.target.files)
    e.target.value = ""
  }

  const onDrop = (e: React.DragEvent) => {
    e.preventDefault()
    dragDepth.current = 0
    setDragging(false)
    if (e.dataTransfer.files?.length) uploadFiles(e.dataTransfer.files)
  }
  const onDragEnter = (e: React.DragEvent) => {
    if (!Array.from(e.dataTransfer.types).includes("Files")) return
    dragDepth.current += 1
    setDragging(true)
  }
  const onDragLeave = () => {
    dragDepth.current = Math.max(0, dragDepth.current - 1)
    if (dragDepth.current === 0) setDragging(false)
  }

  const handleDelete = async (item: ShStaticFileItem) => {
    if (!confirm(t("dialog.deleteMessage"))) return
    await postService.delete(item.id).catch(() => {})
    setItems((prev) => prev.filter((i) => i.id !== item.id))
    setSelected((prev) => { const next = new Set(prev); next.delete(item.id); return next })
    if (detail?.id === item.id) setDetail(null)
    toast.success(t("content.deletedShort"))
  }

  const toggleSelect = (id: string) =>
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id); else next.add(id)
      return next
    })
  const clearSelection = () => setSelected(new Set())
  const selectAllFiltered = () => setSelected(new Set(filtered.map((i) => i.id)))

  const bulkDelete = async () => {
    const ids = [...selected]
    if (ids.length === 0 || !confirm(t("staticFile.confirmBulkDelete", { count: ids.length }))) return
    await Promise.all(ids.map((id) => postService.delete(id).catch(() => {})))
    setItems((prev) => prev.filter((i) => !selected.has(i.id)))
    clearSelection()
    toast.success(t("content.deletedShort"))
  }

  const handleFolderPicked = async (sel: ContentBrowserSelection) => {
    const mode = folderDialog
    setFolderDialog(null)
    if (!mode || sel.type !== "FOLDER") return
    const ids = [...selected]
    if (ids.length === 0) return
    try {
      if (mode === "move") await service.moveTo(sel.id, ids)
      else await service.copyTo(sel.id, ids)
      toast.success(t(mode === "move" ? "staticFile.moved" : "staticFile.copied", { count: ids.length }))
      clearSelection()
      load()
    } catch {
      toast.error(t("common.error"))
    }
  }

  const handleMetaSaved = (updated: ShStaticFileItem) => {
    setItems((prev) => prev.map((i) => (i.id === updated.id ? updated : i)))
    setDetail(updated)
  }

  const copyUrl = (url: string) => {
    const absolute = new URL(url, window.location.origin).toString()
    navigator.clipboard.writeText(absolute).then(
      () => toast.success(t("staticFile.urlCopied")),
      () => toast.error(t("common.error")),
    )
  }

  const activeUploads = uploads.filter((u) => u.status === "uploading").length

  return (
    <>
      <AdaptivePageHeader
        icon={ImageIcon}
        title={t("staticFile.title")}
        subtitle={t("staticFile.description")}
        urlBase={ROUTES.CONTENT_ROOT}
      />
      <div
        className="relative p-6"
        onDrop={onDrop}
        onDragOver={(e) => e.preventDefault()}
        onDragEnter={onDragEnter}
        onDragLeave={onDragLeave}
      >
        {/* Drag-and-drop overlay */}
        {dragging && (
          <div className="absolute inset-3 z-20 rounded-xl border-2 border-dashed border-orange-400 bg-orange-50/80 dark:bg-orange-950/40 flex flex-col items-center justify-center pointer-events-none">
            <Upload className="h-10 w-10 text-orange-500 mb-2" />
            <p className="font-semibold text-orange-700 dark:text-orange-300">{t("staticFile.dropToUpload")}</p>
          </div>
        )}

        {/* Toolbar: title + search + site picker + upload + view toggle */}
        <div className="flex flex-col gap-4 mb-4">
          <div className="flex items-start justify-end gap-3 flex-wrap">
            <div className="flex items-center gap-2">
              <Select value={targetSite} onValueChange={setTargetSite}>
                <SelectTrigger className="w-40">
                  <SelectValue placeholder={t("staticFile.uploadTarget")} />
                </SelectTrigger>
                <SelectContent>
                  {sites.map((site) => (
                    <SelectItem key={site.id} value={site.id}>{site.name}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Button size="sm" disabled={!targetSite || activeUploads > 0}
                onClick={() => fileInputRef.current?.click()}>
                {activeUploads > 0
                  ? <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  : <Upload className="mr-2 h-4 w-4" />}
                {t("staticFile.upload")}
              </Button>
              <input ref={fileInputRef} type="file" multiple className="hidden"
                aria-label={t("staticFile.upload")} onChange={handleFileInput} />
            </div>
          </div>

          <div className="flex items-center gap-3 flex-wrap">
            <div className="relative flex-1 min-w-52 max-w-md">
              <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
              <Input value={query} onChange={(e) => setQuery(e.target.value)}
                placeholder={t("staticFile.searchPlaceholder")} className="pl-8" />
            </div>
            <div className="flex items-center rounded-md border p-0.5">
              <Button variant={view === "grid" ? "secondary" : "ghost"} size="icon" className="h-7 w-7"
                aria-label={t("staticFile.viewGrid")} onClick={() => setView("grid")}>
                <LayoutGrid className="h-4 w-4" />
              </Button>
              <Button variant={view === "list" ? "secondary" : "ghost"} size="icon" className="h-7 w-7"
                aria-label={t("staticFile.viewList")} onClick={() => setView("list")}>
                <ListIcon className="h-4 w-4" />
              </Button>
            </div>
          </div>

          {/* Category filter chips (real, derived from content type) */}
          <div className="flex items-center gap-2 flex-wrap">
            <CategoryChip active={category === "all"} label={t("staticFile.catAll")}
              count={counts.all ?? 0} onClick={() => setCategory("all")} />
            {SH_MEDIA_CATEGORIES.filter((c) => (counts[c] ?? 0) > 0).map((c) => (
              <CategoryChip key={c} active={category === c} label={t(`staticFile.cat_${c}`)}
                count={counts[c] ?? 0} onClick={() => setCategory(c)} />
            ))}
          </div>
        </div>

        {/* Bulk-selection toolbar */}
        {selected.size > 0 && (
          <div className="flex items-center gap-2 flex-wrap mb-4 rounded-lg border bg-orange-50 dark:bg-orange-950/30 px-3 py-2">
            <span className="text-sm font-medium">{t("staticFile.selectedCount", { count: selected.size })}</span>
            <div className="flex-1" />
            <Button variant="outline" size="sm" onClick={selectAllFiltered}>{t("staticFile.selectAll")}</Button>
            <Button variant="outline" size="sm" onClick={() => setFolderDialog("move")}>
              <FolderInput className="mr-2 h-4 w-4" />{t("staticFile.move")}
            </Button>
            <Button variant="outline" size="sm" onClick={() => setFolderDialog("copy")}>
              <Copy className="mr-2 h-4 w-4" />{t("staticFile.copy")}
            </Button>
            <Button variant="outline" size="sm" className="text-destructive" onClick={bulkDelete}>
              <Trash2 className="mr-2 h-4 w-4" />{t("common.delete")}
            </Button>
            <Button variant="ghost" size="sm" onClick={clearSelection}>
              <X className="mr-2 h-4 w-4" />{t("staticFile.clearSelection")}
            </Button>
          </div>
        )}

        {/* Upload queue */}
        {uploads.length > 0 && (
          <AdaptivePanel className="mb-4" contentClassName="p-3 space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium">{t("staticFile.uploadsTitle")}</span>
              <Button variant="ghost" size="sm" className="h-6 text-xs"
                onClick={() => setUploads((prev) => prev.filter((u) => u.status === "uploading"))}>
                {t("staticFile.clearDone")}
              </Button>
            </div>
            {uploads.map((u) => (
              <div key={u.id} className="flex items-center gap-2 text-sm">
                {u.status === "done" && <CheckCircle2 className="h-4 w-4 text-green-500 shrink-0" />}
                {u.status === "error" && <AlertCircle className="h-4 w-4 text-destructive shrink-0" />}
                {u.status === "uploading" && <Loader2 className="h-4 w-4 animate-spin text-muted-foreground shrink-0" />}
                <span className="truncate flex-1">{u.name}</span>
                <div className="w-32 h-1.5 rounded bg-muted overflow-hidden">
                  <div className={cn("h-full transition-all",
                    u.status === "error" ? "bg-destructive" : "bg-orange-500")}
                    style={{ width: `${u.percent}%` }} />
                </div>
              </div>
            ))}
          </AdaptivePanel>
        )}

        {loading ? (
          <div className="text-center py-12 text-muted-foreground">{t("common.loading")}</div>
        ) : filtered.length === 0 ? (
          <EmptyState items={items.length} query={query} category={category} t={t} />
        ) : view === "grid" ? (
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-3">
            {filtered.map((item) => (
              <MediaCard key={item.id} item={item} onOpen={() => setDetail(item)}
                selected={selected.has(item.id)} onToggleSelect={() => toggleSelect(item.id)} />
            ))}
          </div>
        ) : (
          <FileTable items={filtered} onOpen={setDetail} onDelete={handleDelete} t={t}
            selected={selected} onToggleSelect={toggleSelect} />
        )}
      </div>

      {detail && (
        <DetailDialog key={detail.id} item={detail} onClose={() => setDetail(null)}
          onDelete={handleDelete} onCopyUrl={copyUrl} onSaved={handleMetaSaved} t={t} />
      )}

      {folderDialog && (
        <ContentBrowserDialog open onOpenChange={(open) => !open && setFolderDialog(null)}
          onSelect={handleFolderPicked} />
      )}
    </>
  )
}

function CategoryChip({ active, label, count, onClick }: {
  active: boolean; label: string; count: number; onClick: () => void
}) {
  return (
    <button type="button" onClick={onClick}
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-medium transition-colors",
        active
          ? "border-orange-400 bg-orange-50 text-orange-700 dark:bg-orange-950/40 dark:text-orange-300"
          : "border-border text-muted-foreground hover:bg-muted",
      )}>
      {label}
      <span className="rounded-full bg-muted px-1.5 text-[10px] tabular-nums">{count}</span>
    </button>
  )
}

/**
 * One file, as a tile.
 *
 * SH903 — this is the one surface in the slice that is **already** a mosaic, so it is the one
 * place the bento layer's tile classes belong rather than a panel. It was never a `Card`: it is a
 * hand-rolled `rounded-lg border bg-background` container, which is why a grep for console chrome
 * did not find it and the census counted this page as carrying one `Card` when it renders a grid
 * of forty. `bento-tile bento-glass` is what `BentoTile` itself puts on its root — the same
 * classes, not a copy of the look — and the frost is what `checkBentoSurface` reads.
 *
 * Not `BentoTile` itself: this tile carries a thumbnail, a hover-revealed selection checkbox and
 * an aspect-locked preview, none of which that component's children slot expresses.
 */
function MediaCard({ item, onOpen, selected, onToggleSelect }: {
  item: ShStaticFileItem; onOpen: () => void; selected: boolean; onToggleSelect: () => void
}) {
  const image = isImageType(item.contentType)
  const Icon = mediaCategoryIcon(mediaCategory(item.contentType))
  const chrome = useSectionChrome()
  return (
    <div
      className={cn(
        "group relative flex flex-col overflow-hidden transition-all",
        chrome === "bento"
          ? "bento-tile bento-glass rounded-2xl"
          : "rounded-lg border bg-background hover:shadow-md",
        selected ? "border-orange-400 ring-2 ring-orange-300" : "hover:border-orange-300",
      )}>
      {/* Selection checkbox (always visible when selected, on hover otherwise) */}
      <div className={cn("absolute left-2 top-2 z-10 transition-opacity",
        selected ? "opacity-100" : "opacity-0 group-hover:opacity-100")}
        onClick={(e) => e.stopPropagation()}>
        <Checkbox checked={selected} onCheckedChange={onToggleSelect}
          className="bg-background/90 border-muted-foreground" />
      </div>
      <button type="button" onClick={onOpen} className="flex flex-col text-left">
        <div className="aspect-square bg-muted/50 flex items-center justify-center overflow-hidden">
          {image ? (
            <img src={service.thumbUrl(item)} alt={item.fileName ?? ""} loading="lazy"
              className="h-full w-full object-cover group-hover:scale-105 transition-transform" />
          ) : (
            <Icon className="h-10 w-10 text-muted-foreground" />
          )}
        </div>
        <div className="p-2">
          <p className="truncate text-xs font-medium" title={item.fileName ?? item.id}>
            {item.fileName ?? item.id}
          </p>
          <p className="text-[10px] text-muted-foreground">{formatBytes(item.size)}</p>
        </div>
      </button>
    </div>
  )
}

function FileTable({ items, onOpen, onDelete, t, selected, onToggleSelect }: {
  items: ShStaticFileItem[]
  onOpen: (item: ShStaticFileItem) => void
  onDelete: (item: ShStaticFileItem) => void
  t: (key: string) => string
  selected: Set<string>
  onToggleSelect: (id: string) => void
}) {
  return (
    <AdaptivePanel contentClassName="p-0">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-10"></TableHead>
              <TableHead className="w-14"></TableHead>
              <TableHead>{t("staticFile.fileName")}</TableHead>
              <TableHead>{t("staticFile.type")}</TableHead>
              <TableHead>{t("staticFile.size")}</TableHead>
              <TableHead>{t("content.siteLabel")}</TableHead>
              <TableHead>{t("common.owner")}</TableHead>
              <TableHead className="w-24">{t("common.actions")}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {items.map((item) => (
              <TableRow key={item.id} className={cn("cursor-pointer", selected.has(item.id) && "bg-orange-50 dark:bg-orange-950/20")}
                onClick={() => onOpen(item)}>
                <TableCell onClick={(e) => e.stopPropagation()}>
                  <Checkbox checked={selected.has(item.id)} onCheckedChange={() => onToggleSelect(item.id)}
                    aria-label={item.fileName ?? item.id} />
                </TableCell>
                <TableCell>
                  {isImageType(item.contentType) ? (
                    <img src={service.thumbUrl(item, 72)} alt={item.fileName ?? ""}
                      className="h-9 w-9 rounded object-cover border" loading="lazy" />
                  ) : (
                    <div className="h-9 w-9 rounded border flex items-center justify-center bg-muted">
                      {(() => {
                        const Icon = mediaCategoryIcon(mediaCategory(item.contentType))
                        return <Icon className="h-4 w-4 text-muted-foreground" />
                      })()}
                    </div>
                  )}
                </TableCell>
                <TableCell className="font-medium">
                  <span className="truncate max-w-xs block">{item.fileName ?? item.id}</span>
                </TableCell>
                <TableCell>
                  <Badge variant="outline" className="font-mono text-xs">{item.contentType ?? "—"}</Badge>
                </TableCell>
                <TableCell className="text-muted-foreground">{formatBytes(item.size)}</TableCell>
                <TableCell className="text-muted-foreground">{item.siteName ?? "—"}</TableCell>
                <TableCell className="text-muted-foreground">{item.owner ?? "—"}</TableCell>
                <TableCell onClick={(e) => e.stopPropagation()}>
                  <div className="flex gap-1">
                    {/*
                      SH922 — the anchor IS the control, so it carries the styling and there is
                      no button inside it. A `<button>` nested in an `<a>` is interactive content
                      inside interactive content: invalid markup, and on a rendered instance it
                      was two tab stops on every one of 169 rows, the second announcing nothing
                      because only the anchor was named. The classes below are what `Button`
                      variant="ghost" size="icon" was drawing.
                    */}
                    <a href={service.fileUrl(item)} download={item.fileName ?? undefined}
                      target="_blank" rel="noopener noreferrer"
                      aria-label={t("staticFile.download")} title={t("staticFile.download")}
                      className="hover:bg-accent hover:text-accent-foreground focus-visible:ring-ring inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-md transition-colors focus-visible:ring-1 focus-visible:outline-none">
                      <Download className="h-4 w-4" />
                    </a>
                    <Button variant="ghost" size="icon" className="h-8 w-8 text-destructive"
                      onClick={() => onDelete(item)}>
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
    </AdaptivePanel>
  )
}

/**
 * The two empties this page can render, which is one more than search or the trash has.
 *
 * A file list showing nothing means one of two different things, and the difference is the whole
 * value of the message: **no files at all** (a fresh site — the answer is to upload one) or **a
 * filter that excluded them** (files exist, the query or the category chip is hiding them — the
 * answer is to widen it). Telling a curator "no files" while forty sit behind an active chip is
 * worse than silence, because it reads as data loss.
 *
 * `filteredOut` is what tells them apart, and it needs `items` — the unfiltered count — rather
 * than the filtered one the caller is already rendering. That distinction is the reason this stays
 * a named component after SH903 moved the markup into `AdaptiveEmptyState`: the shape is shared,
 * this decision is not.
 */
function EmptyState({ items, query, category, t }: {
  items: number; query: string; category: string; t: (key: string) => string
}) {
  const filteredOut = items > 0 && (query.trim() !== "" || category !== "all")
  return (
    <AdaptiveEmptyState
      icon={IconPhoto}
      tone={filteredOut ? "slate" : "amber"}
      title={filteredOut ? t("staticFile.noMatches") : t("staticFile.blankTitle")}
      description={filteredOut ? t("staticFile.noMatchesHint") : t("staticFile.blankDescription")}
    />
  )
}

function DetailDialog({ item, onClose, onDelete, onCopyUrl, onSaved, t }: {
  item: ShStaticFileItem
  onClose: () => void
  onDelete: (item: ShStaticFileItem) => void
  onCopyUrl: (url: string) => void
  onSaved: (item: ShStaticFileItem) => void
  t: (key: string, opts?: Record<string, unknown>) => string
}) {
  const image = isImageType(item.contentType)
  const Icon = mediaCategoryIcon(mediaCategory(item.contentType))
  const [alt, setAlt] = useState(item.alt ?? "")
  const [caption, setCaption] = useState(item.caption ?? "")
  const [tags, setTags] = useState<string[]>(item.tags ?? [])
  const [tagDraft, setTagDraft] = useState("")
  const [saving, setSaving] = useState(false)
  const [usage, setUsage] = useState<ShStaticFileUsage[] | null>(null)

  useEffect(() => {
    service.usage(item.id).then(setUsage).catch(() => setUsage([]))
  }, [item.id])

  const dirty = alt !== (item.alt ?? "") || caption !== (item.caption ?? "")
    || tags.join("\u0000") !== (item.tags ?? []).join("\u0000")

  const addTag = () => {
    const v = tagDraft.trim()
    if (v && !tags.includes(v)) setTags([...tags, v])
    setTagDraft("")
  }

  const save = async () => {
    setSaving(true)
    try {
      const updated = await service.updateMeta(item.id, { alt, caption, tags })
      toast.success(t("common.saved"))
      onSaved(updated)
    } catch {
      toast.error(t("common.error"))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="truncate pr-6">{item.fileName ?? item.id}</DialogTitle>
          <DialogDescription>{item.contentType ?? t("staticFile.unknownType")}</DialogDescription>
        </DialogHeader>

        <div className="rounded-lg border bg-muted/30 flex items-center justify-center overflow-hidden max-h-80">
          {image ? (
            <img src={service.transformUrl(item, { w: 800, format: "webp" })}
              alt={item.alt ?? item.fileName ?? ""} className="max-h-80 w-auto object-contain" />
          ) : (
            <div className="flex flex-col items-center gap-2 py-12 text-muted-foreground">
              <Icon className="h-12 w-12" />
              <span className="text-xs">{item.contentType ?? t("staticFile.unknownType")}</span>
            </div>
          )}
        </div>

        <dl className="grid grid-cols-2 gap-x-4 gap-y-1.5 text-sm">
          <Meta label={t("staticFile.size")} value={formatBytes(item.size)} />
          {item.width && item.height && (
            <Meta label={t("staticFile.dimensions")} value={`${item.width} × ${item.height} px`} />
          )}
          <Meta label={t("content.siteLabel")} value={item.siteName ?? "—"} />
          <Meta label={t("staticFile.folder")} value={item.folderName ?? "—"} />
          <Meta label={t("common.owner")} value={item.owner ?? "—"} />
          <Meta label={t("staticFile.uploaded")} value={item.date ? new Date(item.date).toLocaleString() : "—"} />
          <Meta label="ID" value={item.id} mono />
        </dl>

        {/* Editable metadata (SH62) */}
        <div className="space-y-3 border-t pt-3">
          <div className="space-y-1">
            <Label htmlFor="sf-alt">{t("staticFile.altText")}</Label>
            <Input id="sf-alt" value={alt} onChange={(e) => setAlt(e.target.value)}
              placeholder={t("staticFile.altPlaceholder")} />
          </div>
          <div className="space-y-1">
            <Label htmlFor="sf-caption">{t("staticFile.caption")}</Label>
            <Textarea id="sf-caption" rows={2} value={caption} onChange={(e) => setCaption(e.target.value)}
              placeholder={t("staticFile.captionPlaceholder")} />
          </div>
          <div className="space-y-1">
            <Label htmlFor="sf-tags">{t("staticFile.tags")}</Label>
            <div className="flex flex-wrap gap-1.5 mb-1">
              {tags.map((tag) => (
                <Badge key={tag} variant="secondary" className="gap-1">
                  {tag}
                  <button type="button" aria-label={`${t("staticFile.removeTag")} ${tag}`}
                    onClick={() => setTags(tags.filter((x) => x !== tag))}>
                    <X className="h-3 w-3" />
                  </button>
                </Badge>
              ))}
            </div>
            <Input id="sf-tags" value={tagDraft} onChange={(e) => setTagDraft(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); addTag() } }}
              placeholder={t("staticFile.tagsPlaceholder")} />
          </div>
        </div>

        {/* Used in (SH62) */}
        <div className="border-t pt-3">
          <p className="text-sm font-medium mb-2">
            {t("staticFile.usedIn")}
            {usage && usage.length > 0 && (
              <Badge variant="secondary" className="ml-2">{usage.length}</Badge>
            )}
          </p>
          {usage === null ? (
            <p className="text-sm text-muted-foreground">{t("common.loading")}</p>
          ) : usage.length === 0 ? (
            <p className="text-sm text-muted-foreground">{t("staticFile.usedInNone")}</p>
          ) : (
            <ul className="space-y-1 max-h-40 overflow-y-auto">
              {usage.map((u) => (
                <li key={u.postId} className="flex items-center gap-2 text-sm">
                  <FileText className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
                  <span className="truncate flex-1">{u.title || u.postId}</span>
                  {u.postTypeName && <Badge variant="outline" className="text-xs">{u.postTypeName}</Badge>}
                  {u.siteName && <span className="text-xs text-muted-foreground">{u.siteName}</span>}
                </li>
              ))}
            </ul>
          )}
        </div>

        <DialogFooter className="flex-wrap gap-2 sm:justify-between">
          <div className="flex gap-2">
            <Button variant="outline" size="sm" onClick={() => onCopyUrl(service.fileUrl(item))}>
              <LinkIcon className="mr-2 h-4 w-4" />{t("staticFile.copyUrl")}
            </Button>
            {/*
              SH922 — the same shape as the row action, in the detail panel. The browser probe
              that measured 169 nested pairs never reached this one, because it is behind a
              selection; the source scan is what found it. That is the division of labour between
              the two — a run measures what a rule is worth, and a scan finds the rest of it.
            */}
            <a href={service.fileUrl(item)} download={item.fileName ?? undefined}
              target="_blank" rel="noopener noreferrer"
              className="border-input hover:bg-accent hover:text-accent-foreground focus-visible:ring-ring inline-flex h-8 items-center justify-center rounded-md border px-3 text-sm font-medium transition-colors focus-visible:ring-1 focus-visible:outline-none">
              <Download className="mr-2 h-4 w-4" />{t("staticFile.download")}
            </a>
          </div>
          <div className="flex gap-2">
            <Button variant="ghost" size="sm" className="text-destructive" onClick={() => onDelete(item)}>
              <Trash2 className="mr-2 h-4 w-4" />{t("common.delete")}
            </Button>
            <Button size="sm" disabled={!dirty || saving} onClick={save}>
              {saving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
              {t("common.save")}
            </Button>
            <Button variant="secondary" size="sm" onClick={onClose}>
              <X className="mr-2 h-4 w-4" />{t("common.close")}
            </Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function Meta({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <>
      <dt className="text-muted-foreground">{label}</dt>
      <dd className={cn("truncate", mono && "font-mono text-xs")} title={value}>{value}</dd>
    </>
  )
}
