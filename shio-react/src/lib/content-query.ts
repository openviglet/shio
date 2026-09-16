/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH1022 — the content-query vocabulary, as the console spells it.
 *
 * ## Why this is a module and not four `useState`s in the page
 *
 * Because the words are a contract with three other surfaces. `GET /api/v2/agent/find`, the MCP
 * tool `shio_find` and `shio find` take the same ones, meaning the same thing, and the server
 * refuses a word it does not serve rather than ignoring it — so a key spelled `modifedSince` here
 * is a 400 rather than a listing that quietly answers about everything. Keeping the names in one
 * exported list is what lets a test say "these are the server's words" instead of reading JSX.
 *
 * ## What is deliberately not here
 *
 * Any filtering. A client-side filter over a paged listing filters one page — the rows it is
 * narrowing were chosen by a query that did not know about the filter, so the answer is wrong in
 * a way that looks right until a folder outgrows a page. Every word below travels to the server.
 */

/** One filter each, in the order the popover shows them. */
export interface ShContentFilters {
  type?: string
  state?: string
  locale?: string
  modifiedSince?: string
  modifiedBefore?: string
  author?: string
  modifier?: string
  subtree?: string
}

/**
 * The words the console listing sends, which are the server's own.
 *
 * `site` and `folder` are absent on purpose: this listing's folder is the `{id}` in its path, and
 * the server refuses a second one. `sort`, `page` and `size` are the listing's own paging, set
 * beside these rather than through them.
 */
export const CONTENT_FILTERS = [
  "type", "state", "locale", "modifiedSince", "modifiedBefore", "author", "modifier", "subtree",
] as const

/** The filters that carry a value, as query parameters — blank means "not asked for". */
export function filterParams(filters: ShContentFilters): Record<string, string> {
  const params: Record<string, string> = {}
  for (const key of CONTENT_FILTERS) {
    const value = filters[key]
    if (typeof value === "string" && value.trim()) {
      params[key] = value.trim()
    }
  }
  return params
}

/** How many filters are set — what the toolbar badge counts, so a narrowed listing says so. */
export function activeFilterCount(filters: ShContentFilters): number {
  return Object.keys(filterParams(filters)).length
}
