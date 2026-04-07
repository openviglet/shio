import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { PostFormFields } from "@/components/widgets/post-form-fields"
import { IconFileText } from "@tabler/icons-react"
import { Loader2, Save, ShieldCheck } from "lucide-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { ShPostService } from "@/services/post/post.service"
import type { ShPost, ShPostAttr } from "@/models/post/post"
import { toast } from "@openviglet/viglet-design-system"

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

  const navigateBack = () =>
    parentFolderId
      ? navigate(`${ROUTES.CONTENT_ROOT}/object/${parentFolderId}`)
      : navigate(ROUTES.CONTENT_ROOT)

  async function handleSave() {
    if (!post) return
    setIsLoading(true)
    try {
      await service.update(post)
      toast.success(t("common.saved"))
    } catch {
      toast.error(t("common.error"))
    } finally {
      setIsLoading(false)
    }
  }

  async function handleSaveAndClose() {
    if (!post) return
    setIsLoading(true)
    try {
      await service.update(post)
      toast.success(t("common.saved"))
      navigateBack()
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

  const postTypeName = post.shPostType?.title || post.shPostType?.name
  const fieldCount = post.shPostAttrs?.filter(
    (a) => a.shPostTypeAttr?.shWidget?.name !== "Hidden"
  ).length ?? 0

  return (
    <>
      <PageHeader title={t("content.editPost")} turIcon={IconFileText} urlBase={ROUTES.CONTENT_ROOT} />
      <div className="p-6 space-y-6">
        {/* Sticky save bar */}
        <div className="sticky top-0 z-10 bg-orange-50 dark:bg-orange-950/30 border rounded-lg px-4 py-3 flex items-center justify-between gap-4">
          <div className="flex items-center gap-3 min-w-0">
            <h3 className="text-lg font-semibold truncate">
              {post.title || t("content.editPost")}
            </h3>
            <div className="flex items-center gap-1.5 shrink-0">
              <Badge variant="outline" className="text-xs">
                {postTypeName}
              </Badge>
              {allowPublish && (
                <Badge variant="outline" className="text-xs bg-green-50 text-green-700 border-green-200 dark:bg-green-900/20 dark:text-green-400 dark:border-green-800 gap-1">
                  <ShieldCheck className="h-3 w-3" />
                  {t("content.publish")}
                </Badge>
              )}
              <Badge variant="secondary" className="text-xs">
                {fieldCount} {t("content.fields").toLowerCase()}
              </Badge>
            </div>
          </div>
          <div className="flex gap-2 shrink-0">
            <Button type="button" disabled={isLoading} onClick={handleSave}>
              {isLoading ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Save className="mr-2 h-4 w-4" />}
              {t("common.save")}
            </Button>
            <Button type="button" variant="outline" disabled={isLoading} onClick={handleSaveAndClose}>
              {t("content.saveAndClose")}
            </Button>
            <Button type="button" variant="ghost" onClick={navigateBack}>
              {t("common.cancel")}
            </Button>
          </div>
        </div>

        {/* Form card */}
        <Card>
          <CardHeader className="pb-2" />
          <CardContent>
            <PostFormFields
              attrs={post.shPostAttrs ?? []}
              onAttrChange={handleAttrChange}
              postId={postId}
              postTypeName={postTypeName}
            />
          </CardContent>
        </Card>
      </div>
    </>
  )
}
