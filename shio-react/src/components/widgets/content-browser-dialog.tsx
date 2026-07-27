import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Separator } from "@/components/ui/separator"
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { ChevronRight, FileText, Folder, Globe, Home, Loader2 } from "lucide-react"
import { useCallback, useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import axios from "axios"
import type { ShFolder } from "@/models/folder/folder"
import type { ShPostUnified as ShPost } from "@/models/post-unified/post-unified"
import type { ShSite } from "@/models/site/site"

/** SH120: the CDA listing envelope — `folders`/`posts`, rows carrying the CDA's names. */
interface ObjectListResponse {
  folders: ShFolder[]
  posts: ShPost[]
}

interface BreadcrumbItem {
  id: string
  name: string
  type: "SITE_LIST" | "SITE" | "FOLDER"
}

export interface ContentBrowserSelection {
  id: string
  name: string
  path: string
  type: "FOLDER" | "POST"
  pathSegments: { id: string; name: string }[]
  parentFolderId?: string
}

interface ContentBrowserDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSelect: (selection: ContentBrowserSelection) => void
  startFolderId?: string
  selectMode?: "content" | "file"
}

export function ContentBrowserDialog({
  open,
  onOpenChange,
  onSelect,
  startFolderId,
  selectMode = "content",
}: ContentBrowserDialogProps) {
  const { t } = useTranslation()
  const [loading, setLoading] = useState(false)
  const [sites, setSites] = useState<ShSite[]>([])
  const [folders, setFolders] = useState<ShFolder[]>([])
  const [posts, setPosts] = useState<ShPost[]>([])
  const [breadcrumb, setBreadcrumb] = useState<BreadcrumbItem[]>([])
  const [selectedItem, setSelectedItem] = useState<{ id: string; name: string; type: "FOLDER" | "POST" } | null>(null)

  const buildPathString = useCallback((itemName: string) => {
    // Skip "Sites" root entry for the display path
    return breadcrumb
      .filter((b) => b.type !== "SITE_LIST")
      .map((b) => b.name)
      .concat(itemName)
      .join(" / ")
  }, [breadcrumb])

  const buildPathSegments = useCallback(() => {
    return breadcrumb
      .filter((b) => b.type !== "SITE_LIST")
      .map((b) => ({ id: b.id, name: b.name }))
  }, [breadcrumb])

  // The current folder ID is the last non-SITE_LIST breadcrumb item
  const getCurrentFolderId = useCallback(() => {
    for (let i = breadcrumb.length - 1; i >= 0; i--) {
      if (breadcrumb[i].type !== "SITE_LIST") return breadcrumb[i].id
    }
    return undefined
  }, [breadcrumb])

  const buildSelection = useCallback((id: string, name: string, type: "FOLDER" | "POST"): ContentBrowserSelection => {
    return {
      id,
      name,
      path: buildPathString(name),
      type,
      pathSegments: buildPathSegments(),
      parentFolderId: getCurrentFolderId(),
    }
  }, [buildPathString, buildPathSegments, getCurrentFolderId])

  // Load site list
  const loadSites = useCallback(async () => {
    setLoading(true)
    setSelectedItem(null)
    try {
      const res = await axios.get<ShSite[]>("/v2/site")
      setSites(res.data ?? [])
      setFolders([])
      setPosts([])
      setBreadcrumb([{ id: "__sites__", name: t("content.sites"), type: "SITE_LIST" }])
    } finally {
      setLoading(false)
    }
  }, [t])

  // Load children of an object (site or folder)
  const loadChildren = useCallback(async (objectId: string, crumbs: BreadcrumbItem[]) => {
    setLoading(true)
    setSelectedItem(null)
    try {
      const res = await axios.get<ObjectListResponse>(`/v2/object/${objectId}/list`)
      setFolders(res.data.folders ?? [])
      setPosts(res.data.posts ?? [])
      setSites([])
      setBreadcrumb(crumbs)
    } finally {
      setLoading(false)
    }
  }, [])

  // Navigate to a site
  const navigateToSite = useCallback((site: ShSite) => {
    const crumbs: BreadcrumbItem[] = [
      { id: "__sites__", name: t("content.sites"), type: "SITE_LIST" },
      { id: site.id, name: site.name, type: "SITE" },
    ]
    loadChildren(site.id, crumbs)
  }, [t, loadChildren])

  // Navigate into a folder
  const navigateToFolder = useCallback((folder: ShFolder) => {
    const crumbs: BreadcrumbItem[] = [
      ...breadcrumb,
      { id: folder.id, name: folder.name, type: "FOLDER" },
    ]
    loadChildren(folder.id, crumbs)
  }, [breadcrumb, loadChildren])

  // Navigate to a breadcrumb item
  const navigateToBreadcrumb = useCallback((item: BreadcrumbItem, index: number) => {
    if (item.type === "SITE_LIST") {
      loadSites()
    } else {
      const crumbs = breadcrumb.slice(0, index + 1)
      loadChildren(item.id, crumbs)
    }
  }, [breadcrumb, loadSites, loadChildren])

  // In "file" select mode, only File-type posts are pickable (SH62) — folders stay
  // navigable, but other content types are hidden so the user can't pick a non-file.
  const visiblePosts = selectMode === "file"
    ? posts.filter((p) => p.shPostType?.name === "File")
    : posts

  // Handle content selection confirm
  const handleSelect = useCallback(() => {
    if (!selectedItem) return
    onSelect(buildSelection(selectedItem.id, selectedItem.name, selectedItem.type))
    onOpenChange(false)
  }, [selectedItem, buildSelection, onSelect, onOpenChange])

  // Initialize on open
  useEffect(() => {
    if (!open) return
    setSelectedItem(null)
    if (startFolderId) {
      setLoading(true)
      axios.get(`/v2/object/${startFolderId}/path`).then((pathRes) => {
        const data = pathRes.data
        const crumbs: BreadcrumbItem[] = [
          { id: "__sites__", name: t("content.sites"), type: "SITE_LIST" },
        ]
        if (data.site) {
          crumbs.push({ id: data.site.id, name: data.site.name, type: "SITE" })
        }
        if (Array.isArray(data.breadcrumb)) {
          for (const c of data.breadcrumb) {
            crumbs.push({ id: c.id, name: c.name, type: "FOLDER" })
          }
        }
        loadChildren(startFolderId, crumbs)
      }).catch(() => {
        loadSites()
      })
    } else {
      loadSites()
    }
  }, [open, startFolderId, t, loadChildren, loadSites])

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl max-h-[80vh] flex flex-col">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            {selectMode === "file" ? t("content.selectFile") : t("content.selectContent")}
          </DialogTitle>
        </DialogHeader>

        {/* Breadcrumb navigation */}
        <div className="flex items-center gap-1 text-sm flex-wrap min-h-7">
          {breadcrumb.map((item, index) => (
            <span key={`${item.id}-${index}`} className="flex items-center gap-1">
              {index > 0 && <ChevronRight className="h-3 w-3 text-muted-foreground shrink-0" />}
              <button
                type="button"
                className={`hover:underline transition-colors ${
                  index === breadcrumb.length - 1
                    ? "text-foreground font-medium"
                    : "text-primary hover:text-primary/80"
                }`}
                onClick={() => navigateToBreadcrumb(item, index)}
              >
                {item.type === "SITE_LIST" && <Home className="h-3.5 w-3.5 inline mr-1" />}
                {item.name}
              </button>
            </span>
          ))}
        </div>

        <Separator />

        {/* Content area */}
        <div className="flex-1 overflow-y-auto min-h-75 max-h-100 -mx-2 px-2">
          {loading ? (
            <div className="flex items-center justify-center h-full gap-2 text-muted-foreground">
              <Loader2 className="h-5 w-5 animate-spin" />
              <span>{t("common.loading")}</span>
            </div>
          ) : (
            <div className="space-y-1">
              {/* Sites */}
              {sites.map((site) => (
                <button
                  key={site.id}
                  type="button"
                  className="w-full flex items-center gap-3 px-3 py-2.5 rounded-md text-left hover:bg-muted/60 transition-colors group"
                  onClick={() => navigateToSite(site)}
                >
                  <Globe className="h-4.5 w-4.5 text-green-600 dark:text-green-400 shrink-0" />
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium truncate">{site.name}</p>
                    {site.description && (
                      <p className="text-xs text-muted-foreground truncate">{site.description}</p>
                    )}
                  </div>
                  <ChevronRight className="h-4 w-4 text-muted-foreground opacity-0 group-hover:opacity-100 transition-opacity shrink-0" />
                </button>
              ))}

              {/* Folders */}
              {folders.map((folder) => (
                <button
                  key={folder.id}
                  type="button"
                  className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-md text-left transition-colors group ${
                    selectedItem?.id === folder.id
                      ? "bg-primary/10 ring-1 ring-primary/30"
                      : "hover:bg-muted/60"
                  }`}
                  onClick={() => setSelectedItem({ id: folder.id, name: folder.name, type: "FOLDER" })}
                  onDoubleClick={() => navigateToFolder(folder)}
                >
                  <Folder className="h-4.5 w-4.5 text-orange-500 dark:text-orange-400 shrink-0" />
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium truncate">{folder.name}</p>
                  </div>
                  <ChevronRight className="h-4 w-4 text-muted-foreground opacity-0 group-hover:opacity-100 transition-opacity shrink-0" />
                </button>
              ))}

              {/* Posts */}
              {visiblePosts.map((post) => (
                <button
                  key={post.id}
                  type="button"
                  className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-md text-left transition-colors ${
                    selectedItem?.id === post.id
                      ? "bg-primary/10 ring-1 ring-primary/30"
                      : "hover:bg-muted/60"
                  }`}
                  onClick={() => setSelectedItem({ id: post.id, name: post.title ?? "", type: "POST" })}
                  onDoubleClick={() => {
                    onSelect(buildSelection(post.id, post.title ?? "", "POST"))
                    onOpenChange(false)
                  }}
                >
                  <FileText className="h-4.5 w-4.5 text-blue-500 dark:text-blue-400 shrink-0" />
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium truncate">{post.title}</p>
                  </div>
                  {post.shPostType && (
                    <Badge variant="outline" className="text-[10px] px-1.5 py-0 h-5 shrink-0">
                      {post.shPostType.title || post.shPostType.name}
                    </Badge>
                  )}
                </button>
              ))}

              {/* Empty state */}
              {sites.length === 0 && folders.length === 0 && visiblePosts.length === 0 && (
                <div className="flex flex-col items-center justify-center py-12 text-center text-muted-foreground">
                  <Folder className="h-10 w-10 mb-3 opacity-30" />
                  <p className="text-sm">{t("content.emptyFolder")}</p>
                </div>
              )}
            </div>
          )}
        </div>

        <Separator />

        {/* Selection preview */}
        {selectedItem && (
          <div className="flex items-center gap-2 rounded-md bg-muted/50 px-3 py-2 text-sm">
            {selectedItem.type === "FOLDER"
              ? <Folder className="h-4 w-4 text-orange-500 shrink-0" />
              : <FileText className="h-4 w-4 text-blue-500 shrink-0" />}
            <span className="text-muted-foreground truncate">{buildPathString(selectedItem.name)}</span>
          </div>
        )}

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            {t("common.cancel")}
          </Button>
          <Button type="button" disabled={!selectedItem} onClick={handleSelect}>
            {t("content.select")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
