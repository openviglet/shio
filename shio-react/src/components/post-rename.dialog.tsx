/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH1132 — change a page's address, which the console could not do at all.
 *
 * <p>A post's friendly URL is its identity (SH81) and `post.move` on the agent surface was its only
 * writer, so a curator who wanted `/pricing` to become `/plans` had to ask an agent — or create a
 * second post and delete the first, which takes the original's id, its audit trail, its version
 * history and every inbound reference with it.</p>
 *
 * <h2>Two steps, because both rows move</h2>
 *
 * <p>The plan comes first and is not skippable. Renaming a **published** page changes the live URL
 * immediately, with no publish step — that is what a move means, since moving only the draft would
 * leave the live page at the old URL while every read reported the new one. A curator shown a text
 * field and a Save button would not know that; one who reads "this page is live, /pricing stops
 * answering, 4 pages link to it" and presses Rename has not been surprised (P7).</p>
 *
 * <p>The redirect is offered rather than assumed, because a redirect is a published object at the
 * old address and a page may be moving precisely so that nothing answers there.</p>
 */
import { useState } from "react"
import { useTranslation } from "react-i18next"
import { ArrowRight, Link2 } from "lucide-react"
import { toast } from "@viglet/viglet-design-system"

import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { reportProblem } from "@/lib/problem"
import { ShPostUnifiedService, type ShMovePlan } from "@/services/post-unified/post-unified.service"

const service = new ShPostUnifiedService()

export interface PostRenameDialogProps {
  postId: string
  /** The address the page answers at today, shown as the starting value. */
  currentUrl: string
  open: boolean
  setOpen: (open: boolean) => void
  /** The rename landed; the editor re-reads so its own copy is not one address behind. */
  onRenamed: (url: string) => void
}

export function PostRenameDialog({
  postId, currentUrl, open, setOpen, onRenamed,
}: PostRenameDialogProps) {
  const { t } = useTranslation()
  const [url, setUrl] = useState(currentUrl)
  const [redirect, setRedirect] = useState(true)
  const [plan, setPlan] = useState<ShMovePlan | null>(null)
  const [running, setRunning] = useState(false)

  const close = (next: boolean) => {
    setOpen(next)
    if (!next) {
      // The plan belongs to the URL it was asked about, so it does not outlive the dialog.
      setPlan(null)
      setUrl(currentUrl)
    }
  }

  const askForPlan = async () => {
    setRunning(true)
    try {
      setPlan(await service.move(postId, url, { redirect, dryRun: true }))
    } catch (error) {
      reportProblem(error, t("common.error"))
    } finally {
      setRunning(false)
    }
  }

  const apply = async () => {
    setRunning(true)
    try {
      await service.move(postId, url, { redirect, dryRun: false })
      toast.success(t("content.renamed", { url }))
      onRenamed(url)
      close(false)
    } catch (error) {
      reportProblem(error, t("common.error"))
    } finally {
      setRunning(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>{t("content.renameTitle")}</DialogTitle>
        </DialogHeader>

        <div className="space-y-2">
          <Label htmlFor="sh-rename-url">{t("content.renameUrl")}</Label>
          <Input
            id="sh-rename-url"
            value={url}
            // A new address invalidates the plan that was asked about the old one.
            onChange={(event) => { setUrl(event.target.value); setPlan(null) }}
          />
          <p className="text-xs text-muted-foreground flex items-center gap-1.5">
            <span className="font-mono">{currentUrl}</span>
            <ArrowRight className="size-3" aria-hidden="true" />
            <span className="font-mono">{url || "—"}</span>
          </p>
        </div>

        <label className="flex items-start gap-2 text-sm">
          <Checkbox checked={redirect}
            onCheckedChange={(next: boolean | "indeterminate") => setRedirect(next === true)} />
          <span>
            <Link2 className="inline size-3.5 mr-1" aria-hidden="true" />
            {t("content.renameRedirect")}
          </span>
        </label>

        {/*
          The plan, and the sentence the whole dialog exists for. `live` is why this is two steps:
          a rename of a published page moves the live URL at once, and a curator who has not been
          told that is the P7 surprise.
        */}
        {plan && (
          <div className="rounded-md border p-3 space-y-1 text-sm">
            {plan.live ? (
              <p className="font-medium">{t("content.renameGoesLiveNow")}</p>
            ) : (
              <p>{t("content.renameDraftOnly")}</p>
            )}
            {plan.vacated && <p className="text-muted-foreground">{t("content.renameVacates", { url: plan.vacated })}</p>}
            {plan.inbound != null && (
              <p className="text-muted-foreground">{t("content.renameInbound", { count: plan.inbound })}</p>
            )}
          </div>
        )}

        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={() => close(false)}>{t("common.cancel")}</Button>
          {plan ? (
            <Button onClick={apply} disabled={running || !url.trim()}>
              {running ? t("common.loading") : t("content.renameApply")}
            </Button>
          ) : (
            <Button onClick={askForPlan} disabled={running || !url.trim() || url === currentUrl}>
              {running ? t("common.loading") : t("content.renamePlan")}
            </Button>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
