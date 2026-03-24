import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { Badge } from "@/components/ui/badge"
import { Separator } from "@/components/ui/separator"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { IconFileText } from "@tabler/icons-react"
import { Loader2, Save } from "lucide-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { ShPostTypeService } from "@/services/post-type/post-type.service"
import { ShPostService } from "@/services/post/post.service"
import type { ShPostType, ShPostTypeAttr } from "@/models/post-type/post-type"
import type { ShPost } from "@/models/post/post"
import { toast } from "sonner"

const postTypeService = new ShPostTypeService()
const postService = new ShPostService()

export default function PostNewPage() {
  const { folderId, postTypeId } = useParams() as { folderId: string; postTypeId: string }
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { resetBreadcrumb } = useBreadcrumb()
  const [postType, setPostType] = useState<ShPostType | null>(null)
  const [formValues, setFormValues] = useState<Record<string, string>>({})
  const [isLoading, setIsLoading] = useState(false)

  useEffect(() => {
    resetBreadcrumb([
      { label: t("content.sites"), href: ROUTES.CONTENT_ROOT },
      { label: t("content.newPost") },
    ])
    postTypeService.get(postTypeId).then((data) => {
      setPostType(data)
      const defaults: Record<string, string> = {}
      if (data.shPostTypeAttrs) {
        for (const attr of data.shPostTypeAttrs) {
          defaults[attr.id] = ""
        }
      }
      setFormValues(defaults)
    })
  }, [postTypeId, resetBreadcrumb, t])

  const handleFieldChange = (attrId: string, value: string) => {
    setFormValues((prev) => ({ ...prev, [attrId]: value }))
  }

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (!postType) return
    setIsLoading(true)
    try {
      const attrs = postType.shPostTypeAttrs?.map((attr) => ({
        id: undefined as unknown as string,
        strValue: formValues[attr.id] ?? "",
        arrayValue: [],
        dateValue: "",
        intValue: 0,
        shPostTypeAttr: {
          id: attr.id,
          name: attr.name,
          label: attr.label,
          description: attr.description,
          ordinal: attr.ordinal,
          required: attr.required,
          isSummary: attr.isSummary,
          isTitle: attr.isTitle,
          shWidget: attr.shWidget,
        },
      })) ?? []

      const titleAttr = postType.shPostTypeAttrs?.find((a) => a.isTitle === 1)
      const summaryAttr = postType.shPostTypeAttrs?.find((a) => a.isSummary === 1)

      const post: Partial<ShPost> = {
        title: titleAttr ? formValues[titleAttr.id] ?? "" : formValues[Object.keys(formValues)[0]] ?? "",
        summary: summaryAttr ? formValues[summaryAttr.id] ?? "" : "",
        shPostType: { id: postType.id, name: postType.name, title: postType.title },
        shPostAttrs: attrs,
      }

      await postService.create(post)
      toast.success(t("common.save"))
      navigate(`${ROUTES.CONTENT_ROOT}/object/${folderId}`)
    } finally {
      setIsLoading(false)
    }
  }

  const renderAttrField = (attr: ShPostTypeAttr) => {
    const widgetName = attr.shWidget?.name?.toLowerCase() ?? ""
    const isTextArea = widgetName.includes("textarea") || widgetName.includes("html")

    return (
      <div key={attr.id} className="space-y-2">
        <Label>
          {attr.label || attr.name}
          {attr.required === 1 && <span className="text-destructive ml-1">*</span>}
        </Label>
        {isTextArea ? (
          <Textarea
            value={formValues[attr.id] ?? ""}
            onChange={(e) => handleFieldChange(attr.id, e.target.value)}
            rows={4}
          />
        ) : (
          <Input
            value={formValues[attr.id] ?? ""}
            onChange={(e) => handleFieldChange(attr.id, e.target.value)}
          />
        )}
      </div>
    )
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
              {postType.shPostTypeAttrs && postType.shPostTypeAttrs.length > 0 ? (
                <>
                  {postType.shPostTypeAttrs
                    .sort((a, b) => a.ordinal - b.ordinal)
                    .map((attr) => renderAttrField(attr))}
                </>
              ) : (
                <p className="text-sm text-muted-foreground">{t("common.noResults")}</p>
              )}
              <Separator />
              <div className="flex gap-2 pt-4">
                <Button type="submit" disabled={isLoading}>
                  {isLoading ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Save className="mr-2 h-4 w-4" />}
                  {t("common.save")}
                </Button>
                <Button type="button" variant="outline" onClick={() => navigate(-1)}>
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
