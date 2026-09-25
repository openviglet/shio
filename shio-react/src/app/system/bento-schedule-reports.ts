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
import { ROUTES } from "@/app/routes.const"
import { replayChanges, subscribeChanges } from "@/hooks/use-change-stream"
import { pushReport } from "@/lib/dock-reports"
import { scheduleReports, type ScheduleReportDeps } from "@/lib/schedule-reports"
import { retrySchedule } from "./schedule-retry"

/** Where this browser last read the feed up to, so a failure while it was closed still reports. */
const SEEN_KEY = "shio.dock.feedSeen"
/** How far back the first visit looks, with no cursor of its own. */
const FIRST_LOOK_MS = 7 * 24 * 60 * 60 * 1000

function readSeen(): string | undefined {
  try {
    return localStorage.getItem(SEEN_KEY) ?? undefined
  } catch {
    return undefined
  }
}

function writeSeen(cursor: string) {
  try {
    localStorage.setItem(SEEN_KEY, cursor)
  } catch {
    // A private window: the next visit looks back a week instead, which is the first-visit rule.
  }
}

/**
 * SH969 — a scheduled publish's outcome, reported to the curator who set it.
 *
 * <p>The scheduler logged a failure as an agent incident and a success as a log line, so the
 * curator who picked the date saw neither, and a launch that did not happen was found by a
 * customer. The feed now carries both, and this puts them in the dock. A failure is an error report
 * with "open the page" and "retry now", where the retry is the incident's own batch op. A success
 * names the page and the time it went live.</p>
 *
 * <p>A failure that only reaches a live session is still a failure nobody reads. So at load this
 * replays the stream from where this browser last read it up to, a week back on a first visit, and
 * reports the failures in that range as unread. Through the stream, because the hub applies the
 * reader's content ACL to incidents and the agent feed does not. The cursor is kept per browser:
 * losing it costs a week's failures reported again, never one missed.</p>
 */
export function useScheduleReports() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const live = useRef({ t, navigate, locale: i18n.language })
  useEffect(() => {
    live.current = { t, navigate, locale: i18n.language }
  }, [t, navigate, i18n.language])

  useEffect(() => {
    const deps: ScheduleReportDeps = {
      t: (key, options) => String(live.current.t(key, options)),
      when: (at) => new Date(at).toLocaleString(live.current.locale),
      openPost: (id) => live.current.navigate(`${ROUTES.CONTENT_ROOT}/post/${id}`),
      retry: (op, address) => retrySchedule(op, address, (key, options) => String(live.current.t(key, options))),
    }

    const since = readSeen() ?? `${new Date(Date.now() - FIRST_LOOK_MS).toISOString()}|`
    // Only the failures: a success nobody saw live needs nothing done about it.
    const stopReplay = replayChanges(since, (page, cursor) => {
      for (const report of scheduleReports({ incidents: page?.incidents }, deps)) pushReport(report)
      if (cursor) writeSeen(cursor)
    })
    const off = subscribeChanges(undefined, (page, cursor) => {
      for (const report of scheduleReports(page, deps)) pushReport(report)
      if (cursor) writeSeen(cursor)
    })
    return () => {
      stopReplay()
      off()
    }
  }, [])
}
