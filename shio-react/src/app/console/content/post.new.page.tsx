import { PageHeader } from "@/components/page-header"
import { Card, CardContent, CardHeader } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { BadgeColorful } from "@/components/badge-colorful"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { PostFormFields } from "@/components/widgets/post-form-fields"
import { IconFileText } from "@tabler/icons-react"
import { Loader2, Save } from "lucide-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { ShPostTypeService } from "@/services/post-type/post-type.service"
import { ShPostService } from "@/services/post/post.service"
import type { ShPostType } from "@/models/post-type/post-type"
import type { ShPost, ShPostAttr } from "@/models/post/post"
import { GradientButton, StickySaveBar, toast } from "@viglet/viglet-design-system"

const postTypeService = new ShPostTypeService()
const postService = new ShPostService()

export default function PostNewPage() {
  const { folderId, postTypeId } = useParams() as { folderId: string; postTypeId: string }
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { resetBreadcrumb } = useBreadcrumb()
  const [postType, setPostType] = useState<ShPostType | null>(null)
  const [postAttrs, setPostAttrs] = useState<ShPostAttr[]>([])
  const [isLoading, setIsLoading] = useState(false)

  useEffect(() => {
    resetBreadcrumb([
      { label: t("content.sites"), href: ROUTES.CONTENT_ROOT },
      { label: t("content.newPost") },
    ])
    postTypeService.get(postTypeId).then((data) => {
      setPostType(data)
      // Build ShPostAttr[] from ShPostTypeAttr[] with empty values
      const attrs: ShPostAttr[] = (data.shPostTypeAttrs ?? []).map((pta) => ({
        id: "",
        strValue: "",
        arrayValue: [],
        dateValue: "",
        intValue: 0,
        shPostTypeAttr: {
          id: pta.id,
          name: pta.name,
          label: pta.label,
          description: pta.description,
          ordinal: pta.ordinal,
          required: pta.required,
          isSummary: pta.isSummary,
          isTitle: pta.isTitle,
          shWidget: pta.shWidget,
          widgetSettings: pta.widgetSettings,
        },
        shChildrenRelatorItems: [],
        shChildrenPostAttrs: [],
      }))
      setPostAttrs(attrs)
    })
  }, [postTypeId, folderId, resetBreadcrumb, t])

  const handleAttrChange = (index: number, updatedAttr: ShPostAttr) => {
    setPostAttrs((prev) => prev.map((attr, i) => (i === index ? updatedAttr : attr)))
  }

  const navigateBack = () => navigate(`${ROUTES.CONTENT_ROOT}/object/${folderId}`)

  async function handleSave() {
    if (!postType) return
    setIsLoading(true)
    try {
      const titleAttr = postAttrs.find((a) => a.shPostTypeAttr?.isTitle === 1)
      const summaryAttr = postAttrs.find((a) => a.shPostTypeAttr?.isSummary === 1)

      const post: Partial<ShPost> = {
        title: titleAttr?.strValue ?? postAttrs[0]?.strValue ?? "",
        summary: summaryAttr?.strValue ?? "",
        shPostType: { id: postType.id, name: postType.name, title: postType.title },
        shFolder: { id: folderId, name: "" },
        shPostAttrs: postAttrs,
      }

      await postService.create(post)
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
        <PageHeader title={t("content.newPost")} turIcon={IconFileText} urlBase={ROUTES.CONTENT_ROOT} />
        <div className="p-6 flex items-center gap-2 text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" />
          <span>{t("common.loading")}</span>
        </div>
      </>
    )
  }

  const postTypeName = postType.title || postType.name
  const fieldCount = postAttrs.filter(
    (a) => a.shPostTypeAttr?.shWidget?.name !== "Hidden"
  ).length

  return (
    <>
      <PageHeader title={t("content.newPost")} turIcon={IconFileText} urlBase={ROUTES.CONTENT_ROOT} />
      <div className="p-6 space-y-6">
        <StickySaveBar
          title={t("content.newPost")}
          badges={
            <>
              <BadgeColorful text={postTypeName || ""} className="text-xs" />
              <Badge variant="secondary" className="text-xs">
                {fieldCount} {t("content.fields").toLowerCase()}
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

        {/* Form card */}
        <Card>
          <CardHeader className="pb-2" />
          <CardContent>
            <PostFormFields
              attrs={postAttrs}
              onAttrChange={handleAttrChange}
              postTypeName={postTypeName}
            />
          </CardContent>
        </Card>
      </div>
    </>
  )
}
