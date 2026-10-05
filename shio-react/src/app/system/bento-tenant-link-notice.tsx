/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { IconBuildingSkyscraper, IconX } from "@tabler/icons-react"
import { useState } from "react"
import { useTranslation } from "react-i18next"
import { useLocation } from "react-router-dom"

import { useMultiTenant } from "@/app/system/bento-nav"
import { useCurrentTenant, useMyTenants } from "@/hooks/use-catalogue"
import { tenantFromSearch } from "@/app/system/bento-tenant-link"
import { Button } from "@/components/ui/button"
import { ShTenantService } from "@/services/tenant/tenant.service"
import { reportProblem } from "@/lib/problem"

const service = new ShTenantService()

/**
 * SH931 — what a link that names a tenant does when it is opened in a different one.
 *
 * <h2>It says, and it does not act</h2>
 *
 * `?tenant=<name>` is a claim about what the sender meant, and this reads it. When the claim
 * matches the session nothing renders at all — which is the case for whoever produced the link,
 * so the ordinary path stays silent. When it differs, this states both names and offers a switch
 * **the reader presses**.
 *
 * Nothing here switches on its own, and that is the design rather than an omission. A parameter
 * that moved the session on open would turn every pasted URL into a state change: a curator
 * following a colleague's link would be carried out of the tenant they were working in, with a
 * half-written draft left behind in it. `bento-tenant-link.ts` carries the full argument; the
 * consequence for this file is that `service.switchTo` may only ever be reached from a click, and
 * `bento-tenant-link.test.mjs` reads the effect bodies to hold that.
 *
 * <h2>Three states, because a member and a non-member are not the same reader</h2>
 *
 * A reader who belongs to the named tenant is offered the switch. One who does not is told
 * plainly, because the alternative is a button that fails with a 403 and reads as a broken
 * console rather than as a link they were not meant to receive. A reader on a single-tenant
 * instance sees nothing: `useMultiTenant()` gates the whole component, so the parameter is inert
 * where the ambiguity it addresses cannot arise.
 *
 * <h2>Dismissable, and not remembered</h2>
 *
 * The banner is a fact about the URL in the address bar, so it comes back on the next navigation
 * that carries the parameter. Dismissal is local state and nothing persists it: a preference
 * stored against this would silently suppress the notice for the one link that mattered.
 */
export function BentoTenantLinkNotice() {
  const { t } = useTranslation()
  const multiTenant = useMultiTenant()
  const location = useLocation()
  const claimed = tenantFromSearch(location.search)

  /*
   * SH1151 — the same two shared reads as `BentoTenantSwitcher`, which this renders beside. Both
   * asked for them independently, so the bento root made four requests for two answers.
   */
  const current = useCurrentTenant().data?.tenantId ?? ""
  const memberships = (useMyTenants().data ?? []).map((tenant) => tenant.name)
  // A dismissal belongs to the link it was about, so the banner comes back for the next link that
  // carries the parameter. SH1136 — recorded as *which* link rather than reset by an effect when the
  // link changes: the effect set state on every navigation, and naming the link says the rule itself.
  const [dismissedFor, setDismissedFor] = useState<string | null>(null)
  const dismissed = dismissedFor !== null && dismissedFor === claimed
  const [switching, setSwitching] = useState(false)

  if (!multiTenant || !claimed || dismissed) return null
  // `current` is empty until the first read answers; showing the banner before then would name a
  // mismatch that may not exist.
  if (!current || current === claimed) return null

  const member = memberships.includes(claimed)

  const switchTo = async () => {
    if (switching) return
    setSwitching(true)
    try {
      await service.switchTo(claimed)
      globalThis.location.reload()
    } catch (error) {
      reportProblem(error, t("common.error"))
      setSwitching(false)
    }
  }

  return (
    <div
      className="bento-glass mx-4 mt-4 flex flex-wrap items-center gap-3 rounded-2xl border px-4 py-3 shadow-md"
      role="status"
    >
      <IconBuildingSkyscraper className="h-4 w-4 shrink-0 opacity-70" aria-hidden="true" />
      <p className="min-w-0 flex-1 text-sm">
        {t("tenantLink.body", { claimed, current })}
        {member ? null : <span className="block opacity-70">{t("tenantLink.notMember", { tenant: claimed })}</span>}
      </p>
      {member
        ? (
          <Button size="sm" variant="outline" disabled={switching} onClick={switchTo}>
            {t("tenantLink.switch", { tenant: claimed })}
          </Button>
        )
        : null}
      <Button
        size="icon-sm"
        variant="ghost"
        aria-label={t("tenantLink.dismiss")}
        onClick={() => setDismissedFor(claimed)}
      >
        <IconX className="h-4 w-4" aria-hidden="true" />
      </Button>
    </div>
  )
}
