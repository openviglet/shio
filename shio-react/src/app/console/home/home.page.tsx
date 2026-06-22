import { PageHeader } from "@/components/page-header"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { ROUTES } from "@/app/routes.const"
import {
  IconHome, IconFileText, IconForms, IconSearch, IconPlus, IconWebhook, IconHistory,
} from "@tabler/icons-react"
import { useEffect, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { ShSiteService } from "@/services/site/site.service"
import { ShPostTypeService } from "@/services/post-type/post-type.service"
import { ShSystemInfoService } from "@/services/system/system-info.service"
import { ShHistoryService } from "@/services/history/history.service"
import type { ShHistory } from "@/models/history/history.model"

const siteService = new ShSiteService()
const postTypeService = new ShPostTypeService()
const systemInfoService = new ShSystemInfoService()
const historyService = new ShHistoryService()

const ACTION_BADGE: Record<string, string> = {
  CREATE: "border-blue-300 text-blue-600 dark:border-blue-700 dark:text-blue-400",
  UPDATE: "border-indigo-300 text-indigo-600 dark:border-indigo-700 dark:text-indigo-400",
  PUBLISH: "border-green-300 text-green-600 dark:border-green-700 dark:text-green-400",
  UNPUBLISH: "border-amber-300 text-amber-600 dark:border-amber-700 dark:text-amber-400",
  DELETE: "border-red-300 text-red-600 dark:border-red-700 dark:text-red-400",
  RESTORE: "border-teal-300 text-teal-600 dark:border-teal-700 dark:text-teal-400",
}

export default function HomePage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [siteCount, setSiteCount] = useState<number | null>(null)
  const [postTypeCount, setPostTypeCount] = useState<number | null>(null)
  const [appVersion, setAppVersion] = useState<string>("")
  const [activity, setActivity] = useState<ShHistory[]>([])

  useEffect(() => {
    siteService.query().then((s) => setSiteCount(s.length)).catch(() => setSiteCount(0))
    postTypeService.query().then((p) => setPostTypeCount(p.length)).catch(() => setPostTypeCount(0))
    systemInfoService.getInfo().then((i) => setAppVersion(i.appVersion)).catch(() => setAppVersion(""))
    historyService.recent(8).then(setActivity).catch(() => setActivity([]))
  }, [])

  const stats = useMemo(() => [
    { label: t("home.statSites"), value: siteCount, icon: IconFileText, url: `${ROUTES.CONSOLE}/content` },
    { label: t("home.statPostTypes"), value: postTypeCount, icon: IconForms, url: ROUTES.POST_TYPE_ROOT },
  ], [t, siteCount, postTypeCount])

  const quickActions = useMemo(() => [
    { label: t("home.actionNewSite"), icon: IconPlus, url: ROUTES.SITE_NEW },
    { label: t("home.actionBrowseContent"), icon: IconFileText, url: `${ROUTES.CONSOLE}/content` },
    { label: t("home.actionSearch"), icon: IconSearch, url: ROUTES.SEARCH },
    { label: t("home.actionPostTypes"), icon: IconForms, url: ROUTES.POST_TYPE_ROOT },
    { label: t("home.actionWebhooks"), icon: IconWebhook, url: ROUTES.ADMIN_WEBHOOKS },
    { label: t("home.actionActivity"), icon: IconHistory, url: ROUTES.ADMIN_ACTIVITY },
  ], [t])

  const actionLabel = (action: string) => {
    const key = `activity.action${action.charAt(0)}${action.slice(1).toLowerCase()}`
    const label = t(key)
    return label === key ? action : label
  }

  return (
    <>
      <PageHeader title={t("sidebar.dashboard")} turIcon={IconHome} />
      <div className="p-6 space-y-6">
        <div>
          <h2 className="text-2xl font-bold">{t("home.welcomeTitle")}</h2>
          <p className="text-muted-foreground mt-1">{t("home.welcomeSubtitle")}</p>
        </div>

        {/* Stat cards */}
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {stats.map((stat) => (
            <Card key={stat.label} className="cursor-pointer hover:shadow-sm transition-shadow"
              onClick={() => navigate(stat.url)}>
              <CardContent className="flex items-center gap-4 p-6">
                <div className="rounded-full bg-orange-100 dark:bg-orange-950/40 p-3">
                  <stat.icon className="h-6 w-6 text-orange-600 dark:text-orange-400" />
                </div>
                <div>
                  <div className="text-2xl font-bold">{stat.value ?? "—"}</div>
                  <div className="text-sm text-muted-foreground">{stat.label}</div>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>

        <div className="grid gap-6 lg:grid-cols-3">
          {/* Recent activity */}
          <Card className="lg:col-span-2">
            <CardHeader className="flex flex-row items-center justify-between">
              <div>
                <CardTitle className="text-base">{t("home.recentActivity")}</CardTitle>
                <CardDescription>{t("home.recentActivityDesc")}</CardDescription>
              </div>
              <Button variant="ghost" size="sm" onClick={() => navigate(ROUTES.ADMIN_ACTIVITY)}>
                {t("home.viewAll")}
              </Button>
            </CardHeader>
            <CardContent>
              {activity.length === 0 ? (
                <p className="text-sm text-muted-foreground py-6 text-center">{t("home.noActivity")}</p>
              ) : (
                <ul className="divide-y">
                  {activity.map((item) => (
                    <li key={item.id} className="flex items-center gap-3 py-2.5">
                      <Badge variant="outline" className={`shrink-0 ${ACTION_BADGE[item.action] ?? ""}`}>
                        {actionLabel(item.action)}
                      </Badge>
                      <span className="flex-1 text-sm font-medium truncate">{item.description}</span>
                      <span className="text-xs text-muted-foreground shrink-0">
                        {item.date ? new Date(item.date).toLocaleString() : ""}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>

          {/* Quick actions */}
          <Card>
            <CardHeader>
              <CardTitle className="text-base">{t("home.quickActions")}</CardTitle>
              <CardDescription>{t("home.quickActionsDesc")}</CardDescription>
            </CardHeader>
            <CardContent className="grid grid-cols-2 gap-2">
              {quickActions.map((action) => (
                <Button key={action.label} variant="outline" size="sm"
                  className="justify-start h-auto py-2.5"
                  onClick={() => navigate(action.url)}>
                  <action.icon className="mr-2 h-4 w-4 shrink-0" />
                  <span className="truncate">{action.label}</span>
                </Button>
              ))}
            </CardContent>
          </Card>
        </div>

        {appVersion && (
          <p className="text-xs text-muted-foreground text-center pt-2">
            {t("home.version", { version: appVersion })}
          </p>
        )}
      </div>
    </>
  )
}
