/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useSyncExternalStore } from "react"
import type { VigletAssistantReport } from "@viglet/viglet-design-system"

/**
 * SH966 — the reports the dock carries: outcomes a curator can act on, not just read.
 *
 * <p>A toast is a sentence that leaves. A refused save needs to stay until somebody reads it, and
 * to offer what the server suggested: go to the field, take the correction. VDS134 gave the dock a
 * `report` message kind for exactly this, with a tone, a read state and up to three actions. This
 * is the one place the console keeps them. A module-level store rather than a context, because a
 * page reports and the dock renders in two different subtrees, and a store needs no provider that
 * a test or a second shell could forget to mount.</p>
 *
 * <p>Ids and times are for the dock's own read and dismiss bookkeeping. Nothing is persisted: a
 * reload starts empty, the same as the toast list.</p>
 */
export type DockReport = VigletAssistantReport

let reports: readonly DockReport[] = []
const listeners = new Set<() => void>()
let next = 0

function emit(updated: readonly DockReport[]) {
  reports = updated
  for (const listener of listeners) listener()
}

/** The newest ten; a dock is not a log, and the activity page is. */
const KEPT = 10

export function pushReport(report: Omit<DockReport, "id" | "role" | "at" | "read">): string {
  next += 1
  const id = `report-${Date.now().toString(36)}-${next}`
  emit([...reports, { ...report, id, role: "report" as const, at: Date.now(), read: false }].slice(-KEPT))
  return id
}

export function markReportRead(id: string) {
  if (!reports.some((report) => report.id === id && !report.read)) return
  emit(reports.map((report) => (report.id === id ? { ...report, read: true } : report)))
}

export function dismissReport(id: string) {
  if (!reports.some((report) => report.id === id)) return
  emit(reports.filter((report) => report.id !== id))
}

export function currentReports(): readonly DockReport[] {
  return reports
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => { listeners.delete(listener) }
}

export function useDockReports(): readonly DockReport[] {
  return useSyncExternalStore(subscribe, currentReports, currentReports)
}
