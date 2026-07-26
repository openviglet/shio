import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent } from "@/components/ui/card"
import { Separator } from "@/components/ui/separator"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import {
  Bot,
  Check,
  ChevronDown,
  ChevronRight,
  Globe,
  Loader2,
  RefreshCw,
  Undo2,
} from "lucide-react"
import { useCallback, useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { ShReviewService } from "@/services/review/review.service"
import type {
  ShReview,
  ShReviewChange,
  ShReviewOutcome,
  ShReviewSession,
} from "@/models/review/review.model"

const service = new ShReviewService()

/**
 * The agent review queue (SH109) — the human's primary workflow under the agent-native
 * orientation: the agent builds, the person approves or puts it back.
 *
 * One card per **agent session** rather than one row per change. A run is one intention
 * expressed as many audit rows, and a flat list would ask the reviewer to reconstruct the
 * intention from its fragments. Expanding a card loads the full change list; the summary
 * carries the number that decides urgency, which is how many changes went *live*.
 */

/** Maps an audit action to its badge styling — same palette as the activity trail. */
const ACTION_BADGE: Record<string, string> = {
  CREATE: "border-blue-300 text-blue-600 dark:border-blue-700 dark:text-blue-400",
  UPDATE: "border-indigo-300 text-indigo-600 dark:border-indigo-700 dark:text-indigo-400",
  PUBLISH: "border-green-300 text-green-600 dark:border-green-700 dark:text-green-400",
  UNPUBLISH: "border-amber-300 text-amber-600 dark:border-amber-700 dark:text-amber-400",
  DELETE: "border-red-300 text-red-600 dark:border-red-700 dark:text-red-400",
  RESTORE: "border-teal-300 text-teal-600 dark:border-teal-700 dark:text-teal-400",
}

export default function ReviewListPage() {
  const { t } = useTranslation()
  const { resetBreadcrumb } = useBreadcrumb()
  const [queue, setQueue] = useState<ShReview>({ sessions: 0, pending: 0, results: [] })
  const [loading, setLoading] = useState(false)
  const [pendingOnly, setPendingOnly] = useState(true)
  const [expanded, setExpanded] = useState<string | null>(null)
  const [detail, setDetail] = useState<ShReviewChange[]>([])
  const [busy, setBusy] = useState<string | null>(null)
  const [outcome, setOutcome] = useState<ShReviewOutcome | null>(null)

  const load = useCallback(
    (only: boolean) => {
      setLoading(true)
      service
        .queue(only)
        .then(setQueue)
        .catch(() => setQueue({ sessions: 0, pending: 0, results: [] }))
        .finally(() => setLoading(false))
    },
    [],
  )

  useEffect(() => {
    resetBreadcrumb([
      { label: t("admin.title"), href: ROUTES.ADMIN_ROOT },
      { label: t("review.title") },
    ])
    load(pendingOnly)
  }, [resetBreadcrumb, t, load, pendingOnly])

  const toggle = async (session: ShReviewSession) => {
    if (expanded === session.session) {
      setExpanded(null)
      return
    }
    setExpanded(session.session)
    setDetail(session.preview)
    const changes = await service.changes(session.session).catch(() => session.preview)
    setDetail(changes)
  }

  const act = async (session: string, verb: "approve" | "revert") => {
    // Revert is destructive in the direction the reviewer cares about — it undoes real
    // content — so it confirms. Approve records that a person looked, and does not.
    if (verb === "revert" && !confirm(t("review.revertConfirm"))) return
    setBusy(session)
    setOutcome(null)
    try {
      const result = verb === "approve"
        ? await service.approve(session)
        : await service.revert(session)
      setOutcome(result)
      load(pendingOnly)
    } finally {
      setBusy(null)
    }
  }

  const when = (iso?: string) => (iso ? new Date(iso).toLocaleString() : "—")

  const actionLabel = (action: string) => {
    const key = `activity.action${action.charAt(0)}${action.slice(1).toLowerCase()}`
    const label = t(key)
    return label === key ? action : label
  }

  return (
    <>
      <PageHeader title={t("review.title")} turIcon={Bot} urlBase={ROUTES.ADMIN_ROOT} />
      <div className="p-6">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h2 className="text-lg font-semibold">{t("review.title")}</h2>
            <p className="text-sm text-muted-foreground">{t("review.description")}</p>
          </div>
          <div className="flex items-center gap-2">
            <Button
              onClick={() => setPendingOnly((prev) => !prev)}
              size="sm"
              variant={pendingOnly ? "default" : "outline"}
            >
              {pendingOnly ? t("review.showingPending") : t("review.showingAll")}
            </Button>
            <Button onClick={() => load(pendingOnly)} size="sm" variant="outline" disabled={loading}>
              <RefreshCw className={`mr-2 h-4 w-4 ${loading ? "animate-spin" : ""}`} />
              {t("activity.refresh")}
            </Button>
          </div>
        </div>

        {outcome && (
          <Card className="mb-4 border-l-4 border-l-orange-500">
            <CardContent className="py-3 text-sm">
              <p className="font-medium">
                {t("review.outcome", {
                  affected: outcome.affected,
                  reverted: outcome.reverted,
                })}
              </p>
              {/* Never silent about a partial undo: reporting "reverted" over changes that
                  are still in place is the one wrong answer this screen can give. */}
              {outcome.skipped && outcome.skipped.length > 0 && (
                <ul className="mt-2 list-disc pl-5 text-muted-foreground">
                  {outcome.skipped.map((reason) => (
                    <li key={reason}>{reason}</li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>
        )}

        {queue.results.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-center">
            <div className="rounded-full bg-muted p-4 mb-4">
              <Bot className="h-8 w-8 text-muted-foreground" />
            </div>
            <h3 className="font-semibold text-lg mb-1">{t("review.blankTitle")}</h3>
            <p className="text-sm text-muted-foreground max-w-sm">{t("review.blankDescription")}</p>
          </div>
        ) : (
          <div className="space-y-3">
            {queue.results.map((session) => {
              const open = expanded === session.session
              return (
                <Card key={session.session} className="border-l-4 border-l-orange-500">
                  <CardContent className="p-4">
                    <div className="flex items-start justify-between gap-4">
                      <button
                        type="button"
                        onClick={() => toggle(session)}
                        className="flex flex-1 items-start gap-3 text-left"
                      >
                        {open ? (
                          <ChevronDown className="mt-1 h-4 w-4 shrink-0 text-muted-foreground" />
                        ) : (
                          <ChevronRight className="mt-1 h-4 w-4 shrink-0 text-muted-foreground" />
                        )}
                        <div className="min-w-0">
                          <div className="flex flex-wrap items-center gap-2">
                            <span className="font-semibold">
                              {t("review.proposed", { count: session.changes })}
                            </span>
                            {session.pending > 0 && (
                              <Badge variant="outline" className="border-amber-300 text-amber-600 dark:border-amber-700 dark:text-amber-400">
                                {t("review.pendingCount", { count: session.pending })}
                              </Badge>
                            )}
                            {session.published > 0 && (
                              <Badge variant="outline" className="border-green-300 text-green-600 dark:border-green-700 dark:text-green-400">
                                <Globe className="mr-1 h-3 w-3" />
                                {t("review.liveCount", { count: session.published })}
                              </Badge>
                            )}
                            {session.sites.map((site) => (
                              <Badge key={site} variant="secondary">{site}</Badge>
                            ))}
                          </div>
                          <p className="mt-1 text-xs text-muted-foreground">
                            {t("review.byAt", {
                              actor: session.actor ?? "—",
                              at: when(session.endedAt),
                            })}
                          </p>
                          <p className="mt-0.5 font-mono text-[11px] text-muted-foreground">
                            {session.session}
                          </p>
                        </div>
                      </button>
                      <div className="flex shrink-0 gap-2">
                        <Button
                          size="sm"
                          variant="outline"
                          disabled={busy === session.session || session.pending === 0}
                          onClick={() => act(session.session, "approve")}
                        >
                          {busy === session.session ? (
                            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                          ) : (
                            <Check className="mr-2 h-4 w-4" />
                          )}
                          {t("review.approve")}
                        </Button>
                        <Button
                          size="sm"
                          variant="outline"
                          className="text-red-600 hover:text-red-700"
                          disabled={busy === session.session}
                          onClick={() => act(session.session, "revert")}
                        >
                          <Undo2 className="mr-2 h-4 w-4" />
                          {t("review.revert")}
                        </Button>
                      </div>
                    </div>

                    {open && (
                      <>
                        <Separator className="my-3" />
                        <Table>
                          <TableHeader>
                            <TableRow>
                              <TableHead className="w-32">{t("activity.action")}</TableHead>
                              <TableHead>{t("review.address")}</TableHead>
                              <TableHead>{t("activity.what")}</TableHead>
                              <TableHead className="w-44">{t("activity.when")}</TableHead>
                              <TableHead className="w-28">{t("review.state")}</TableHead>
                            </TableRow>
                          </TableHeader>
                          <TableBody>
                            {detail.map((change) => (
                              <TableRow key={change.id}>
                                <TableCell>
                                  <Badge
                                    variant="outline"
                                    className={ACTION_BADGE[change.action] ?? ""}
                                  >
                                    {actionLabel(change.action)}
                                  </Badge>
                                </TableCell>
                                <TableCell className="font-mono text-xs">
                                  {change.address ?? "—"}
                                </TableCell>
                                <TableCell className="text-sm">{change.what ?? "—"}</TableCell>
                                <TableCell className="text-xs text-muted-foreground">
                                  {when(change.at)}
                                </TableCell>
                                <TableCell className="text-xs">
                                  {change.reviewed ? (
                                    <span className="text-green-600 dark:text-green-400">
                                      {t("review.reviewedBy", { who: change.reviewedBy ?? "—" })}
                                    </span>
                                  ) : change.revertible ? (
                                    <span className="text-muted-foreground">{t("review.pending")}</span>
                                  ) : (
                                    /* Said before the button is pressed, not after: a
                                       reviewer needs to know which rows revert will not
                                       touch while deciding whether to press it. */
                                    <span className="text-amber-600 dark:text-amber-400">
                                      {t("review.notRevertible")}
                                    </span>
                                  )}
                                </TableCell>
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      </>
                    )}
                  </CardContent>
                </Card>
              )
            })}
          </div>
        )}
      </div>
    </>
  )
}
