/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { BentoHero, BentoSection, BentoTile, bentoNavTarget } from "@viglet/viglet-design-system/bento"
import { useTranslation } from "react-i18next"
import { useParams } from "react-router-dom"
import NotFoundPage from "@/app/system/not-found.page"
import { useBentoHub } from "./bento-nav"

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
  if (!hub) return <NotFoundPage />

  return (
    <div className="space-y-8">
      <BentoHero
        title={hub.section.labelKey ? t(hub.section.labelKey) : hub.section.id}
        subtitle={hub.section.descriptionKey ? t(hub.section.descriptionKey) : undefined}
      />
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
