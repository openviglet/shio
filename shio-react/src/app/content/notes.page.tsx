import { IconCheck, IconExternalLink, IconMessage } from "@tabler/icons-react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import {
  BentoChangeCell, BentoDataTable, BentoIdentityCell, type BentoDataTableAction,
} from "@viglet/viglet-design-system/bento"
import { addressKey } from "@/lib/query-keys"
import { EmptyState } from "@/components/empty-state"
import { PageHero } from "@/components/page-hero"
import { Badge } from "@/components/ui/badge"
import { ROUTES } from "@/app/routes.const"
import { reportProblem } from "@/lib/problem"
import type { ShOpenNote } from "@/models/note/note.model"
import { ShNoteService } from "@/services/note/note.service"

const service = new ShNoteService()

const KIND_LABEL: Record<string, string> = {
  COMMENT: "note.kindComment",
  REQUEST: "note.kindRequest",
}

/**
 * SH1214 — every note still asking for something, newest first.
 *
 * Home's Open notes tile counted these and linked to the content browser, which shows no notes:
 * the tile said something was owed and the click led nowhere that said what. This reads the same
 * `GET /note/open` an agent reads, so the count and the list cannot disagree, and each row leads to
 * the page whose note rail holds the thread. Resolving from here is the one write, because a
 * resolved note is one the curator has already acted on elsewhere.
 */
export default function NotesPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const read = useQuery<ShOpenNote[]>({
    queryKey: addressKey("summary:notes"),
    queryFn: ({ signal }) => service.open({ signal }),
  })
  const notes = read.data ?? []

  const kindLabel = (kind: string) => {
    const key = KIND_LABEL[kind?.toUpperCase()]
    return key ? t(key) : kind
  }

  const resolve = async (note: ShOpenNote) => {
    try {
      await service.resolve(note.id)
      toast.success(t("note.openResolved", { page: note.address ?? t("note.openGone") }))
    } catch (error) {
      reportProblem(error, t("common.error"))
    } finally {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: addressKey("summary:notes") }),
        queryClient.invalidateQueries({ queryKey: addressKey("summary:") }),
      ])
    }
  }

  /** The page whose note rail holds the thread; a note on a page that is gone opens nothing. */
  const openPage = (note: ShOpenNote) => {
    if (note.address?.startsWith("post:")) navigate(`${ROUTES.CONSOLE}/content/post/${note.objectId}`)
  }
  const resolveAction: BentoDataTableAction<ShOpenNote> = {
    id: "resolve", label: t("note.resolve"), icon: IconCheck,
    onSelect: (chosen) => { for (const note of chosen) void resolve(note) },
  }
  const openAction: BentoDataTableAction<ShOpenNote> = {
    id: "open", label: t("note.openOpenPage"), icon: IconExternalLink, onSelect: ([note]) => openPage(note),
  }

  return (
    <>
      <PageHero
        title={t("note.openTitle")}
        subtitle={t("note.openDescription")}
        urlBase={ROUTES.HOME}
        backLabel={t("home.title")}
      />
      {!read.isPending && notes.length === 0 ? (
        <EmptyState
          icon={IconMessage}
          title={t("note.openEmptyTitle")}
          description={t("note.openEmptyDescription")}
        />
      ) : (
        /*
         * SH1232 — the notes on the design system's table, like the lists around it. The page is the
         * identity cell (the field under it, when the note is about one), the note is its kind and
         * one line of its body with the whole of it as the tooltip, and when it was left is the
         * change cell, naming who. Resolve is the row's menu and the selection bar, so a curator who
         * has dealt with several closes them in one gesture; Enter opens the page holding the thread.
         */
        <BentoDataTable
          rows={notes}
          getRowId={(note) => note.id}
          getRowLabel={(note) => `${kindLabel(note.kind)}: ${note.address ?? t("note.openGone")}`}
          columns={[
            {
              id: "page", header: t("note.openPage"), width: "minmax(16rem, 2fr)", hideable: false, lines: 2,
              sortValue: (note) => note.address ?? "",
              cell: (note) => (
                <BentoIdentityCell name={note.address ?? t("note.openGone")} detail={note.field ?? undefined} />
              ),
            },
            {
              id: "note", header: t("note.openNote"), width: "minmax(18rem, 3fr)",
              sortValue: (note) => note.kind,
              cell: (note) => (
                <span className="flex min-w-0 items-center gap-2" title={note.body}>
                  <Badge variant="outline">{kindLabel(note.kind)}</Badge>
                  <span className="truncate text-sm">{note.body}</span>
                </span>
              ),
            },
            {
              id: "when", header: t("note.openWhen"), width: "minmax(10rem, 1fr)", lines: 2,
              sortValue: (note) => (note.createdAt ? new Date(note.createdAt) : null),
              cell: (note) => (note.createdAt
                ? <BentoChangeCell at={note.createdAt} actor={note.author ?? undefined} />
                : null),
            },
          ]}
          label={t("note.openTitle")}
          rowActions={[resolveAction, openAction]}
          selectionActions={[resolveAction]}
          onRowOpen={openPage}
        />
      )}
    </>
  )
}
