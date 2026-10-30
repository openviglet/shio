import { PageHero } from "@/components/page-hero"
import { AdaptiveSectionCard } from "@viglet/viglet-design-system/bento"
import { Badge } from "@/components/ui/badge"
import { BadgeColorful } from "@/components/badge-colorful"
import { ROUTES } from "@/app/routes.const"
import { PostFormFields } from "@/components/widgets/post-form-fields"
import { IconFileText } from "@tabler/icons-react"
import { Save, Upload } from "lucide-react"
import { useQuery } from "@tanstack/react-query"
import { addressKey } from "@/lib/query-keys"
import { isCancelled } from "@/lib/read-options"
import { useEffect, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { ShPostTypeService } from "@/services/post-type/post-type.service"
import { ShPostUnifiedService } from "@/services/post-unified/post-unified.service"
import type { ShPostType } from "@/models/post-type/post-type"
import { postFormSchema } from "@/models/post-unified/post-unified"
import { GradientButton, toast } from "@viglet/viglet-design-system"
import { reportProblem } from "@/lib/problem"
import { Spinner } from "@/components/spinner"

const postTypeService = new ShPostTypeService()
const unifiedService = new ShPostUnifiedService()

export default function PostUnifiedNewPage() {
  const { folderId, postTypeName } = useParams() as { folderId: string; postTypeName: string }
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [postType, setPostType] = useState<ShPostType | null>(null)
  const [data, setData] = useState<Record<string, unknown>>({})
  const [isLoading, setIsLoading] = useState(false)

  /*
   * SH1156 — one read per post-type, cancelled on leaving, keyed on the name it reads. `t` was in the
   * dependency list and is not a subject: a translation function says nothing about which type is
   * wanted. This is the same correction SH1150 made to the post-type *editor*, one page over.
   */
  const loaded = useQuery<ShPostType>({
    queryKey: addressKey(`postType:${postTypeName}`),
    queryFn: ({ signal }) => postTypeService.get(postTypeName, { signal }),
    enabled: Boolean(postTypeName),
  })

  // Set during render rather than in an effect (`react-hooks/set-state-in-effect`), once per type.
  const [seeded, setSeeded] = useState<string | null>(null)
  if (loaded.data && seeded !== postTypeName) {
    setSeeded(postTypeName)
    setPostType(loaded.data)
  }

  useEffect(() => {
    // The read had no failure path: a type that would not load left the form with no fields, which a
    // curator would have taken for a type that declares none.
    if (loaded.error && !isCancelled(loaded.error)) reportProblem(loaded.error, t("common.error"))
  }, [loaded.error, t])

  const schema = useMemo(() => postFormSchema(postType), [postType])

  const visibleFieldCount = useMemo(
    () => schema.filter((f) => f.widget !== "Hidden" && f.widget !== "Tab").length,
    [schema]
  )

  // Back returns to the folder the post is being created in, the same place Cancel does.
  const backUrl = `${ROUTES.CONTENT_ROOT}/object/${folderId}`
  const navigateBack = () => navigate(backUrl)

  /**
   * SH1326 — `publish` saves, then opens the editor with its own publish confirmation already
   * asking: one confirmation and one gate for a page, whether it was saved a moment ago or a week.
   */
  async function handleSave(publish = false) {
    if (!postType) return
    setIsLoading(true)
    try {
      const created = await unifiedService.create({
        folderId,
        postType: postType.name,
        data,
        state: "DRAFT",
      })
      toast.success(t("common.saved"))
      // SH1326 — on into the editor of the post just made, where Publish is: returning to the
      // folder left the curator to find the page again before they could put it live.
      navigate(created?.id ? `${ROUTES.CONTENT_ROOT}/post/${created.id}${publish ? "?publish=1" : ""}` : backUrl)
    } catch (error) {
      reportProblem(error, t("common.error"))
    } finally {
      setIsLoading(false)
    }
  }

  if (!postType) {
    return (
      <>
        <PageHero grammar="work" title={t("content.newPost")} urlBase={backUrl} />
        <div className="flex items-center gap-2 text-muted-foreground">
          <Spinner labelled />
          <span>{t("common.loading")}</span>
        </div>
      </>
    )
  }

  const postTypeLabel = postType.title || postType.name

  return (
    <>
      {/* SH901 — one call, for the same 1:2 reason as the editor next door. */}
      {/* SH1326 — the header names the type ("New Page"), and no internal word ("unified"). */}
      <PageHero grammar="work"
        eyebrow={t("content.postLabel")}
        title={t("content.newPostOfType", { type: postTypeLabel })}
        urlBase={backUrl}
        onCancel={navigateBack}
        loading={isLoading}
        saveBadges={
            <>
              <BadgeColorful text={postTypeLabel || ""} className="text-xs" />
              <Badge variant="secondary" className="text-xs">
                {visibleFieldCount} {t("content.fields").toLowerCase()}
              </Badge>
            </>
          }
        actions={
            <>
              <GradientButton type="button" size="sm" loading={isLoading} onClick={() => handleSave()}>
                <Save className="size-4" />
                {t("common.save")}
              </GradientButton>
              <GradientButton type="button" size="sm" variant="outline" disabled={isLoading}
                onClick={() => handleSave(true)}>
                <Upload className="size-4" />
                {t("content.publishNew")}
              </GradientButton>
              <GradientButton type="button" size="sm" variant="ghost" onClick={navigateBack}>
                {t("common.cancel")}
              </GradientButton>
            </>
          }
      />
      <div className="space-y-6">

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
