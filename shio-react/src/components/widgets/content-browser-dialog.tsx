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
import { ChevronRight, FileText, Folder, Globe, Home } from "lucide-react"
import { useCallback, useEffect, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import axios from "axios"
import { isCancelled } from "@/lib/read-options"
import type { ShFolder } from "@/models/folder/folder"
import type { ShSite } from "@/models/site/site"
import { breadcrumbFrom, soleRootFolder } from "@/app/content/content-browser/browser-view"
import { Spinner } from "@/components/spinner"

/**
 * SH120: the CDA listing envelope — `folders`/`posts`, rows carrying the CDA's names.
 * SH165 added the paging: `posts` is one page, `folders` is the whole set.
 */
interface ObjectListResponse {
  folders: ShFolder[]
  posts: ConsolePostRow[]
  page: number
  size: number
  totalPosts: number
}

/**
 * A post row as this listing actually delivers it.
 *
 * <p>It used to be typed as the JPA entity (`ShPostUnified`), which had been wrong since
 * SH120 flattened the row: `shPostType.name` is `postType` and `shPostType.title` is
 * `postTypeTitle`. Nothing failed loudly — the type badge simply never rendered, and the
 * `file` select mode filtered on `shPostType?.name === "File"`, which is `undefined` for every
 * row, so the File picker showed no files at all. Another read faithful to a shape the server
 * stopped sending (Block O's lesson, one component late).</p>
 */
interface ConsolePostRow {
  id: string
  title?: string
  furl?: string
  postType?: string
  postTypeTitle?: string
}

interface BreadcrumbItem {
  id: string
  name: string
  type: "SITE_LIST" | "SITE" | "FOLDER"
}

/** Posts per request in the picker — smaller than the browser's, since the dialog is short. */
const PICKER_PAGE_SIZE = 25

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
  /**
   * What the caller is picking.
   *
   * `folder` (SH1035) hides every post: the File widget needs somewhere to PUT an upload, and its
   * folder setting is optional, so an unconfigured widget used to dead-end on an error toast. A
   * destination is a folder, and showing pickable posts in that dialog would offer an answer the
   * caller cannot use.
   */
  selectMode?: "content" | "file" | "folder"
}

export function ContentBrowserDialog({ open, onOpenChange, ...rest }: ContentBrowserDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl max-h-[80vh] flex flex-col">
        <ContentBrowserBody key={rest.startFolderId ?? "__root__"} onOpenChange={onOpenChange} {...rest} />
      </DialogContent>
    </Dialog>
  )
}

/**
 * SH702 — mounted inside `DialogContent`, so closing unmounts it. Before this, reopening the
 * browser showed the previous session's breadcrumb, rows and selection until the new read landed,
 * and the effect that cleared them had to run after the paint that showed them.
 */
function ContentBrowserBody({
  onOpenChange,
  onSelect,
  startFolderId,
  selectMode = "content",
}: Omit<ContentBrowserDialogProps, "open">) {
  const { t } = useTranslation()
  const [loading, setLoading] = useState(true)
  const [sites, setSites] = useState<ShSite[]>([])
  const [folders, setFolders] = useState<ShFolder[]>([])
  const [posts, setPosts] = useState<ConsolePostRow[]>([])
  const [postPage, setPostPage] = useState(0)
  const [totalPosts, setTotalPosts] = useState(0)
  const [currentFolder, setCurrentFolder] = useState<string | null>(null)
  const [loadingMore, setLoadingMore] = useState(false)
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

  /*
   * SH1116 — one controller for whichever read is in flight. A curator walking folders quickly used
   * to be shown the slow answer for a folder they had left under the breadcrumb of the one they were
   * in, and a "load more" page for the old folder could be appended to the new one's rows. Every read
   * aborts the one before it, closing the dialog aborts the last, and only a read nobody superseded
   * may write the rows or lower the spinner.
   */
  const inflight = useRef<AbortController | null>(null)

  const begin = useCallback(() => {
    inflight.current?.abort()
    const abort = new AbortController()
    inflight.current = abort
    return abort.signal
  }, [])

  const cancel = useCallback(() => inflight.current?.abort(), [])

  // Load site list
  // SH702 — `fetch*` reads, `load*` reads AND raises the flags. The mount effect uses the first,
  // because a freshly mounted body is already `loading` with nothing selected; navigation inside
  // the open dialog uses the second, where the spinner is a response to a click.
  const fetchSites = useCallback((signal: AbortSignal) => axios.get<ShSite[]>("/v2/site", { signal })
    .then((res) => {
      setSites(res.data ?? [])
      setFolders([])
      setPosts([])
      setBreadcrumb([{ id: "__sites__", name: t("content.sites"), type: "SITE_LIST" }])
    })
    .catch((reason: unknown) => {
      if (!isCancelled(reason)) throw reason
    })
    .finally(() => {
      if (!signal.aborted) setLoading(false)
    }), [t])

  const loadSites = useCallback(async () => {
    setLoading(true)
    setSelectedItem(null)
    await fetchSites(begin())
  }, [fetchSites, begin])

  // Load children of an object (site or folder)
  const fetchChildren = useCallback((objectId: string, crumbs: BreadcrumbItem[], signal: AbortSignal) => axios
    .get<ObjectListResponse>(`/v2/object/${objectId}/list?page=0&size=${PICKER_PAGE_SIZE}`, { signal })
    // SH1274 — a site whose only child is its root folder is that folder, so the picker opens there
    // rather than offering one row named like the site.
    .then(async (first) => {
      const sole = soleRootFolder(first.data)
      return sole
        ? { at: sole, res: await axios.get<ObjectListResponse>(`/v2/object/${sole}/list?page=0&size=${PICKER_PAGE_SIZE}`, { signal }) }
        : { at: objectId, res: first }
    })
    .then(({ at, res }) => {
      setFolders(res.data.folders ?? [])
      setPosts(res.data.posts ?? [])
      setPostPage(res.data.page ?? 0)
      setTotalPosts(res.data.totalPosts ?? 0)
      setCurrentFolder(at)
      setSites([])
      setBreadcrumb(crumbs)
    })
    .catch((reason: unknown) => {
      if (!isCancelled(reason)) throw reason
    })
    .finally(() => {
      if (!signal.aborted) setLoading(false)
    }), [])

  const loadChildren = useCallback(async (objectId: string, crumbs: BreadcrumbItem[]) => {
    setLoading(true)
    setSelectedItem(null)
    await fetchChildren(objectId, crumbs, begin())
  }, [fetchChildren, begin])

  /**
   * The next page of this folder's posts (SH165).
   *
   * A picker is the surface where a silent cut does the most damage — the content the user is
   * looking for simply is not in the list and nothing says why — so the count is shown and the
   * rest is one click away.
   */
  const loadMorePosts = useCallback(async () => {
    if (!currentFolder) return
    setLoadingMore(true)
    const signal = begin()
    try {
      const next = postPage + 1
      const res = await axios.get<ObjectListResponse>(`/v2/object/${currentFolder}/list?page=${next}&size=${PICKER_PAGE_SIZE}`,
        { signal })
      setPosts((prev) => [...prev, ...(res.data.posts ?? [])])
      setPostPage(res.data.page ?? next)
      setTotalPosts(res.data.totalPosts ?? 0)
    } catch (reason) {
      if (!isCancelled(reason)) throw reason
    } finally {
      // Unconditionally: a navigation that aborted this page starts its own read and must not find
      // the next folder's "load more" still disabled by this one.
      setLoadingMore(false)
    }
  }, [currentFolder, postPage, begin])

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
  // `postType` is the row's own field since SH120; reading `shPostType.name` here matched
  // nothing, so this filter used to hide every file in the instance.
  const visiblePosts = selectMode === "folder"
    // SH1035 — none. The caller is choosing a destination, and a row it could select that is not a
    // folder is a wrong answer the dialog offered.
    ? []
    : selectMode === "file"
      ? posts.filter((p) => p.postType === "File")
      : posts

  // What the folder holds minus what the filter above hides is not something the server told
  // us, so the count is stated in the server's terms and the button offers the next page.
  const hasMorePosts = posts.length < totalPosts

  // Handle content selection confirm
  const handleSelect = useCallback(() => {
    if (!selectedItem) return
    onSelect(buildSelection(selectedItem.id, selectedItem.name, selectedItem.type))
    onOpenChange(false)
  }, [selectedItem, buildSelection, onSelect, onOpenChange])

  // SH702 — the mount IS the open, so there is nothing to clear and no flag to check: `loading`
  // starts true and `selectedItem` starts null because this component did not exist a moment ago.
  useEffect(() => {
    const signal = begin()
    if (startFolderId) {
      axios.get(`/v2/object/${startFolderId}/path`, { signal }).then((pathRes) => {
        const data = pathRes.data
        const crumbs: BreadcrumbItem[] = [
          { id: "__sites__", name: t("content.sites"), type: "SITE_LIST" },
        ]
        // SH1274 — the site once, never its root folder's copy of the name.
        for (const c of breadcrumbFrom(data)) {
          crumbs.push({ id: c.id, name: c.name, type: c.type === "SITE" ? "SITE" : "FOLDER" })
        }
        void fetchChildren(startFolderId, crumbs, signal)
      }).catch((reason: unknown) => {
        // A folder whose path cannot be read falls back to the site list; a closed dialog does not.
        if (isCancelled(reason)) return
        void fetchSites(signal)
      })
    } else {
      void fetchSites(signal)
    }
    return cancel
  }, [startFolderId, t, fetchChildren, fetchSites, begin, cancel])

  return (
    <>
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            {selectMode === "folder"
              ? t("content.selectFolder")
              : selectMode === "file" ? t("content.selectFile") : t("content.selectContent")}
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
              <Spinner labelled className="h-5 w-5" />
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
                  {(post.postTypeTitle || post.postType) && (
                    <Badge variant="outline" className="text-[10px] px-1.5 py-0 h-5 shrink-0">
                      {post.postTypeTitle || post.postType}
                    </Badge>
                  )}
                </button>
              ))}

              {/* SH165: this list is a page. Say so, and offer the rest. */}
              {hasMorePosts && (
                <div className="flex flex-col items-center gap-1.5 pt-2">
                  <span className="text-xs text-muted-foreground">
                    {t("content.showingPosts", { loaded: posts.length, total: totalPosts })}
                  </span>
                  <Button type="button" variant="outline" size="sm" disabled={loadingMore} onClick={loadMorePosts}>
                    {loadingMore ? t("common.loading") : t("content.loadMore")}
                  </Button>
                </div>
              )}

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
    </>
  )
}
