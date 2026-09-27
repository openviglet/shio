import { useCallback, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import { Check, GitCompare, Pause, Undo2 } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import type { ShReviewDecisionInput, ShReviewEntry } from "@/models/review/review.model"
import { ReviewEntryDiff } from "@/components/review-entry-diff"

/**
 * SH1001 — deciding a run one change at a time.
 *
 * <h2>Why this is a component and not more of the page</h2>
 *
 * <p>The queue page renders a list of runs; this is a triage tool over one run's changes, with a
 * selection, a keyboard and a refusal of its own. Folding it into the page would put four pieces of
 * state that belong to one expanded row into a component that owns the whole list.</p>
 *
 * <h2>The three affordances, and why each is here</h2>
 *
 * <p><b>Range select.</b> A run is often a sweep — eleven pages of the same change — and a curator
 * who agrees with all of them should not click eleven times. Shift-click takes the range from the
 * last box they touched, which is the gesture every file list has taught them.</p>
 *
 * <p><b>A required note on reject.</b> The server refuses a rejection with nothing written down, and
 * the button knows it: a curator should not learn the rule from a refusal. `HOLD` is the decision
 * for "not now" and takes no reason, which is what keeps the requirement from being a tax on
 * parking something.</p>
 *
 * <p><b>Keyboard triage.</b> This is a screen somebody lives in during a long run — `j`/`k` to move,
 * `x` to select, `a`/`h` to decide. `r` focuses the note rather than rejecting, because the one
 * destructive key on this list must not be a keystroke away from acting.</p>
 */
export function ReviewTriage({
  entries,
  decided,
  busy,
  onDecide,
}: {
  entries: ShReviewEntry[]
  /** postId → the standing decision, so a row already decided says so. */
  decided: Map<string, string>
  busy: boolean
  onDecide: (decisions: ShReviewDecisionInput[]) => Promise<void> | void
}) {
  const { t } = useTranslation()
  const [selected, setSelected] = useState<Set<string>>(new Set())
  /** SH988 — the rows whose diff is open under them. */
  const [expanded, setExpanded] = useState<Set<string>>(new Set())
  const toggleDiff = (postId: string) => setExpanded((open) => {
    const next = new Set(open)
    if (next.has(postId)) next.delete(postId)
    else next.add(postId)
    return next
  })
  const [cursor, setCursor] = useState(0)
  const [note, setNote] = useState("")
  const anchor = useRef<number | null>(null)
  const noteBox = useRef<HTMLInputElement>(null)

  const ids = entries.map((entry) => entry.postId).filter((id): id is string => !!id)

  const toggle = useCallback((index: number, range: boolean) => {
    const id = entries[index]?.postId
    if (!id) return
    setSelected((prev) => {
      const next = new Set(prev)
      // Shift-click takes everything between the last box touched and this one, the gesture every
      // file list has already taught a curator. Without an anchor it is an ordinary toggle.
      const from = range && anchor.current !== null ? anchor.current : index
      const [start, end] = from <= index ? [from, index] : [index, from]
      const adding = !next.has(id)
      for (let at = start; at <= end; at++) {
        const each = entries[at]?.postId
        if (!each) continue
        if (adding) next.add(each)
        else next.delete(each)
      }
      return next
    })
    anchor.current = index
  }, [entries])

  /** What a decision applies to: the selection, or the row under the cursor when there is none. */
  const subjects = useCallback(() => {
    if (selected.size > 0) return ids.filter((id) => selected.has(id))
    const here = entries[cursor]?.postId
    return here ? [here] : []
  }, [selected, ids, entries, cursor])

  const decide = useCallback(
    async (decision: ShReviewDecisionInput["decision"]) => {
      const posts = subjects()
      if (posts.length === 0) return
      if (decision === "REJECT" && !note.trim()) {
        noteBox.current?.focus()
        return
      }
      await onDecide(posts.map((postId) => ({ postId, decision, note: note.trim() || undefined })))
      setSelected(new Set())
      setNote("")
    },
    [subjects, note, onDecide],
  )

  const onKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
    // Never while typing the reason: `a` in "the prices are wrong" must not approve the page.
    if (event.target instanceof HTMLInputElement || event.target instanceof HTMLTextAreaElement) {
      return
    }
    const keys: Record<string, () => void> = {
      j: () => setCursor((at) => Math.min(at + 1, entries.length - 1)),
      k: () => setCursor((at) => Math.max(at - 1, 0)),
      x: () => toggle(cursor, false),
      a: () => void decide("APPROVE"),
      h: () => void decide("HOLD"),
      // Focuses the box rather than rejecting: the one destructive key on this list must not be a
      // keystroke away from acting.
      r: () => noteBox.current?.focus(),
    }
    const act = keys[event.key.toLowerCase()]
    if (!act) return
    event.preventDefault()
    act()
  }

  const count = selected.size > 0 ? selected.size : 1

  return (
    <div className="space-y-2 py-2" tabIndex={0} onKeyDown={onKeyDown}>
      <div className="flex flex-wrap items-center gap-2">
        <Input
          ref={noteBox}
          value={note}
          onChange={(event) => setNote(event.target.value)}
          placeholder={t("review.notePlaceholder")}
          className="h-8 max-w-xs"
        />
        <Button size="sm" loading={busy} onClick={() => void decide("APPROVE")}>
          {!busy && <Check className="mr-1 h-3.5 w-3.5" />}
          {t("review.approveCount", { count })}
        </Button>
        <Button size="sm" variant="outline" disabled={busy} onClick={() => void decide("HOLD")}>
          <Pause className="mr-1 h-3.5 w-3.5" /> {t("review.holdCount", { count })}
        </Button>
        <Button
          size="sm"
          variant="outline"
          // The server refuses a rejection with no reason, and the button knows it: a curator
          // should not learn the rule from a refusal.
          disabled={busy || !note.trim()}
          title={note.trim() ? undefined : t("review.rejectNeedsNote")}
          onClick={() => void decide("REJECT")}
        >
          <Undo2 className="mr-1 h-3.5 w-3.5" /> {t("review.rejectCount", { count })}
        </Button>
        <span className="text-xs text-muted-foreground">{t("review.keys")}</span>
      </div>
      <ul className="divide-y">
        {entries.map((entry, index) => (
          <li key={`${entry.postId}-${entry.date}-${index}`} className="py-2 text-sm">
          <div className={`flex items-center gap-3 ${index === cursor ? "bg-muted/60" : ""}`}>
            <Checkbox
              checked={!!entry.postId && selected.has(entry.postId)}
              onClick={(event) => toggle(index, (event as React.MouseEvent).shiftKey)}
              aria-label={entry.title ?? entry.postId ?? ""}
            />
            <span className="flex-1 min-w-0 truncate">
              {entry.title || entry.postId}
              {entry.address && (
                <code className="ml-2 text-xs text-muted-foreground">{entry.address}</code>
              )}
            </span>
            {entry.draft && (
              <Badge variant="secondary" className="text-[10px]">{t("review.draft")}</Badge>
            )}
            {entry.published && (
              <Badge variant="secondary" className="text-[10px]">{t("review.published")}</Badge>
            )}
            {entry.trashed && (
              <Badge variant="secondary" className="text-[10px]">{t("review.trashed")}</Badge>
            )}
            {entry.postId && decided.has(entry.postId) && (
              <span className="text-xs vg-accent-text">{decided.get(entry.postId)}</span>
            )}
            {/*
              SH988 — a diff for every entry that still exists, not only one with a published twin:
              a create, the commonest thing an agent writes, compares against nothing and draws as
              additions. An approved entry has no draft left to compare, so it names the fields its
              audit row recorded instead (SH989).
            */}
            {entry.postId && !entry.trashed && (entry.draft || entry.published)
              && decided.get(entry.postId) !== "APPROVE" && (
              <Button size="sm" variant="ghost" aria-expanded={expanded.has(entry.postId) ? "true" : "false"}
                onClick={() => toggleDiff(entry.postId!)}>
                <GitCompare className="mr-1 h-3.5 w-3.5" /> {t("review.diff")}
              </Button>
            )}
          </div>
          {entry.postId && decided.get(entry.postId) === "APPROVE" && entry.fields?.length ? (
            <p className="mt-1 pl-7 text-xs text-muted-foreground">
              {t("review.changedFields", { fields: entry.fields.join(", ") })}
            </p>
          ) : null}
          {entry.postId && expanded.has(entry.postId) && (
            <div className="mt-2 pl-7">
              <ReviewEntryDiff postId={entry.postId} />
            </div>
          )}
          </li>
        ))}
      </ul>
    </div>
  )
}
