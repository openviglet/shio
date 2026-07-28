import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent } from "@/components/ui/card"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { Trash2, RotateCcw, X, Folder, FileText } from "lucide-react"
import { useCallback, useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "@viglet/viglet-design-system"
import { ShPostUnifiedService } from "@/services/post-unified/post-unified.service"
import { ShFolderService } from "@/services/folder/folder.service"

const postService = new ShPostUnifiedService()
const folderService = new ShFolderService()

/**
 * One row of the trash. Posts and deleted folder sections share it (SH200): the curator threw
 * away one thing and wants it back, and which table the row came from is not what they are
 * looking for. `kind` is what routes restore and purge to the right endpoint.
 */
type TrashRow = {
  kind: "post" | "folder"
  id: string
  title: string
  label?: string
}

export default function TrashPage() {
  const { t } = useTranslation()
  const { resetBreadcrumb } = useBreadcrumb()
  const [items, setItems] = useState<TrashRow[]>([])
  const [loading, setLoading] = useState(false)

  const load = useCallback(() => {
    setLoading(true)
    // Both halves in one list. A read that fails must not hide the other's rows, so each
    // falls back to empty instead of rejecting the pair.
    Promise.all([
      postService.trash().catch(() => []),
      folderService.trash().catch(() => []),
    ])
      .then(([posts, folders]) =>
        setItems([
          ...folders.map<TrashRow>((f) => ({
            kind: "folder",
            id: f.id,
            title: f.name || "(untitled)",
            label: t("content.trashSection"),
          })),
          ...posts.map<TrashRow>((p) => ({
            kind: "post",
            id: p.id,
            title: p.title || "(untitled)",
            label: p.shPostType?.title || p.shPostType?.name,
          })),
        ]),
      )
      .finally(() => setLoading(false))
  }, [t])

  useEffect(() => {
    resetBreadcrumb([
      { label: t("content.sites"), href: `${ROUTES.CONSOLE}/content` },
      { label: t("content.trash") },
    ])
    load()
  }, [resetBreadcrumb, t, load])

  const drop = (row: TrashRow) =>
    setItems((prev) => prev.filter((r) => !(r.kind === row.kind && r.id === row.id)))

  const restore = async (row: TrashRow) => {
    const call = row.kind === "folder" ? folderService.untrash(row.id) : postService.untrash(row.id)
    await call
      .then(() => toast.success(t("content.trashRestored")))
      .catch(() => toast.error(t("common.error")))
    drop(row)
  }

  const purge = async (row: TrashRow) => {
    const message =
      row.kind === "folder"
        ? t("content.trashPurgeSectionConfirm", { name: row.title })
        : t("content.trashPurgeConfirm", { name: row.title })
    if (!confirm(message)) return
    const call = row.kind === "folder" ? folderService.purge(row.id) : postService.purge(row.id)
    await call
      .then(() => toast.success(t("content.trashPurged")))
      .catch(() => toast.error(t("common.error")))
    drop(row)
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
                  {items.map((row) => (
                    <TableRow key={`${row.kind}-${row.id}`}>
                      <TableCell className="font-medium">
                        <span className="flex items-center gap-2">
                          {row.kind === "folder" ? (
                            <Folder className="h-4 w-4 text-orange-500" />
                          ) : (
                            <FileText className="h-4 w-4 text-blue-500" />
                          )}
                          {row.title}
                        </span>
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline">{row.label}</Badge>
                      </TableCell>
                      <TableCell>
                        <div className="flex gap-2">
                          <Button variant="outline" size="sm" onClick={() => restore(row)}>
                            <RotateCcw className="mr-2 h-4 w-4" /> {t("content.trashRestore")}
                          </Button>
                          <Button variant="ghost" size="sm" className="text-destructive"
                            onClick={() => purge(row)}>
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
