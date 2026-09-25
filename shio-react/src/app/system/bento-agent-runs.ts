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
import { subscribeChanges } from "@/hooks/use-change-stream"
import { createRunTracker, type AgentRun } from "@/lib/agent-runs"
import { bumpActivity, pushReport, setWorking } from "@/lib/dock-reports"
import { ShReviewService } from "@/services/review/review.service"

/** How long a run's session stays quiet before it counts as finished. */
export const RUN_QUIET_MS = 20_000

/**
 * SH968 — what the agent did, told in the dock rather than as a header count.
 *
 * <p>SH936 put the review queue's pending count beside the account menu, because the rail carries
 * no badge. The contract says a count belongs to the surface that owns it, and a number said
 * nothing about what happened. So the shell reads SH967's stream instead. A run that starts turns
 * the mascot to working, with the agent and the site in the caption. Each write bumps the dock's
 * activity. A run that ends becomes a report: attention when it left changes for review, success
 * when it did not. The report opens the run in the review queue or the agent activity.</p>
 *
 * <p>The header count is gone, and so is its provider. What it knew at load is not lost: the
 * pending count is read once when the shell mounts, and becomes one report when it is not zero,
 * so a curator who arrives after the run still hears about it.</p>
 */
export function useAgentRunReports() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  // Read by callbacks that outlive the render that made them: a report's action runs long after
  // the run ended, and must navigate with the router and language of that moment.
  const live = useRef({ t, navigate })
  useEffect(() => {
    live.current = { t, navigate }
  }, [t, navigate])

  useEffect(() => {
    const say = (key: string, options?: Record<string, unknown>) => String(live.current.t(key, options))
    const agentOf = (run: AgentRun) => run.name ?? say("dock.anAgent")
    const siteOf = (run: AgentRun) => run.sites.join(", ") || "Shio"
    const workingOf = (run: AgentRun) => say("dock.agentWorking", { agent: agentOf(run), site: siteOf(run) })
    const openReview = (session?: string) => ({
      label: say("dock.openReview"),
      onSelect: () => live.current.navigate(session
        ? `${ROUTES.ADMIN_REVIEW}?session=${encodeURIComponent(session)}`
        : ROUTES.ADMIN_REVIEW),
    })

    const tracker = createRunTracker({
      quietMs: RUN_QUIET_MS,
      schedule: (run, ms) => setTimeout(run, ms),
      cancel: (handle) => clearTimeout(handle as ReturnType<typeof setTimeout>),
      onStart: (run) => setWorking(workingOf(run)),
      onWrite: () => bumpActivity(),
      onEnd: (run) => {
        const still = tracker.running()
        setWorking(still.length ? workingOf(still[0]) : null)
        const values = { agent: agentOf(run), site: siteOf(run), count: run.writes, pending: run.pending }
        pushReport({
          tone: run.pending ? "attention" : "success",
          text: say(run.pending ? "dock.agentFinishedReview" : "dock.agentFinished", values),
          actions: [
            openReview(run.session),
            { label: say("dock.openActivity"), onSelect: () => live.current.navigate(`${ROUTES.ADMIN_ACTIVITY}?who=agent`) },
          ],
        })
      },
    })
    const off = subscribeChanges(undefined, (page) => tracker.observe(page?.changes))

    new ShReviewService().pending()
      .then(({ sessions }) => {
        if (sessions > 0) {
          pushReport({ tone: "attention", text: say("dock.pendingAtLoad", { count: sessions }), actions: [openReview()] })
        }
      })
      // Nothing to report: a count this instance cannot give is not an outcome of anything the
      // curator did, and the review queue says it for itself when they open it.
      .catch(() => undefined)

    return () => {
      off()
      tracker.dispose()
      setWorking(null)
    }
  }, [])
}
