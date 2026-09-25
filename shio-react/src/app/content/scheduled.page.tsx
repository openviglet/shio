import { IconCalendarTime } from "@tabler/icons-react"
import { CalendarClock } from "lucide-react"
import { useCallback, useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { Link } from "react-router-dom"
import { EmptyState } from "@/components/empty-state"
import { PageHero } from "@/components/page-hero"
import { Panel } from "@/components/panel"
import { Badge } from "@/components/ui/badge"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { ROUTES } from "@/app/routes.const"
import { ShScheduleService, type ShScheduleEntry } from "@/services/schedule/schedule.service"

const service = new ShScheduleService()

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
 */
export default function ScheduledPage() {
  const { t } = useTranslation()
  const [entries, setEntries] = useState<ShScheduleEntry[]>([])
  const [loading, setLoading] = useState(true)

  /** The read. It raises no state before the request, which is what lets the mount effect call it. */
  const fetchAgenda = useCallback(
    () => service.upcoming().then(setEntries).catch(() => setEntries([]))
      .finally(() => setLoading(false)),
    [],
  )

  useEffect(() => {
    void fetchAgenda()
  }, [fetchAgenda])

  const when = (at: string) => new Date(at).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" })

  return (
    <>
      <PageHero
        icon={CalendarClock}
        title={t("content.scheduledTitle")}
        subtitle={t("content.scheduledDescription")}
        urlBase={`${ROUTES.CONSOLE}/content`}
      />
      <div className="space-y-3">
        <p className="text-sm text-muted-foreground">{t("content.scheduledZone", { zone: ZONE })}</p>
        {!loading && entries.length === 0 ? (
          <EmptyState
            icon={IconCalendarTime}
            tone="blue"
            title={t("content.scheduledEmptyTitle")}
            description={t("content.scheduledEmptyDescription")}
          />
        ) : (
          <Panel contentClassName="p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-56">{t("content.scheduledWhen")}</TableHead>
                  <TableHead className="w-36">{t("content.scheduledAction")}</TableHead>
                  <TableHead>{t("content.scheduledPage")}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {entries.map((entry) => (
                  <TableRow key={`${entry.at}|${entry.action}|${entry.address}`}>
                    <TableCell>
                      <time dateTime={entry.at}>{when(entry.at)}</time>
                    </TableCell>
                    <TableCell>
                      <Badge variant="outline" className={ACTION_BADGE[entry.action]}>
                        {entry.action === "publish" ? t("content.scheduledPublish") : t("content.scheduledUnpublish")}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      {entry.postId ? (
                        <Link className="font-medium hover:underline" to={`${ROUTES.CONSOLE}/content/post/${entry.postId}`}>
                          {entry.title || entry.address}
                        </Link>
                      ) : (
                        <span className="font-medium">{entry.title || entry.address}</span>
                      )}
                      <div className="text-xs text-muted-foreground">{entry.address}</div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Panel>
        )}
      </div>
    </>
  )
}
