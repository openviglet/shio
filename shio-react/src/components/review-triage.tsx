import { useCallback, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import { Check, Clock, Eye, GitCompare, Undo2 } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import type { ShReviewDecisionInput, ShReviewPage } from "@/models/review/review.model"
import { ReviewEntryDiff } from "@/components/review-entry-diff"
import { ReviewDiscardDialog } from "@/components/review-discard.dialog"
import { ReviewPageThumbnail } from "@/components/review-page-thumbnail"
import { draftPreviewUrl } from "@/lib/draft-preview-url"

/** SH1416 — what the curator calls each kind of change, rather than the audit row's verb. */
const KIND: Record<string, string> = {
  CREATE: "review.kindCreate",
  UPDATE: "review.kindUpdate",
  DELETE: "review.kindDelete",
  PUBLISH: "review.kindPublish",
  UNPUBLISH: "review.kindUnpublish",
  MOVE: "review.kindMove",
  RESTORE: "review.kindRestore",
}

/** SH1416 — a standing decision, in words: `APPROVE` on a row is a sentence about the API. */
const DECIDED: Record<string, string> = {
  APPROVE: "review.decidedApprove",
  REJECT: "review.decidedReject",
  HOLD: "review.decidedHold",
}

/**
 * SH1001 — deciding a run one change at a time; SH1416 — one place per decision.
 *
 * <h2>Why this is a component and not more of the page</h2>
 *
 * <p>The queue page renders a list of runs; this is a triage tool over one run's changes, with a
 * selection, a keyboard and a refusal of its own. Folding it into the page would put four pieces of
 * state that belong to one expanded row into a component that owns the whole list.</p>
 *
 * <h2>Where each decision lives (SH1416)</h2>
 *
 * <p>Each page carries its own Publish and Discard, so deciding one page is one press on that page.
 * The toolbar that decides several exists only while some are ticked: it used to sit above every
 * expanded run with its own Approve beside the run's Approve, two primaries saying the same word over
 * different scopes. Discard always goes through {@link ReviewDiscardDialog}, which says what it will
 * do and takes the reason the server requires.</p>
 *
 * <h2>The affordances kept from SH1001</h2>
 *
 * <p><b>Range select.</b> Shift-click takes the range from the last box touched. <b>A required
 * reason on discard.</b> The dialog's confirm stays disabled until there is one, and `reject` refuses
 * an empty one too, so the rule is not a property of one button. <b>Keyboard triage.</b> `j`/`k`,
 * `x`, `a`/`h`; `r` opens the dialog rather than discarding, because the one destructive key on this
 * list must not be a keystroke away from acting.</p>
 */
export function ReviewTriage({
  entries,
  decided,
  busy,
  onDecide,
}: {
  /** SH1425 — one per page; a run that wrote a page three times lists it once. */
  entries: ShReviewPage[]
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
  /** SH1416 — the posts the discard dialog is asking about; null while it is closed. */
  const [discarding, setDiscarding] = useState<string[] | null>(null)
  const anchor = useRef<number | null>(null)

  const ids = [...new Set(entries.map((entry) => entry.postId).filter((id): id is string => !!id))]

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
    async (decision: "APPROVE" | "HOLD", posts: string[]) => {
      if (posts.length === 0) return
      await onDecide(posts.map((postId) => ({ postId, decision })))
      setSelected(new Set())
    },
    [onDecide],
  )

  /** The server refuses a rejection with nothing written down, and so does this. */
  const reject = useCallback(
    async (posts: string[], note: string) => {
      if (posts.length === 0 || !note.trim()) return
      await onDecide(posts.map((postId) => ({ postId, decision: "REJECT", note: note.trim() })))
      setSelected(new Set())
      setDiscarding(null)
    },
    [onDecide],
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
      a: () => void decide("APPROVE", subjects()),
      h: () => void decide("HOLD", subjects()),
      // Opens the dialog rather than discarding: the one destructive key on this list must not be a
      // keystroke away from acting.
      r: () => { const posts = subjects(); if (posts.length > 0) setDiscarding(posts) },
    }
    const act = keys[event.key.toLowerCase()]
    if (!act) return
    event.preventDefault()
    act()
  }

  /** One entry per post — a run that wrote a page four times is discarding one page. */
  const kindsOf = (posts: string[]) => {
    const counts: Record<string, number> = {}
    for (const postId of posts) {
      const entry = entries.find((each) => each.postId === postId)
      if (entry) counts[entry.action] = (counts[entry.action] ?? 0) + 1
    }
    return counts
  }

  const ticked = ids.filter((id) => selected.has(id))

  return (
    // eslint-disable-next-line jsx-a11y/no-static-element-interactions, jsx-a11y/no-noninteractive-tabindex -- a focus region for the triage shortcut keys, not a control; its buttons are the controls (SH1001)
    <div className="space-y-2 py-2" tabIndex={0} onKeyDown={onKeyDown}>
      {ticked.length > 0 && (
        <div className="flex flex-wrap items-center gap-2 rounded-md border border-primary/40 bg-primary/5 px-3 py-2 text-sm">
          <span className="font-medium">{t("review.selectedPages", { count: ticked.length })}</span>
          <span className="hidden text-xs text-muted-foreground md:inline">{t("review.keys")}</span>
          <span className="flex-1" />
          <Button size="sm" variant="ghost" disabled={busy} onClick={() => void decide("HOLD", ticked)}>
            <Clock className="mr-1 h-3.5 w-3.5" /> {t("review.holdCount", { count: ticked.length })}
          </Button>
          <Button size="sm" variant="outline" disabled={busy} onClick={() => setDiscarding(ticked)}>
            <Undo2 className="mr-1 h-3.5 w-3.5" /> {t("review.rejectCount", { count: ticked.length })}
          </Button>
          <Button size="sm" loading={busy} onClick={() => void decide("APPROVE", ticked)}>
            {!busy && <Check className="mr-1 h-3.5 w-3.5" />}
            {t("review.approveCount", { count: ticked.length })}
          </Button>
        </div>
      )}
      <ul className="divide-y">
        {entries.map((entry, index) => {
          const standing = entry.postId ? decided.get(entry.postId) : undefined
          const canPublish = !!entry.postId && entry.draft && standing !== "APPROVE"
          const canDiscard = !!entry.postId && entry.revertable && !entry.trashed && standing !== "REJECT"
          const preview = entry.trashed ? null : draftPreviewUrl(entry.address)
          return (
            <li key={`${entry.postId}-${entry.date}-${index}`} className="py-2 text-sm">
              <div className={`flex flex-wrap items-center gap-3 rounded-md px-1 ${index === cursor ? "bg-muted/60" : ""}`}>
                <Checkbox
                  checked={!!entry.postId && selected.has(entry.postId)}
                  onClick={(event) => toggle(index, (event as React.MouseEvent).shiftKey)}
                  aria-label={entry.title ?? entry.postId ?? ""}
                />
                {/*
                  SH1429 — the page as it will look, before its name: a curator decides on what they
                  see, and an address in code type is a developer's handle on it. The address stays
                  reachable as the title's tooltip.
                */}
                {preview && <ReviewPageThumbnail url={preview} title={entry.title || entry.postId || ""} />}
                <div className="min-w-0 flex-1">
                  <div className="truncate font-medium" title={entry.address}>{entry.title || entry.postId}</div>
                </div>
                {entry.locale && (
                  <Badge variant="secondary" className="text-[10px] uppercase">{entry.locale}</Badge>
                )}
                <Badge variant="outline" className="text-[10px]">
                  {KIND[entry.action] ? t(KIND[entry.action]) : entry.action}
                </Badge>
                {entry.trashed && (
                  <Badge variant="secondary" className="text-[10px]">{t("review.trashed")}</Badge>
                )}
                {standing && (
                  <Badge variant="secondary" className="text-[10px]">
                    {DECIDED[standing] ? t(DECIDED[standing]) : standing}
                  </Badge>
                )}
                <div className="flex shrink-0 items-center gap-1">
                  {preview && (
                    <Button size="sm" variant="ghost" asChild>
                      <a href={preview} target="_blank" rel="noopener noreferrer">
                        <Eye className="mr-1 h-3.5 w-3.5" /> {t("review.preview")}
                      </a>
                    </Button>
                  )}
                  {/*
                    SH988 — a diff for every entry that still exists, not only one with a published twin:
                    a create, the commonest thing an agent writes, compares against nothing and draws as
                    additions. An approved entry has no draft left to compare, so it names the fields its
                    audit row recorded instead (SH989).
                  */}
                  {entry.postId && !entry.trashed && (entry.draft || entry.published)
                    && standing !== "APPROVE" && (
                    <Button size="sm" variant="ghost" aria-expanded={expanded.has(entry.postId) ? "true" : "false"}
                      onClick={() => toggleDiff(entry.postId!)}>
                      <GitCompare className="mr-1 h-3.5 w-3.5" /> {t("review.diff")}
                    </Button>
                  )}
                  {canDiscard && (
                    <Button size="sm" variant="ghost" disabled={busy} onClick={() => setDiscarding([entry.postId!])}>
                      <Undo2 className="mr-1 h-3.5 w-3.5" /> {t("review.discard")}
                    </Button>
                  )}
                  {canPublish && (
                    <Button size="sm" variant="outline" disabled={busy}
                      onClick={() => void decide("APPROVE", [entry.postId!])}>
                      <Check className="mr-1 h-3.5 w-3.5" /> {t("review.publish")}
                    </Button>
                  )}
                </div>
              </div>
              {/* SH1425 — the writes folded into this row, so the audit stays readable from it. */}
              {entry.steps && entry.steps.length > 1 && (
                <p className="mt-1 pl-7 text-xs text-muted-foreground">
                  {t("review.steps", {
                    count: entry.steps.length,
                    steps: entry.steps.map((step) => (KIND[step] ? t(KIND[step]) : step)).join(", "),
                  })}
                </p>
              )}
              {entry.postId && standing === "APPROVE" && entry.fields?.length ? (
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
          )
        })}
      </ul>
      <ReviewDiscardDialog
        open={discarding !== null}
        byAction={kindsOf(discarding ?? [])}
        askReason
        busy={busy}
        onCancel={() => setDiscarding(null)}
        onConfirm={(note) => void reject(discarding ?? [], note)}
      />
    </div>
  )
}
