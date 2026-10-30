import { IconPencil, IconTrash } from "@tabler/icons-react"
import {
  BentoIdentityCell, BentoListPage, type BentoDataTableAction, type BentoDataTableColumn,
} from "@viglet/viglet-design-system/bento"
import { ROUTES } from "@/app/routes.const"
import { distinctKey, labelOf } from "@/lib/post-type-label"
import { useMemo } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import type { ShPostType } from "@/models/post-type/post-type"
import { ShPostTypeService } from "@/services/post-type/post-type.service"
import { toast } from "@viglet/viglet-design-system"
import { usePostTypes, useInvalidate } from "@/hooks/use-catalogue"
import { headlineOf, problemOf, reportProblem } from "@/lib/problem"

const service = new ShPostTypeService()

/**
 * SH1310 — the second line, when there is one: the API key where it differs from the title, in the
 * mono face so it reads as a key, and a muted System mark that replaced a column of mostly nothing.
 */
function TypeDetail({ type, system }: Readonly<{ type: ShPostType; system: string }>) {
  const key = distinctKey(type)
  if (!key && !type.system) return null
  return (
    <span className="flex items-center gap-2">
      {key && <span className="font-mono">{key}</span>}
      {type.system ? <span className="text-muted-foreground">{system}</span> : null}
    </span>
  )
}

/** Whether a type's row draws a second line, which is what the name column's height follows. */
const hasDetail = (type: ShPostType) => Boolean(distinctKey(type) || type.system)

/**
 * SH1225 — the content model, on the design system's table.
 *
 * It was a shadcn `Table` with Name, Title, System and a column of two icon buttons per row, so it
 * had no sortable header, no row keyboard model and no column menu, and every change to the
 * package's table skipped it (SH1224's debt list). The type is now one identity cell, and Edit and
 * Delete are the row's actions menu, which a keyboard reaches, rather than two buttons in a column.
 * SH1310 — its second line is the key only where it differs from the title, and System is a mark
 * on that line rather than a column.
 */
export default function PostTypeListPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  // SH1046 — the catalogue, cached under `postType:` and shared with every other screen.
  const { data, error: failure } = usePostTypes()
  // `undefined` is "still loading" to the package's gate, so a failure has to become an empty array
  // and an error, or a failed load spins for ever.
  const items = data ?? (failure ? [] : undefined)
  const error = failure ? headlineOf(problemOf(failure), failure.message) : null
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

  const open = (type: ShPostType) =>
    navigate(`${ROUTES.POST_TYPE_ROOT}/${encodeURIComponent(type.name)}`)

  // SH1314's taller row only while some type has a second line to draw.
  const twoLines = Boolean(items?.some(hasDetail))
  const columns = useMemo<BentoDataTableColumn<ShPostType>[]>(() => [
    {
      id: "name", header: t("common.name"), width: "minmax(16rem, 3fr)", hideable: false,
      lines: twoLines ? 2 : 1,
      sortValue: (type) => labelOf(type).toLowerCase(),
      cell: (type) => (
        <BentoIdentityCell name={labelOf(type)}
          detail={hasDetail(type) ? <TypeDetail type={type} system={t("content.system")} /> : undefined} />
      ),
    },
  ], [t, twoLines])

  // The package hands the one row, or every selected one; each is deleted with its own answer.
  const rowActions: BentoDataTableAction<ShPostType>[] = [
    { id: "edit", label: t("common.edit"), icon: IconPencil, onSelect: ([type]) => open(type) },
    {
      id: "delete", label: t("common.delete"), icon: IconTrash, tone: "destructive",
      onSelect: (types) => { for (const type of types) void handleDelete(type.name) },
    },
  ]

  return (
    <BentoListPage
      items={items}
      error={error}
      tryAgainUrl={ROUTES.POST_TYPE_ROOT}
      listId="post-types"
      backTo={ROUTES.HOME}
      backLabel={t("home.title")}
      title={t("content.postTypes")}
      subtitle={t("nav.desc.postTypes")}
      newRoute={`${ROUTES.POST_TYPE_ROOT}/new`}
      newLabel={t("common.create")}
      itemKey={(type: ShPostType) => type.name}
      columns={columns}
      rowActions={rowActions}
      getRowLabel={labelOf}
      onRowOpen={open}
      emptyTitle={t("common.noResults")}
      emptyDescription={t("content.createFirstPostType")}
    />
  )
}
