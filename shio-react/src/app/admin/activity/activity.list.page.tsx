import { activitySentence } from "@/lib/activity-text"
import { IconHistory, IconUserSearch } from "@tabler/icons-react"
import { BentoChangeCell, BentoDataTable } from "@viglet/viglet-design-system/bento"
import { EmptyState } from "@/components/empty-state"
import { PageHero } from "@/components/page-hero"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { ROUTES } from "@/app/routes.const"
import { Bot, ChevronLeft, ChevronRight, RefreshCw, X } from "lucide-react"
import { Link, useSearchParams } from "react-router-dom"
import { useCallback, useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useChangeStream } from "@/hooks/use-change-stream"
import { ShHistoryService } from "@/services/history/history.service"
import type { ShHistory } from "@/models/history/history.model"
import { isCancelled } from "@/lib/read-options"

const service = new ShHistoryService()

/** One page. Smaller than the old 200 because there is a page after this one now (SH1005). */
const PAGE_SIZE = 50

/** The whole transition vocabulary (SH51), which is short enough to offer rather than describe. */
const ACTIONS = ["CREATE", "UPDATE", "PUBLISH", "UNPUBLISH", "DELETE", "RESTORE"] as const

/** Maps an audit action to its badge styling. Unknown actions fall back to a neutral badge. */
const ACTION_BADGE: Record<string, string> = {
  CREATE: "border-blue-300 text-blue-600 dark:border-blue-700 dark:text-blue-400",
  UPDATE: "border-indigo-300 text-indigo-600 dark:border-indigo-700 dark:text-indigo-400",
  PUBLISH: "border-green-300 text-green-600 dark:border-green-700 dark:text-green-400",
  UNPUBLISH: "border-amber-300 text-amber-600 dark:border-amber-700 dark:text-amber-400",
  DELETE: "border-red-300 text-red-600 dark:border-red-700 dark:text-red-400",
  RESTORE: "border-teal-300 text-teal-600 dark:border-teal-700 dark:text-teal-400",
}

export default function ActivityListPage() {
  const { t } = useTranslation()
  /**
   * SH1005 — the filters live in the URL, not in component state.
   *
   * A narrowed trail is a thing a curator sends to somebody ("look at what this run did on
   * Tuesday"), and state that only exists in a component cannot be sent, bookmarked, or survive
   * the browser's own back button. The reader underneath takes them as query predicates, so what
   * the URL says and what the server was asked are the same list.
   */
  const [search, setSearch] = useSearchParams()
  const who = search.get("who") ?? ""
  const action = search.get("action") ?? ""
  const actor = search.get("actor") ?? ""
  const before = search.get("before") ?? ""

  const [items, setItems] = useState<ShHistory[]>([])
  const [cursor, setCursor] = useState<string | null>(null)
  const [more, setMore] = useState(false)
  const [loading, setLoading] = useState(true)
  /** SH1005 — the stream said the trail moved; the curator decides when their page moves. */
  const [arrived, setArrived] = useState(false)
  /**
   * The positions walked past, so "newer" goes back exactly the way "older" came. State rather than a
   * ref since SH1136: whether there is anywhere to go back to decides whether "Newer" is enabled, and
   * a ref read during render is a value React never re-renders for.
   */
  const [back, setBack] = useState<string[]>([])

  /** One narrowing changed: page one again, and the walk starts over from the newest row. */
  const narrow = useCallback((key: string, value: string) => {
    setBack([])
    const next = new URLSearchParams(search)
    if (value) next.set(key, value)
    else next.delete(key)
    next.delete("before")
    setSearch(next, { replace: false })
  }, [search, setSearch])

  /** The read. It raises no state before the request, which is what lets the mount effect call it. */
  const fetchPage = useCallback(
    (signal?: AbortSignal) => service.recent({
      limit: PAGE_SIZE,
      before: before || undefined,
      agent: who || undefined,
      action: action || undefined,
      actor: actor || undefined,
    }, { signal })
      .then((page) => {
        setItems(page.rows)
        setCursor(page.cursor)
        setMore(page.more)
        setArrived(false)
        setLoading(false)
      })
      .catch((reason: unknown) => {
        // SH1047 — every filter on this page rebuilds the callback, so a narrowing that overtakes
        // its own request must not land as an empty trail with the old cursor cleared.
        if (isCancelled(reason)) return
        setItems([])
        setCursor(null)
        setMore(false)
        setLoading(false)
      }),
    [before, who, action, actor],
  )

  /** The curator asked, so the spinner is a response to them (SH701). */
  const load = useCallback(() => {
    setLoading(true)
    void fetchPage()
  }, [fetchPage])

  useEffect(() => {
    const abort = new AbortController()
    void fetchPage(abort.signal)
    return () => abort.abort()
  }, [t, fetchPage])

  useChangeStream(useCallback(() => setArrived(true), []))

  const pageAt = (position: string) => {
    const next = new URLSearchParams(search)
    if (position) next.set("before", position)
    else next.delete("before")
    setSearch(next, { replace: false })
  }

  const older = () => {
    if (!cursor) return
    setBack((walked) => [...walked, before])
    pageAt(cursor)
  }

  const newer = () => {
    const previous = back.at(-1)
    setBack(back.slice(0, -1))
    pageAt(previous ?? "")
  }

  /** SH989 — the fields a write changed, by name; nothing when the row recorded none. */
  const fieldsLine = (changed: string[] | null | undefined) => {
    if (!changed) return ""
    return changed.length === 0 ? t("activity.noFieldsChanged") : t("activity.fields", { fields: changed.join(", ") })
  }

  const actionLabel = (value: string) => {
    const key = `activity.action${value.charAt(0)}${value.slice(1).toLowerCase()}`
    const label = t(key)
    return label === key ? value : label
  }

  return (
    <>
      {/*
        SH906 — a read-mostly table under a hero, the second of the two shapes §LV.9 said the
        package covers least well. It stays a table: an audit log is read down a column, and a
        mosaic of tiles is the wrong instrument for "what changed, by whom, when".
      */}
      <PageHero
        title={t("activity.title")}
        eyebrow={t("admin.title")}
        subtitle={t("activity.description")}
        urlBase={ROUTES.HOME}
        backLabel={t("home.title")}
        actions={
          <div className="flex flex-wrap items-center gap-2">
            {/*
              SH999 — three buttons rather than a select: the whole vocabulary is three words, and a
              curator asking "was that us or the agent" should not have to open a menu to ask it.
            */}
            {([["", "activity.filterAll"], ["agent", "activity.filterAgent"],
              ["curator", "activity.filterCurator"]] as const).map(([value, key]) => (
              <Button
                key={key}
                size="sm"
                variant={who === value ? "default" : "outline"}
                disabled={loading}
                onClick={() => narrow("who", value)}
              >
                {t(key)}
              </Button>
            ))}
            {/*
              SH1005 — six kinds is a menu rather than six more buttons, and it is the narrowing a
              curator reaches for first: "what went live today" is a question this page was asked
              and could only answer by scrolling.
            */}
            <Select value={action || "all"}
              onValueChange={(value) => narrow("action", value === "all" ? "" : value)}>
              <SelectTrigger className="w-40" aria-label={t("activity.action")}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">{t("activity.actionAny")}</SelectItem>
                {ACTIONS.map((value) => (
                  <SelectItem key={value} value={value}>{actionLabel(value)}</SelectItem>
                ))}
              </SelectContent>
            </Select>
            {actor && (
              <Button size="sm" variant="secondary" onClick={() => narrow("actor", "")}>
                {actor}
                <X className="ml-2 h-3 w-3" />
              </Button>
            )}
            <Button onClick={load} size="sm" variant="outline" disabled={loading}>
              <RefreshCw className={`mr-2 h-4 w-4 ${loading ? "animate-spin" : ""}`} />
              {t("activity.refresh")}
            </Button>
          </div>
        }
      />
      <div className="space-y-4">
        {/*
          SH1005 — SH967's stream, as a line the curator acts on rather than a table that moves
          under them. Redrawing on every event would take the row they were reading off the screen,
          and on this page the row they are reading is the reason they came (P7).
        */}
        {arrived && (
          <button
            type="button"
            onClick={load}
            className="w-full rounded-md border border-dashed px-3 py-2 text-sm text-muted-foreground hover:bg-muted"
          >
            {t("activity.arrived")}
          </button>
        )}
        {items.length === 0 ? (
          <EmptyState
            icon={IconHistory}
            title={t("activity.blankTitle")}
            description={t("activity.blankDescription")}
          />
        ) : (
          /*
           * SH1233 — the audit rows on the design system's table: the trail is long and filtered,
           * which is the case its virtualized body exists for. The keyset paging below is untouched;
           * the table draws one page of it. When and by whom is the change cell, marked when an
           * agent did it, with the run beside it linked to the queue (SH999); the name-as-filter
           * moved to the row's menu, since a row is one fixed height and holds one control per idea.
           */
          <BentoDataTable
            rows={items}
            getRowId={(item) => item.id}
            getRowLabel={(item) => activitySentence(item.description, item.action, t)}
            columns={[
              {
                id: "action", header: t("activity.action"), width: "minmax(9rem, 0.8fr)",
                sortValue: (item) => item.action,
                cell: (item) => (
                  <span className="flex items-center gap-1">
                    <Badge variant="outline" className={ACTION_BADGE[item.action] ?? ""}>
                      {actionLabel(item.action)}
                    </Badge>
                    {/*
                      SH196 recorded the subject on every row and no reader carried it, so a folder
                      rename read as a post's. Said only when it is not a post, because a badge on
                      every row is a column that says nothing.
                    */}
                    {item.subjectKind && item.subjectKind !== "POST" && (
                      <Badge variant="outline" className="text-[10px]">
                        {t(`activity.subject.${item.subjectKind}`, { defaultValue: item.subjectKind.toLowerCase() })}
                      </Badge>
                    )}
                  </span>
                ),
              },
              {
                id: "what", header: t("activity.what"), width: "minmax(18rem, 3fr)", hideable: false,
                cell: (item) => {
                  const sentence = activitySentence(item.description, item.action, t)
                  // SH989 — what the write changed, by name. After an approve the draft and the
                  // live row are one document, so this is what still describes it.
                  const fields = fieldsLine(item.changedFields)
                  return (
                    <span className="flex min-w-0 items-baseline gap-2" title={[sentence, fields].filter(Boolean).join("\n")}>
                      <span className="truncate font-medium">{sentence}</span>
                      {fields && <span className="text-muted-foreground truncate text-xs">{fields}</span>}
                    </span>
                  )
                },
              },
              {
                id: "site", header: t("activity.site"), width: "minmax(8rem, 0.8fr)",
                sortValue: (item) => (item.siteName ?? "").toLowerCase(),
                cell: (item) => <span className="text-muted-foreground text-sm">{item.siteName ?? ""}</span>,
              },
              {
                id: "when", header: t("activity.when"), width: "minmax(12rem, 1.2fr)",
                sortValue: (item) => (item.date ? new Date(item.date) : null),
                cell: (item) => (
                  <span className="flex min-w-0 items-center gap-2">
                    {item.date
                      ? <BentoChangeCell at={item.date} actor={item.owner} agent={Boolean(item.agent)} inline />
                      : <span className="text-muted-foreground">{item.owner}</span>}
                    {/*
                      SH999 — the run beside the person, because both are true: an agent acts as
                      somebody's credential. It links to the queue where that run can be read and
                      undone, which is the next thing a curator who noticed this wants.
                    */}
                    {item.agent && (
                      <Link
                        to={`${ROUTES.ADMIN_REVIEW}?session=${encodeURIComponent(item.agent)}`}
                        title={t("activity.byAgent")}
                        className="inline-flex shrink-0 items-center gap-1"
                      >
                        <Badge variant="outline" className="vg-accent-text gap-1">
                          <Bot className="h-3 w-3" />
                          {item.agentName ?? item.agent.slice(0, 8)}
                        </Badge>
                      </Link>
                    )}
                  </span>
                ),
              },
            ]}
            label={t("activity.title")}
            // SH1005 — the name is the filter: "everything this person did" was a question the server
            // could answer and the page had no way to ask.
            rowActions={[{
              id: "byActor", label: t("activity.byActor"), icon: IconUserSearch,
              onSelect: ([item]) => narrow("actor", item.owner),
            }]}
          />
        )}
        {/*
          SH1005 — the trail did not end at row five hundred; that was where the reader stopped.
        */}
        <div className="flex items-center justify-end gap-2">
          <Button size="sm" variant="outline" disabled={loading || back.length === 0}
            onClick={newer}>
            <ChevronLeft className="mr-1 h-4 w-4" />
            {t("activity.newer")}
          </Button>
          <Button size="sm" variant="outline" disabled={loading || !more} onClick={older}>
            {t("activity.older")}
            <ChevronRight className="ml-1 h-4 w-4" />
          </Button>
        </div>
      </div>
    </>
  )
}
