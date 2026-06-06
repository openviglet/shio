import { useCallback, useEffect, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { History, RotateCcw, Loader2 } from "lucide-react"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Switch } from "@/components/ui/switch"
import { Label } from "@/components/ui/label"
import { cn } from "@/lib/utils"
import { toast } from "@viglet/viglet-design-system"
import { ShPostUnifiedService } from "@/services/post-unified/post-unified.service"
import type { ShPostUnified, ShPostTypeSchemaField } from "@/models/post-unified/post-unified"

const service = new ShPostUnifiedService()

/** `data` may arrive as a JSON string (entity getter) or an object — coerce defensively. */
function asDataObject(d: unknown): Record<string, unknown> {
  if (typeof d === "string") {
    try {
      const parsed = JSON.parse(d)
      return parsed && typeof parsed === "object" ? (parsed as Record<string, unknown>) : {}
    } catch {
      return {}
    }
  }
  return d && typeof d === "object" ? (d as Record<string, unknown>) : {}
}

function formatValue(v: unknown, emptyLabel: string): string {
  if (v === undefined || v === null || v === "") return emptyLabel
  if (typeof v === "string") return v
  return JSON.stringify(v)
}

type DiffStatus = "unchanged" | "changed" | "added" | "removed"

interface DiffRow {
  name: string
  label: string
  published: unknown
  draft: unknown
  status: DiffStatus
}

function isEmptyValue(v: unknown): boolean {
  return v === undefined || v === null || v === ""
}

function buildDiff(
  schema: ShPostTypeSchemaField[],
  publishedData: Record<string, unknown>,
  draftData: Record<string, unknown>
): DiffRow[] {
  return schema
    .filter((f) => f.widget !== "Tab" && f.widget !== "Hidden")
    .map((f) => {
      const published = publishedData[f.name]
      const draft = draftData[f.name]
      let status: DiffStatus = "unchanged"
      if (JSON.stringify(published) !== JSON.stringify(draft)) {
        if (isEmptyValue(published)) status = "added"
        else if (isEmptyValue(draft)) status = "removed"
        else status = "changed"
      }
      return { name: f.name, label: f.label || f.name, published, draft, status }
    })
}

interface Props {
  postId: string
  schema: ShPostTypeSchemaField[]
  open: boolean
  setOpen: (open: boolean) => void
  /** Called after a successful restore with the reverted draft row. */
  onRestored: (draft: ShPostUnified) => void
}

export function PostVersionsDialog({ postId, schema, open, setOpen, onRestored }: Props) {
  const { t } = useTranslation()
  const [loading, setLoading] = useState(false)
  const [restoring, setRestoring] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [onlyChanges, setOnlyChanges] = useState(true)
  const [draft, setDraft] = useState<ShPostUnified | null>(null)
  const [published, setPublished] = useState<ShPostUnified | null>(null)

  const emptyLabel = t("content.emptyValue")

  const load = useCallback(async () => {
    setLoading(true)
    setConfirming(false)
    try {
      const versions = await service.versions(postId)
      setDraft(versions.find((v) => v.state === "DRAFT") ?? null)
      setPublished(versions.find((v) => v.state === "PUBLISHED") ?? null)
    } catch {
      toast.error(t("content.noVersions"))
    } finally {
      setLoading(false)
    }
  }, [postId, t])

  useEffect(() => {
    if (open) load()
  }, [open, load])

  const diff = useMemo(() => {
    if (!published && !draft) return []
    return buildDiff(schema, asDataObject(published?.data), asDataObject(draft?.data))
  }, [schema, published, draft])

  const changedCount = useMemo(() => diff.filter((d) => d.status !== "unchanged").length, [diff])
  const visibleRows = useMemo(
    () => (onlyChanges ? diff.filter((d) => d.status !== "unchanged") : diff),
    [diff, onlyChanges]
  )

  async function handleRestore() {
    if (!confirming) {
      setConfirming(true)
      return
    }
    setRestoring(true)
    try {
      const updated = await service.restore(postId)
      toast.success(t("content.restored"))
      onRestored(updated)
      setOpen(false)
    } catch {
      toast.error(t("content.restoreError"))
    } finally {
      setRestoring(false)
      setConfirming(false)
    }
  }

  function fmtDate(value?: string) {
    if (!value) return "—"
    const d = new Date(value)
    return isNaN(d.getTime()) ? value : d.toLocaleString()
  }

  const statusLabel: Record<DiffStatus, string> = {
    unchanged: "",
    changed: t("content.statusChanged"),
    added: t("content.statusAdded"),
    removed: t("content.statusRemoved"),
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogContent className="sm:max-w-3xl max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <History className="size-5 text-orange-500" />
            {t("content.versionHistory")}
          </DialogTitle>
          <DialogDescription>
            {published
              ? changedCount > 0
                ? t("content.changesSincePublish", { count: changedCount })
                : t("content.noChangesSincePublish")
              : t("content.notYetPublished")}
          </DialogDescription>
        </DialogHeader>

        {loading ? (
          <div className="flex items-center gap-2 py-10 justify-center text-muted-foreground">
            <Loader2 className="size-4 animate-spin" />
            <span>{t("common.loading")}</span>
          </div>
        ) : (
          <div className="space-y-4">
            {/* State metadata cards */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="rounded-lg border border-l-4 border-l-orange-500 bg-background p-3">
                <div className="flex items-center justify-between">
                  <span className="text-sm font-semibold">{t("content.draftWorkingCopy")}</span>
                  <Badge variant="secondary" className="text-xs">DRAFT</Badge>
                </div>
                {draft ? (
                  <p className="text-xs text-muted-foreground mt-1">
                    {t("content.lastModifiedBy", { name: draft.modifier || draft.owner || "—" })}
                    {" · "}
                    {fmtDate(draft.modifiedDate || draft.date)}
                  </p>
                ) : (
                  <p className="text-xs text-muted-foreground mt-1">—</p>
                )}
              </div>
              <div className="rounded-lg border border-l-4 border-l-green-500 bg-background p-3">
                <div className="flex items-center justify-between">
                  <span className="text-sm font-semibold">{t("content.publishedLive")}</span>
                  <Badge variant="secondary" className="text-xs">PUBLISHED</Badge>
                </div>
                {published ? (
                  <p className="text-xs text-muted-foreground mt-1">
                    {t("content.publishedBy", { name: published.publisher || "—" })}
                    {" · "}
                    {fmtDate(published.publicationDate)}
                  </p>
                ) : (
                  <p className="text-xs text-muted-foreground mt-1">{t("content.noPublishedToCompare")}</p>
                )}
              </div>
            </div>

            {/* Diff table */}
            {diff.length > 0 && (
              <>
                <div className="flex items-center justify-end gap-2">
                  <Switch id="only-changes" checked={onlyChanges} onCheckedChange={setOnlyChanges} />
                  <Label htmlFor="only-changes" className="text-sm text-muted-foreground">
                    {t("content.onlyChanges")}
                  </Label>
                </div>
                <div className="rounded-lg border overflow-hidden">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead className="w-1/4">{t("content.field")}</TableHead>
                        <TableHead>{t("content.publishedLive")}</TableHead>
                        <TableHead>{t("content.draftWorkingCopy")}</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {visibleRows.length === 0 ? (
                        <TableRow>
                          <TableCell colSpan={3} className="text-center text-sm text-muted-foreground py-6">
                            {t("content.noChangesSincePublish")}
                          </TableCell>
                        </TableRow>
                      ) : (
                        visibleRows.map((row) => (
                          <TableRow
                            key={row.name}
                            className={cn(row.status !== "unchanged" && "bg-orange-50 dark:bg-orange-950/20")}
                          >
                            <TableCell className="align-top">
                              <span className="font-medium text-sm">{row.label}</span>
                              {row.status !== "unchanged" && (
                                <Badge variant="outline" className="ml-2 text-[10px] border-orange-300 text-orange-600 dark:border-orange-700 dark:text-orange-400">
                                  {statusLabel[row.status]}
                                </Badge>
                              )}
                            </TableCell>
                            <TableCell className="align-top text-xs text-muted-foreground break-words max-w-[14rem]">
                              {formatValue(row.published, emptyLabel)}
                            </TableCell>
                            <TableCell className="align-top text-xs break-words max-w-[14rem]">
                              {formatValue(row.draft, emptyLabel)}
                            </TableCell>
                          </TableRow>
                        ))
                      )}
                    </TableBody>
                  </Table>
                </div>
              </>
            )}
          </div>
        )}

        <DialogFooter className="gap-2">
          {published && (
            <Button
              variant="destructive"
              onClick={handleRestore}
              disabled={restoring || loading}
            >
              {restoring ? <Loader2 className="size-4 animate-spin" /> : <RotateCcw className="size-4" />}
              {confirming ? t("content.restoreConfirm") : t("content.restorePublished")}
            </Button>
          )}
          <Button variant="ghost" onClick={() => setOpen(false)}>
            {t("common.cancel")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
