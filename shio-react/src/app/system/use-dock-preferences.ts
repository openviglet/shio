/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useEffect } from "react"
import { setMascotStill, setMutedKinds } from "@/lib/dock-reports"
import { isCancelled } from "@/lib/read-options"
import { ShDockService } from "@/services/dock/dock.service"

const dockService = new ShDockService()

/**
 * SH971 — the curator's choice of what the dock reports, read once when the shell mounts.
 *
 * <p>A failed read leaves nothing muted and the mascot moving, which is what the dock did before
 * this existed: a preference the instance could not answer for is not a reason to hide reports.</p>
 */
export function useDockPreferencesLoad() {
  useEffect(() => {
    const abort = new AbortController()
    dockService.preferences({ signal: abort.signal })
      .then((preferences) => {
        setMutedKinds(preferences.muted)
        setMascotStill(preferences.still === true)
      })
      .catch((error: unknown) => {
        if (isCancelled(error)) return
        setMutedKinds([])
        setMascotStill(false)
      })
    return () => abort.abort()
  }, [])
}
