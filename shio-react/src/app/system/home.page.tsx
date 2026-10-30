/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { activitySentence } from "@/lib/activity-text"
import {
  IconAlertTriangle,
  IconCalendarClock,
  IconChecklist,
  IconChevronRight,
  IconEyeOff,
  IconFileText,
  IconForms,
  IconHistory,
  IconMessage,
  IconPencil,
  IconPlus,
  IconRestore,
  IconRobot,
  IconSearch,
  IconTrash,
  IconWebhook,
  IconWorldUpload,
  type Icon as TablerIcon,
} from "@tabler/icons-react"
import {
  BentoChangeMark,
  BentoEmptyState,
  BentoHero,
  BentoSection,
} from "@viglet/viglet-design-system/bento"
import { Button } from "@/components/ui/button"
import { useMemo } from "react"
import { useTranslation } from "react-i18next"
import { Link } from "react-router-dom"
import { ROUTES } from "@/app/routes.const"
import type { ShHistory } from "@/models/history/history.model"
import { useQuery } from "@tanstack/react-query"
import { addressKey } from "@/lib/query-keys"
import { changeStateOf } from "@/lib/change-mark"
import { ShConsoleSummaryService, type ShRun } from "@/services/console/summary.service"
import { ShNoteService } from "@/services/note/note.service"


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
 * A `BentoHero`, `BentoSection`s, and `BentoEmptyState` for an activity list with nothing in it.
 * SH1311 retired the `BentoCountTile`s and `BentoTile`s: what waits is a list of rows and leads, the
 * counts are one unframed line of figures, and the quick actions are one row of links.
 *
 * ## A row's action is a change, not a colour (SH1211)
 *
 * The activity rows used to sit behind a gradient chip coloured per action, a tone per verb on a
 * list. Since VDS182 a list has one colour, a record's state, so the action's icon sits in a
 * neutral well and the change is `BentoChangeMark`, through the same `changeStateOf` the bulk plan
 * uses: a create is added, a delete is removed, every other action changes something that stays,
 * and the row's second line still names the verb.
 *
 * ## Why the counts wait rather than showing zero
 *
 * The obvious `?? 0` puts **"0 Sites"** on the first thing a curator sees after login, for as long
 * as the request takes. Zero is a real answer to that question and a wrong one. The figures render
 * when the counts arrive.
 */

const summaryService = new ShConsoleSummaryService()
const noteService = new ShNoteService()

/** SH1318 — how many open notes the inbox lists by name; past it, a count row links to them all. */
const INBOX_NOTES = 5

/** SH1050 — one identity each while the summary loads, not one per render. */
const EMPTY_ACTIVITY: ShHistory[] = []
const EMPTY_RUNS: ShRun[] = []

const ACTION_ICON: Record<string, TablerIcon> = {
  CREATE: IconPlus,
  UPDATE: IconPencil,
  PUBLISH: IconWorldUpload,
  UNPUBLISH: IconEyeOff,
  DELETE: IconTrash,
  RESTORE: IconRestore,
}

/*
 * A list row's icon names the kind of thing, in a neutral well (VDS182), and a row's colour, where
 * it has one, is its state.
 */
const LIST_CLASS = "bg-card divide-border/60 divide-y overflow-hidden rounded-xl border"
const ROW_CLASS = "hover:bg-muted/40 flex items-center gap-3 px-4 py-3 transition-colors"

function RowIcon({ icon: Icon }: Readonly<{ icon: TablerIcon }>) {
  return (
    <span className="bg-muted text-muted-foreground grid size-8 shrink-0 place-items-center rounded-lg">
      <Icon size={16} aria-hidden="true" />
    </span>
  )
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
  const runs = summary?.runs ?? EMPTY_RUNS
  const incidents = summary?.diagnostics?.incidents ?? 0

  const stats = useMemo(() => [
    { label: t("home.statSites"), value: siteCount, url: `${ROUTES.CONSOLE}/content` },
    { label: t("home.statPostTypes"), value: postTypeCount, url: ROUTES.POST_TYPE_ROOT },
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
    },
    {
      label: t("home.attentionScheduled"),
      value: attention.scheduledSoon,
      icon: IconCalendarClock,
      url: ROUTES.SCHEDULED,
    },
    {
      label: t("home.attentionNotes"),
      value: attention.openNotes,
      icon: IconMessage,
      // SH1214 — the list it counts, not the content browser, which shows no notes.
      url: ROUTES.NOTES,
    },
    {
      // A window and not a total: the incident log starts empty at boot, so zero means "nothing
      // since this process started". Drawn only when it is not zero, which is when the difference
      // stops mattering — and the trail is the nearest screen that can say what happened.
      label: t("home.attentionIncidents"),
      value: incidents,
      icon: IconAlertTriangle,
      url: ROUTES.ADMIN_ACTIVITY,
    },
  ].filter((tile) => tile.value > 0) : []), [t, attention, incidents])

  /*
   * SH1318 — the inbox: the things themselves, not their count. Each pending agent run with what it
   * said it was doing, and each open note with its page and who left it, each opening where it is
   * resolved. The notes are the notes page's own read, under its key, so the two share one fetch,
   * and they are asked for only when the summary says there are some.
   */
  const openNotes = attention?.openNotes ?? 0
  const { data: notes } = useQuery({
    queryKey: addressKey("summary:notes"),
    queryFn: ({ signal }) => noteService.open({ signal }),
    enabled: openNotes > 0,
  })
  const pendingRuns = runs.filter((run) => run.pending > 0)
  // The runs list below keeps the settled ones: a pending run is already an inbox item above.
  const settledRuns = runs.filter((run) => run.pending === 0)
  const shownNotes = (notes ?? []).slice(0, INBOX_NOTES)
  const inbox = [
    ...pendingRuns.map((run) => ({
      key: `run:${run.session}`, icon: IconRobot, url: ROUTES.ADMIN_REVIEW,
      label: run.intent || run.agent || t("home.runSession", { id: run.session.slice(0, 8) }),
      detail: t("home.runPending", { count: run.pending }),
    })),
    ...shownNotes.map((note) => ({
      key: `note:${note.id}`, icon: IconMessage,
      url: note.address?.startsWith("post:") ? `${ROUTES.CONSOLE}/content/post/${note.objectId}` : ROUTES.NOTES,
      label: note.body,
      detail: [note.address, note.author].filter(Boolean).join(" · "),
    })),
  ]
  // A count row stays only for what the items above do not already list in full.
  const counts = waiting.filter((tile) => !(tile.url === ROUTES.ADMIN_REVIEW && pendingRuns.length > 0)
    && !(tile.url === ROUTES.NOTES && notes !== undefined && openNotes <= INBOX_NOTES))

  /* The first is the one that starts something, and the only one drawn filled. */
  const quickActions = useMemo(() => [
    { label: t("home.actionNewSite"), icon: IconPlus, url: ROUTES.SITE_NEW },
    { label: t("home.actionBrowseContent"), icon: IconFileText, url: `${ROUTES.CONSOLE}/content` },
    { label: t("home.actionSearch"), icon: IconSearch, url: ROUTES.SEARCH },
    { label: t("home.actionPostTypes"), icon: IconForms, url: ROUTES.POST_TYPE_ROOT },
    { label: t("home.actionWebhooks"), icon: IconWebhook, url: ROUTES.ADMIN_WEBHOOKS },
    { label: t("home.actionActivity"), icon: IconHistory, url: ROUTES.ADMIN_ACTIVITY },
  ], [t])

  const actionLabel = (action: string) => {
    const key = `activity.action${action.charAt(0)}${action.slice(1).toLowerCase()}`
    const label = t(key)
    return label === key ? action : label
  }

  return (
    <div className="space-y-8">
      <BentoHero
        eyebrow={t("sidebar.dashboard")}
        title={t("home.welcomeTitle")}
        subtitle={t("home.welcomeSubtitle")}
      />

      {(inbox.length > 0 || counts.length > 0) && (
        /*
         * First: what is owed outranks what exists (P7). SH1311 — as rows, each the thing owed and
         * how many, rather than 390px tiles that gave a count the same weight as the inventory below.
         * SH1318 — and the items themselves first, one click from where each is resolved.
         */
        <BentoSection title={t("home.attention")} description={t("home.attentionDesc")} gridClassName="block">
          <ul className={LIST_CLASS}>
            {inbox.map((item) => (
              <li key={item.key}>
                <Link to={item.url} className={ROW_CLASS}>
                  <RowIcon icon={item.icon} />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-medium">{item.label}</span>
                    {item.detail && <span className="text-muted-foreground block truncate text-xs">{item.detail}</span>}
                  </span>
                  <IconChevronRight size={16} className="text-muted-foreground shrink-0" aria-hidden="true" />
                </Link>
              </li>
            ))}
            {counts.map((tile) => (
              <li key={tile.label}>
                <Link to={tile.url} className={ROW_CLASS}>
                  <RowIcon icon={tile.icon} />
                  <span className="min-w-0 flex-1 truncate text-sm font-medium">{tile.label}</span>
                  <span className="text-foreground shrink-0 text-base font-semibold tabular-nums">{tile.value}</span>
                  <IconChevronRight size={16} className="text-muted-foreground shrink-0" aria-hidden="true" />
                </Link>
              </li>
            ))}
          </ul>
        </BentoSection>
      )}

      {siteCount !== null && postTypeCount !== null && (
        /*
         * `home.overview`, not `home.statSites`: this section holds two different counts. SH1311 — a
         * line of figures, each a number and its label and a link, with no frame: two inventory
         * counts were two 390×140 tiles, mostly white, louder than anything a curator had to do.
         */
        <BentoSection title={t("home.overview")} gridClassName="flex flex-wrap gap-x-10 gap-y-3">
          {stats.map((stat) => (
            <Link key={stat.label} to={stat.url} className="group flex items-baseline gap-2">
              <span className="text-2xl font-semibold tabular-nums">{stat.value ?? 0}</span>
              <span className="text-muted-foreground text-sm group-hover:underline">{stat.label}</span>
            </Link>
          ))}
        </BentoSection>
      )}

      {/*
        SH1311 — one row of links. Six tiles each with an eyebrow and a title were the page's largest
        area. New site keeps the accent: the one action here that starts something (SH1195).
      */}
      <BentoSection title={t("home.quickActions")} gridClassName="flex flex-wrap gap-2">
        {quickActions.map((action, index) => (
          <Button key={action.label} asChild size="sm" variant={index === 0 ? "default" : "outline"}>
            <Link to={action.url}>
              <action.icon size={16} aria-hidden="true" className="mr-1.5" />
              {action.label}
            </Link>
          </Button>
        ))}
      </BentoSection>

      {settledRuns.length > 0 && (
        /*
         * SH1050 — what the agent has been doing, and what it said it was doing.
         *
         * A list rather than a count, because the number is already on the review tile above and
         * the thing a curator needs before opening a diff is the *intent*: a session id says an
         * agent was here, the sentence it declared says what it was trying to do. Each row links
         * into the review queue, which is where the deciding happens — the "review action" the
         * design asks for is the row itself being a way in.
         *
         * Sessions with nothing pending are drawn too. "The agent ran and you have dealt with it"
         * is a different fact from "the agent has not run", and a list showing only the first
         * would make a quiet instance and a busy one look the same.
         */
        <BentoSection
          title={t("home.runs")}
          description={t("home.runsDesc")}
          titleHref={ROUTES.ADMIN_REVIEW}
          gridClassName="block"
        >
          <ul className={LIST_CLASS}>
            {settledRuns.map((run) => {
              const pending = run.pending > 0
              return (
                <li key={run.session}>
                  <Link to={ROUTES.ADMIN_REVIEW} className={ROW_CLASS}>
                    <RowIcon icon={IconRobot} />
                    <span className="min-w-0 flex-1">
                      {/* The intent, or the agent's name, or the session — in that order, because
                          the first is what a person can act on and the last is what is always
                          there. A bare session is shortened: a full UUID is noise to a reader. */}
                      <span className="block truncate text-sm font-medium">
                        {run.intent || run.agent || t("home.runSession", { id: run.session.slice(0, 8) })}
                      </span>
                      <span className={pending ? "text-foreground block text-xs font-medium" : "text-muted-foreground block text-xs"}>
                        {pending ? t("home.runPending", { count: run.pending }) : t("home.runSettled")}
                      </span>
                    </span>
                    <span className="text-muted-foreground shrink-0 text-xs tabular-nums">
                      {t("home.runChanges", { count: run.changes })}
                    </span>
                    <IconChevronRight size={16} className="text-muted-foreground shrink-0" aria-hidden="true" />
                  </Link>
                </li>
              )
            })}
          </ul>
        </BentoSection>
      )}

      <BentoSection
        title={t("home.recentActivity")}
        description={t("home.recentActivityDesc")}
        titleHref={ROUTES.ADMIN_ACTIVITY}
        gridClassName="block"
      >
        {activity.length === 0 ? (
          <BentoEmptyState
            icon={IconHistory}
            title={t("home.noActivity")}
            action={<Link to={ROUTES.ADMIN_ACTIVITY}>{t("home.viewAll")}</Link>}
          />
        ) : (
          <ul className={LIST_CLASS}>
            {activity.map((item) => (
              <li key={item.id} className={ROW_CLASS}>
                <RowIcon icon={ACTION_ICON[item.action] ?? IconHistory} />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium">{activitySentence(item.description, item.action, t)}</span>
                  <span className="text-muted-foreground block text-xs">{actionLabel(item.action)}</span>
                </span>
                <BentoChangeMark state={changeStateOf(item.action.toLowerCase())} compact />
                {item.date && (
                  <time
                    dateTime={item.date}
                    className="text-muted-foreground shrink-0 text-xs tabular-nums"
                  >
                    {new Date(item.date).toLocaleString(undefined, { dateStyle: "short", timeStyle: "short" })}
                  </time>
                )}
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
    </div>
  )
}
