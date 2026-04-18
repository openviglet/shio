import { PageHeader } from "@/components/page-header"
import { Card, CardContent, CardHeader } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { BadgeColorful } from "@/components/badge-colorful"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { UnifiedPostFormFields } from "@/components/widgets/unified-post-form-fields"
import { IconFileText } from "@tabler/icons-react"
import { Loader2, Save, ShieldCheck, Sparkles } from "lucide-react"
import { useEffect, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { ShPostUnifiedService } from "@/services/post-unified/post-unified.service"
import { ShPostService } from "@/services/post/post.service"
import type { ShPost, ShPostAttr } from "@/models/post/post"
import type { ShPostUnified } from "@/models/post-unified/post-unified"
import { parseSchema } from "@/models/post-unified/post-unified"
import { GradientButton, StickySaveBar, toast } from "@viglet/viglet-design-system"

const unifiedService = new ShPostUnifiedService()
const legacyService = new ShPostService()

export default function PostUnifiedEditPage() {
  const { postId } = useParams() as { postId: string }
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { resetBreadcrumb } = useBreadcrumb()
  const [post, setPost] = useState<ShPostUnified | null>(null)
  const [legacyPost, setLegacyPost] = useState<ShPost | null>(null)
  const [allowPublish, setAllowPublish] = useState(false)
  const [data, setData] = useState<Record<string, unknown>>({})
  const [isLoading, setIsLoading] = useState(false)
  const [parentFolderId, setParentFolderId] = useState<string | null>(null)

  useEffect(() => {
    async function load() {
      let unified: ShPostUnified
      try {
        unified = await unifiedService.get(postId, "DRAFT")
      } catch {
        unified = await unifiedService.get(postId, "PUBLISHED")
      }

      const legacy = await legacyService.get(postId)
      setPost(unified)
      setLegacyPost(legacy.shPost)
      setAllowPublish(legacy.allowPublish)
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

  const schema = useMemo(() => parseSchema(post?.shPostType?.schemaJson), [post])
  const postTypeName = post?.shPostType?.title || post?.shPostType?.name

  const visibleFieldCount = useMemo(
    () => schema.filter((f) => f.widget !== "Hidden" && f.widget !== "Tab").length,
    [schema]
  )

  const navigateBack = () =>
    parentFolderId
      ? navigate(`${ROUTES.CONTENT_ROOT}/object/${parentFolderId}`)
      : navigate(ROUTES.CONTENT_ROOT)

  /**
   * Save path bridges the unified JSON back to the legacy ShPost.
   * For each shPostAttr on the legacy post, overlay its string/array/date
   * value from the current data map. Relator items are preserved as-is.
   * The legacy PUT triggers the server-side sync hook so the unified row
   * is re-projected automatically.
   */
  async function handleSave(close: boolean) {
    if (!legacyPost) return
    setIsLoading(true)
    try {
      const merged: ShPost = {
        ...legacyPost,
        shPostAttrs: (legacyPost.shPostAttrs ?? []).map((attr) => applyDataToAttr(attr, data)),
      }
      await legacyService.update(merged)
      toast.success(t("common.saved"))
      if (close) navigateBack()
      else {
        const updated = await unifiedService.get(postId, "DRAFT").catch(() => unifiedService.get(postId, "PUBLISHED"))
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
                text={post.state}
                className="text-xs"
                prefix={<Sparkles className="h-3 w-3" />}
              />
              {allowPublish && (
                <BadgeColorful
                  text={t("content.publish")}
                  className="text-xs"
                  prefix={<ShieldCheck className="h-3 w-3" />}
                />
              )}
              <Badge variant="secondary" className="text-xs">
                {visibleFieldCount} {t("content.fields").toLowerCase()}
              </Badge>
            </>
          }
          actions={
            <>
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
            {schema.length === 0 ? (
              <p className="text-sm text-muted-foreground">
                Schema JSON is empty for this post type. Run the unified migration (
                <code>POST /api/v2/migration/unified/run</code>) to populate it.
              </p>
            ) : (
              <UnifiedPostFormFields
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
    </>
  )
}

function applyDataToAttr(attr: ShPostAttr, data: Record<string, unknown>): ShPostAttr {
  const fieldName = attr.shPostTypeAttr?.name
  if (!fieldName || !(fieldName in data)) return attr

  const value = data[fieldName]
  const widget = attr.shPostTypeAttr?.shWidget?.name

  if (value == null) {
    return { ...attr, strValue: "", arrayValue: [], dateValue: "" }
  }

  if (Array.isArray(value) && value.length > 0 && typeof value[0] === "object") {
    // Relator — preserve legacy children rather than reconstructing them
    return attr
  }

  if (Array.isArray(value)) {
    return { ...attr, arrayValue: (value as unknown[]).map(String) }
  }

  if (widget === "Date") {
    return { ...attr, dateValue: String(value) }
  }

  return { ...attr, strValue: String(value) }
}
