import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent } from "@/components/ui/card"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { Fingerprint, Plus, Pencil, Trash2 } from "lucide-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { ShAuthProviderService } from "@/services/provider/auth-provider.service"
import type { ShAuthProviderInstance } from "@/models/provider/auth-provider.model"

const service = new ShAuthProviderService()

export default function AuthProviderListPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { resetBreadcrumb } = useBreadcrumb()
  const [items, setItems] = useState<ShAuthProviderInstance[]>([])

  useEffect(() => {
    resetBreadcrumb([
      { label: t("admin.title"), href: ROUTES.ADMIN_ROOT },
      { label: t("authProvider.title") },
    ])
    service.list().then(setItems).catch(() => setItems([]))
  }, [resetBreadcrumb, t])

  const handleDelete = async (id: string) => {
    if (confirm(t("dialog.deleteMessage"))) {
      await service.delete(id)
      setItems((prev) => prev.filter((i) => i.id !== id))
    }
  }

  return (
    <>
      <PageHeader title={t("authProvider.title")} turIcon={Fingerprint} urlBase={ROUTES.ADMIN_ROOT} />
      <div className="p-6">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h2 className="text-lg font-semibold">{t("authProvider.title")}</h2>
            <p className="text-sm text-muted-foreground">{t("authProvider.description")}</p>
          </div>
          <Button onClick={() => navigate(`${ROUTES.ADMIN_AUTH_PROVIDERS}/new`)} size="sm">
            <Plus className="mr-2 h-4 w-4" />
            {t("authProvider.newInstance")}
          </Button>
        </div>

        {items.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-center">
            <div className="rounded-full bg-muted p-4 mb-4">
              <Fingerprint className="h-8 w-8 text-muted-foreground" />
            </div>
            <h3 className="font-semibold text-lg mb-1">{t("authProvider.blankTitle")}</h3>
            <p className="text-sm text-muted-foreground max-w-sm mb-6">{t("authProvider.blankDescription")}</p>
            <Button onClick={() => navigate(`${ROUTES.ADMIN_AUTH_PROVIDERS}/new`)}>
              <Plus className="mr-2 h-4 w-4" />
              {t("authProvider.newInstance")}
            </Button>
          </div>
        ) : (
          <Card>
            <CardContent className="p-0">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t("common.name")}</TableHead>
                    <TableHead>{t("common.description")}</TableHead>
                    <TableHead>{t("authProvider.vendor")}</TableHead>
                    <TableHead>{t("common.status")}</TableHead>
                    <TableHead className="w-24">{t("common.actions")}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {items.map((item) => (
                    <TableRow key={item.id} className="cursor-pointer"
                      onClick={() => navigate(`${ROUTES.ADMIN_AUTH_PROVIDERS}/${item.id}`)}>
                      <TableCell className="font-medium">{item.name}</TableCell>
                      <TableCell className="text-muted-foreground">{item.description}</TableCell>
                      <TableCell>
                        {item.vendor?.name && (
                          <Badge variant="outline">{item.vendor.name}</Badge>
                        )}
                      </TableCell>
                      <TableCell>
                        <Badge variant={item.enabled ? "default" : "secondary"}>
                          {item.enabled ? t("common.enabled") : t("common.disabled")}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <div className="flex gap-1" onClick={(e) => e.stopPropagation()}>
                          <Button variant="ghost" size="icon" className="h-8 w-8"
                            onClick={() => navigate(`${ROUTES.ADMIN_AUTH_PROVIDERS}/${item.id}`)}>
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
