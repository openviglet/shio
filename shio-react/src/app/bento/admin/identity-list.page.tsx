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
import { useEffect, useState } from "react"
import type { IdentityEntity } from "./identity-entity.page"

interface IdentityListPageProps {
  query: () => Promise<IdentityEntity[]>
  listRoute: string
  newRoute: string
  /** Where a tile navigates — the entity route, minus the id. */
  editRouteOf: (entity: IdentityEntity) => string
  icon: TablerIcon
  tone: BentoTone
  eyebrow: string
  title: string
  subtitle: string
  newLabel: string
  emptyTitle: string
  emptyDescription: string
}

/**
 * SH904 — the group and role lists, which are the same list twice.
 *
 * ## What the console rows were doing, and where those actions went
 *
 * Each row carried a pencil and a bin as icon-only ghost buttons in an actions column. Both are
 * gone from the tile, and that reads as a regression until you know it is the division SH899
 * settled: **a tile navigates, an entity acts.** Edit *is* the navigation, so it needs no button
 * of its own; Delete lives in the entity's own `⋮` menu, where it is focusable, announced, and
 * one level away from a misclick rather than sitting under the pointer in a dense table.
 *
 * The console's delete was a bare `confirm(t("dialog.deleteMessage"))` — a browser dialog whose
 * text names neither the group nor the consequence. The shell's is a real dialog naming the
 * entity, which is P7's "never surprise the curator" on a destructive action that used to be two
 * clicks with no subject.
 *
 * ## The failure state the hand-rolled list did not have
 *
 * The console table rendered `common.noResults` whenever `items` was empty — including when the
 * request had failed, because `service.query().then(setItems)` has no `catch` and the array just
 * stays empty. "No groups" and "the server did not answer" were the same screen. `BentoListPage`
 * separates them, so the load failure has to become an explicit error here (`items` must go from
 * `undefined` to `[]` or the gate spins for ever) — the same handling SH899 wrote for sites.
 */
export function IdentityListPage({
  query,
  listRoute,
  newRoute,
  editRouteOf,
  icon,
  tone,
  eyebrow,
  title,
  subtitle,
  newLabel,
  emptyTitle,
  emptyDescription,
}: Readonly<IdentityListPageProps>) {
  const [items, setItems] = useState<IdentityEntity[] | undefined>(undefined)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    query()
      .then(setItems)
      .catch((reason: unknown) => {
        setItems([])
        setError(String(reason))
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
      newRoute={newRoute}
      newLabel={newLabel}
      itemKey={(item: IdentityEntity) => item.id}
      renderTile={(item: IdentityEntity, emphasis) => (
        <BentoEntityTile
          to={editRouteOf(item)}
          emphasis={emphasis}
          defaultIcon={icon}
          tone={tone}
          title={item.name}
          description={item.description}
        />
      )}
      emptyTitle={emptyTitle}
      emptyDescription={emptyDescription}
    />
  )
}
