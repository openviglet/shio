import { PageHero } from "@/components/page-hero"
import { Button } from "@/components/ui/button"
/*
 * SH892 — the form's section is `AdaptiveSectionCard`. The schema is built at runtime from the post
 * type and the fields are fifteen dynamic widgets rendered through a widget map, each reading a
 * `widgetSettings` value the backend can return as the literal string "null"; the section only
 * frames them. Imported from the bento subpath, the only entry that exports it.
 */
import { AdaptiveSectionCard, BentoSplitPage } from "@viglet/viglet-design-system/bento"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Badge } from "@/components/ui/badge"
import { BadgeColorful } from "@/components/badge-colorful"
import { ROUTES } from "@/app/routes.const"
import { PostFormFields } from "@/components/widgets/post-form-fields"
import { IconEye, IconFileText, IconHistory, IconMessage, IconSparkles } from "@tabler/icons-react"
import { ChevronDown, Clock, Eye, EyeOff, History, Link2, LogOut, Save, Sparkles, Upload, X } from "lucide-react"
import { useQuery } from "@tanstack/react-query"
import { addressKey } from "@/lib/query-keys"
import { isCancelled } from "@/lib/read-options"
import { useEffect, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom"
import { useUsedOn } from "@/app/content/use-used-on"
import { draftDiffers, lifecycleKey } from "@/lib/post-lifecycle"
import axios from "axios"
import { ShPostUnifiedService } from "@/services/post-unified/post-unified.service"
import { ShPreviewTokenService } from "@/services/preview-token/preview-token.service"
import { ShAclService } from "@/services/acl/acl.service"
import { ShSiteService } from "@/services/site/site.service"
import type { ShSite } from "@/models/site/site"
import { isRenderable } from "@/lib/layout-bindings"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { NoteRail } from "@/components/note-rail"
import { PostHistoryPanel } from "@/components/post-history.panel"
import { PostInsightsPanel } from "@/components/post-insights.panel"
import { PostLivePreview } from "@/components/post-live-preview"
import { fieldAnchorId } from "@/lib/field-ids"
import { WorkingLine } from "@/components/working-line"
import { PostVersionsDialog } from "@/components/post-versions.dialog"
import { AskAgentDialog } from "@/components/ask-agent-dialog"
import { useAgentAvailable } from "@/app/system/use-agent-available"
import { useAppliedSuggestions } from "@/app/content/use-applied-suggestions"
import { PostRenameDialog } from "@/components/post-rename.dialog"
import { PostScheduleDialog } from "@/components/post-schedule.dialog"
import { PublishPreviewDialog } from "@/components/publish-preview.dialog"
import { PostTranslationsMenu } from "@/components/post-translations.menu"
import type { ShPostUnified, ShPostState } from "@/models/post-unified/post-unified"
import { postFormSchema } from "@/models/post-unified/post-unified"
import { GradientButton, toast } from "@viglet/viglet-design-system"
import { breadcrumbFrom, heroTrail } from "@/app/content/content-browser/browser-view"
import { fieldOf, headlineOf, problemOf, reportRefusal, type RefusalActions } from "@/lib/problem"
import { LoadProvider } from "@viglet/viglet-design-system/router"
import { changedAttrs, isDirty } from "@/lib/dirty"
import { bufferDraft, clearBuffer, readBuffer } from "@/lib/draft-buffer"
import { useUnsavedGuard } from "@/hooks/use-unsaved-guard"
import { useAutosave } from "@/hooks/use-autosave"
import { PostConflictDialog } from "@/app/content/post-conflict.dialog"
import { conflictsBetween, type PostConflictField } from "@/lib/post-conflict"

const unifiedService = new ShPostUnifiedService()
const previewService = new ShPreviewTokenService()
// SH995 — asked once per post: whether this curator may put it live.
const aclService = new ShAclService()
const siteService = new ShSiteService()

export default function PostUnifiedEditPage() {
  const { postId } = useParams() as { postId: string }
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [post, setPost] = useState<ShPostUnified | null>(null)
  const [state, setState] = useState<ShPostState>("DRAFT")
  const [data, setData] = useState<Record<string, unknown>>({})
  /** SH1019 — the field whose "ask the agent" prompt is open. */
  const [asking, setAsking] = useState<{ name: string; label: string } | null>(null)
  const agentAvailable = useAgentAvailable()
  const [isLoading, setIsLoading] = useState(false)
  /**
   * SH955 — the field the server's last refusal was about, so the form can mark it.
   *
   * A conflict over a friendly URL used to arrive as a toast above a page with sixteen inputs: it
   * said which URL was taken and not which box to type in, and by the time the curator went
   * looking the toast had gone.
   */
  const [refusal, setRefusal] = useState<{ field: string; message: string } | undefined>(undefined)
  const [isPreviewing, setIsPreviewing] = useState(false)
  /**
   * A PUBLISHED row exists — i.e. the page is live (SH654). Distinct from `state`, which
   * says which row this form is editing: a post published and then edited has both rows,
   * so `state` is DRAFT while the site is serving it. Deciding Unpublish off `state` would
   * hide the button in exactly the case a curator needs it.
   */
  const [isLive, setIsLive] = useState(false)
  /** SH1324 — the row the site serves, so the header can say when the draft has moved past it. */
  const [liveRow, setLiveRow] = useState<ShPostUnified | null>(null)
  const [isPublishing, setIsPublishing] = useState(false)
  const [parentFolderId, setParentFolderId] = useState<string | null>(null)
  const [versionsOpen, setVersionsOpen] = useState(false)
  // SH1132 — the console's first writer of a friendly URL, which `post.move` was alone in being.
  const [renameOpen, setRenameOpen] = useState(false)
  /**
   * SH995 — publishing is a permission above writing, so the two controls that change what visitors
   * see are disabled when this curator does not hold it, carrying the server's own sentence. Asked
   * rather than assumed: the alternative is offering a button and answering it with a 403, which
   * tells somebody they cannot do a thing only after they have tried.
   */
  const [scheduleOpen, setScheduleOpen] = useState(false)
  /** SH996 — Publish arms behind the server's own plan, rather than being a button with no preview. */
  const [publishPreviewOpen, setPublishPreviewOpen] = useState(false)
  /** SH998 — what a gate said about this publish, which turns the dialog into the override. */
  const [gateRefusal, setGateRefusal] = useState<string | null>(null)
  /**
   * SH985 — the refusal that needs a view rather than a message.
   *
   * A 409 means somebody wrote to this post after the form was loaded. The server knows the two
   * versions disagree and cannot know which the curator wants, so the answer is the comparison:
   * what is stored now beside what is in the form, field by field, defaulting to what is stored.
   */
  const [conflict, setConflict] = useState<
    { fields: PostConflictField[]; theirs: Record<string, unknown>; detail?: string } | undefined
  >(undefined)
  /*
   * SH1158 — the heaviest form in the product, read through the query layer and cancelled when the
   * curator leaves. Three reads, and each keeps the reason it had:
   *
   * Both rows in one round trip. Which one is editable decides `state`, and whether the PUBLISHED row
   * exists is the separate fact Unpublish needs (SH654) — the old sequential try/catch could not
   * report it when a draft was found, which is why this is `allSettled` and not `all`.
   *
   * The permission read is its own query rather than a call inside this one, because it answers a
   * different question and a failure means something different: a permission read that fails leaves
   * the buttons as they were, since the server is the one that decides and hiding a control because a
   * side call broke would be the worse guess.
   */
  const loaded = useQuery({
    queryKey: addressKey(`id:${postId}`),
    queryFn: async ({ signal }) => {
      const [draft, published] = await Promise.allSettled([
        unifiedService.get(postId, "DRAFT", { signal }),
        unifiedService.get(postId, "PUBLISHED", { signal }),
      ])
      const editable = draft.status === "fulfilled" ? draft.value : null
      const live = published.status === "fulfilled" ? published.value : null
      const unified: ShPostUnified | null = editable ?? live
      if (!unified) {
        // Both reads failed, or the post is gone. Either way this page has nothing to draw, and the
        // rejection the DRAFT read carries is the one that says which.
        throw draft.status === "rejected" ? draft.reason : new Error("not found")
      }
      return { unified, live, resolvedState: (editable ? "DRAFT" : "PUBLISHED") as ShPostState }
    },
    enabled: Boolean(postId),
    retry: false,
  })

  const allowed = useQuery({
    queryKey: addressKey(`summary:allowed|${postId}`),
    queryFn: ({ signal }) => aclService.allowed(postId, { signal }),
    enabled: Boolean(postId),
  })

  /*
   * Seeded during render, once per post (`react-hooks/set-state-in-effect`). `data` is the form the
   * curator edits, so it is a local copy on purpose — the rest follow it so the whole page describes
   * one answer rather than parts of two.
   */
  const [seeded, setSeeded] = useState<string | null>(null)
  if (loaded.isSuccess && seeded !== postId) {
    setSeeded(postId)
    setPost(loaded.data.unified)
    setState(loaded.data.resolvedState)
    setIsLive(loaded.data.live !== null)
    setLiveRow(loaded.data.live)
    setData(loaded.data.unified.data || {})
    // SH933 — the folder is still read for the parent id, which the move dialog and the "up one
    // level" action both need.
    if (loaded.data.unified.shFolder) setParentFolderId(loaded.data.unified.shFolder.id)
  }

  /*
   * SH1049 — why the post is not here, when it is not.
   *
   * Without it, `post` stays `null` on a failed read and the render below spins for ever: a 500, a
   * deleted post and a slow server are the same screen, and there is nothing to press. Derived from the
   * query rather than recorded by a setter now, and `isCancelled` is what the derivation adds — an
   * aborted read rejects, so a curator navigating away would otherwise be told the post is not there.
   */
  const loadFailure = loaded.isError && !isCancelled(loaded.error) ? loaded.error : null
  const publishHint = allowed.data && !allowed.data.publish
    ? (allowed.data.publishHint ?? null)
    : null

  /*
   * SH1326 — "Publish…" on the new-post form arrives here with `?publish=1`: the same confirmation
   * Actions → Publish opens, asked once the post and the right to publish it are known. Opened in
   * render, once, and the parameter dropped afterwards so a reload does not ask again.
   */
  const [searchParams, setSearchParams] = useSearchParams()
  const wantsPublish = searchParams.get("publish") === "1"
  const [publishAsked, setPublishAsked] = useState(false)
  if (wantsPublish && !publishAsked && post && allowed.data) {
    setPublishAsked(true)
    if (allowed.data.publish) setPublishPreviewOpen(true)
  }
  useEffect(() => {
    if (!publishAsked || !wantsPublish) return
    setSearchParams((previous) => {
      const next = new URLSearchParams(previous)
      next.delete("publish")
      return next
    }, { replace: true })
  }, [publishAsked, wantsPublish, setSearchParams])

  /**
   * SH1011 — is there unsaved work here?
   *
   * Derived from a canonical serialization against the post as it was loaded, rather than tracked
   * by a flag somebody has to remember to set: a widget hands back a new object on every keystroke,
   * so reference equality says dirty always, and a flag set on change and cleared on save is one
   * more thing that can be wrong in the direction that loses work.
   */
  const dirty = useMemo(() => post != null && isDirty(post.data ?? {}, data), [post, data])

  // The browser asks before the tab closes, and only while there is something to lose. An in-app
  // route change still walks away silently — that needs a data router (SH1090).
  useUnsavedGuard(dirty)

  /**
   * SH1011 — what an autosave would send: the changed keys, and only those.
   *
   * A PATCH of the whole document would be a PUT with extra steps, and it would overwrite a field
   * somebody else changed while this page sat open. The merge verb's rule is that a field not named
   * keeps its value, and that rule is worth something only if the caller names as little as it can.
   */
  const pendingAttrs = useMemo(
    () => (post == null ? {} : changedAttrs(post.data ?? {}, data)),
    [post, data],
  )

  const autosave = useAutosave({
    dirty,
    // Never while the curator's own write is in flight: two writes racing on one row is the thing
    // SH985's guard would then refuse, and the refusal would be this page arguing with itself.
    busy: isLoading || isPublishing || conflict !== undefined,
    attrs: pendingAttrs,
    save: async (attrs) => {
      const saved = await unifiedService.patch(postId, attrs)
      setPost(saved)
      void clearBuffer(postId)
    },
  })

  // SH1011 — and the browser's own copy, kept as the curator types. It covers what autosave cannot:
  // the seconds before the next write, and every case where the write cannot happen at all.
  useEffect(() => {
    if (!dirty) return
    void bufferDraft(postId, data)
  }, [dirty, postId, data])

  /**
   * SH1011 — a buffer this browser kept, offered rather than restored.
   *
   * Restoring it would make the page a second writer with no name on it: a curator who opens a post
   * and finds text they do not remember typing cannot tell whether it is theirs, an agent's, or
   * yesterday's. So the page loads what the server has, and asks.
   */
  const [offered, setOffered] = useState<{ data: unknown; at: number } | null>(null)

  /** SH1013 — bumped by a save the curator asked for, which is when the lint is worth re-reading. */
  const [insightsAt, setInsightsAt] = useState(0)

  useEffect(() => {
    let cancelled = false
    void readBuffer(postId).then((buffer) => {
      if (cancelled || !buffer || post == null) return
      if (isDirty(post.data ?? {}, buffer.data)) {
        setOffered({ data: buffer.data, at: buffer.at })
      }
    })
    return () => {
      cancelled = true
    }
    // Once the post is loaded, and only then: the comparison is against what the server sent.
  }, [postId, post])

  const schema = useMemo(() => postFormSchema(post?.shPostType), [post])
  // SH1019 — a suggestion applied from the dock lands here as an unsaved edit.
  useAppliedSuggestions(postId, post != null, setData)

  /** Bring a field into view and put the cursor in it — the insights panel and a refusal both ask. */
  function goToField(name: string) {
    const anchor = document.getElementById(fieldAnchorId(name))
    anchor?.scrollIntoView({ behavior: "smooth", block: "center" })
    anchor?.querySelector<HTMLElement>("input, textarea, select, [contenteditable]")?.focus()
  }

  /*
   * SH966 — a refusal goes to the dock, not a toast: it stays until read, types the server's
   * detail as the caption, and offers what the problem document makes possible — the field `at`
   * names, and the `didYouMean` correction put into it.
   */
  const refusalActions: RefusalActions = {
    t: (key, options) => String(t(key, options)),
    goToField,
    applySuggestion: (field, value) => setData((current) => ({ ...current, [field]: value })),
  }
  const postTypeName = post?.shPostType?.title || post?.shPostType?.name

  const visibleFieldCount = useMemo(
    () => schema.filter((f) => f.widget !== "Hidden" && f.widget !== "Tab").length,
    [schema]
  )

  const hasSite = Boolean(post?.shFolder?.shSite?.id || post?.shSite?.id)

  /*
   * SH1167 — whether this post is a page at all on its site. A menu, a section or a theme has no
   * layout binding, and asking the server to render one is a 400 the browser logs on every such
   * editor. Keyed like the visual editor's read of the same site, so the two share one cache entry.
   * Unknown while loading (nothing is rendered yet), and a read that fails leaves the preview asking
   * as it always did: a missing answer must not hide a preview that would have worked.
   */
  const siteId = post?.shSite?.id ?? post?.shFolder?.shSite?.id
  const site = useQuery<ShSite>({
    queryKey: addressKey(`id:${siteId ?? ""}`),
    queryFn: ({ signal }) => siteService.get(siteId ?? "", { signal }),
    enabled: Boolean(siteId),
  })
  const renderable = isRenderable({
    siteId, failed: site.isError, layouts: site.data?.postTypeLayout, typeName: post?.shPostType?.name,
  })
  // SH1323 — a post with no layout of its own (a section) is previewed on a page that uses it.
  const usedOn = useUsedOn(postId, site.data?.name, post?.furl, renderable === false).data ?? []

  // The folder the post lives in: Cancel and the hero's Back link both return there, not to the
  // site list — the link used to be the bare content root, so Back skipped the site entirely.
  const backUrl = parentFolderId ? `${ROUTES.CONTENT_ROOT}/object/${parentFolderId}` : ROUTES.CONTENT_ROOT

  // SH1192 — where the post lives, as the hero's trail: the site list, the site, each folder down
  // to the one it sits in. The same `/path` read the folder browser draws its own trail from.
  const folderPath = useQuery({
    queryKey: addressKey(`summary:path|${parentFolderId ?? ""}`),
    queryFn: ({ signal }) => axios.get(`/v2/object/${parentFolderId}/path`, { signal })
      .then((res) => breadcrumbFrom(res.data)),
    enabled: Boolean(parentFolderId),
  })
  const trail = folderPath.data
    ? heroTrail(folderPath.data, { to: ROUTES.CONTENT_ROOT, label: t("content.sites") },
      (id) => `${ROUTES.CONTENT_ROOT}/object/${id}`, { ownPath: false })
    : undefined

  // SH1090 — no confirm of its own: useUnsavedGuard's router blocker asks for this exit as it
  // does for every other, so Cancel with unsaved work still says "discard" before it does.
  const navigateBack = () => navigate(backUrl)

  async function handlePreview() {
    setIsPreviewing(true)
    try {
      const { previewUrl } = await previewService.mint(postId)
      window.open(previewUrl, "_blank", "noopener")
    } catch (error) {
      // 400 = the site has no front-end url configured; 404 = preview disabled / gone.
      if (axios.isAxiosError(error) && error.response?.status === 400) {
        toast.error(t("content.previewNoUrl"))
      } else {
        toast.error(t("content.previewError"))
      }
    } finally {
      setIsPreviewing(false)
    }
  }

  async function handleSave(close: boolean) {
    setIsLoading(true)
    setRefusal(undefined)
    try {
      const updated = await unifiedService.update(postId, { data, state })
      // SH1013 — the insights re-read on the curator's own save, not on autosave's. Both write the
      // same row, but a lint renders the page server-side, and doing that every three seconds of
      // typing is the cost P3 exists to refuse for an answer nobody asked for.
      setInsightsAt((n) => n + 1)
      toast.success(t("common.saved"))
      if (close) {
        navigateBack()
      } else {
        setPost(updated)
        setData(updated.data || {})
      }
    } catch (error) {
      if (await openConflict(error)) return
      // SH955 — the report says what happened; `at` says where, so the field is marked too.
      const problem = reportRefusal(error, t("common.error"), refusalActions)
      const field = fieldOf(problem)
      setRefusal(field ? { field, message: headlineOf(problem, t("common.error")) } : undefined)
    } finally {
      setIsLoading(false)
    }
  }

  /**
   * A stale save opens the comparison instead of toasting the refusal.
   *
   * The re-read is what makes the resolution safe: it fetches the row as it is now *and* leaves
   * this service holding that row's version, so whatever the curator decides is saved conditionally
   * on what they were shown rather than on the version the refusal was about.
   */
  async function openConflict(error: unknown): Promise<boolean> {
    const problem = problemOf(error)
    if (problem.status !== 409) return false
    const theirs = await unifiedService.get(postId, state)
    const labelOf = (name: string) =>
      schema.find((field) => field.name === name)?.label || name
    setConflict({
      fields: conflictsBetween(data, theirs.data || {}, labelOf),
      theirs: theirs.data || {},
      detail: problem.detail,
    })
    return true
  }

  /** Save what the curator chose, conditional on the row the dialog showed them. */
  async function resolveConflict(keepMine: readonly string[]) {
    if (!conflict) return
    const merged = { ...conflict.theirs }
    for (const field of keepMine) merged[field] = data[field]
    setIsLoading(true)
    try {
      const updated = await unifiedService.update(postId, { data: merged, state })
      setPost(updated)
      setData(updated.data || {})
      setConflict(undefined)
      toast.success(t("common.saved"))
    } catch (error) {
      if (!(await openConflict(error))) reportRefusal(error, t("common.error"), refusalActions)
    } finally {
      setIsLoading(false)
    }
  }

  /**
   * Save first, then publish (SH654). The server publishes the *stored* draft, so
   * publishing without the save would put a version on the site that the curator
   * never saw — draft-default (P7) is only meaningful if the deliberate act promotes
   * what is on screen.
   */
  // SH1064 — `redirect` arrives from the dialog, which offers it only when an address stops
  // answering; the server still decides whether there is one, so a stale tick writes nothing.
  async function handlePublish(override?: string, redirect?: boolean) {
    setIsPublishing(true)
    try {
      const saved = await unifiedService.update(postId, { data, state })
      await unifiedService.publish(postId, override, redirect)
      setInsightsAt((n) => n + 1)
      setPost(saved)
      setData(saved.data || {})
      setIsLive(true)
      setLiveRow(saved)
      setGateRefusal(null)
      setPublishPreviewOpen(false)
      toast.success(t("content.publishSuccess"))
    } catch (error) {
      if (await openConflict(error)) return
      // SH955 — the report says what happened; `at` says where, so the field is marked too.
      const problem = reportRefusal(error, t("common.error"), refusalActions)
      // SH998 — a gate is the one refusal this dialog can do something about, so it stays open and
      // asks for the reason instead of closing over a toast. Matched on the problem's `type`, never
      // on its sentence: the sentence is for the curator and is meant to be improvable.
      if (problem.type?.endsWith("publish-gate-failed")
        || problem.type?.endsWith("waiver-needs-a-reason")) {
        setGateRefusal(headlineOf(problem, t("common.error")))
        return
      }
      setPublishPreviewOpen(false)
      const field = fieldOf(problem)
      setRefusal(field ? { field, message: headlineOf(problem, t("common.error")) } : undefined)
    } finally {
      setIsPublishing(false)
    }
  }

  /** Take the page off the site, leaving this draft to go on being edited (SH654). */
  async function handleUnpublish() {
    setIsPublishing(true)
    try {
      await unifiedService.unpublish(postId)
      setIsLive(false)
      setLiveRow(null)
      toast.success(t("content.unpublishSuccess"))
    } catch (error) {
      // SH955 — the report says what happened; `at` says where, so the field is marked too.
      const problem = reportRefusal(error, t("common.error"), refusalActions)
      const field = fieldOf(problem)
      setRefusal(field ? { field, message: headlineOf(problem, t("common.error")) } : undefined)
    } finally {
      setIsPublishing(false)
    }
  }

  if (!post) {
    return (
      <>
        <PageHero grammar="work" title={t("content.editPost")} urlBase={ROUTES.CONTENT_ROOT} />
        {/*
          SH1049 — three states, not two. `LoadProvider` shows the skeleton while
          `checkIsNotUndefined` is undefined and the blank slate with "try again" once `error` is
          set, so a failed read stops being indistinguishable from a slow one. The spinner this
          replaced ran for ever on a 500, because the load settles every promise and had nowhere
          to put the rejection.
        */}
        <div>
          <LoadProvider
            checkIsNotUndefined={loadFailure ? null : undefined}
            error={loadFailure
              ? headlineOf(problemOf(loadFailure), t("content.postLoadFailed"))
              : null}
            tryAgainUrl={`${ROUTES.CONTENT_ROOT}/folder/${parentFolderId ?? ""}`}
          >
            <span>{t("common.loading")}</span>
          </LoadProvider>
        </div>
      </>
    )
  }

  return (
    <>
      {/*
        SH901 — the header and the save bar are one call, because `BentoFormHero` owns both halves
        of the morph and the authoring contract forbids composing them by hand.
      */}
      <PageHero grammar="work"
        eyebrow={t("content.postLabel")}
        /*
         * SH1204 — the page is the post, so the title is its title, and the line under it says what
         * the curator needs before editing: its type, its language and which row this is. "Editar
         * Post" on every post said nothing a reader did not already know.
         */
        title={post.title || t("content.editPost")}
        subtitle={[postTypeName, post.locale?.toUpperCase(),
          // SH1324 — whether the site serves it, not which row is open: a live page reads Published.
          t(lifecycleKey(isLive, draftDiffers(post, liveRow)))].filter(Boolean).join(" · ")}
        trail={trail}
        urlBase={backUrl}
        saveTitle={post.title || t("content.editPost")}
        onCancel={navigateBack}
        loading={isLoading}
        titleMissing={!post.title}
        // SH1011 — the design system has rendered this cue since it shipped, on a prop this page
        // was the only one not passing.
        dirty={dirty}
        saveBadges={
            <>
              {/*
                SH1011 — what the draft on the server knows, in the bar where the curator is
                deciding whether they still have to press Save. "Stopped" is said out loud rather
                than by the absence of "saved": a silent autosave that has given up is worse than
                one that never ran.
              */}
              {autosave.status === "saving" && (
                <BadgeColorful text={t("content.autosaveSaving")} className="text-xs" />
              )}
              {autosave.status === "saved" && (
                <BadgeColorful text={t("content.autosaveSaved")} className="text-xs" />
              )}
              {autosave.status === "stopped" && (
                <BadgeColorful text={t("content.autosaveStopped")} className="text-xs" />
              )}
              <BadgeColorful text={postTypeName || ""} className="text-xs" />
              <BadgeColorful
                text={state}
                className="text-xs"
                prefix={<Sparkles className="h-3 w-3" />}
              />
              {/*
                `state` says which row is being edited, not whether the site is serving
                one. Without this, Unpublish appears beside a badge reading DRAFT and the
                button's own precondition is invisible (SH654).
              */}
              {isLive && state === "DRAFT" && (
                <Badge variant="secondary" className="text-xs">
                  {t("content.publishedLive")}
                </Badge>
              )}
              <Badge variant="secondary" className="text-xs">
                {visibleFieldCount} {t("content.fields").toLowerCase()}
              </Badge>
            </>
          }
        /*
          Three controls, flush right: the two a curator reaches for while writing (preview,
          language) and one menu for everything that commits or leaves. Ten
          equal-weight outlined buttons gave no primary at all.
        */
        actions={
            <>
              {hasSite && (
                // SH1204 — a neutral secondary: the Actions menu is the page's one primary.
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  disabled={isPreviewing}
                  title={t("content.previewDraft")}
                  onClick={handlePreview}
                >
                  <Eye className="size-4" />
                  {t("content.preview")}
                </Button>
              )}
              <PostTranslationsMenu postId={postId} siteId={post.shSite?.id ?? post.shFolder?.shSite?.id}
                currentLocale={post.locale} />
              {publishHint && (
                <span className="text-xs text-muted-foreground max-w-xs">
                  {t("content.publishNotAllowed", { reason: publishHint })}
                </span>
              )}
              {/*
                One filled "Actions" menu carries every commit verb, grouped by what they touch:
                publish and save first, then the page (history, address, schedule), leaving the
                form, and taking the page down — kept last and tinted, since it is the one visitors
                notice. Autosave (SH1011) is why saving can sit one click deep.
              */}
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <GradientButton type="button" size="sm" loading={isLoading || isPublishing}>
                    {t("content.actions")}
                    <ChevronDown className="size-4" />
                  </GradientButton>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" className="w-60">
                    {/*
                      Publish needs a DRAFT row to promote — `POST /{id}/publish` answers
                      "Draft not found" without one — so a post that only ever existed as
                      PUBLISHED is not offered an item that cannot work. Saving it already
                      writes the live row, so nothing is out of reach (SH654).
                    */}
                    {state === "DRAFT" && (
                      <DropdownMenuItem
                        data-action-id="post.publish"
                        className="font-medium"
                        disabled={isLoading || publishHint !== null}
                        title={publishHint ?? undefined}
                        onClick={() => setPublishPreviewOpen(true)}
                      >
                        <Upload className="size-4" />
                        {t("content.publish")}
                      </DropdownMenuItem>
                    )}
                    <DropdownMenuItem data-action-id="post.save" disabled={isLoading}
                      onClick={() => handleSave(false)}>
                      <Save className="size-4" />
                      {t("common.save")}
                    </DropdownMenuItem>
                    <DropdownMenuItem data-action-id="post.saveAndClose" disabled={isLoading}
                      onClick={() => handleSave(true)}>
                      <LogOut className="size-4" />
                      {t("content.saveAndClose")}
                    </DropdownMenuItem>
                    <DropdownMenuItem data-action-id="post.schedule" onClick={() => setScheduleOpen(true)}>
                      <Clock className="size-4" />
                      {t("content.schedule")}
                    </DropdownMenuItem>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem data-action-id="post.history" onClick={() => setVersionsOpen(true)}>
                      <History className="size-4" />
                      {t("content.versionHistory")}
                    </DropdownMenuItem>
                    {/*
                      SH1132 — a rename a curator can do. Only where the page has a URL to change: a
                      post with none has nothing to move FROM, which the service refuses rather than
                      guesses.
                    */}
                    {post.furl && (
                      <DropdownMenuItem data-action-id="post.move" onClick={() => setRenameOpen(true)}>
                        <Link2 className="size-4" />
                        {t("content.renameTitle")}
                      </DropdownMenuItem>
                    )}
                    <DropdownMenuSeparator />
                    <DropdownMenuItem onClick={navigateBack}>
                      <X className="size-4" />
                      {t("content.closeWithoutSaving")}
                    </DropdownMenuItem>
                    {isLive && (
                      <>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem
                          data-action-id="post.unpublish"
                          variant="destructive"
                          disabled={isLoading || isPublishing || publishHint !== null}
                          title={publishHint ?? undefined}
                          onClick={handleUnpublish}
                        >
                          <EyeOff className="size-4" />
                          {t("content.unpublish")}
                        </DropdownMenuItem>
                      </>
                    )}
                  </DropdownMenuContent>
                </DropdownMenu>
            </>
          }
      />
      <div className="space-y-6">

        {/*
          SH1011 — the offer, never the restore. It says when the buffer was kept, because "restore
          what you were typing" is a different decision depending on whether that was a minute ago
          or last Tuesday.
        */}
        {offered && (
          <div className="flex flex-wrap items-center gap-3 rounded-md border border-dashed px-3 py-2 text-sm">
            <span>{t("content.bufferedOffer", {
              when: new Date(offered.at).toLocaleString(),
            })}</span>
            <GradientButton
              type="button"
              size="sm"
              variant="outline"
              onClick={() => {
                setData((offered.data ?? {}) as Record<string, unknown>)
                setOffered(null)
              }}
            >
              {t("content.bufferedRestore")}
            </GradientButton>
            <GradientButton
              type="button"
              size="sm"
              variant="ghost"
              onClick={() => {
                void clearBuffer(postId)
                setOffered(null)
              }}
            >
              {t("content.bufferedDiscard")}
            </GradientButton>
          </div>
        )}

        {/*
          SH1094 — the form and what it changes, side by side (VDS173). The left pane is what a
          curator writes: who else is working, the fields, and the history of the post. The right
          pane is what they read while writing: the page as it would look, its lint and digest,
          and the notes, which are written while reading the page they are about. The ratio a
          curator drags to is kept per viewer; at phone width the two become one with a switch,
          and neither unmounts, so nothing typed is lost to looking at the preview.
        */}
        <BentoSplitPage
          storageKey="shio.post-editor.split"
          primaryLabel={t("content.fields")}
          secondaryLabel={t("insights.previewSection")}
          primary={<div className="space-y-6">
        {/*
          SH1008 — above the fields, because the point of it is to be read BEFORE anyone types.
          It refuses nothing: the fields, Save and Publish behave exactly as they did, and a save
          that collides still meets SH985's guard.
        */}
        <WorkingLine objectId={postId} />

        {/*
          The header is new, and it is not decoration. The adapter's bento branch is entered
          **only when it recognises a header child** — a section built any other way falls
          through to the console card so its content cannot go missing silently. The card here
          previously carried an empty `CardHeader`, so keeping it header-less would have made
          the whole swap a no-op on the one page it matters most: SH901 would wrap this page in
          a provider and get a console card back, with every source scan still reporting the
          adapter adopted. It says `fields` and not the post's title, because identity belongs
          to the hero and this is the section, not the page.
        */}
        <AdaptiveSectionCard>
          <AdaptiveSectionCard.StaticHeader icon={IconFileText} title={t("content.fields")} />
          <AdaptiveSectionCard.Content>
            {/*
              A post whose data holds a field its post-type does not declare (SH176). The
              form cannot render it, so it is named here rather than hidden: hiding a stored
              value is how content gets lost, and until SH168 refuses such a write it will
              keep arriving. Saving preserves it — `data` is edited whole.
            */}
            {post.undeclaredFields && post.undeclaredFields.length > 0 && (
              <p className="mb-4 rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-800 dark:border-amber-700 dark:bg-amber-950/30 dark:text-amber-300">
                {t("content.undeclaredFields", { fields: post.undeclaredFields.join(", ") })}
              </p>
            )}
            {schema.length === 0 ? (
              <div className="space-y-3">
                <p className="text-sm text-muted-foreground">
                  {t("content.postTypeHasNoFields", { name: postTypeName ?? "" })}
                </p>
                {post.shPostType?.name && (
                  <GradientButton
                    type="button"
                    size="sm"
                    variant="outline"
                    onClick={() => navigate(`${ROUTES.POST_TYPE_ROOT}/${post.shPostType?.name}`)}
                  >
                    {t("content.editPostType")}
                  </GradientButton>
                )}
              </div>
            ) : (
              <PostFormFields
                schema={schema}
                data={data}
                onDataChange={setData}
                postId={postId}
                postTypeName={postTypeName}
                problem={refusal}
                onAsk={agentAvailable ? setAsking : undefined}
              />
            )}
          </AdaptiveSectionCard.Content>
        </AdaptiveSectionCard>
          </div>}
          secondary={<div className="space-y-6">
        {/*
          SH1014 — the page as it would look, from the values on screen. The preview token still
          mints a shareable URL; this answers the question that had no answer at all, which is what
          the change looks like BEFORE committing to it.
        */}
        <AdaptiveSectionCard>
          <AdaptiveSectionCard.StaticHeader icon={IconEye} title={t("insights.previewSection")} />
          <AdaptiveSectionCard.Content>
            {usedOn.length > 0 && (
              <p className="mb-3 text-xs text-muted-foreground">
                {t("insights.previewUsedOn")}{" "}
                {usedOn.map((page, index) => (
                  <span key={page.id}>
                    {index > 0 && ", "}
                    <Link to={`${ROUTES.CONSOLE}/content/post/${page.id}`} className="font-medium text-foreground underline-offset-4 hover:underline">
                      {page.title ?? page.address}
                    </Link>
                  </span>
                ))}
              </p>
            )}
            <PostLivePreview address={`id:${postId}`} data={data} revision={insightsAt}
              renderable={renderable} on={usedOn[0]?.address} />
          </AdaptiveSectionCard.Content>
        </AdaptiveSectionCard>

        {/*
          SH1013 — the lint and the page digest the instance already computes, on the page they are
          about. Addressed by `id:`, the escape hatch SH172 kept for a console that holds an id
          rather than a friendly URL.
        */}
        {/*
          SH1003 — the corrections on this page, beside it rather than in a dialog: a note is
          written WHILE reading the page it is about, and a dialog would hide the thing being
          corrected behind the box for correcting it.
          SH1005 — "who changed this, and when" is the question every CMS is asked, and answering
          it meant leaving the page and scanning an instance-wide log for your own post.
          The three share one card, one tab each, so the preview above them stays in view.
        */}
        <AdaptiveSectionCard>
          {/* SH902 — a section card with no header renders a console card in both eras. */}
          <AdaptiveSectionCard.StaticHeader icon={IconMessage} title={t("content.reviewSection")} />
          <AdaptiveSectionCard.Content>
            {/* SH1336 — `?tab=insights` opens on the findings, which is where home's failing-check row leads. */}
            <Tabs defaultValue={searchParams.get("tab") === "insights" ? "insights" : "notes"}>
              <TabsList className="mb-4">
                <TabsTrigger value="notes" className="gap-1.5">
                  <IconMessage className="size-3.5" />
                  {t("note.section")}
                </TabsTrigger>
                <TabsTrigger value="history" className="gap-1.5">
                  <IconHistory className="size-3.5" />
                  {t("activity.section")}
                </TabsTrigger>
                <TabsTrigger value="insights" className="gap-1.5">
                  <IconSparkles className="size-3.5" />
                  {t("insights.section")}
                </TabsTrigger>
              </TabsList>
              <TabsContent value="notes">
                <NoteRail objectId={postId} />
              </TabsContent>
              <TabsContent value="history">
                <PostHistoryPanel objectId={postId} onCompare={() => setVersionsOpen(true)} />
              </TabsContent>
              <TabsContent value="insights">
                <PostInsightsPanel
                  address={`id:${postId}`}
                  revision={insightsAt}
                  onGoToField={goToField}
                />
              </TabsContent>
            </Tabs>
          </AdaptiveSectionCard.Content>
        </AdaptiveSectionCard>
          </div>}
        />
      </div>

      {asking && postId && (
        <AskAgentDialog postId={postId} field={asking} onClose={() => setAsking(null)} />
      )}

      <PostVersionsDialog
        postId={postId}
        schema={schema}
        open={versionsOpen}
        setOpen={setVersionsOpen}
        onRestored={(d) => {
          setPost(d)
          setData(d.data || {})
        }}
      />

      <PublishPreviewDialog
        postId={postId}
        open={publishPreviewOpen}
        setOpen={(next) => {
          setPublishPreviewOpen(next)
          if (!next) setGateRefusal(null)
        }}
        onConfirm={handlePublish}
        publishing={isPublishing}
        gateRefusal={gateRefusal}
      />
      <PostRenameDialog
        postId={postId}
        currentUrl={post.furl ?? ""}
        open={renameOpen}
        setOpen={setRenameOpen}
        onRenamed={(url) => setPost((prev) => (prev ? { ...prev, furl: url } : prev))}
      />
      <PostScheduleDialog
        postId={postId}
        publishAt={post.publishScheduledDate}
        unpublishAt={post.unpublishScheduledDate}
        open={scheduleOpen}
        setOpen={setScheduleOpen}
        onScheduled={(d) => setPost(d)}
      />
      <PostConflictDialog
        open={conflict !== undefined}
        onOpenChange={(open) => !open && setConflict(undefined)}
        fields={conflict?.fields ?? []}
        detail={conflict?.detail}
        saving={isLoading}
        onResolve={resolveConflict}
      />
    </>
  )
}
