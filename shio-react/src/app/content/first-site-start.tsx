/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import {
  IconChevronRight, IconFilePlus, IconPackages, IconUpload, type Icon as TablerIcon,
} from "@tabler/icons-react"
import { useTranslation } from "react-i18next"
import { Link } from "react-router-dom"
import { ROUTES } from "@/app/routes.const"
import { Badge } from "@/components/ui/badge"
import { useCurrentUser } from "@/contexts/user.context"

interface StartChoice {
  id: string
  to: string
  icon: TablerIcon
  title: string
  description: string
  recommended?: boolean
}

/**
 * SH1420 — how a first site starts, offered where a newcomer actually is.
 *
 * <p>The Marketplace is the human half of blueprints and the fastest way to a site that already has
 * pages, layouts and a theme, and it used to be reachable only from Administration, Instance. So an
 * empty site list and an empty home page show the three ways in, the Marketplace first and marked
 * as the recommended one. Installing a package is an administrator's act, so a curator is offered
 * the other two only — the same rule that keeps the Marketplace out of a curator's rail.</p>
 */
export function FirstSiteStart() {
  const { t } = useTranslation()
  const { user } = useCurrentUser()
  // Unknown while the user loads, and unknown offers everything, as the rail does (SH1317).
  const admin = user ? user.admin : true

  const choices: StartChoice[] = [
    ...(admin ? [{
      id: "marketplace",
      to: ROUTES.ADMIN_MARKETPLACE,
      icon: IconPackages,
      title: t("content.startMarketplaceTitle"),
      description: t("content.startMarketplaceDesc"),
      recommended: true,
    }] : []),
    {
      id: "blank",
      to: ROUTES.BENTO_SITE_NEW,
      icon: IconFilePlus,
      title: t("content.startBlankTitle"),
      description: t("content.startBlankDesc"),
    },
    {
      id: "import",
      to: ROUTES.BENTO_SITE_IMPORT,
      icon: IconUpload,
      title: t("content.startImportTitle"),
      description: t("content.startImportDesc"),
    },
  ]

  return (
    <ul className="grid gap-4 md:grid-cols-3" data-testid="first-site-start">
      {choices.map((choice) => (
        <li key={choice.id} className="flex">
          <Link
            to={choice.to}
            data-start-choice={choice.id}
            className={`bg-card group flex w-full flex-col gap-3 rounded-xl border p-5 transition-colors hover:border-primary focus-visible:ring-ring focus-visible:ring-2 focus-visible:outline-none ${choice.recommended ? "border-primary/60" : ""}`}
          >
            <span className="flex items-center justify-between gap-2">
              <span className={`grid size-10 place-items-center rounded-lg ${choice.recommended ? "bg-primary text-primary-foreground" : "bg-muted text-muted-foreground"}`}>
                <choice.icon size={20} aria-hidden="true" />
              </span>
              {choice.recommended && <Badge variant="secondary">{t("content.startRecommended")}</Badge>}
            </span>
            <span className="block text-base font-semibold">{choice.title}</span>
            <span className="text-muted-foreground block flex-1 text-sm">{choice.description}</span>
            <span className="text-primary flex items-center gap-1 text-sm font-medium">
              {t("content.startGo")}
              <IconChevronRight size={16} aria-hidden="true" className="transition-transform group-hover:translate-x-0.5" />
            </span>
          </Link>
        </li>
      ))}
    </ul>
  )
}
