import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { Loader2, Upload } from "lucide-react"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { ShPostUnifiedService } from "@/services/post-unified/post-unified.service"
import type { ShPublishPlan, ShPublishRoutes } from "@/models/post-unified/post-unified"

const service = new ShPostUnifiedService()

/**
 * SH996 — what Publish is about to do, before it does it.
 *
 * <p>The agent's publish has a dry run and the console's had a button, so the surface P7 was written
 * for was the one with none of P7's instruments. This is the dry run with a face: the server's own
 * plan row, and — when publishing moves a URL — which address starts resolving, which one stops, and
 * how many published pages link to the one that goes. Both come from the server; nothing here decides
 * what publishing would do.</p>
 *
 * <p>It opens on every Publish rather than only when something moves. A confirmation that appears
 * only sometimes trains a curator to click through the times it does, which is the opposite of what
 * it is for — and the ordinary case is one line saying the page goes live.</p>
 */
export function PublishPreviewDialog({
  postId,
  open,
  setOpen,
  onConfirm,
  publishing,
}: {
  postId: string
  open: boolean
  setOpen: (open: boolean) => void
  onConfirm: () => void
  publishing: boolean
}) {
  const { t } = useTranslation()
  const [plan, setPlan] = useState<ShPublishPlan | null>(null)
  const [routes, setRoutes] = useState<ShPublishRoutes | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!open) return
    setLoading(true)
    setPlan(null)
    setRoutes(null)
    Promise.all([service.publishPlan(postId), service.publishRoutes(postId)])
      .then(([answer, moved]) => {
        setPlan(answer)
        setRoutes(moved)
      })
      // A preview that cannot be read must not block the publish: the server refuses what the
      // curator may not do, and this dialog is an explanation rather than a gate.
      .catch(() => setPlan(null))
      .finally(() => setLoading(false))
  }, [open, postId])

  const change = plan?.results?.[0]

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Upload className="size-5 vg-accent-text" />
            {t("content.publishPreviewTitle")}
          </DialogTitle>
          <DialogDescription>{t("content.publishPreviewHint")}</DialogDescription>
        </DialogHeader>

        {loading ? (
          <Loader2 className="size-4 animate-spin" />
        ) : (
          <div className="space-y-2 text-sm">
            {change && (
              <p>
                <span className="font-medium">{change.action}</span> {change.address}
                {change.detail ? ` — ${change.detail}` : ""}
              </p>
            )}
            {routes?.goesLive && (
              <p>{t("content.publishGoesLive", { url: routes.goesLive })}</p>
            )}
            {routes?.moves && (
              <p>{t("content.publishMoves", { to: routes.moves, from: routes.stops })}</p>
            )}
            {routes?.inbound ? (
              <p className="vg-accent-text">
                {t("content.publishInbound", { count: routes.inbound, url: routes.stops })}
              </p>
            ) : null}
          </div>
        )}

        <DialogFooter className="gap-2">
          <Button onClick={onConfirm} disabled={publishing || loading}>
            {publishing ? <Loader2 className="size-4 animate-spin" /> : <Upload className="size-4" />}
            {t("content.publish")}
          </Button>
          <Button variant="ghost" onClick={() => setOpen(false)}>
            {t("common.cancel")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
