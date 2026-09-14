import { PageHero } from "@/components/page-hero"
import { Panel } from "@/components/panel"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { ROUTES } from "@/app/routes.const"
import { IconFileText } from "@tabler/icons-react"
import { Pencil, Plus, Trash2 } from "lucide-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { ShPostTypeService } from "@/services/post-type/post-type.service"
import type { ShPostType } from "@/models/post-type/post-type"
import { toast } from "@viglet/viglet-design-system"

const service = new ShPostTypeService()

export default function PostTypeListPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [items, setItems] = useState<ShPostType[]>([])

  useEffect(() => {
    service.query().then(setItems)
  }, [t])

  const handleDelete = async (name: string) => {
    if (confirm(t("dialog.deleteMessage"))) {
      await service.delete(name)
      setItems((prev) => prev.filter((i) => i.name !== name))
      toast.success(t("common.delete"))
    }
  }

  return (
    <>
      {/* SH902 — header and panel through the adapters; the row list itself is untouched. */}
      <PageHero
        icon={IconFileText}
        eyebrow={t("sidebar.content")}
        title={t("content.postTypes")}
        urlBase={ROUTES.CONTENT_ROOT}
      />
      <div className="p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold">{t("content.postTypes")}</h2>
          <Button onClick={() => navigate(`${ROUTES.POST_TYPE_ROOT}/new`)} size="sm">
            <Plus className="mr-2 h-4 w-4" />
            {t("common.create")}
          </Button>
        </div>
        <Panel contentClassName="p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t("common.name")}</TableHead>
                  <TableHead>{t("content.title")}</TableHead>
                  <TableHead>{t("content.system")}</TableHead>
                  <TableHead className="w-24">{t("common.actions")}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {items.map((item) => (
                  <TableRow key={item.name}>
                    <TableCell className="font-medium">{item.name}</TableCell>
                    <TableCell>{item.title}</TableCell>
                    <TableCell>
                      {item.system ? (
                        <Badge variant="secondary">{t("content.system")}</Badge>
                      ) : null}
                    </TableCell>
                    <TableCell>
                      <div className="flex gap-1">
                        <Button variant="ghost" size="icon" onClick={() => navigate(`${ROUTES.POST_TYPE_ROOT}/${encodeURIComponent(item.name)}`)}>
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button variant="ghost" size="icon" onClick={() => handleDelete(item.name)}>
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
        </Panel>
      </div>
    </>
  )
}
