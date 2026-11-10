import { useState } from "react"
import { useTranslation } from "react-i18next"
import { Undo2 } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"

/**
 * SH1416 — what "discard" does, said before it is done.
 *
 * <p>The queue used to offer "Revert" on a run and "Reject" on a change, two words for one server
 * act whose consequences lived only in `ShReviewService.revertOne`: a page the agent created goes to
 * the trash, a page it edited goes back to the version before. A curator who could not predict that
 * either guessed or did not press. This dialog states it per kind of change, from the counts the
 * caller already holds, so nothing is read to open it. An edit goes back to the version it had
 * before the run: the PUBLISHED row, or, for a page never published, the draft the run's first
 * write replaced, which the server keeps for exactly this (SH1418).</p>
 *
 * <p>`askReason` is the per-change path, where the server refuses a rejection with nothing written
 * down. The whole-run verb records its own reason server-side and takes none.</p>
 */
export function ReviewDiscardDialog({
  open,
  byAction,
  askReason,
  busy,
  onCancel,
  onConfirm,
}: Readonly<{
  open: boolean
  /** Audit action → how many of the changes being discarded are of that kind. */
  byAction: Record<string, number>
  askReason: boolean
  busy: boolean
  onCancel: () => void
  onConfirm: (note: string) => void
}>) {
  const { t } = useTranslation()
  const [note, setNote] = useState("")
  const total = Object.values(byAction).reduce((sum, each) => sum + each, 0)
  const created = byAction.CREATE ?? 0
  const edited = (byAction.UPDATE ?? 0) + (byAction.MOVE ?? 0)
  const other = total - created - edited
  const ready = !askReason || note.trim().length > 0

  return (
    <Dialog open={open} onOpenChange={(next) => { if (!next) { setNote(""); onCancel() } }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("review.discardTitle", { count: total })}</DialogTitle>
          <DialogDescription>{t("review.discardLead")}</DialogDescription>
        </DialogHeader>
        <ul className="list-disc space-y-1 pl-5 text-sm">
          {created > 0 && <li>{t("review.discardCreated", { count: created })}</li>}
          {edited > 0 && <li>{t("review.discardEdited", { count: edited })}</li>}
          {other > 0 && <li>{t("review.discardOther", { count: other })}</li>}
        </ul>
        {askReason && (
          <div className="space-y-1">
            <label htmlFor="review-discard-note" className="text-sm font-medium">{t("review.discardReason")}</label>
            <Textarea
              id="review-discard-note"
              value={note}
              onChange={(event) => setNote(event.target.value)}
              placeholder={t("review.notePlaceholder")}
            />
            <p className="text-xs text-muted-foreground">{t("review.rejectNeedsNote")}</p>
          </div>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={() => { setNote(""); onCancel() }}>{t("common.cancel")}</Button>
          <Button
            variant="destructive"
            disabled={busy || !ready}
            onClick={() => { const said = note.trim(); setNote(""); onConfirm(said) }}
          >
            <Undo2 className="mr-1 h-3.5 w-3.5" /> {t("review.discardConfirm", { count: total })}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
