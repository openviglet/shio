/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH1145 — everything the browser reads: the page, the next page, the re-read after a write.
 *
 * <h2>Why this is a hook and not four hundred more lines of the page</h2>
 *
 * <p>SH1026's design asked for the browser page to be split "because at that size the next feature
 * makes it unreviewable", and moved what each view *draws*. This is the other half. The page kept
 * three concerns tangled in one component — the listing's reads, the listbox keyboard, and seven
 * dialogs — and the cost was never only size: SH1026's own live check found the listing effect
 * re-reading page 0 after every render, because a value built in the component body fed a callback
 * the effect depended on. Nothing in a file that size makes that visible, and the lint that could
 * have seen it was answering about a different line.</p>
 *
 * <p>What that argues for is a unit whose <b>dependencies are the whole of its inputs</b>. This one
 * takes the folder, the committed query and the sort; everything else it owns. A reviewer can check
 * every `useCallback` against the four lines above it.</p>
 */
import axios from "axios"
import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { toast } from "@viglet/viglet-design-system"

import { filterParams } from "@/lib/content-query"
import { isCancelled } from "@/lib/read-options"
import { useSiteLocalePolicy } from "@/hooks/use-catalogue"

import {
  PAGE_SIZE, POST_SORT, SELECT_ALL_CAP, browserFilters,
  type BreadcrumbPathItem, type BROWSER_LIST as BrowserListShape, type ConsoleFolderRow,
  type ObjectChild, type ObjectListResponse,
} from "./browser-listing"
import { breadcrumbFrom, declaredDefaultLocale } from "./browser-view"

/** What the page hands in: where it is, and which query is committed. */
export interface BrowserListing {
  objectId: string
  /** `useListState`'s committed query, as a string — the identity the reads are keyed on. */
  listKey: string
  sortBy: string
  t: (key: string, options?: Record<string, unknown>) => string
}

export function useBrowserListing({ objectId, listKey, sortBy, t }: BrowserListing) {
  const [listData, setListData] = useState<ObjectListResponse>(
    { folders: [], posts: [], page: 0, size: PAGE_SIZE, totalPosts: 0, totalFolders: 0 })
  const [loadingMore, setLoadingMore] = useState(false)
  const [breadcrumbPath, setBreadcrumbPath] = useState<BreadcrumbPathItem[]>([])

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

  /*
   * SH1026 — derived from `list.key`, the committed query as a string, so it is a new object only
   * when the query is. Built from `list.value` on every render it was new every render, which made
   * `fetchPage` new every render and the listing effect re-read page 0 after each one: measured in
   * a browser, a continuous stream of `/list?page=0` that also threw away every "load more".
   */
  const filters = useMemo(
    () => browserFilters(JSON.parse(listKey) as Record<keyof typeof BrowserListShape, string>),
    [listKey])

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
  const queryKey = `${objectId}|${listKey}`
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
      // SH120: `ShCdaPath` — { site, breadcrumb: [...folders], currentFolder }.
      setBreadcrumbPath(breadcrumbFrom(res.data))
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
  // SH120: the site comes from /path, whose breadcrumb opens with it. The listing used to
  // repeat it, which meant two endpoints answering "which site is this" — and the listing's
  // copy was the one that went stale first, because it is the response people add fields to.
  //
  // Read here rather than below the mappings since SH1125: the post mapping needs the site to know
  // which of two translations is the site's own language.
  const site = breadcrumbPath.find((crumb) => crumb.type === "SITE")
  const siteId = site?.id ?? null
  const siteName = site?.name ?? null
  /*
   * SH1125 — one read of the site's locale policy per site the curator opens, cached under the same
   * key the query layer already uses for the site (`useSiteLocalePolicy`), so walking a tree inside
   * one site asks once. Only a DECLARED default marks a row — see `declaredDefaultLocale`.
   */
  const localePolicy = useSiteLocalePolicy(siteId ?? undefined)
  const defaultLocale = declaredDefaultLocale(localePolicy.data)

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
    // SH1125 — whether this is the language the site declared as its own, computed once here rather
    // than at each of the four places a row label is built. `declaredDefaultLocale` is what keeps an
    // UNdeclared policy from marking anything (SH1056): a site that chose nothing still reports a
    // default, and a chip saying "default" about that would be the confusion SH1056 removes.
    isDefaultLocale: Boolean(post.locale && defaultLocale && post.locale === defaultLocale),
    thumbnail: post.thumbnail,
  }))

  const combinedItems: ObjectChild[] = [...folderItems, ...postItems]
  const sortedItems = combinedItems
  const loadedPosts = listData.posts.length
  const loadedFolders = listData.folders.length
  const hasMoreRows = loadedPosts < listData.totalPosts
    || loadedFolders < listData.totalFolders


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


  // SH1025 — the total, not the page: this line is what a curator reads to know how many
  // subfolders a section has, and it would otherwise say fifty for every folder over fifty.
  const folderCount = listData.totalFolders
  // The folder's post count, not the loaded one: the stats bar answers "what is in here", and
  // since SH165 those are two different numbers.
  const postCount = listData.totalPosts


  return {
    listData, setListData, loading, loadingMore, breadcrumbPath,
    fetchPage, loadMore, refreshList, rememberFolders, knownFolderIds,
    allMatchingPostIds, allMatchingFolderIds,
    folderItems, postItems, combinedItems, sortedItems, hasMoreRows,
    loadedPosts, loadedFolders,
    site, siteId, siteName, folderCount, postCount,
    // SH1125 — the page passes it to the row views, which mark the chip with it.
    defaultLocale,
  }
}
