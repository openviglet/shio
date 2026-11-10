import { IconRobot } from "@tabler/icons-react"
import { EmptyState } from "@/components/empty-state"
import { PageHero } from "@/components/page-hero"
import { Panel } from "@/components/panel"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { ROUTES } from "@/app/routes.const"
import { ReviewTriage } from "@/components/review-triage"
import { ShReviewService } from "@/services/review/review.service"
import type {
  ShReviewDecisionInput,
  ShReviewSession,
} from "@/models/review/review.model"
import { relativeDate } from "@/lib/relative-date"
import { Check, ChevronDown, ChevronRight, RefreshCw, Undo2 } from "lucide-react"
import { useQuery } from "@tanstack/react-query"
import { addressKey } from "@/lib/query-keys"
import { useInvalidate } from "@/hooks/use-catalogue"
import { useCallback, useMemo, useState } from "react"
import { useSearchParams } from "react-router-dom"
import { useTranslation } from "react-i18next"
import type { TFunction } from "i18next"
import { ReviewDiscardDialog } from "@/components/review-discard.dialog"
import { toast } from "@viglet/viglet-design-system"
import { BentoFilterBar, type BentoFilterValue } from "@viglet/viglet-design-system/bento"
import { headlineOf, problemOf, reportProblem } from "@/lib/problem"
import { isCancelled } from "@/lib/read-options"
import { countLocally, fromBarValue, matchesRun, runSites, toBarValue, withCounts, withSiteFacet } from "@/lib/filter-bar"
// SH1024 — the queue's filters live in its URL, like every other list's (SH1023).
import { useListState } from "@/hooks/use-list-state"
import { SavedViews } from "@/components/saved-views"

const service = new ShReviewService()

/**
 * SH1024 — the review queue's filters, in its URL: `q` over what a curator knows a run by, `site`
 * over the sites it touched. Applied to the runs already read, as the trash's are: the queue is one
 * bounded read, and a run is not a post, so the post vocabulary's server filters do not reach it.
 */
const REVIEW_LIST = { q: "", site: "", show: "" }

/**
 * SH1416 — a run with no declared intent is titled by what it did ("4 new pages in quilhara").
 *
 * The run's short id led the row before this, followed by "no intent declared": eight hex digits a
 * curator cannot act on, above a note about an absence. The counts the listing already carries say
 * what the run was, which is the question the title answers; the id survives as the row's tooltip.
 */
const SUMMARY_ORDER = ["CREATE", "UPDATE", "MOVE", "PUBLISH", "UNPUBLISH", "DELETE", "RESTORE"]
const rank = (action: string) => { const at = SUMMARY_ORDER.indexOf(action); return at < 0 ? SUMMARY_ORDER.length : at }

function runSummary(run: ShReviewSession, t: TFunction): string {
  // SH1425 — pages by their net change, not audit rows: four pages created and two fixed twice is
  // "4 new pages", not "4 new, 4 edited".
  const parts = Object.entries(run.byPage ?? run.byAction).sort(([a], [b]) => rank(a) - rank(b)).map(([action, count]) =>
    t(`review.summary${action.charAt(0)}${action.slice(1).toLowerCase()}`,
      { count, defaultValue: t("review.summaryOther", { count }) }))
  const what = parts.length > 0 ? parts.join(", ") : t("review.summaryOther", { count: run.changes })
  const sites = Object.keys(run.bySite ?? {})
  return sites.length > 0 ? t("review.summaryIn", { what, sites: sites.join(", ") }) : what
}

/** The changes a set of runs would discard, by kind — what the discard dialog states (SH1416). */
function kindsOf(runs: ShReviewSession[]): Record<string, number> {
  const counts: Record<string, number> = {}
  for (const run of runs) {
    for (const [action, count] of Object.entries(run.byPage ?? run.byAction)) counts[action] = (counts[action] ?? 0) + count
  }
  return counts
}

/**
 * The review queue (SH109) — the human's side of the agent-native loop.
 *
 * Under the product's orientation this is meant to become the curator's primary workflow:
 * the agent writes drafts (SH107), and this is where a person reads what one run did and
 * either publishes it or puts it back. The diff is the SH14 dialog, unchanged — a second
 * diff would be a second thing to keep right.
 */
export default function ReviewListPage() {
  const { t, i18n } = useTranslation()
  /*
   * SH1158 — which runs are expanded, and nothing else.
   *
   * This was a `Record<string, ShReviewDetail | null>` that four call sites wrote to: the expander's
   * click, the deep link, a triage and a whole-run verb. `null` meant "asked, not answered" — and, on
   * a failed read, "asked, never answering", which drew the loading line for ever. The detail is now
   * read by the row that renders it, so this holds only the curator's own open/closed choice.
   */
  const [open, setOpen] = useState<Record<string, true>>({})
  const [busy, setBusy] = useState<string | null>(null)
  const list = useListState(REVIEW_LIST)
  const { q, site, show } = list.value
  /*
   * SH1197 — which runs the curator has ticked, so one decision covers several. A ticked run the
   * filter no longer lists is left out of the decision (see `ticked` below).
   */
  const [picked, setPicked] = useState<Record<string, true>>({})
  /** SH1416 — the runs the discard dialog is asking about; null while it is closed. */
  const [discarding, setDiscarding] = useState<string[] | null>(null)

  /*
   * SH1024 — the design system's bar, as on the browser, search and trash. Its value is the URL's
   * and both are memoised, because the bar debounces typing in an effect keyed on them. The site
   * facet offers only sites a listed run touched, and only when there are two.
   */
  const barValue = useMemo(() => toBarValue({ q, site }), [q, site])
  const patchList = list.patch
  const onBarChange = useCallback((next: BentoFilterValue) => {
    const { q: nextQ, site: nextSite } = fromBarValue(next)
    patchList({ q: nextQ, site: nextSite })
  }, [patchList])
  /*
   * SH1157 — the review queue's own list, cancelled when the page unmounts.
   *
   * This read is expensive: `sessions()` scans 2000 audit rows and resolves every post they touched,
   * which is why `pending()` exists as a separate cheap read for the nav badge. So it is the one on
   * this list where leaving the page mid-read saves the most server work, not just a stale render.
   *
   * `catch(() => setSessions([]))` also made "the queue could not be read" and "nothing is waiting for
   * you" the same screen — and on a review queue the second is the one a curator stops checking.
   */
  const read = useQuery<ShReviewSession[]>({
    queryKey: addressKey("summary:review"),
    queryFn: ({ signal }) => service.sessions(50, { signal }),
  })
  const sessions = read.data ?? []
  const loading = read.isFetching
  /*
   * SH1158 — the half SH1157 did not deliver, and the reason its assertion passed anyway.
   *
   * That task removed `catch(() => setSessions([]))` and left the screen it made: `read.data ?? []` is
   * `[]` on a failure too, so `sessions.length === 0` still drew "no agent runs yet" for a queue that
   * could not be read — the one message on this page a curator stops coming back to. Its assertion
   * matched the *mechanism* (that `catch` being absent) rather than the property, which is the same
   * mistake SH1028's guard made and the reason this one is written against what the page renders.
   *
   * A cancelled read is a curator who left, not a queue that failed, so it must not raise the flag.
   */
  const queueFailure = read.isError && !isCancelled(read.error) ? read.error : null
  const invalidate = useInvalidate()

  /** The curator asked, so the refetch is a response to them (SH701) — `isFetching` shows it. */
  const load = useCallback(() => { void invalidate("summary:review") }, [invalidate])

  // SH1024 — each site counts the runs `q` leaves that touched it, which is what choosing it lists.
  const facets = useMemo(() => withCounts(withSiteFacet([], runSites(sessions), t("content.filterSite")),
    { site: countLocally(sessions.filter((run) => matchesRun(run, q, "")), (run) => Object.keys(run.bySite ?? {})) }, barValue),
  [sessions, q, barValue, t])
  // SH1197 — "Waiting" is the default view, because a resolved run is history, not work.
  const shown = useMemo(() => sessions.filter((run) => matchesRun(run, q, site) && (show === "all" || run.pending > 0)),
    [sessions, q, site, show])
  const ticked = shown.filter((run) => run.session in picked).map((run) => run.session)
  const waiting = sessions.filter((run) => run.pending > 0).length
  const pick = (session: string, on: boolean) => setPicked((prev) => {
    const next = { ...prev }
    if (on) next[session] = true
    else delete next[session]
    return next
  })


  /** SH1158 — opening a row no longer reads anything; the row it opens does that for itself. */
  const toggle = (session: string) => {
    setOpen((prev) => {
      if (!(session in prev)) return { ...prev, [session]: true }
      const next = { ...prev }
      delete next[session]
      return next
    })
  }

  /**
   * SH999, rewritten by SH1158 — arriving from the Activity page's agent chip, with one run named.
   *
   * <p>All this does now is expand the row. The detail is read where it is rendered, so the deep link
   * makes no request of its own, cannot fail on its own, and does not need the read-then-set dance
   * SH1075 paid this page off for. It still waits for a run the list actually holds: a link to a run
   * that has been approved away lands on the queue rather than on an empty expander, which is the
   * same fact the page states by not listing it.</p>
   *
   * <p>And it expands once per named run, during render, so a curator who closes the row is not
   * argued with — the `open in` guard it replaced re-opened it the next time the list settled. The
   * run comes from the router rather than from `window.location.search`, which is the same URL read
   * the rest of this page's state already does through `useListState`.</p>
   */
  const [searchParams] = useSearchParams()
  const deepLink = searchParams.get("session")
  const linked = deepLink && sessions.some((each) => each.session === deepLink) ? deepLink : null
  const [expandedLink, setExpandedLink] = useState<string | null>(null)
  if (linked && expandedLink !== linked) {
    setExpandedLink(linked)
    setOpen((prev) => ({ ...prev, [linked]: true }))
  }

  /**
   * SH1001 — carry out a per-change triage, then re-read the run.
   *
   * The same refresh the whole-run verbs do, and for the same reason: a decision changes what the
   * badge counts and what the row below it says, and a page that showed the old answer after an
   * action is the one number a curator learns to distrust (SH207).
   */
  const triage = async (session: string, decisions: ShReviewDecisionInput[]) => {
    setBusy(session)
    try {
      const outcome = await service.decide(session, decisions)
      toast.success(t("review.decided", { count: outcome.changed }))
      for (const skipped of outcome.skipped) {
        toast.warning(`${skipped.postId}: ${skipped.reason}`)
      }
      // SH1158 — the run's detail is a cached read now, so a decision says it is stale rather than
      // making the same request a second way. `load()` does the same for the list above it.
      await invalidate(`summary:reviewRun|${session}`)
      load()
    } catch (error) {
      reportProblem(error, t("common.error"))
    } finally {
      setBusy(null)
    }
  }

  const act = async (session: string, action: "approve" | "revert") => {
    setBusy(session)
    try {
      const outcome = action === "approve"
        ? await service.approve(session)
        : await service.revert(session)
      toast.success(t(`review.${action}d`, { count: outcome.changed }))
      // Every skipped post carries its own reason, and each reason needs a different next
      // step — so they are surfaced individually rather than counted.
      for (const skipped of outcome.skipped) {
        toast.warning(`${skipped.postId}: ${skipped.reason}`)
      }
      await invalidate(`summary:reviewRun|${session}`)
      load()
      // SH207 — the navigation badge counts what this just changed, and a badge that disagrees
      // with the page it opens is the one number a curator learns to distrust.
    } catch (error) {
      reportProblem(error, t("common.error"))
    } finally {
      setBusy(null)
    }
  }

  /** SH1197 — one decision over the ticked runs, reported once rather than a toast per run. */
  const actMany = async (sessionsToAct: string[], action: "approve" | "revert") => {
    setBusy("*")
    let changed = 0
    try {
      for (const session of sessionsToAct) {
        const outcome = action === "approve" ? await service.approve(session) : await service.revert(session)
        changed += outcome.changed
        for (const skipped of outcome.skipped) toast.warning(`${skipped.postId}: ${skipped.reason}`)
        await invalidate(`summary:reviewRun|${session}`)
      }
      toast.success(t(`review.${action}d`, { count: changed }))
      setPicked({})
      load()
    } catch (error) {
      reportProblem(error, t("common.error"))
    } finally {
      setBusy(null)
    }
  }

  return (
    <>
      {/*
        SH906 — the curator's approval surface, and the one §LV.9 says must not be the last thing
        that still looks old. It is also the shape that section predicted the package covers least
        well: a hero over a list of expandable sessions, which is not a mosaic and should not be
        made into one. `Panel` rather than `AdaptiveSectionCard` because the panel has no
        header — the hero above already names the page, and a header-less section card renders a
        console card in both eras (SH902).
      */}
      <PageHero grammar="work"
        title={t("review.title")}
        eyebrow={t("admin.title")}
        subtitle={t("review.description")}
        urlBase={ROUTES.HOME}
        backLabel={t("home.title")}
        actions={
          <Button onClick={load} size="sm" variant="outline" disabled={loading}>
            <RefreshCw className={`mr-2 h-4 w-4 ${loading ? "animate-spin" : ""}`} />
            {t("common.refresh")}
          </Button>
        }
      />
      <div className="space-y-3">
        <BentoFilterBar
          value={barValue}
          onChange={onBarChange}
          facets={facets}
          views={<SavedViews list="review" />}
          queryLabel={t("review.filterRuns")}
        />
        {/* SH1197 — what is waiting first; everything the agent did is one step away. */}
        <div role="tablist" aria-label={t("review.showLabel")} className="flex gap-1">
          <Button role="tab" aria-selected={show === "all" ? "false" : "true"} size="sm" variant={show === "all" ? "ghost" : "secondary"}
            onClick={() => list.patch({ show: "" })}>
            {t("review.showWaiting", { count: waiting })}
          </Button>
          <Button role="tab" aria-selected={show === "all" ? "true" : "false"} size="sm" variant={show === "all" ? "secondary" : "ghost"}
            onClick={() => list.patch({ show: "all" })}>
            {t("review.showAll")}
          </Button>
        </div>
        {ticked.length > 0 && (
          <div className="flex items-center gap-3 rounded-lg border border-primary/40 bg-primary/5 px-4 py-2 text-sm">
            <span className="font-medium">{t("review.selectedRuns", { count: ticked.length })}</span>
            <span className="flex-1" />
            <Button size="sm" variant="outline" disabled={busy !== null} onClick={() => setDiscarding(ticked)}>
              <Undo2 className="mr-1 h-3.5 w-3.5" /> {t("review.discard")}
            </Button>
            <Button size="sm" disabled={busy !== null} onClick={() => void actMany(ticked, "approve")}>
              <Check className="mr-1 h-3.5 w-3.5" /> {t("review.approveSelected")}
            </Button>
          </div>
        )}
        {/*
          Three facts, three screens (SH1158). A queue that could not be read is not an empty queue,
          and an empty queue is not a filter that matched nothing — the middle one used to speak for
          the first, and it is the one that says there is nothing to come back for.
        */}
        {queueFailure ? (
          <EmptyState
            icon={IconRobot}
            title={t("review.readFailedTitle")}
            description={headlineOf(problemOf(queueFailure), t("review.readFailedHint"))}
            action={
              <Button onClick={load} size="sm" variant="outline" disabled={loading}>
                <RefreshCw className={`mr-2 h-4 w-4 ${loading ? "animate-spin" : ""}`} />
                {t("common.refresh")}
              </Button>
            }
          />
        ) : sessions.length === 0 ? (
          <EmptyState
            icon={IconRobot}
            title={t("review.blankTitle")}
            description={t("review.blankDescription")}
          />
        ) : show !== "all" && waiting === 0 && !q && !site ? (
          /*
           * SH1197 — nothing waiting is its own fact, not a filter that matched nothing: every run the
           * agent made has been decided. It says so and offers the history, one click away.
           */
          <EmptyState
            icon={IconRobot}
            title={t("review.nothingWaitingTitle")}
            description={t("review.nothingWaitingHint", { count: sessions.length })}
            action={
              <Button onClick={() => list.patch({ show: "all" })} size="sm" variant="outline">
                {t("review.showAll")}
              </Button>
            }
          />
        ) : shown.length === 0 ? (
          <EmptyState
            icon={IconRobot}
            title={t("review.noMatchTitle")}
            description={t("review.noMatchHint")}
          />
        ) : (
          <Panel contentClassName="p-0 divide-y">
              {shown.map((session) => {
                const expanded = session.session in open
                return (
                  <div key={session.session}>
                    <div className="flex flex-wrap items-center gap-3 px-4 py-3" title={t("review.runId", { run: session.session })}>
                      <Checkbox
                        aria-label={t("review.selectRun", { run: session.intent ?? runSummary(session, t) })}
                        checked={session.session in picked}
                        onCheckedChange={(value: boolean | "indeterminate") => pick(session.session, value === true)}
                      />
                      <button
                        type="button"
                        className="flex flex-1 items-center gap-3 text-left min-w-0"
                        onClick={() => toggle(session.session)}
                      >
                        {expanded
                          ? <ChevronDown className="h-4 w-4 shrink-0 text-muted-foreground" />
                          : <ChevronRight className="h-4 w-4 shrink-0 text-muted-foreground" />}
                        <div className="min-w-0">
                          {/*
                            SH1000 — the premise leads, because it is what decides whether the list
                            underneath is the right list. The count and the actor move below it: a
                            reviewer who knows what the run was for reads eleven changes as one
                            decision, and without it they read the diff to find out why.
                          */}
                          {/*
                            SH1416 — with no intent, the run is titled by what it did. The short id
                            that led here (SH1197) told a curator nothing; it is the row's tooltip.
                          */}
                          <div className="font-medium truncate">
                            {session.intent ?? runSummary(session, t)}
                          </div>
                          <div className="text-xs text-muted-foreground truncate">
                            {t("review.byActor", { actor: session.actor ?? t("review.unknownActor") })}
                            {session.agentName && ` ${t("review.viaAgent", { agent: session.agentName })}`}
                            {session.lastSeen &&
                              ` · ${relativeDate(session.lastSeen, i18n.language)}`}
                            {session.pending > 0 && ` · ${t("review.pending", { count: session.pending })}`}
                          </div>
                        </div>
                      </button>
                      <div className="flex gap-2 shrink-0">
                        <Button
                          size="sm"
                          variant="ghost"
                          disabled={busy === session.session}
                          onClick={() => setDiscarding([session.session])}
                        >
                          <Undo2 className="mr-1 h-3.5 w-3.5" /> {t("review.discardAll")}
                        </Button>
                        {/* SH1197 — per row a secondary: the page's one primary is the selection bar's. */}
                        <Button
                          size="sm"
                          variant="outline"
                          disabled={busy === session.session || session.pending === 0}
                          onClick={() => act(session.session, "approve")}
                        >
                          <Check className="mr-1 h-3.5 w-3.5" /> {t("review.publishAll", { count: session.pending })}
                        </Button>
                      </div>
                    </div>

                    {expanded && (
                      <div className="bg-muted/30 px-4 py-2">
                        <ReviewRunDetail
                          session={session.session}
                          busy={busy === session.session}
                          onDecide={(decisions) => triage(session.session, decisions)}
                        />
                      </div>
                    )}
                  </div>
                )
              })}
          </Panel>
        )}
        {shown.some((run) => !run.intent) && (
          <p className="px-1 text-xs text-muted-foreground">{t("review.intentHint")}</p>
        )}
      </div>
      {/*
        SH1416 — the whole-run discard says what it will do before it does it. The server records its
        own reason for this verb, so the dialog asks for none.
      */}
      <ReviewDiscardDialog
        open={discarding !== null}
        byAction={kindsOf(sessions.filter((run) => discarding?.includes(run.session)))}
        askReason={false}
        busy={busy !== null}
        onCancel={() => setDiscarding(null)}
        onConfirm={() => {
          const runs = discarding ?? []
          setDiscarding(null)
          void (runs.length === 1 ? act(runs[0], "revert") : actMany(runs, "revert"))
        }}
      />
    </>
  )
}

/**
 * SH1158 — one run's changes, read where they are rendered.
 *
 * <h2>Why this is a component and not four `setOpen` calls</h2>
 *
 * <p>The list used to hold every expanded run's detail in a map, written from the expander's click,
 * the deep link, a triage and a whole-run verb. None of the four could be cancelled, so closing a row
 * or leaving the page left the read running; and `null` in that map meant both "asked, not answered"
 * and — on a read that failed — "asked, never answering", which drew `common.loading` for ever with
 * nothing to press. A query per expanded run is the smallest thing that fixes all of it: the key is
 * the run, so the signal is aborted when the row closes, two surfaces wanting the same run make one
 * request, and a decision re-reads it by saying the key is stale.</p>
 *
 * <p>The failure is its own state rather than an absent detail, which is the distinction the map could
 * not carry. A cancelled read is a row the curator closed, so `isCancelled` keeps a close out of the
 * failure path — otherwise the fix would be a reliable error message in place of a rare hang.</p>
 */
function ReviewRunDetail({ session, busy, onDecide }: Readonly<{
  session: string
  busy: boolean
  onDecide: (decisions: ShReviewDecisionInput[]) => void
}>) {
  const { t } = useTranslation()
  const read = useQuery({
    queryKey: addressKey(`summary:reviewRun|${session}`),
    queryFn: ({ signal }) => service.detail(session, { signal }),
  })
  const failure = read.isError && !isCancelled(read.error) ? read.error : null
  const detail = read.data

  if (failure) {
    return (
      <p className="py-2 text-sm text-destructive">
        {headlineOf(problemOf(failure), t("review.runReadFailed"))}
      </p>
    )
  }
  if (!detail) {
    return <p className="py-2 text-sm text-muted-foreground">{t("common.loading")}</p>
  }
  return (
    <>
      {/*
        SH1000 — what this run changed the LOOK of, which the action column cannot say: an UPDATE
        that moved a headline and one that moved the whole page read identically there. Shown only
        when something was comparable, because "0 of 0 pages" is a sentence about the instrument.
      */}
      {detail.appearance && detail.appearance.checked > 0 && (
        <p className="py-2 text-xs text-muted-foreground">
          {detail.appearance.moved.length === 0
            ? t("review.appearanceSteady", { checked: detail.appearance.checked })
            : t("review.appearanceMoved", {
                checked: detail.appearance.checked,
                pages: detail.appearance.moved.join(", "),
              })}
          {detail.appearance.note ? ` — ${detail.appearance.note}` : ""}
        </p>
      )}
      {/*
        SH1001 — the decision moves from the run to the change. The two buttons above still decide
        the whole run, because "all of it" is a real answer and the common one; this is the answer
        for a run where one page is wrong, which used to force a curator to accept all of it or undo
        all of it.
      */}
      {/* SH1425 — one row per page, the unit a curator decides; the audit rows ride on each. */}
      <ReviewTriage
        entries={detail.pages ?? detail.entries}
        decided={new Map((detail.decisions ?? []).map((each) => [each.postId, each.decision]))}
        busy={busy}
        onDecide={onDecide}
      />
    </>
  )
}
