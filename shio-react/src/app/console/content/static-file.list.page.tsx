import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter,
} from "@/components/ui/dialog"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import {
  Image as ImageIcon, Download, Trash2, Upload, Loader2, Search, LayoutGrid, List as ListIcon,
  X, Link as LinkIcon, CheckCircle2, AlertCircle,
} from "lucide-react"
import { useEffect, useMemo, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "@viglet/viglet-design-system"
import { cn } from "@/lib/utils"
import { ShStaticFileService } from "@/services/static-file/static-file.service"
import type { ShStaticFileItem } from "@/models/static-file/static-file.model"
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
  const { resetBreadcrumb } = useBreadcrumb()
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
  const fileInputRef = useRef<HTMLInputElement>(null)
  const dragDepth = useRef(0)

  const load = () => {
    setLoading(true)
    service.list().then(setItems).catch(() => setItems([])).finally(() => setLoading(false))
  }

  useEffect(() => {
    resetBreadcrumb([
      { label: t("content.sites"), href: ROUTES.CONTENT_ROOT },
      { label: t("staticFile.title") },
    ])
    load()
    siteService.query().then((s) => {
      setSites(s)
      if (s.length > 0) setTargetSite((prev) => prev || s[0].id)
    }).catch(() => setSites([]))
  }, [resetBreadcrumb, t])

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
    if (detail?.id === item.id) setDetail(null)
    toast.success(t("content.deletedShort"))
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
      <PageHeader title={t("staticFile.title")} turIcon={ImageIcon} urlBase={ROUTES.CONTENT_ROOT} />
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
          <div className="flex items-start justify-between gap-3 flex-wrap">
            <div>
              <h2 className="text-lg font-semibold">{t("staticFile.title")}</h2>
              <p className="text-sm text-muted-foreground">{t("staticFile.description")}</p>
            </div>
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

        {/* Upload queue */}
        {uploads.length > 0 && (
          <Card className="mb-4">
            <CardContent className="p-3 space-y-2">
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
            </CardContent>
          </Card>
        )}

        {loading ? (
          <div className="text-center py-12 text-muted-foreground">{t("common.loading")}</div>
        ) : filtered.length === 0 ? (
          <EmptyState items={items.length} query={query} category={category} t={t} />
        ) : view === "grid" ? (
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-3">
            {filtered.map((item) => (
              <MediaCard key={item.id} item={item} onOpen={() => setDetail(item)} />
            ))}
          </div>
        ) : (
          <FileTable items={filtered} onOpen={setDetail} onDelete={handleDelete} t={t} />
        )}
      </div>

      <DetailDialog item={detail} onClose={() => setDetail(null)}
        onDelete={handleDelete} onCopyUrl={copyUrl} t={t} />
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

function MediaCard({ item, onOpen }: { item: ShStaticFileItem; onOpen: () => void }) {
  const image = isImageType(item.contentType)
  const Icon = mediaCategoryIcon(mediaCategory(item.contentType))
  return (
    <button type="button" onClick={onOpen}
      className="group flex flex-col overflow-hidden rounded-lg border bg-background text-left hover:shadow-md hover:border-orange-300 transition-all">
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
  )
}

function FileTable({ items, onOpen, onDelete, t }: {
  items: ShStaticFileItem[]
  onOpen: (item: ShStaticFileItem) => void
  onDelete: (item: ShStaticFileItem) => void
  t: (key: string) => string
}) {
  return (
    <Card>
      <CardContent className="p-0">
        <Table>
          <TableHeader>
            <TableRow>
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
              <TableRow key={item.id} className="cursor-pointer" onClick={() => onOpen(item)}>
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
                    <a href={service.fileUrl(item)} download={item.fileName ?? undefined}
                      target="_blank" rel="noopener noreferrer"
                      aria-label={t("staticFile.download")} title={t("staticFile.download")}>
                      <Button variant="ghost" size="icon" className="h-8 w-8">
                        <Download className="h-4 w-4" />
                      </Button>
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
      </CardContent>
    </Card>
  )
}

function EmptyState({ items, query, category, t }: {
  items: number; query: string; category: string; t: (key: string) => string
}) {
  const filteredOut = items > 0 && (query.trim() !== "" || category !== "all")
  return (
    <div className="flex flex-col items-center justify-center py-16 text-center">
      <div className="rounded-full bg-muted p-4 mb-4">
        <ImageIcon className="h-8 w-8 text-muted-foreground" />
      </div>
      <h3 className="font-semibold text-lg mb-1">
        {filteredOut ? t("staticFile.noMatches") : t("staticFile.blankTitle")}
      </h3>
      <p className="text-sm text-muted-foreground max-w-sm">
        {filteredOut ? t("staticFile.noMatchesHint") : t("staticFile.blankDescription")}
      </p>
    </div>
  )
}

function DetailDialog({ item, onClose, onDelete, onCopyUrl, t }: {
  item: ShStaticFileItem | null
  onClose: () => void
  onDelete: (item: ShStaticFileItem) => void
  onCopyUrl: (url: string) => void
  t: (key: string, opts?: Record<string, unknown>) => string
}) {
  if (!item) return null
  const image = isImageType(item.contentType)
  const Icon = mediaCategoryIcon(mediaCategory(item.contentType))
  return (
    <Dialog open={!!item} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle className="truncate pr-6">{item.fileName ?? item.id}</DialogTitle>
          <DialogDescription>{item.contentType ?? t("staticFile.unknownType")}</DialogDescription>
        </DialogHeader>

        <div className="rounded-lg border bg-muted/30 flex items-center justify-center overflow-hidden max-h-80">
          {image ? (
            <img src={service.transformUrl(item, { w: 800, format: "webp" })}
              alt={item.fileName ?? ""} className="max-h-80 w-auto object-contain" />
          ) : (
            <div className="flex flex-col items-center gap-2 py-12 text-muted-foreground">
              <Icon className="h-12 w-12" />
              <span className="text-xs">{item.contentType ?? t("staticFile.unknownType")}</span>
            </div>
          )}
        </div>

        <dl className="grid grid-cols-2 gap-x-4 gap-y-1.5 text-sm">
          <Meta label={t("staticFile.size")} value={formatBytes(item.size)} />
          <Meta label={t("content.siteLabel")} value={item.siteName ?? "—"} />
          <Meta label={t("staticFile.folder")} value={item.folderName ?? "—"} />
          <Meta label={t("common.owner")} value={item.owner ?? "—"} />
          <Meta label={t("staticFile.uploaded")} value={item.date ? new Date(item.date).toLocaleString() : "—"} />
          <Meta label="ID" value={item.id} mono />
        </dl>

        <DialogFooter className="flex-wrap gap-2 sm:justify-between">
          <div className="flex gap-2">
            <Button variant="outline" size="sm" onClick={() => onCopyUrl(service.fileUrl(item))}>
              <LinkIcon className="mr-2 h-4 w-4" />{t("staticFile.copyUrl")}
            </Button>
            <a href={service.fileUrl(item)} download={item.fileName ?? undefined}
              target="_blank" rel="noopener noreferrer">
              <Button variant="outline" size="sm">
                <Download className="mr-2 h-4 w-4" />{t("staticFile.download")}
              </Button>
            </a>
          </div>
          <div className="flex gap-2">
            <Button variant="ghost" size="sm" className="text-destructive" onClick={() => onDelete(item)}>
              <Trash2 className="mr-2 h-4 w-4" />{t("common.delete")}
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
