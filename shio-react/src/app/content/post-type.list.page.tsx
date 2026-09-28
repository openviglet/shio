import { PageHero } from "@/components/page-hero"
import { Panel } from "@/components/panel"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { ROUTES } from "@/app/routes.const"
import { Pencil, Plus, Trash2 } from "lucide-react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { ShPostTypeService } from "@/services/post-type/post-type.service"
import { toast } from "@viglet/viglet-design-system"
import { usePostTypes, useInvalidate } from "@/hooks/use-catalogue"
import { reportProblem } from "@/lib/problem"

const service = new ShPostTypeService()

export default function PostTypeListPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  // SH1046 — the catalogue, cached under `postType:` and shared with every other screen.
  const items = usePostTypes().data ?? []
  const invalidate = useInvalidate()

  const handleDelete = async (name: string) => {
    if (confirm(t("dialog.deleteMessage"))) {
      // SH1139 — a post-type in use is refused, which is the common case here, and the refusal used
      // to escape as an unhandled rejection: the row stayed, nothing was said, and the curator had
      // no way to learn that the type still has posts under it.
      try {
        await service.delete(name)
        // The write half of the cache, and the half it is wrong without: dropping the scheme drops
        // the catalogue AND every single type under it, so no other screen keeps the deleted row.
        // Filtering local state instead would leave this page right and the next one wrong.
        await invalidate("postType:")
        toast.success(t("common.deleted"))
      } catch (error) {
        reportProblem(error, t("common.error"))
      }
    }
  }

  return (
    <>
      {/* SH902 — header and panel through the adapters; the row list itself is untouched. */}
      <PageHero
        eyebrow={t("sidebar.content")}
        title={t("content.postTypes")}
        urlBase={ROUTES.HOME}
        backLabel={t("home.title")}
      />
      <div>
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
                        {/* SH1054 — named, and after the type: axe found 32 unnamed icon buttons here, a
                            pair per row, each announced as "button" and nothing else. */}
                        <Button variant="ghost" size="icon" aria-label={`${t("common.edit")} ${item.title || item.name}`}
                          onClick={() => navigate(`${ROUTES.POST_TYPE_ROOT}/${encodeURIComponent(item.name)}`)}>
                          <Pencil className="h-4 w-4" aria-hidden="true" />
                        </Button>
                        <Button variant="ghost" size="icon" aria-label={`${t("common.delete")} ${item.title || item.name}`}
                          onClick={() => handleDelete(item.name)}>
                          <Trash2 className="h-4 w-4 text-destructive" aria-hidden="true" />
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
