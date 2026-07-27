import { PageHeader } from "@/components/page-header"
import { Card, CardContent, CardHeader } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { BadgeColorful } from "@/components/badge-colorful"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { PostFormFields } from "@/components/widgets/post-form-fields"
import { IconFileText } from "@tabler/icons-react"
import { Clock, Eye, History, Loader2, Save, Sparkles } from "lucide-react"
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
import { GradientButton, StickySaveBar, toast } from "@viglet/viglet-design-system"

const unifiedService = new ShPostUnifiedService()
const previewService = new ShPreviewTokenService()

export default function PostUnifiedEditPage() {
  const { postId } = useParams() as { postId: string }
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { resetBreadcrumb } = useBreadcrumb()
  const [post, setPost] = useState<ShPostUnified | null>(null)
  const [state, setState] = useState<ShPostState>("DRAFT")
  const [data, setData] = useState<Record<string, unknown>>({})
  const [isLoading, setIsLoading] = useState(false)
  const [isPreviewing, setIsPreviewing] = useState(false)
  const [parentFolderId, setParentFolderId] = useState<string | null>(null)
  const [versionsOpen, setVersionsOpen] = useState(false)
  const [scheduleOpen, setScheduleOpen] = useState(false)

  useEffect(() => {
    async function load() {
      let unified: ShPostUnified
      let resolvedState: ShPostState = "DRAFT"
      try {
        unified = await unifiedService.get(postId, "DRAFT")
      } catch {
        unified = await unifiedService.get(postId, "PUBLISHED")
        resolvedState = "PUBLISHED"
      }

      setPost(unified)
      setState(resolvedState)
      setData(unified.data || {})

      const items: { label: string; href?: string }[] = [
        { label: t("content.sites"), href: ROUTES.CONTENT_ROOT },
      ]
      const folder = unified.shFolder
      if (folder) {
        setParentFolderId(folder.id)
        const site = folder.shSite
        if (site) {
          items.push({ label: site.name, href: `${ROUTES.CONTENT_ROOT}/object/${site.id}` })
        }
        items.push({ label: folder.name, href: `${ROUTES.CONTENT_ROOT}/object/${folder.id}` })
      }
      items.push({ label: unified.title || t("content.editPost") })
      resetBreadcrumb(items)
    }
    load()
  }, [postId, resetBreadcrumb, t])

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
    } catch {
      toast.error(t("common.error"))
    } finally {
      setIsLoading(false)
    }
  }

  if (!post) {
    return (
      <>
        <PageHeader title={t("content.editPost")} turIcon={IconFileText} urlBase={ROUTES.CONTENT_ROOT} />
        <div className="p-6 flex items-center gap-2 text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" />
          <span>{t("common.loading")}</span>
        </div>
      </>
    )
  }

  return (
    <>
      <PageHeader title={t("content.editPost")} turIcon={IconFileText} urlBase={ROUTES.CONTENT_ROOT} />
      <div className="p-6 space-y-6">
        <StickySaveBar
          title={post.title || t("content.editPost")}
          badges={
            <>
              <BadgeColorful text={postTypeName || ""} className="text-xs" />
              <BadgeColorful
                text={state}
                className="text-xs"
                prefix={<Sparkles className="h-3 w-3" />}
              />
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
              <GradientButton type="button" size="sm" variant="outline" disabled={isLoading} onClick={() => handleSave(true)}>
                {t("content.saveAndClose")}
              </GradientButton>
              <GradientButton type="button" size="sm" variant="ghost" onClick={navigateBack}>
                {t("common.cancel")}
              </GradientButton>
            </>
          }
        />

        <Card>
          <CardHeader className="pb-2" />
          <CardContent>
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
          </CardContent>
        </Card>
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
