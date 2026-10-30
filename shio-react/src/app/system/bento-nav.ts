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
  IconMessage,
  IconPackages,
  IconPhoto,
  IconRobot,
  IconSearch,
  IconShieldCog,
  IconUsers,
  IconTrash,
  IconCalendarTime,
  IconWebhook,
} from "@tabler/icons-react"
import type { BentoNavGroup, BentoNavItem } from "@viglet/viglet-design-system/bento"
import { useMemo } from "react"
import { ROUTES } from "@/app/routes.const"
import { useDiscovery } from "@/hooks/use-discovery"
// SH979 — the backend owns the list of surfaces, and this is one of its four readers. The rail and
// the palette derive from the same file the manifest serves behind `include=surfaces`, the MCP
// resource `shio://surfaces` publishes and `shio surfaces` prints, so an agent and a curator are
// looking at one list. What stays here is what TypeScript owns: the icon, the two i18n keys, the
// tile's tone, the route constant the router is built from, and the visibility gate.
import SURFACES from "../../../../shio-app/src/main/resources/console/surfaces.json"

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
 * ## The two routes, and the migration they were for (SH935)
 *
 * `fallbackRoute` was where a surface lived and `bentoRoute` where it had moved, with
 * `bentoNavTarget(item)` preferring the second — so a migration was one line here and the rail,
 * the palette and every hub followed. That is what the pair was for, and the migration is over:
 * SH909 repointed `ROUTES.CONSOLE` at `/bento`, which moved every constant the fallbacks are
 * built from, so both fields name the same path for every surface below.
 *
 * `at(route)` writes it once into both. The package still owns the two-field shape, so collapsing
 * them into a single `route` is a conversation with it rather than an edit here.
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
 * one, so every `titleKey` is the key the surface's own page already renders. A palette row and a
 * hub tile both show a sentence as well, which no page had, so `descriptionKey` is new, under
 * `nav.desc.*`, in `en` and `pt` together.
 */

/** A section of the rail. `areaRoute` is what makes it *visible* — the rail draws only those. */
const SECTIONS = {
  content: {
    id: "content",
    labelKey: "sidebar.content",
    descriptionKey: "nav.desc.section.content",
    icon: IconFileText,
    areaRoute: `${ROUTES.BENTO_AREAS}/content`,
  },
  administration: {
    id: "administration",
    labelKey: "sidebar.administration",
    descriptionKey: "nav.desc.section.administration",
    icon: IconShieldCog,
    areaRoute: `${ROUTES.BENTO_AREAS}/administration`,
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
 * SH935 — one route, written once, into the two fields the package's contract still has.
 *
 * Leaving `bentoRoute` off was not merely untidy. The command palette reads its absence as "this
 * one still opens the old console" and renders a marker whose screen-reader label is **"Opens in
 * console"** — so sixteen of seventeen migrated surfaces announced a destination that no longer
 * exists. A wrong accessible name is a worse defect than a stale field, and it is what made this
 * a task rather than a cleanup.
 */
const at = (route: string) => ({ bentoRoute: route, fallbackRoute: route })

/**
 * Every surface in this console, once.
 *
 * Ordered as the sidebar orders them, because a curator who has used the console should find the
 * palette in the order they already know. `tone` is the tile's colour on a hub mosaic and is
 * grouped by kind — blue for content, indigo for the model, violet for delivery surfaces, amber
 * for identity, slate for the plumbing — rather than assigned per item, so a new entry has an
 * obvious answer instead of a free choice.
 */
/**
 * What TypeScript owns, per surface: the icon, the titles a person reads, the tile's tone, the
 * route constant the router is built from, and the gate.
 *
 * Keyed by the declaration's id, and every declared surface must appear here — a surface added to
 * `console/surfaces.json` and not decorated fails at module load rather than rendering an
 * unlabelled tile. `tone` is grouped by kind — blue for content, indigo for the model, violet for
 * delivery, amber for identity, slate for the plumbing — so a new entry has an obvious answer
 * rather than a free choice.
 */
const DECORATION: Record<string, Omit<ShioNavItem, "id" | "section" | "bentoRoute" | "fallbackRoute">
  & { route: string }> = {
  "sites": { titleKey: "content.sites", descriptionKey: "nav.desc.sites", icon: IconFileText, tone: "blue", route: ROUTES.BENTO_CONTENT },
  "search": { titleKey: "content.searchTitle", descriptionKey: "nav.desc.search", icon: IconSearch, tone: "blue", route: ROUTES.SEARCH },
  "post-types": { titleKey: "content.postTypes", descriptionKey: "nav.desc.postTypes", icon: IconForms, tone: "indigo", route: ROUTES.POST_TYPE_ROOT },
  "files": { titleKey: "staticFile.title", descriptionKey: "nav.desc.files", icon: IconPhoto, tone: "blue", route: ROUTES.FILES },
  "trash": { titleKey: "content.trash", descriptionKey: "nav.desc.trash", icon: IconTrash, tone: "slate", route: ROUTES.TRASH },
  "forms": { titleKey: "content.formsTitle", descriptionKey: "nav.desc.forms", icon: IconMail, tone: "blue", route: ROUTES.FORMS },
  "scheduled": { titleKey: "content.scheduledTitle", descriptionKey: "nav.desc.scheduled", icon: IconCalendarTime, tone: "blue", route: ROUTES.SCHEDULED },
  "notes": { titleKey: "note.openTitle", descriptionKey: "nav.desc.notes", icon: IconMessage, tone: "rose", route: ROUTES.NOTES },
  "universal-editor": { titleKey: "sidebar.universalEditor", descriptionKey: "nav.desc.universalEditor", icon: IconLayersIntersect, tone: "violet", route: ROUTES.UNIVERSAL_EDITOR },
  "admin": { titleKey: "admin.title", descriptionKey: "nav.desc.section.administration", icon: IconShieldCog, tone: "amber", route: ROUTES.ADMIN_ROOT },
  "users": { titleKey: "admin.users", descriptionKey: "nav.desc.admin", icon: IconUsers, tone: "amber", route: ROUTES.BENTO_USERS },
  "api-tokens": { titleKey: "apiToken.title", descriptionKey: "nav.desc.apiTokens", icon: IconKey, tone: "amber", route: ROUTES.ADMIN_TOKENS },
  "tenants": { titleKey: "tenantAdmin.title", descriptionKey: "nav.desc.tenants", icon: IconBuildingSkyscraper, tone: "amber", route: ROUTES.ADMIN_TENANTS, requires: "multiTenant" },
  "auth-providers": { titleKey: "authProvider.title", descriptionKey: "nav.desc.authProviders", icon: IconFingerprint, tone: "amber", route: ROUTES.ADMIN_AUTH_PROVIDERS },
  "exchange-providers": { titleKey: "exchangeProvider.title", descriptionKey: "nav.desc.exchangeProviders", icon: IconArrowsExchange, tone: "slate", route: ROUTES.ADMIN_EXCHANGE_PROVIDERS },
  "email-config": { titleKey: "emailConfig.title", descriptionKey: "nav.desc.emailConfig", icon: IconMail, tone: "slate", route: ROUTES.ADMIN_EMAIL_CONFIG },
  "webhooks": { titleKey: "siteWebhook.title", descriptionKey: "nav.desc.webhooks", icon: IconWebhook, tone: "slate", route: ROUTES.ADMIN_WEBHOOKS },
  "marketplace": { titleKey: "marketplace.title", descriptionKey: "nav.desc.marketplace", icon: IconPackages, tone: "violet", route: ROUTES.ADMIN_MARKETPLACE },
  "activity": { titleKey: "activity.title", descriptionKey: "nav.desc.activity", icon: IconHistory, tone: "slate", route: ROUTES.ADMIN_ACTIVITY },
  "review": { titleKey: "review.title", descriptionKey: "nav.desc.review", icon: IconRobot, tone: "rose", route: ROUTES.ADMIN_REVIEW },
  "graphql": { titleKey: "graphql.title", descriptionKey: "nav.desc.graphql", icon: IconBrandGraphql, tone: "violet", route: ROUTES.GRAPHQL_ROOT },
}

/**
 * Every surface in this console, once — the backend's list, decorated here.
 *
 * Ordered as the declaration orders them, which is the order the sidebar had: a curator who has
 * used the console should find the palette in the order they already know.
 */
const ITEMS: ShioNavItem[] = SURFACES.surfaces.map((surface) => {
  const decoration = DECORATION[surface.id]
  if (!decoration) {
    throw new Error(
      `bento-nav: surface "${surface.id}" is declared in console/surfaces.json and has no icon or `
        + "titles here. Add it to DECORATION — the declaration is the list, this is what the "
        + "console adds to it.",
    )
  }
  const { route, ...rest } = decoration
  return { ...rest, id: surface.id, section: surface.section, ...at(route) }
})

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

/**
 * SH1193 — how a section's hub groups its surfaces.
 *
 * <p>The rail offers three choices and a hub is the second level: every surface of the section,
 * one click from the rail. A mosaic of eleven tiles is the 1.0 sidebar again, laid out as a grid, so
 * each hub is chunked into groups of four or fewer. The grouping is presentation, which is why it is
 * here and not in `console/surfaces.json`: the declaration says which surfaces exist and in which
 * section, and an agent reading the manifest has no use for how the console lays them out.</p>
 *
 * <p>Every surface of a section appears in exactly one of its groups, except the one the declaration
 * marks as the section's own hub (`admin`, reason `navigation`), which is this page and not a tile on
 * it; `bento-hub.test.mjs` holds that, so a surface declared without a group fails rather than
 * vanishing.</p>
 */
export const HUB_GROUPS: Record<string, readonly { labelKey: string; ids: readonly string[] }[]> = {
  // SH1317 — Review is the curator's main job (P7), so it leads the Content hub with the other things
  // that wait for a person, rather than sitting under Administration beside users and tokens.
  content: [
    { labelKey: "nav.hub.content.waiting", ids: ["review", "notes", "scheduled"] },
    { labelKey: "nav.hub.content.library", ids: ["sites", "search", "files", "trash"] },
    { labelKey: "nav.hub.content.publishing", ids: ["forms", "universal-editor"] },
    { labelKey: "nav.hub.content.model", ids: ["post-types"] },
  ],
  administration: [
    { labelKey: "nav.hub.administration.access", ids: ["users", "auth-providers", "tenants"] },
    { labelKey: "nav.hub.administration.oversight", ids: ["activity"] },
    { labelKey: "nav.hub.administration.integration", ids: ["api-tokens", "webhooks", "graphql", "exchange-providers"] },
    { labelKey: "nav.hub.administration.instance", ids: ["email-config", "marketplace"] },
  ],
}

/** One hub: its section and its groups, holding only the surfaces this reader may see. */
export function useBentoHub(sectionId: string | undefined) {
  const groups = useBentoNav()
  return useMemo(() => {
    const group = groups.find((one) => one.section.id === sectionId)
    if (!group) return null
    const byId = new Map(group.items.map((item) => [item.id, item]))
    return {
      section: group.section,
      groups: (HUB_GROUPS[group.section.id] ?? [])
        .map(({ labelKey, ids }) => ({
          labelKey,
          items: ids.map((id) => byId.get(id)).filter((item): item is BentoNavItem => item !== undefined),
        }))
        .filter(({ items }) => items.length > 0),
    }
  }, [groups, sectionId])
}
