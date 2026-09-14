/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import type { Icon as TablerIcon } from "@tabler/icons-react"
import { BentoEntityTile, BentoListPage, type BentoTone } from "@viglet/viglet-design-system/bento"
import type { ReactNode } from "react"
import { useEffect, useState } from "react"

/** What one row becomes on the mosaic. Every field the package's tile takes, and no more. */
export interface AdminTile {
  to: string
  title: string
  description?: string | null
  /** The one line under the title that is not the description — an owner, a URL, a scope. */
  meta?: ReactNode
  /** `0`/`1`, because the package's pill is numeric and every one of these models is boolean. */
  enabled?: number
  hasStatus?: boolean
}

export interface AdminListPageProps<T> {
  query: () => Promise<T[]>
  listRoute: string
  newRoute: string
  icon: TablerIcon
  tone: BentoTone
  eyebrow: string
  title: string
  subtitle: string
  newLabel: string
  emptyTitle: string
  emptyDescription: string
  itemKey: (item: T) => string
  tileOf: (item: T) => AdminTile
  /** Rendered in the row beside "Customize" — a filter, a secondary action. */
  headerAction?: ReactNode
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
  query,
  listRoute,
  newRoute,
  icon,
  tone,
  eyebrow,
  title,
  subtitle,
  newLabel,
  emptyTitle,
  emptyDescription,
  itemKey,
  tileOf,
  headerAction,
}: Readonly<AdminListPageProps<T>>) {
  const [items, setItems] = useState<T[] | undefined>(undefined)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    query()
      .then(setItems)
      .catch((reason: unknown) => {
        setItems([])
        setError(reason instanceof Error ? reason.message : String(reason))
      })
  }, [query])

  return (
    <BentoListPage
      items={items}
      error={error}
      tryAgainUrl={listRoute}
      eyebrow={eyebrow}
      heroIcon={icon}
      title={title}
      subtitle={subtitle}
      tone={tone}
      headerAction={headerAction}
      newRoute={newRoute}
      newLabel={newLabel}
      itemKey={itemKey}
      renderTile={(item: T, emphasis) => {
        const tile = tileOf(item)
        return (
          <BentoEntityTile
            to={tile.to}
            emphasis={emphasis}
            defaultIcon={icon}
            tone={tone}
            title={tile.title}
            description={tile.description}
            meta={tile.meta}
            enabled={tile.enabled}
            hasStatus={tile.hasStatus}
          />
        )
      }}
      emptyTitle={emptyTitle}
      emptyDescription={emptyDescription}
    />
  )
}
