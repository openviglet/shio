/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import {
  BentoCommandPalette,
  BentoNavRail,
  BentoPaletteTrigger,
  BentoShell,
  BentoShortcutsDialog,
  BentoUserMenu,
  useBentoShellShortcuts,
} from "@viglet/viglet-design-system/bento"
import { columnFor } from "./bento-column"
import { useQuery } from "@tanstack/react-query"
import { addressKey } from "@/lib/query-keys"
import type { ShConsoleSearchResult } from "@/services/search/search.service"
import { useCallback, useEffect, useMemo, useState } from "react"
import { Link, Outlet, useLocation, useNavigate } from "react-router-dom"
import { RouteBoundary } from "@/components/route-boundary"
import { ShLogo } from "@/components/logo/sh-logo"
import { ModeToggle } from "@/components/mode-toggle"
import { useTranslation } from "react-i18next"
import { ROUTES } from "@/app/routes.const"
// SH1028 — the palette answers with content and verbs, not only with destinations.
import { commandsFor, contextOf } from "./bento-commands"
import { rank, remember } from "@/lib/command-match"
import { lifecycleKey, paletteDescription } from "@/lib/post-lifecycle"
import { ShSearchService } from "@/services/search/search.service"
import { UserProvider } from "@/contexts/user.context"
import { useBentoNav, useBentoNavItems } from "./bento-nav"
import { useAgentRunReports } from "./bento-agent-runs"
import { useScheduleReports } from "./bento-schedule-reports"
import { useArrivalReports } from "./bento-arrival-reports"
import { BentoDockPreferences } from "./bento-dock-preferences"
import { useDockPreferencesLoad } from "./use-dock-preferences"
import { useDockChatRelay } from "./use-dock-chat-relay"
import { BentoStatusDock } from "./bento-status-dock"
import { BentoTenantLinkNotice } from "./bento-tenant-link-notice"
import { BentoTenantSwitcher } from "./bento-tenant-switcher"

/**
 * SH888 — the bento shell, mounted beside the console one rather than replacing it.
 *
 * ## Why a second route tree and not a migration in place
 *
 * `console.root.page.tsx` wraps everything in a `SidebarProvider`, and its `PageHeader` calls
 * `SidebarTrigger`. The bento shell ships **no provider at all** — the rail is fixed, one width,
 * and hidden below `md`, so a provider would be state nothing reads — and a bento component
 * reaching for the sidebar context inside the console renders blank. The two are not compatible,
 * which means a page-at-a-time migration in place leaves the console broken in the middle of
 * every one of 43 slices.
 *
 * So both trees are mounted and each surface moves when its own task ships. The cost is two
 * shells for the length of Block AR; what it buys is that **every slice is independently
 * reversible**, which across 43 pages is worth more. SH909 is the task that removes whichever
 * shell loses, and it is on the roadmap precisely so the temporary state cannot become the
 * permanent one by nobody deciding.
 *
 * ## The shell is the package's, and no context between its pieces
 *
 * `BentoShell` (VDS131, SH946) takes the rail and the header's controls as props and owns the
 * rest: the rail's gutter, the sticky header, a labelled `main` whose reading column is chosen
 * by name, a skip link, focus moved to the page's heading on navigation, and the corner where
 * back-to-top sits. The rail is still `position: fixed`, and the shell reserves its gutter
 * because it was handed one — no wrapper has to remember the class.
 *
 * ## Whose UserProvider, and the thing that made it visible
 *
 * Shio's — and since SH920 that is the same thing as the package's, which is the point.
 *
 * `BentoUserMenu` calls the design system's own `useCurrentUser`, and a local variant of a
 * context is a *different context object*: the shell's first render came up entirely blank
 * behind the error boundary with `useCurrentUser must be used within UserProvider`. Nothing in
 * `tsc` said so — both are named `UserProvider` and both take children.
 *
 * SH888 worked around it by mounting the package's provider here directly, which left the two
 * trees on two contexts and put the same trap in front of all fourteen Block AR surfaces.
 * SH920 removed the seam instead: `@/contexts/user.context` now *wraps* the package's provider
 * and republishes its value as `ShCurrentUser`, so one mount satisfies both hooks from one
 * fetch. This file imports Shio's again and needs to know nothing about it.
 *
 * SH936 put the review queue's pending count in the header, because the rail carries no badge.
 * SH968 moved it to where the system reports: `useAgentRunReports` reads the change stream and
 * tells the dock when an agent run starts and what it left when it ends, and turns the count read at
 * load into one report. The header holds the tenant switcher and the account menu, and no count.
 */
/** One row the palette offers, whichever half it came from. */
interface PaletteRow {
  id: string
  label: string
  description?: string
  route: string
  confirm: boolean
  aliases?: readonly string[]
  contextual?: boolean
}

const searchService = new ShSearchService()

/** How long the palette waits before asking the server. A keystroke is not a query. */
const SEARCH_DEBOUNCE_MS = 200

/**
 * SH1317 — the provider is mounted above the shell, not inside it, because the shell's own hooks
 * read who is signed in: the rail is chosen by role, and `useBentoNav` ran above the provider it
 * rendered, where `useCurrentUser` throws.
 */
export default function BentoRootPage() {
  return (
    <UserProvider>
      <BentoShellRoot />
    </UserProvider>
  )
}

function BentoShellRoot() {
  const groups = useBentoNav()
  useAgentRunReports()
  useScheduleReports()
  useArrivalReports()
  useDockPreferencesLoad()
  const { onSend } = useDockChatRelay()
  const items = useBentoNavItems()
  const [paletteOpen, setPaletteOpen] = useState(false)
  const [shortcutsOpen, setShortcutsOpen] = useState(false)
  // SH971 — what the dock reports, chosen per person. SH1207 — opened from the dock's own panel.
  const [dockPreferencesOpen, setDockPreferencesOpen] = useState(false)

  /*
   * SH1097 — the shell's keys are the package's one binding set (VDS149), bound by the hook that
   * the shortcuts sheet lists from. SH889 bound Ctrl/Cmd+K and `/` here by hand while the sheet
   * advertised a `?` nothing bound and never listed `/`: two declarations of one keymap, wrong both
   * ways. Now the sheet cannot name a key this shell does not answer, and `?` opens it. The
   * platform is the package's to detect, in the hook and the sheet alike.
   */
  useBentoShellShortcuts({
    onPalette: () => setPaletteOpen((open) => !open),
    onShortcuts: () => setShortcutsOpen((open) => !open),
  })

  /*
   * SH1028 — what the palette answers with, and why it is one group and not three.
   *
   * The design asked for three — actions, content, destinations. The package takes the nav array
   * plus **one** product group (VDS123), so destinations stay where they were and the other two
   * share a group, actions first. That is not a compromise on the ordering: the palette does not
   * reorder a product group, so "actions above content" is a fact this file decides and the
   * component preserves. It is a compromise on the *headings*, and a second group is a package
   * change rather than a Shio one.
   */
  const { t } = useTranslation()
  const navigate = useNavigate()
  const location = useLocation()
  const [query, setQuery] = useState("")
  const [recent, setRecent] = useState<string[]>([])

  const context = useMemo(() => contextOf(location.pathname), [location.pathname])

  /**
   * The actions, ranked here because nothing else can (VDS123).
   *
   * Contextual ones first — "publish this page" is a different offer on a post than in a folder
   * listing — then match quality, then what was run last. `command-match.ts` holds the rule, and
   * holds it as a function of two strings so it can be asserted without rendering anything.
   */
  const actions = useMemo(
    () => rank<PaletteRow>(commandsFor(context).map((command) => ({
      id: command.id,
      label: t(command.labelKey),
      aliases: command.aliases,
      contextual: command.contextualHere,
      confirm: command.confirm,
      route: command.route(context) ?? "",
    })), query, recent),
    [context, query, recent, t],
  )

  /**
   * The content half, from the console's own search (SH1027) — debounced and cancellable.
   *
   * Cancellable matters more than debounced: a slow answer to `pri` arriving after a fast answer
   * to `pricing` would replace the right rows with stale ones, and the reader would see the
   * palette go backwards as they typed. The token is checked on arrival rather than the request
   * aborted, because an abort is a network saving and this is a correctness one.
   */
  /*
   * SH1157 — the palette's search, and the last of the token guards this task set out to replace.
   *
   * `queryToken` was a ref compared against itself in three callbacks, so a superseded answer was
   * *ignored* — the server was still asked for it, and still answered. SH1047's note names that
   * pattern; the signal stops the request instead, and a query key does the ignoring for free because
   * an answer under a key nobody is asking for is not rendered.
   *
   * The debounce becomes a committed term rather than a timer around a request: `query` is what the
   * curator has typed and `term` is what has been asked for, which is also what makes "searching"
   * honest — it is true while the two disagree, or while the request for `term` is in flight, and not
   * merely because a keystroke happened.
   */
  const [term, setTerm] = useState("")
  const wanted = query.trim().length < 2 ? "" : query.trim()

  useEffect(() => {
    // The state is set inside the timer, never synchronously: `react-hooks/set-state-in-effect`.
    const timer = setTimeout(() => setTerm(wanted), SEARCH_DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [wanted])

  const read = useQuery<ShConsoleSearchResult>({
    queryKey: addressKey(`summary:palette|${term}`),
    queryFn: ({ signal }) => searchService.search({ q: term }, 8, { signal }),
    enabled: term.length >= 2,
  })
  const found = useMemo<PaletteRow[]>(() => (read.data?.results ?? []).map((row) => ({
    id: row.address,
    label: row.title || row.address,
    // SH1324 — live or not, by `published`: `state` is the working row, DRAFT on a live post too.
    description: paletteDescription(row.type, t(lifecycleKey(row.published, row.changed))),
    route: row.id ? `${ROUTES.CONTENT_ROOT}/post/${row.id}` : "",
    confirm: false,
  })), [read.data, t])
  const searching = wanted !== term || (term.length >= 2 && read.isFetching)

  const onPaletteQuery = useCallback((next: string) => setQuery(next), [])

  const paletteGroup = useMemo(() => ({
    label: t("palette.group"),
    pending: searching,
    items: [...actions, ...found].map((row) => ({
      id: row.id,
      label: row.label,
      description: row.description,
    })),
    onSelect: (chosen: { id: string }) => {
      const row = [...actions, ...found].find((each) => each.id === chosen.id)
      if (!row?.route) return
      setRecent((previous) => remember(previous, row.id))
      // Every row navigates. A command that writes routes to the flow that ASKS — a plan, a form,
      // a dry run — because a palette is where people press Enter fast, and fast is exactly what
      // turns a mis-typed Enter into a published page (P7).
      navigate(row.route)
    },
  }), [actions, found, navigate, searching, t])

  return (
    <>
      {/*
        SH946 — the package's shell owns the rail's gutter, the header, `main` with its reading
        column, and the corner. It replaced a hand-composed `div`/`header`/gutter here, and with
        it every page's own width, gutter and padding: the column is `columnFor`'s answer, and a
        page sets none of the three. The header stays `sticky`, so `BackendStatusProvider`'s
        sticky retry bar above this tree keeps its place instead of being covered.
      */}
      <BentoShell
        rail={<BentoNavRail groups={groups} homeRoute={ROUTES.BENTO} />}
        headerStart={
          <>
            {/*
              The shell's contract puts the mark, the wordmark and the palette trigger here, and
              marks the first and the last required. Left empty, the header carried no product
              name at all — and below `md`, where the rail hides, no way to the palette but a key.
            */}
            <Link to={ROUTES.BENTO} className="flex items-center gap-2">
              <ShLogo size={28} />
              <span className="text-base font-semibold tracking-tight">Shio</span>
            </Link>
            <BentoPaletteTrigger onClick={() => setPaletteOpen(true)} />
          </>
        }
        headerEnd={
          <>
            {/*
              SH934 — the tenant switch is an identity action, so it sits beside the identity
              menu, and renders nothing unless the reader belongs to more than one tenant.
            */}
            <BentoTenantSwitcher />
            {/*
              SH1216 — the ground: light, dark or the system's. The shell contract marks it
              required on the trailing edge, before the user menu; the ThemeProvider it sets is
              App's, so the choice holds across the console, login and setup alike.
            */}
            <ModeToggle />
            <BentoUserMenu
              accountRoute={ROUTES.ADMIN_USERS}
              logoutUrl={ROUTES.LOGOUT}
              onOpenShortcuts={() => setShortcutsOpen(true)}
            />
          </>
        }
        column={columnFor(location.pathname)}
        dock={<BentoStatusDock onSend={onSend} onOpenSettings={() => setDockPreferencesOpen(true)} />}
      >
        {/*
          SH931 — above the Outlet, so it sits over whatever page the link led to rather than
          inside it; it renders nothing unless the URL names a tenant that is not the session's.
        */}
        <BentoTenantLinkNotice />
        {/*
          SH1049 — one screen's failure stays inside that screen: a throw under this boundary
          leaves the rail, the header and the palette alive.
        */}
        <RouteBoundary>
          <Outlet />
        </RouteBoundary>
      </BentoShell>

      {/*
        Both read the *same* declaration the rail does — that is SH889's whole point. They
        render beside the shell, which carries no state for them.
      */}
      <BentoCommandPalette
        open={paletteOpen}
        onOpenChange={setPaletteOpen}
        items={items}
        group={paletteGroup}
        onQueryChange={onPaletteQuery}
      />
      <BentoShortcutsDialog open={shortcutsOpen} onOpenChange={setShortcutsOpen} />
      <BentoDockPreferences open={dockPreferencesOpen} onOpenChange={setDockPreferencesOpen} />
    </>
  )
}
