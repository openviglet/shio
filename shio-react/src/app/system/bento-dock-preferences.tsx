/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { IconBellCog } from "@tabler/icons-react"
import { useState } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "@viglet/viglet-design-system"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Switch } from "@/components/ui/switch"
import { setMutedKinds, useMutedKinds } from "@/lib/dock-reports"
import { reportProblem } from "@/lib/problem"
import { DOCK_KINDS, ShDockService, type DockKind } from "@/services/dock/dock.service"

const dockService = new ShDockService()

/**
 * SH971 — the header control that opens the choice.
 *
 * <p>One switch per kind, on meaning "report it". Failures are not a switch: a report in tone
 * error reaches the dock whatever is muted, and the dialog says so, because a curator who muted
 * schedules and then missed a failed launch would have been told nothing by a switch that lied.</p>
 */
export function BentoDockPreferences() {
  const { t } = useTranslation()
  const muted = useMutedKinds()
  const [open, setOpen] = useState(false)
  const [saving, setSaving] = useState(false)

  const toggle = async (kind: DockKind, reported: boolean) => {
    const next = DOCK_KINDS.filter((each) => (each === kind ? !reported : muted.has(each)))
    setSaving(true)
    try {
      const saved = await dockService.savePreferences({ muted: next })
      setMutedKinds(saved.muted)
      toast.success(t("dock.preferencesSaved"))
    } catch (error) {
      reportProblem(error, t("common.error"))
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <Button variant="ghost" size="icon" aria-label={t("dock.preferences")} onClick={() => setOpen(true)}>
        <IconBellCog className="size-5" />
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("dock.preferences")}</DialogTitle>
            <DialogDescription>{t("dock.preferencesHint")}</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            {DOCK_KINDS.map((kind) => (
              <label key={kind} className="flex items-center justify-between gap-4">
                <span className="text-sm">{t(`dock.kind.${kind}`)}</span>
                <Switch
                  checked={!muted.has(kind)}
                  disabled={saving}
                  onCheckedChange={(reported: boolean) => { void toggle(kind, reported) }}
                />
              </label>
            ))}
          </div>
        </DialogContent>
      </Dialog>
    </>
  )
}
