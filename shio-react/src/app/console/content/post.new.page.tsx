import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Separator } from "@/components/ui/separator"
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
import { toast } from "sonner"

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

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
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
      navigate(`${ROUTES.CONTENT_ROOT}/object/${folderId}`)
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
        <div className="p-6">
          <p className="text-muted-foreground">{t("common.loading")}</p>
        </div>
      </>
    )
  }

  return (
    <>
      <PageHeader title={t("content.newPost")} turIcon={IconFileText} urlBase={ROUTES.CONTENT_ROOT} />
      <div className="p-6">
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle>{t("content.newPost")}</CardTitle>
              <Badge variant="outline">{postType.title || postType.name}</Badge>
            </div>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit} className="space-y-4">
              <PostFormFields attrs={postAttrs} onAttrChange={handleAttrChange} postTypeName={postType?.title || postType?.name} />
              <Separator />
              <div className="flex gap-2 pt-2">
                <Button type="submit" disabled={isLoading}>
                  {isLoading ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Save className="mr-2 h-4 w-4" />}
                  {t("common.save")}
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => navigate(`${ROUTES.CONTENT_ROOT}/object/${folderId}`)}
                >
                  {t("common.cancel")}
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      </div>
    </>
  )
}
