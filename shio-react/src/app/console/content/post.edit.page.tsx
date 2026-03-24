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
  const [allowPublish, setAllowPublish] = useState(false)
  const [isLoading, setIsLoading] = useState(false)
  const [parentFolderId, setParentFolderId] = useState<string | null>(null)

  useEffect(() => {
    service.get(postId).then((data) => {
      const shPost = data.shPost
      setPost(shPost)
      setAllowPublish(data.allowPublish)

      const items: { label: string; href?: string }[] = [
        { label: t("content.sites"), href: ROUTES.CONTENT_ROOT },
      ]
      const folder = shPost.shFolder
      if (folder) {
        setParentFolderId(folder.id)
        const site = folder.shSite
        if (site) {
          items.push({
            label: site.name,
            href: `${ROUTES.CONTENT_ROOT}/object/${site.id}`,
          })
        }
        items.push({
          label: folder.name,
          href: `${ROUTES.CONTENT_ROOT}/object/${folder.id}`,
        })
      }
      items.push({ label: shPost.title || t("content.editPost") })
      resetBreadcrumb(items)
    })
  }, [postId, resetBreadcrumb, t])

  const handleAttrChange = (index: number, updatedAttr: ShPostAttr) => {
    if (!post) return
    setPost({
      ...post,
      shPostAttrs: post.shPostAttrs.map((attr, i) =>
        i === index ? updatedAttr : attr
      ),
    })
  }

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (!post) return
    setIsLoading(true)
    try {
      await service.update(post)
      toast.success(t("common.saved"))
      if (parentFolderId) {
        navigate(`${ROUTES.CONTENT_ROOT}/object/${parentFolderId}`)
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
              <CardTitle>{post.title || t("content.editPost")}</CardTitle>
              <div className="flex items-center gap-2">
                <Badge variant="outline">{post.shPostType?.title || post.shPostType?.name}</Badge>
                {allowPublish && (
                  <Badge variant="secondary" className="bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400">
                    Can Publish
                  </Badge>
                )}
              </div>
            </div>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit} className="space-y-4">
              <PostFormFields attrs={post.shPostAttrs ?? []} onAttrChange={handleAttrChange} postId={postId} postTypeName={post.shPostType?.title || post.shPostType?.name} />
              <Separator />
              <div className="flex gap-2 pt-2">
                <Button type="submit" disabled={isLoading}>
                  {isLoading ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Save className="mr-2 h-4 w-4" />}
                  {t("common.save")}
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => parentFolderId
                    ? navigate(`${ROUTES.CONTENT_ROOT}/object/${parentFolderId}`)
                    : navigate(ROUTES.CONTENT_ROOT)
                  }
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
