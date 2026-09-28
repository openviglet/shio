import { ArrowLeftRight, Bot, CheckCheck, Circle, Save } from "lucide-react"
import { useCallback, useEffect, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { useParams } from "react-router-dom"
import { GradientButton, toast } from "@viglet/viglet-design-system"
import { BentoSplitPage } from "@viglet/viglet-design-system/bento"
import { PageHero } from "@/components/page-hero"
import { Panel } from "@/components/panel"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { PostFormFields } from "@/components/widgets/post-form-fields"
import { ROUTES } from "@/app/routes.const"
import { isCancelled } from "@/lib/read-options"
import { reportProblem } from "@/lib/problem"
import { REVIEWED, copyFromSource, pairedFields, reviewedFields, sourceText } from "@/lib/translate-pane"
import { ShNoteService } from "@/services/note/note.service"
import { postFormSchema } from "@/models/post-unified/post-unified"
import type { ShPostTypeSchemaField, ShPostUnified } from "@/models/post-unified/post-unified"
import { ShPostUnifiedService } from "@/services/post-unified/post-unified.service"
import { sendRequest } from "@/app/system/dock-chat-send"
import { useAgentAvailable } from "@/app/system/use-agent-available"

const service = new ShPostUnifiedService()
const noteService = new ShNoteService()

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
  /** SH1061 — the fields already checked against the source, as notes the agent sees too. */
  const [reviewed, setReviewed] = useState<Set<string>>(new Set())
  /** SH1062 — whether this translation carries an open request to the connected agent. */
  const [requested, setRequested] = useState(false)
  const agentAvailable = useAgentAvailable()

  const load = useCallback((signal?: AbortSignal) => {
    // One read for the pair: the translations list carries every sibling's document, so the source
    // pane costs no second request (the group is what the list is). The row itself is read because
    // SH985's version guard needs it, and the notes are what carry the review marks.
    return Promise.all([
      service.get(postId, "DRAFT"),
      service.translations(postId),
      noteService.of(postId),
    ])
      .then(([row, siblings, notes]) => {
        if (signal?.aborted) return
        setTarget(row)
        setData({ ...(row.data ?? {}) })
        setReviewed(reviewedFields(notes))
        // SH1062 — an open request is the same mark the Languages board shows.
        setRequested(notes.some((note) => !note.resolved && note.kind?.toLowerCase() === "request"))
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

  /**
   * SH1061 — this field has been checked against the source, written where an agent can read it.
   *
   * A note rather than a flag on the row: review state is a statement somebody made, with an
   * author and a time, and the channel for those already exists (SH1003). Unmarking resolves the
   * note, which is the same gesture every other note has.
   */
  const toggleReviewed = async (field: string) => {
    const was = reviewed.has(field)
    try {
      if (was) {
        const notes = await noteService.of(postId)
        const mark = notes.find((note) => !note.resolved && note.fieldPath === field
          && note.kind?.toLowerCase() === REVIEWED)
        if (mark) await noteService.resolve(mark.id)
      } else {
        await noteService.add({
          objectId: postId,
          fieldPath: field,
          kind: REVIEWED,
          body: t("content.translateReviewedBody", { field }),
        })
      }
      setReviewed((current) => {
        const next = new Set(current)
        if (was) next.delete(field)
        else next.add(field)
        return next
      })
    } catch (error) {
      reportProblem(error, t("common.error"))
    }
  }

  /**
   * SH1062 — the board's ask, from the page it would open: a request note on this translation,
   * which already carries the language and the source the agent needs.
   */
  const askAgent = async () => {
    if (!target?.locale) return
    if (await sendRequest(postId, t("content.coverageAskWrite", { locale: target.locale }), t)) {
      setRequested(true)
      toast.success(t("content.coverageAskedMany", { count: 1 }))
    }
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
        title={t("content.translateTitle")}
        subtitle={t("content.translateDescription")}
        urlBase={`${ROUTES.CONTENT_ROOT}/post/${postId}`}
        backLabel={t("content.postLabel")}
      />
      <div className="space-y-3">
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant="outline" className="font-mono text-xs uppercase">
            {source?.locale ?? "—"}
          </Badge>
          <ArrowLeftRight className="size-4 text-muted-foreground" />
          <Badge variant="outline" className="font-mono text-xs uppercase">
            {target?.locale ?? "—"}
          </Badge>
          <div className="ml-auto flex items-center gap-2">
            {requested ? (
              <Badge variant="outline" className="gap-1 text-xs" title={t("content.coverageRequestedHint")}>
                <Bot className="size-3.5" />
                {t("content.coverageRequested")}
              </Badge>
            ) : agentAvailable && source && (
              <Button type="button" size="sm" variant="outline" onClick={askAgent}>
                <Bot className="size-4" />
                {t("content.askAgent")}
              </Button>
            )}
            <GradientButton type="button" size="sm" onClick={save} loading={saving} disabled={!dirty}>
              <Save className="size-4" />
              {t("common.save")}
            </GradientButton>
          </div>
        </div>

        {/* SH1094 — the design system's split (VDS173), the same shape as the post editor's: the
            source read on the left, the translation written on the right, the ratio kept per
            viewer. At phone width it is one pane with a labelled switch, which is what this page
            hand-built before (SH1061), and neither pane unmounts, so switching keeps what was typed. */}
        <BentoSplitPage
          storageKey="shio.translate.split"
          primaryLabel={source?.locale ?? t("content.translateSource")}
          secondaryLabel={target?.locale ?? t("content.translateTarget")}
          primary={
          <Panel contentClassName="p-6">
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
          }
          secondary={
          <Panel contentClassName="p-6">
            <p className="mb-3 text-xs text-muted-foreground">
              {t("content.translateTargetHint", { locale: target?.locale ?? "" })}
            </p>
            {/* SH1061 — the review marks, per field, beside the fields they are about. Written as
                notes, so what a curator ticks here is what an agent reads. */}
            {schema.length > 0 && (
              <div className="mb-3 flex flex-wrap gap-1">
                {schema.map((field) => (
                  <Button key={field.name} type="button" variant="ghost" size="sm"
                    className={`h-6 gap-1 px-2 text-xs ${reviewed.has(field.name)
                      ? "text-green-600 dark:text-green-400" : "text-muted-foreground"}`}
                    onClick={() => toggleReviewed(field.name)}
                    title={t("content.translateReviewedHint")}>
                    {reviewed.has(field.name) ? <CheckCheck className="size-3.5" /> : <Circle className="size-3.5" />}
                    {field.label || field.name}
                  </Button>
                ))}
              </div>
            )}
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
          }
        />
      </div>
    </>
  )
}
