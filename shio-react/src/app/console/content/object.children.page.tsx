import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Separator } from "@/components/ui/separator"
import { Checkbox } from "@/components/ui/checkbox"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip"
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from "@/components/ui/dropdown-menu"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { IconFileText } from "@tabler/icons-react"
import { DialogDelete } from "@/components/dialog.delete"
import { PermissionsDialog } from "@/components/permissions.dialog"
import { Folder, FileText, Plus, Download, FolderPlus, Eye, Pencil, Settings, ChevronDown, Copy, Move, Trash2, Search, Upload, LayoutGrid, List, Sheet, RefreshCw, ClipboardCopy, ShieldCheck } from "lucide-react"
import { useEffect, useState, useCallback } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { formatDistanceToNow } from "date-fns"
import { ShPostTypeService } from "@/services/post-type/post-type.service"
import { ShSiteService } from "@/services/site/site.service"
import { ShPreviewTokenService } from "@/services/preview-token/preview-token.service"
import type { ShPostType } from "@/models/post-type/post-type"
import axios from "axios"
import { toast } from "@viglet/viglet-design-system"

const postTypeService = new ShPostTypeService()
const siteService = new ShSiteService()
const previewService = new ShPreviewTokenService()

/**
 * `GET /v2/object/{id}/list` (SH120) — the CDA listing envelope, with rows whose shared
 * fields carry the CDA's names. It used to be `{ shFolders, shPosts, shSite, breadcrumb }`
 * of raw JPA entities; the site and the breadcrumb now come from `/path`, which is the one
 * endpoint that answers "where am I".
 *
 * SH165: the posts are one page of the folder's content — `folders` is still the whole set.
 */
interface ObjectListResponse {
  folderId?: string
  folders: ConsoleFolderRow[]
  posts: ConsolePostRow[]
  page: number
  size: number
  totalPosts: number
}

/** A folder row: the CDA's `{id, name, root}` plus the console's ordering axis. */
interface ConsoleFolderRow {
  id: string
  name: string
  root?: boolean
  date?: string
  position?: number
}

/** A post row: the CDA post summary plus the draft axis a delivery API cannot express. */
interface ConsolePostRow {
  id: string
  title?: string
  summary?: string
  furl?: string
  postType?: string
  locale?: string
  date?: string
  modifiedDate?: string
  postTypeTitle?: string
  published?: boolean
  publishStatus?: string
  state?: string
}

interface BreadcrumbPathItem {
  id: string
  name: string
  type: string
}

interface ObjectChild {
  id: string
  name: string
  date: string
  objectType: "FOLDER" | "POST"
  position?: number
  postTypeName?: string
  publishStatus?: string
}

/** Posts per request. The folder's subfolders come whole regardless (SH165). */
const PAGE_SIZE = 50

/**
 * How many post ids "select everything" will pull in before it stops and says so (SH237).
 *
 * A selection is a list of ids, so selecting everything means holding every id — and an unbounded
 * folder is exactly the response SH165 removed. The cap is high enough that no realistic curator
 * gesture hits it and low enough that hitting it costs a warning rather than the tab.
 */
const SELECT_ALL_CAP = 1000

/**
 * The browser's sort selector → the endpoint's `sort` (SH165).
 *
 * The order used to be applied here, over the whole folder, which was right while the whole
 * folder was in the browser. With the posts paged it has to be the server's, or "sort by date"
 * would order the fifty rows that happen to be loaded and quietly leave the rest where they
 * were. `order` is manual position, which only a folder has — so for the posts underneath it
 * both `order` and `name` mean title.
 */
const POST_SORT: Record<string, string> = { order: "title", name: "title", date: "date" }

export default function ObjectChildrenPage() {
  const { objectId } = useParams() as { objectId: string }
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { resetBreadcrumb } = useBreadcrumb()
  const [listData, setListData] = useState<ObjectListResponse>({ folders: [], posts: [], page: 0, size: PAGE_SIZE, totalPosts: 0 })
  const [loadingMore, setLoadingMore] = useState(false)
  const [appliedQuery, setAppliedQuery] = useState("")
  const [breadcrumbPath, setBreadcrumbPath] = useState<BreadcrumbPathItem[]>([])
  const [selectedItems, setSelectedItems] = useState<Set<string>>(new Set())
  const [lastPostType, setLastPostType] = useState<ShPostType | null>(null)
  const [loading, setLoading] = useState(true)
  const [sortBy, setSortBy] = useState("order")
  const [viewMode, setViewMode] = useState<"list" | "grid" | "spreadsheet">("list")
  const [searchOpen, setSearchOpen] = useState(false)
  const [searchQuery, setSearchQuery] = useState("")
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<ObjectChild | null>(null)
  const [permsTarget, setPermsTarget] = useState<ObjectChild | null>(null)
  const [permsOpen, setPermsOpen] = useState(false)
  const [folderDialogOpen, setFolderDialogOpen] = useState(false)
  const [folderDialogMode, setFolderDialogMode] = useState<"copy" | "move">("copy")
  const [folderDialogIds, setFolderDialogIds] = useState<string[]>([])
  const [folderDialogFolders, setFolderDialogFolders] = useState<Array<{ id: string; name: string }>>([])
  const [folderDialogCurrentId, setFolderDialogCurrentId] = useState<string | null>(null)
  const [folderDialogPath, setFolderDialogPath] = useState<Array<{ id: string; name: string }>>([])
  /**
   * SH203 — the folders in this run that cannot be their own destination.
   *
   * Only folder ids: a post has no subtree, so it can go anywhere. The dialog navigates
   * strictly *down* from the site root, so "is this row inside a folder being moved" needs
   * nothing from the server — a row is unreachable exactly when it is one of these, and
   * disabling it means the walk can never get inside one. Hence no ancestor check: there is
   * no way to arrive below a blocked row in the first place.
   */
  const [folderDialogBlockedIds, setFolderDialogBlockedIds] = useState<Set<string>>(new Set())


  /**
   * The search box talks to the server now, so it is debounced: it used to filter an array
   * already in the browser, where every keystroke was free.
   */
  useEffect(() => {
    const timer = setTimeout(() => setAppliedQuery(searchQuery.trim()), 300)
    return () => clearTimeout(timer)
  }, [searchQuery])

  /** One page of posts, plus the folder's whole subfolder set. */
  const fetchPage = useCallback((page: number) => {
    const params = new URLSearchParams({
      page: String(page),
      size: String(PAGE_SIZE),
      sort: POST_SORT[sortBy] ?? "title",
    })
    if (appliedQuery) params.set("q", appliedQuery)
    return axios.get<ObjectListResponse>(`/v2/object/${objectId}/list?${params}`)
  }, [objectId, sortBy, appliedQuery])

  /** The next page, appended. Only the posts accumulate — the folders are already all here. */
  const loadMore = useCallback(async () => {
    setLoadingMore(true)
    try {
      const res = await fetchPage(listData.page + 1)
      setListData((prev) => ({
        ...res.data,
        folders: res.data.folders ?? prev.folders,
        posts: [...prev.posts, ...(res.data.posts ?? [])],
      }))
    } finally {
      setLoadingMore(false)
    }
  }, [fetchPage, listData.page])

  // Sort and search re-query from page 0: the server owns both, so changing either changes
  // which rows the first page holds.
  useEffect(() => {
    setLoading(true)
    fetchPage(0)
      .then((res) => setListData(res.data))
      .finally(() => setLoading(false))
  }, [fetchPage])

  useEffect(() => {
    setSelectedItems(new Set())

    axios.get(`/v2/object/${objectId}/path`).then((res) => {
      const data = res.data
      const items: BreadcrumbPathItem[] = []
      // SH120: `ShCdaPath` — { site, breadcrumb: [...folders], currentFolder }.
      if (data.site) {
        items.push({ id: data.site.id, name: data.site.name, type: "SITE" })
      }
      for (const crumb of data.breadcrumb ?? []) {
        items.push({ id: crumb.id, name: crumb.name, type: "FOLDER" })
      }
      setBreadcrumbPath(items)
    }).catch(() => {
      setBreadcrumbPath([])
    })

    postTypeService.query().then((pts) => {
      const savedName = localStorage.getItem("sh-last-post-type")
      const saved = pts.find(p => p.name === savedName)
      setLastPostType(saved || pts.find(p => !p.system) || pts[0] || null)
    })
  }, [objectId])

  useEffect(() => {
    const items: { label: string; href?: string }[] = [
      { label: t("content.sites"), href: `${ROUTES.CONSOLE}/content` },
    ]
    for (const crumb of breadcrumbPath) {
      items.push({
        label: crumb.name,
        href: `${ROUTES.CONTENT_ROOT}/object/${crumb.id}`,
      })
    }
    if (items.length > 1) {
      delete items[items.length - 1].href
    }
    resetBreadcrumb(items)
  }, [breadcrumbPath, resetBreadcrumb, t])

  /**
   * Folders first — the whole set, ordered here — then the posts in the order the server
   * returned them (SH165).
   *
   * The two halves are ordered by different parties on purpose, and that is the honest split
   * rather than an inconsistency: the folders are all present, so sorting them locally says
   * something true about the whole set; the posts are a page, so only the server can. Merging
   * the two into one sorted sequence would interleave complete data with partial data and read
   * as a single sorted list, which is exactly the half-migration this task had to avoid.
   */
  const folderItems: ObjectChild[] = [...listData.folders]
    .map((folder) => ({
      id: folder.id,
      name: folder.name,
      date: folder.date ?? "",
      objectType: "FOLDER" as const,
      position: folder.position,
    }))
    .sort((a, b) => {
      if (sortBy === "name") return a.name.localeCompare(b.name)
      if (sortBy === "date") return new Date(b.date).getTime() - new Date(a.date).getTime()
      return (a.position ?? 0) - (b.position ?? 0)
    })

  const postItems: ObjectChild[] = listData.posts.map((post) => ({
    id: post.id,
    name: post.title ?? "",
    date: post.date ?? "",
    objectType: "POST" as const,
    postTypeName: post.postTypeTitle || post.postType,
    publishStatus: post.publishStatus,
  }))

  const combinedItems: ObjectChild[] = [...folderItems, ...postItems]
  const sortedItems = combinedItems
  const loadedPosts = listData.posts.length
  const hasMorePosts = loadedPosts < listData.totalPosts

  const toggleSelect = useCallback((id: string) => {
    setSelectedItems((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }, [])

  /**
   * SH237 — every id of every post that matches, not just the ones on screen.
   *
   * The selection is a list of ids and stays one, so "everything" has to *be* everything: it
   * pages the rest in before selecting. Before this, `Select → Everything` on a folder of five
   * hundred posts selected fifty, the delete dialog said "50 item(s)", and the curator was left
   * believing the folder was empty — the P7 surprise paging introduces if nothing follows it.
   *
   * Past `SELECT_ALL_CAP` it stops and says so rather than pulling an unbounded folder into the
   * browser, which is the response SH165 removed. The number in the dialog is then still the
   * number that will be acted on, which is the one property this has to keep.
   */
  const allMatchingPostIds = useCallback(async (): Promise<string[]> => {
    const ids = listData.posts.map((post) => post.id)
    if (ids.length >= listData.totalPosts) return ids
    setLoadingMore(true)
    try {
      let page = listData.page
      while (ids.length < Math.min(listData.totalPosts, SELECT_ALL_CAP)) {
        page += 1
        const res = await fetchPage(page)
        const rows = res.data.posts ?? []
        if (rows.length === 0) break
        ids.push(...rows.map((post) => post.id))
      }
      if (ids.length < listData.totalPosts) {
        toast.warning(t("content.selectAllCapped", { loaded: ids.length, total: listData.totalPosts }))
      }
      return ids
    } finally {
      setLoadingMore(false)
    }
  }, [fetchPage, listData.page, listData.posts, listData.totalPosts, t])

  const handleSelectAction = async (action: string) => {
    switch (action) {
      case "content":
        setSelectedItems(new Set(await allMatchingPostIds()))
        break
      case "folder":
        // Folders are never paged (SH165), so this one really is all of them.
        setSelectedItems(new Set(combinedItems.filter(i => i.objectType === "FOLDER").map(i => i.id)))
        break
      case "everything":
        setSelectedItems(new Set([
          ...combinedItems.filter(i => i.objectType === "FOLDER").map(i => i.id),
          ...await allMatchingPostIds(),
        ]))
        break
      case "invert":
        // Deliberately the loaded rows: "invert" is a statement about what is on screen, and
        // inverting a set that includes ids nobody has seen is not a gesture anyone means.
        setSelectedItems(prev => {
          const all = new Set(combinedItems.map(i => i.id))
          prev.forEach(id => all.delete(id))
          return all
        })
        break
      case "nothing":
        setSelectedItems(new Set())
        break
    }
  }

  const deleteObject = async (id: string, objectType: string) => {
    if (objectType === "FOLDER") {
      await axios.delete(`/v2/folder/${id}`)
    } else {
      await axios.delete(`/v2/post-unified/${id}`)
    }
  }

  const handleBatchAction = async (action: string) => {
    if (selectedItems.size === 0) return
    if (action === "delete") {
      setDeleteTarget(null)
      setDeleteDialogOpen(true)
    }
  }

  const formatDate = (dateStr: string | undefined) => {
    if (!dateStr) return ""
    try {
      return formatDistanceToNow(new Date(dateStr), { addSuffix: true })
    } catch {
      return ""
    }
  }

  const handleItemClick = (item: ObjectChild) => {
    if (item.objectType === "FOLDER") {
      navigate(`${ROUTES.CONTENT_ROOT}/object/${item.id}`)
    } else {
      navigate(`${ROUTES.CONTENT_ROOT}/post/${item.id}`)
    }
  }

  /**
   * Back to the first page after a write. Re-fetching only the pages already loaded would be
   * a lie after a create or a delete — the rows have shifted — and silently dropping the reader
   * back to page 1 is the behaviour a curator can actually predict.
   */
  const refreshList = async () => {
    const res = await fetchPage(0)
    setListData(res.data)
  }

  const handleClone = async (itemId: string) => {
    await axios.put(`/v2/object/copyto/${objectId}`, [itemId])
    toast.success(t("content.clonedSuccess"))
    await refreshList()
  }

  /**
   * SH179 — "View Site" opens the site's front end.
   *
   * It used to navigate to `ROUTES.PREVIEW`, a path declared in `routes.const.ts` and
   * registered by nothing, so the button rendered an empty page. Where the published site
   * lives is the front end's business and the CMS may not have one configured — saying so is
   * a legitimate answer; a blank page is not.
   */
  const handleViewSite = async () => {
    if (!siteId) return
    try {
      const site = await siteService.get(siteId)
      if (!site.url) {
        toast.error(t("content.siteNoUrl"))
        return
      }
      window.open(site.url, "_blank", "noopener")
    } catch {
      toast.error(t("content.previewError"))
    }
  }

  /** SH179 — "View Page" opens the post's draft through an SH13 preview token. */
  const handleViewPage = async (postId: string) => {
    try {
      const { previewUrl } = await previewService.mint(postId)
      window.open(previewUrl, "_blank", "noopener")
    } catch (error) {
      // 400 = the site has no front-end url configured; anything else = preview unavailable.
      const noUrl = axios.isAxiosError(error) && error.response?.status === 400
      toast.error(t(noUrl ? "content.previewNoUrl" : "content.previewError"))
    }
  }

  /**
   * The move/copy picker's folder list. It asks for `size=1` because it renders no posts and
   * the folders come back whole on any page (SH165) — one row of content instead of fifty, for
   * a dialog that walks a site folder by folder.
   */
  const fetchDialogFolders = (id: string) =>
    axios.get<ObjectListResponse>(`/v2/object/${id}/list?size=1`).then((res) => {
      setFolderDialogFolders((res.data.folders ?? []).map(f => ({ id: f.id, name: f.name })))
    })

  const openFolderDialog = (mode: "copy" | "move", ids: string[]) => {
    setFolderDialogMode(mode)
    setFolderDialogIds(ids)
    // SH203 — both modes, because both refuse this destination: a move would detach the
    // subtree from the site root (SH137) and a copy would recurse into the copies it is
    // making. Reading the folders off `combinedItems` is complete even under SH165 paging
    // and an SH237 select-everything: only *posts* are paged, and child folders come back
    // whole on every page, so every folder that could be in the selection is on screen.
    setFolderDialogBlockedIds(new Set(
      combinedItems.filter(i => i.objectType === "FOLDER" && ids.includes(i.id)).map(i => i.id)
    ))
    // Start at site root
    const startId = siteId || objectId
    setFolderDialogCurrentId(startId)
    setFolderDialogPath([])
    fetchDialogFolders(startId)
    setFolderDialogOpen(true)
  }

  const navigateFolderDialog = (folderId: string, folderName: string) => {
    setFolderDialogCurrentId(folderId)
    setFolderDialogPath(prev => [...prev, { id: folderId, name: folderName }])
    fetchDialogFolders(folderId)
  }

  const goBackFolderDialog = (index: number) => {
    const targetPath = folderDialogPath.slice(0, index)
    const targetId = index === 0 ? (siteId || objectId) : targetPath.at(-1)!.id
    setFolderDialogCurrentId(targetId)
    setFolderDialogPath(targetPath)
    fetchDialogFolders(targetId)
  }

  /**
   * Why the current folder cannot be the destination, or null when it can (SH203).
   *
   * The refusals stay on the server — this only stops the dialog from *offering* them.
   * A picker that lists choices the server will reject teaches the curator that the
   * operation fails at random; the same division SH108's confirm flow uses, where the CLI
   * prints the deletions and the server still demands the token.
   */
  const folderDialogBlockedReason = (() => {
    if (!folderDialogCurrentId) return null
    if (folderDialogBlockedIds.has(folderDialogCurrentId)) return t("content.moveInsideItself")
    if (folderDialogMode === "move" && folderDialogCurrentId === objectId) {
      return t("content.moveSameFolder")
    }
    return null
  })()

  const confirmFolderDialog = async () => {
    if (!folderDialogCurrentId || folderDialogBlockedReason) return
    const endpoint = folderDialogMode === "copy" ? "copyto" : "moveto"
    try {
      await axios.put(`/v2/object/${endpoint}/${folderDialogCurrentId}`, folderDialogIds)
    } catch (error) {
      // SH139: a move now refuses rather than re-slugging a colliding URL, so this
      // dialog has a failure to show. It used to have none, and an unhandled
      // rejection left the dialog open with no explanation.
      const problem = axios.isAxiosError(error) ? error.response?.data : null
      toast.error(problem?.detail || problem?.title || t("content.moveError"))
      return
    }
    toast.success(folderDialogMode === "copy" ? t("content.copiedSuccess") : t("content.movedSuccess"))
    setFolderDialogOpen(false)
    setSelectedItems(new Set())
    await refreshList()
  }

  // SH120: the site comes from /path, whose breadcrumb opens with it. The listing used to
  // repeat it, which meant two endpoints answering "which site is this" — and the listing's
  // copy was the one that went stale first, because it is the response people add fields to.
  const site = breadcrumbPath.find((crumb) => crumb.type === "SITE")
  const siteId = site?.id ?? null
  const siteName = site?.name ?? null
  const folderCount = listData.folders.length
  // The folder's post count, not the loaded one: the stats bar answers "what is in here", and
  // since SH165 those are two different numbers.
  const postCount = listData.totalPosts

  return (
    <>
      <PageHeader title={siteName || t("content.browsing")} turIcon={IconFileText} urlBase={ROUTES.CONTENT_ROOT} />
      <div className="p-4 md:p-6 space-y-4">
        {/* Top action bar: View Site + Site Properties */}
        {siteId && (
          <div className="flex items-center justify-end gap-2">
            <Button
              variant="default"
              size="sm"
              className="bg-green-600 hover:bg-green-700 text-white"
              onClick={handleViewSite}
            >
              <Eye className="mr-2 h-4 w-4" /> {t("content.viewSite")}
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => navigate(`${ROUTES.CONTENT_ROOT}/site/${siteId}`)}
            >
              <Settings className="mr-2 h-4 w-4" /> {t("content.siteProperties")}
            </Button>
          </div>
        )}

        {/* Stats bar */}
        <Card>
          <CardContent className="flex items-center gap-8 py-3 px-6 text-sm text-muted-foreground">
            <div className="flex items-center gap-1.5">
              <Folder className="h-4 w-4" />
              <span>{t("content.folderCount", { count: folderCount })}</span>
            </div>
            <div className="flex items-center gap-1.5">
              <FileText className="h-4 w-4" />
              <span>{t("content.postCount", { count: postCount })}</span>
            </div>
          </CardContent>
        </Card>

        {/* Toolbar: New Article | Grid | New Folder | Import || Order | Select | Action in Batch */}
        <div className="flex flex-wrap items-center gap-2">
          {/* Left actions: New [PostType] + grid selector */}
          <TooltipProvider>
            <div className="flex items-center">
              <Button
                variant="outline"
                size="sm"
                className="rounded-r-none"
                disabled={!lastPostType}
                onClick={() => {
                  if (lastPostType) {
                    localStorage.setItem("sh-last-post-type", lastPostType.name)
                    navigate(`${ROUTES.CONTENT_ROOT}/folder/${objectId}/post-type/${encodeURIComponent(lastPostType.name)}/new`)
                  }
                }}
              >
                <Plus className="mr-2 h-4 w-4" /> {t("content.newItem", { type: lastPostType?.title || lastPostType?.name || t("content.defaultPostType") })}
              </Button>
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button
                    variant="outline"
                    size="icon"
                    className="h-8 w-8 rounded-l-none border-l-0"
                    onClick={() => navigate(`${ROUTES.CONTENT_ROOT}/folder/${objectId}/post-type-select`)}
                  >
                    <LayoutGrid className="h-4 w-4" />
                  </Button>
                </TooltipTrigger>
                <TooltipContent>{t("content.selectPostTypeTooltip")}</TooltipContent>
              </Tooltip>
            </div>
          </TooltipProvider>

          <Button variant="outline" size="sm" onClick={() => navigate(`${ROUTES.CONTENT_ROOT}/folder/${objectId}/new-folder`)}>
            <FolderPlus className="mr-2 h-4 w-4" /> {t("content.newFolder")}
          </Button>

          <Button variant="outline" size="sm" onClick={() => navigate(ROUTES.SITE_IMPORT)}>
            <Upload className="mr-2 h-4 w-4" /> {t("content.import")}
          </Button>

          <div className="flex-1" />

          {/* Right: Sort + Select + Batch + Download */}
          <Select value={sortBy} onValueChange={setSortBy}>
            <SelectTrigger className="w-28 h-8 text-xs"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="order">{t("content.order")}</SelectItem>
              <SelectItem value="name">{t("common.name")}</SelectItem>
              <SelectItem value="date">{t("common.date")}</SelectItem>
            </SelectContent>
          </Select>

          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" size="sm">{t("content.select")} <ChevronDown className="ml-1 h-3.5 w-3.5" /></Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onClick={() => handleSelectAction("content")}><FileText className="mr-2 h-4 w-4 text-green-600" /> {t("content.batchContent")}</DropdownMenuItem>
              <DropdownMenuItem onClick={() => handleSelectAction("folder")}><Folder className="mr-2 h-4 w-4" /> {t("content.folder")}</DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem onClick={() => handleSelectAction("everything")}>{t("content.selectEverything")}</DropdownMenuItem>
              <DropdownMenuItem onClick={() => handleSelectAction("invert")}>{t("content.selectInvert")}</DropdownMenuItem>
              <DropdownMenuItem onClick={() => handleSelectAction("nothing")}>{t("content.selectNothing")}</DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>

          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" size="sm" disabled={selectedItems.size === 0}>{t("content.actionInBatch")} <ChevronDown className="ml-1 h-3.5 w-3.5" /></Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onClick={() => openFolderDialog("copy", [...selectedItems])}><Copy className="mr-2 h-4 w-4" /> {t("content.copy")}</DropdownMenuItem>
              <DropdownMenuItem onClick={() => openFolderDialog("move", [...selectedItems])}><Move className="mr-2 h-4 w-4" /> {t("content.move")}</DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem onClick={() => handleBatchAction("delete")} className="text-destructive"><Trash2 className="mr-2 h-4 w-4" /> {t("common.delete")}</DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>

          {siteId && (
            <Button size="sm" className="bg-green-600 hover:bg-green-700 text-white" onClick={() => window.open(`/api/v2/site/${siteId}/export`, "_blank")}>
              <Download className="mr-2 h-4 w-4" /> {t("content.downloadSite")}
            </Button>
          )}
        </div>

        {/* Sub-toolbar: breadcrumb path + search/view icons */}
        <Card>
          <CardContent className="flex items-center gap-2 py-2 px-4">
            <div className="flex-1 flex items-center gap-1 text-sm text-muted-foreground overflow-hidden">
              {breadcrumbPath.map((crumb, i) => (
                <span key={crumb.id} className="flex items-center gap-1 shrink-0">
                  {i > 0 && <span>/</span>}
                  <button type="button" className="hover:text-foreground hover:underline transition-colors truncate" onClick={() => navigate(`${ROUTES.CONTENT_ROOT}/object/${crumb.id}`)}>
                    {crumb.name}
                  </button>
                </span>
              ))}
            </div>
            <div className="flex items-center gap-1 shrink-0">
              <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => { setSearchOpen(!searchOpen); setSearchQuery("") }}>
                <Search className="h-4 w-4" />
              </Button>
              <Button variant="ghost" size="icon" className={`h-7 w-7 ${viewMode === "list" ? "bg-muted" : ""}`} onClick={() => setViewMode("list")}>
                <List className="h-4 w-4" />
              </Button>
              <Button variant="ghost" size="icon" className={`h-7 w-7 ${viewMode === "grid" ? "bg-muted" : ""}`} onClick={() => setViewMode("grid")}>
                <LayoutGrid className="h-4 w-4" />
              </Button>
              <Button variant="ghost" size="icon" className={`h-7 w-7 ${viewMode === "spreadsheet" ? "bg-muted" : ""}`} onClick={() => setViewMode("spreadsheet")}>
                <Sheet className="h-4 w-4" />
              </Button>
            </div>
          </CardContent>
        </Card>

        {/* Search bar (toggleable) */}
        {searchOpen && (
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <input
              className="w-full pl-10 pr-4 py-2 text-sm border rounded-md bg-background focus:outline-none focus:ring-2 focus:ring-ring"
              placeholder={t("content.filterItems")}
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              autoFocus
            />
          </div>
        )}

        {/* Content list */}
        <Card className="overflow-hidden">
          <CardContent className="p-0">
            {loading && (
              <div className="px-4 py-12 text-center text-sm text-muted-foreground">{t("common.loading")}</div>
            )}

            {!loading && sortedItems.map((item, index) => (
              <div
                key={item.id}
                className={`group border-b last:border-b-0 hover:bg-muted/40 transition-colors ${selectedItems.has(item.id) ? "bg-primary/5" : ""}`}
              >
                <div className="flex items-start gap-3 px-4 py-3">
                  {/* Position badge */}
                  <span className={`mt-0.5 inline-flex items-center justify-center h-6 w-6 rounded-full text-xs font-medium text-white shrink-0 ${item.objectType === "FOLDER" ? "bg-orange-400" : "bg-blue-400"}`}>
                    {index + 1}
                  </span>

                  {/* Checkbox */}
                  <Checkbox
                    className="mt-1 shrink-0"
                    checked={selectedItems.has(item.id)}
                    onCheckedChange={() => toggleSelect(item.id)}
                    aria-label={`Select ${item.name}`}
                  />

                  {/* Icon */}
                  {item.objectType === "FOLDER" ? (
                    <Folder className="h-4 w-4 shrink-0 mt-1 text-amber-500" />
                  ) : (
                    <FileText className="h-4 w-4 shrink-0 mt-1 text-blue-500" />
                  )}

                  {/* Content: name, date, badges */}
                  <div className="flex-1 min-w-0">
                    <button
                      type="button"
                      className="text-left font-medium text-blue-600 dark:text-blue-400 hover:underline transition-colors truncate block"
                      onClick={() => handleItemClick(item)}
                    >
                      {item.name}
                    </button>
                    <div className="text-xs text-muted-foreground mt-0.5">
                      {formatDate(item.date)}
                      {item.objectType === "POST" && item.postTypeName && ` - ${item.postTypeName}`}
                    </div>
                    <div className="flex flex-wrap gap-1 mt-1.5">
                      {item.objectType === "POST" && (
                        <Badge variant="secondary" className="text-[10px] px-1.5 py-0 bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400">
                          {t("content.postLabel")}
                        </Badge>
                      )}
                      <Badge variant="secondary" className="text-[10px] px-1.5 py-0 bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400">
                        {item.objectType === "FOLDER" ? t("content.folder") : item.postTypeName || t("content.postLabel")}
                      </Badge>
                      {siteName && (
                        <Badge variant="secondary" className="text-[10px] px-1.5 py-0 bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400">
                          {siteName}
                        </Badge>
                      )}
                      {item.publishStatus && (
                        <Badge variant="secondary" className="text-[10px] px-1.5 py-0 bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400">
                          {item.publishStatus}
                        </Badge>
                      )}
                    </div>
                  </div>

                  {/* Hover actions - wrap in grid like AngularJS */}
                  <div className="shrink-0 hidden group-hover:flex flex-wrap gap-x-4 gap-y-1 items-start text-xs max-w-70 justify-end">
                    <button type="button" className="flex items-center gap-1 text-muted-foreground hover:text-foreground whitespace-nowrap" onClick={(e) => { e.stopPropagation(); item.objectType === "FOLDER" ? navigate(`${ROUTES.CONTENT_ROOT}/folder/${item.id}/edit`) : navigate(`${ROUTES.CONTENT_ROOT}/post/${item.id}`) }}>
                      <Pencil className="h-3 w-3" /> {t("common.edit")}
                    </button>
                    <button type="button" className="flex items-center gap-1 text-muted-foreground hover:text-foreground whitespace-nowrap" onClick={(e) => { e.stopPropagation(); handleClone(item.id) }}>
                      <ClipboardCopy className="h-3 w-3" /> {t("content.clone")}
                    </button>
                    <button type="button" className="flex items-center gap-1 text-muted-foreground hover:text-foreground whitespace-nowrap" onClick={(e) => { e.stopPropagation(); axios.get(`/v2/object/${item.id}/clear-cache`).then(() => toast.success(t("content.cacheCleared"))) }}>
                      <RefreshCw className="h-3 w-3" /> {t("content.clearCacheAction")}
                    </button>
                    <button type="button" className="flex items-center gap-1 text-muted-foreground hover:text-foreground whitespace-nowrap" onClick={(e) => { e.stopPropagation(); openFolderDialog("copy", [item.id]) }}>
                      <Copy className="h-3 w-3" /> {t("content.copy")}
                    </button>
                    <button type="button" className="flex items-center gap-1 text-muted-foreground hover:text-foreground whitespace-nowrap" onClick={(e) => { e.stopPropagation(); setPermsTarget(item); setPermsOpen(true) }}>
                      <ShieldCheck className="h-3 w-3" /> {t("acl.permissions")}
                    </button>
                    <button type="button" className="flex items-center gap-1 text-destructive/70 hover:text-destructive whitespace-nowrap" onClick={(e) => { e.stopPropagation(); setDeleteTarget(item); setDeleteDialogOpen(true) }}>
                      <Trash2 className="h-3 w-3" /> {t("common.delete")}
                    </button>
                    <button type="button" className="flex items-center gap-1 text-muted-foreground hover:text-foreground whitespace-nowrap" onClick={(e) => { e.stopPropagation(); openFolderDialog("move", [item.id]) }}>
                      <Move className="h-3 w-3" /> {t("content.move")}
                    </button>
                    {item.objectType === "POST" && (
                      <button type="button" className="flex items-center gap-1 text-muted-foreground hover:text-foreground whitespace-nowrap" onClick={(e) => { e.stopPropagation(); handleViewPage(item.id) }}>
                        <Eye className="h-3 w-3" /> {t("content.viewPage")}
                      </button>
                    )}
                  </div>
                </div>
              </div>
            ))}

            {!loading && sortedItems.length === 0 && (
              <div className="px-4 py-16 text-center">
                <Folder className="h-12 w-12 mx-auto text-muted-foreground/30 mb-3" />
                <p className="text-sm text-muted-foreground">{t("common.noResults")}</p>
                <p className="text-xs text-muted-foreground/70 mt-1">{t("content.createFolderOrPost")}</p>
              </div>
            )}

            {/* SH165: the page boundary, stated. A list that just stopped at fifty rows would
                look complete, which is the failure mode paging introduces. */}
            {!loading && hasMorePosts && (
              <div className="flex flex-col items-center gap-2 border-t px-4 py-4">
                <span className="text-xs text-muted-foreground">
                  {t("content.showingPosts", { loaded: loadedPosts, total: listData.totalPosts })}
                </span>
                <Button variant="outline" size="sm" disabled={loadingMore} onClick={loadMore}>
                  {loadingMore ? t("common.loading") : t("content.loadMore")}
                </Button>
              </div>
            )}
          </CardContent>
        </Card>

        {/* Selection info bar */}
        {selectedItems.size > 0 && (
          <div className="flex items-center gap-3 text-sm text-muted-foreground">
            <span>{t("content.itemsSelected", { count: selectedItems.size })}</span>
            <Separator orientation="vertical" className="h-4" />
            <Button variant="ghost" size="sm" className="h-7 text-xs" onClick={() => setSelectedItems(new Set())}>
              {t("content.clearSelection")}
            </Button>
          </div>
        )}
      </div>

      {/* Folder picker dialog for Copy/Move */}
      <Dialog open={folderDialogOpen} onOpenChange={setFolderDialogOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>{folderDialogMode === "copy" ? t("content.copyTo") : t("content.moveTo")}</DialogTitle>
          </DialogHeader>
          {/* Breadcrumb inside dialog */}
          <div className="flex items-center gap-1 text-sm text-muted-foreground flex-wrap">
            <button type="button" className="hover:text-foreground hover:underline" onClick={() => goBackFolderDialog(0)}>
              {t("content.root")}
            </button>
            {folderDialogPath.map((item, i) => (
              <span key={item.id} className="flex items-center gap-1">
                <span>/</span>
                <button type="button" className="hover:text-foreground hover:underline" onClick={() => goBackFolderDialog(i + 1)}>
                  {item.name}
                </button>
              </span>
            ))}
          </div>
          {/* Folder list */}
          <div className="max-h-60 overflow-y-auto border rounded-md divide-y">
            {folderDialogFolders.length === 0 && (
              <div className="px-4 py-6 text-center text-sm text-muted-foreground">{t("content.noSubfolders")}</div>
            )}
            {folderDialogFolders.map(folder => {
              // SH203 — a folder being moved or copied is shown and disabled rather than
              // hidden: a curator looking for "Docs" in the tree needs to see that it is
              // the thing they are dragging, not conclude the picker has lost it.
              const blocked = folderDialogBlockedIds.has(folder.id)
              return (
                <button
                  key={folder.id}
                  type="button"
                  disabled={blocked}
                  title={blocked ? t("content.moveInsideItself") : undefined}
                  className="w-full flex items-center gap-2 px-3 py-2 text-sm transition-colors text-left enabled:hover:bg-muted/50 disabled:opacity-50 disabled:cursor-not-allowed"
                  onClick={() => navigateFolderDialog(folder.id, folder.name)}
                >
                  <Folder className="h-4 w-4 text-amber-500 shrink-0" />
                  <span className="truncate">{folder.name}</span>
                  {blocked && (
                    <span className="ml-auto text-xs text-muted-foreground shrink-0">
                      {folderDialogMode === "copy" ? t("content.beingCopied") : t("content.beingMoved")}
                    </span>
                  )}
                </button>
              )
            })}
          </div>
          {folderDialogBlockedReason && (
            <p className="text-xs text-muted-foreground">{folderDialogBlockedReason}</p>
          )}
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" size="sm" onClick={() => setFolderDialogOpen(false)}>{t("common.cancel")}</Button>
            <Button size="sm" onClick={confirmFolderDialog} disabled={!!folderDialogBlockedReason}>
              {folderDialogMode === "copy" ? t("content.copyHere") : t("content.moveHere")}
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      {/* Delete confirmation dialog */}
      <DialogDelete
        feature={deleteTarget ? (deleteTarget.objectType === "FOLDER" ? t("content.folder") : t("content.postLabel")) : t("content.batchContent")}
        name={deleteTarget?.name || `${selectedItems.size} item(s)`}
        open={deleteDialogOpen}
        setOpen={setDeleteDialogOpen}
        onDelete={async () => {
          if (deleteTarget) {
            await deleteObject(deleteTarget.id, deleteTarget.objectType).catch(() => {})
            toast.success(t("content.deletedShort"))
          } else {
            for (const id of selectedItems) {
              // An id with no loaded row came from SH237's select-everything, which only ever
              // collects posts — so the POST default is the right one rather than a guess.
              const item = combinedItems.find(i => i.id === id)
              await deleteObject(id, item?.objectType || "POST").catch(() => {})
            }
            toast.success(t("content.itemsDeleted", { count: selectedItems.size }))
            setSelectedItems(new Set())
          }
          setDeleteDialogOpen(false)
          setDeleteTarget(null)
          await refreshList()
        }}
      />

      <PermissionsDialog
        objectId={permsTarget?.id ?? null}
        objectName={permsTarget?.name}
        open={permsOpen}
        setOpen={setPermsOpen}
      />
    </>
  )
}
