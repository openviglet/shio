/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import {
  IconDownload, IconExternalLink, IconFolderOpen, IconTrash, IconUpload, IconWorld,
} from "@tabler/icons-react"
import { toast } from "@viglet/viglet-design-system"
import {
  BentoActionsMenu, BentoChangeCell, BentoIdentityCell, BentoListPage, type BentoActionsMenuItem,
  type BentoDataTableAction, type BentoDataTableColumn,
} from "@viglet/viglet-design-system/bento"
import { Search } from "lucide-react"
import { useCallback, useMemo, useState } from "react"
import { useCurrentUser } from "@/contexts/user.context"
import {
  defaultSitesView, initialsOf, readSitesView, sitesViewKey, type SitesView,
} from "@/lib/site-list-view"
import { useTranslation } from "react-i18next"
import { Link, useNavigate } from "react-router-dom"
import { ROUTES } from "@/app/routes.const"
import { Input } from "@/components/ui/input"
import type { ShSite } from "@/models/site/site"
import { useInvalidate, useSites } from "@/hooks/use-catalogue"
import { useSiteThumbnails } from "@/hooks/use-site-thumbnails"
import { headlineOf, problemOf, reportProblem } from "@/lib/problem"
import { publishedDestination, siteDestination } from "@/lib/site-front-end"
import { ShSiteService } from "@/services/site/site.service"


/**
 * SH899 — the site list, and the shape roughly ten more list screens will copy.
 *
 * ## What `BentoListPage` takes over
 *
 * The hero, the mosaic, the dashed "New" tile, the empty state and the try-again link. The
 * console page hand-rolled all five, and the ones it got wrong are the ones a hand-rolled list
 * always gets wrong: a load failure rendered the empty state, so "no sites" and "the request
 * failed" were the same screen.
 *
 * ## The row actions moved to the entity, and that is the point
 *
 * The console row carried export, edit, view-site and delete behind
 * `opacity-0 group-hover:opacity-100` — **four actions reachable only by a mouse**. They are on
 * the site's own page now, where `BentoEntityShell` gives them a menu that is focusable and
 * announced. A tile navigates; an entity acts. That is the division the rest of Block AR copies,
 * and it is worth stating because "the tile has no buttons" reads as a regression until you know
 * where they went.
 *
 * ## The filter is the product's, in the slot the package left
 *
 * `BentoListPage` has no search, deliberately — a list of four does not want one and a list of
 * four hundred wants something cleverer. `toolbar` is the seam (SH1221 moved it there from the
 * deprecated `headerAction`, so the filter sits in the bar over the rows it narrows), and the page
 * filters before handing the array over. Dropping the filter would have been a regression on the
 * one console list most likely to grow.
 *
 * ## What is deliberately gone
 *
 * The Sites/Tasks tab strip. `tasks` rendered `content.noPendingTasks` and nothing else, in every
 * state, since it was written — a tab that is always empty is a tab that teaches a curator not to
 * look at tabs. The console page keeps it until SH909 removes that tree.
 */
/** Where a site's row leads: its root listing in the content browser. */
const browse = (site: ShSite) => `${ROUTES.CONSOLE}/content/object/${site.id}`

const siteService = new ShSiteService()

/** When a site last changed, as a number to sort by; its creation when nothing was recorded. */
function changedAt(site: ShSite): number {
  const at = site.lastChange?.at ?? site.date
  return at ? Date.parse(at) || 0 : 0
}

/** SH1219 — the site's newest change and who made it, or its creation date alone. */
function LastChanged({ site }: Readonly<{ site: ShSite }>) {
  if (site.lastChange) {
    const { at, actor, agent } = site.lastChange
    return <BentoChangeCell at={at} actor={actor} agent={agent} />
  }
  return site.date ? <BentoChangeCell at={site.date} /> : null
}

/**
 * SH1219 — whether a site is used: its live and draft pages as a bar, with the numbers beside it.
 * A page live with a pending draft counts under both, as the listing reports it (SH1217).
 *
 * SH1309 — in neutral fills. The accent is the primary action's colour, and sixteen orange meters
 * down a list read as sixteen warnings about sites that were fine. Only colour utilities the design
 * system's sheet ships paint here (the console's Tailwind has no colour theme), and its old
 * `bg-muted-foreground/40` is not one: the draft share had computed to transparent since SH1219.
 */
function PagesBar({ published, draft }: Readonly<{ published: number; draft: number }>) {
  const { t } = useTranslation()
  const total = published + draft
  if (total === 0) return <span className="text-muted-foreground text-sm">{t("content.noPagesYet")}</span>
  return (
    <div className="flex w-full min-w-0 items-center gap-2">
      <div className="bg-muted flex h-1.5 min-w-12 flex-1 overflow-hidden rounded-full" aria-hidden="true">
        <div className="bg-muted-foreground/60" style={{ width: `${(published / total) * 100}%` }} />
        <div className="bg-border" style={{ width: `${(draft / total) * 100}%` }} />
      </div>
      <span className="text-muted-foreground whitespace-nowrap text-xs tabular-nums">
        {t("content.sitePages", { published, draft })}
      </span>
    </div>
  )
}

/**
 * SH1220 — a site as a card: its published home's picture, then exactly the row's facts. A site
 * with no stored picture shows its initials and says so, rather than a placeholder that reads as a
 * page. The card is one link, so the focus ring is the card's; the menu is the row's.
 */
function SiteCard({ site, actions }: Readonly<{ site: ShSite; actions: BentoActionsMenuItem[] }>) {
  const { t } = useTranslation()
  return (
    <div className="bg-card focus-within:ring-ring relative flex h-full flex-col overflow-hidden rounded-xl border focus-within:ring-2">
      <Link to={browse(site)} aria-label={site.name} className="flex flex-1 flex-col outline-none">
        <div className="bg-muted aspect-[16/10] w-full overflow-hidden">
          {site.thumbnail ? (
            <img
              src={`${site.thumbnail.url}?v=${encodeURIComponent(site.thumbnail.digest)}`}
              alt="" loading="lazy" className="size-full object-cover object-top"
            />
          ) : (
            <div className="text-muted-foreground flex size-full flex-col items-center justify-center gap-1">
              <span aria-hidden="true" className="text-2xl font-semibold">{initialsOf(site.name)}</span>
              <span className="text-xs">{t("content.noHomePageYet")}</span>
            </div>
          )}
        </div>
        <div className="flex flex-col gap-2 p-3">
          <BentoIdentityCell name={site.name} detail={site.description?.trim() || undefined} />
          <PagesBar published={site.pages?.published ?? 0} draft={site.pages?.draft ?? 0} />
          <LastChanged site={site} />
        </div>
      </Link>
      <div className="absolute top-2 right-2">
        <BentoActionsMenu actions={actions} />
      </div>
    </div>
  )
}

/** SH1220 — the reader's own choice of view, per person, in this browser. Storage may refuse. */
function useSitesView(username: string | undefined): [SitesView | undefined, (view: SitesView) => void] {
  const key = sitesViewKey(username)
  const [view, setView] = useState<SitesView | undefined>(() => {
    try { return readSitesView(localStorage.getItem(key)) } catch { return undefined }
  })
  const store = useCallback((next: SitesView) => {
    setView(next)
    try { localStorage.setItem(key, next) } catch { /* private mode: this session only */ }
  }, [key])
  return [view, store]
}

export default function BentoSiteListPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const invalidate = useInvalidate()
  const { user } = useCurrentUser()
  const [chosenView, chooseView] = useSitesView(user?.username)
  const [filter, setFilter] = useState("")
  // SH1046 — one cached read under `site:`, shared with the nine other screens that ask.
  const { data, error: failure } = useSites()
  // `undefined` means "still loading" to the package's gate, so a failure has to become an empty
  // array *and* an error — otherwise a failed load spins for ever.
  const sites = data ?? (failure ? [] : undefined)
  const error = failure ? headlineOf(problemOf(failure), failure.message) : null
  // SH1218 — keeps each site's home picture current, in the background, for the grid (SH1220).
  useSiteThumbnails(data)

  // SH1219 — newest change first, which is the order a curator scans for "what is alive".
  const visible = useMemo(
    () => sites
      ?.filter((s) => s.name.toLowerCase().includes(filter.toLowerCase().trim()))
      .sort((a, b) => changedAt(b) - changedAt(a)),
    [sites, filter],
  )

  /*
   * SH1194 — fifteen sites are records a curator compares by name and by when they last changed,
   * not by picture, so they are rows (VDS183). The mosaic gave the first site a double cell with a
   * glow, which made it look more important than the other fourteen for no reason the data had.
   *
   * SH1219 — three columns, each a fact the listing's one read carries (SH1217). Description had
   * half the width for one site in twenty-three; it is the identity cell's second line now. Pages
   * says whether a site is used, and Last changed says when and by whom, marked when an agent did
   * it, instead of one date repeated down the list.
   *
   * SH1309 — and nothing where there is no description. The `site:<name>` it fell back to is the
   * agent's address, which a curator does not read, and it doubled every row's text. So the column
   * asks for two lines (SH1314's taller row) only while some site has a description to show.
   */
  const twoLines = Boolean(sites?.some((site) => site.description?.trim()))
  const columns = useMemo<BentoDataTableColumn<ShSite>[]>(() => [
    {
      id: "name", header: t("common.name"), width: "minmax(16rem, 3fr)", hideable: false,
      lines: twoLines ? 2 : 1,
      sortValue: (site) => site.name.toLowerCase(),
      cell: (site) => (
        <Link to={browse(site)} className="block min-w-0 hover:underline">
          <BentoIdentityCell name={site.name} detail={site.description?.trim() || undefined} />
        </Link>
      ),
    },
    {
      id: "pages", header: t("content.sitePagesHeader"), width: "minmax(12rem, 2fr)",
      sortValue: (site) => (site.pages?.published ?? 0) + (site.pages?.draft ?? 0),
      cell: (site) => <PagesBar published={site.pages?.published ?? 0} draft={site.pages?.draft ?? 0} />,
    },
    {
      id: "changed", header: t("content.lastChanged"), width: "minmax(10rem, 1fr)", lines: 2,
      sortValue: (site) => changedAt(site) || null,
      cell: (site) => <LastChanged site={site} />,
    },
  ], [t, twoLines])

  const handleDelete = async (site: ShSite) => {
    if (!confirm(t("dialog.deleteMessage"))) return
    try {
      // SH648 — the trash, so this stays reversible from the trash page.
      await siteService.delete(site.id)
      await invalidate("site:")
      toast.success(t("common.deleted"))
    } catch (error) {
      reportProblem(error, t("common.error"))
    }
  }

  // SH1219 — the verbs SH899 moved to the site's own page come back to the row, as a focusable menu
  // rather than buttons that appear on hover, which is the rule SH899 was keeping.
  const rowActions: BentoDataTableAction<ShSite>[] = [
    {
      id: "preview", label: t("content.preview"), icon: IconExternalLink,
      onSelect: ([site]) => window.open(siteDestination(site).url, "_blank", "noopener"),
    },
    {
      // SH1304 — what a visitor sees, beside the curator's preview.
      id: "published", label: t("content.viewPublishedSite"), icon: IconWorld,
      onSelect: ([site]) => window.open(publishedDestination(site).url, "_blank", "noopener"),
    },
    { id: "open", label: t("content.openSite"), icon: IconFolderOpen, onSelect: ([site]) => navigate(browse(site)) },
    {
      id: "export", label: t("content.export"), icon: IconDownload,
      onSelect: ([site]) => {
        // SH1065 — the server's own filename, so this download and `shio export` write one file.
        void siteService.export(site.id).then(({ blob, fileName }) => {
          const href = URL.createObjectURL(blob)
          const a = document.createElement("a")
          a.href = href
          a.download = fileName ?? `${site.name}.zip`
          a.click()
          URL.revokeObjectURL(href)
        }).catch((error) => reportProblem(error, t("common.error")))
      },
    },
    {
      id: "delete", label: t("common.delete"), icon: IconTrash, tone: "destructive",
      onSelect: (rows) => { for (const site of rows) void handleDelete(site) },
    },
  ]

  // SH1220 — the card's menu is the row's, one site at a time.
  const cardActions = (site: ShSite): BentoActionsMenuItem[] => rowActions.map((action) => ({
    id: action.id, label: action.label, icon: action.icon, tone: action.tone,
    onSelect: () => action.onSelect([site]),
  }))
  // Controlled, so the default can follow the count once the read arrives: a grid up to thirty
  // sites, rows past it, and whatever this reader last picked over either.
  const view = chosenView ?? defaultSitesView(sites?.length ?? 0)

  return (
    <BentoListPage
      items={visible}
      error={error}
      tryAgainUrl={ROUTES.BENTO_CONTENT}
      listId="sites"
      eyebrow={t("sidebar.content")}
      title={t("content.sites")}
      // SH1309 — what the list holds, not an instruction the list already makes obvious.
      subtitle={sites ? t("content.siteCount", { count: sites.length }) : ""}
      toolbar={
        <div className="flex items-center gap-2">
          <div className="relative">
            <Search aria-hidden="true" className="text-muted-foreground absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2" />
            <Input
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
              placeholder={t("content.findSite")}
              aria-label={t("content.findSite")}
              className="w-56 pl-10"
            />
          </div>
          <Link
            to={ROUTES.BENTO_SITE_IMPORT}
            className="border-input hover:bg-accent hover:text-accent-foreground focus-visible:ring-ring inline-flex h-9 items-center gap-2 rounded-md border px-3 text-sm font-medium transition-colors focus-visible:ring-1 focus-visible:outline-none"
          >
            <IconUpload size={16} aria-hidden="true" />
            {t("content.import")}
          </Link>
        </div>
      }
      newRoute={ROUTES.BENTO_SITE_NEW}
      newLabel={t("content.newSite")}
      itemKey={(site: ShSite) => site.id}
      columns={columns}
      rowActions={rowActions}
      renderTile={(site: ShSite) => <SiteCard site={site} actions={cardActions(site)} />}
      view={view}
      onViewChange={chooseView}
      getRowLabel={(site: ShSite) => site.name}
      onRowOpen={(site: ShSite) => navigate(browse(site))}
      emptyTitle={t("common.noResults")}
      emptyDescription={t("content.createFirstSite")}
    />
  )
}
