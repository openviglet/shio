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
import type { ShPostType } from "@/models/post-type/post-type"
import type { ShFolder } from "@/models/folder/folder"
import type { ShPostUnified as ShPost } from "@/models/post-unified/post-unified"
import axios from "axios"
import { toast } from "@viglet/viglet-design-system"

const postTypeService = new ShPostTypeService()

interface ObjectListResponse {
  shFolders: ShFolder[]
  shPosts: ShPost[]
  shSite?: { id: string; name: string }
  breadcrumb?: Array<{ id: string; name: string; shSite?: { id: string; name: string } }>
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

export default function ObjectChildrenPage() {
  const { objectId } = useParams() as { objectId: string }
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { resetBreadcrumb } = useBreadcrumb()
  const [listData, setListData] = useState<ObjectListResponse>({ shFolders: [], shPosts: [] })
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


  useEffect(() => {
    setLoading(true)
    setSelectedItems(new Set())

    const fetchPath = axios.get(`/v2/object/${objectId}/path`).then((res) => {
      const data = res.data
      const items: BreadcrumbPathItem[] = []
      // API returns { breadcrumb: [...folders], shSite: {...}, currentFolder: {...} }
      if (data.shSite) {
        items.push({ id: data.shSite.id, name: data.shSite.name, type: "SITE" })
      }
      if (Array.isArray(data.breadcrumb)) {
        for (const crumb of data.breadcrumb) {
          items.push({ id: crumb.id, name: crumb.name, type: crumb.objectType || "FOLDER" })
        }
      } else if (Array.isArray(data)) {
        // Fallback: API returns plain array
        for (const crumb of data) {
          items.push({ id: crumb.id, name: crumb.name, type: crumb.objectType || crumb.type || "FOLDER" })
        }
      }
      setBreadcrumbPath(items)
    }).catch(() => {
      setBreadcrumbPath([])
    })

    const fetchChildren = axios.get<ObjectListResponse>(`/v2/object/${objectId}/list`).then((res) => {
      setListData({
        shFolders: res.data.shFolders ?? [],
        shPosts: res.data.shPosts ?? [],
      })
    })

    Promise.all([fetchPath, fetchChildren]).finally(() => setLoading(false))
    postTypeService.query().then((pts) => {
      const savedId = localStorage.getItem("sh-last-post-type")
      const saved = pts.find(p => p.id === savedId)
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

  const combinedItems: ObjectChild[] = [
    ...listData.shFolders.map((folder) => ({
      id: folder.id,
      name: folder.name,
      date: folder.date,
      objectType: "FOLDER" as const,
      position: folder.position,
    })),
    ...listData.shPosts.map((post) => ({
      id: post.id,
      name: post.title ?? "",
      date: post.date ?? "",
      objectType: "POST" as const,
      postTypeName: post.shPostType?.title || post.shPostType?.name,
      publishStatus: post.publishStatus,
    })),
  ]

  const filteredItems = searchQuery
    ? combinedItems.filter(i => i.name.toLowerCase().includes(searchQuery.toLowerCase()))
    : combinedItems

  const sortedItems = [...filteredItems].sort((a, b) => {
    if (sortBy === "name") return a.name.localeCompare(b.name)
    if (sortBy === "date") return new Date(b.date).getTime() - new Date(a.date).getTime()
    return (a.position ?? 0) - (b.position ?? 0)
  })

  const toggleSelect = useCallback((id: string) => {
    setSelectedItems((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }, [])

  const handleSelectAction = (action: string) => {
    switch (action) {
      case "content":
        setSelectedItems(new Set(combinedItems.filter(i => i.objectType === "POST").map(i => i.id)))
        break
      case "folder":
        setSelectedItems(new Set(combinedItems.filter(i => i.objectType === "FOLDER").map(i => i.id)))
        break
      case "everything":
        setSelectedItems(new Set(combinedItems.map(i => i.id)))
        break
      case "invert":
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

  const refreshList = async () => {
    const res = await axios.get<ObjectListResponse>(`/v2/object/${objectId}/list`)
    setListData({ shFolders: res.data.shFolders ?? [], shPosts: res.data.shPosts ?? [] })
  }

  const handleClone = async (itemId: string) => {
    await axios.put(`/v2/object/copyto/${objectId}`, [itemId])
    toast.success(t("content.clonedSuccess"))
    await refreshList()
  }

  const openFolderDialog = (mode: "copy" | "move", ids: string[]) => {
    setFolderDialogMode(mode)
    setFolderDialogIds(ids)
    // Start at site root
    const startId = siteId || objectId
    setFolderDialogCurrentId(startId)
    setFolderDialogPath([])
    axios.get<ObjectListResponse>(`/v2/object/${startId}/list`).then((res) => {
      setFolderDialogFolders((res.data.shFolders ?? []).map(f => ({ id: f.id, name: f.name })))
    })
    setFolderDialogOpen(true)
  }

  const navigateFolderDialog = (folderId: string, folderName: string) => {
    setFolderDialogCurrentId(folderId)
    setFolderDialogPath(prev => [...prev, { id: folderId, name: folderName }])
    axios.get<ObjectListResponse>(`/v2/object/${folderId}/list`).then((res) => {
      setFolderDialogFolders((res.data.shFolders ?? []).map(f => ({ id: f.id, name: f.name })))
    })
  }

  const goBackFolderDialog = (index: number) => {
    const targetPath = folderDialogPath.slice(0, index)
    const targetId = index === 0 ? (siteId || objectId) : targetPath.at(-1)!.id
    setFolderDialogCurrentId(targetId)
    setFolderDialogPath(targetPath)
    axios.get<ObjectListResponse>(`/v2/object/${targetId}/list`).then((res) => {
      setFolderDialogFolders((res.data.shFolders ?? []).map(f => ({ id: f.id, name: f.name })))
    })
  }

  const confirmFolderDialog = async () => {
    if (!folderDialogCurrentId) return
    if (folderDialogMode === "move" && folderDialogCurrentId === objectId) {
      toast.warning("Cannot move to the same folder")
      return
    }
    const endpoint = folderDialogMode === "copy" ? "copyto" : "moveto"
    await axios.put(`/v2/object/${endpoint}/${folderDialogCurrentId}`, folderDialogIds)
    toast.success(folderDialogMode === "copy" ? t("content.copiedSuccess") : t("content.movedSuccess"))
    setFolderDialogOpen(false)
    setSelectedItems(new Set())
    await refreshList()
  }

  const siteInfo = listData.shSite
  const siteId = siteInfo?.id || (breadcrumbPath.length > 0 ? breadcrumbPath[0]?.id : null)
  const siteName = siteInfo?.name || (breadcrumbPath.length > 0 ? breadcrumbPath[0]?.name : null)
  const folderCount = listData.shFolders.length
  const postCount = listData.shPosts.length

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
              onClick={() => siteId && navigate(`${ROUTES.PREVIEW}/${siteId}`)}
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
                    localStorage.setItem("sh-last-post-type", lastPostType.id)
                    navigate(`${ROUTES.CONTENT_ROOT}/folder/${objectId}/post-type/${lastPostType.id}/new`)
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
                    <button type="button" className="flex items-center gap-1 text-muted-foreground hover:text-foreground whitespace-nowrap" onClick={(e) => { e.stopPropagation(); navigate(`${ROUTES.PREVIEW}/${item.id}`) }}>
                      <Eye className="h-3 w-3" /> {t("content.viewPage")}
                    </button>
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
            {folderDialogFolders.map(folder => (
              <button
                key={folder.id}
                type="button"
                className="w-full flex items-center gap-2 px-3 py-2 text-sm hover:bg-muted/50 transition-colors text-left"
                onClick={() => navigateFolderDialog(folder.id, folder.name)}
              >
                <Folder className="h-4 w-4 text-amber-500 shrink-0" />
                <span className="truncate">{folder.name}</span>
              </button>
            ))}
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" size="sm" onClick={() => setFolderDialogOpen(false)}>{t("common.cancel")}</Button>
            <Button size="sm" onClick={confirmFolderDialog}>
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
