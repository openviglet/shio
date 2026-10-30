/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH1316 — a site as its pages, the way a visitor walks it, rather than as the folders it is stored in.
 *
 * Opening a site showed `en`, `fonts`, `images`, `menu`, `pt`, `theme` and "0 posts" for a site of
 * 130 published pages: storage, shown directly. A page is a post of a type the site binds to a
 * layout (`postTypeLayout`, the binding the renderer itself resolves), so a section, a menu item or a
 * theme is not one. The tree is the pages' URLs, split on `/`, and the rows come from
 * `/agent/find`, the agent's own listing, so this composes a read rather than adding one (P10).
 */

/** The part of an `/agent/find` row this view reads. */
export interface PageRow {
  address: string
  type?: string
  title?: string
  id?: string
  locale?: string
  published?: boolean
  state?: string
}

/** A site's two fields that say which types are pages. */
export interface PageSite {
  name: string
  postTypeLayout?: string | null
  pageType?: string | null
}

export interface PageNode {
  /** The URL segment this node stands for; the home is "". */
  segment: string
  /** The full path, `/` for the home. */
  path: string
  /** The page at this path, when one exists (a path can be only a parent of pages). */
  page?: PageRow
  children: PageNode[]
}

/**
 * The types this site renders as pages: the keys `postTypeLayout` binds, and its declared page type.
 * The stored value is JSON and can be the literal `"null"`, so it is parsed defensively.
 */
export function pageTypesOf(site: PageSite): Set<string> {
  const types = new Set<string>()
  try {
    const parsed: unknown = site.postTypeLayout ? JSON.parse(site.postTypeLayout) : null
    if (parsed && typeof parsed === "object") {
      for (const key of Object.keys(parsed as Record<string, unknown>)) types.add(key)
    }
  } catch {
    // A malformed binding binds nothing; the declared page type below still counts.
  }
  if (site.pageType) types.add(site.pageType)
  return types
}

/** The path an address names within its site: `post:Acme/blog/hello` is `/blog/hello`. */
export function pathOf(address: string, siteName: string): string {
  const prefix = `post:${siteName}`
  const rest = address.startsWith(prefix) ? address.slice(prefix.length) : ""
  return rest === "" ? "/" : rest
}

/** The locales the pages are written in, sorted, so a switch is drawn only when there is a choice. */
export function localesOf(rows: readonly PageRow[]): string[] {
  return [...new Set(rows.map((row) => row.locale ?? "").filter(Boolean))].sort()
}

/**
 * The page tree of one locale. Children are ordered by path, so two reads draw the same tree (P5).
 * A row with no locale belongs to every locale: it is a page the site never translated.
 */
export function pageTree(rows: readonly PageRow[], site: PageSite, locale?: string): PageNode {
  const types = pageTypesOf(site)
  const root: PageNode = { segment: "", path: "/", children: [] }
  const pages = rows
    .filter((row) => row.type && types.has(row.type))
    .filter((row) => !locale || !row.locale || row.locale === locale)
    .map((row) => ({ row, path: pathOf(row.address, site.name) }))
    .sort((a, b) => a.path.localeCompare(b.path))
  for (const { row, path } of pages) {
    if (path === "/") {
      root.page = root.page ?? row
      continue
    }
    let node = root
    let walked = ""
    for (const segment of path.split("/").filter(Boolean)) {
      walked += `/${segment}`
      let child = node.children.find((each) => each.segment === segment)
      if (!child) {
        child = { segment, path: walked, children: [] }
        node.children.push(child)
      }
      node = child
    }
    node.page = node.page ?? row
  }
  return root
}

/**
 * The rest of the site, by type: what is not a page (a theme, its layouts, its menus, its sections),
 * with how many of each. Named by the type rather than a fixed list, because a menu is a blueprint's
 * own type and the next blueprint may call it something else. Ordered by type, so two reads agree.
 */
export function otherContentOf(rows: readonly PageRow[], site: PageSite): { type: string; count: number }[] {
  const types = pageTypesOf(site)
  const counts = new Map<string, number>()
  for (const row of rows) {
    if (!row.type || types.has(row.type)) continue
    counts.set(row.type, (counts.get(row.type) ?? 0) + 1)
  }
  return [...counts].map(([type, count]) => ({ type, count })).sort((a, b) => a.type.localeCompare(b.type))
}

/** How many pages the tree holds, which is the number the view states. */
export function pageCount(node: PageNode): number {
  return (node.page ? 1 : 0) + node.children.reduce((sum, child) => sum + pageCount(child), 0)
}
