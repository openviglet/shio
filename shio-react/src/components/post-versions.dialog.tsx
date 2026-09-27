import { useEffect, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { History, RotateCcw } from "lucide-react"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { toast } from "@viglet/viglet-design-system"
import { BentoDiff, BentoVersionRail } from "@viglet/viglet-design-system/bento"
import { CreatedPagePanel } from "@/components/created-page-panel"
import { reportProblem } from "@/lib/problem"
import { isCancelled } from "@/lib/read-options"
import { DRAFT, PUBLISHED, diffFields, railVersions, revisionOf } from "@/lib/version-diff"
import { ShPostUnifiedService } from "@/services/post-unified/post-unified.service"
import { Spinner } from "@/components/spinner"
import type {
  ShPostDiff,
  ShPostTypeSchemaField,
  ShPostUnified,
  ShRevisionRow,
} from "@/models/post-unified/post-unified"

const service = new ShPostUnifiedService()

interface Props {
  postId: string
  schema: ShPostTypeSchemaField[]
  open: boolean
  setOpen: (open: boolean) => void
  /** Called after a successful restore with the reverted draft row. */
  onRestored: (draft: ShPostUnified) => void
}

/**
 * SH702 — the shell. It owns nothing but the open flag, so there is no state to go stale.
 *
 * <p>The body is mounted *inside* `DialogContent`, which Radix unmounts on close, and keyed on the
 * post. Measured before this: with the versions read delayed to 2s, a reopened dialog kept the
 * previous read's summary and its <b>Restaurar versão publicada</b> button for the whole two
 * seconds, above a table that said it was still loading — a restore control acting on a version the
 * dialog was no longer showing. Asserted by `e2e/dialog-remount.check.mjs`.</p>
 */
export function PostVersionsDialog({ postId, schema, open, setOpen, onRestored }: Props) {
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogContent className="sm:max-w-5xl max-h-[85vh] overflow-y-auto">
        <PostVersionsBody
          key={postId}
          postId={postId}
          schema={schema}
          setOpen={setOpen}
          onRestored={onRestored}
        />
      </DialogContent>
    </Dialog>
  )
}

/**
 * SH988 — any two versions, compared by the server and drawn by the package.
 *
 * <p>It used to pick the draft and the published row and render two capped cells per field, so a
 * long body was two walls of text and a page never published showed nothing at all. Now the rail
 * lists the draft, the published page and every captured revision; the reader picks two; the
 * server answers what changed (`GET /post-unified/{id}/diff`); and `BentoDiff` draws it word by
 * word, collapsing what did not move. A page never published compares against nothing, which the
 * package draws whole as additions. Restore puts the older side back into the draft, behind a
 * second press.</p>
 */
function PostVersionsBody({ postId, schema, setOpen, onRestored }: Omit<Props, "open">) {
  const { t } = useTranslation()
  const [rows, setRows] = useState<ShPostUnified[]>([])
  const [revisions, setRevisions] = useState<ShRevisionRow[]>([])
  const [pair, setPair] = useState<string[]>([PUBLISHED, DRAFT])
  const [diff, setDiff] = useState<ShPostDiff | null>(null)
  const [restoring, setRestoring] = useState(false)
  const [confirming, setConfirming] = useState(false)
  /** SH988 — the one field whose restore is waiting for its second press. */
  const [confirmingField, setConfirmingField] = useState<string | null>(null)

  useEffect(() => {
    const abort = new AbortController()
    Promise.all([service.versions(postId), service.revisions(postId, { signal: abort.signal })])
      .then(([versions, captured]) => {
        setRows(versions)
        setRevisions(captured)
      })
      .catch((error: unknown) => {
        if (!isCancelled(error)) reportProblem(error, t("content.noVersions"))
      })
    return () => abort.abort()
  }, [postId, t])

  const [from, to] = pair
  useEffect(() => {
    const abort = new AbortController()
    service.diff(postId, from, to, { signal: abort.signal })
      .then(setDiff)
      .catch((error: unknown) => {
        if (!isCancelled(error)) reportProblem(error, t("common.error"))
      })
    return () => abort.abort()
  }, [postId, from, to, t])

  // Loading is derived rather than set: the answer on screen is for the pair asked, or it is not.
  const loading = !diff || diff.from !== from || diff.to !== to
  const draft = rows.find((row) => row.state === "DRAFT")
  const published = rows.find((row) => row.state === "PUBLISHED")
  const versions = useMemo(() => railVersions(revisions,
    { draft: t("content.draftWorkingCopy"), published: t("content.publishedLive"), person: "—" },
    { draft: draft?.modifiedDate || draft?.date, published: published?.publicationDate }),
  [revisions, draft, published, t])
  const fields = useMemo(() => (diff ? diffFields(diff, schema) : []), [diff, schema])
  const changedCount = diff?.fields.filter((field) => field.changed).length ?? 0

  const restoreSide = from === PUBLISHED && published ? PUBLISHED : revisionOf(from) !== null ? from : null

  async function handleRestore() {
    if (!restoreSide) return
    if (!confirming) {
      setConfirming(true)
      return
    }
    setRestoring(true)
    try {
      const revision = revisionOf(restoreSide)
      const updated = revision === null
        ? await service.restore(postId)
        : await service.restoreRevision(postId, revision)
      toast.success(t("content.restored"))
      onRestored(updated)
      setOpen(false)
    } catch (error) {
      reportProblem(error, t("content.restoreError"))
    } finally {
      setRestoring(false)
      setConfirming(false)
    }
  }

  /**
   * SH988 — put one field's older value back into the draft, leaving every other field as it is.
   * A patch rather than a rollback, because a rollback replaces the whole body; like a rollback it
   * publishes nothing, and the draft's If-Match refuses it if somebody has moved on.
   */
  async function restoreField(name: string, label: string) {
    if (!diff?.before) return
    if (confirmingField !== name) {
      setConfirmingField(name)
      return
    }
    setRestoring(true)
    try {
      const updated = await service.patch(postId, { [name]: diff.before[name] ?? null })
      toast.success(t("content.restoredField", { field: label }))
      onRestored(updated)
      setOpen(false)
    } catch (error) {
      reportProblem(error, t("content.restoreError"))
    } finally {
      setRestoring(false)
      setConfirmingField(null)
    }
  }

  const changedFields = diff && diff.before && to === DRAFT
    ? fields.filter((field) => diff.fields.some((each) => each.name === field.id && each.changed))
    : []

  return (
    <>
      <DialogHeader>
        <DialogTitle className="flex items-center gap-2">
          <History className="size-5" />
          {t("content.versionHistory")}
        </DialogTitle>
        <DialogDescription>
          {diff && !diff.before
            ? t("content.notYetPublished")
            : changedCount > 0
              ? t("content.changesSincePublish", { count: changedCount })
              : t("content.noChangesSincePublish")}
        </DialogDescription>
      </DialogHeader>

      <div className="grid gap-4 md:grid-cols-[16rem_1fr]">
        <BentoVersionRail
          versions={versions}
          selected={pair}
          onSelect={(ids) => {
            if (ids.length === 2) {
              setPair(ids)
              setConfirming(false)
              setConfirmingField(null)
            }
          }}
          label={t("content.versionHistory")}
        />
        {loading ? (
          <div className="flex items-center gap-2 py-10 justify-center text-muted-foreground">
            <Spinner labelled className="size-4" />
            <span>{t("common.loading")}</span>
          </div>
        ) : (
          <div className="space-y-3">
            {!diff.before && <CreatedPagePanel postId={postId} />}
            <BentoDiff before={diff.before} after={diff.after} fields={fields} />
            {changedFields.length > 0 && (
              <div className="flex flex-wrap gap-2">
                {changedFields.map((field) => (
                  <Button key={field.id} size="sm" variant="outline" disabled={restoring}
                    onClick={() => restoreField(field.id, field.label)}>
                    <RotateCcw className="size-3.5" />
                    {confirmingField === field.id
                      ? t("content.restoreFieldConfirm", { field: field.label })
                      : t("content.restoreField", { field: field.label })}
                  </Button>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      <DialogFooter className="gap-2">
        {restoreSide && (
          <Button variant="destructive" onClick={handleRestore} disabled={loading} loading={restoring}>
            {!restoring && <RotateCcw className="size-4" />}
            {confirming
              ? t("content.restoreConfirm")
              : restoreSide === PUBLISHED
                ? t("content.restorePublished")
                : t("content.restoreRevision", { version: revisionOf(restoreSide) })}
          </Button>
        )}
        <Button variant="ghost" onClick={() => setOpen(false)}>
          {t("common.cancel")}
        </Button>
      </DialogFooter>
    </>
  )
}
