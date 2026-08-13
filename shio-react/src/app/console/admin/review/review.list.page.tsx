import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent } from "@/components/ui/card"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { useReviewPending } from "@/contexts/review-pending.context"
import { PostVersionsDialog } from "@/components/post-versions.dialog"
import { ShPostUnifiedService } from "@/services/post-unified/post-unified.service"
import { postFormSchema } from "@/models/post-unified/post-unified"
import type { ShPostTypeSchemaField } from "@/models/post-unified/post-unified"
import { ShReviewService } from "@/services/review/review.service"
import type { ShReviewDetail, ShReviewSession } from "@/models/review/review.model"
import { formatDistanceToNow } from "date-fns"
import { Bot, Check, ChevronDown, ChevronRight, GitCompare, RefreshCw, Undo2 } from "lucide-react"
import { useCallback, useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "@viglet/viglet-design-system"

const service = new ShReviewService()
const postService = new ShPostUnifiedService()

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
  const { resetBreadcrumb } = useBreadcrumb()
  const { refresh: refreshPending } = useReviewPending()
  const [sessions, setSessions] = useState<ShReviewSession[]>([])
  const [open, setOpen] = useState<Record<string, ShReviewDetail | null>>({})
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState<string | null>(null)
  const [diffPostId, setDiffPostId] = useState<string | null>(null)
  const [diffSchema, setDiffSchema] = useState<ShPostTypeSchemaField[]>([])

  /** The read. It raises no state before the request, which is what lets the mount effect call it. */
  const fetchSessions = useCallback(
    () => service.sessions().then(setSessions).catch(() => setSessions([]))
      .finally(() => setLoading(false)),
    [],
  )

  /** The curator asked, so the spinner is a response to them (SH701). */
  const load = useCallback(() => {
    setLoading(true)
    void fetchSessions()
  }, [fetchSessions])

  useEffect(() => {
    resetBreadcrumb([
      { label: t("admin.title"), href: ROUTES.ADMIN_ROOT },
      { label: t("review.title") },
    ])
    void fetchSessions()
  }, [resetBreadcrumb, t, fetchSessions])

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
    } catch {
      toast.error(t("common.error"))
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
      refreshPending()
    } catch {
      toast.error(t("common.error"))
    } finally {
      setBusy(null)
    }
  }

  /**
   * Opens the SH14 draft↔published diff for one post. The schema comes from the post read
   * itself, which is what SH176 made possible: before it, the post-type never reached the
   * browser and this dialog had nothing to render.
   */
  const openDiff = async (postId: string) => {
    try {
      const post = await postService.get(postId, "DRAFT").catch(() => postService.get(postId, "PUBLISHED"))
      setDiffSchema(postFormSchema(post.shPostType))
      setDiffPostId(postId)
    } catch {
      toast.error(t("common.error"))
    }
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
          <Button onClick={load} size="sm" variant="outline" disabled={loading}>
            <RefreshCw className={`mr-2 h-4 w-4 ${loading ? "animate-spin" : ""}`} />
            {t("common.refresh")}
          </Button>
        </div>

        {sessions.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-center">
            <div className="rounded-full bg-muted p-4 mb-4">
              <Bot className="h-8 w-8 text-muted-foreground" />
            </div>
            <h3 className="font-semibold text-lg mb-1">{t("review.blankTitle")}</h3>
            <p className="text-sm text-muted-foreground max-w-sm">{t("review.blankDescription")}</p>
          </div>
        ) : (
          <Card>
            <CardContent className="p-0 divide-y">
              {sessions.map((session) => {
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
                          <div className="font-medium truncate">
                            {t("review.changesBy", {
                              count: session.changes,
                              actor: session.actor ?? t("review.unknownActor"),
                            })}
                          </div>
                          <div className="text-xs text-muted-foreground truncate">
                            <code>{session.session}</code>
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
                          <ul className="divide-y">
                            {detail.entries.map((entry, index) => (
                              <li
                                key={`${entry.postId}-${entry.date}-${index}`}
                                className="flex items-center gap-3 py-2 text-sm"
                              >
                                <Badge
                                  variant="outline"
                                  className={`text-[10px] ${ACTION_BADGE[entry.action] ?? ""}`}
                                >
                                  {entry.action}
                                </Badge>
                                <span className="flex-1 min-w-0 truncate">
                                  {entry.title || entry.postId}
                                  {entry.address && (
                                    <code className="ml-2 text-xs text-muted-foreground">
                                      {entry.address}
                                    </code>
                                  )}
                                </span>
                                {entry.draft && (
                                  <Badge variant="secondary" className="text-[10px]">
                                    {t("review.draft")}
                                  </Badge>
                                )}
                                {entry.published && (
                                  <Badge variant="secondary" className="text-[10px]">
                                    {t("review.published")}
                                  </Badge>
                                )}
                                {entry.trashed && (
                                  <Badge variant="secondary" className="text-[10px]">
                                    {t("review.trashed")}
                                  </Badge>
                                )}
                                {entry.postId && entry.published && entry.draft && (
                                  <Button
                                    size="sm"
                                    variant="ghost"
                                    onClick={() => openDiff(entry.postId!)}
                                  >
                                    <GitCompare className="mr-1 h-3.5 w-3.5" /> {t("review.diff")}
                                  </Button>
                                )}
                              </li>
                            ))}
                          </ul>
                        )}
                      </div>
                    )}
                  </div>
                )
              })}
            </CardContent>
          </Card>
        )}
      </div>

      {diffPostId && (
        <PostVersionsDialog
          postId={diffPostId}
          schema={diffSchema}
          open={Boolean(diffPostId)}
          setOpen={(next) => {
            if (!next) setDiffPostId(null)
          }}
          onRestored={() => {
            setDiffPostId(null)
            load()
          }}
        />
      )}
    </>
  )
}
