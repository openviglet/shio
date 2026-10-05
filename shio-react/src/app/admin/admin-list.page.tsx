/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useQuery } from "@tanstack/react-query"
import {
  BentoChangeCell, BentoIdentityCell, BentoListPage, type BentoDataTableColumn,
} from "@viglet/viglet-design-system/bento"
import { useMemo, type ReactNode } from "react"
import { useTranslation } from "react-i18next"
import { Link, useNavigate } from "react-router-dom"

import { headlineOf, problemOf } from "@/lib/problem"
import { addressKey } from "@/lib/query-keys"

/** What one record shows. SH1194 — a row of the table now, with the fields the mosaic tile took. */
export interface AdminTile {
  to: string
  title: string
  description?: string | null
  /** The one line under the title that is not the description — an owner, a URL, a scope. */
  meta?: ReactNode
  /** `0`/`1`, because the package's pill is numeric and every one of these models is boolean. */
  enabled?: number
  hasStatus?: boolean
  /**
   * SH1222 — the one date a record of this kind has, for the entities whose model carries one:
   * when a token, webhook or tenant was made, and since SH1238 when a user last signed in. Which
   * date it is, the list says through `dateHeader`.
   */
  dated?: string | null
}

export interface AdminListPageProps<T> {
  /**
   * What this list is, in the address grammar (SH1046) — `"group:"`, `"role:"`, `"user:"`.
   *
   * <p>The cache key, so the eight lists built from this component share one entry each rather than
   * refetching on every visit. It is a required prop and not derived from `listRoute`: a route is a
   * place in the console and an address is a thing in the instance, and two lists of one thing
   * under two routes must not be two cache entries.</p>
   */
  address: string
  query: () => Promise<T[]>
  listRoute: string
  newRoute: string
  eyebrow: string
  title: string
  subtitle: string
  newLabel: string
  emptyTitle: string
  emptyDescription: string
  itemKey: (item: T) => string
  tileOf: (item: T) => AdminTile
  /**
   * The list's own controls, a filter or a secondary action, in the package's one bar between the
   * hero and the rows (SH1221); New stays the hero's primary.
   */
  toolbar?: ReactNode
  /** What the tiles' `dated` is, as its column header. Defaults to Created. */
  dateHeader?: string
}

/**
 * SH905 — one admin list, seven configurations.
 *
 * ## What this replaces
 *
 * Seven hand-rolled `Card` + `Table` lists: groups, roles, users, API tokens, auth providers,
 * exchange providers, site webhooks and tenants. §LV.8 says they are "the most repetitive surface
 * in the console", and the measurement agrees — the five infrastructure lists differ only in which
 * two columns sit between the name and the status.
 *
 * This started as SH904's `IdentityListPage`, which was fixed to `{id, name, description}` because
 * that is all a group is. Generalising it rather than writing a second one is the whole point of
 * the slice: two nearly-identical list components is the state this task exists to leave, not to
 * arrive at.
 *
 * ## The three things every one of them got wrong, once each
 *
 * - **A load failure rendered the empty state.** `service.query().then(setItems)` with no `catch`
 *   leaves the array empty, so "no tokens" and "the server did not answer" were the same screen.
 *   `BentoListPage` separates them, which is why the catch below must set *both* an empty array
 *   and an error — `undefined` means "still loading" to the package's gate, so a bare failure
 *   spins for ever.
 * - **Row actions were mouse-only.** A pencil and a bin as icon-only ghost buttons in a table
 *   cell, several of them behind `opacity-0 group-hover:opacity-100`. A tile navigates and the
 *   entity acts (SH899): edit *is* the navigation, and delete lives in the entity's own menu where
 *   it is focusable and announced.
 * - **Status was a badge saying "Enabled".** The package's pill is the same information in the
 *   place every other surface puts it, and it is driven by `enabled` rather than by a string each
 *   list translated for itself.
 */
export function AdminListPage<T>({
  address,
  query,
  listRoute,
  newRoute,
  eyebrow,
  title,
  subtitle,
  newLabel,
  emptyTitle,
  emptyDescription,
  itemKey,
  tileOf,
  toolbar,
  dateHeader,
}: Readonly<AdminListPageProps<T>>) {
  /*
   * SH1046 — one read per address, shared by every screen that asks for it.
   *
   * The three states the package needs are `useQuery`'s own: `undefined` while pending is what
   * `BentoListPage` reads as loading, `[]` is genuinely empty, and `error` is the server not
   * answering. The effect this replaced collapsed the last two — `catch(() => setItems([]))` with
   * a message beside it — and the collapse is why this component's own doc comment has a paragraph
   * about a load failure rendering the empty state.
   */
  const { data, error } = useQuery<T[]>({
    queryKey: addressKey(address),
    queryFn: query,
  })
  // `undefined` means "still loading" to the package's gate, so a failure has to become an empty
  // array *and* an error — otherwise a failed load spins for ever. That was true of the effect
  // this replaced and it is still true: `useQuery` leaves `data` undefined on a failed first read.
  const items = data ?? (error ? [] : undefined)

  const { t } = useTranslation()
  const navigate = useNavigate()
  /*
   * SH1194 — records a reader compares are rows (VDS183). The mosaic drew one user as a 390 x 300
   * card with a glow, gave the first of fifteen sites a double cell for no reason the data had, and
   * could not sort, filter by a column or show more than two fields. The columns are the tile's own
   * fields, so no list lost anything it showed; the name links to the record, and Enter opens it.
   */
  /*
   * SH1222 — the sites list's rule (SH1219): the description was a column most rows left empty, at
   * the width the useful columns needed, so it is the identity cell's second line now. Details and
   * Status keep their columns, and Created appears where the entity has a date.
   */
  const columns = useMemo<BentoDataTableColumn<T>[]>(() => {
    const status = items?.some((item) => tileOf(item).hasStatus) ?? false
    const dated = items?.some((item) => tileOf(item).dated) ?? false
    return [
      {
        id: "name",
        header: t("common.name"),
        width: "minmax(16rem, 3fr)",
        hideable: false,
        sortValue: (item) => tileOf(item).title.toLowerCase(),
        cell: (item) => {
          const tile = tileOf(item)
          return (
            <Link to={tile.to} className="block min-w-0 hover:underline">
              <BentoIdentityCell name={tile.title} detail={tile.description?.trim() || undefined} />
            </Link>
          )
        },
      },
      {
        id: "details",
        header: t("admin.listDetails"),
        cell: (item) => <span className="text-muted-foreground">{tileOf(item).meta}</span>,
      },
      ...(dated ? [{
        id: "dated",
        header: dateHeader ?? t("admin.listCreated"),
        width: "minmax(9rem, 1fr)",
        sortValue: (item: T) => {
          const at = tileOf(item).dated
          return at ? new Date(at) : null
        },
        cell: (item: T) => {
          const at = tileOf(item).dated
          return at ? <BentoChangeCell at={at} /> : null
        },
      }] : []),
      ...(status ? [{
        id: "status",
        header: t("common.status"),
        width: "minmax(8rem, 0.8fr)",
        sortValue: (item: T) => tileOf(item).enabled ?? 0,
        cell: (item: T) => {
          const on = tileOf(item).enabled === 1
          return (
            <span className="inline-flex items-center gap-2 text-muted-foreground">
              <span aria-hidden="true" className={`size-2 rounded-full ${on ? "bg-emerald-500" : "bg-muted-foreground/50"}`} />
              {on ? t("common.enabled") : t("common.disabled")}
            </span>
          )
        },
      }] : []),
    ]
  }, [items, t, tileOf, dateHeader])

  return (
    <BentoListPage
      items={items}
      error={error ? headlineOf(problemOf(error), error.message) : null}
      tryAgainUrl={listRoute}
      eyebrow={eyebrow}
      title={title}
      subtitle={subtitle}
      toolbar={toolbar}
      newRoute={newRoute}
      newLabel={newLabel}
      itemKey={itemKey}
      columns={columns}
      getRowLabel={(item) => tileOf(item).title}
      onRowOpen={(item) => navigate(tileOf(item).to)}
      emptyTitle={emptyTitle}
      emptyDescription={emptyDescription}
    />
  )
}
