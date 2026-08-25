/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { IconRobot } from "@tabler/icons-react"
import { useTranslation } from "react-i18next"
import { Link } from "react-router-dom"

import { ROUTES } from "@/app/routes.const"
import { Button } from "@/components/ui/button"
import { useReviewPending } from "@/contexts/review-pending.context"

/**
 * SH936 — the one thing that says an agent run is waiting for a decision.
 *
 * <h2>Why it is in the header and not on the rail</h2>
 *
 * The console sidebar carried this on its Agent Review entry until SH910, and the bento rail draws
 * **sections** rather than screens, so there was no entry left to sit on. Three homes were
 * considered and two are not available today: `BentoNavSection` carries no badge field, so a badge
 * on the Administration section is a design-system change and a release; and the hub that
 * `areaRoute` is meant to open does not exist in this console — `/bento/admin` redirects to the
 * user list.
 *
 * The header is not merely what was left. This is an ambient, personal signal — *work is waiting
 * for you* — which is the vocabulary of a notification rather than of navigation, and the header is
 * where a reader already looks for those. It is visible from every surface, which is what makes it
 * ambient at all, and it is one click from the queue.
 *
 * <h2>Absent, not zero</h2>
 *
 * SH207's rule, kept: with nothing pending this renders **nothing**. A dimmed "0" is a number a
 * curator has to read and dismiss on every screen, forever, to learn what its absence would have
 * told them at a glance.
 *
 * A reader who may not open the queue also sees nothing, and gets there without a permission check:
 * `ReviewPendingProvider` swallows the error and reports 0, so a 403 on the count is indistinguishable
 * from an empty one — which is the correct outcome for an indicator, though not for a page.
 */
export function BentoReviewPending() {
  const { t } = useTranslation()
  const { pending } = useReviewPending()

  if (pending <= 0) return null

  return (
    <Button asChild variant="ghost" size="sm" className="gap-1.5">
      <Link to={ROUTES.ADMIN_REVIEW} aria-label={t("review.pendingSessions", { count: pending })}>
        <IconRobot className="h-4 w-4" aria-hidden="true" />
        <span className="text-xs font-medium tabular-nums">{pending}</span>
      </Link>
    </Button>
  )
}
