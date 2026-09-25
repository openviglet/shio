/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import type { ChangePage } from "../hooks/use-change-stream.ts"
import type { DockReport } from "./dock-reports.ts"

/**
 * SH969 — what a schedule did, as the dock reports it.
 *
 * <p>The scheduler's outcomes are on the feed since the feed half of this task. A transition that
 * went through is a `PUBLISH` or `UNPUBLISH` row by `scheduler`. One that failed is a `schedule`
 * incident carrying the server's message, the post as `id:<uuid>`, and a `retry` that is one batch
 * op. This turns a page of that feed into the reports: a failure in tone error with "open the page"
 * and "retry now", and a success naming the page and when it went live.</p>
 *
 * <p>Pure, with the side effects passed in, so `schedule-reports.test.mjs` can plant a failed
 * schedule and press its retry without a browser.</p>
 */
export interface ScheduleReportDeps {
  t: (key: string, options?: Record<string, unknown>) => string
  /** How an instant reads to this curator. */
  when: (at: string) => string
  openPost: (id: string) => void
  retry: (op: string, address: string) => void
}

export type ScheduleReport = Omit<DockReport, "id" | "role" | "at" | "read">

const SCHEDULER = "scheduler"
const ID = "id:"

export function scheduleReports(page: ChangePage | undefined, deps: ScheduleReportDeps): ScheduleReport[] {
  const reports: ScheduleReport[] = []
  for (const incident of page?.incidents ?? []) {
    if (incident.kind !== "schedule") continue
    const actions = []
    if (incident.subject?.startsWith(ID)) {
      const id = incident.subject.slice(ID.length)
      actions.push({ label: deps.t("dock.openPage"), onSelect: () => deps.openPost(id) })
    }
    if (incident.retry) {
      const { op, address } = incident.retry
      actions.push({ label: deps.t("dock.retryNow"), onSelect: () => deps.retry(op, address) })
    }
    const text = deps.t("dock.scheduleFailed", { message: incident.message ?? incident.retry?.address ?? "" })
    reports.push({ tone: "error", text: [text, incident.fix].filter(Boolean).join(" — "), actions })
  }
  for (const change of page?.changes ?? []) {
    if (change.by !== SCHEDULER) continue
    if (change.action !== "PUBLISH" && change.action !== "UNPUBLISH") continue
    reports.push({
      tone: "success",
      text: deps.t(change.action === "PUBLISH" ? "dock.schedulePublished" : "dock.scheduleUnpublished", {
        page: change.what ?? change.address ?? "",
        time: change.at ? deps.when(change.at) : "",
      }),
    })
  }
  return reports
}
