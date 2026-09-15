import { IconHistory } from "@tabler/icons-react"
import { EmptyState } from "@/components/empty-state"
import { PageHero } from "@/components/page-hero"
import { Panel } from "@/components/panel"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { ROUTES } from "@/app/routes.const"
import { Bot, History, RefreshCw } from "lucide-react"
import { Link } from "react-router-dom"
import { useCallback, useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { ShHistoryService } from "@/services/history/history.service"
import type { ShHistory } from "@/models/history/history.model"

const service = new ShHistoryService()

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
  const [items, setItems] = useState<ShHistory[]>([])
  const [loading, setLoading] = useState(true)
  /** SH999 — everyone, the machines, or the people. The server decides which rows those are. */
  const [who, setWho] = useState<"" | "agent" | "curator">("")

  /** The read. It raises no state before the request, which is what lets the mount effect call it. */
  const fetchRecent = useCallback(
    () => service.recent(200, who || undefined).then(setItems).catch(() => setItems([]))
      .finally(() => setLoading(false)),
    [who],
  )

  /** The curator asked, so the spinner is a response to them (SH701). */
  const load = useCallback(() => {
    setLoading(true)
    void fetchRecent()
  }, [fetchRecent])

  useEffect(() => {
    void fetchRecent()
  }, [t, fetchRecent])

  const actionLabel = (action: string) => {
    const key = `activity.action${action.charAt(0)}${action.slice(1).toLowerCase()}`
    const label = t(key)
    return label === key ? action : label
  }

  return (
    <>
      {/*
        SH906 — a read-mostly table under a hero, the second of the two shapes §LV.9 said the
        package covers least well. It stays a table: an audit log is read down a column, and a
        mosaic of tiles is the wrong instrument for "what changed, by whom, when".
      */}
      <PageHero
        icon={History}
        title={t("activity.title")}
        eyebrow={t("admin.title")}
        subtitle={t("activity.description")}
        urlBase={ROUTES.ADMIN_ROOT}
        actions={
          <div className="flex items-center gap-2">
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
                onClick={() => setWho(value)}
              >
                {t(key)}
              </Button>
            ))}
            <Button onClick={load} size="sm" variant="outline" disabled={loading}>
              <RefreshCw className={`mr-2 h-4 w-4 ${loading ? "animate-spin" : ""}`} />
              {t("activity.refresh")}
            </Button>
          </div>
        }
      />
      <div className="p-6">
        {items.length === 0 ? (
          <EmptyState
            icon={IconHistory}
            tone="slate"
            title={t("activity.blankTitle")}
            description={t("activity.blankDescription")}
          />
        ) : (
          <Panel contentClassName="p-0">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="w-32">{t("activity.action")}</TableHead>
                    <TableHead>{t("activity.what")}</TableHead>
                    <TableHead className="w-40">{t("activity.who")}</TableHead>
                    <TableHead className="w-52">{t("activity.when")}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {items.map((item) => (
                    <TableRow key={item.id}>
                      <TableCell>
                        <Badge variant="outline" className={ACTION_BADGE[item.action] ?? ""}>
                          {actionLabel(item.action)}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <div className="font-medium">{item.description}</div>
                        {/*
                          SH989 — what the write changed, by name. After an approve the draft and
                          the live row are one document, so this line is what still describes it.
                        */}
                        {item.changedFields && (
                          <div className="text-xs text-muted-foreground">
                            {item.changedFields.length === 0
                              ? t("activity.noFieldsChanged")
                              : t("activity.fields", { fields: item.changedFields.join(", ") })}
                          </div>
                        )}
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {item.owner}
                        {/*
                          SH999 — the run beside the person, because both are true: an agent acts as
                          somebody's credential, so a column showing only the name said the same
                          thing about a machine's write and a person's. It links to the queue where
                          that run can be read and undone, which is the next thing a curator who
                          noticed this wants.
                        */}
                        {item.agent && (
                          <Link
                            to={`${ROUTES.ADMIN_REVIEW}?session=${encodeURIComponent(item.agent)}`}
                            title={t("activity.byAgent")}
                            className="ml-2 inline-flex items-center gap-1 align-middle"
                          >
                            <Badge variant="outline" className="vg-accent-text gap-1">
                              <Bot className="h-3 w-3" />
                              {item.agentName ?? item.agent.slice(0, 8)}
                            </Badge>
                          </Link>
                        )}
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {item.date ? new Date(item.date).toLocaleString() : ""}
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
