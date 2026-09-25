/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import type { BentoFilterFacet, BentoFilterValue } from "@viglet/viglet-design-system/bento"

/**
 * SH1024 — the design system's filter bar over the query vocabulary a list already speaks.
 *
 * <p>The bar owns no state and holds no product words (VDS139); the list owns both, in its URL
 * (SH1023), spelled as the server spells them (SH1022). This is the translation between the two,
 * and nothing else: the bar's value is read from the URL's keys and written back to them, so the
 * link a curator copies is still the request an agent would run. Pure, so
 * `filter-bar.test.mjs` round-trips it without a browser.</p>
 */
export const FACET_TYPE = "type"
export const FACET_STATE = "state"
export const FACET_LOCALE = "locale"
export const FACET_MODIFIED = "modified"
export const FACET_SITE = "site"

/**
 * The keys this bar reads and writes. Anything else in the URL is left alone, and a key a list does
 * not declare is dropped by its `patch` — which is why `site` can live here although only the
 * search page, which has no folder of its own, offers it.
 */
export type BarKey = "q" | "site" | "type" | "state" | "locale" | "modifiedSince" | "modifiedBefore"

export function toBarValue(value: Partial<Record<BarKey, string>>): BentoFilterValue {
  const one = (text: string | undefined) => (text ? [text] : undefined)
  return {
    query: value.q ?? "",
    facets: {
      [FACET_SITE]: one(value.site),
      [FACET_TYPE]: one(value.type),
      [FACET_STATE]: one(value.state),
      [FACET_LOCALE]: one(value.locale),
      [FACET_MODIFIED]: value.modifiedSince || value.modifiedBefore
        ? { from: value.modifiedSince || undefined, to: value.modifiedBefore || undefined }
        : undefined,
    },
  }
}

/**
 * The URL keys a bar value says. Every key is present, empty when cleared, so one `patch` both sets
 * what the reader chose and clears what they removed: a chip removed must leave the address.
 */
export function fromBarValue(bar: BentoFilterValue): Record<BarKey, string> {
  const first = (id: string) => {
    const chosen = bar.facets[id]
    return Array.isArray(chosen) && chosen.length ? String(chosen[0]) : ""
  }
  const range = bar.facets[FACET_MODIFIED]
  const dates = range && !Array.isArray(range) ? range as { from?: string; to?: string } : {}
  return {
    q: bar.query.trim(),
    site: first(FACET_SITE),
    type: first(FACET_TYPE),
    state: first(FACET_STATE),
    locale: first(FACET_LOCALE),
    modifiedSince: dates.from ?? "",
    modifiedBefore: dates.to ?? "",
  }
}

export interface FacetWords {
  type: string
  state: string
  locale: string
  modified: string
  draft: string
  published: string
}

/**
 * The facets, with only the values that exist: the post types this instance has and the locales
 * present in what is listed. A menu offering a value nothing has is a filter that always empties
 * the list.
 */
export function contentFacets(types: readonly { name: string; title?: string }[],
  locales: readonly string[], words: FacetWords): BentoFilterFacet[] {
  const facets: BentoFilterFacet[] = [
    { id: FACET_TYPE, kind: "choice", label: words.type,
      options: types.map((type) => ({ value: type.name, label: type.title || type.name })) },
    { id: FACET_STATE, kind: "choice", label: words.state,
      options: [{ value: "draft", label: words.draft }, { value: "published", label: words.published }] },
  ]
  const present = [...new Set(locales.filter(Boolean))].sort()
  if (present.length > 1) {
    facets.push({ id: FACET_LOCALE, kind: "choice", label: words.locale,
      options: present.map((locale) => ({ value: locale, label: locale })) })
  }
  facets.push({ id: FACET_MODIFIED, kind: "date", label: words.modified })
  return facets
}

/**
 * The site facet, by **name** — the word `/agent/find` takes, so the search a curator shares is the
 * question an agent runs (SH1027). Offered first, and only where there is more than one site: with
 * one, every choice is the whole list.
 */
export function withSiteFacet(facets: readonly BentoFilterFacet[],
  sites: readonly { name: string }[], label: string): BentoFilterFacet[] {
  const names = [...new Set(sites.map((site) => site.name).filter(Boolean))]
    .sort((left, right) => left.localeCompare(right))
  if (names.length < 2) return [...facets]
  return [{ id: FACET_SITE, kind: "choice", label,
    options: names.map((name) => ({ value: name, label: name })) }, ...facets]
}
