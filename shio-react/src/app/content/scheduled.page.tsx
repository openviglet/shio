import { IconCalendarTime } from "@tabler/icons-react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useState } from "react"
import { addressKey } from "@/lib/query-keys"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import {
  BentoCalendar, BentoDataTable, BentoIdentityCell, type BentoCalendarEntry,
} from "@viglet/viglet-design-system/bento"
import { toast } from "@viglet/viglet-design-system"
import { EmptyState } from "@/components/empty-state"
import { PageHero } from "@/components/page-hero"
import { Panel } from "@/components/panel"
import { Badge } from "@/components/ui/badge"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { ROUTES } from "@/app/routes.const"
import { entryKey, movedSchedule } from "@/lib/schedule-move"
import { reportProblem } from "@/lib/problem"
import { ShPostUnifiedService } from "@/services/post-unified/post-unified.service"
import { ShScheduleService, type ShScheduleEntry } from "@/services/schedule/schedule.service"

const service = new ShScheduleService()
const unifiedService = new ShPostUnifiedService()

type Shown = "list" | "calendar"

/**
 * The reader's own zone, named. A schedule is stored as an instant and shown in local time, so the
 * page says which local time it means — the thing the one-post dialog never said (SH991).
 */
const ZONE = Intl.DateTimeFormat().resolvedOptions().timeZone

const ACTION_BADGE: Record<ShScheduleEntry["action"], string> = {
  publish: "border-green-300 text-green-600 dark:border-green-700 dark:text-green-400",
  unpublish: "border-amber-300 text-amber-600 dark:border-amber-700 dark:text-amber-400",
}

/**
 * SH991 — what goes live or comes down, in the order it will happen.
 *
 * A read-mostly table under a hero, the Activity page's shape: an agenda is read down a column.
 *
 * SH1082 — or a month or week of it, from the design system's `BentoCalendar` (VDS172). The zone
 * line stays above both views and the grid is drawn in that zone. Dropping an entry on another day,
 * by pointer or from the keyboard, keeps its time of day and goes through the same schedule write
 * the post's dialog uses, which records the curator as the requester (SH991).
 */
export default function ScheduledPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [shown, setShown] = useState<Shown>("list")

  /** The read. It raises no state before the request, which is what lets the mount effect call it. */
  /*
   * SH1155 — the agenda, cancelled when the page unmounts. It walks up to fifty pages of
   * `/agent/find`, so a curator who opens it and leaves immediately used to leave that walk running
   * to its own bound; the signal stops it at the page it is on.
   */
  const read = useQuery<ShScheduleEntry[]>({
    queryKey: addressKey("summary:scheduled"),
    queryFn: ({ signal }) => service.upcoming({ signal }),
  })
  const entries = read.data ?? []
  const loading = read.isPending

  const when = (at: string) => new Date(at).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" })
  const actionLabel = (entry: ShScheduleEntry) =>
    entry.action === "publish" ? t("content.scheduledPublish") : t("content.scheduledUnpublish")

  const byKey = new Map(entries.map((entry) => [entryKey(entry), entry]))
  const calendarEntries: BentoCalendarEntry[] = entries.map((entry) => ({
    id: entryKey(entry),
    start: entry.at,
    label: `${actionLabel(entry)}: ${entry.title || entry.address}`,
    tone: entry.action === "publish" ? "on" : "warn",
  }))

  const move = async (id: string, start: Date) => {
    const entry = byKey.get(id)
    if (!entry?.postId) return
    try {
      await unifiedService.schedule(entry.postId, movedSchedule(entry, start))
      toast.success(t("content.scheduledMoved", { page: entry.title || entry.address, when: when(start.toISOString()) }))
    } catch (error) {
      reportProblem(error, t("common.error"))
    } finally {
      await queryClient.invalidateQueries({ queryKey: addressKey("summary:scheduled") })
    }
  }

  const open = (id: string) => {
    const postId = byKey.get(id)?.postId
    if (postId) navigate(`${ROUTES.CONSOLE}/content/post/${postId}`)
  }

  return (
    <>
      <PageHero grammar="work"
        title={t("content.scheduledTitle")}
        subtitle={t("content.scheduledDescription")}
        urlBase={ROUTES.HOME}
        backLabel={t("home.title")}
      />
      <div className="space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p className="text-sm text-muted-foreground">{t("content.scheduledZone", { zone: ZONE })}</p>
          <Tabs value={shown} onValueChange={(value) => setShown(value as Shown)}>
            <TabsList aria-label={t("content.scheduledView")}>
              <TabsTrigger value="list">{t("content.scheduledViewList")}</TabsTrigger>
              <TabsTrigger value="calendar">{t("content.scheduledViewCalendar")}</TabsTrigger>
            </TabsList>
          </Tabs>
        </div>
        {shown === "calendar" ? (
          <Panel>
            <BentoCalendar
              entries={calendarEntries}
              timeZone={ZONE}
              label={t("content.scheduledTitle")}
              onEntryMove={(id, start) => { void move(id, start) }}
              onEntrySelect={open}
            />
          </Panel>
        ) : !loading && entries.length === 0 ? (
          <EmptyState
            icon={IconCalendarTime}
            title={t("content.scheduledEmptyTitle")}
            description={t("content.scheduledEmptyDescription")}
          />
        ) : (
          /*
           * SH1229 — the list view on the design system's table, beside the package's calendar. The
           * rows arrive in the order they will happen and the table keeps that order until a header
           * is chosen, because time order is the point of the page; Enter opens the post.
           */
          <BentoDataTable
            rows={entries}
            getRowId={entryKey}
            getRowLabel={(entry) => `${actionLabel(entry)}: ${entry.title || entry.address}`}
            columns={[
              {
                id: "when", header: t("content.scheduledWhen"), width: "minmax(12rem, 1fr)", hideable: false,
                sortValue: (entry) => new Date(entry.at),
                cell: (entry) => <time dateTime={entry.at}>{when(entry.at)}</time>,
              },
              {
                id: "action", header: t("content.scheduledAction"), width: "minmax(9rem, 0.8fr)",
                sortValue: (entry) => entry.action,
                cell: (entry) => (
                  <Badge variant="outline" className={ACTION_BADGE[entry.action]}>{actionLabel(entry)}</Badge>
                ),
              },
              {
                id: "page", header: t("content.scheduledPage"), width: "minmax(16rem, 3fr)", lines: 2,
                sortValue: (entry) => (entry.title || entry.address).toLowerCase(),
                cell: (entry) => <BentoIdentityCell name={entry.title || entry.address} detail={entry.address} />,
              },
            ]}
            label={t("content.scheduledTitle")}
            onRowOpen={(entry) => open(entryKey(entry))}
          />
        )}
      </div>
    </>
  )
}
