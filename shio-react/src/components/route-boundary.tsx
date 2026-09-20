/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useQueryClient } from "@tanstack/react-query"
import { IconAlertTriangle, IconRefresh } from "@tabler/icons-react"
import { ErrorBoundary } from "@viglet/viglet-design-system"
import type { ReactNode } from "react"
import { useTranslation } from "react-i18next"
import { useLocation } from "react-router-dom"

import { Button } from "@/components/ui/button"
import { adviceOf, headlineOf, problemOf } from "@/lib/problem"

/**
 * SH1049 — a failure that stays inside the thing that failed.
 *
 * ## What one boundary around everything costs
 *
 * `main.tsx` mounts the design system's `ErrorBoundary` around the whole application, so a render
 * throw anywhere blanks the console — and this codebase's own prose records twice that the bento
 * shell came up blank behind exactly that, with the compiler saying nothing. A blank page is the
 * worst possible report: it names nothing, it offers nothing, and the only thing a curator can do
 * is reload and lose whatever they were doing.
 *
 * ## Two sizes, because a failure has a size
 *
 * `RouteBoundary` wraps the shell's outlet: one screen fails, the rail and the header stay, and the
 * curator can go somewhere else without reloading. `PanelBoundary` wraps a section, so one dead
 * widget does not take the page it is on — the case the post editor is full of, where a single
 * field's widget throwing would otherwise cost the whole form including unsaved work.
 *
 * ## Try again means re-run the reads, not reload the tab
 *
 * Resetting the boundary alone re-renders the same components against the same cached, failed
 * queries, so the throw happens again and the button looks broken. SH1046's client is what makes
 * the honest version possible: `resetQueries` drops what the route read and the re-render asks
 * again. That is the whole reason this lives beside the query layer rather than before it.
 */

/** What both sizes render: the server's own words where there are any, and a way forward. */
function Failure({ error, reset, compact }: {
  error: Error
  reset: () => void
  compact: boolean
}) {
  const { t } = useTranslation()
  const client = useQueryClient()
  const problem = problemOf(error)
  const advice = adviceOf(problem)

  const tryAgain = () => {
    // The reads first, then the boundary: resetting the boundary re-renders against the same
    // failed cache entries, so the throw would simply happen again (SH1046).
    void client.resetQueries()
    reset()
  }

  return (
    <section
      role="alert"
      className={compact
        ? "border-border bg-muted/30 rounded-lg border p-4"
        : "mx-auto max-w-2xl p-10 text-center"}
    >
      <IconAlertTriangle
        aria-hidden="true"
        className={compact ? "text-muted-foreground mb-2 h-5 w-5" : "text-muted-foreground mx-auto mb-4 h-8 w-8"}
      />
      <h2 className={compact ? "text-sm font-medium" : "text-lg font-semibold"}>
        {headlineOf(problem, t("common.error"))}
      </h2>
      {advice ? <p className="text-muted-foreground mt-2 text-sm">{advice}</p> : null}
      <Button variant="outline" size="sm" className="mt-4" onClick={tryAgain}>
        <IconRefresh aria-hidden="true" className="mr-2 h-4 w-4" />
        {t("common.tryAgain")}
      </Button>
    </section>
  )
}

/**
 * One screen's worth of failure, keyed so leaving the route clears it.
 *
 * <p>The key is not decoration: a class boundary holds `hasError` until something resets it, so
 * without it a curator who navigated away from a broken screen would carry its error to every
 * screen after. React unmounts and remounts on a changed key, which is the reset.</p>
 */
export function RouteBoundary({ children }: { children: ReactNode }) {
  const { pathname } = useLocation()
  return (
    <ErrorBoundary
      key={pathname}
      fallback={({ error, reset }) => <Failure error={error} reset={reset} compact={false} />}
    >
      {children}
    </ErrorBoundary>
  )
}

/** One panel's worth, so a dead widget does not take the form it is inside. */
export function PanelBoundary({ children }: { children: ReactNode }) {
  return (
    <ErrorBoundary
      fallback={({ error, reset }) => <Failure error={error} reset={reset} compact />}
    >
      {children}
    </ErrorBoundary>
  )
}
