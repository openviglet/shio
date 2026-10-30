import { IconCopy, IconDownload, IconFolderShare, IconPhoto, IconTrash } from "@tabler/icons-react"
import {
  BentoDataTable, type BentoDataTableAction, type BentoDataTableColumn,
} from "@viglet/viglet-design-system/bento"
import { EmptyState } from "@/components/empty-state"
import { MediaCategoryIcon } from "@/components/media-category-icon"
import { PageHero } from "@/components/page-hero"
import { Panel } from "@/components/panel"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Checkbox } from "@/components/ui/checkbox"
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter,
} from "@/components/ui/dialog"
import { ContentBrowserDialog, type ContentBrowserSelection } from "@/components/widgets/content-browser-dialog"
import { ROUTES } from "@/app/routes.const"
import { Download, Trash2, Upload, Search, LayoutGrid, List as ListIcon, X, Link as LinkIcon, CheckCircle2, AlertCircle, FolderInput, Copy, FileText, History } from "lucide-react"
import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
// SH1023 — the search, the category chip and the view mode live in the URL.
import { useListState } from "@/hooks/use-list-state"
import { toast } from "@viglet/viglet-design-system"
import { cn } from "@/lib/utils"
import { ShStaticFileService } from "@/services/static-file/static-file.service"
import type {
  ShAssetRendition, ShStaticFileItem, ShStaticFileUsage,
} from "@/models/static-file/static-file.model"
import { useSites } from "@/hooks/use-catalogue"
import { isCancelled } from "@/lib/read-options"
import type { ShSite } from "@/models/site/site"
import { ShPostUnifiedService } from "@/services/post-unified/post-unified.service"
import { reportProblem } from "@/lib/problem"
import { deleteEach, refusalSummary } from "@/lib/batch-delete"
import { Spinner } from "@/components/spinner"
import {
  isImageType, formatBytes,
  SH_MEDIA_CATEGORIES, type ShMediaCategory,
} from "@/lib/media-type"

/** SH1046 — one identity while the cached list loads, not one per render. */
const EMPTY_SITES: ShSite[] = []

const service = new ShStaticFileService()
const postService = new ShPostUnifiedService()

type UploadStatus = "uploading" | "done" | "error"
interface UploadEntry {
  id: string
  name: string
  percent: number
  status: UploadStatus
}

/** SH1023 — every key the media library owns, and what each means when the URL says nothing. */
const FILES_LIST = { q: "", category: "all", view: "grid", page: "0", needsAlt: "", loads: "" }

/** SH1030 — assets per page, the value the object listing has defaulted to since SH165. */
const PAGE_SIZE = 50

/**
 * SH1031 — an image nobody has described.
 *
 * <p>The same question `verify`'s `asset-no-alt` asks, spelled here so the chip and the finding
 * cannot disagree about which images need work. A non-image has no alt to miss, which is why this
 * is not simply "alt is blank".</p>
 *
 * <p><b>It narrows the page, not the library</b>, and the count says so by being the page's. A
 * library-wide count would need the server to answer it, and the honest version of that is a facet
 * beside the category ones rather than a number computed here and presented as if it were one.</p>
 */
function undescribed(item: ShStaticFileItem): boolean {
  return isImageType(item.contentType) && !(item.alt ?? "").trim()
}

export default function StaticFileListPage() {
  const { t } = useTranslation()
  const [items, setItems] = useState<ShStaticFileItem[]>([])
  // SH1046 — one cached read under `site:`, shared with every other picker in the console.
  const sites = useSites().data ?? EMPTY_SITES
  /*
   * SH1046 — the chosen destination, or the first site once the cached list lands.
   *
   * Derived rather than synced. Seeding a `useState` from an effect that watches `sites` is the
   * shape SH703 removed one screen over: it renders once with an empty picker, then again with the
   * default, and the effect has to remember not to overwrite a choice the curator has since made.
   * The empty string means "nothing picked yet", so `chosen || sites[0]` is the whole rule.
   */
  const [chosenSite, setTargetSite] = useState<string>("")
  const targetSite = chosenSite || sites[0]?.id || ""
  /**
   * SH1075 — which read the grid is showing, so the spinner is a comparison rather than a flag the
   * mount effect sets. SH701 paid this page off by moving `setLoading(true)` out of that effect;
   * SH1047's abort brought it back, and the lint stayed red in CI while nothing local ran it.
   */
  const [loadedKey, setLoadedKey] = useState<string | null>(null)
  /** The curator asked — upload, delete, move — for the same read again (SH701). */
  const [refreshing, setRefreshing] = useState(false)
  /**
   * SH1023 — the library's view, in its URL.
   *
   * `q` rather than `query`, because that is what the word is called on every other surface. The
   * other two are the console's own: `category` is derived from a content type in the browser and
   * has no REST twin, and a grid and a list are the same files.
   *
   * `targetSite` stays component state on purpose — it is where an upload GOES, not what the page
   * is showing, and a link that carried it would hand somebody a destination they did not pick.
   */
  const list = useListState(FILES_LIST, { text: ["q"] })
  /** SH1030 — one page at a time, and what the whole filtered set holds. */
  const [totalFiles, setTotalFiles] = useState(0)
  const [facets, setFacets] = useState<Record<string, number>>({})
  /** SH1280 — how many pages the server rendered to count page loads, once it was asked. */
  const [pagesRendered, setPagesRendered] = useState<number | null>(null)
  const page = Number.parseInt(list.value.page || "0", 10) || 0
  /** SH1031 — the images with no alt text, narrowed on the page this reader is looking at. */
  const needsAlt = list.value.needsAlt === "true"
  const needsAltCount = items.filter(undescribed).length
  /**
   * SH1280 — also count what rendered pages load. Opt-in because the server renders every page of
   * the sites on screen to answer; until it is asked, a zero reference count reads "unreferenced"
   * rather than "unused", since a theme's font is referenced by nothing and loaded everywhere.
   */
  const countLoads = list.value.loads === "true"
  const view = list.value.view as "grid" | "list"
  const query = list.value.q
  const category = list.value.category as ShMediaCategory | "all"
  const [uploads, setUploads] = useState<UploadEntry[]>([])
  const [dragging, setDragging] = useState(false)
  const [detail, setDetail] = useState<ShStaticFileItem | null>(null)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [folderDialog, setFolderDialog] = useState<"move" | "copy" | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const dragDepth = useRef(0)

  /**
   * The read — one page, filtered and counted by the server (SH1030).
   *
   * It used to ask for the whole library and then filter it here and count the chips over the
   * array. That was correct exactly because it had all of it, and a library big enough to need
   * chips is one where downloading it to draw them is the problem.
   */
  const requestKey = JSON.stringify([query, category, page, countLoads])
  const loading = refreshing || loadedKey !== requestKey

  const fetchList = useCallback((signal?: AbortSignal) =>
    service.list({
      q: query || undefined,
      category: category === "all" ? undefined : category,
      page,
      size: PAGE_SIZE,
      loads: countLoads || undefined,
    }, { signal })
      .then((listing) => {
        setItems(listing.items ?? [])
        setTotalFiles(listing.totalFiles ?? 0)
        setFacets(listing.facets ?? {})
        setPagesRendered(typeof listing.pagesRendered === "number" ? listing.pagesRendered : null)
        setLoadedKey(JSON.stringify([query, category, page, countLoads]))
        setRefreshing(false)
      })
      .catch((reason: unknown) => {
        // SH1047 — a superseded listing is not an empty library. The search box and the category
        // chips both rebuild this callback, so without the guard typing a query would blank the
        // grid on every keystroke that overtook its own request.
        if (isCancelled(reason)) return
        setItems([])
        setTotalFiles(0)
        setFacets({})
        setPagesRendered(null)
        setLoadedKey(JSON.stringify([query, category, page, countLoads]))
        setRefreshing(false)
      }), [category, page, query, countLoads])

  /** The curator asked — upload, delete, move — so the spinner is a response to them (SH701). */
  const load = () => {
    setRefreshing(true)
    void fetchList()
  }

  useEffect(() => {
    const abort = new AbortController()
    void fetchList(abort.signal)
    return () => abort.abort()
  }, [fetchList])

  // SH1030 — the server's counts, over the whole filtered set. Counted here they would describe
  // the page: a library of two thousand images would report fifty, and the chip a curator presses
  // to find the rest would be the one telling them there is nothing to find.
  const counts = facets

  // The rows this page holds. The filtering is the server's now, so there is no second opinion
  // here that could disagree with the count above it — except `needsAlt`, which is declared below
  // as what it is: a narrowing of the page, not of the library.
  const filtered = needsAlt ? items.filter(undescribed) : items

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
    // SH1139 — this swallowed the refusal with `.catch(() => {})`, dropped the row from local state
    // and toasted success, so a file the server refused to delete LOOKED deleted until a reload put
    // it back. The state now moves only on success, which is the half a swallow cannot get right.
    try {
      await postService.delete(item.id)
    } catch (error) {
      reportProblem(error, t("common.error"))
      return
    }
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
    /*
     * SH1139 — the worst of the sixteen, and the same defect SH953 already fixed for the content
     * browser: every failure was swallowed with `.catch(() => {})` and then ONE success toast spoke
     * for the whole selection. A bulk pass in which nothing went reported all of it deleted, and the
     * rows left the list either way.
     *
     * `deleteEach` is that fix, reused rather than written again (SH953 put it in `lib/` precisely
     * so the next caller would not fork it). It also makes the loop sequential, which is what the
     * per-object endpoint wants until Block BD's bulk one lands — a select-all here is capped at a
     * thousand, and a thousand parallel writes against one folder tree was never the plan.
     */
    const outcome = await deleteEach(
      ids.map((id) => ({ id, name: items.find((i) => i.id === id)?.fileName ?? undefined, objectType: "POST" })),
      (target) => postService.delete(target.id),
    )

    if (outcome.refusals.length > 0) {
      toast.error(t("content.itemsRefused", {
        count: outcome.refusals.length,
        reasons: refusalSummary(outcome.refusals),
      }))
    }
    if (outcome.deleted > 0) toast.success(t("content.itemsDeleted", { count: outcome.deleted }))

    // Only what went leaves the list, and only what went is deselected: a row that refused stays
    // where it is so the curator can read the reason and retry without hunting for it again.
    const refused = new Set(outcome.refusals.map((refusal) => refusal.id))
    const gone = new Set(ids.filter((id) => !refused.has(id)))
    setItems((prev) => prev.filter((i) => !gone.has(i.id)))
    setSelected(new Set(refused))
    if (detail && gone.has(detail.id)) setDetail(null)
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
    } catch (error) {
      reportProblem(error, t("common.error"))
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
      (error) => reportProblem(error, t("common.error")),
    )
  }

  const activeUploads = uploads.filter((u) => u.status === "uploading").length

  return (
    <>
      <PageHero
        title={t("staticFile.title")}
        subtitle={t("staticFile.description")}
        urlBase={ROUTES.HOME}
        backLabel={t("home.title")}
        /*
          SH1312 — Upload is the page's primary action, so it sits in the hero's action slot where
          every other page puts its own, and the site beside it is where the upload goes. They
          used to take a row of their own between the title and the search.
        */
        actions={
          <>
            <Select value={targetSite} onValueChange={setTargetSite}>
              <SelectTrigger size="sm" className="w-40" aria-label={t("staticFile.uploadTarget")}>
                <SelectValue placeholder={t("staticFile.uploadTarget")} />
              </SelectTrigger>
              <SelectContent>
                {sites.map((site) => (
                  <SelectItem key={site.id} value={site.id}>{site.name}</SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Button size="sm" disabled={!targetSite} loading={activeUploads > 0}
              onClick={() => fileInputRef.current?.click()}>
              {activeUploads === 0 && <Upload className="mr-2 h-4 w-4" />}
              {t("staticFile.upload")}
            </Button>
            <input ref={fileInputRef} type="file" multiple className="hidden"
              aria-label={t("staticFile.upload")} onChange={handleFileInput} />
          </>
        }
      />
      <div
        className="relative"
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

        {/* Toolbar: search + view toggle, then the chips. */}
        <div className="flex flex-col gap-4 mb-4">
          <div className="flex items-center gap-3 flex-wrap">
            <div className="relative flex-1 min-w-52 max-w-md">
              <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
              <Input value={list.draft.q} onChange={(e) => list.set("q", e.target.value)}
                placeholder={t("staticFile.searchPlaceholder")} className="pl-8" />
            </div>
            <div className="flex items-center rounded-md border p-0.5">
              <Button variant={view === "grid" ? "secondary" : "ghost"} size="icon-sm"
                aria-label={t("staticFile.viewGrid")} onClick={() => list.set("view", "grid")}>
                <LayoutGrid className="h-4 w-4" />
              </Button>
              <Button variant={view === "list" ? "secondary" : "ghost"} size="icon-sm"
                aria-label={t("staticFile.viewList")} onClick={() => list.set("view", "list")}>
                <ListIcon className="h-4 w-4" />
              </Button>
            </div>
          </div>

          {/* Category filter chips (real, derived from content type) */}
          <div className="flex items-center gap-2 flex-wrap">
            <CategoryChip active={category === "all"} label={t("staticFile.catAll")}
              count={counts.all ?? 0} onClick={() => list.set("category", "all")} />
            {SH_MEDIA_CATEGORIES.filter((c) => (counts[c] ?? 0) > 0).map((c) => (
              <CategoryChip key={c} active={category === c} label={t(`staticFile.cat_${c}`)}
                count={counts[c] ?? 0} onClick={() => list.set("category", c)} />
            ))}
            {/*
              SH1031 — the images nobody has described, which `verify` reports as `asset-no-alt`.
              A chip rather than a page of its own: the fix is typing one line per image, and the
              thing that makes fifty of them bearable is that the list narrows to exactly those
              fifty and each card takes its sentence in place.
            */}
            <CategoryChip active={needsAlt} label={t("staticFile.needsAlt")}
              count={needsAltCount}
              onClick={() => list.set("needsAlt", needsAlt ? "" : "true")} />
            {/* SH1280 — the count is the pages the server rendered to answer, once it was asked. */}
            <CategoryChip active={countLoads} label={t("staticFile.countLoads")}
              count={pagesRendered ?? 0}
              onClick={() => list.set("loads", countLoads ? "" : "true")} />
          </div>
        </div>

        {/* Bulk-selection toolbar, for the grid. SH1226 — the rows view uses the table's own bar,
            which carries the same three verbs and whose header checkbox selects every row. */}
        {selected.size > 0 && view === "grid" && (
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
          <Panel className="mb-4" contentClassName="p-3 space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium">{t("staticFile.uploadsTitle")}</span>
              <Button variant="ghost" size="sm" className="text-xs"
                onClick={() => setUploads((prev) => prev.filter((u) => u.status === "uploading"))}>
                {t("staticFile.clearDone")}
              </Button>
            </div>
            {uploads.map((u) => (
              <div key={u.id} className="flex items-center gap-2 text-sm">
                {u.status === "done" && <CheckCircle2 className="h-4 w-4 text-green-500 shrink-0" />}
                {u.status === "error" && <AlertCircle className="h-4 w-4 text-destructive shrink-0" />}
                {u.status === "uploading" && <Spinner className="text-muted-foreground shrink-0" />}
                <span className="truncate flex-1">{u.name}</span>
                <div className="w-32 h-1.5 rounded-full bg-muted overflow-hidden">
                  <div className={cn("h-full transition-all",
                    u.status === "error" ? "bg-destructive" : "bg-orange-500")}
                    style={{ width: `${u.percent}%` }} />
                </div>
              </div>
            ))}
          </Panel>
        )}

        {loading ? (
          <div className="text-center py-12 text-muted-foreground">{t("common.loading")}</div>
        ) : filtered.length === 0 ? (
          <FilesEmptyState items={items.length} query={query} category={category} t={t} />
        ) : view === "grid" ? (
          // SH1312 — `items-start`, or a row stretches a file's compact tile to the photo beside it.
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 items-start gap-3">
            {filtered.map((item) => (
              <MediaCard key={item.id} item={item} onOpen={() => setDetail(item)} altAlways={needsAlt}
                selected={selected.has(item.id)} onToggleSelect={() => toggleSelect(item.id)}
                onAltSaved={(alt) => setItems((rows) =>
                  rows.map((row) => (row.id === item.id ? { ...row, alt } : row)))} t={t} />
            ))}
          </div>
        ) : (
          <FileTable items={filtered} onOpen={setDetail} onDelete={handleDelete} t={t}
            selected={selected} onSelectionChange={(ids) => setSelected(new Set(ids))}
            selectionActions={[
              { id: "move", label: t("staticFile.move"), icon: IconFolderShare,
                onSelect: () => setFolderDialog("move") },
              { id: "copy", label: t("staticFile.copy"), icon: IconCopy,
                onSelect: () => setFolderDialog("copy") },
              { id: "delete", label: t("common.delete"), icon: IconTrash, tone: "destructive",
                onSelect: () => { void bulkDelete() } },
            ]} />
        )}

        {/*
          SH1030 — the page boundary, stated. A library that just stopped at fifty cards would
          look complete, which is the failure mode paging introduces and the one the object
          listing has said out loud since SH165. Numbered rather than "load more": the selection
          is per page here, and a grid that grew under a curator who had ticked six cards would
          make "delete selected" a different set than the one on screen.
        */}
        {!loading && totalFiles > PAGE_SIZE && (
          <div className="flex items-center justify-center gap-3 pt-4">
            <Button variant="outline" size="sm" disabled={page === 0}
              onClick={() => list.set("page", String(page - 1))}>
              {t("common.previous")}
            </Button>
            <span className="text-xs text-muted-foreground">
              {t("staticFile.showing", {
                from: page * PAGE_SIZE + 1,
                to: Math.min((page + 1) * PAGE_SIZE, totalFiles),
                total: totalFiles,
              })}
            </span>
            <Button variant="outline" size="sm"
              disabled={(page + 1) * PAGE_SIZE >= totalFiles}
              onClick={() => list.set("page", String(page + 1))}>
              {t("common.next")}
            </Button>
          </div>
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
    <button type="button" onClick={onClick} aria-pressed={active ? "true" : "false"}
      className={cn(
        // SH1210 — a dense control, so it reads the scale's height and rises with a coarse pointer.
        "inline-flex h-(--vg-control-dense) items-center gap-1.5 rounded-full border px-3 text-xs font-medium transition-colors",
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
 * SH903 — the file list is a mosaic, so this carries the package's tile classes rather than a
 * panel's. `bento-tile bento-glass` is what `BentoTile` itself puts on its root — the same classes,
 * not a copy of the look — and the frost is what `checkBentoSurface` reads.
 *
 * Not `BentoTile` itself: this tile carries a thumbnail, a hover-revealed selection checkbox and
 * an aspect-locked preview, none of which that component's children slot expresses.
 */
function MediaCard({ item, onOpen, selected, onToggleSelect, onAltSaved, altAlways, t }: {
  item: ShStaticFileItem; onOpen: () => void; selected: boolean; onToggleSelect: () => void
  onAltSaved: (alt: string) => void
  /** SH1312 — the alt field stays open while the curator is working through the images that need one. */
  altAlways: boolean
  // SH1034 — options, because the usage badge is pluralised. The dialog's own `t` prop has taken
  // them since SH62; this one had not needed them and a narrower type is not a smaller `t`.
  t: (key: string, opts?: Record<string, unknown>) => string
}) {
  const image = isImageType(item.contentType)
  /**
   * SH1031 — alt text, in place.
   *
   * The dialog has had an alt field since SH62 and it is three clicks away per image: open, type,
   * close. That is fine for one and is why nobody fixes fifty. Here it is one line and Enter, and
   * the card stays where it is — the whole reason the `Needs alt text` chip is worth having.
   */
  const [alt, setAlt] = useState(item.alt ?? "")
  const [saving, setSaving] = useState(false)
  const needsAlt = image && !(item.alt ?? "").trim()

  const saveAlt = async () => {
    const next = alt.trim()
    if (next === (item.alt ?? "").trim()) return
    setSaving(true)
    try {
      await service.updateMeta(item.id, { alt: next })
      onAltSaved(next)
    } catch {
      // Put the field back to what the row still says, so the card never shows a value the
      // instance did not take. A toast here would fire once per card on a bulk pass.
      setAlt(item.alt ?? "")
    } finally {
      setSaving(false)
    }
  }
  return (
    <div
      className={cn(
        "group relative flex flex-col overflow-hidden transition-all",
        "bento-tile bento-glass rounded-2xl",
        selected ? "border-orange-400 ring-2 ring-orange-300" : "hover:border-orange-300",
      )}>
      {/* Selection checkbox: visible when selected, and on hover or keyboard focus otherwise. SH1226 —
          it was hover-only, so a keyboard user tabbed onto a control nobody could see. */}
      {/* eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-static-element-interactions -- only stops the tile's click; the checkbox inside is the control (SH1054) */}
      <div className={cn("absolute left-2 top-2 z-10 transition-opacity",
        selected ? "opacity-100" : "opacity-0 group-hover:opacity-100 group-focus-within:opacity-100")}
        onClick={(e) => e.stopPropagation()}>
        <Checkbox checked={selected} onCheckedChange={onToggleSelect}
          className="bg-background/90 border-muted-foreground" />
      </div>
      <button type="button" onClick={onOpen} className="flex flex-col text-left">
        {/*
          SH1312 — a picture's space for a picture. A font, a document or an archive has no preview,
          and a 290px grey square with a glyph in it read as a broken image: it gets a short band
          that names the kind instead.
        */}
        {image ? (
          <div className="aspect-square bg-muted/50 flex items-center justify-center overflow-hidden">
            <img src={service.thumbUrl(item)} alt={item.fileName ?? ""} loading="lazy"
              className="h-full w-full object-cover group-hover:scale-105 transition-transform" />
          </div>
        ) : (
          <div className="bg-muted flex h-16 items-center gap-2 px-3">
            <MediaCategoryIcon contentType={item.contentType} className="h-5 w-5 shrink-0 text-muted-foreground" />
            <span className="text-muted-foreground font-mono text-xs uppercase">{extensionOf(item)}</span>
          </div>
        )}
        <div className="p-2">
          <p className="truncate text-xs font-medium" title={item.fileName ?? item.id}>
            {item.fileName ?? item.id}
          </p>
          <div className="flex items-center gap-1.5">
            <p className="text-[10px] text-muted-foreground">{formatBytes(item.size)}</p>
            {/*
              SH1034 — how many posts use this asset, on the card rather than only in the dialog.
              The dialog's badge has always been there and it answers one asset at a time, which
              is the wrong shape for "which of these can I clear out". Rendered only when the
              server sent a count (null means nobody asked), and `0` is the value worth showing:
              it is what a prune acts on, so it gets the `outline` treatment rather than being
              hidden as an absence.
            */}
            {/*
              SH1280 — a zero reference count is three answers, and only one of them is "unused":
              rendered pages load it (a theme's font), page loads were not counted, or neither.
            */}
            {typeof item.usageCount === "number" && item.usageCount > 0 && (
              <Badge variant="secondary" className="px-1 py-0 text-[10px] leading-4"
                title={t("staticFile.usageHint", { count: item.usageCount })}>
                {t("staticFile.usageBadge", { count: item.usageCount })}
              </Badge>
            )}
            {item.usageCount === 0 && typeof item.loadedBy === "number" && item.loadedBy > 0 && (
              <Badge variant="secondary" className="px-1 py-0 text-[10px] leading-4"
                title={t("staticFile.loadedHint")}>
                {t("staticFile.loadedBadge", { count: item.loadedBy })}
              </Badge>
            )}
            {item.usageCount === 0 && !(typeof item.loadedBy === "number" && item.loadedBy > 0) && (
              <Badge variant="outline" className="px-1 py-0 text-[10px] leading-4"
                title={typeof item.loadedBy === "number"
                  ? t("staticFile.usageNoneHint")
                  : t("staticFile.unreferencedHint")}>
                {typeof item.loadedBy === "number"
                  ? t("staticFile.usageBadge", { count: 0 })
                  : t("staticFile.unreferencedBadge")}
              </Badge>
            )}
          </div>
        </div>
      </button>
      {/*
        SH1031 — outside the button, because a click in here must not open the dialog. Only on
        images: a font has no alt to miss, which is the same question `asset-no-alt` asks.
      */}
      {/*
        SH1312 — at rest it is a muted mark when the image has no alt, and the field opens on hover
        or keyboard focus. Collapsed rather than hidden, so Tab still reaches it and opens it.
      */}
      {needsAlt && !altAlways && (
        <p className="text-muted-foreground px-2 pb-1 text-[10px] group-hover:hidden group-focus-within:hidden">
          {t("staticFile.needsAlt")}
        </p>
      )}
      {image && (
        // eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-static-element-interactions -- only stops the tile's click; the alt-text input inside is the control (SH1054)
        <div className={cn("px-2 transition-all",
          altAlways ? "pb-2" : "max-h-0 overflow-hidden group-hover:max-h-12 group-hover:pb-2 group-focus-within:max-h-12 group-focus-within:pb-2")}
          onClick={(event) => event.stopPropagation()}>
          <Input
            value={alt}
            disabled={saving}
            aria-label={t("staticFile.altText")}
            placeholder={t("staticFile.altPlaceholder")}
            className={cn("h-(--vg-control-dense) text-[11px]", needsAlt && "border-amber-400")}
            onChange={(event) => setAlt(event.target.value)}
            onBlur={saveAlt}
            onKeyDown={(event) => {
              if (event.key === "Enter") event.currentTarget.blur()
            }}
          />
        </div>
      )}
    </div>
  )
}

/** A file's extension, from its name, for a tile that has no picture to show (SH1312). */
function extensionOf(item: ShStaticFileItem): string {
  const name = item.fileName ?? ""
  const dot = name.lastIndexOf(".")
  return dot > 0 ? name.slice(dot + 1) : (item.contentType?.split("/").pop() ?? "")
}

/** A file's thumbnail, or its kind's icon where it is not an image. */
function FileThumb({ item }: Readonly<{ item: ShStaticFileItem }>) {
  if (isImageType(item.contentType)) {
    return (
      <img src={service.thumbUrl(item, 72)} alt="" loading="lazy"
        className="h-8 w-8 shrink-0 rounded-sm border object-cover" />
    )
  }
  return (
    <div className="bg-muted flex h-8 w-8 shrink-0 items-center justify-center rounded-sm border">
      <MediaCategoryIcon contentType={item.contentType} className="text-muted-foreground h-4 w-4" />
    </div>
  )
}

/** The file's bytes, saved under its own name. A link clicked rather than drawn (SH922's rule). */
function download(item: ShStaticFileItem) {
  const a = document.createElement("a")
  a.href = service.fileUrl(item)
  if (item.fileName) a.download = item.fileName
  a.rel = "noopener noreferrer"
  a.click()
}

/**
 * SH1226 — the rows view on the design system's table.
 *
 * It was shadcn's `Table` with a checkbox column and two icon buttons per row, so it had no sortable
 * header, no row keyboard model and no column menu, and every change to the package's table skipped
 * it (SH1224's debt list). Rows now sort from their headers, Enter opens the detail dialog, Download
 * and Delete are the row's menu, and the selection is the table's own, with the bulk verbs in its
 * bar. The selection stays the page's state, so switching to the grid keeps what was ticked.
 */
function FileTable({ items, onOpen, onDelete, t, selected, onSelectionChange, selectionActions }: {
  items: ShStaticFileItem[]
  onOpen: (item: ShStaticFileItem) => void
  onDelete: (item: ShStaticFileItem) => void
  t: (key: string) => string
  selected: Set<string>
  onSelectionChange: (ids: string[]) => void
  selectionActions: BentoDataTableAction<ShStaticFileItem>[]
}) {
  const columns = useMemo<BentoDataTableColumn<ShStaticFileItem>[]>(() => [
    {
      id: "file", header: t("staticFile.fileName"), width: "minmax(16rem, 3fr)", hideable: false,
      sortValue: (item) => (item.fileName ?? item.id).toLowerCase(),
      cell: (item) => (
        <span className="flex min-w-0 items-center gap-3">
          <FileThumb item={item} />
          <span className="truncate font-medium">{item.fileName ?? item.id}</span>
        </span>
      ),
    },
    {
      id: "type", header: t("staticFile.type"), width: "minmax(9rem, 1fr)",
      sortValue: (item) => item.contentType ?? "",
      cell: (item) => <Badge variant="outline" className="font-mono text-xs">{item.contentType ?? "—"}</Badge>,
    },
    {
      id: "size", header: t("staticFile.size"), width: "minmax(6rem, 0.6fr)",
      sortValue: (item) => item.size ?? 0,
      cell: (item) => <span className="text-muted-foreground">{formatBytes(item.size)}</span>,
    },
    {
      id: "site", header: t("content.siteLabel"),
      sortValue: (item) => (item.siteName ?? "").toLowerCase(),
      cell: (item) => <span className="text-muted-foreground">{item.siteName ?? "—"}</span>,
    },
    {
      id: "owner", header: t("common.owner"),
      sortValue: (item) => (item.owner ?? "").toLowerCase(),
      cell: (item) => <span className="text-muted-foreground">{item.owner ?? "—"}</span>,
    },
  ], [t])

  const rowActions: BentoDataTableAction<ShStaticFileItem>[] = [
    { id: "download", label: t("staticFile.download"), icon: IconDownload, onSelect: ([item]) => download(item) },
    {
      id: "delete", label: t("common.delete"), icon: IconTrash, tone: "destructive",
      onSelect: (rows) => { for (const item of rows) onDelete(item) },
    },
  ]

  return (
    <BentoDataTable
      rows={items}
      getRowId={(item) => item.id}
      getRowLabel={(item) => item.fileName ?? item.id}
      columns={columns}
      label={t("staticFile.title")}
      rowActions={rowActions}
      selectionActions={selectionActions}
      selectedIds={[...selected]}
      onSelectionChange={onSelectionChange}
      onRowOpen={onOpen}
    />
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
 * a named component after SH903 moved the markup into `EmptyState`: the shape is shared,
 * this decision is not. Named for the surface rather than the shape (SH932), because the shared
 * component it wraps is called `EmptyState` now and one name cannot mean both.
 */
function FilesEmptyState({ items, query, category, t }: {
  items: number; query: string; category: string; t: (key: string) => string
}) {
  const filteredOut = items > 0 && (query.trim() !== "" || category !== "all")
  return (
    <EmptyState
      icon={IconPhoto}
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
  const [alt, setAlt] = useState(item.alt ?? "")
  const [caption, setCaption] = useState(item.caption ?? "")
  const [tags, setTags] = useState<string[]>(item.tags ?? [])
  const [tagDraft, setTagDraft] = useState("")
  const [saving, setSaving] = useState(false)
  const [usage, setUsage] = useState<ShStaticFileUsage[] | null>(null)
  // SH1035 — the replace control's own state. `renditions` starts empty rather than null: an
  // instance with the feature off answers an empty list too, so there is nothing for the UI to
  // distinguish and no configuration for it to special-case.
  const [renditions, setRenditions] = useState<ShAssetRendition[]>([])
  const [replacing, setReplacing] = useState(false)
  const replaceInput = useRef<HTMLInputElement>(null)

  useEffect(() => {
    service.usage(item.id).then(setUsage).catch(() => setUsage([]))
    service.renditions(item.id).then(setRenditions).catch(() => setRenditions([]))
  }, [item.id])

  /** Reloads the history after any write that changes the bytes. */
  const refreshRenditions = () =>
    service.renditions(item.id).then(setRenditions).catch(() => setRenditions([]))

  const replaceBytes = async (file: File) => {
    setReplacing(true)
    try {
      const updated = await service.replace(item.id, file)
      toast.success(t("staticFile.replaced"))
      // The parent's row has to be told: the size, the dimensions and the digest all moved, and a
      // listing still showing the old ones is a card that disagrees with the bytes it links to.
      onSaved(updated)
      await refreshRenditions()
    } catch (error) {
      reportProblem(error, t("common.error"))
    } finally {
      setReplacing(false)
    }
  }

  const revertTo = async (number: number) => {
    setReplacing(true)
    try {
      const updated = await service.revert(item.id, number)
      toast.success(t("staticFile.reverted", { number }))
      onSaved(updated)
      // Reverting captures the bytes it replaced, so the list GREW: reloading is what shows the
      // curator that going back is itself undoable.
      await refreshRenditions()
    } catch (error) {
      reportProblem(error, t("common.error"))
    } finally {
      setReplacing(false)
    }
  }

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
    } catch (error) {
      reportProblem(error, t("common.error"))
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
              <MediaCategoryIcon contentType={item.contentType} className="h-12 w-12" />
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

        {/*
          SH1035 — Replace, which the API has had since SH210 and the console did not. The route to
          new bytes was delete-and-re-upload, which mints a new id and breaks every field pointing
          at the old one; the endpoint's own javadoc calls that unsound.

          What is shown around the control is what makes a curator willing to press it: the count
          of pages that change (SH1034's usage, already loaded above) BEFORE the picker, and the
          superseded bytes as numbered renditions with a revert after. Without the second, replace
          is a one-way door and a curator goes back to the route that breaks references.
        */}
        <div className="border-t pt-3">
          <p className="text-sm font-medium mb-2">{t("staticFile.replaceBytes")}</p>
          <p className="text-xs text-muted-foreground mb-2">
            {usage === null
              ? t("common.loading")
              : t("staticFile.replaceImpact", { count: usage.length })}
          </p>
          <div className="flex items-center gap-2 flex-wrap">
            <input ref={replaceInput} type="file" className="hidden"
              onChange={(event) => {
                const picked = event.target.files?.[0]
                // Cleared before the await: the same file chosen twice in a row must fire, and an
                // input that still holds it does not raise `change`.
                event.target.value = ""
                if (picked) void replaceBytes(picked)
              }} />
            <Button variant="outline" size="sm" loading={replacing}
              onClick={() => replaceInput.current?.click()}>
              {t("staticFile.chooseNewBytes")}
            </Button>
            <span className="text-xs text-muted-foreground">
              {t("staticFile.replaceKeepsName", { name: item.fileName ?? item.id })}
            </span>
          </div>
          {renditions.length > 0 && (
            <ul className="mt-2 space-y-1 max-h-40 overflow-y-auto">
              {renditions.map((rendition) => (
                <li key={rendition.number} className="flex items-center gap-2 text-sm">
                  <History className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
                  <span className="flex-1 truncate">
                    {t("staticFile.renditionRow", {
                      number: rendition.number,
                      size: formatBytes(rendition.size),
                    })}
                  </span>
                  <Button variant="ghost" size="sm" disabled={replacing}
                    onClick={() => void revertTo(rendition.number)}>
                    {t("staticFile.revert")}
                  </Button>
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
            <Button size="sm" disabled={!dirty} loading={saving} onClick={save}>
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
