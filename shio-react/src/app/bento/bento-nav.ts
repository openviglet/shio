/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import {
  IconArrowsExchange,
  IconBrandGraphql,
  IconBuildingSkyscraper,
  IconFileText,
  IconFingerprint,
  IconForms,
  IconHistory,
  IconKey,
  IconLayersIntersect,
  IconMail,
  IconPackages,
  IconPhoto,
  IconRobot,
  IconSearch,
  IconShieldCog,
  IconTrash,
  IconWebhook,
} from "@tabler/icons-react"
import type { BentoNavGroup, BentoNavItem } from "@viglet/viglet-design-system/bento"
import { useMemo } from "react"
import { ROUTES } from "@/app/routes.const"
import { useDiscovery } from "@/hooks/use-discovery"

/**
 * SH888/SH889 — the map of this product, declared once, for both readers.
 *
 * ## Why this file is the one to get right early
 *
 * The rail and the command palette both need to know what surfaces exist, what they are called,
 * which group they belong to and what gates them. Declared twice they disagree within a release;
 * declared in the package they would be Turing's routes inside a Shio console. So the package
 * supplies the typed shape and both components that read it, and the array lives here.
 *
 * §LIV.4's own warning is the reason the whole vocabulary is declared now rather than grown:
 * this is the file every one of Block AR's fourteen surface tasks appends to, so a shape settled
 * after five of them have shipped is a shape five tasks have to be reopened for.
 *
 * ## The two routes, and which one a migration flips
 *
 * `fallbackRoute` is where a surface lives **today** — a console path, for all seventeen of them.
 * `bentoRoute` is where it lives once its own AR task has moved it, and is absent until then.
 * `bentoNavTarget(item)` prefers the bento one, so **a migration is one line here**: add
 * `bentoRoute`, and the rail, the palette and every hub follow. Nothing else in this file changes
 * and no second list has to be kept in step.
 *
 * ## Visibility is Shio's, and stays on Shio's type
 *
 * `BentoNavItem` is deliberately the *render* contract: the package cannot read a feature model
 * and should not try. `ShioNavItem` extends it with `requires`, and {@link useBentoNav} filters
 * before handing anything over — so a reader who may not see a surface is not shown one that
 * refuses them, which is the same rule SH46 applied to the sidebar's tenant entry.
 *
 * ## Titles reuse keys; descriptions are new
 *
 * The authoring contract says not to mint a parallel namespace for a surface that already has
 * one — the same entity in two chromes is the same entity — so every `titleKey` is a key the
 * console sidebar already renders. `descriptionKey` has no console equivalent: the sidebar shows
 * a label and nothing else, while a palette row and a hub tile both show a sentence. Those are
 * new, under `nav.desc.*`, in `en` and `pt` together.
 */

/** A section of the rail. `areaRoute` is what makes it *visible* — the rail draws only those. */
const SECTIONS = {
  content: {
    id: "content",
    labelKey: "sidebar.content",
    descriptionKey: "nav.desc.section.content",
    icon: IconFileText,
    areaRoute: `${ROUTES.CONSOLE}/content`,
  },
  administration: {
    id: "administration",
    labelKey: "sidebar.administration",
    descriptionKey: "nav.desc.section.administration",
    icon: IconShieldCog,
    areaRoute: ROUTES.ADMIN_ROOT,
  },
} as const

/**
 * Shio's own entry type. `requires` is the field the package will not hold, and it is a name
 * rather than a boolean so the reason survives in the declaration: `useBentoNav` resolves it.
 */
interface ShioNavItem extends BentoNavItem {
  requires?: "multiTenant"
}

/**
 * Every surface in this console, once.
 *
 * Ordered as the sidebar orders them, because a curator who has used the console should find the
 * palette in the order they already know. `tone` is the tile's colour on a hub mosaic and is
 * grouped by kind — blue for content, indigo for the model, violet for delivery surfaces, amber
 * for identity, slate for the plumbing — rather than assigned per item, so a new entry has an
 * obvious answer instead of a free choice.
 */
const ITEMS: ShioNavItem[] = [
  // Content
  // SH899 — the first entry to move. `bentoNavTarget` prefers `bentoRoute`, so adding this one
  // line repoints the rail, the palette and every hub at once; `fallbackRoute` stays so the
  // slice can be reverted without editing anything else.
  { id: "sites", titleKey: "content.sites", descriptionKey: "nav.desc.sites", icon: IconFileText, section: "content", tone: "blue", bentoRoute: ROUTES.BENTO_CONTENT, fallbackRoute: `${ROUTES.CONSOLE}/content` },
  { id: "search", titleKey: "content.searchTitle", descriptionKey: "nav.desc.search", icon: IconSearch, section: "content", tone: "blue", fallbackRoute: ROUTES.SEARCH },
  { id: "post-types", titleKey: "content.postTypes", descriptionKey: "nav.desc.postTypes", icon: IconForms, section: "content", tone: "indigo", fallbackRoute: ROUTES.POST_TYPE_ROOT },
  { id: "files", titleKey: "staticFile.title", descriptionKey: "nav.desc.files", icon: IconPhoto, section: "content", tone: "blue", fallbackRoute: ROUTES.FILES },
  { id: "trash", titleKey: "content.trash", descriptionKey: "nav.desc.trash", icon: IconTrash, section: "content", tone: "slate", fallbackRoute: ROUTES.TRASH },
  { id: "universal-editor", titleKey: "sidebar.universalEditor", descriptionKey: "nav.desc.universalEditor", icon: IconLayersIntersect, section: "content", tone: "violet", fallbackRoute: ROUTES.UNIVERSAL_EDITOR },

  // Administration
  { id: "admin", titleKey: "admin.title", descriptionKey: "nav.desc.admin", icon: IconShieldCog, section: "administration", tone: "amber", fallbackRoute: ROUTES.ADMIN_ROOT },
  { id: "api-tokens", titleKey: "apiToken.title", descriptionKey: "nav.desc.apiTokens", icon: IconKey, section: "administration", tone: "amber", fallbackRoute: ROUTES.ADMIN_TOKENS },
  { id: "tenants", titleKey: "tenantAdmin.title", descriptionKey: "nav.desc.tenants", icon: IconBuildingSkyscraper, section: "administration", tone: "amber", fallbackRoute: ROUTES.ADMIN_TENANTS, requires: "multiTenant" },
  { id: "auth-providers", titleKey: "authProvider.title", descriptionKey: "nav.desc.authProviders", icon: IconFingerprint, section: "administration", tone: "amber", fallbackRoute: ROUTES.ADMIN_AUTH_PROVIDERS },
  { id: "exchange-providers", titleKey: "exchangeProvider.title", descriptionKey: "nav.desc.exchangeProviders", icon: IconArrowsExchange, section: "administration", tone: "slate", fallbackRoute: ROUTES.ADMIN_EXCHANGE_PROVIDERS },
  { id: "email-config", titleKey: "emailConfig.title", descriptionKey: "nav.desc.emailConfig", icon: IconMail, section: "administration", tone: "slate", fallbackRoute: ROUTES.ADMIN_EMAIL_CONFIG },
  { id: "webhooks", titleKey: "siteWebhook.title", descriptionKey: "nav.desc.webhooks", icon: IconWebhook, section: "administration", tone: "slate", fallbackRoute: ROUTES.ADMIN_WEBHOOKS },
  { id: "marketplace", titleKey: "marketplace.title", descriptionKey: "nav.desc.marketplace", icon: IconPackages, section: "administration", tone: "violet", fallbackRoute: ROUTES.ADMIN_MARKETPLACE },
  { id: "activity", titleKey: "activity.title", descriptionKey: "nav.desc.activity", icon: IconHistory, section: "administration", tone: "slate", fallbackRoute: ROUTES.ADMIN_ACTIVITY },
  { id: "review", titleKey: "review.title", descriptionKey: "nav.desc.review", icon: IconRobot, section: "administration", tone: "rose", fallbackRoute: ROUTES.ADMIN_REVIEW },
  { id: "graphql", titleKey: "graphql.title", descriptionKey: "nav.desc.graphql", icon: IconBrandGraphql, section: "administration", tone: "violet", fallbackRoute: ROUTES.GRAPHQL_ROOT },
]

/** Every id declared above, for a reader that wants to check coverage without importing data. */
export const BENTO_NAV_IDS: readonly string[] = ITEMS.map((item) => item.id)

/**
 * The nav, filtered to what this instance and this reader may see, grouped for the rail.
 *
 * A hook rather than a constant because visibility is answered at runtime: `useDiscovery()` is
 * how the console learns whether `shio.multi-tenant` is on, and SH46's finding was that an
 * ungated entry offers every single-tenant install a page for managing tenants it does not have.
 */
export function useBentoNav(): BentoNavGroup[] {
  const discovery = useDiscovery()
  const multiTenant = discovery?.multiTenant === true

  return useMemo(() => {
    const visible = ITEMS.filter((item) => item.requires !== "multiTenant" || multiTenant)
    return Object.values(SECTIONS).map((section) => ({
      section,
      items: visible.filter((item) => item.section === section.id),
    }))
  }, [multiTenant])
}

/** The same list flattened, which is what the command palette takes. */
export function useBentoNavItems(): BentoNavItem[] {
  const groups = useBentoNav()
  return useMemo(() => groups.flatMap((group) => group.items), [groups])
}

/**
 * Whether this instance runs more than one tenant (SH934).
 *
 * Exported from here rather than answered by a second `useDiscovery()` call, and that is a rule
 * with a reader behind it. SH46's defect was two surfaces deciding "is this a SaaS install" by
 * different means and drifting apart; `tenant-nav-gating.test.mjs` now fails on a second consumer
 * of `useDiscovery` anywhere under `src/`, naming this declaration as the place to route through.
 *
 * So the tenant switcher in the shell header asks the same question the Tenants nav entry does,
 * through the same call, and the two cannot disagree about whether to appear.
 */
export function useMultiTenant(): boolean {
  return useDiscovery()?.multiTenant === true
}
