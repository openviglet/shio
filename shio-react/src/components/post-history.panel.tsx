import { useCallback, useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { Bot, ChevronDown, GitCompare } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Link } from "react-router-dom"
import { ROUTES } from "@/app/routes.const"
import { ShHistoryService } from "@/services/history/history.service"
import type { ShHistory } from "@/models/history/history.model"

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
  const [loading, setLoading] = useState(true)

  const load = useCallback((before?: string) => {
    setLoading(true)
    return service.byObject(objectId, { limit: PAGE_SIZE, before })
      .then((page) => {
        setRows((current) => (before ? [...current, ...page.rows] : page.rows))
        setCursor(page.cursor)
        setMore(page.more)
      })
      .catch(() => setMore(false))
      .finally(() => setLoading(false))
  }, [objectId])

  useEffect(() => {
    void load()
  }, [load])

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
          onClick={() => void load(cursor ?? undefined)}>
          <ChevronDown className="mr-1 h-4 w-4" />
          {t("activity.more")}
        </Button>
      )}
    </div>
  )
}
