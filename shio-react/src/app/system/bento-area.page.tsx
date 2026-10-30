/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { BentoHero, BentoSection, BentoTile, bentoNavTarget } from "@viglet/viglet-design-system/bento"
import { IconWorld } from "@tabler/icons-react"
import { useTranslation } from "react-i18next"
import { useParams } from "react-router-dom"
import { ROUTES } from "@/app/routes.const"
import NotFoundPage from "@/app/system/not-found.page"
import { useSites } from "@/hooks/use-catalogue"
import { useBentoHub } from "./bento-nav"

/**
 * SH1338 — how many sites the Content hub names directly. A curator with a few sites opens one from
 * here instead of through the Sites card; past that, the Sites list is where they are found.
 */
const DIRECT_SITES = 4

/**
 * SH1193 — a rail section's hub, the bento navigation's second level.
 *
 * <p>The rail links a section to its `areaRoute`, and the contract says that route is the section's
 * hub. Shio's two sections pointed elsewhere — Content at the Sites list, Administration at a
 * redirect to Users — so the second level never rendered, and Trash, Media, Scheduled, Forms,
 * Tokens, Webhooks, GraphQL, Tenants, E-mail and Activity had no path a reader could see from the
 * rail. This page is that level: the section's surfaces as tiles, in the groups `HUB_GROUPS`
 * declares, each group four tiles or fewer.</p>
 */
export default function BentoAreaPage() {
  const { t } = useTranslation()
  const { sectionId } = useParams<{ sectionId: string }>()
  const hub = useBentoHub(sectionId)
  // SH1046's one cached read under `site:`, which the Sites list and nine other screens share.
  const { data: sites, isPending, error } = useSites()
  if (!hub) return <NotFoundPage />
  // SH1338 — writing a page was four screens of getting there: this hub, the Sites card, the site,
  // its New Page. With a few sites, the hub opens each directly and the Sites card step is gone.
  // While the read is pending or has failed the group is simply absent: it is a shortcut, and the
  // Sites card below is the way in either way, so a spinner or an error here would only be noise.
  const direct = sectionId === "content" && !isPending && !error && sites
    && sites.length > 0 && sites.length <= DIRECT_SITES ? sites : []

  return (
    <div className="space-y-8">
      <BentoHero
        title={hub.section.labelKey ? t(hub.section.labelKey) : hub.section.id}
        subtitle={hub.section.descriptionKey ? t(hub.section.descriptionKey) : undefined}
      />
      {direct.length > 0 && (
        <BentoSection title={t("nav.hub.content.yourSites")}>
          {direct.map((site) => (
            <BentoTile
              key={site.id}
              to={`${ROUTES.CONSOLE}/content/object/${site.id}`}
              icon={IconWorld}
              tone="blue"
              eyebrow=""
              title={site.name}
              span="col-span-2"
            >
              {site.description?.trim() && <p className="text-sm text-muted-foreground">{site.description}</p>}
            </BentoTile>
          ))}
        </BentoSection>
      )}
      {hub.groups.map((group) => (
        <BentoSection key={group.labelKey} title={t(group.labelKey)}>
          {group.items.map((item) => (
            <BentoTile
              key={item.id}
              to={bentoNavTarget(item)}
              icon={item.icon}
              tone={item.tone}
              eyebrow=""
              title={t(item.titleKey)}
              span="col-span-2"
            >
              <p className="text-sm text-muted-foreground">{t(item.descriptionKey)}</p>
            </BentoTile>
          ))}
        </BentoSection>
      ))}
    </div>
  )
}
