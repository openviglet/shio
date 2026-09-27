import { useCallback, useEffect, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import { Bot, ChevronDown, GitCompare } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Link } from "react-router-dom"
import { ROUTES } from "@/app/routes.const"
import { ShHistoryService } from "@/services/history/history.service"
import type { ShHistory } from "@/models/history/history.model"
import { isCancelled } from "@/lib/read-options"

const service = new ShHistoryService()

/** Short enough to read at a glance under the fields, long enough to cover a day's work. */
const PAGE_SIZE = 10

interface Props {
  objectId: string
  /** Opens the versions dialog — where the console compares one state with another. */
  onCompare: () => void
}

/**
 * SH1005 — what happened to *this* page, on the page it happened to.
 *
 * <p>"Who changed this, and when" is the question every CMS is asked, and this console could not
 * answer it on screen: the trail existed, the per-object read existed and had a typed client method
 * with no callers, and a curator looking at a page they did not recognise had to leave it, open the
 * Activity list, and scan an instance-wide log for their own post.</p>
 *
 * <p>It reads through the same reader the Activity page does, narrowed to this object — not a
 * second query and not the whole trail filtered here, which would page wrongly the moment the post
 * has more history than one screen.</p>
 *
 * <p>Each row offers the comparison the console actually has, which is the version dialog's
 * draft-against-published diff. AZ4 will give a row its own revision to open; until it does, a link
 * to a diff that cannot be rendered would be a control that answers nothing.</p>
 */
export function PostHistoryPanel({ objectId, onCompare }: Props) {
  const { t } = useTranslation()
  const [rows, setRows] = useState<ShHistory[]>([])
  const [cursor, setCursor] = useState<string | null>(null)
  const [more, setMore] = useState(false)
  const [failed, setFailed] = useState(false)
  // SH1136 — derived, not raised by the read: the panel is loading while the rows on screen belong to
  // another object than the one asked for, or while an older page is in flight. Raising a flag at the
  // top of the read is what put a synchronous setState in the mount effect.
  const [settledFor, setSettledFor] = useState<string | null>(null)
  const [olderLoading, setOlderLoading] = useState(false)
  const loading = settledFor !== objectId || olderLoading
  /*
   * SH1116 — one controller for whichever read is in flight, the first page or an older one. Rows
   * accumulate, so an older page of the post the curator just left landing after the switch would be
   * appended to the new post's trail: the object changing, a second "show older" and unmounting all
   * abort it, and only a read nobody cancelled may write.
   */
  const inflight = useRef<AbortController | null>(null)

  const load = useCallback((before?: string) => {
    inflight.current?.abort()
    const abort = new AbortController()
    inflight.current = abort
    return service.byObject(objectId, { limit: PAGE_SIZE, before }, { signal: abort.signal })
      .then((page) => {
        setRows((current) => (before ? [...current, ...page.rows] : page.rows))
        setCursor(page.cursor)
        setMore(page.more)
        setFailed(false)
      })
      .catch((reason: unknown) => {
        if (isCancelled(reason)) return
        setMore(false)
        setFailed(true)
      })
      .finally(() => {
        // A superseded read settles nothing: the one that replaced it is what the spinner waits for.
        if (!abort.signal.aborted) {
          setSettledFor(objectId)
          setOlderLoading(false)
        }
      })
  }, [objectId])

  const showOlder = () => {
    setOlderLoading(true)
    void load(cursor ?? undefined)
  }

  const cancel = useCallback(() => inflight.current?.abort(), [])

  useEffect(() => {
    void load()
    return cancel
  }, [load, cancel])

  // "Nothing was recorded" and "the trail could not be read" are opposite facts, and the first is
  // what a curator stops checking for — so a failure says so rather than borrowing the empty one.
  if (!loading && failed && rows.length === 0) {
    return <p className="text-sm text-muted-foreground">{t("activity.readFailed")}</p>
  }
  if (!loading && rows.length === 0) {
    return <p className="text-sm text-muted-foreground">{t("activity.none")}</p>
  }

  return (
    <div className="space-y-2">
      <ol className="space-y-2">
        {rows.map((row) => (
          <li key={row.id} className="flex items-start justify-between gap-3 text-sm">
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <Badge variant="outline" className="text-[10px]">{row.action}</Badge>
                <span className="truncate font-medium">{row.description}</span>
              </div>
              <div className="mt-0.5 text-xs text-muted-foreground">
                {row.owner}
                {row.agent && (
                  <Link
                    to={`${ROUTES.ADMIN_REVIEW}?session=${encodeURIComponent(row.agent)}`}
                    title={t("activity.byAgent")}
                    className="ml-2 inline-flex items-center gap-1 align-middle"
                  >
                    <Bot className="h-3 w-3" />
                    {row.agentName ?? row.agent.slice(0, 8)}
                  </Link>
                )}
                {" · "}
                {row.date ? new Date(row.date).toLocaleString() : ""}
                {/*
                  SH989 — the names of the fields the write changed. On this panel they are the
                  difference between "somebody updated this" and something a curator can act on.
                */}
                {row.changedFields && row.changedFields.length > 0
                  && ` · ${t("activity.fields", { fields: row.changedFields.join(", ") })}`}
              </div>
            </div>
            <Button size="sm" variant="ghost" onClick={onCompare} title={t("activity.compare")}>
              <GitCompare className="h-4 w-4" />
            </Button>
          </li>
        ))}
      </ol>
      {more && (
        <Button size="sm" variant="outline" disabled={loading}
          onClick={showOlder}>
          <ChevronDown className="mr-1 h-4 w-4" />
          {t("activity.more")}
        </Button>
      )}
    </div>
  )
}
