/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useEffect, useRef } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { subscribeChanges } from "@/hooks/use-change-stream"
import { arrivalReport, arrivalsIn, type Arrival, type ArrivalDeps } from "@/lib/arrival-reports"
import { pushReport } from "@/lib/dock-reports"
import { openSubmission, trashSubmission } from "./submission-actions"

/** How long arrivals keep gathering into one report before it is made. */
export const ARRIVAL_GATHER_MS = 2000

/**
 * SH970 — form submissions arriving, reported in the dock.
 *
 * <p>The design the assistant was drawn from shows its attention state for one event above all: a
 * lead arriving from the site's form. Shio wrote the submission as a draft and nothing told anyone.
 * This reads arrivals off the shared stream and gathers them for `ARRIVAL_GATHER_MS` after the last
 * one, so several that land together become one report (`lib/arrival-reports.ts`).</p>
 */
export function useArrivalReports() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const live = useRef({ t, navigate })
  useEffect(() => {
    live.current = { t, navigate }
  }, [t, navigate])

  useEffect(() => {
    const say = (key: string, options?: Record<string, unknown>) => String(live.current.t(key, options))
    const deps: ArrivalDeps = {
      t: say,
      open: (address) => openSubmission(address, (to) => live.current.navigate(to), say),
      trash: (address) => trashSubmission(address, say),
    }
    let gathered: Arrival[] = []
    let timer: ReturnType<typeof setTimeout> | undefined
    const flush = () => {
      const report = arrivalReport(gathered, deps)
      gathered = []
      if (report) pushReport(report)
    }
    const off = subscribeChanges(undefined, (page) => {
      const arrived = arrivalsIn(page)
      if (arrived.length === 0) return
      gathered.push(...arrived)
      clearTimeout(timer)
      timer = setTimeout(flush, ARRIVAL_GATHER_MS)
    })
    return () => {
      clearTimeout(timer)
      off()
    }
  }, [])
}
