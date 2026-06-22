import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent } from "@/components/ui/card"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { Trash2, RotateCcw, X } from "lucide-react"
import { useCallback, useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "@viglet/viglet-design-system"
import { ShPostUnifiedService } from "@/services/post-unified/post-unified.service"
import type { ShPostUnified } from "@/models/post-unified/post-unified"

const service = new ShPostUnifiedService()

export default function TrashPage() {
  const { t } = useTranslation()
  const { resetBreadcrumb } = useBreadcrumb()
  const [items, setItems] = useState<ShPostUnified[]>([])
  const [loading, setLoading] = useState(false)

  const load = useCallback(() => {
    setLoading(true)
    service.trash().then(setItems).catch(() => setItems([])).finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    resetBreadcrumb([
      { label: t("content.sites"), href: `${ROUTES.CONSOLE}/content` },
      { label: t("content.trash") },
    ])
    load()
  }, [resetBreadcrumb, t, load])

  const restore = async (id: string) => {
    await service.untrash(id).then(() => toast.success(t("content.trashRestored"))).catch(() => toast.error(t("common.error")))
    setItems((prev) => prev.filter((p) => p.id !== id))
  }

  const purge = async (id: string, name?: string) => {
    if (!confirm(t("content.trashPurgeConfirm", { name: name || "" }))) return
    await service.purge(id).then(() => toast.success(t("content.trashPurged"))).catch(() => toast.error(t("common.error")))
    setItems((prev) => prev.filter((p) => p.id !== id))
  }

  return (
    <>
      <PageHeader title={t("content.trash")} turIcon={Trash2} urlBase={`${ROUTES.CONSOLE}/content`} />
      <div className="p-6">
        <div className="mb-4">
          <h2 className="text-lg font-semibold">{t("content.trash")}</h2>
          <p className="text-sm text-muted-foreground">{t("content.trashDescription")}</p>
        </div>

        {!loading && items.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-center">
            <div className="rounded-full bg-muted p-4 mb-4">
              <Trash2 className="h-8 w-8 text-muted-foreground" />
            </div>
            <h3 className="font-semibold text-lg mb-1">{t("content.trashEmpty")}</h3>
            <p className="text-sm text-muted-foreground max-w-sm">{t("content.trashEmptyHint")}</p>
          </div>
        ) : (
          <Card>
            <CardContent className="p-0">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t("common.title")}</TableHead>
                    <TableHead className="w-40">{t("content.postType")}</TableHead>
                    <TableHead className="w-48">{t("common.actions")}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {items.map((item) => (
                    <TableRow key={item.id}>
                      <TableCell className="font-medium">{item.title || "(untitled)"}</TableCell>
                      <TableCell>
                        <Badge variant="outline">{item.shPostType?.title || item.shPostType?.name}</Badge>
                      </TableCell>
                      <TableCell>
                        <div className="flex gap-2">
                          <Button variant="outline" size="sm" onClick={() => restore(item.id)}>
                            <RotateCcw className="mr-2 h-4 w-4" /> {t("content.trashRestore")}
                          </Button>
                          <Button variant="ghost" size="sm" className="text-destructive"
                            onClick={() => purge(item.id, item.title)}>
                            <X className="mr-2 h-4 w-4" /> {t("content.trashPurge")}
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
