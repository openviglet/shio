/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import {
  IconCalendarClock,
  IconChecklist,
  IconFileText,
  IconForms,
  IconHistory,
  IconHome,
  IconMessage,
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
import { useMemo } from "react"
import { useTranslation } from "react-i18next"
import { Link } from "react-router-dom"
import { ROUTES } from "@/app/routes.const"
import type { ShHistory } from "@/models/history/history.model"
import { useQuery } from "@tanstack/react-query"
import { addressKey } from "@/lib/query-keys"
import { ShConsoleSummaryService } from "@/services/console/summary.service"


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

const summaryService = new ShConsoleSummaryService()

/** SH1050 — one identity while the summary loads, not one per render. */
const EMPTY_ACTIVITY: ShHistory[] = []

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
   * SH1050 — one read for the whole screen.
   *
   * SH1046 had already made these cached, and cached is not the same as cheap: `useSites()` still
   * transfers every site so that `.length` can be taken, which is what this page did with the post
   * types too. `/v2/console/summary` answers with count queries and carries the activity rows, so
   * four requests became one and an instance with four hundred sites costs what one with four does.
   *
   * `null` while it is pending, deliberately: zero is a real answer to "how many sites" and a wrong
   * one, and "0 Sites" is a poor first thing to show somebody who has just logged in.
   */
  const { data: summary, isPending } = useQuery({
    queryKey: addressKey("summary:"),
    queryFn: ({ signal }) => summaryService.get({ signal }),
  })
  const siteCount = isPending ? null : (summary?.sites ?? 0)
  const postTypeCount = isPending ? null : (summary?.postTypes ?? 0)
  const appVersion = summary?.version ?? ""
  const activity = summary?.activity ?? EMPTY_ACTIVITY
  const attention = summary?.attention

  const stats = useMemo(() => [
    { label: t("home.statSites"), value: siteCount, icon: IconFileText, url: `${ROUTES.CONSOLE}/content`, tone: "blue" as BentoTone },
    { label: t("home.statPostTypes"), value: postTypeCount, icon: IconForms, url: ROUTES.POST_TYPE_ROOT, tone: "indigo" as BentoTone },
  ], [t, siteCount, postTypeCount])

  /*
   * SH1050 — what is waiting for a person, which is the dashboard's real question here.
   *
   * "How much content exists" is a number nobody acts on. In a CMS an agent drives, the thing a
   * curator opens the console to find out is what needs *them*: a review queue somebody has to
   * decide on, a publish that happens this week whether or not anybody remembers, a note left open
   * on a page. Each tile links to the screen that acts on it, which is the difference between a
   * dashboard and a report.
   *
   * A tile with nothing in it is not drawn. A row of zeroes is a screen that trains its reader to
   * stop looking, and the point of this section is that a number on it means something is owed.
   */
  const waiting = useMemo(() => (attention ? [
    {
      // The count is sessions and the label carries the changes inside them, because a reviewer
      // deciding on two sessions of ninety writes is not doing the same work as two of three.
      label: t("home.attentionReview", { count: attention.reviewChanges }),
      value: attention.reviewSessions,
      icon: IconChecklist,
      url: ROUTES.ADMIN_REVIEW,
      tone: "amber" as BentoTone,
    },
    {
      label: t("home.attentionScheduled"),
      value: attention.scheduledSoon,
      icon: IconCalendarClock,
      url: ROUTES.SCHEDULED,
      tone: "violet" as BentoTone,
    },
    {
      label: t("home.attentionNotes"),
      value: attention.openNotes,
      icon: IconMessage,
      url: `${ROUTES.CONSOLE}/content`,
      tone: "rose" as BentoTone,
    },
  ].filter((tile) => tile.value > 0) : []), [t, attention])

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

      {waiting.length > 0 && (
        /* First, above the counts: what is owed outranks what exists. */
        <BentoSection title={t("home.attention")} description={t("home.attentionDesc")}>
          {waiting.map((tile) => (
            <BentoCountTile
              key={tile.label}
              to={tile.url}
              icon={tile.icon}
              tone={tile.tone}
              label={tile.label}
              count={tile.value}
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
