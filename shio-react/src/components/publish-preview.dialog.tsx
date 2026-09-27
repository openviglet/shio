import { useQuery } from "@tanstack/react-query"
import { addressKey } from "@/lib/query-keys"
import { useState } from "react"
import { useTranslation } from "react-i18next"
import { Upload } from "lucide-react"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { ShPostUnifiedService } from "@/services/post-unified/post-unified.service"
import type { ShPublishPlan, ShPublishRoutes } from "@/models/post-unified/post-unified"
import { Spinner } from "@/components/spinner"

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
 *
 * <h2>SH998 — the override, and why it appears only after the refusal</h2>
 *
 * <p>A reason box on every publish would train a curator to fill one in, which is how a waiver stops
 * meaning anything. So the gate refuses first, in the server's own words, and the box appears in the
 * refusal: the only person who ever sees it is the one who has just been told which check stopped
 * them and why. The sentence they write is not a formality — it is the whole of the override, and
 * the hint says who will read it.</p>
 */
/**
 * The plan and the route impact, read together because the dialog shows them together.
 *
 * <p>Named and at module scope rather than an inline `queryFn`, and that is not only tidiness:
 * SH965's lint names an offender by its enclosing function, and its two halves disagreed about what
 * encloses an inline query — the finding said `queryFn` and the stale-exception check said
 * `PublishPreviewDialog`, so no single EXCEPTIONS key could satisfy both. A named function is one
 * answer to that question.</p>
 */
async function readPreview(postId: string, signal?: AbortSignal):
Promise<{ plan: ShPublishPlan; routes: ShPublishRoutes | null }> {
  const [plan, routes] = await Promise.all([
    service.publishPlan(postId, { signal }),
    service.publishRoutes(postId, { signal }),
  ])
  return { plan, routes }
}

export function PublishPreviewDialog({
  postId,
  open,
  setOpen,
  onConfirm,
  publishing,
  gateRefusal,
}: {
  postId: string
  open: boolean
  setOpen: (open: boolean) => void
  onConfirm: (override?: string, redirect?: boolean) => void
  publishing: boolean
  /** SH998 — what the gate said, when one has already refused this publish. */
  gateRefusal?: string | null
}) {
  const { t } = useTranslation()
  const [override, setOverride] = useState("")
  /*
   * SH1064 — ticked, and the curator can untick it. A rename with no redirect breaks every
   * bookmark and inbound link into the page, and nothing anywhere says so afterwards; a redirect
   * nobody wanted is one post to delete. The default goes to the recoverable side.
   */
  const [redirect, setRedirect] = useState(true)

  /*
   * SH1153 — two reads when the dialog opens, both cancelled if it closes before they answer, and the
   * whole thing out of an effect.
   *
   * What this replaced did two jobs in one effect: it *read* the plan and the route impact, and it
   * *reset* four pieces of form state so a second opening did not show the first one's answers. Doing
   * both synchronously is what earned this file the `react-hooks/set-state-in-effect` suppression
   * SH1136 holds — and splitting them removes it: the reads are a query keyed on the post and enabled
   * only while open, so closing the dialog IS the reset for those three, and the two genuine form
   * fields reset on the open transition during render.
   *
   * A preview that cannot be read must still not block the publish: the server refuses what the
   * curator may not do, and this dialog is an explanation rather than a gate. So a failure leaves the
   * plan absent and says nothing, which is what `read.data ?? null` already means.
   */
  const read = useQuery({
    queryKey: addressKey(`summary:publish|${postId}`),
    queryFn: ({ signal }) => readPreview(postId, signal),
    enabled: open && Boolean(postId),
  })
  const plan = read.data?.plan ?? null
  const routes = read.data?.routes ?? null
  const loading = read.isFetching

  // The two fields a curator types into, reset on the transition into `open` rather than in an effect.
  const [wasOpen, setWasOpen] = useState(false)
  if (open !== wasOpen) {
    setWasOpen(open)
    if (open) {
      setOverride("")
      setRedirect(true)
    }
  }

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
          <Spinner className="size-4" />
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
            {/*
              SH1064 — offered exactly when an address stops answering, which is the only moment
              it means anything. The count above is the argument for it, and until now that
              paragraph was a fact with nothing to do about it.
            */}
            {routes?.stops && (
              <div className="flex items-start gap-2 pt-1">
                <Checkbox
                  id="publish-redirect"
                  checked={redirect}
                  onCheckedChange={(value) => setRedirect(value === true)}
                />
                <Label htmlFor="publish-redirect" className="font-normal leading-snug">
                  {t("content.publishRedirect", { url: routes.stops })}
                </Label>
              </div>
            )}
          </div>
        )}

        {gateRefusal && (
          <div className="space-y-2 text-sm">
            <p className="text-destructive">
              {t("content.publishGateStopped", { reason: gateRefusal })}
            </p>
            <Label htmlFor="publish-override">{t("content.publishOverrideLabel")}</Label>
            <Textarea
              id="publish-override"
              rows={2}
              value={override}
              onChange={(event) => setOverride(event.target.value)}
              placeholder={t("content.publishOverridePlaceholder")}
            />
            <p className="text-muted-foreground">{t("content.publishOverrideHint")}</p>
          </div>
        )}

        <DialogFooter className="gap-2">
          <Button
            onClick={() => onConfirm(gateRefusal ? override : undefined,
              redirect && !!routes?.stops)}
            // A blank reason is refused by the server, which is the rule; the button knows it too, so
            // a curator is not taught the rule by a second refusal.
            disabled={loading || (!!gateRefusal && !override.trim())}
            loading={publishing}
          >
            {!publishing && <Upload className="size-4" />}
            {gateRefusal ? t("content.publishOverrideConfirm") : t("content.publish")}
          </Button>
          <Button variant="ghost" onClick={() => setOpen(false)}>
            {t("common.cancel")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
