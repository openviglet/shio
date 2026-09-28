/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { IconUpload, IconWorld } from "@tabler/icons-react"
import { BentoEntityTile, BentoListPage } from "@viglet/viglet-design-system/bento"
import { formatDistanceToNow } from "date-fns"
import { Search } from "lucide-react"
import { useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { Link } from "react-router-dom"
import { ROUTES } from "@/app/routes.const"
import { Input } from "@/components/ui/input"
import type { ShSite } from "@/models/site/site"
import { useSites } from "@/hooks/use-catalogue"
import { headlineOf, problemOf } from "@/lib/problem"


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
 * four hundred wants something cleverer. `headerAction` is the seam, so the input lives there and
 * the page filters before handing the array over. Dropping the filter would have been a
 * regression on the one console list most likely to grow.
 *
 * ## What is deliberately gone
 *
 * The Sites/Tasks tab strip. `tasks` rendered `content.noPendingTasks` and nothing else, in every
 * state, since it was written — a tab that is always empty is a tab that teaches a curator not to
 * look at tabs. The console page keeps it until SH909 removes that tree.
 */
export default function BentoSiteListPage() {
  const { t } = useTranslation()
  const [filter, setFilter] = useState("")
  // SH1046 — one cached read under `site:`, shared with the nine other screens that ask.
  const { data, error: failure } = useSites()
  // `undefined` means "still loading" to the package's gate, so a failure has to become an empty
  // array *and* an error — otherwise a failed load spins for ever.
  const sites = data ?? (failure ? [] : undefined)
  const error = failure ? headlineOf(problemOf(failure), failure.message) : null

  const visible = useMemo(
    () => sites?.filter((s) => s.name.toLowerCase().includes(filter.toLowerCase().trim())),
    [sites, filter],
  )

  return (
    <BentoListPage
      items={visible}
      error={error}
      tryAgainUrl={ROUTES.BENTO_CONTENT}
      listId="sites"
      eyebrow={t("sidebar.content")}
      title={t("content.sites")}
      subtitle={t("content.selectYourSite")}
      tone="blue"
      headerAction={
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
      renderTile={(site: ShSite, emphasis) => (
        <BentoEntityTile
          to={`${ROUTES.CONSOLE}/content/object/${site.id}`}
          emphasis={emphasis}
          defaultIcon={IconWorld}
          tone="blue"
          title={site.name}
          description={site.description}
          meta={site.date ? formatDistanceToNow(new Date(site.date), { addSuffix: true }) : undefined}
        />
      )}
      emptyTitle={t("common.noResults")}
      emptyDescription={t("content.createFirstSite")}
    />
  )
}
