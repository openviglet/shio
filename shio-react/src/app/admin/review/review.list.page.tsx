import { IconRobot } from "@tabler/icons-react"
import { EmptyState } from "@/components/empty-state"
import { PageHero } from "@/components/page-hero"
import { Panel } from "@/components/panel"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { ROUTES } from "@/app/routes.const"
import { ReviewTriage } from "@/components/review-triage"
import { ShReviewService } from "@/services/review/review.service"
import type {
  ShReviewDecisionInput,
  ShReviewDetail,
  ShReviewSession,
} from "@/models/review/review.model"
import { formatDistanceToNow } from "date-fns"
import { Bot, Check, ChevronDown, ChevronRight, RefreshCw, Undo2 } from "lucide-react"
import { useQuery } from "@tanstack/react-query"
import { addressKey } from "@/lib/query-keys"
import { useInvalidate } from "@/hooks/use-catalogue"
import { useCallback, useEffect, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "@viglet/viglet-design-system"
import { BentoFilterBar, type BentoFilterValue } from "@viglet/viglet-design-system/bento"
import { reportProblem } from "@/lib/problem"
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
const REVIEW_LIST = { q: "", site: "" }

/** Same action colours the Activity page uses — one action, one colour, wherever it appears. */
const ACTION_BADGE: Record<string, string> = {
  CREATE: "border-blue-300 text-blue-600 dark:border-blue-700 dark:text-blue-400",
  UPDATE: "border-indigo-300 text-indigo-600 dark:border-indigo-700 dark:text-indigo-400",
  PUBLISH: "border-green-300 text-green-600 dark:border-green-700 dark:text-green-400",
  UNPUBLISH: "border-amber-300 text-amber-600 dark:border-amber-700 dark:text-amber-400",
  DELETE: "border-red-300 text-red-600 dark:border-red-700 dark:text-red-400",
  RESTORE: "border-teal-300 text-teal-600 dark:border-teal-700 dark:text-teal-400",
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
  const { t } = useTranslation()
  const [open, setOpen] = useState<Record<string, ShReviewDetail | null>>({})
  const [busy, setBusy] = useState<string | null>(null)
  const list = useListState(REVIEW_LIST)
  const { q, site } = list.value

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
  const invalidate = useInvalidate()

  /** The curator asked, so the refetch is a response to them (SH701) — `isFetching` shows it. */
  const load = useCallback(() => { void invalidate("summary:review") }, [invalidate])

  // SH1024 — each site counts the runs `q` leaves that touched it, which is what choosing it lists.
  const facets = useMemo(() => withCounts(withSiteFacet([], runSites(sessions), t("content.filterSite")),
    { site: countLocally(sessions.filter((run) => matchesRun(run, q, "")), (run) => Object.keys(run.bySite ?? {})) }),
  [sessions, q, t])
  const shown = useMemo(() => sessions.filter((run) => matchesRun(run, q, site)), [sessions, q, site])


  const toggle = async (session: string) => {
    if (session in open) {
      const next = { ...open }
      delete next[session]
      setOpen(next)
      return
    }
    setOpen({ ...open, [session]: null })
    try {
      const detail = await service.detail(session)
      setOpen((prev) => ({ ...prev, [session]: detail }))
    } catch (error) {
      reportProblem(error, t("common.error"))
    }
  }

  /**
   * SH999 — arriving from the Activity page's agent chip, with one run named.
   *
   * Opened once the list is in, and only for a session the list actually holds: a link to a run
   * that has been approved away should land on the queue rather than on an empty expander, which
   * is the same fact the page already states by not listing it.
   */
  useEffect(() => {
    const wanted = new URLSearchParams(window.location.search).get("session")
    if (!wanted || sessions.every((each) => each.session !== wanted) || wanted in open) return
    // SH1075 — the read first and the state when it answers, SH701's shape: calling `toggle` here
    // set `open` synchronously inside the effect, which is the cascade this page was paid off for.
    service.detail(wanted)
      .then((detail) => setOpen((prev) => ({ ...prev, [wanted]: detail })))
      .catch((error: unknown) => reportProblem(error, t("common.error")))
    // `open` changes as a result of this; the guard above is what makes running once enough, so it
    // does not belong in the dependencies.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessions])

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
      const detail = await service.detail(session)
      setOpen((prev) => ({ ...prev, [session]: detail }))
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
      if (session in open) {
        setOpen((prev) => ({ ...prev, [session]: null }))
        service.detail(session).then((detail) =>
          setOpen((prev) => ({ ...prev, [session]: detail })),
        )
      }
      load()
      // SH207 — the navigation badge counts what this just changed, and a badge that disagrees
      // with the page it opens is the one number a curator learns to distrust.
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
      <PageHero
        icon={Bot}
        title={t("review.title")}
        eyebrow={t("admin.title")}
        subtitle={t("review.description")}
        urlBase={ROUTES.ADMIN_ROOT}
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
        {/* An empty queue and a filter that matched nothing are two facts, and say two things. */}
        {sessions.length === 0 ? (
          <EmptyState
            icon={IconRobot}
            tone="violet"
            title={t("review.blankTitle")}
            description={t("review.blankDescription")}
          />
        ) : shown.length === 0 ? (
          <EmptyState
            icon={IconRobot}
            tone="slate"
            title={t("review.noMatchTitle")}
            description={t("review.noMatchHint")}
          />
        ) : (
          <Panel contentClassName="p-0 divide-y">
              {shown.map((session) => {
                const expanded = session.session in open
                const detail = open[session.session]
                return (
                  <div key={session.session}>
                    <div className="flex items-center gap-3 px-4 py-3">
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
                          <div className="font-medium truncate">
                            {session.intent ?? t("review.noIntent")}
                          </div>
                          <div className="text-xs text-muted-foreground truncate">
                            {t("review.changesBy", {
                              count: session.changes,
                              actor: session.actor ?? t("review.unknownActor"),
                            })}
                            {` · `}
                            <code>{session.agentName ?? session.session}</code>
                            {session.lastSeen &&
                              ` · ${formatDistanceToNow(new Date(session.lastSeen), { addSuffix: true })}`}
                          </div>
                        </div>
                      </button>
                      <div className="flex flex-wrap items-center gap-1.5 shrink-0">
                        {Object.entries(session.byAction).map(([action, count]) => (
                          <Badge
                            key={action}
                            variant="outline"
                            className={`text-[10px] ${ACTION_BADGE[action] ?? ""}`}
                          >
                            {action} {count}
                          </Badge>
                        ))}
                        {/*
                          SH1000 — by site, and only when a run touched more than one: on a
                          single-site run the badge repeats what the addresses below already say,
                          and a badge that is always right and never news is one people stop
                          reading.
                        */}
                        {Object.keys(session.bySite ?? {}).length > 1 &&
                          Object.entries(session.bySite ?? {}).map(([site, count]) => (
                            <Badge key={site} variant="outline" className="text-[10px]">
                              {site} {count}
                            </Badge>
                          ))}
                        {session.pending > 0 && (
                          <Badge variant="secondary" className="text-[10px]">
                            {t("review.pending", { count: session.pending })}
                          </Badge>
                        )}
                      </div>
                      <div className="flex gap-2 shrink-0">
                        <Button
                          size="sm"
                          disabled={busy === session.session || session.pending === 0}
                          onClick={() => act(session.session, "approve")}
                        >
                          <Check className="mr-1 h-3.5 w-3.5" /> {t("review.approve")}
                        </Button>
                        <Button
                          size="sm"
                          variant="outline"
                          disabled={busy === session.session}
                          onClick={() => act(session.session, "revert")}
                        >
                          <Undo2 className="mr-1 h-3.5 w-3.5" /> {t("review.revert")}
                        </Button>
                      </div>
                    </div>

                    {expanded && (
                      <div className="bg-muted/30 px-4 py-2">
                        {detail === null ? (
                          <p className="py-2 text-sm text-muted-foreground">{t("common.loading")}</p>
                        ) : (
                          <>
                          {/*
                            SH1000 — what this run changed the LOOK of, which the action column
                            cannot say: an UPDATE that moved a headline and one that moved the whole
                            page read identically there. Shown only when something was comparable,
                            because "0 of 0 pages" is a sentence about the instrument.
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
                            SH1001 — the decision moves from the run to the change. The two buttons
                            above still decide the whole run, because "all of it" is a real answer
                            and the common one; this is the answer for a run where one page is
                            wrong, which used to force a curator to accept all of it or undo all
                            of it.
                          */}
                          <ReviewTriage
                            entries={detail.entries}
                            decided={new Map((detail.decisions ?? []).map((each) =>
                              [each.postId, each.decision]))}
                            busy={busy === session.session}
                            onDecide={(decisions) => triage(session.session, decisions)}
                          />
                          </>
                        )}
                      </div>
                    )}
                  </div>
                )
              })}
          </Panel>
        )}
      </div>
    </>
  )
}
