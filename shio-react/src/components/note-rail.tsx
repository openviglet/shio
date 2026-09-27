import { useQuery } from "@tanstack/react-query"
import { useCallback, useState } from "react"
import { useTranslation } from "react-i18next"
import { Check, MessageSquare, Undo2 } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { ShNoteService } from "@/services/note/note.service"
import type { ShNote } from "@/models/note/note.model"
import { reportProblem } from "@/lib/problem"
import { addressKey } from "@/lib/query-keys"
import { useInvalidate } from "@/hooks/use-catalogue"

const service = new ShNoteService()

/**
 * SH1003 — the corrections on this page, and the box that adds one.
 *
 * <h2>Why a rail rather than a dialog</h2>
 *
 * <p>A note is written <em>while</em> reading the page it is about: the curator has just seen the
 * wrong price. A dialog would hide the thing being corrected behind the box for correcting it,
 * which is the one arrangement this feature cannot afford.</p>
 *
 * <h2>Open notes lead; resolved ones are behind a click</h2>
 *
 * <p>The list a curator opens this for is "what is still being asked". Resolved notes are the
 * record of a conversation that finished, and a rail that showed every one of them would grow into
 * a page nobody scrolls — which is how a comment thread stops being read.</p>
 *
 * <h2>The field is carried, not guessed</h2>
 *
 * <p>`field` comes from whoever mounted the rail, so a note left beside a field anchors to it and
 * one left at the top of the page anchors to the page. Inferring it from focus would give a
 * different answer depending on what the curator last clicked.</p>
 */
export function NoteRail({ objectId, field }: { objectId: string; field?: string }) {
  const { t } = useTranslation()
  const [body, setBody] = useState("")
  const [kind, setKind] = useState<"COMMENT" | "REQUEST">("COMMENT")
  const [busy, setBusy] = useState(false)
  const [showResolved, setShowResolved] = useState(false)

  /*
   * SH1152 — one read per object, cancelled when the object changes or the rail unmounts, and the
   * refresh after a write is an invalidation rather than a second call site. `catch(() => setNotes([]))`
   * had made "the server did not answer" and "no notes on this field" the same empty rail.
   */
  const read = useQuery<ShNote[]>({
    queryKey: addressKey(`note:${objectId}`),
    queryFn: ({ signal }) => service.of(objectId, { signal }),
    enabled: Boolean(objectId),
  })
  const notes = read.data ?? []
  const invalidate = useInvalidate()
  const load = useCallback(() => { void invalidate(`note:${objectId}`) }, [invalidate, objectId])

  const add = async () => {
    if (!body.trim()) return
    setBusy(true)
    try {
      await service.add({ objectId, fieldPath: field, kind, body: body.trim() })
      setBody("")
      load()
    } catch (error) {
      reportProblem(error, t("common.error"))
    } finally {
      setBusy(false)
    }
  }

  const resolve = async (id: string, resolved: boolean) => {
    setBusy(true)
    try {
      await service.resolve(id, resolved)
      load()
    } catch (error) {
      reportProblem(error, t("common.error"))
    } finally {
      setBusy(false)
    }
  }

  const open = notes.filter((note) => !note.resolved)
  const done = notes.filter((note) => note.resolved)
  const shown = showResolved ? notes : open

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-2 text-sm font-medium">
        <MessageSquare className="size-4 vg-accent-text" />
        {t("note.title", { count: open.length })}
      </div>

      <ul className="space-y-2">
        {shown.map((note) => (
          <li key={note.id} className="rounded-md border p-2 text-sm">
            <div className="flex items-center gap-2">
              <Badge variant="outline" className="text-[10px]">{note.kind}</Badge>
              {note.fieldPath && (
                <code className="text-xs text-muted-foreground">{note.fieldPath}</code>
              )}
              <span className="text-xs text-muted-foreground">{note.author}</span>
              <Button
                size="sm"
                variant="ghost"
                className="ml-auto"
                disabled={busy}
                onClick={() => void resolve(note.id, !note.resolved)}
              >
                {note.resolved
                  ? <><Undo2 className="mr-1 size-3.5" /> {t("note.reopen")}</>
                  : <><Check className="mr-1 size-3.5" /> {t("note.resolve")}</>}
              </Button>
            </div>
            <p className={note.resolved ? "text-muted-foreground line-through" : ""}>{note.body}</p>
          </li>
        ))}
      </ul>

      {done.length > 0 && (
        <Button size="sm" variant="ghost" onClick={() => setShowResolved(!showResolved)}>
          {showResolved ? t("note.hideResolved") : t("note.showResolved", { count: done.length })}
        </Button>
      )}

      <Textarea
        rows={2}
        value={body}
        onChange={(event) => setBody(event.target.value)}
        placeholder={field ? t("note.placeholderField", { field }) : t("note.placeholder")}
      />
      <div className="flex items-center gap-2">
        {/*
          Two kinds, not three: a reply is what the API calls a note under another one, and the rail
          threads by page rather than by parent — so offering it here would be a control that means
          nothing until a thread exists to reply into.
        */}
        <Button
          size="sm"
          variant={kind === "COMMENT" ? "default" : "outline"}
          onClick={() => setKind("COMMENT")}
        >
          {t("note.kindComment")}
        </Button>
        <Button
          size="sm"
          variant={kind === "REQUEST" ? "default" : "outline"}
          onClick={() => setKind("REQUEST")}
        >
          {t("note.kindRequest")}
        </Button>
        <Button size="sm" className="ml-auto" disabled={!body.trim()} loading={busy} onClick={() => void add()}>
          {t("note.add")}
        </Button>
      </div>
    </div>
  )
}
