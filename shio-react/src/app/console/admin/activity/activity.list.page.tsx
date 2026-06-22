import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent } from "@/components/ui/card"
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
  const [loading, setLoading] = useState(false)

  const load = useCallback(() => {
    setLoading(true)
    service.recent(200).then(setItems).catch(() => setItems([])).finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    resetBreadcrumb([
      { label: t("admin.title"), href: ROUTES.ADMIN_ROOT },
      { label: t("activity.title") },
    ])
    load()
  }, [resetBreadcrumb, t, load])

  const actionLabel = (action: string) => {
    const key = `activity.action${action.charAt(0)}${action.slice(1).toLowerCase()}`
    const label = t(key)
    return label === key ? action : label
  }

  return (
    <>
      <PageHeader title={t("activity.title")} turIcon={History} urlBase={ROUTES.ADMIN_ROOT} />
      <div className="p-6">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h2 className="text-lg font-semibold">{t("activity.title")}</h2>
            <p className="text-sm text-muted-foreground">{t("activity.description")}</p>
          </div>
          <Button onClick={load} size="sm" variant="outline" disabled={loading}>
            <RefreshCw className={`mr-2 h-4 w-4 ${loading ? "animate-spin" : ""}`} />
            {t("activity.refresh")}
          </Button>
        </div>

        {items.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-center">
            <div className="rounded-full bg-muted p-4 mb-4">
              <History className="h-8 w-8 text-muted-foreground" />
            </div>
            <h3 className="font-semibold text-lg mb-1">{t("activity.blankTitle")}</h3>
            <p className="text-sm text-muted-foreground max-w-sm">{t("activity.blankDescription")}</p>
          </div>
        ) : (
          <Card>
            <CardContent className="p-0">
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
            </CardContent>
          </Card>
        )}
      </div>
    </>
  )
}
