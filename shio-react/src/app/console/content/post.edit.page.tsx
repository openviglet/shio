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
import { ShPostService } from "@/services/post/post.service"
import type { ShPost, ShPostAttr } from "@/models/post/post"
import { toast } from "sonner"

const service = new ShPostService()

export default function PostEditPage() {
  const { postId } = useParams() as { postId: string }
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { resetBreadcrumb } = useBreadcrumb()
  const [post, setPost] = useState<ShPost | null>(null)
  const [isLoading, setIsLoading] = useState(false)

  useEffect(() => {
    service.get(postId).then((data) => {
      setPost(data)
      const items: { label: string; href?: string }[] = [
        { label: t("content.sites"), href: ROUTES.CONTENT_ROOT },
      ]
      if (data.breadcrumb) {
        for (const crumb of data.breadcrumb) {
          items.push({
            label: crumb.name,
            href: `${ROUTES.CONTENT_ROOT}/object/${crumb.id}`,
          })
        }
      }
      items.push({ label: t("content.editPost") })
      resetBreadcrumb(items)
    })
  }, [postId, resetBreadcrumb, t])

  const handleAttrChange = (attrIndex: number, value: string) => {
    if (!post) return
    setPost({
      ...post,
      shPostAttrs: post.shPostAttrs.map((attr, i) =>
        i === attrIndex ? { ...attr, strValue: value } : attr
      ),
    })
  }

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (!post) return
    setIsLoading(true)
    try {
      await service.update(post)
      toast.success(t("common.save"))
      navigate(-1)
    } finally {
      setIsLoading(false)
    }
  }

  const renderAttrField = (attr: ShPostAttr, index: number) => {
    const widgetName = attr.shPostTypeAttr?.shWidget?.name?.toLowerCase() ?? ""
    const isTextArea = widgetName.includes("textarea") || widgetName.includes("html")

    return (
      <div key={attr.id || index} className="space-y-2">
        <Label>{attr.shPostTypeAttr?.label || attr.shPostTypeAttr?.name || `Field ${index + 1}`}</Label>
        {isTextArea ? (
          <Textarea
            value={attr.strValue ?? ""}
            onChange={(e) => handleAttrChange(index, e.target.value)}
            rows={4}
          />
        ) : (
          <Input
            value={attr.strValue ?? ""}
            onChange={(e) => handleAttrChange(index, e.target.value)}
          />
        )}
      </div>
    )
  }

  if (!post) {
    return (
      <>
        <PageHeader title={t("content.editPost")} turIcon={IconFileText} urlBase={ROUTES.CONTENT_ROOT} />
        <div className="p-6">
          <p className="text-muted-foreground">{t("common.loading")}</p>
        </div>
      </>
    )
  }

  return (
    <>
      <PageHeader title={t("content.editPost")} turIcon={IconFileText} urlBase={ROUTES.CONTENT_ROOT} />
      <div className="p-6">
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle>{t("content.editPost")}</CardTitle>
              <Badge variant="outline">{post.shPostType?.title || post.shPostType?.name}</Badge>
            </div>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="space-y-2">
                <Label>{t("content.title")}</Label>
                <Input
                  value={post.title ?? ""}
                  onChange={(e) => setPost({ ...post, title: e.target.value })}
                />
              </div>
              <div className="space-y-2">
                <Label>{t("content.summary")}</Label>
                <Textarea
                  value={post.summary ?? ""}
                  onChange={(e) => setPost({ ...post, summary: e.target.value })}
                  rows={3}
                />
              </div>
              {post.shPostAttrs && post.shPostAttrs.length > 0 && (
                <>
                  <Separator />
                  <h3 className="text-sm font-semibold">{t("content.attributes")}</h3>
                  {post.shPostAttrs
                    .sort((a, b) => (a.shPostTypeAttr?.ordinal ?? 0) - (b.shPostTypeAttr?.ordinal ?? 0))
                    .map((attr, index) => renderAttrField(attr, index))}
                </>
              )}
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
