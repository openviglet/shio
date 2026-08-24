/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { IconFileText, IconShieldCog } from "@tabler/icons-react"
import type { BentoNavGroup } from "@viglet/viglet-design-system/bento"
import { ROUTES } from "@/app/routes.const"

/**
 * SH888 — the map of this product, in the shape the bento shell reads.
 *
 * ## What the package will not hold
 *
 * The authoring contract is explicit that the nav array is the product's: "an entry appears when
 * its route does. That is how you say a reader may see something — the package cannot read your
 * feature model, and should not try." So the rail, and later the command palette, are handed a
 * `BentoNavGroup[]` from here, already filtered by whatever privilege or licence decides it.
 *
 * ## Sections now, items with SH889
 *
 * `BentoNavRail` renders **sections only** — it filters `groups` down to those with an
 * `areaRoute` and draws one icon each. `BentoNavItem` exists for `BentoCommandPalette`, which
 * has no nav to search until SH889 declares one, and which would list every surface a second
 * time if the two read separate arrays. So this file starts with the two sections the rail needs
 * and empty item lists, and SH889 fills them in **here** rather than beside the palette.
 *
 * The two sections mirror the console sidebar's two labelled groups — content and
 * administration. The sidebar's third group is the dashboard, which is not a section here: the
 * rail draws Home itself, from `homeRoute`, above a divider.
 *
 * ## Routes point at the console on purpose
 *
 * `fallbackRoute` and `areaRoute` name the **console** paths, because that is where those
 * surfaces still live. Each Block AR task moves one surface and repoints its entry; until then
 * the rail is honest — it takes you to the page that exists. `bentoNavTarget(item)` prefers
 * `bentoRoute` when an item has one, which is the seam each migration writes into.
 */
export const BENTO_NAV: BentoNavGroup[] = [
  {
    section: {
      id: "content",
      labelKey: "sidebar.content",
      icon: IconFileText,
      areaRoute: `${ROUTES.CONSOLE}/content`,
    },
    items: [],
  },
  {
    section: {
      id: "administration",
      labelKey: "sidebar.administration",
      icon: IconShieldCog,
      areaRoute: ROUTES.ADMIN_ROOT,
    },
    items: [],
  },
]
