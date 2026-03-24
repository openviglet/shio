import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { IconShieldCog } from "@tabler/icons-react"
import { Plus, Pencil, Trash2 } from "lucide-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { ShUserService } from "@/services/auth/user.service"
import type { ShUser } from "@/models/auth/user"

const service = new ShUserService()

export default function AdminUsersListPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { resetBreadcrumb } = useBreadcrumb()
  const [items, setItems] = useState<ShUser[]>([])

  useEffect(() => {
    resetBreadcrumb([
      { label: t("admin.title"), href: ROUTES.ADMIN_ROOT },
      { label: t("admin.users") },
    ])
    service.list().then(setItems)
  }, [resetBreadcrumb, t])

  const handleDelete = async (username: string) => {
    if (confirm(t("dialog.deleteMessage"))) {
      await service.delete(username)
      setItems((prev) => prev.filter((i) => i.username !== username))
    }
  }

  return (
    <>
      <PageHeader title={t("admin.users")} turIcon={IconShieldCog} urlBase={ROUTES.ADMIN_ROOT} />
      <div className="p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold">{t("admin.users")}</h2>
          <Button onClick={() => navigate(`${ROUTES.ADMIN_ROOT}/users/new`)} size="sm">
            <Plus className="mr-2 h-4 w-4" />
            {t("common.create")}
          </Button>
        </div>
        <Card>
          <CardContent className="p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t("common.username")}</TableHead>
                  <TableHead>{t("common.firstName")}</TableHead>
                  <TableHead>{t("common.lastName")}</TableHead>
                  <TableHead>{t("common.email")}</TableHead>
                  <TableHead className="w-24">{t("common.actions")}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {items.map((item) => (
                  <TableRow key={item.username}>
                    <TableCell className="font-medium">{item.username}</TableCell>
                    <TableCell>{item.firstName}</TableCell>
                    <TableCell>{item.lastName}</TableCell>
                    <TableCell>{item.email}</TableCell>
                    <TableCell>
                      <div className="flex gap-1">
                        <Button variant="ghost" size="icon" onClick={() => navigate(`${ROUTES.ADMIN_ROOT}/users/${item.username}`)}>
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button variant="ghost" size="icon" onClick={() => handleDelete(item.username)}>
                          <Trash2 className="h-4 w-4 text-destructive" />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
                {items.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={5} className="text-center text-muted-foreground py-8">
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
