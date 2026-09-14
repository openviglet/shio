import { PageHero } from "@/components/page-hero"
/*
 * SH892 — the section chrome comes from the adapter, not from a Card. This is the form the
 * adapter exists for: the schema is built at runtime from the post type and the fields are
 * fifteen dynamic widgets rendered through a widget map, each reading a `widgetSettings` value
 * the backend can return as the literal string "null". None of that is touched here and none of
 * it should be — a parallel bento route that rebuilt this page would fork the most defect-prone
 * code in the console. Swapping the wrapper is the whole change.
 *
 * Imported from the bento subpath because that is the only entry the package exports the adapter
 * from; the era classifier in `console-chrome-census.test.mjs` excludes it by name.
 */
import { AdaptiveSectionCard } from "@viglet/viglet-design-system/bento"
import { Badge } from "@/components/ui/badge"
import { BadgeColorful } from "@/components/badge-colorful"
import { ROUTES } from "@/app/routes.const"
import { PostFormFields } from "@/components/widgets/post-form-fields"
import { IconFileText } from "@tabler/icons-react"
import { Clock, Eye, EyeOff, History, Loader2, Save, Sparkles, Upload } from "lucide-react"
import { useEffect, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import axios from "axios"
import { ShPostUnifiedService } from "@/services/post-unified/post-unified.service"
import { ShPreviewTokenService } from "@/services/preview-token/preview-token.service"
import { PostVersionsDialog } from "@/components/post-versions.dialog"
import { PostScheduleDialog } from "@/components/post-schedule.dialog"
import { PostTranslationsMenu } from "@/components/post-translations.menu"
import type { ShPostUnified, ShPostState } from "@/models/post-unified/post-unified"
import { postFormSchema } from "@/models/post-unified/post-unified"
import { GradientButton, toast } from "@viglet/viglet-design-system"
import { reportProblem } from "@/lib/problem"

const unifiedService = new ShPostUnifiedService()
const previewService = new ShPreviewTokenService()

export default function PostUnifiedEditPage() {
  const { postId } = useParams() as { postId: string }
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [post, setPost] = useState<ShPostUnified | null>(null)
  const [state, setState] = useState<ShPostState>("DRAFT")
  const [data, setData] = useState<Record<string, unknown>>({})
  const [isLoading, setIsLoading] = useState(false)
  const [isPreviewing, setIsPreviewing] = useState(false)
  /**
   * A PUBLISHED row exists — i.e. the page is live (SH654). Distinct from `state`, which
   * says which row this form is editing: a post published and then edited has both rows,
   * so `state` is DRAFT while the site is serving it. Deciding Unpublish off `state` would
   * hide the button in exactly the case a curator needs it.
   */
  const [isLive, setIsLive] = useState(false)
  const [isPublishing, setIsPublishing] = useState(false)
  const [parentFolderId, setParentFolderId] = useState<string | null>(null)
  const [versionsOpen, setVersionsOpen] = useState(false)
  const [scheduleOpen, setScheduleOpen] = useState(false)

  useEffect(() => {
    async function load() {
      // Both rows in one round trip. Which one is editable decides `state`, as before;
      // whether the PUBLISHED row exists is the separate fact Unpublish needs (SH654),
      // and the old sequential try/catch could not report it when a draft was found.
      const [draft, published] = await Promise.allSettled([
        unifiedService.get(postId, "DRAFT"),
        unifiedService.get(postId, "PUBLISHED"),
      ])
      const editable = draft.status === "fulfilled" ? draft.value : null
      const live = published.status === "fulfilled" ? published.value : null
      const unified: ShPostUnified | null = editable ?? live
      if (!unified) return
      const resolvedState: ShPostState = editable ? "DRAFT" : "PUBLISHED"

      setPost(unified)
      setState(resolvedState)
      setIsLive(live !== null)
      setData(unified.data || {})

      // SH933 — the folder is still read for the parent id, which the move dialog and the
      // "up one level" action both need. The trail this block used to build alongside it went
      // with the renderer: the hero names the post and carries its way back.
      if (unified.shFolder) {
        setParentFolderId(unified.shFolder.id)
      }
    }
    load()
  }, [postId])

  const schema = useMemo(() => postFormSchema(post?.shPostType), [post])
  const postTypeName = post?.shPostType?.title || post?.shPostType?.name

  const visibleFieldCount = useMemo(
    () => schema.filter((f) => f.widget !== "Hidden" && f.widget !== "Tab").length,
    [schema]
  )

  const hasSite = Boolean(post?.shFolder?.shSite?.id || post?.shSite?.id)

  const navigateBack = () =>
    parentFolderId
      ? navigate(`${ROUTES.CONTENT_ROOT}/object/${parentFolderId}`)
      : navigate(ROUTES.CONTENT_ROOT)

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
    try {
      const updated = await unifiedService.update(postId, { data, state })
      toast.success(t("common.saved"))
      if (close) {
        navigateBack()
      } else {
        setPost(updated)
        setData(updated.data || {})
      }
    } catch (error) {
      reportProblem(error, t("common.error"))
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
  async function handlePublish() {
    setIsPublishing(true)
    try {
      const saved = await unifiedService.update(postId, { data, state })
      await unifiedService.publish(postId)
      setPost(saved)
      setData(saved.data || {})
      setIsLive(true)
      toast.success(t("content.publishSuccess"))
    } catch (error) {
      reportProblem(error, t("common.error"))
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
      toast.success(t("content.unpublishSuccess"))
    } catch (error) {
      reportProblem(error, t("common.error"))
    } finally {
      setIsPublishing(false)
    }
  }

  if (!post) {
    return (
      <>
        <PageHero icon={IconFileText} title={t("content.editPost")} urlBase={ROUTES.CONTENT_ROOT} />
        <div className="p-6 flex items-center gap-2 text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" />
          <span>{t("common.loading")}</span>
        </div>
      </>
    )
  }

  return (
    <>
      {/*
        SH901 — the header and the save bar are one call, because in bento they are one
        component. `BentoFormHero` owns both halves of the morph and the authoring contract
        forbids composing them by hand; console chrome has no such pairing, so the adapter spans
        a 1:2 mapping. Every badge and every action below is exactly what the console rendered.
      */}
      <PageHero
        icon={IconFileText}
        eyebrow={t("content.postLabel")}
        title={t("content.editPost")}
        urlBase={ROUTES.CONTENT_ROOT}
        saveTitle={post.title || t("content.editPost")}
        onCancel={navigateBack}
        loading={isLoading}
        titleMissing={!post.title}
        saveBadges={
            <>
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
        actions={
            <>
              {hasSite && (
                <GradientButton
                  type="button"
                  size="sm"
                  variant="outline"
                  loading={isPreviewing}
                  onClick={handlePreview}
                >
                  <Eye className="size-4" />
                  {t("content.previewDraft")}
                </GradientButton>
              )}
              <PostTranslationsMenu postId={postId} currentLocale={post.locale} />
              <GradientButton
                type="button"
                size="sm"
                variant="outline"
                onClick={() => setVersionsOpen(true)}
              >
                <History className="size-4" />
                {t("content.versionHistory")}
              </GradientButton>
              <GradientButton
                type="button"
                size="sm"
                variant="outline"
                onClick={() => setScheduleOpen(true)}
              >
                <Clock className="size-4" />
                {t("content.schedule")}
              </GradientButton>
              <GradientButton type="button" size="sm" loading={isLoading} onClick={() => handleSave(false)}>
                <Save className="size-4" />
                {t("common.save")}
              </GradientButton>
              {/*
                Publish needs a DRAFT row to promote — `POST /{id}/publish` answers
                "Draft not found" without one — so a post that only ever existed as
                PUBLISHED is not offered a button that cannot work. Saving it already
                writes the live row, so nothing is out of reach (SH654).
              */}
              {state === "DRAFT" && (
                <GradientButton
                  type="button"
                  size="sm"
                  loading={isPublishing}
                  disabled={isLoading}
                  onClick={handlePublish}
                >
                  <Upload className="size-4" />
                  {t("content.publish")}
                </GradientButton>
              )}
              {isLive && (
                <GradientButton
                  type="button"
                  size="sm"
                  variant="outline"
                  disabled={isLoading || isPublishing}
                  onClick={handleUnpublish}
                >
                  <EyeOff className="size-4" />
                  {t("content.unpublish")}
                </GradientButton>
              )}
              <GradientButton type="button" size="sm" variant="outline" disabled={isLoading} onClick={() => handleSave(true)}>
                {t("content.saveAndClose")}
              </GradientButton>
              <GradientButton type="button" size="sm" variant="ghost" onClick={navigateBack}>
                {t("common.cancel")}
              </GradientButton>
            </>
          }
      />
      <div className="p-6 space-y-6">

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
              />
            )}
          </AdaptiveSectionCard.Content>
        </AdaptiveSectionCard>
      </div>

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

      <PostScheduleDialog
        postId={postId}
        publishAt={post.publishScheduledDate}
        unpublishAt={post.unpublishScheduledDate}
        open={scheduleOpen}
        setOpen={setScheduleOpen}
        onScheduled={(d) => setPost(d)}
      />
    </>
  )
}
