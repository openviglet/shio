import { IconRobot } from "@tabler/icons-react"
import { AdaptiveEmptyState } from "@/components/adaptive-empty-state"
import { AdaptivePageHeader } from "@/components/adaptive-page-header"
import { AdaptivePanel } from "@/components/adaptive-panel"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { ROUTES } from "@/app/routes.const"
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
    void fetchSessions()
  }, [t, fetchSessions])

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
      {/*
        SH906 — the curator's approval surface, and the one §LV.9 says must not be the last thing
        that still looks old. It is also the shape that section predicted the package covers least
        well: a hero over a list of expandable sessions, which is not a mosaic and should not be
        made into one. `AdaptivePanel` rather than `AdaptiveSectionCard` because the panel has no
        header — the hero above already names the page, and a header-less section card renders a
        console card in both eras (SH902).
      */}
      <AdaptivePageHeader
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
      <div className="p-6">
        {sessions.length === 0 ? (
          <AdaptiveEmptyState
            icon={IconRobot}
            tone="violet"
            title={t("review.blankTitle")}
            description={t("review.blankDescription")}
          />
        ) : (
          <AdaptivePanel contentClassName="p-0 divide-y">
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
          </AdaptivePanel>
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
