/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { IconBuildingSkyscraper, IconCheck, IconSelector } from "@tabler/icons-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "@viglet/viglet-design-system"

import { useMultiTenant } from "@/app/bento/bento-nav"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import type { ShTenant } from "@/models/tenant/tenant.model"
import { ShTenantService } from "@/services/tenant/tenant.service"

const service = new ShTenantService()

/**
 * SH934 — the one control that changes which tenant you are working in.
 *
 * <h2>Why it came back</h2>
 *
 * The console sidebar carried a switcher until SH910 deleted it, correctly, because nothing
 * rendered it any more. Nothing rendering it is not the same as nothing needing it:
 * `ShVigletTenantResolver` reads the tenant from the session, a CDA token or an OIDC claim and
 * **never** from the URL, so `POST /tenant/{name}/switch` is what changes the answer. With no
 * caller, a curator who belongs to two tenants saw whichever one they were last put in, and the
 * ways out were logging in again or writing the POST by hand. SH909 had dropped the
 * `/{tenant}/console` prefix in the same block, so both levers went at once.
 *
 * <h2>It appears when there is something to switch between</h2>
 *
 * Two conditions, and the second is the one the old switcher got wrong. `multiTenant` says the
 * *instance* runs more than one tenant — SH46's rule, and it is read through `useMultiTenant()`
 * so that this and the Tenants nav entry cannot drift apart about it. Membership size says
 * *this reader* has somewhere to go: the old control rendered on `tenants.length > 0`, so a
 * curator with exactly one membership got a menu whose only entry was where they already were.
 *
 * <h2>Switching reloads, and that is deliberate</h2>
 *
 * The tenant is a property of the session, and almost everything on screen was fetched under the
 * old one — the site list, the nav's feature gating, every cached id. Re-fetching them piecemeal
 * is a long tail of stale reads for a control somebody uses twice a day. A full reload is the
 * honest way to say the world changed.
 */
export function BentoTenantSwitcher() {
  const { t } = useTranslation()
  const multiTenant = useMultiTenant()
  const [tenants, setTenants] = useState<ShTenant[]>([])
  const [current, setCurrent] = useState("")
  const [switching, setSwitching] = useState(false)

  useEffect(() => {
    if (!multiTenant) return
    service.mine().then(setTenants).catch(() => setTenants([]))
    service.current().then((c) => setCurrent(c.tenantId)).catch(() => undefined)
  }, [multiTenant])

  // Nothing to offer: a single-tenant instance, or a reader who belongs to one tenant.
  if (!multiTenant || tenants.length < 2) return null

  const switchTo = async (name: string) => {
    if (name === current || switching) return
    setSwitching(true)
    try {
      await service.switchTo(name)
      globalThis.location.reload()
    } catch {
      toast.error(t("common.error"))
      setSwitching(false)
    }
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="sm" className="gap-1.5" disabled={switching}
          aria-label={t("tenantSwitcher.label")}>
          <IconBuildingSkyscraper className="h-4 w-4" aria-hidden="true" />
          <span className="max-w-32 truncate font-mono text-xs">
            {current || t("tenantAdmin.title")}
          </span>
          <IconSelector className="h-3.5 w-3.5 opacity-60" aria-hidden="true" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="min-w-48">
        <DropdownMenuLabel>{t("tenantSwitcher.label")}</DropdownMenuLabel>
        <DropdownMenuSeparator />
        {tenants.map((tenant) => (
          <DropdownMenuItem
            key={tenant.id}
            onClick={() => switchTo(tenant.name)}
            className="font-mono text-xs"
          >
            <IconCheck
              className={tenant.name === current ? "h-4 w-4 opacity-100" : "h-4 w-4 opacity-0"}
              aria-hidden="true"
            />
            {tenant.name}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
