import { AdaptivePageHeader } from "@/components/adaptive-page-header"
import { AdaptiveSectionCard } from "@viglet/viglet-design-system/bento"
import { Badge } from "@/components/ui/badge"
import { BadgeColorful } from "@/components/badge-colorful"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { PostFormFields } from "@/components/widgets/post-form-fields"
import { IconFileText } from "@tabler/icons-react"
import { Loader2, Save, Sparkles } from "lucide-react"
import { useEffect, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { ShPostTypeService } from "@/services/post-type/post-type.service"
import { ShPostUnifiedService } from "@/services/post-unified/post-unified.service"
import type { ShPostType } from "@/models/post-type/post-type"
import { postFormSchema } from "@/models/post-unified/post-unified"
import { GradientButton, toast } from "@viglet/viglet-design-system"

const postTypeService = new ShPostTypeService()
const unifiedService = new ShPostUnifiedService()

export default function PostUnifiedNewPage() {
  const { folderId, postTypeName } = useParams() as { folderId: string; postTypeName: string }
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { resetBreadcrumb } = useBreadcrumb()
  const [postType, setPostType] = useState<ShPostType | null>(null)
  const [data, setData] = useState<Record<string, unknown>>({})
  const [isLoading, setIsLoading] = useState(false)

  useEffect(() => {
    resetBreadcrumb([
      { label: t("content.sites"), href: ROUTES.CONTENT_ROOT },
      { label: t("content.newPost") },
    ])
    postTypeService.get(postTypeName).then(setPostType)
  }, [postTypeName, resetBreadcrumb, t])

  const schema = useMemo(() => postFormSchema(postType), [postType])

  const visibleFieldCount = useMemo(
    () => schema.filter((f) => f.widget !== "Hidden" && f.widget !== "Tab").length,
    [schema]
  )

  const navigateBack = () => navigate(`${ROUTES.CONTENT_ROOT}/object/${folderId}`)

  async function handleSave() {
    if (!postType) return
    setIsLoading(true)
    try {
      await unifiedService.create({
        folderId,
        postType: postType.name,
        data,
        state: "DRAFT",
      })
      toast.success(t("common.saved"))
      navigateBack()
    } catch {
      toast.error(t("common.error"))
    } finally {
      setIsLoading(false)
    }
  }

  if (!postType) {
    return (
      <>
        <AdaptivePageHeader icon={IconFileText} title={t("content.newPost")} urlBase={ROUTES.CONTENT_ROOT} />
        <div className="p-6 flex items-center gap-2 text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" />
          <span>{t("common.loading")}</span>
        </div>
      </>
    )
  }

  const postTypeLabel = postType.title || postType.name

  return (
    <>
      {/* SH901 — one call, for the same 1:2 reason as the editor next door. */}
      <AdaptivePageHeader
        icon={IconFileText}
        eyebrow={t("content.postLabel")}
        title={t("content.newPost")}
        urlBase={ROUTES.CONTENT_ROOT}
        onCancel={navigateBack}
        loading={isLoading}
        saveBadges={
            <>
              <BadgeColorful text={postTypeLabel || ""} className="text-xs" />
              <BadgeColorful
                text="unified"
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
              <GradientButton type="button" size="sm" loading={isLoading} onClick={handleSave}>
                <Save className="size-4" />
                {t("common.save")}
              </GradientButton>
              <GradientButton type="button" size="sm" variant="ghost" onClick={navigateBack}>
                {t("common.cancel")}
              </GradientButton>
            </>
          }
      />
      <div className="p-6 space-y-6">

        {/*
          SH901 — the same header rule the editor learned in SH892: the adapter's bento branch
          needs a header child or it falls through to a console card, silently. `content.fields`,
          not the post's title, because identity belongs to the hero.
        */}
        <AdaptiveSectionCard>
          <AdaptiveSectionCard.StaticHeader icon={IconFileText} title={t("content.fields")} />
          <AdaptiveSectionCard.Content>
            {schema.length === 0 ? (
              <p className="text-sm text-muted-foreground">
                This post type has no fields defined.
              </p>
            ) : (
              <PostFormFields
                schema={schema}
                data={data}
                onDataChange={setData}
                postTypeName={postTypeLabel}
              />
            )}
          </AdaptiveSectionCard.Content>
        </AdaptiveSectionCard>
      </div>
    </>
  )
}
