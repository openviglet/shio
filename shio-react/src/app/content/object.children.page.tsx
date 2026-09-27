import { Button } from "@/components/ui/button"
import { Panel } from "@/components/panel"
import { PageHero } from "@/components/page-hero"
import { Separator } from "@/components/ui/separator"
import { ROUTES } from "@/app/routes.const"
import { IconFileText } from "@tabler/icons-react"
import { DialogDelete } from "@/components/dialog.delete"
import { PermissionsDialog } from "@/components/permissions.dialog"
import { Folder, FileText, Download, Eye, Settings, Copy, Move, Trash2 } from "lucide-react"
// SH1020 — the row actions live in the design system's menu, which is where the site list put
// its four in its own sweep.
import { BentoFilterBar, type BentoFilterValue } from "@viglet/viglet-design-system/bento"
import { contentFacets, fromBarValue, toBarValue, withCounts } from "@/lib/filter-bar"
import {
  browserView, listboxClass, optionClass, relativeDate, searchHereFor,
} from "./content-browser/browser-view"
import { BrowserItem } from "./content-browser/browser-views"
import { useBrowserKeyboard } from "./content-browser/use-browser-keyboard"
import { useBrowserListing } from "./content-browser/use-browser-listing"
import { FolderPickerDialog, type FolderPickerRequest } from "./content-browser/folder-picker.dialog"
import { BulkPlanDialog } from "./content-browser/bulk-plan.dialog"
import { BrowserToolbar } from "./content-browser/browser-toolbar"
import { useBulkVerbs } from "./content-browser/use-bulk-verbs"
import { useBrowserOpen } from "./content-browser/use-browser-open"
import { rowActionsFor } from "./content-browser/row-actions"
import { BrowserTable } from "./content-browser/browser-table"
import {
  BROWSER_LIST, EMPTY_POST_TYPES, EMPTY_SELECTION,
  type ObjectChild,
} from "./content-browser/browser-listing"
import { useState, useCallback, useMemo } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import axios from "axios"
import { toast } from "@viglet/viglet-design-system"
import { deleteEach, refusalSummary, type DeleteTarget } from "@/lib/batch-delete"
// SH1021 — the bulk verbs go through the writer the agent's batch runs through.
// SH1022 — the content-query vocabulary, spelled here exactly as the server spells it.
// SH1023 — the view lives in the URL, so the address a curator sends reproduces what they saw.
import { useListState } from "@/hooks/use-list-state"
import { usePostTypes } from "@/hooks/use-catalogue"
import { SavedViews } from "@/components/saved-views"


export default function ObjectChildrenPage() {
  const { objectId } = useParams() as { objectId: string }
  const { t } = useTranslation()
  const navigate = useNavigate()
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
  /*
   * SH1145 — every read this page makes, in one unit whose dependencies are the whole of its
   * inputs: the folder, the committed query and the sort. It was four hundred lines inside this
   * component, which is where SH1026's live check found the listing effect re-reading page 0 after
   * every render and nothing made it visible.
   */
  const {
    listData, loading, loadingMore, breadcrumbPath, loadMore, refreshList,
    knownFolderIds, allMatchingPostIds, allMatchingFolderIds,
    combinedItems, sortedItems, hasMoreRows, loadedPosts, loadedFolders,
    siteId, siteName, folderCount, postCount,
  } = useBrowserListing({ objectId, listKey: list.key, sortBy, t })

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
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<ObjectChild | null>(null)
  const [permsTarget, setPermsTarget] = useState<ObjectChild | null>(null)
  const [permsOpen, setPermsOpen] = useState(false)
  /*
   * SH1145 — the picker's own seven pieces of state went with it. What stays here is the request:
   * which rows, which verb, and which of them are folders that cannot be their own destination.
   */
  const [picker, setPicker] = useState<FolderPickerRequest | null>(null)
  /*
   * SH1025 — against every folder id this page has SEEN rather than the rows on screen: the folders
   * are paged now, and SH237's walk can put an id in the selection that no row shows. Intersecting
   * with the loaded rows would miss it, and the dialog would offer a folder as a destination for
   * itself — the recursion SH203 refuses, reintroduced by the paging underneath it.
   */
  const blockedFolderIds = useCallback(
    (ids: string[]) => new Set(ids.filter((id) => knownFolderIds.current.has(id))),
    [knownFolderIds])


  const toggleSelect = useCallback((id: string) => {
    setSelectedItems((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }, [setSelectedItems])

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

  /*
   * SH1145 — the listbox is `use-browser-keyboard`: six pieces of state, three refs and five
   * callbacks that only ever talked to each other. What it needs from here is what a key ends up
   * doing, which is the whole of the coupling and is now written down.
   */
  const {
    setFocusedRow, rowRefs, searchRef, listRef, rowWindow, tabStop, onListKeyDown, onRowClick,
  } = useBrowserKeyboard({
    rows: sortedItems,
    view: viewMode,
    toggleSelect,
    addToSelection: useCallback((ids: string[]) => setSelectedItems((prev) => {
      const next = new Set(prev)
      ids.forEach((id) => next.add(id))
      return next
    }), [setSelectedItems]),
    onOpen: (item) => handleItemClick(item as ObjectChild),
    onDelete: (item) => { setDeleteTarget(item as ObjectChild); setDeleteDialogOpen(true) },
  })

  // SH1145 — the seven verbs a row offers are `row-actions`, one object of handlers wide.
  const rowActions = rowActionsFor({
    navigate, t,
    onClone: (id) => void handleClone(id),
    onPick: (mode, ids) => setPicker({ mode, ids, blockedIds: blockedFolderIds(ids) }),
    onPermissions: (item) => { setPermsTarget(item); setPermsOpen(true) },
    onViewPage: (id) => void handleViewPage(id),
    onDelete: (item) => { setDeleteTarget(item); setDeleteDialogOpen(true) },
  })

  /*
   * SH1145 — the plan, the run and the two pieces of state between them are `use-bulk-verbs`.
   */
  const { bulkPlan, setBulkPlan, bulkRunning, planBulk, applyBulk } = useBulkVerbs({
    selectedItems,
    clearSelection: () => setSelectedItems(new Set()),
    refreshList,
    t,
  })

  /**
   * SH1026 — the toolbar's verbs, handed to the table's selection bar. They read `selectedItems`,
   * which the table's `onSelectionChange` has already set to the rows they are chosen on.
   */
  const tableSelectionActions = [
    { id: "publish", label: t("content.publish"), icon: Eye, onSelect: () => planBulk("PUBLISH") },
    { id: "unpublish", label: t("content.unpublish"), icon: Download, onSelect: () => planBulk("UNPUBLISH") },
    { id: "copy", label: t("content.copy"), icon: Copy, onSelect: () => setPicker({ mode: "copy", ids: [...selectedItems], blockedIds: blockedFolderIds([...selectedItems]) }) },
    { id: "move", label: t("content.move"), icon: Move, onSelect: () => setPicker({ mode: "move", ids: [...selectedItems], blockedIds: blockedFolderIds([...selectedItems]) }) },
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

  /*
   * SH1145 — the three verbs that leave this list: duplicate a row, open the site, open a page.
   */
  const { handleClone, handleViewSite, handleViewPage } = useBrowserOpen({
    objectId, siteId, refreshList, t,
  })

  // SH1027 — the search page's `folder:` address for this folder, or null before the breadcrumb
  // has landed. Pure, and beside the other pure view helpers since SH1145.
  const searchHereHref = searchHereFor(siteName, breadcrumbPath)

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
              className="bg-green-700 text-white hover:bg-green-800"
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
        {/*
          SH1145 — both toolbars are `BrowserToolbar`: markup with no state of its own, driven by
          the list state, the facets the listing returned and the page's verbs.
        */}
        <BrowserToolbar
          objectId={objectId}
          siteId={siteId}
          list={list}
          sortBy={sortBy}
          viewMode={viewMode}
          lastPostType={lastPostType}
          breadcrumbPath={breadcrumbPath}
          searchHereHref={searchHereHref}
          selectedItems={selectedItems}
          onSelectAction={(action) => void handleSelectAction(action)}
          onBatchAction={(action) => void handleBatchAction(action)}
          onPlanBulk={(op) => void planBulk(op)}
          onPick={(mode, ids) => setPicker({ mode, ids, blockedIds: blockedFolderIds(ids) })}
          navigate={navigate}
          t={t}
        />

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
            views={<SavedViews list="browser" />}
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
            // eslint-disable-next-line jsx-a11y/interactive-supports-focus -- roving focus: the rows are the tab stops, not the grid (SH1020)
            <div
              // SH1054 — a grid: an option may not hold the row's controls; a grid row may (SH1020's keys unchanged).
              role="grid"
              aria-multiselectable="true"
              aria-label={t("content.browserList")}
              aria-rowcount={sortedItems.length}
              onKeyDown={onListKeyDown}
              data-view={viewMode}
              ref={listRef}
              className={listboxClass(viewMode)}
            >
            {/* SH1026 — the unmounted rows above the window, as the space they would take. */}
            {!loading && rowWindow.before > 0 && (
              <div aria-hidden="true" style={{ height: rowWindow.before, gridColumn: "1 / -1" }} />
            )}
            {!loading && sortedItems.slice(rowWindow.start, rowWindow.end).map((item, offset) => {
              const index = rowWindow.start + offset
              return (
              // eslint-disable-next-line jsx-a11y/click-events-have-key-events -- the grid's onKeyDown is the row's keyboard (SH1020)
              <div
                key={item.id}
                // One row is in the tab order at a time (a roving tabindex), so Tab moves past the
                // list rather than through every row of a hundred-row page. When the focused row is
                // outside the window, the first mounted row holds the tab stop instead.
                role="row"
                aria-selected={selectedItems.has(item.id) ? "true" : "false"}
                // Its place in the whole list, since a screen reader could count only the mounted rows.
                aria-rowindex={index + 1}
                tabIndex={index === tabStop ? 0 : -1}
                ref={(node) => { rowRefs.current[index] = node }}
                onFocus={() => setFocusedRow(index)}
                onClick={(event) => onRowClick(event, index)}
                className={`group ${optionClass(viewMode)} transition-colors outline-none focus-visible:ring-2 focus-visible:ring-ring ${selectedItems.has(item.id) ? "bg-primary/5" : ""}`}
              >
                {/*
                  SH1026 — the one thing the view decides: how this row is drawn. SH1020's eight
                  actions stay in the design system's menu with a focusable trigger in every view.
                */}
                <div role="gridcell" className="contents">
                  <BrowserItem
                    view={viewMode}
                    item={item}
                    index={index}
                    selected={selectedItems.has(item.id)}
                    when={relativeDate(item.date)}
                    siteName={siteName ?? undefined}
                    actions={rowActions(item)}
                    onToggle={() => toggleSelect(item.id)}
                    onOpen={() => handleItemClick(item)}
                  />
                </div>
              </div>
              )
            })}
            {!loading && rowWindow.after > 0 && (
              <div aria-hidden="true" style={{ height: rowWindow.after, gridColumn: "1 / -1" }} />
            )}
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

        {/*
          Selection info bar — SH1142: not in the table view, for the same reason as the menu above.
          Its Clear writes `selectedItems` and the table would go on showing the rows ticked, and the
          count beside it would be a second count next to the one the table's own selection bar
          draws. Escape clears there.
        */}
        {viewMode !== "spreadsheet" && selectedItems.size > 0 && (
          <div className="flex items-center gap-3 text-sm text-muted-foreground">
            <span>{t("content.itemsSelected", { count: selectedItems.size })}</span>
            <Separator orientation="vertical" className="h-4" />
            <Button variant="ghost" size="sm" className="h-7 text-xs" onClick={() => setSelectedItems(new Set())}>
              {t("content.clearSelection")}
            </Button>
          </div>
        )}
      </div>

      {/*
        SH203 — where a copy or a move is going. Mounted only while it is open, so the walk starts
        at the site root every time rather than keeping the last one in state nobody reset.
      */}
      {picker && (
        <FolderPickerDialog
          request={picker}
          startId={siteId || objectId}
          objectId={objectId}
          t={t}
          onClose={() => setPicker(null)}
          onDone={async () => {
            setPicker(null)
            setSelectedItems(new Set())
            await refreshList()
          }}
        />
      )}

      {/* Delete confirmation dialog */}
      {bulkPlan && (
        <BulkPlanDialog
          plan={bulkPlan}
          running={bulkRunning}
          t={t}
          onCancel={() => setBulkPlan(null)}
          onApply={() => void applyBulk()}
        />
      )}

      <DialogDelete
        // SH1054 — opened from the row menu and the batch bar, never from a trigger of its own. With
        // none given, the package draws a default: a trash icon with no name, at the foot of the page.
        trigger={null}
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
