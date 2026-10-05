import { IconMessage } from "@tabler/icons-react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { Link } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { addressKey } from "@/lib/query-keys"
import { EmptyState } from "@/components/empty-state"
import { PageHero } from "@/components/page-hero"
import { Panel } from "@/components/panel"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
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
  const queryClient = useQueryClient()

  const read = useQuery<ShOpenNote[]>({
    queryKey: addressKey("summary:notes"),
    queryFn: ({ signal }) => service.open({ signal }),
  })
  const notes = read.data ?? []

  const when = (at?: string | null) =>
    at ? new Date(at).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" }) : ""
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
        <Panel contentClassName="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("note.openPage")}</TableHead>
                <TableHead>{t("note.openNote")}</TableHead>
                <TableHead className="w-48">{t("note.openWhen")}</TableHead>
                <TableHead className="w-28" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {notes.map((note) => (
                <TableRow key={note.id}>
                  <TableCell className="align-top">
                    {note.address?.startsWith("post:") ? (
                      <Link className="font-medium hover:underline" to={`${ROUTES.CONSOLE}/content/post/${note.objectId}`}>
                        {note.address}
                      </Link>
                    ) : (
                      <span className="font-medium">{note.address ?? t("note.openGone")}</span>
                    )}
                    {note.field && <div className="text-xs text-muted-foreground">{note.field}</div>}
                  </TableCell>
                  <TableCell className="align-top">
                    <Badge variant="outline" className="mb-1">{kindLabel(note.kind)}</Badge>
                    <p className="whitespace-pre-wrap text-sm">{note.body}</p>
                  </TableCell>
                  <TableCell className="align-top">
                    {note.createdAt && <time dateTime={note.createdAt}>{when(note.createdAt)}</time>}
                    {note.author && <div className="text-xs text-muted-foreground">{note.author}</div>}
                  </TableCell>
                  <TableCell className="align-top text-right">
                    <Button variant="outline" size="sm" onClick={() => { void resolve(note) }}>
                      {t("note.resolve")}
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Panel>
      )}
    </>
  )
}
