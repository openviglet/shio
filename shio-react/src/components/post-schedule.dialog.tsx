import { useState } from "react"
import { useTranslation } from "react-i18next"
import { Clock, X } from "lucide-react"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { toast } from "@viglet/viglet-design-system"
import { ShPostUnifiedService } from "@/services/post-unified/post-unified.service"
import type { ShPostUnified } from "@/models/post-unified/post-unified"

const service = new ShPostUnifiedService()

/** ISO instant → `datetime-local` input value (YYYY-MM-DDTHH:mm) in local time. */
function toInputValue(iso?: string | null): string {
  if (!iso) return ""
  const d = new Date(iso)
  if (isNaN(d.getTime())) return ""
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16)
}

/** `datetime-local` value (local time) → ISO instant, or null when empty. */
function toIso(value: string): string | null {
  if (!value) return null
  const d = new Date(value)
  return isNaN(d.getTime()) ? null : d.toISOString()
}

interface Props {
  postId: string
  publishAt?: string | null
  unpublishAt?: string | null
  open: boolean
  setOpen: (open: boolean) => void
  /** Called after a successful save with the updated draft row. */
  onScheduled: (draft: ShPostUnified) => void
}

export function PostScheduleDialog({ open, setOpen, ...rest }: Props) {
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogContent className="sm:max-w-md">
        <PostScheduleBody key={`${rest.postId}|${rest.publishAt}|${rest.unpublishAt}`}
          setOpen={setOpen} {...rest} />
      </DialogContent>
    </Dialog>
  )
}

/**
 * SH702 — the inputs are seeded at mount from the props rather than reset by an effect on `open`.
 *
 * <p>The key carries the two dates as well as the post: a save updates them on the page behind, and
 * the next open must start from what was saved rather than from what the last mount was given.</p>
 */
function PostScheduleBody({
  postId,
  publishAt,
  unpublishAt,
  setOpen,
  onScheduled,
}: Omit<Props, "open">) {
  const { t } = useTranslation()
  const [saving, setSaving] = useState(false)
  const [publishInput, setPublishInput] = useState(() => toInputValue(publishAt))
  const [unpublishInput, setUnpublishInput] = useState(() => toInputValue(unpublishAt))

  async function handleSave() {
    setSaving(true)
    try {
      const updated = await service.schedule(postId, {
        publishAt: toIso(publishInput),
        unpublishAt: toIso(unpublishInput),
      })
      toast.success(t("content.scheduleSaved"))
      onScheduled(updated)
      setOpen(false)
    } catch {
      toast.error(t("content.scheduleError"))
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Clock className="size-5 text-orange-500" />
            {t("content.schedulePublishUnpublish")}
          </DialogTitle>
          <DialogDescription>{t("content.scheduleHint")}</DialogDescription>
          {/*
            SH991 — the inputs are local time and the server stores an instant, so the dialog names
            which local time it means rather than leaving a curator to guess the browser's zone.
          */}
          <p className="text-xs text-muted-foreground">
            {t("content.scheduleZone", { zone: Intl.DateTimeFormat().resolvedOptions().timeZone })}
          </p>
        </DialogHeader>

        <div className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="publish-at" className="text-sm">
              {t("content.publishAt")}
            </Label>
            <div className="flex items-center gap-2">
              <Input
                id="publish-at"
                type="datetime-local"
                value={publishInput}
                onChange={(e) => setPublishInput(e.target.value)}
              />
              <Button
                type="button"
                variant="ghost"
                size="icon"
                disabled={!publishInput}
                onClick={() => setPublishInput("")}
                aria-label={t("content.clear")}
              >
                <X className="size-4" />
              </Button>
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="unpublish-at" className="text-sm">
              {t("content.unpublishAt")}
            </Label>
            <div className="flex items-center gap-2">
              <Input
                id="unpublish-at"
                type="datetime-local"
                value={unpublishInput}
                onChange={(e) => setUnpublishInput(e.target.value)}
              />
              <Button
                type="button"
                variant="ghost"
                size="icon"
                disabled={!unpublishInput}
                onClick={() => setUnpublishInput("")}
                aria-label={t("content.clear")}
              >
                <X className="size-4" />
              </Button>
            </div>
          </div>
        </div>

        <DialogFooter className="gap-2">
          <Button onClick={handleSave} loading={saving}>
            {!saving && <Clock className="size-4" />}
            {t("content.saveSchedule")}
          </Button>
          <Button variant="ghost" onClick={() => setOpen(false)}>
            {t("common.cancel")}
          </Button>
        </DialogFooter>
    </>
  )
}
