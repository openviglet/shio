/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import {
  IconFileText,
  IconForms,
  IconHistory,
  IconHome,
  IconPlus,
  IconSearch,
  IconWebhook,
} from "@tabler/icons-react"
import {
  BENTO_TONE_CLASS,
  BentoCountTile,
  BentoEmptyState,
  BentoHero,
  BentoSection,
  BentoTile,
  bentoChipClass,
  type BentoTone,
} from "@viglet/viglet-design-system/bento"
import { useEffect, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { Link } from "react-router-dom"
import { ROUTES } from "@/app/routes.const"
import type { ShHistory } from "@/models/history/history.model"
import { ShHistoryService } from "@/services/history/history.service"
import { usePostTypes, useSites } from "@/hooks/use-catalogue"
import { ShSystemInfoService } from "@/services/system/system-info.service"

const systemInfoService = new ShSystemInfoService()
const historyService = new ShHistoryService()

/**
 * SH898 — the console home, on the bento shell.
 *
 * ## Why this page first
 *
 * Not because it matters most: because it is the cheapest possible proof that the shell, the
 * tokens, the translations and the digest all work together. If something about the extracted
 * layer is wrong, one page is where it should be found — not four pages into the site slice.
 *
 * ## What it composes
 *
 * A `BentoHero`, `BentoCountTile` for the two counts, `BentoTile` for the six quick actions, and
 * `BentoEmptyState` for an activity list with nothing in it.
 *
 * ## The action badges lost their hardcoded hues
 *
 * The console page spelled six of them out — `border-blue-300 text-blue-600 dark:border-blue-700
 * dark:text-blue-400` and five more like it. `bentoChipClass` takes a tone and the preset defines
 * the pair, so re-keying the palette reaches these too. `teal` has no tone, so `RESTORE` reads
 * violet; that is the one visible difference from the console page and it is a deliberate one.
 *
 * ## Why the counts wait rather than showing zero
 *
 * `BentoCountTile` takes a `number`, and the obvious `?? 0` puts **"0 Sites"** on the first
 * thing a curator sees after login, for as long as the request takes. Zero is a real answer to
 * that question and a wrong one. The tiles render when the counts arrive.
 */

/** Tone per activity action. Replaces six hardcoded colour-class strings on the console page. */
const ACTION_TONE: Record<string, BentoTone> = {
  CREATE: "blue",
  UPDATE: "indigo",
  PUBLISH: "emerald",
  UNPUBLISH: "amber",
  DELETE: "rose",
  RESTORE: "violet",
}

export default function BentoHomePage() {
  const { t } = useTranslation()
  /*
   * SH1046 — the two counts are the two cached catalogues, counted.
   *
   * `null` while the read is pending is the whole point of the comment above about not showing
   * "0 Sites" to somebody who has just logged in: zero is a real answer to that question and a
   * wrong one. A failure counts as zero, which is what the `.catch` this replaced did.
   */
  const sites = useSites()
  const postTypes = usePostTypes()
  const siteCount = sites.isPending ? null : (sites.data?.length ?? 0)
  const postTypeCount = postTypes.isPending ? null : (postTypes.data?.length ?? 0)
  const [appVersion, setAppVersion] = useState<string>("")
  const [activity, setActivity] = useState<ShHistory[]>([])

  useEffect(() => {
    systemInfoService.getInfo().then((i) => setAppVersion(i.appVersion)).catch(() => setAppVersion(""))
    historyService.recent({ limit: 8 }).then((page) => setActivity(page.rows))
      .catch(() => setActivity([]))
  }, [])

  const stats = useMemo(() => [
    { label: t("home.statSites"), value: siteCount, icon: IconFileText, url: `${ROUTES.CONSOLE}/content`, tone: "blue" as BentoTone },
    { label: t("home.statPostTypes"), value: postTypeCount, icon: IconForms, url: ROUTES.POST_TYPE_ROOT, tone: "indigo" as BentoTone },
  ], [t, siteCount, postTypeCount])

  /*
   * `BentoTile` requires an eyebrow, so it carries the **area** each action leads into rather
   * than the section's own name. The first draft passed `t("home.quickActions")` to all six,
   * which rendered "QUICK ACTIONS" six times under a heading already saying it — a required prop
   * answered with the nearest string to hand, which is how a tile grid becomes noise.
   */
  const quickActions = useMemo(() => [
    { label: t("home.actionNewSite"), area: t("sidebar.content"), icon: IconPlus, url: ROUTES.SITE_NEW, tone: "emerald" as BentoTone },
    { label: t("home.actionBrowseContent"), area: t("sidebar.content"), icon: IconFileText, url: `${ROUTES.CONSOLE}/content`, tone: "blue" as BentoTone },
    { label: t("home.actionSearch"), area: t("sidebar.content"), icon: IconSearch, url: ROUTES.SEARCH, tone: "blue" as BentoTone },
    { label: t("home.actionPostTypes"), area: t("sidebar.content"), icon: IconForms, url: ROUTES.POST_TYPE_ROOT, tone: "indigo" as BentoTone },
    { label: t("home.actionWebhooks"), area: t("sidebar.administration"), icon: IconWebhook, url: ROUTES.ADMIN_WEBHOOKS, tone: "slate" as BentoTone },
    { label: t("home.actionActivity"), area: t("sidebar.administration"), icon: IconHistory, url: ROUTES.ADMIN_ACTIVITY, tone: "slate" as BentoTone },
  ], [t])

  const actionLabel = (action: string) => {
    const key = `activity.action${action.charAt(0)}${action.slice(1).toLowerCase()}`
    const label = t(key)
    return label === key ? action : label
  }

  return (
    <main className="mx-auto max-w-7xl space-y-8 px-6 pb-12">
      <BentoHero
        eyebrow={t("sidebar.dashboard")}
        title={t("home.welcomeTitle")}
        subtitle={t("home.welcomeSubtitle")}
        leading={<IconHome size={28} aria-hidden="true" />}
      />

      {siteCount !== null && postTypeCount !== null && (
        /*
         * `home.overview`, not `home.statSites`: this section holds two different counts, so
         * naming it after one of them is wrong — and the first draft also gave it the hero's own
         * subtitle as a description, which put the same sentence on screen twice, forty pixels
         * apart. A section with nothing to add says nothing.
         */
        <BentoSection title={t("home.overview")}>
          {stats.map((stat) => (
            <BentoCountTile
              key={stat.label}
              to={stat.url}
              icon={stat.icon}
              tone={stat.tone}
              label={stat.label}
              count={stat.value ?? 0}
              span="col-span-2"
            />
          ))}
        </BentoSection>
      )}

      <BentoSection
        title={t("home.quickActions")}
        description={t("home.quickActionsDesc")}
      >
        {quickActions.map((action) => (
          <BentoTile
            key={action.label}
            to={action.url}
            icon={action.icon}
            tone={action.tone}
            eyebrow={action.area}
            title={action.label}
            span="col-span-2"
          />
        ))}
      </BentoSection>

      <BentoSection
        title={t("home.recentActivity")}
        description={t("home.recentActivityDesc")}
        titleHref={ROUTES.ADMIN_ACTIVITY}
        gridClassName="block"
      >
        {activity.length === 0 ? (
          <BentoEmptyState
            icon={IconHistory}
            tone="slate"
            title={t("home.noActivity")}
            action={<Link to={ROUTES.ADMIN_ACTIVITY}>{t("home.viewAll")}</Link>}
          />
        ) : (
          <ul className="divide-border/60 divide-y">
            {activity.map((item) => (
              <li key={item.id} className="flex items-center gap-3 py-2.5">
                <span className={bentoChipClass(ACTION_TONE[item.action])}>
                  <span className={BENTO_TONE_CLASS[ACTION_TONE[item.action] ?? "slate"]} aria-hidden="true" />
                  {actionLabel(item.action)}
                </span>
                <span className="flex-1 truncate text-sm font-medium">{item.description}</span>
                <span className="text-muted-foreground shrink-0 text-xs">
                  {item.date ? new Date(item.date).toLocaleString() : ""}
                </span>
              </li>
            ))}
          </ul>
        )}
      </BentoSection>

      {appVersion && (
        <p className="text-muted-foreground pt-2 text-center text-xs">
          {t("home.version", { version: appVersion })}
        </p>
      )}
    </main>
  )
}
