/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

// SH1205 — the address arithmetic the Sections list needs, kept pure so a test can call it.

/** `post:VigR3/sections/hero` → `VigR3`; a post's own address names the site its friendly URLs live in. */
export function siteOf(address: string | undefined): string | null {
  const match = /^post:([^/]+)\//.exec(address ?? "")
  return match ? match[1] : null
}

/** `post:VigR3/sections/hero` → `/sections/hero`, the value a page's Sections field stores. */
export function friendlyUrlOf(address: string, site: string): string | null {
  const prefix = `post:${site}`
  return address.startsWith(`${prefix}/`) ? address.slice(prefix.length) : null
}

/** One step up or down; the value is a list in render order, so reordering is swapping neighbours. */
export function moved(values: readonly string[], index: number, by: -1 | 1): string[] {
  const target = index + by
  if (target < 0 || target >= values.length) return [...values]
  const next = [...values]
  ;[next[index], next[target]] = [next[target], next[index]]
  return next
}

/**
 * SH1323 — the `/agent/find` that lists the posts using a friendly URL: the URL, quoted, in its own
 * site's data. A Sections field stores friendly URLs and `q` already searches stored field data, so
 * this is the listing every surface has (`shio_find`, `shio find`), not a reverse index of its own.
 * Quoted because the stored value is a JSON string: `"/sections/pricing/hero"` does not match
 * `/sections/pricing/hero-2`.
 */
export function usedOnParams(site: string, furl: string) {
  return { site, q: `"${furl}"`, ids: true, limit: 10 }
}

/**
 * SH1343 — of the posts naming a URL, the ones a preview can be drawn on: pages, by the site's own
 * layout bindings, other than the post itself.
 *
 * The listing is a text search, so a menu item's URL also finds the Menu that lists it. The editor
 * passed that Menu as `on`, and the render, which draws a section on a page that uses it, answered
 * 400 for a type with no layout: a failed preview on every menu item, and a red console check.
 */
export function pagesAmong<T extends { id: string; type?: string }>(rows: readonly T[], pageTypes: ReadonlySet<string>, self: string): T[] {
  return rows.filter((row) => row.id !== self && row.type !== undefined && pageTypes.has(row.type))
}
