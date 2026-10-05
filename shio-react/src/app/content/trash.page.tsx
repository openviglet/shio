import { IconRestore, IconTrash, IconTrashX } from "@tabler/icons-react"
import { EmptyState } from "@/components/empty-state"
import { PageHero } from "@/components/page-hero"
import { Badge } from "@/components/ui/badge"
import { ROUTES } from "@/app/routes.const"
import { Folder, FileText, Globe } from "lucide-react"
import { useCallback, useEffect, useMemo, useState } from "react"
import {
  BentoChangeCell, BentoDataTable, BentoFilterBar, type BentoDataTableAction,
  type BentoDataTableColumn, type BentoFilterFacet, type BentoFilterValue,
} from "@viglet/viglet-design-system/bento"
import { FACET_TYPE, countLocally, fromBarValue, matchesLocally, toBarValue, withCounts } from "@/lib/filter-bar"
// SH1024 — the trash's filters live in its URL, like every other list's (SH1023).
import { useListState } from "@/hooks/use-list-state"
import { useTranslation } from "react-i18next"
import { toast } from "@viglet/viglet-design-system"
import { ShPostUnifiedService } from "@/services/post-unified/post-unified.service"
import { ShFolderService } from "@/services/folder/folder.service"
import { ShSiteService } from "@/services/site/site.service"
import { isCancelled } from "@/lib/read-options"
import type { ShPostUnified } from "@/models/post-unified/post-unified"
import type { ShFolder } from "@/models/folder/folder"
import type { ShSite } from "@/models/site/site"
import { SavedViews } from "@/components/saved-views"

/**
 * One read failing must not hide the other two, and a cancellation must not read as a failure.
 *
 * SH1047 — the bare `catch(() => [])` this replaced was right about the first and blind to the
 * second: an aborted read would have become an empty list beside two real ones, so leaving the
 * page mid-load emptied the trash it was drawing.
 */
function rethrowCancel(reason: unknown): never[] {
  if (isCancelled(reason)) throw reason
  return []
}

const postService = new ShPostUnifiedService()
const folderService = new ShFolderService()
const siteService = new ShSiteService()

/**
 * One row of the trash. Posts, deleted folder sections and deleted sites share it (SH200, SH648):
 * the curator threw away one thing and wants it back, and which table the row came from is not
 * what they are looking for. `kind` is what routes restore and purge to the right endpoint.
 */
type TrashRow = {
  kind: "post" | "folder" | "site"
  id: string
  title: string
  label?: string
  /** A post's type name, the word `type` filters by; a folder or a site has none. */
  type?: string
  /** SH1227 — when it went into the trash, where the server says. */
  deleted?: string
}

/** The row's key: an id is unique within its kind, and three kinds share the list. */
const rowKey = (row: TrashRow) => `${row.kind}:${row.id}`

/**
 * SH1024 — the trash's filters, in its URL, in the vocabulary's own words (`q`, `type`).
 *
 * Applied to the rows already here rather than sent: the trash is one bounded list of three kinds
 * read whole, and only one of those three reads speaks the vocabulary, so a server-side `q` would
 * filter the posts and leave every deleted folder and site on screen.
 */
const TRASH_LIST = { q: "", type: "" }

export default function TrashPage() {
  const { t } = useTranslation()
  const [items, setItems] = useState<TrashRow[]>([])
  const [loading, setLoading] = useState(true)
  const list = useListState(TRASH_LIST)
  const { q, type } = list.value

  /*
   * SH1024 — the design system's filter bar, as on the browser and search: its value is the URL's,
   * and both are stable because the bar debounces typing in an effect keyed on them. The type facet
   * offers only the post types in the trash, so a choice can never empty it by construction.
   */
  const barValue = useMemo(() => toBarValue({ q, type }), [q, type])
  const patchList = list.patch
  const onBarChange = useCallback(
    (next: BentoFilterValue) => {
      const { q: nextQ, type: nextType } = fromBarValue(next)
      patchList({ q: nextQ, type: nextType })
    },
    [patchList])
  const facets = useMemo<BentoFilterFacet[]>(() => {
    const types = [...new Map(items.filter((row) => row.type)
      .map((row) => [row.type as string, row.label || row.type as string])).entries()]
      .sort(([left], [right]) => left.localeCompare(right))
    // SH1024 — counted over the rows `q` leaves, so a type's count is what choosing it lists.
    const counts = { type: countLocally(items.filter((row) => matchesLocally(row, q, "")), (row) => [row.type]) }
    return types.length > 1
      ? withCounts([{ id: FACET_TYPE, kind: "choice", label: t("content.filterType"),
        options: types.map(([value, label]) => ({ value, label })) }], counts, barValue)
      : []
  }, [items, q, barValue, t])
  const shown = useMemo(() => items.filter((row) => matchesLocally(row, q, type)), [items, q, type])

  /** The read. It raises no state before the request, which is what lets the mount effect call it. */
  const fetchTrash = useCallback((signal?: AbortSignal) => {
    // All three in one list. A read that fails must not hide the others' rows, so each falls
    // back to empty instead of rejecting the set.
    return Promise.all([
      postService.trash({ signal }).catch<ShPostUnified[]>(rethrowCancel),
      folderService.trash({ signal }).catch<ShFolder[]>(rethrowCancel),
      siteService.trash({ signal }).catch<ShSite[]>(rethrowCancel),
    ])
      .then(([posts, folders, sites]) =>
        setItems([
          // Sites first, and deliberately: it is the widest thing in here, it takes folders and
          // posts of its own down with it, and restoring it is what the curator wants before they
          // start restoring rows that came from it.
          ...sites.map<TrashRow>((s) => ({
            kind: "site",
            id: s.id,
            title: s.name || "(untitled)",
            label: t("content.siteLabel"),
            deleted: s.deletedDate,
          })),
          ...folders.map<TrashRow>((f) => ({
            kind: "folder",
            id: f.id,
            title: f.name || "(untitled)",
            label: t("content.trashSection"),
            deleted: f.deletedDate,
          })),
          ...posts.map<TrashRow>((p) => ({
            kind: "post",
            id: p.id,
            title: p.title || "(untitled)",
            label: p.shPostType?.title || p.shPostType?.name,
            type: p.shPostType?.name,
            deleted: p.deletedDate,
          })),
        ]),
      )
      // SH1047 — a cancellation is not one of the three reads failing, so it must not reach the
      // per-read fallback above and land as an empty trash. It rejects out of the whole set, and
      // the only handling it needs is none.
      .catch((reason: unknown) => { if (!isCancelled(reason)) setItems([]) })
      .finally(() => setLoading(false))
  }, [t])

  // No `load` wrapper here: this page has no refresh control, so the mount read is the only one
  // and a `setLoading(true)` nobody triggers would be the cascading render itself (SH701).
  useEffect(() => {
    // SH1047 — nothing here changes key while mounted, so this is the unmount case: a curator who
    // leaves before the three reads land must not have `setItems` run against a gone component,
    // and the server must not finish three queries for a page nobody is on.
    const abort = new AbortController()
    void fetchTrash(abort.signal)
    return () => abort.abort()
  }, [t, fetchTrash])

  const drop = (row: TrashRow) =>
    setItems((prev) => prev.filter((r) => !(r.kind === row.kind && r.id === row.id)))

  /** The service each row's verbs belong to — one lookup, so restore and purge cannot disagree. */
  const serviceFor = (kind: TrashRow["kind"]) =>
    ({ site: siteService, folder: folderService, post: postService })[kind]

  const restore = async (row: TrashRow) => {
    await serviceFor(row.kind)
      .untrash(row.id)
      .then(() => toast.success(t("content.trashRestored")))
      // The message matters here in a way it did not before SH648: a site restore can be REFUSED
      // (a live site took its friendly URL), and the refusal names the occupant and both ways out.
      // Swallowing it into "an error occurred" would leave the curator with a button that does
      // nothing and no way to find out why.
      .catch((error) => toast.error(problemOf(error, t("common.error"))))
    drop(row)
  }

  /** @param asked whether the curator already confirmed, as a selection's one question does */
  const purge = async (row: TrashRow, asked = false) => {
    const message =
      row.kind === "post"
        ? t("content.trashPurgeConfirm", { name: row.title })
        : t("content.trashPurgeSectionConfirm", { name: row.title })
    if (!asked && !confirm(message)) return
    await serviceFor(row.kind)
      .purge(row.id)
      .then(() => toast.success(t("content.trashPurged")))
      .catch((error) => toast.error(problemOf(error, t("common.error"))))
    drop(row)
  }

  /*
   * SH1227 — the trash on the design system's table. Restoring and purging many items wants the
   * package's selection and its range keys, which the hand-drawn table had none of; the selection
   * holds within one filter (`selectionScope`), so a verb never lands on rows a filter has hidden.
   * Purge stays out of sight as the menu's destructive item, which is SH1195's rule: on the page
   * whose point is getting things back, the loudest thing must not be the way to lose them.
   */
  const columns = useMemo<BentoDataTableColumn<TrashRow>[]>(() => [
    {
      id: "title", header: t("common.title"), width: "minmax(16rem, 3fr)", hideable: false,
      sortValue: (row) => row.title.toLowerCase(),
      cell: (row) => (
        <span className="flex min-w-0 items-center gap-2 font-medium">
          <RowIcon kind={row.kind} />
          <span className="truncate">{row.title}</span>
        </span>
      ),
    },
    {
      id: "kind", header: t("content.postType"), width: "minmax(9rem, 1fr)",
      sortValue: (row) => (row.label ?? "").toLowerCase(),
      cell: (row) => <Badge variant="outline">{row.label}</Badge>,
    },
    {
      id: "deleted", header: t("content.trashDeleted"), width: "minmax(9rem, 1fr)",
      sortValue: (row) => (row.deleted ? new Date(row.deleted) : null),
      cell: (row) => (row.deleted ? <BentoChangeCell at={row.deleted} /> : null),
    },
  ], [t])

  const restoreAction: BentoDataTableAction<TrashRow> = {
    id: "restore", label: t("content.trashRestore"), icon: IconRestore,
    onSelect: (rows) => { for (const row of rows) void restore(row) },
  }
  const purgeAction: BentoDataTableAction<TrashRow> = {
    id: "purge", label: t("content.trashPurge"), icon: IconTrashX, tone: "destructive",
    onSelect: (rows) => {
      if (rows.length === 1) { void purge(rows[0]); return }
      if (!confirm(t("content.trashPurgeManyConfirm", { count: rows.length }))) return
      for (const row of rows) void purge(row, true)
    },
  }

  return (
    <>
      <PageHero
        title={t("content.trash")}
        subtitle={t("content.trashDescription")}
        urlBase={ROUTES.HOME}
        backLabel={t("home.title")}
      />
      <div className="space-y-3">
        <BentoFilterBar
          value={barValue}
          onChange={onBarChange}
          facets={facets}
          views={<SavedViews list="trash" />}
          queryLabel={t("content.filterItems")}
        />

        {/* An empty trash and a filter that matched nothing are two facts, and say two things. */}
        {!loading && items.length === 0 ? (
          <EmptyState
            icon={IconTrash}
            title={t("content.trashEmpty")}
            description={t("content.trashEmptyHint")}
          />
        ) : !loading && shown.length === 0 ? (
          <EmptyState
            icon={IconTrash}
            title={t("common.noResults")}
            description={t("content.trashNoMatchHint")}
          />
        ) : (
          <BentoDataTable
            rows={shown}
            getRowId={rowKey}
            getRowLabel={(row) => row.title}
            columns={columns}
            label={t("content.trash")}
            rowActions={[restoreAction, purgeAction]}
            selectionActions={[restoreAction, purgeAction]}
            selectionScope={`${q}|${type}`}
          />
        )}
      </div>
    </>
  )
}

/** What kind of thing the row is, at a glance — the column's label says it in words. */
function RowIcon({ kind }: { kind: TrashRow["kind"] }) {
  if (kind === "site") return <Globe className="h-4 w-4 text-emerald-600" />
  if (kind === "folder") return <Folder className="h-4 w-4 text-orange-500" />
  return <FileText className="h-4 w-4 text-blue-500" />
}

/**
 * The server's teaching error, or a fallback.
 *
 * Every 4xx here carries `fix` (P6), and since SH648 the trash has refusals worth reading: a site
 * restore is refused when a live site took its friendly URL, and the message names the occupant and
 * both ways out. A bare "an error occurred" would leave the curator with a button that does nothing.
 */
function problemOf(error: unknown, fallback: string): string {
  const problem = (error as { response?: { data?: { title?: string; fix?: string } } })?.response?.data
  if (!problem) return fallback
  return [problem.title, problem.fix].filter(Boolean).join(" — ") || fallback
}
