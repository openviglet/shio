import { IconHistory } from "@tabler/icons-react"
import { AdaptiveEmptyState } from "@/components/adaptive-empty-state"
import { AdaptivePageHeader } from "@/components/adaptive-page-header"
import { AdaptivePanel } from "@/components/adaptive-panel"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { History, RefreshCw } from "lucide-react"
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
  const { resetBreadcrumb } = useBreadcrumb()
  const [items, setItems] = useState<ShHistory[]>([])
  const [loading, setLoading] = useState(true)

  /** The read. It raises no state before the request, which is what lets the mount effect call it. */
  const fetchRecent = useCallback(
    () => service.recent(200).then(setItems).catch(() => setItems([]))
      .finally(() => setLoading(false)),
    [],
  )

  /** The curator asked, so the spinner is a response to them (SH701). */
  const load = useCallback(() => {
    setLoading(true)
    void fetchRecent()
  }, [fetchRecent])

  useEffect(() => {
    resetBreadcrumb([
      { label: t("admin.title"), href: ROUTES.ADMIN_ROOT },
      { label: t("activity.title") },
    ])
    void fetchRecent()
  }, [resetBreadcrumb, t, fetchRecent])

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
      <AdaptivePageHeader
        icon={History}
        title={t("activity.title")}
        eyebrow={t("admin.title")}
        subtitle={t("activity.description")}
        urlBase={ROUTES.ADMIN_ROOT}
        actions={
          <Button onClick={load} size="sm" variant="outline" disabled={loading}>
            <RefreshCw className={`mr-2 h-4 w-4 ${loading ? "animate-spin" : ""}`} />
            {t("activity.refresh")}
          </Button>
        }
      />
      <div className="p-6">
        {items.length === 0 ? (
          <AdaptiveEmptyState
            icon={IconHistory}
            tone="slate"
            title={t("activity.blankTitle")}
            description={t("activity.blankDescription")}
          />
        ) : (
          <AdaptivePanel contentClassName="p-0">
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
                      <TableCell className="font-medium">{item.description}</TableCell>
                      <TableCell className="text-muted-foreground">{item.owner}</TableCell>
                      <TableCell className="text-muted-foreground">
                        {item.date ? new Date(item.date).toLocaleString() : ""}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
          </AdaptivePanel>
        )}
      </div>
    </>
  )
}
