import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent } from "@/components/ui/card"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { Building2, Plus, Pencil, Trash2 } from "lucide-react"
import { useEffect, useState, type MouseEvent } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { ShTenantService } from "@/services/tenant/tenant.service"
import type { ShTenant } from "@/models/tenant/tenant.model"

const service = new ShTenantService()

export default function TenantListPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { resetBreadcrumb } = useBreadcrumb()
  const [items, setItems] = useState<ShTenant[]>([])

  useEffect(() => {
    resetBreadcrumb([
      { label: t("admin.title"), href: ROUTES.ADMIN_ROOT },
      { label: t("tenantAdmin.title") },
    ])
    service.list().then(setItems).catch(() => setItems([]))
  }, [resetBreadcrumb, t])

  const handleDelete = async (e: MouseEvent, item: ShTenant) => {
    e.stopPropagation()
    if (item.name === "shio") return
    if (confirm(t("dialog.deleteMessage"))) {
      await service.delete(item.id)
      setItems((prev) => prev.filter((i) => i.id !== item.id))
    }
  }

  return (
    <>
      <PageHeader title={t("tenantAdmin.title")} turIcon={Building2} urlBase={ROUTES.ADMIN_ROOT} />
      <div className="p-6">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h2 className="text-lg font-semibold">{t("tenantAdmin.title")}</h2>
            <p className="text-sm text-muted-foreground">{t("tenantAdmin.description")}</p>
          </div>
          <Button onClick={() => navigate(`${ROUTES.ADMIN_TENANTS}/new`)} size="sm">
            <Plus className="mr-2 h-4 w-4" />
            {t("tenantAdmin.newInstance")}
          </Button>
        </div>

        {items.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-center">
            <div className="rounded-full bg-muted p-4 mb-4">
              <Building2 className="h-8 w-8 text-muted-foreground" />
            </div>
            <h3 className="font-semibold text-lg mb-1">{t("tenantAdmin.blankTitle")}</h3>
            <p className="text-sm text-muted-foreground max-w-sm mb-6">{t("tenantAdmin.blankDescription")}</p>
            <Button onClick={() => navigate(`${ROUTES.ADMIN_TENANTS}/new`)}>
              <Plus className="mr-2 h-4 w-4" />
              {t("tenantAdmin.newInstance")}
            </Button>
          </div>
        ) : (
          <Card>
            <CardContent className="p-0">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t("tenantAdmin.name")}</TableHead>
                    <TableHead>{t("common.description")}</TableHead>
                    <TableHead>{t("common.status")}</TableHead>
                    <TableHead>{t("common.owner")}</TableHead>
                    <TableHead className="w-24">{t("common.actions")}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {items.map((item) => (
                    <TableRow key={item.id} className="cursor-pointer"
                      onClick={() => navigate(`${ROUTES.ADMIN_TENANTS}/${item.id}`)}>
                      <TableCell className="font-medium">
                        <div className="flex items-center gap-2">
                          <span className="font-mono">{item.name}</span>
                          {item.name === "shio" && (
                            <Badge variant="secondary" className="text-xs">{t("tenantAdmin.defaultBadge")}</Badge>
                          )}
                        </div>
                      </TableCell>
                      <TableCell className="text-muted-foreground">{item.description}</TableCell>
                      <TableCell>
                        <Badge variant={item.enabled ? "default" : "secondary"}>
                          {item.enabled ? t("common.enabled") : t("common.disabled")}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-muted-foreground">{item.owner}</TableCell>
                      <TableCell>
                        <div className="flex gap-1" onClick={(e) => e.stopPropagation()}>
                          <Button variant="ghost" size="icon" className="h-8 w-8"
                            onClick={() => navigate(`${ROUTES.ADMIN_TENANTS}/${item.id}`)}>
                            <Pencil className="h-4 w-4" />
                          </Button>
                          {item.name !== "shio" && (
                            <Button variant="ghost" size="icon" className="h-8 w-8 text-destructive"
                              onClick={(e) => handleDelete(e, item)}>
                              <Trash2 className="h-4 w-4" />
                            </Button>
                          )}
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
