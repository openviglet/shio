import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent } from "@/components/ui/card"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { IconSettings } from "@tabler/icons-react"
import { Plus, Pencil, Trash2 } from "lucide-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import axios from "axios"

type ShAuthProvider = {
  id: string
  name: string
  description: string
  vendor: string
  enabled: boolean
}

export default function ConfigAuthProvidersPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { resetBreadcrumb } = useBreadcrumb()
  const [items, setItems] = useState<ShAuthProvider[]>([])

  useEffect(() => {
    resetBreadcrumb([
      { label: t("config.title"), href: ROUTES.CONFIG_ROOT },
      { label: t("config.authProviders") },
    ])
    axios.get<ShAuthProvider[]>("/api/v2/config/provider/auth").then((res) => setItems(res.data))
  }, [resetBreadcrumb, t])

  const handleDelete = async (id: string) => {
    if (confirm(t("dialog.deleteMessage"))) {
      await axios.delete(`/api/v2/config/provider/auth/${id}`)
      setItems((prev) => prev.filter((i) => i.id !== id))
    }
  }

  return (
    <>
      <PageHeader title={t("config.authProviders")} turIcon={IconSettings} urlBase={ROUTES.CONFIG_ROOT} />
      <div className="p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold">{t("config.authProviders")}</h2>
          <Button onClick={() => navigate(`${ROUTES.CONFIG_AUTH_PROVIDERS}/new`)} size="sm">
            <Plus className="mr-2 h-4 w-4" />
            {t("common.create")}
          </Button>
        </div>
        <Card>
          <CardContent className="p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t("common.name")}</TableHead>
                  <TableHead>{t("config.vendor")}</TableHead>
                  <TableHead>{t("config.enabled")}</TableHead>
                  <TableHead className="w-24">{t("common.actions")}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {items.map((item) => (
                  <TableRow key={item.id}>
                    <TableCell className="font-medium">{item.name}</TableCell>
                    <TableCell>{item.vendor}</TableCell>
                    <TableCell>
                      <Badge variant={item.enabled ? "default" : "secondary"}>
                        {item.enabled ? t("config.enabled") : t("config.disabled")}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <div className="flex gap-1">
                        <Button variant="ghost" size="icon" onClick={() => navigate(`${ROUTES.CONFIG_AUTH_PROVIDERS}/${item.id}`)}>
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button variant="ghost" size="icon" onClick={() => handleDelete(item.id)}>
                          <Trash2 className="h-4 w-4 text-destructive" />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
                {items.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={4} className="text-center text-muted-foreground py-8">
                      {t("common.noResults")}
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </div>
    </>
  )
}
