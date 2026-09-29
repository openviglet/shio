/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useState } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "@viglet/viglet-design-system"
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Switch } from "@/components/ui/switch"
import { setMascotStill, setMutedKinds, useMascotStill, useMutedKinds } from "@/lib/dock-reports"
import { reportProblem } from "@/lib/problem"
import { DOCK_KINDS, ShDockService, type DockKind, type ShDockPreferences } from "@/services/dock/dock.service"

const dockService = new ShDockService()

/**
 * SH971 — the choice of what the dock reports. SH1207 — opened from the dock's own panel (the
 * assistant's `onOpenSettings`, VDS191), so it is a controlled dialog and draws no trigger: the
 * bell-with-a-cog it used to put in the header read as a second notice beside the mascot.
 *
 * <p>One switch per kind, on meaning "report it". Failures are not a switch: a report in tone
 * error reaches the dock whatever is muted, and the dialog says so, because a curator who muted
 * schedules and then missed a failed launch would have been told nothing by a switch that lied.</p>
 *
 * <p>The last switch keeps the mascot moving. Off, it stands still, and only the motion stops: the
 * caption and the announcement go on reporting, so a curator presenting their screen loses nothing.</p>
 */
export function BentoDockPreferences({ open, onOpenChange }: Readonly<{
  open: boolean
  onOpenChange: (open: boolean) => void
}>) {
  const { t } = useTranslation()
  const muted = useMutedKinds()
  const still = useMascotStill()
  const [saving, setSaving] = useState(false)

  const save = async (preferences: ShDockPreferences) => {
    setSaving(true)
    try {
      const saved = await dockService.savePreferences(preferences)
      setMutedKinds(saved.muted)
      setMascotStill(saved.still === true)
      toast.success(t("dock.preferencesSaved"))
    } catch (error) {
      reportProblem(error, t("common.error"))
    } finally {
      setSaving(false)
    }
  }

  const toggle = (kind: DockKind, reported: boolean) =>
    save({ muted: DOCK_KINDS.filter((each) => (each === kind ? !reported : muted.has(each))), still })

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
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
            <label className="flex items-center justify-between gap-4 border-t pt-4">
              <span className="text-sm">{t("dock.animateMascot")}</span>
              <Switch
                checked={!still}
                disabled={saving}
                onCheckedChange={(animates: boolean) => {
                  void save({ muted: DOCK_KINDS.filter((each) => muted.has(each)), still: !animates })
                }}
              />
            </label>
          </div>
        </DialogContent>
      </Dialog>
    </>
  )
}
