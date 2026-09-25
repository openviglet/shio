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
 * SH970 — a form submission arriving, as the dock reports it.
 *
 * <p>A submission is a draft post (SH633), and since the feed half of this task its `CREATE` row
 * carries `submission: { title, summary }`, the two fields the post-type marks as its title and
 * summary. This picks those rows out of a page and turns a batch of them into one report in tone
 * attention: the title and summary as the caption, with "open" and "move to trash" for a single
 * arrival. Several that arrive together are one report, so a campaign is not fifty rows. A group
 * offers "open the newest" and no bulk trash, because one click in the corner should never be what
 * discards fifty leads (P7).</p>
 *
 * <p>"Move to trash" rather than "archive": a submission is a post, and a post has no archive
 * state. The trash is where it can be restored from; an inbox with its own states is Block BI's.</p>
 */
export interface Arrival {
  address: string
  title: string
  summary?: string
  site?: string
}

export interface ArrivalDeps {
  t: (key: string, options?: Record<string, unknown>) => string
  open: (address: string) => void
  trash: (address: string) => void
}

export function arrivalsIn(page: ChangePage | undefined): Arrival[] {
  const arrivals: Arrival[] = []
  for (const change of page?.changes ?? []) {
    if (change.action !== "CREATE" || !change.submission || !change.address) continue
    arrivals.push({
      address: change.address,
      title: change.submission.title || change.what || change.address,
      summary: change.submission.summary,
      site: change.site,
    })
  }
  return arrivals
}

export function arrivalReport(arrivals: readonly Arrival[], deps: ArrivalDeps)
  : Omit<DockReport, "id" | "role" | "at" | "read"> | undefined {
  if (arrivals.length === 0) return undefined
  const newest = arrivals[arrivals.length - 1]
  if (arrivals.length === 1) {
    return {
      tone: "attention",
      text: deps.t(newest.summary ? "dock.arrivedWithSummary" : "dock.arrived",
        { title: newest.title, summary: newest.summary, site: newest.site ?? "" }),
      actions: [
        { label: deps.t("dock.openSubmission"), onSelect: () => deps.open(newest.address) },
        { label: deps.t("dock.trashSubmission"), onSelect: () => deps.trash(newest.address) },
      ],
    }
  }
  const sites = [...new Set(arrivals.map((a) => a.site).filter(Boolean))].join(", ")
  return {
    tone: "attention",
    text: deps.t("dock.arrivedMany", {
      count: arrivals.length, site: sites,
      titles: arrivals.slice(-3).reverse().map((a) => a.title).join(", "),
    }),
    actions: [{ label: deps.t("dock.openNewest"), onSelect: () => deps.open(newest.address) }],
  }
}
