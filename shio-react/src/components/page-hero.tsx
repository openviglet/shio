/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { BentoFormHero, BentoHero } from "@viglet/viglet-design-system/bento"
import type React from "react"

interface PageHeroProps {
  /** Tabler icon component, rendered as the hero's `leading`. */
  icon?: React.ElementType
  title: string
  /** The hero's eyebrow. */
  eyebrow?: string
  /** The hero's subtitle. */
  subtitle?: string
  /** The hero's back link. */
  urlBase?: string
  /** Actions, rendered as the hero's `trailing`. */
  actions?: React.ReactNode

  /*
   * SH901 — the save bar, when the page has one.
   *
   * §LV.4 predicted this slice would "reveal that the adapter needs a prop it does not have", and
   * it was sharper than a prop: the same thing was **one** component in bento and **two** in
   * console chrome, so this had to span a 1:2 mapping. SH910 deleted the console half, and what
   * survives is the half that was always the better shape — `BentoFormHero` renders the controls
   * in the hero and fades a fixed bar in as the hero scrolls away, which the authoring contract
   * asks for precisely because composing those two halves by hand is how they drift.
   *
   * Give it these and it renders the morph; leave them out and it is just a hero.
   */
  /** Sticky/morph title. Defaults to `title`. */
  saveTitle?: string
  saveBadges?: React.ReactNode
  onCancel?: () => void
  loading?: boolean
  saveDisabled?: boolean
  dirty?: boolean
  titleMissing?: boolean
}

/**
 * SH900 — one page hero for every surface that has one, rather than a copy per page.
 *
 * ## Why this exists rather than a second copy of the content browser
 *
 * SH898 and SH899 migrated by **adding** a surface: the console page stays, a bento page is
 * written beside it, and SH909 removes whichever loses. That is right for a home page of 154
 * lines and for four site pages of 100–200. `object.children.page.tsx` is **965 lines** with
 * twenty state variables, three view modes, selection, pagination and the copy/move machinery —
 * and only **four** of those lines are chrome: a `PageHeader` and three `Card`s.
 *
 * Copying it would duplicate 960 lines of the most defect-prone code in the console so that four
 * could differ, and every fix would then have to land twice until SH909. So the browser takes the
 * shape SH892 built for exactly this: the page is written once and a provider above it decides
 * which chrome it renders. `AdaptiveSectionCard` covers the three panels; this covers the header.
 *
 * **The migration shape depends on the surface, and that is the finding.** A small entity-shaped
 * page gets a true bento shape — `BentoEntityShell`, a hero that owns identity, a real mosaic —
 * and is worth writing twice. A large working surface gets the adapter, because the thing being
 * migrated is its chrome and nothing else.
 *
 * ## What SH910 left of it, and why it is no longer called Adaptive (SH932)
 *
 * The console branch went with the shell that rendered it, so this chooses nothing about chrome.
 * It maps this product's header props onto the package's `BentoHero` and `BentoFormHero` — a 1:2
 * mapping, on whether the page gave it something only a save bar takes — which is still worth one
 * place rather than twenty-four.
 *
 * The name outlived the branching, and a wrapper called *Adaptive* tells the next reader there is
 * a chrome to choose. The cheapest way to honour that reading is to write the branch back, which
 * is how a retired era returns one page at a time. `PageHero` says what it renders instead.
 *
 * It stays in `components/` for the reason it was put there: the readers that encode Block AR's
 * two-era model decide a page's era from its imports, and keeping the `BentoHero` import on this
 * side of the boundary is what lets them stay honest about the pages.
 */
export function PageHero({
  icon: Icon,
  title,
  eyebrow,
  subtitle,
  urlBase,
  actions,
  saveTitle,
  saveBadges,
  onCancel,
  loading,
  saveDisabled,
  dirty,
  titleMissing,
}: Readonly<PageHeroProps>) {
  // A page has a save bar when it gave this adapter something only a save bar takes. `actions`
  // alone is not enough — the content browser passes two navigation buttons and wants no bar.
  const hasSaveBar = onCancel !== undefined || saveBadges !== undefined

  if (hasSaveBar) {
    return (
      <BentoFormHero
        eyebrow={eyebrow}
        backTo={urlBase}
        leading={Icon ? <Icon size={28} aria-hidden="true" /> : undefined}
        title={title}
        subtitle={subtitle}
        onCancel={onCancel ?? (() => undefined)}
        actions={actions}
        loading={loading}
        dirty={dirty}
        titleMissing={titleMissing}
        saveDisabled={saveDisabled}
        stickyTitle={saveTitle ?? title}
        stickyBadges={saveBadges}
      />
    )
  }

  return (
    <BentoHero
      eyebrow={eyebrow}
      backTo={urlBase}
      title={title}
      subtitle={subtitle}
      leading={Icon ? <Icon size={28} aria-hidden="true" /> : undefined}
      trailing={actions}
    />
  )
}
