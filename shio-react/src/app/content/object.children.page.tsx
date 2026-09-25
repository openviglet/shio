import { Button } from "@/components/ui/button"
import { Panel } from "@/components/panel"
import { PageHero } from "@/components/page-hero"
import { Badge } from "@/components/ui/badge"
import { Separator } from "@/components/ui/separator"
import { Checkbox } from "@/components/ui/checkbox"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip"
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from "@/components/ui/dropdown-menu"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { ROUTES } from "@/app/routes.const"
import { IconFileText } from "@tabler/icons-react"
import { DialogDelete } from "@/components/dialog.delete"
import { PermissionsDialog } from "@/components/permissions.dialog"
import { Folder, FileText, Plus, Download, FolderPlus, Eye, Pencil, Settings, ChevronDown, Copy, Move, Trash2, Upload, LayoutGrid, List, Sheet, ClipboardCopy, ShieldCheck, ExternalLink, SlidersHorizontal } from "lucide-react"
// SH1020 — the row actions live in the design system's menu, which is where the site list put
// its four in its own sweep.
import { BentoFilterBar, type BentoFilterValue } from "@viglet/viglet-design-system/bento"
import { contentFacets, fromBarValue, toBarValue, withCounts, type FacetCounts } from "@/lib/filter-bar"
import {
  browserView, listboxClass, optionClass, stepFor, type BrowserRow,
} from "./content-browser/browser-view"
import { BrowserItem } from "./content-browser/browser-views"
import { BrowserTable } from "./content-browser/browser-table"
import { useEffect, useState, useCallback, useMemo, useRef } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { formatDistanceToNow } from "date-fns"
import { ShSiteService } from "@/services/site/site.service"
import { ShPreviewTokenService } from "@/services/preview-token/preview-token.service"
import type { ShPostType } from "@/models/post-type/post-type"
import axios from "axios"
import { toast } from "@viglet/viglet-design-system"
import { siteDestination } from "@/lib/site-front-end"
import { deleteEach, refusalSummary, type DeleteTarget } from "@/lib/batch-delete"
// SH1021 — the bulk verbs go through the writer the agent's batch runs through.
import { ShPostUnifiedService, type ShBulkResult }
  from "@/services/post-unified/post-unified.service"
import { headlineOf, problemOf } from "@/lib/problem"
// SH1022 — the content-query vocabulary, spelled here exactly as the server spells it.
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { filterParams, type ShContentFilters } from "@/lib/content-query"
// SH1023 — the view lives in the URL, so the address a curator sends reproduces what they saw.
import { useListState } from "@/hooks/use-list-state"
import { usePostTypes } from "@/hooks/use-catalogue"
import { isCancelled } from "@/lib/read-options"

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
  /** SH1025 — how many child folders the folder holds, whether or not they were paged. */
  totalFolders: number
  /** SH1024 — the filtered posts counted by facet; asked for on the first page only. */
  facets?: FacetCounts
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

/** SH1026 — the row every view draws; it and its label moved beside the renderers. */
type ObjectChild = BrowserRow

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

/**
 * SH703 — one frozen empty set, at module scope on purpose.
 *
 * A `new Set()` per render would be a new identity every time, so every `useMemo` and every child
 * keyed on the selection would recompute on every render of a page that renders a lot.
 */
const EMPTY_SELECTION: Set<string> = new Set<string>()

/** SH1046 — the same argument for the catalogue while it loads: one identity, not one per render. */
const EMPTY_POST_TYPES: ShPostType[] = []

/** SH1021 — the three verbs a selection can take. */
type BulkOp = "PUBLISH" | "UNPUBLISH" | "DELETE"

const unifiedService = new ShPostUnifiedService()

/**
 * SH1023 — every key this list owns, and what each one means when the URL says nothing.
 *
 * The filter names are `ShContentQuery`'s (SH1022), which is the whole point: the address is the
 * request. `sort` and `view` are the console's own — `sort` because the browser's three options
 * map onto the listing's axes, `view` because a grid and a list are the same rows.
 */
/** What the "more filters" badge does not count: the bar shows its own chips for these. */
const MORE_IGNORES = ["sort", "view", "q", "type", "state", "locale", "modifiedSince", "modifiedBefore"] as const

const BROWSER_LIST = {
  q: "", sort: "order", view: "list",
  type: "", state: "", locale: "", missingLocale: "", modifiedSince: "", modifiedBefore: "",
  author: "", modifier: "", agent: "", subtree: "",
}

/** The list state, as the filter shape the request builder and the popover both read. */
function browserFilters(value: Record<keyof typeof BROWSER_LIST, string>): ShContentFilters {
  return {
    q: value.q, type: value.type, state: value.state, locale: value.locale,
    // SH1060 — the coverage question, asked of a folder: which pages here have no pt-br yet.
    missingLocale: value.missingLocale,
    modifiedSince: value.modifiedSince, modifiedBefore: value.modifiedBefore,
    author: value.author, modifier: value.modifier, agent: value.agent, subtree: value.subtree,
  }
}

export default function ObjectChildrenPage() {
  const { objectId } = useParams() as { objectId: string }
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [listData, setListData] = useState<ObjectListResponse>(
    { folders: [], posts: [], page: 0, size: PAGE_SIZE, totalPosts: 0, totalFolders: 0 })
  const [loadingMore, setLoadingMore] = useState(false)
  const [breadcrumbPath, setBreadcrumbPath] = useState<BreadcrumbPathItem[]>([])
  /**
   * SH703 — the selection, tagged with the folder it was made in.
   *
   * <p>It used to be cleared by an effect on `objectId`, which is the case React documents as
   * adjusting state to a prop and answers with a remount. A remount is not available here — the
   * router keeps this page mounted across `/object/:objectId` — so the tag does the same job: a
   * selection belonging to another folder is not the selection, and reads as empty without anybody
   * clearing it after the fact.</p>
   *
   * <p>`setSelectedItems` keeps both call shapes, the value and the updater, so the twelve places
   * that select rows did not have to learn about this.</p>
   */
  const [selection, setSelection] = useState<{ folder: string; ids: Set<string> }>(
    () => ({ folder: objectId, ids: new Set() }),
  )
  const selectedItems = selection.folder === objectId ? selection.ids : EMPTY_SELECTION
  const setSelectedItems = useCallback(
    (next: Set<string> | ((prev: Set<string>) => Set<string>)) =>
      setSelection((prev) => {
        const base = prev.folder === objectId ? prev.ids : EMPTY_SELECTION
        return { folder: objectId, ids: typeof next === "function" ? next(base) : next }
      }),
    [objectId],
  )
  /*
   * SH1046 — the post-type catalogue, read once instead of once per folder.
   *
   * This was a `.query()` inside the effect below, which is keyed on `objectId`: walking a tree
   * cost one full catalogue per folder, for a list that decides a default and fills a filter and
   * does not change while somebody navigates. It is now one cached read under `postType:`, and the
   * default is DERIVED from it rather than pushed into a second state — the saved name is in
   * `localStorage` and the list is in the cache, so there is nothing left for an effect to keep in
   * step (SH703's shape, one screen over).
   */
  const postTypes = usePostTypes().data ?? EMPTY_POST_TYPES
  const lastPostType = useMemo(() => {
    const savedName = localStorage.getItem("sh-last-post-type")
    return postTypes.find((p) => p.name === savedName)
      ?? postTypes.find((p) => !p.system)
      ?? postTypes[0]
      ?? null
  }, [postTypes])
  /**
   * SH703 — which query's rows are on screen. `loading` is **computed** from it rather than set.
   *
   * <p>What the flag meant was "the rows showing are not the rows this query asks for", which is a
   * comparison and was being maintained by hand. Deriving it removes the synchronous `setState` the
   * effect opened with, and it removes a frame with it: the spinner used to appear one render
   * <em>after</em> the sort or the search term changed, because the effect that raised it runs after
   * the paint that showed the stale rows.</p>
   */
  const [loadedQuery, setLoadedQuery] = useState<string | null>(null)
  /**
   * SH1023 — the browser's whole view, in its URL.
   *
   * Sort, view mode, the search term and every filter used to be five pieces of component state
   * and a debounced mirror of one of them. None of it was in the address, so a curator who
   * narrowed a folder to "drafts Ana changed this week" had nothing to send anybody, and an agent
   * handed the link opened the unfiltered folder. The names are `ShContentQuery`'s (SH1022), so
   * the link IS the request: what a curator pastes into a chat is what `shio find` would run.
   */
  const list = useListState(BROWSER_LIST, { text: ["q", "author", "modifier"] })
  const sortBy = list.value.sort
  const viewMode = browserView(list.value.view)
  const filters = browserFilters(list.value)
  /**
   * SH1024 — the bar's value and handler, stable across renders. The bar debounces typing in an
   * effect that depends on both, so a fresh object each render restarts its timer and the query is
   * never sent; measured in a browser, where the page re-renders faster than the 300 ms pause.
   */
  const { q, type: typeFilter, state: stateFilter, locale: localeFilter, modifiedSince, modifiedBefore } = list.value
  const barValue = useMemo(
    () => toBarValue({ q, type: typeFilter, state: stateFilter, locale: localeFilter, modifiedSince, modifiedBefore }),
    [q, typeFilter, stateFilter, localeFilter, modifiedSince, modifiedBefore])
  const patchList = list.patch
  const onBarChange = useCallback((next: BentoFilterValue) => patchList(fromBarValue(next)), [patchList])
  /**
   * SH1024 — the bar's facets: this instance's types, and the locales present in what is listed,
   * each option carrying the server's count. The locales come from the counts when there are some,
   * because those cover the whole listing and the rows on screen are one page of it.
   */
  const counts = listData.facets
  const facets = useMemo(() => withCounts(contentFacets(postTypes,
    counts?.locale ? Object.keys(counts.locale) : listData.posts.map((post) => post.locale ?? ""), {
      type: t("content.filterType"), state: t("content.filterState"), locale: t("content.filterLocale"),
      modified: t("content.filterModified"), draft: t("content.filterDraft"), published: t("content.filterPublished"),
    }), counts), [postTypes, counts, listData.posts, t])
  const [filtersOpen, setFiltersOpen] = useState(false)
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
   * Every folder id this page has seen, across pages and across a select-everything walk.
   *
   * SH1025 — the move/copy dialog used to read the folders out of the rows on screen, which was
   * complete because folders came back whole. They are a page now, and a selection can hold a
   * folder id that is not on screen: SH237's walk puts it there. Intersecting the selection with
   * the *loaded* rows would then miss it, and the dialog would offer a folder as a destination
   * for itself — the recursion SH203 exists to refuse, reintroduced by the paging underneath it.
   */
  const knownFolderIds = useRef<Set<string>>(new Set())
  const rememberFolders = useCallback((rows: ConsoleFolderRow[] | undefined) => {
    for (const row of rows ?? []) knownFolderIds.current.add(row.id)
  }, [])


  /**
   * The identity of the current query — the URL's own list state, plus the folder.
   *
   * SH1023 — it used to be four pieces of state joined by hand, and the search term had a
   * debounced mirror of its own beside it. The hook owns both: `list.key` moves exactly when the
   * committed state does, which is one debounce after the last keystroke rather than on each one.
   */
  const queryKey = `${objectId}|${list.key}`
  const loading = loadedQuery !== queryKey

  /**
   * One page of posts and one page of folders.
   *
   * SH1025 — it used to take the folders whole on every page of posts, which
   * `ShConsoleListingBenchmarkTest` priced at 454 rows and 63 KB against 104 and 19 KB for a
   * folder of four hundred. Asking for a page is opt-in on the server precisely so the move/copy
   * picker's whole-set read stays correct; this list opts in, and everything below that used to
   * rely on holding them all had to be paid for in the same change.
   */
  const fetchPage = useCallback((page: number, signal?: AbortSignal, counted = page === 0) => {
    const params = new URLSearchParams({
      page: String(page),
      size: String(PAGE_SIZE),
      folderPage: String(page),
      folderSize: String(PAGE_SIZE),
      sort: POST_SORT[sortBy] ?? "title",
    })
    // SH1022 — one vocabulary: these are the words `/agent/find` and `shio find` take, and since
    // SH1023 they are the URL's own names too, so this loop is the console link turned into a
    // request without a translation step in between.
    for (const [key, value] of Object.entries(filterParams(filters))) params.set(key, value)
    // SH1024 — the facet counts, with the first page (the next pages are the same listing) and
    // with a re-read after a write, which is what changes them.
    if (counted) params.set("facets", "true")
    return axios.get<ObjectListResponse>(`/v2/object/${objectId}/list?${params}`, { signal })
  }, [objectId, sortBy, filters])

  /** The next page, appended. Only the posts accumulate — the folders are already all here. */
  const loadMore = useCallback(async () => {
    setLoadingMore(true)
    try {
      const res = await fetchPage(listData.page + 1)
      // SH1025 — both halves accumulate now. The folders used to arrive whole on every page, so
      // taking the new response's array was the same set; a page of them would have replaced the
      // rows already on screen with the next fifty and read as a list that had lost its start.
      rememberFolders(res.data.folders)
      setListData((prev) => ({
        ...res.data,
        // SH1024 — a later page carries no counts; they are the first page's, for the whole list.
        facets: prev.facets,
        folders: [...prev.folders, ...(res.data.folders ?? [])],
        posts: [...prev.posts, ...(res.data.posts ?? [])],
      }))
    } finally {
      setLoadingMore(false)
    }
  }, [fetchPage, listData.page])

  // Sort and search re-query from page 0: the server owns both, so changing either changes
  // which rows the first page holds. SH703 — nothing is raised here; `loading` is already true
  // because `queryKey` moved, and it goes false when this query is the one that landed.
  useEffect(() => {
    /*
     * SH1047 — the previous listing is cancelled, and this is the page the race was about.
     *
     * `fetchPage` is rebuilt whenever the folder, the sort or a filter moves, and the request it
     * replaced used to keep running. A curator clicking quickly down a tree could be shown a slow
     * earlier response over a fast later one: the rows belong to a folder they already left, under
     * a breadcrumb that says otherwise, with nothing to notice by. Aborting on cleanup means the
     * superseded request rejects instead of resolving, so `then` never runs for it.
     */
    const abort = new AbortController()
    fetchPage(0, abort.signal)
      .then((res) => {
        rememberFolders(res.data.folders)
        setListData(res.data)
        setLoadedQuery(queryKey)
      })
      // A cancelled read has no answer and needs none: the effect that cancelled it is the one
      // that stopped wanting it, and marking the query "loaded" here would clear the spinner over
      // the rows of a folder nobody is looking at any more.
      .catch((reason: unknown) => { if (!isCancelled(reason)) setLoadedQuery(queryKey) })
    return () => abort.abort()
  }, [fetchPage, queryKey])

  useEffect(() => {
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
  }, [objectId])

  /*
   * SH933 — the effect that mirrored `breadcrumbPath` into the console's trail is gone with the
   * trail. `breadcrumbPath` itself stays: this page draws its own path in its sub-toolbar, which
   * is the one place in the console where a reader moves up and down a tree all day and the only
   * trail the bento era kept.
   */

  /**
   * Folders first, then posts — both in the order the server returned them.
   *
   * SH165 ordered these two halves by different parties and said why: the folders were all
   * present, so sorting them here said something true about the whole set, while the posts were
   * a page and only the server could. **SH1025 paged the folders, which retires the first half
   * of that.** A local sort over a page sorts one page and reads exactly like a correct answer —
   * the half-migration SH165 named — so the sort moved to the database with the paging, in the
   * same change, rather than as a follow-up somebody would have had to notice.
   *
   * They are still two sequences rather than one: merging them would interleave the folder page
   * with the post page and read as a single ordered list of things that were ordered separately.
   */
  const folderItems: ObjectChild[] = listData.folders.map((folder) => ({
    id: folder.id,
    name: folder.name,
    date: folder.date ?? "",
    objectType: "FOLDER" as const,
    position: folder.position,
  }))

  const postItems: ObjectChild[] = listData.posts.map((post) => ({
    id: post.id,
    name: post.title ?? "",
    date: post.date ?? "",
    objectType: "POST" as const,
    postTypeName: post.postTypeTitle || post.postType,
    publishStatus: post.publishStatus,
    locale: post.locale,
  }))

  const combinedItems: ObjectChild[] = [...folderItems, ...postItems]
  const sortedItems = combinedItems
  const loadedPosts = listData.posts.length
  const loadedFolders = listData.folders.length
  const hasMoreRows = loadedPosts < listData.totalPosts
    || loadedFolders < listData.totalFolders

  const toggleSelect = useCallback((id: string) => {
    setSelectedItems((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }, [setSelectedItems])

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

  /**
   * SH1025 — every id of every child folder, for the same reason SH237 needed it for posts.
   *
   * The folder half was exempt from that rule by a fact rather than by an argument: folders came
   * back whole, so "all of them" was already on screen. Paging them takes the fact away, and
   * "Select → Folders" on a folder of four hundred would have selected fifty and let the delete
   * dialog say so — the P7 surprise SH237 removed, reintroduced through the other half.
   */
  const allMatchingFolderIds = useCallback(async (): Promise<string[]> => {
    const ids = listData.folders.map((folder) => folder.id)
    if (ids.length >= listData.totalFolders) return ids
    setLoadingMore(true)
    try {
      let page = listData.page
      while (ids.length < Math.min(listData.totalFolders, SELECT_ALL_CAP)) {
        page += 1
        const res = await fetchPage(page)
        const rows = res.data.folders ?? []
        if (rows.length === 0) break
        rememberFolders(rows)
        ids.push(...rows.map((folder) => folder.id))
      }
      if (ids.length < listData.totalFolders) {
        toast.warning(t("content.selectAllCapped",
          { loaded: ids.length, total: listData.totalFolders }))
      }
      return ids
    } finally {
      setLoadingMore(false)
    }
  }, [fetchPage, listData.page, listData.folders, listData.totalFolders, t])

  const handleSelectAction = async (action: string) => {
    switch (action) {
      case "content":
        setSelectedItems(new Set(await allMatchingPostIds()))
        break
      case "folder":
        // SH1025 — folders are paged now, so this walks them the way SH237 walks the posts.
        setSelectedItems(new Set(await allMatchingFolderIds()))
        break
      case "everything":
        setSelectedItems(new Set([
          ...await allMatchingFolderIds(),
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

  /**
   * SH1020 — the roving focus ring, and every key that moves or acts on it.
   *
   * <p>A listbox puts exactly one row in the tab order, so Tab moves past the list rather than
   * through a hundred rows. Everything else is an arrow: that is the contract a screen reader
   * announces when it reads `role="listbox"`, and a list that claims the role and does not move on
   * arrows is worse than one that claims nothing.</p>
   */
  const [focusedRow, setFocusedRow] = useState(0)
  const [anchorRow, setAnchorRow] = useState(0)
  const rowRefs = useRef<(HTMLDivElement | null)[]>([])
  const searchRef = useRef<HTMLDivElement>(null)

  const focusRow = useCallback((index: number) => {
    const bounded = Math.max(0, Math.min(index, sortedItems.length - 1))
    setFocusedRow(bounded)
    rowRefs.current[bounded]?.focus()
  }, [sortedItems.length])

  /** Every id from the anchor to here — what makes a bulk verb a gesture rather than N clicks. */
  const selectRange = useCallback((from: number, to: number) => {
    const [low, high] = from <= to ? [from, to] : [to, from]
    const ids = sortedItems.slice(low, high + 1).map((item) => item.id)
    setSelectedItems((prev) => {
      const next = new Set(prev)
      ids.forEach((id) => next.add(id))
      return next
    })
  }, [sortedItems, setSelectedItems])

  const onListKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
    const index = focusedRow
    const item = sortedItems[index]
    // SH1026 — a grid is read across as well as down, so there left and right step too.
    switch (event.key) {
      case "ArrowDown":
      case "ArrowUp":
      case "ArrowRight":
      case "ArrowLeft": {
        const step = stepFor(viewMode, event.key)
        if (step === 0) break
        event.preventDefault()
        const next = index + step
        focusRow(next)
        // Shift extends from the anchor, which is where the selection last started — the same
        // rule every file manager uses, and the reason the anchor is state rather than the
        // previous row.
        if (event.shiftKey) selectRange(anchorRow, next)
        else setAnchorRow(Math.max(0, Math.min(next, sortedItems.length - 1)))
        break
      }
      case "Home":
        event.preventDefault()
        focusRow(0)
        break
      case "End":
        event.preventDefault()
        focusRow(sortedItems.length - 1)
        break
      case " ":
        event.preventDefault()
        if (item) {
          toggleSelect(item.id)
          setAnchorRow(index)
        }
        break
      case "Enter":
        event.preventDefault()
        if (item) handleItemClick(item)
        break
      case "Delete":
      case "Backspace":
        event.preventDefault()
        // Through the confirm flow, never straight to the server: Delete is one keystroke from
        // every other key on this list.
        if (item) {
          setDeleteTarget(item)
          setDeleteDialogOpen(true)
        }
        break
      default:
        break
    }
  }

  /** Shift-click extends, exactly as Shift with an arrow does. */
  const onRowClick = (event: React.MouseEvent<HTMLDivElement>, index: number) => {
    if (event.shiftKey) {
      event.preventDefault()
      selectRange(anchorRow, index)
      setFocusedRow(index)
      return
    }
    setAnchorRow(index)
  }

  /**
   * SH1020 — `/` focuses search, which is the one binding that is not about the list.
   *
   * <p>Ignored while a field has focus, or typing a slash into a name would jump the cursor out of
   * the box it was typed in.</p>
   */
  useEffect(() => {
    const onSlash = (event: KeyboardEvent) => {
      if (event.key !== "/" || event.metaKey || event.ctrlKey) return
      const active = document.activeElement
      const editing = active instanceof HTMLInputElement
        || active instanceof HTMLTextAreaElement
        || (active as HTMLElement | null)?.isContentEditable
      if (editing) return
      event.preventDefault()
      // SH1024 — the bar's own text field, which is always on screen now.
      searchRef.current?.querySelector("input")?.focus()
    }
    globalThis.addEventListener("keydown", onSlash)
    return () => globalThis.removeEventListener("keydown", onSlash)
  }, [])

  /**
   * SH1020 — the eight row actions, as data.
   *
   * <p>A list rather than eight buttons in the markup, because the menu takes a list and because
   * the set is now stated in one place: what a row can do was previously readable only by scrolling
   * through the JSX that drew it.</p>
   */
  const rowActions = (item: ObjectChild) => [
    {
      label: t("common.edit"),
      icon: Pencil,
      onSelect: () => navigate(item.objectType === "FOLDER"
        ? `${ROUTES.CONTENT_ROOT}/folder/${item.id}/edit`
        : `${ROUTES.CONTENT_ROOT}/post/${item.id}`),
    },
    { label: t("content.clone"), icon: ClipboardCopy, onSelect: () => handleClone(item.id) },
    { label: t("content.copy"), icon: Copy, onSelect: () => openFolderDialog("copy", [item.id]) },
    { label: t("content.move"), icon: Move, onSelect: () => openFolderDialog("move", [item.id]) },
    {
      label: t("acl.permissions"),
      icon: ShieldCheck,
      onSelect: () => { setPermsTarget(item); setPermsOpen(true) },
    },
    { label: t("content.viewPage"), icon: ExternalLink, onSelect: () => handleViewPage(item.id) },
    {
      label: t("common.delete"),
      icon: Trash2,
      tone: "destructive" as const,
      onSelect: () => { setDeleteTarget(item); setDeleteDialogOpen(true) },
    },
  ]

  /**
   * SH1021 — a bulk verb, planned before it is carried out.
   *
   * <p>The plan is the server's own dry run through the same writer the agent's batch uses, so what
   * the sheet names is exactly what the real run will do — not a second opinion computed here. A
   * selection where one post is refused publishes the rest and says which one did not, with the fix
   * its refusal carries.</p>
   */
  const [bulkPlan, setBulkPlan] = useState<{ op: BulkOp; result: ShBulkResult } | null>(null)
  const [bulkRunning, setBulkRunning] = useState(false)

  const planBulk = async (op: BulkOp) => {
    if (selectedItems.size === 0) return
    try {
      const result = await unifiedService.bulk(op, [...selectedItems], { dryRun: true })
      setBulkPlan({ op, result })
    } catch (error) {
      toast.error(headlineOf(problemOf(error), t("common.error")))
    }
  }

  const applyBulk = async () => {
    if (!bulkPlan) return
    setBulkRunning(true)
    try {
      // The confirm token is the one this plan returned, so a destructive run cannot be carried out
      // against a plan the curator never saw (SH108).
      const result = await unifiedService.bulk(bulkPlan.op, [...selectedItems], {
        dryRun: false,
        confirm: bulkPlan.result.confirm,
      })
      const refused = result.results.filter((change) => change.problem)
      toast.success(t("content.bulkDone", { count: result.changed }))
      if (refused.length) {
        // Named rather than counted: "3 refused" sends a curator to find out which three.
        toast.error(t("content.bulkRefused", {
          count: refused.length,
          names: refused.map((change) => change.address ?? change.id).join(", "),
        }))
      }
      setBulkPlan(null)
      setSelectedItems(new Set())
      await refreshList()
    } catch (error) {
      toast.error(headlineOf(problemOf(error), t("common.error")))
    } finally {
      setBulkRunning(false)
    }
  }

  /**
   * SH1026 — the toolbar's verbs, handed to the table's selection bar. They read `selectedItems`,
   * which the table's `onSelectionChange` has already set to the rows they are chosen on.
   */
  const tableSelectionActions = [
    { id: "publish", label: t("content.publish"), icon: Eye, onSelect: () => planBulk("PUBLISH") },
    { id: "unpublish", label: t("content.unpublish"), icon: Download, onSelect: () => planBulk("UNPUBLISH") },
    { id: "copy", label: t("content.copy"), icon: Copy, onSelect: () => openFolderDialog("copy", [...selectedItems]) },
    { id: "move", label: t("content.move"), icon: Move, onSelect: () => openFolderDialog("move", [...selectedItems]) },
    {
      id: "delete", label: t("common.delete"), icon: Trash2, tone: "destructive" as const,
      onSelect: () => handleSelectAction("delete"),
    },
  ]

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
    // SH953 — the page the curator was on, not page zero. A batch delete on page 14 used to
    // answer by scrolling them back to the top of the tree, which loses the place they were
    // working in and hides whichever rows were refused.
    const res = await fetchPage(listData.page ?? 0, undefined, true)
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
      // SH184: a site with no deployed front end falls back to Shio's own renderer, which
      // is the same rule the site list and the Universal Editor follow — one function, so
      // the three cannot drift.
      window.open(siteDestination(site).url, "_blank", "noopener")
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
   * The move/copy picker's folder list. It asks for `size=1` because it renders no posts and the
   * folders come back whole — one row of content instead of fifty, for a dialog that walks a site
   * folder by folder.
   *
   * SH1025 — it asks for no folder page, which is what keeps that true. Folder paging is opt-in
   * on the server for this caller: a dialog handed one page of a site would let the walk stop
   * above a destination that exists, and there is no "load more" in it to notice with.
   */
  const fetchDialogFolders = (id: string) =>
    axios.get<ObjectListResponse>(`/v2/object/${id}/list?size=1`).then((res) => {
      setFolderDialogFolders((res.data.folders ?? []).map(f => ({ id: f.id, name: f.name })))
    })

  const openFolderDialog = (mode: "copy" | "move", ids: string[]) => {
    setFolderDialogMode(mode)
    setFolderDialogIds(ids)
    // SH203 — both modes, because both refuse this destination: a move would detach the
    // subtree from the site root (SH137) and a copy would recurse into the copies it is making.
    // SH1025 — against every folder id this page has SEEN rather than the rows on screen: the
    // folders are paged now, and SH237's walk can put an id in the selection that no row shows.
    setFolderDialogBlockedIds(new Set(ids.filter((id) => knownFolderIds.current.has(id))))
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
  // SH1025 — the total, not the page: this line is what a curator reads to know how many
  // subfolders a section has, and it would otherwise say fifty for every folder over fifty.
  const folderCount = listData.totalFolders
  // The folder's post count, not the loaded one: the stats bar answers "what is in here", and
  // since SH165 those are two different numbers.
  const postCount = listData.totalPosts

  /**
   * SH1027 — search this folder, in the console's search rather than in this list.
   *
   * The two are different questions and the browser's box is the narrower one: it filters the
   * folder's own rows. This hands the search page a `folder:` address it can scope by, which is
   * what makes its "this folder" and "this folder and below" options reachable at all — the page
   * has no folder of its own to offer.
   *
   * A path from the breadcrumb, not an id: the search takes the vocabulary's own addressing
   * (SH79), and an id in that parameter would be a fourth spelling of where content is.
   */
  const searchHereHref = siteName
    ? `${ROUTES.SEARCH}?${new URLSearchParams({
      folder: `folder:${siteName}/${breadcrumbPath
        .filter((crumb) => crumb.type === "FOLDER")
        .map((crumb) => crumb.name)
        .join("/")}`,
      subtree: "true",
    })}`
    : null

  return (
    <>
      {/*
        SH900 — the two site-level actions ride in the hero as `actions`, beside the title, where
        the save-bar morph expects them.
      */}
      <PageHero
        icon={IconFileText}
        eyebrow={t("sidebar.content")}
        title={siteName || t("content.browsing")}
        urlBase={ROUTES.CONTENT_ROOT}
        actions={siteId ? (
          <>
            {/*
              A hardcoded green, carried over when these actions moved into the hero. The package
              expresses a primary action as a tone, and choosing that tone is still open.
            */}
            <Button
              variant="default"
              size="sm"
              className="bg-green-600 text-white hover:bg-green-700"
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
          </>
        ) : undefined}
      />
      <div className="space-y-4">

        {/* Stats bar */}
        <Panel contentClassName="flex items-center gap-8 py-3 px-6 text-sm text-muted-foreground">
            <div className="flex items-center gap-1.5">
              <Folder className="h-4 w-4" />
              <span>{t("content.folderCount", { count: folderCount })}</span>
            </div>
            <div className="flex items-center gap-1.5">
              <FileText className="h-4 w-4" />
              <span>{t("content.postCount", { count: postCount })}</span>
            </div>
        </Panel>

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

          {/*
            SH1022 — the filters, which are the server's words and not a second set computed here.
            A curator asking "drafts changed this week by Ana" is asking the same question an agent
            asks with `shio find --state draft --modified-since ... --modifier ana`, and it is the
            same query: a client-side filter over a paged listing would narrow one page.
          */}
          <Popover open={filtersOpen} onOpenChange={setFiltersOpen}>
            <PopoverTrigger asChild>
              <Button variant="outline" size="sm" data-testid="sh-filters">
                <SlidersHorizontal className="mr-2 h-4 w-4" /> {t("content.moreFilters")}
                {list.narrowed(MORE_IGNORES) > 0 && (
                  <Badge variant="secondary" className="ml-2 text-[10px]">
                    {list.narrowed(MORE_IGNORES)}
                  </Badge>
                )}
              </Button>
            </PopoverTrigger>
            <PopoverContent align="end" className="w-80 space-y-3">
              <div className="grid grid-cols-2 gap-2">
                <div className="space-y-1">
                  <Label className="text-xs" htmlFor="sh-filter-author">
                    {t("content.filterAuthor")}
                  </Label>
                  <Input id="sh-filter-author" className="h-8 text-xs"
                    value={list.draft.author}
                    onChange={(event) => list.set("author", event.target.value)} />
                </div>
                <div className="space-y-1">
                  <Label className="text-xs" htmlFor="sh-filter-modifier">
                    {t("content.filterModifier")}
                  </Label>
                  <Input id="sh-filter-modifier" className="h-8 text-xs"
                    value={list.draft.modifier}
                    onChange={(event) => list.set("modifier", event.target.value)} />
                </div>
              </div>
              {/*
                SH1024 — the author picker's agent option: what an agent run wrote, read off the
                audit trail by the vocabulary's `agent` word, so this is the question `shio find
                --agent true` asks. A checkbox rather than a value in the author field, because an
                agent is not an owner: its writes carry the credential's user, and a name typed
                there would never match one.
              */}
              <label className="flex items-center gap-2 text-xs">
                <Checkbox checked={list.value.agent === "true"}
                  onCheckedChange={(checked) => list.set("agent", checked ? "true" : "")} />
                {t("content.filterAgent")}
              </label>
              <div className="flex items-center justify-between">
                {/* The subtree word, spelled as the server spells it: unsaid means this folder. */}
                <label className="flex items-center gap-2 text-xs">
                  <Checkbox checked={list.value.subtree === "true"}
                    onCheckedChange={(checked) => list.set("subtree", checked ? "true" : "")} />
                  {t("content.filterSubtree")}
                </label>
                <Button variant="ghost" size="sm" onClick={list.clear}>
                  {t("content.filterClear")}
                </Button>
              </div>
            </PopoverContent>
          </Popover>

          {/* Right: Sort + Select + Batch + Download */}
          <Select value={sortBy} onValueChange={(value) => list.set("sort", value)}>
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
              {/*
                SH1021 — the verbs an agent has had since shio_publish shipped. Every one opens the
                plan first: a bulk verb is the one shape where a mistake is multiplied by the size
                of the selection.
              */}
              <DropdownMenuItem onClick={() => planBulk("PUBLISH")}><Eye className="mr-2 h-4 w-4" /> {t("content.publish")}</DropdownMenuItem>
              <DropdownMenuItem onClick={() => planBulk("UNPUBLISH")}><Download className="mr-2 h-4 w-4" /> {t("content.unpublish")}</DropdownMenuItem>
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
        <Panel contentClassName="flex items-center gap-2 py-2 px-4">
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
              {searchHereHref && (
                <Tooltip>
                  <TooltipTrigger asChild>
                    <Button variant="ghost" size="icon" className="h-7 w-7"
                      aria-label={t("content.searchHere")}
                      onClick={() => navigate(searchHereHref)}>
                      <SlidersHorizontal className="h-4 w-4 rotate-90" />
                    </Button>
                  </TooltipTrigger>
                  <TooltipContent>{t("content.searchHere")}</TooltipContent>
                </Tooltip>
              )}
              {/*
                SH1026 — the three views, named and pressed. They used to be three unnamed icons
                whose only effect was their own colour; now each draws the list its own way.
              */}
              <Button variant="ghost" size="icon" className={`h-7 w-7 ${viewMode === "list" ? "bg-muted" : ""}`}
                aria-label={t("content.viewAsList")} aria-pressed={viewMode === "list" ? "true" : "false"}
                onClick={() => list.set("view", "list")}>
                <List className="h-4 w-4" />
              </Button>
              <Button variant="ghost" size="icon" className={`h-7 w-7 ${viewMode === "grid" ? "bg-muted" : ""}`}
                aria-label={t("content.viewAsGrid")} aria-pressed={viewMode === "grid" ? "true" : "false"}
                onClick={() => list.set("view", "grid")}>
                <LayoutGrid className="h-4 w-4" />
              </Button>
              <Button variant="ghost" size="icon" className={`h-7 w-7 ${viewMode === "spreadsheet" ? "bg-muted" : ""}`}
                aria-label={t("content.viewAsTable")} aria-pressed={viewMode === "spreadsheet" ? "true" : "false"}
                onClick={() => list.set("view", "spreadsheet")}>
                <Sheet className="h-4 w-4" />
              </Button>
            </div>
        </Panel>

        {/*
          SH1024 — the design system's filter bar, over the words this list already puts in its URL
          (SH1022, SH1023). It owns nothing: its value is read from the address and every change is
          one patch back to it, so a filtered view is a link the moment it is built. SH1020's `/`
          focuses its text field.
        */}
        <div ref={searchRef}>
          <BentoFilterBar
            value={barValue}
            onChange={onBarChange}
            facets={facets}
            queryLabel={t("content.filterItems")}
          />
        </div>

        {/* Content list */}
        <Panel className="overflow-hidden" contentClassName="p-0">
            {loading && (
              <div className="px-4 py-12 text-center text-sm text-muted-foreground">{t("common.loading")}</div>
            )}

            {/*
              SH1020 — a real listbox. The rows carried no role, no tabindex and no selected state,
              so a keyboard user could open a post and do nothing else to it: every one of the eight
              row actions lived inside a block that was `hidden` until a pointer hovered it.

              `aria-multiselectable` because the checkbox column has always been a multi-select —
              the semantics were the part that was missing, not the behaviour.
            */}
            {/*
              SH1026 — the table is the design system's data table: a virtualised body, sortable
              headers and a column picker. It keeps its own selection and keyboard and reports the
              selection here, so the toolbar's verbs and the selection bar still act on it.

              A static import on purpose. The design system's bento entry is one module, so the
              table's code lands in its eager chunk whichever way this file imports it: lazy()
              was measured at 1673.5 KB raw against 1671.2 KB static, and it split the eager
              load into four regrouped chunks. An empty folder says so once, below, in every view.
            */}
            {!loading && viewMode === "spreadsheet" && sortedItems.length > 0 && (
              <BrowserTable
                rows={sortedItems}
                scope={`${objectId}|${list.key}`}
                actionsFor={rowActions}
                selectionActions={tableSelectionActions}
                onSelectionChange={(ids) => setSelectedItems(new Set(ids))}
                onOpen={handleItemClick}
                onEdited={() => void refreshList()}
              />
            )}
            {viewMode !== "spreadsheet" && (
            <div
              role="listbox"
              aria-multiselectable="true"
              aria-label={t("content.browserList")}
              aria-orientation={viewMode === "grid" ? "horizontal" : "vertical"}
              onKeyDown={onListKeyDown}
              data-view={viewMode}
              className={listboxClass(viewMode)}
            >
            {!loading && sortedItems.map((item, index) => (
              <div
                key={item.id}
                // One row is in the tab order at a time (a roving tabindex), so Tab moves past the
                // list rather than through every row of a hundred-row page.
                role="option"
                aria-selected={selectedItems.has(item.id) ? "true" : "false"}
                tabIndex={index === focusedRow ? 0 : -1}
                ref={(node) => { rowRefs.current[index] = node }}
                onFocus={() => setFocusedRow(index)}
                onClick={(event) => onRowClick(event, index)}
                className={`group ${optionClass(viewMode)} transition-colors outline-none focus-visible:ring-2 focus-visible:ring-ring ${selectedItems.has(item.id) ? "bg-primary/5" : ""}`}
              >
                {/*
                  SH1026 — the one thing the view decides: how this row is drawn. SH1020's eight
                  actions stay in the design system's menu with a focusable trigger in every view.
                */}
                <BrowserItem
                  view={viewMode}
                  item={item}
                  index={index}
                  selected={selectedItems.has(item.id)}
                  when={formatDate(item.date)}
                  siteName={siteName ?? undefined}
                  actions={rowActions(item)}
                  onToggle={() => toggleSelect(item.id)}
                  onOpen={() => handleItemClick(item)}
                />
              </div>
            ))}
            </div>
            )}

            {!loading && sortedItems.length === 0 && (
              <div className="px-4 py-16 text-center">
                <Folder className="h-12 w-12 mx-auto text-muted-foreground/30 mb-3" />
                <p className="text-sm text-muted-foreground">{t("common.noResults")}</p>
                <p className="text-xs text-muted-foreground/70 mt-1">{t("content.createFolderOrPost")}</p>
              </div>
            )}

            {/* SH165: the page boundary, stated. A list that just stopped at fifty rows would
                look complete, which is the failure mode paging introduces. SH1025 — it states
                both halves now, because the folders are a page too and a curator who cannot see
                their subfolder is owed the same sentence. */}
            {!loading && hasMoreRows && (
              <div className="flex flex-col items-center gap-2 border-t px-4 py-4">
                <span className="text-xs text-muted-foreground">
                  {t("content.showingRows", {
                    loadedFolders, totalFolders: listData.totalFolders,
                    loaded: loadedPosts, total: listData.totalPosts,
                  })}
                </span>
                <Button variant="outline" size="sm" disabled={loadingMore} onClick={loadMore}>
                  {loadingMore ? t("common.loading") : t("content.loadMore")}
                </Button>
              </div>
            )}
        </Panel>

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
      {/*
        SH1021 — the plan, as a sheet naming what will change. It is the SERVER's dry run through
        the same writer the real run uses, so what this names is what will happen — not a second
        opinion computed in the browser. Refusals are grouped and each carries the fix its problem
        document came with, because "3 refused" sends a curator to find out which three.
      */}
      <Dialog open={bulkPlan !== null} onOpenChange={(open) => !open && setBulkPlan(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {t("content.bulkPlanTitle", {
                op: bulkPlan ? t(`content.bulkOp.${bulkPlan.op}`) : "",
                count: bulkPlan?.result.changed ?? 0,
              })}
            </DialogTitle>
          </DialogHeader>
          <div className="space-y-2 max-h-80 overflow-auto text-sm">
            {(bulkPlan?.result.results ?? []).map((change) => (
              <div key={`${change.op}-${change.address ?? change.id}`}
                className="flex items-start gap-2">
                <Badge variant={change.problem ? "destructive" : "secondary"}
                  className="text-[10px] shrink-0">
                  {change.problem ? t("content.bulkRefusedBadge") : change.action}
                </Badge>
                <div className="min-w-0">
                  <div className="truncate">{change.address ?? change.id}</div>
                  {change.problem && (
                    <p className="text-xs text-muted-foreground">
                      {change.problem.title}{change.problem.fix ? ` — ${change.problem.fix}` : ""}
                    </p>
                  )}
                </div>
              </div>
            ))}
          </div>
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setBulkPlan(null)}>{t("common.cancel")}</Button>
            <Button onClick={applyBulk} disabled={bulkRunning}>
              {bulkRunning ? t("common.loading") : t("content.bulkApply")}
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      <DialogDelete
        feature={deleteTarget ? (deleteTarget.objectType === "FOLDER" ? t("content.folder") : t("content.postLabel")) : t("content.batchContent")}
        name={deleteTarget?.name || `${selectedItems.size} item(s)`}
        open={deleteDialogOpen}
        setOpen={setDeleteDialogOpen}
        onDelete={async () => {
          // SH953 — every outcome is collected, and the number announced is the number that
          // actually went. This used to `.catch(() => {})` each delete and then report the
          // SELECTION's size, so a thousand refusals announced a thousand deletions.
          const targets: DeleteTarget[] = deleteTarget
            ? [{ id: deleteTarget.id, name: deleteTarget.name, objectType: deleteTarget.objectType }]
            : [...selectedItems].map(id => {
              // An id with no loaded row came from SH237's select-everything, which only ever
              // collects posts — so the POST default is the right one rather than a guess.
              const item = combinedItems.find(i => i.id === id)
              return { id, name: item?.name, objectType: item?.objectType || "POST" }
            })

          const outcome = await deleteEach(targets, target => deleteObject(target.id, target.objectType))

          if (outcome.refusals.length > 0) {
            toast.error(t("content.itemsRefused", {
              count: outcome.refusals.length,
              reasons: refusalSummary(outcome.refusals),
            }))
          }
          if (outcome.deleted > 0) {
            toast.success(deleteTarget
              ? t("content.deletedShort")
              : t("content.itemsDeleted", { count: outcome.deleted }))
          }
          // Only what went is deselected: a row that refused stays selected, so the curator can
          // read the reason and try again without finding it again.
          if (!deleteTarget) {
            const refused = new Set(outcome.refusals.map(refusal => refusal.id))
            setSelectedItems(refused)
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
