import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent } from "@/components/ui/card"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { Webhook, Plus, Pencil, Trash2 } from "lucide-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { ShSiteWebhookService } from "@/services/webhook/site-webhook.service"
import type { ShSiteWebhook } from "@/models/webhook/site-webhook.model"

const service = new ShSiteWebhookService()

export default function SiteWebhookListPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { resetBreadcrumb } = useBreadcrumb()
  const [items, setItems] = useState<ShSiteWebhook[]>([])

  useEffect(() => {
    resetBreadcrumb([
      { label: t("admin.title"), href: ROUTES.ADMIN_ROOT },
      { label: t("siteWebhook.title") },
    ])
    service.list().then(setItems)
  }, [resetBreadcrumb, t])

  const handleDelete = async (id: string) => {
    if (confirm(t("dialog.deleteMessage"))) {
      await service.delete(id)
      setItems((prev) => prev.filter((i) => i.id !== id))
    }
  }

  return (
    <>
      <PageHeader title={t("siteWebhook.title")} turIcon={Webhook} urlBase={ROUTES.ADMIN_ROOT} />
      <div className="p-6">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h2 className="text-lg font-semibold">{t("siteWebhook.title")}</h2>
            <p className="text-sm text-muted-foreground">{t("siteWebhook.description")}</p>
          </div>
          <Button onClick={() => navigate(`${ROUTES.ADMIN_WEBHOOKS}/new`)} size="sm">
            <Plus className="mr-2 h-4 w-4" />
            {t("siteWebhook.newInstance")}
          </Button>
        </div>

        {items.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-center">
            <div className="rounded-full bg-muted p-4 mb-4">
              <Webhook className="h-8 w-8 text-muted-foreground" />
            </div>
            <h3 className="font-semibold text-lg mb-1">{t("siteWebhook.blankTitle")}</h3>
            <p className="text-sm text-muted-foreground max-w-sm mb-6">{t("siteWebhook.blankDescription")}</p>
            <Button onClick={() => navigate(`${ROUTES.ADMIN_WEBHOOKS}/new`)}>
              <Plus className="mr-2 h-4 w-4" />
              {t("siteWebhook.newInstance")}
            </Button>
          </div>
        ) : (
          <Card>
            <CardContent className="p-0">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t("common.title")}</TableHead>
                    <TableHead>{t("siteWebhook.url")}</TableHead>
                    <TableHead>{t("siteWebhook.events")}</TableHead>
                    <TableHead>{t("common.status")}</TableHead>
                    <TableHead>{t("common.owner")}</TableHead>
                    <TableHead className="w-24">{t("common.actions")}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {items.map((item) => (
                    <TableRow key={item.id} className="cursor-pointer"
                      onClick={() => navigate(`${ROUTES.ADMIN_WEBHOOKS}/${item.id}`)}>
                      <TableCell className="font-medium">{item.name}</TableCell>
                      <TableCell className="text-muted-foreground font-mono text-xs max-w-xs truncate">{item.url}</TableCell>
                      <TableCell>
                        <div className="flex flex-wrap items-center gap-1">
                          {(item.events?.length ?? 0) === 0 ? (
                            <Badge variant="secondary" className="text-xs">{t("siteWebhook.allEvents")}</Badge>
                          ) : (
                            item.events.map((evt) => (
                              <Badge key={evt} variant="outline" className="text-xs">
                                {t(`siteWebhook.event${evt.charAt(0)}${evt.slice(1).toLowerCase()}`)}
                              </Badge>
                            ))
                          )}
                        </div>
                      </TableCell>
                      <TableCell>
                        <Badge variant={item.enabled ? "default" : "secondary"}>
                          {item.enabled ? t("common.enabled") : t("common.disabled")}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-muted-foreground">{item.owner}</TableCell>
                      <TableCell>
                        <div className="flex gap-1" onClick={(e) => e.stopPropagation()}>
                          <Button variant="ghost" size="icon" className="h-8 w-8"
                            onClick={() => navigate(`${ROUTES.ADMIN_WEBHOOKS}/${item.id}`)}>
                            <Pencil className="h-4 w-4" />
                          </Button>
                          <Button variant="ghost" size="icon" className="h-8 w-8 text-destructive"
                            onClick={() => handleDelete(item.id)}>
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        </div>
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
