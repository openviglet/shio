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
import { Link } from "react-router-dom"
import { IconCircle, IconCircleCheckFilled, IconChevronRight, IconX } from "@tabler/icons-react"
import { BentoSection } from "@viglet/viglet-design-system/bento"
import { ROUTES } from "@/app/routes.const"
import { Button } from "@/components/ui/button"
import { ConnectAgentDialog } from "@/app/content/connect-agent.dialog"
import { startChecklist, startDismissKey, type StartState, type StartStep } from "@/lib/getting-started"

/** Where each unticked step is done. The agent step opens the connect panel instead. */
const STEP_ROUTE: Record<Exclude<StartStep, "agent">, string> = {
  site: ROUTES.CONTENT_ROOT,
  edited: ROUTES.VISUAL_EDITOR,
  published: ROUTES.CONTENT_ROOT,
  reviewed: ROUTES.ADMIN_REVIEW,
}

function readDismissed(key: string): boolean {
  try {
    return localStorage.getItem(key) === "1"
  } catch {
    return false
  }
}

/**
 * SH1423 — the home page's way round Shio's loop once: have a site, change a page, publish one,
 * connect a coding agent, review what it did.
 *
 * <p>Each tick is the instance's own state from the summary home already reads (`start`), never a
 * click this browser remembered, so a step a colleague or an agent did is done here too. An unticked
 * step links to the screen that does it; connecting an agent opens the same panel as the first-use
 * screen, for an administrator, since issuing a key is theirs. The list hides itself when every step
 * is done, and a person can hide it before that. A coach-mark tour was rejected in the design: it
 * describes the screen rather than doing the work, and it rots with every layout change.</p>
 */
export function GettingStarted({
  state,
  username,
  admin,
}: Readonly<{ state: StartState | null | undefined; username: string | undefined; admin: boolean }>) {
  const { t } = useTranslation()
  const key = startDismissKey(username)
  const [dismissed, setDismissed] = useState(() => readDismissed(key))
  const [connecting, setConnecting] = useState(false)
  const checklist = startChecklist(state)

  if (!checklist || checklist.complete || dismissed) return null

  const dismiss = () => {
    try {
      localStorage.setItem(key, "1")
    } catch {
      // A browser that keeps nothing still hides it for this visit.
    }
    setDismissed(true)
  }

  return (
    <BentoSection
      title={t("home.startTitle")}
      description={t("home.startProgress", { done: checklist.done, total: checklist.steps.length })}
      gridClassName="block"
    >
      <div data-testid="getting-started" className="bg-card divide-border/60 divide-y overflow-hidden rounded-xl border">
        <ol>
          {checklist.steps.map(({ step, done }) => {
            const label = t(`home.startStep_${step}`)
            const icon = done
              ? <IconCircleCheckFilled size={18} aria-hidden="true" className="text-primary shrink-0" />
              : <IconCircle size={18} aria-hidden="true" className="text-muted-foreground shrink-0" />
            const body = (
              <>
                {icon}
                <span className="min-w-0 flex-1">
                  <span className={`block text-sm font-medium ${done ? "text-muted-foreground line-through" : ""}`}>{label}</span>
                  {!done && <span className="text-muted-foreground block text-xs">{t(`home.startHint_${step}`)}</span>}
                </span>
                {!done && <IconChevronRight size={16} aria-hidden="true" className="text-muted-foreground shrink-0" />}
              </>
            )
            const rowClass = "flex w-full items-center gap-3 px-4 py-3 text-left"
            if (done) {
              return <li key={step} data-start-step={step} data-done="true" className={rowClass}>{body}</li>
            }
            if (step === "agent") {
              return (
                <li key={step} data-start-step={step}>
                  {admin ? (
                    <button type="button" className={`${rowClass} hover:bg-muted/40 transition-colors`} onClick={() => setConnecting(true)}>
                      {body}
                    </button>
                  ) : (
                    <span className={rowClass}>{body}</span>
                  )}
                </li>
              )
            }
            return (
              <li key={step} data-start-step={step}>
                <Link to={STEP_ROUTE[step]} className={`${rowClass} hover:bg-muted/40 transition-colors`}>{body}</Link>
              </li>
            )
          })}
        </ol>
        <div className="flex justify-end px-2 py-1.5">
          <Button size="sm" variant="ghost" onClick={dismiss}>
            <IconX size={14} aria-hidden="true" className="mr-1" />
            {t("home.startDismiss")}
          </Button>
        </div>
      </div>
      {admin && <ConnectAgentDialog open={connecting} onOpenChange={setConnecting} />}
    </BentoSection>
  )
}
