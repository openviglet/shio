import { ArrowLeftRight, Languages, Save } from "lucide-react"
import { useCallback, useEffect, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { useParams } from "react-router-dom"
import { GradientButton, toast } from "@viglet/viglet-design-system"
import { PageHero } from "@/components/page-hero"
import { Panel } from "@/components/panel"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { PostFormFields } from "@/components/widgets/post-form-fields"
import { ROUTES } from "@/app/routes.const"
import { isCancelled } from "@/lib/read-options"
import { reportProblem } from "@/lib/problem"
import { copyFromSource, pairedFields, sourceText } from "@/lib/translate-pane"
import { postFormSchema } from "@/models/post-unified/post-unified"
import type { ShPostTypeSchemaField, ShPostUnified } from "@/models/post-unified/post-unified"
import { ShPostUnifiedService } from "@/services/post-unified/post-unified.service"

const service = new ShPostUnifiedService()

/**
 * SH1061 — the translation workspace: the source on one side, the translation on the other.
 *
 * Before this, the whole translation interface was a menu that navigated away: a translator read
 * the English page, went to the Portuguese one, and worked from memory or a second browser tab.
 * The two languages were never on screen together, which is the screen every localization tool
 * treats as the heart of the feature.
 *
 * The panes are aligned by the post-type's own field order, so the two sides say the same thing in
 * the same place, and each field carries a copy-from-source for the values a translation keeps
 * verbatim (a product name, a URL, a number). Saves land in the target's DRAFT; publishing stays
 * the separate, explicit gesture it is everywhere else (P7).
 */
export default function TranslatePage() {
  const { postId } = useParams() as { postId: string }
  const { t } = useTranslation()
  const [target, setTarget] = useState<ShPostUnified | null>(null)
  const [source, setSource] = useState<ShPostUnified | null>(null)
  const [data, setData] = useState<Record<string, unknown>>({})
  const [saving, setSaving] = useState(false)
  const [dirty, setDirty] = useState(false)

  const load = useCallback((signal?: AbortSignal) => {
    // One read for the pair: the translations list carries every sibling's document, so the source
    // pane costs no second request (the group is what the list is).
    return Promise.all([service.get(postId, "DRAFT"), service.translations(postId)])
      .then(([row, siblings]) => {
        if (signal?.aborted) return
        setTarget(row)
        setData({ ...(row.data ?? {}) })
        const group = row.translationGroupId
        setSource(siblings.find((sibling) => sibling.id === group && sibling.id !== row.id) ?? null)
      })
      .catch((reason) => {
        if (isCancelled(reason)) return
        reportProblem(reason, t("common.error"))
      })
  }, [postId, t])

  useEffect(() => {
    const abort = new AbortController()
    void load(abort.signal)
    return () => abort.abort()
  }, [load])

  const schema: ShPostTypeSchemaField[] = useMemo(
    () => pairedFields(postFormSchema(target?.shPostType)),
    [target],
  )

  const copyField = (field: string) => {
    setData((current) => copyFromSource(current, source?.data, field))
    setDirty(true)
  }

  const save = async () => {
    setSaving(true)
    try {
      const saved = await service.update(postId, { data, state: "DRAFT" })
      setTarget(saved)
      setDirty(false)
      toast.success(t("content.translateSaved"))
    } catch (error) {
      reportProblem(error, t("common.error"))
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <PageHero
        icon={Languages}
        title={t("content.translateTitle")}
        subtitle={t("content.translateDescription")}
        urlBase={`${ROUTES.CONSOLE}/content`}
      />
      <div className="space-y-3 p-6">
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant="outline" className="font-mono text-xs uppercase">
            {source?.locale ?? "—"}
          </Badge>
          <ArrowLeftRight className="size-4 text-muted-foreground" />
          <Badge variant="outline" className="font-mono text-xs uppercase">
            {target?.locale ?? "—"}
          </Badge>
          <div className="ml-auto">
            <GradientButton type="button" size="sm" onClick={save} loading={saving} disabled={!dirty}>
              <Save className="size-4" />
              {t("common.save")}
            </GradientButton>
          </div>
        </div>

        {/* Two columns where there is room, stacked where there is not: shrinking two panes to
            half a phone gives a translator two unreadable columns instead of one readable one. */}
        <div className="grid gap-4 lg:grid-cols-2">
          <Panel>
            <p className="mb-3 text-xs text-muted-foreground">
              {t("content.translateSourceHint", { locale: source?.locale ?? "" })}
            </p>
            <div className="space-y-4">
              {schema.map((field) => (
                <div key={field.name} className="space-y-1">
                  <div className="flex items-center justify-between gap-2">
                    <Label className="text-xs">{field.label || field.name}</Label>
                    <Button type="button" variant="ghost" size="sm" className="h-6 px-2 text-xs"
                      disabled={!source}
                      onClick={() => copyField(field.name)}
                      title={t("content.translateCopyHint")}>
                      {t("content.translateCopy")}
                    </Button>
                  </div>
                  {/* Read-only by construction rather than by a disabled input: there is no input. */}
                  <p className="whitespace-pre-wrap rounded-md border bg-muted/40 p-2 text-sm">
                    {sourceText(source?.data?.[field.name]) || "—"}
                  </p>
                </div>
              ))}
            </div>
          </Panel>

          <Panel>
            <p className="mb-3 text-xs text-muted-foreground">
              {t("content.translateTargetHint", { locale: target?.locale ?? "" })}
            </p>
            {schema.length > 0 && (
              <PostFormFields
                schema={schema}
                data={data}
                onDataChange={(next) => {
                  setData(next)
                  setDirty(true)
                }}
                postId={postId}
                postTypeName={target?.shPostType?.name}
              />
            )}
          </Panel>
        </div>
      </div>
    </>
  )
}
