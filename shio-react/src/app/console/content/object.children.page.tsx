import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Separator } from "@/components/ui/separator"
import { Checkbox } from "@/components/ui/checkbox"
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog"
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from "@/components/ui/dropdown-menu"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { IconFileText } from "@tabler/icons-react"
import { Folder, FileText, Plus, Download, FolderPlus, Eye, Pencil, Settings, ChevronDown, Copy, Move, Trash2, Search, Upload, LayoutGrid, List, Sheet, RefreshCw, ClipboardCopy } from "lucide-react"
import { useEffect, useState, useCallback } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { formatDistanceToNow } from "date-fns"
import { ShPostTypeService } from "@/services/post-type/post-type.service"
import type { ShPostType } from "@/models/post-type/post-type"
import type { ShFolder } from "@/models/folder/folder"
import type { ShPost } from "@/models/post/post"
import axios from "axios"
import { toast } from "sonner"

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
  const [postTypes, setPostTypes] = useState<ShPostType[]>([])
  const [breadcrumbPath, setBreadcrumbPath] = useState<BreadcrumbPathItem[]>([])
  const [dialogOpen, setDialogOpen] = useState(false)
  const [selectedItems, setSelectedItems] = useState<Set<string>>(new Set())
  const [loading, setLoading] = useState(true)
  const [sortBy, setSortBy] = useState("order")
  const [viewMode, setViewMode] = useState<"list" | "grid" | "spreadsheet">("list")
  const [searchOpen, setSearchOpen] = useState(false)
  const [searchQuery, setSearchQuery] = useState("")

  useEffect(() => {
    setLoading(true)
    setSelectedItems(new Set())

    const fetchPath = axios.get(`/v2/object/${objectId}/path`).then((res) => {
      if (Array.isArray(res.data)) {
        setBreadcrumbPath(res.data)
      }
    }).catch(() => {
      setBreadcrumbPath([])
    })

    const fetchChildren = axios.get<ObjectListResponse>(`/v2/object/${objectId}/list`).then((res) => {
      setListData(res.data)
    })

    Promise.all([fetchPath, fetchChildren]).finally(() => setLoading(false))
    postTypeService.query().then(setPostTypes)
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
      name: post.title,
      date: post.date,
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

  const handleBatchAction = async (action: string) => {
    if (selectedItems.size === 0) return
    if (action === "delete") {
      if (!confirm(`Delete ${selectedItems.size} selected item(s)?`)) return
      for (const id of selectedItems) {
        await axios.delete(`/v2/object/${id}`).catch(() => { })
      }
      toast.success(`${selectedItems.size} item(s) deleted`)
      setSelectedItems(new Set())
      const res = await axios.get<ObjectListResponse>(`/v2/object/${objectId}/list`)
      setListData(res.data)
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
              onClick={() => window.open(`/sites/${siteName}/default/en-us`, "_blank")}
            >
              <Eye className="mr-2 h-4 w-4" /> View Site
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => navigate(`${ROUTES.CONTENT_ROOT}/site/${siteId}`)}
            >
              <Settings className="mr-2 h-4 w-4" /> Site Properties
            </Button>
          </div>
        )}

        {/* Stats bar */}
        <Card>
          <CardContent className="flex items-center gap-8 py-3 px-6 text-sm text-muted-foreground">
            <div className="flex items-center gap-1.5">
              <Folder className="h-4 w-4" />
              <span>{folderCount} {folderCount === 1 ? "folder" : "folders"}</span>
            </div>
            <div className="flex items-center gap-1.5">
              <FileText className="h-4 w-4" />
              <span>{postCount} {postCount === 1 ? "post" : "posts"}</span>
            </div>
          </CardContent>
        </Card>

        {/* Toolbar: New Article | Grid | New Folder | Import || Order | Select | Action in Batch */}
        <div className="flex flex-wrap items-center gap-2">
          {/* Left actions */}
          <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
            <DialogTrigger asChild>
              <Button variant="outline" size="sm">
                <Plus className="mr-2 h-4 w-4" /> New Article
              </Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>Select Post Type</DialogTitle>
              </DialogHeader>
              <div className="space-y-2 max-h-[60vh] overflow-y-auto">
                {postTypes.map((pt) => (
                  <Button key={pt.id} variant="outline" className="w-full justify-start" onClick={() => { setDialogOpen(false); navigate(`${ROUTES.CONTENT_ROOT}/folder/${objectId}/post-type/${pt.id}/new`) }}>
                    <FileText className="mr-2 h-4 w-4" /> {pt.title || pt.name}
                  </Button>
                ))}
                {postTypes.length === 0 && <p className="text-sm text-muted-foreground text-center py-4">{t("common.noResults")}</p>}
              </div>
            </DialogContent>
          </Dialog>

          <Button variant="outline" size="sm" onClick={() => navigate(`${ROUTES.CONTENT_ROOT}/folder/${objectId}/new-folder`)}>
            <FolderPlus className="mr-2 h-4 w-4" /> New Folder
          </Button>

          <Button variant="outline" size="sm" onClick={() => navigate(ROUTES.SITE_IMPORT)}>
            <Upload className="mr-2 h-4 w-4" /> Import
          </Button>

          <div className="flex-1" />

          {/* Right: Sort + Select + Batch + Download */}
          <Select value={sortBy} onValueChange={setSortBy}>
            <SelectTrigger className="w-28 h-8 text-xs"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="order">Order</SelectItem>
              <SelectItem value="name">Name</SelectItem>
              <SelectItem value="date">Date</SelectItem>
            </SelectContent>
          </Select>

          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" size="sm">Select <ChevronDown className="ml-1 h-3.5 w-3.5" /></Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onClick={() => handleSelectAction("content")}><FileText className="mr-2 h-4 w-4 text-green-600" /> Content</DropdownMenuItem>
              <DropdownMenuItem onClick={() => handleSelectAction("folder")}><Folder className="mr-2 h-4 w-4" /> Folder</DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem onClick={() => handleSelectAction("everything")}>Everything</DropdownMenuItem>
              <DropdownMenuItem onClick={() => handleSelectAction("invert")}>Invert Selection</DropdownMenuItem>
              <DropdownMenuItem onClick={() => handleSelectAction("nothing")}>Nothing</DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>

          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" size="sm" disabled={selectedItems.size === 0}>Action in Batch <ChevronDown className="ml-1 h-3.5 w-3.5" /></Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onClick={() => handleBatchAction("copy")}><Copy className="mr-2 h-4 w-4" /> Copy</DropdownMenuItem>
              <DropdownMenuItem onClick={() => handleBatchAction("move")}><Move className="mr-2 h-4 w-4" /> Move</DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem onClick={() => handleBatchAction("delete")} className="text-destructive"><Trash2 className="mr-2 h-4 w-4" /> Delete</DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>

          {siteId && (
            <Button size="sm" className="bg-green-600 hover:bg-green-700 text-white" onClick={() => window.open(`/api/v2/site/${siteId}/export`, "_blank")}>
              <Download className="mr-2 h-4 w-4" /> Download Site
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
              placeholder="Filter items..."
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              autoFocus
            />
          </div>
        )}

        {/* Content list */}
        <Card className="overflow-hidden">
          <CardContent className="p-0">
            {/* List header */}
            <div className="flex items-center gap-3 px-4 py-2.5 bg-muted/50 border-b text-xs font-medium text-muted-foreground uppercase tracking-wide">
              <Checkbox
                checked={sortedItems.length > 0 && selectedItems.size === sortedItems.length}
                onCheckedChange={() => {
                  if (selectedItems.size === sortedItems.length) setSelectedItems(new Set())
                  else setSelectedItems(new Set(sortedItems.map(i => i.id)))
                }}
                aria-label="Select all"
              />
              <div className="w-8 text-center">#</div>
              <div className="flex-1">Name</div>
              <div className="hidden sm:block w-32 text-right">Type</div>
              <div className="hidden md:block w-32 text-right">Date</div>
              <div className="w-32 text-right">Actions</div>
            </div>

            {loading && (
              <div className="px-4 py-12 text-center text-sm text-muted-foreground">Loading...</div>
            )}

            {!loading && sortedItems.map((item, index) => (
              <div
                key={item.id}
                className={`group flex items-center gap-3 px-4 py-2.5 text-sm border-b last:border-b-0 hover:bg-muted/40 transition-colors ${selectedItems.has(item.id) ? "bg-primary/5" : ""}`}
              >
                <Checkbox
                  checked={selectedItems.has(item.id)}
                  onCheckedChange={() => toggleSelect(item.id)}
                  aria-label={`Select ${item.name}`}
                />

                {/* Position number */}
                <div className="w-8 text-center">
                  <span className={`inline-flex items-center justify-center h-6 w-6 rounded-full text-xs font-medium text-white ${item.objectType === "FOLDER" ? "bg-green-500" : "bg-blue-500"}`}>
                    {index + 1}
                  </span>
                </div>

                {/* Icon + Name */}
                {item.objectType === "FOLDER" ? (
                  <Folder className="h-4 w-4 shrink-0 text-amber-500" />
                ) : (
                  <FileText className="h-4 w-4 shrink-0 text-blue-500" />
                )}
                <button
                  type="button"
                  className="flex-1 text-left truncate font-medium text-blue-600 dark:text-blue-400 hover:underline transition-colors"
                  onClick={() => handleItemClick(item)}
                >
                  {item.name}
                </button>

                {/* Type badges */}
                <div className="hidden sm:flex w-32 justify-end gap-1">
                  <Badge variant="secondary" className="text-[10px] px-1.5 py-0 bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400">
                    {item.objectType === "FOLDER" ? "Folder" : item.postTypeName || "Post"}
                  </Badge>
                  {siteName && (
                    <Badge variant="secondary" className="text-[10px] px-1.5 py-0 bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400">
                      {siteName}
                    </Badge>
                  )}
                </div>

                {/* Date */}
                <div className="hidden md:block w-32 text-right text-xs text-muted-foreground">
                  {formatDate(item.date)}
                </div>

                {/* Hover actions - grid layout like AngularJS */}
                <div className="shrink-0 flex flex-wrap gap-x-3 gap-y-0.5 opacity-0 group-hover:opacity-100 transition-opacity">
                  <button type="button" className="flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground" onClick={(e) => { e.stopPropagation(); item.objectType === "FOLDER" ? navigate(`${ROUTES.CONTENT_ROOT}/folder/${item.id}/edit`) : navigate(`${ROUTES.CONTENT_ROOT}/post/${item.id}`) }}>
                    <Pencil className="h-3 w-3" /> Edit
                  </button>
                  <button type="button" className="flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground" onClick={(e) => { e.stopPropagation(); toast.info("Clone: coming soon") }}>
                    <ClipboardCopy className="h-3 w-3" /> Clone
                  </button>
                  <button type="button" className="flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground" onClick={(e) => { e.stopPropagation(); axios.get(`/v2/object/${item.id}/clear-cache`).then(() => toast.success("Cache cleared")) }}>
                    <RefreshCw className="h-3 w-3" /> Clear Cache
                  </button>
                  <button type="button" className="flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground" onClick={(e) => { e.stopPropagation(); toast.info("Copy: coming soon") }}>
                    <Copy className="h-3 w-3" /> Copy
                  </button>
                  <button type="button" className="flex items-center gap-1 text-xs text-destructive/70 hover:text-destructive" onClick={async (e) => { e.stopPropagation(); if (confirm(`Delete ${item.name}?`)) { await axios.delete(`/v2/object/${item.id}`).catch(() => {}); toast.success("Deleted"); const res = await axios.get<ObjectListResponse>(`/v2/object/${objectId}/list`); setListData(res.data) } }}>
                    <Trash2 className="h-3 w-3" /> Delete
                  </button>
                  <button type="button" className="flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground" onClick={(e) => { e.stopPropagation(); toast.info("Move: coming soon") }}>
                    <Move className="h-3 w-3" /> Move
                  </button>
                  <button type="button" className="flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground" onClick={(e) => { e.stopPropagation(); window.open(`/api/v2/object/${item.id}/preview`, "_blank") }}>
                    <Eye className="h-3 w-3" /> View Page
                  </button>
                </div>
              </div>
            ))}

            {!loading && sortedItems.length === 0 && (
              <div className="px-4 py-16 text-center">
                <Folder className="h-12 w-12 mx-auto text-muted-foreground/30 mb-3" />
                <p className="text-sm text-muted-foreground">{t("common.noResults")}</p>
                <p className="text-xs text-muted-foreground/70 mt-1">Create a folder or post to get started.</p>
              </div>
            )}
          </CardContent>
        </Card>

        {/* Selection info bar */}
        {selectedItems.size > 0 && (
          <div className="flex items-center gap-3 text-sm text-muted-foreground">
            <span>{selectedItems.size} item{selectedItems.size !== 1 ? "s" : ""} selected</span>
            <Separator orientation="vertical" className="h-4" />
            <Button variant="ghost" size="sm" className="h-7 text-xs" onClick={() => setSelectedItems(new Set())}>
              Clear selection
            </Button>
          </div>
        )}
      </div>
    </>
  )
}
