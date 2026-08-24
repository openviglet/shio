/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { BentoHero, useSectionChrome } from "@viglet/viglet-design-system/bento"
import type React from "react"
import { PageHeader } from "@/components/page-header"

interface AdaptivePageHeaderProps {
  /** Tabler icon component, as `PageHeader` takes it. */
  icon?: React.ElementType
  title: string
  /** Bento only: the hero's eyebrow. The console header has no equivalent and ignores it. */
  eyebrow?: string
  /** Bento only: the hero's subtitle. */
  subtitle?: string
  /** Where the console header's breadcrumb trail is rooted, and the bento hero's back link. */
  urlBase?: string
  /** Actions. The console header has no slot for them; the bento hero has `trailing`. */
  actions?: React.ReactNode
}

/**
 * SH900 — one page header that renders in either chrome, the way `AdaptiveSectionCard` does for
 * sections.
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
 * ## Why it lives in `components/` and not beside a page
 *
 * `console-chrome-census.test.mjs` and `surface-digest.mjs` decide a page's era from its imports,
 * and this component imports `BentoHero` — a genuinely bento-era name. A page importing *this* is
 * still console-era, correctly, because it renders console chrome by default. Keeping the import
 * on this side of the boundary is what lets both readers stay honest about the pages.
 */
export function AdaptivePageHeader({
  icon: Icon,
  title,
  eyebrow,
  subtitle,
  urlBase,
  actions,
}: Readonly<AdaptivePageHeaderProps>) {
  const chrome = useSectionChrome()

  if (chrome === "bento") {
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

  return (
    <>
      <PageHeader title={title} turIcon={Icon} urlBase={urlBase} />
      {actions && <div className="flex items-center justify-end gap-2 px-4 pt-4 md:px-6">{actions}</div>}
    </>
  )
}
