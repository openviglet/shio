import { IconTrash } from "@tabler/icons-react"
import { AdaptiveEmptyState } from "@/components/adaptive-empty-state"
import { AdaptivePageHeader } from "@/components/adaptive-page-header"
import { AdaptivePanel } from "@/components/adaptive-panel"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { Trash2, RotateCcw, X, Folder, FileText, Globe } from "lucide-react"
import { useCallback, useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "@viglet/viglet-design-system"
import { ShPostUnifiedService } from "@/services/post-unified/post-unified.service"
import { ShFolderService } from "@/services/folder/folder.service"
import { ShSiteService } from "@/services/site/site.service"

const postService = new ShPostUnifiedService()
const folderService = new ShFolderService()
const siteService = new ShSiteService()

/**
 * One row of the trash. Posts, deleted folder sections and deleted sites share it (SH200, SH648):
 * the curator threw away one thing and wants it back, and which table the row came from is not
 * what they are looking for. `kind` is what routes restore and purge to the right endpoint.
 */
type TrashRow = {
  kind: "post" | "folder" | "site"
  id: string
  title: string
  label?: string
}

export default function TrashPage() {
  const { t } = useTranslation()
  const { resetBreadcrumb } = useBreadcrumb()
  const [items, setItems] = useState<TrashRow[]>([])
  const [loading, setLoading] = useState(true)

  /** The read. It raises no state before the request, which is what lets the mount effect call it. */
  const fetchTrash = useCallback(() => {
    // All three in one list. A read that fails must not hide the others' rows, so each falls
    // back to empty instead of rejecting the set.
    return Promise.all([
      postService.trash().catch(() => []),
      folderService.trash().catch(() => []),
      siteService.trash().catch(() => []),
    ])
      .then(([posts, folders, sites]) =>
        setItems([
          // Sites first, and deliberately: it is the widest thing in here, it takes folders and
          // posts of its own down with it, and restoring it is what the curator wants before they
          // start restoring rows that came from it.
          ...sites.map<TrashRow>((s) => ({
            kind: "site",
            id: s.id,
            title: s.name || "(untitled)",
            label: t("content.siteLabel"),
          })),
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

  // No `load` wrapper here: this page has no refresh control, so the mount read is the only one
  // and a `setLoading(true)` nobody triggers would be the cascading render itself (SH701).
  useEffect(() => {
    resetBreadcrumb([
      { label: t("content.sites"), href: `${ROUTES.CONSOLE}/content` },
      { label: t("content.trash") },
    ])
    void fetchTrash()
  }, [resetBreadcrumb, t, fetchTrash])

  const drop = (row: TrashRow) =>
    setItems((prev) => prev.filter((r) => !(r.kind === row.kind && r.id === row.id)))

  /** The service each row's verbs belong to — one lookup, so restore and purge cannot disagree. */
  const serviceFor = (kind: TrashRow["kind"]) =>
    ({ site: siteService, folder: folderService, post: postService })[kind]

  const restore = async (row: TrashRow) => {
    await serviceFor(row.kind)
      .untrash(row.id)
      .then(() => toast.success(t("content.trashRestored")))
      // The message matters here in a way it did not before SH648: a site restore can be REFUSED
      // (a live site took its friendly URL), and the refusal names the occupant and both ways out.
      // Swallowing it into "an error occurred" would leave the curator with a button that does
      // nothing and no way to find out why.
      .catch((error) => toast.error(problemOf(error, t("common.error"))))
    drop(row)
  }

  const purge = async (row: TrashRow) => {
    const message =
      row.kind === "post"
        ? t("content.trashPurgeConfirm", { name: row.title })
        : t("content.trashPurgeSectionConfirm", { name: row.title })
    if (!confirm(message)) return
    await serviceFor(row.kind)
      .purge(row.id)
      .then(() => toast.success(t("content.trashPurged")))
      .catch((error) => toast.error(problemOf(error, t("common.error"))))
    drop(row)
  }

  return (
    <>
      <AdaptivePageHeader
        icon={Trash2}
        title={t("content.trash")}
        subtitle={t("content.trashDescription")}
        urlBase={`${ROUTES.CONSOLE}/content`}
      />
      <div className="p-6">

        {!loading && items.length === 0 ? (
          <AdaptiveEmptyState
            icon={IconTrash}
            tone="slate"
            title={t("content.trashEmpty")}
            description={t("content.trashEmptyHint")}
          />
        ) : (
          <AdaptivePanel contentClassName="p-0">
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
                        <RowIcon kind={row.kind} />
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
          </AdaptivePanel>
        )}
      </div>
    </>
  )
}

/** What kind of thing the row is, at a glance — the column's label says it in words. */
function RowIcon({ kind }: { kind: TrashRow["kind"] }) {
  if (kind === "site") return <Globe className="h-4 w-4 text-emerald-600" />
  if (kind === "folder") return <Folder className="h-4 w-4 text-orange-500" />
  return <FileText className="h-4 w-4 text-blue-500" />
}

/**
 * The server's teaching error, or a fallback.
 *
 * Every 4xx here carries `fix` (P6), and since SH648 the trash has refusals worth reading: a site
 * restore is refused when a live site took its friendly URL, and the message names the occupant and
 * both ways out. A bare "an error occurred" would leave the curator with a button that does nothing.
 */
function problemOf(error: unknown, fallback: string): string {
  const problem = (error as { response?: { data?: { title?: string; fix?: string } } })?.response?.data
  if (!problem) return fallback
  return [problem.title, problem.fix].filter(Boolean).join(" — ") || fallback
}
