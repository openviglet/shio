/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

import { relativeDate as relativeDateIn } from "../../../lib/relative-date.ts"

/**
 * SH1026 — the content browser's three views, as data. Pure, so `browser-views.test.mjs` reads it
 * without a browser; the renderers are `browser-views.tsx`.
 */
export type BrowserView = "list" | "grid" | "spreadsheet"

const VIEWS: readonly BrowserView[] = ["list", "grid", "spreadsheet"]

/**
 * What the URL's `view` says, as a view. An unknown word is the default, not a blank page.
 * SH1203 — the default is the table, which sorts and shows type, state, language and date as columns.
 */
export function browserView(word: string): BrowserView {
  return (VIEWS as readonly string[]).includes(word) ? word as BrowserView : "spreadsheet"
}

/**
 * How far a key moves focus, per view. A grid is read in two directions, so its left and right
 * arrows step through the same order its up and down do; the list and the table are one column.
 */
export function stepFor(view: BrowserView, key: string): -1 | 0 | 1 {
  if (key === "ArrowDown") return 1
  if (key === "ArrowUp") return -1
  if (view !== "grid") return 0
  if (key === "ArrowRight") return 1
  if (key === "ArrowLeft") return -1
  return 0
}

/** The listbox's own layout, per view. */
export function listboxClass(view: BrowserView): string {
  if (view === "grid") return "grid grid-cols-1 gap-3 p-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4"
  return ""
}

/** The option's own chrome, per view: a card carries its border, a row its divider. */
export function optionClass(view: BrowserView): string {
  if (view === "grid") return "rounded-lg"
  return "border-b last:border-b-0 hover:bg-muted/40"
}

/**
 * SH1026 — where the table's column layout is kept: per user, so two curators sharing a browser
 * do not hide each other's columns, and per browser, because a layout is a convenience of the
 * reader's screen and not content anybody else should see.
 */
export function tableLayoutKey(username: string | undefined): string {
  return `shio.browser.table.${username || "anonymous"}`
}

/**
 * A stored layout, read defensively: storage is text anybody can have written, and `"null"` parses
 * to `null`, which passes a truthy check and crashes on the property read (the widgetSettings rule).
 */
export function readTableLayout(raw: string | null): { hidden: string[] } {
  try {
    const parsed: unknown = raw ? JSON.parse(raw) : null
    const hidden = parsed && typeof parsed === "object" ? (parsed as { hidden?: unknown }).hidden : undefined
    return { hidden: Array.isArray(hidden) ? hidden.filter((id): id is string => typeof id === "string") : [] }
  } catch {
    return { hidden: [] }
  }
}

/** The widgets whose value is one line of text, which is all a table cell can edit honestly. */
const SCALAR_WIDGETS = new Set(["Text", "Text Area"])

/**
 * SH1026 — the one field a table cell may edit as the title, or `undefined`.
 *
 * A title is whatever the type's `isTitle` fields join to, so a cell can only stand in for it when
 * there is exactly one such field, at the top level, holding plain text. Two title fields, or a
 * rich-text one, is a title the cell would write half of or flatten, so it is not offered.
 */
export function editableTitleField(
  fields: readonly { name: string; isTitle?: boolean; widget?: string; fields?: unknown[] }[] | undefined,
): string | undefined {
  const titles = (fields ?? []).filter((field) => field.isTitle)
  if (titles.length !== 1) return undefined
  const [title] = titles
  return SCALAR_WIDGETS.has(title.widget ?? "Text") && !title.fields?.length ? title.name : undefined
}

export interface BrowserRow {
  id: string
  name: string
  date: string
  objectType: "FOLDER" | "POST"
  position?: number
  postTypeName?: string
  publishStatus?: string
  /**
   * SH1059 — which language this row is. Two locales of one page are two rows with the same title,
   * date and type, and without this a curator deleting "the duplicate" deletes a translation.
   */
  locale?: string
  /**
   * SH1125 — whether this row's language is the one the site **declared** as its default.
   *
   * <p>A fact about this row in this site rather than about the row, which is why it is computed once
   * per listing from {@link declaredDefaultLocale} instead of at each of the four places a label is
   * built. It answers the question a curator deleting one of two translations is actually asking,
   * which SH1059's chip could not: it says which row is which and not which one is the original.</p>
   */
  isDefaultLocale?: boolean
  /** SH1026 — the byte path of the image a card shows, from the listing; absent when there is none. */
  thumbnail?: string
}

/**
 * SH1026 — a card's image, through the transform pipeline the media library's thumbnails use:
 * cropped to the card, as webp. The listing gives the byte path and the screen decides the size.
 */
export function thumbnailSrc(path: string, width = 320, height = 192): string {
  const params = new URLSearchParams({ w: String(width), h: String(height), crop: "fill", format: "webp" })
  return `${path}?${params}`
}

/**
 * SH1026 — above this many rows the list and the grid mount only the window a reader can see. Below
 * it every row mounts, which keeps a small folder exactly as it was: nothing to measure, nothing to
 * scroll into range, and the whole list in the accessibility tree.
 */
export const WINDOW_THRESHOLD = 150

/** Which rows to mount, and how much space the unmounted ones above and below stand in for. */
export interface RowWindow {
  start: number
  end: number
  before: number
  after: number
}

/** Every row, no spacers: the answer below the threshold and before anything is measured. */
export function fullWindow(count: number): RowWindow {
  return { start: 0, end: count, before: 0, after: 0 }
}

/**
 * SH1026 — the rows a viewport shows, from where the list sits in it.
 *
 * `top` is the list's top edge relative to the viewport (negative once scrolled past it), rows are
 * `rowHeight` tall including their gap, and a grid lays `columns` to a row. `overscan` rows either
 * side keep a fast scroll from showing blank space before the next measure. Pure, so
 * `browser-views.test.mjs` checks the arithmetic without a browser.
 */
export function visibleWindow({ count, columns, rowHeight, top, viewportHeight, overscan = 4 }: {
  count: number; columns: number; rowHeight: number; top: number; viewportHeight: number; overscan?: number
}): RowWindow {
  if (count <= 0 || rowHeight <= 0 || columns <= 0) return fullWindow(Math.max(count, 0))
  const rows = Math.ceil(count / columns)
  const firstRow = Math.min(rows, Math.max(0, Math.floor(-top / rowHeight) - overscan))
  // Never an empty window: a list below the fold keeps its first rows mounted, and the measure
  // that sizes a row needs one to read. With none, the next measure would mount every row again.
  const lastRow = Math.min(rows, Math.max(firstRow + overscan,
    Math.ceil((viewportHeight - top) / rowHeight) + overscan))
  return {
    start: firstRow * columns,
    end: Math.min(count, lastRow * columns),
    before: firstRow * rowHeight,
    after: (rows - lastRow) * rowHeight,
  }
}

/**
 * SH1125 — the site's default locale, or `null` when there is nothing to mark.
 *
 * <p>`declared` is the whole of it, and it is SH1056's distinction rather than a nicety. A site that
 * has chosen nothing still reports a default — the locale its content happens to be written in — and
 * a chip saying "default" about something nobody chose is exactly the confusion SH1056 exists to
 * remove. So an undeclared policy marks no row, and a folder of two translations then says which
 * language each is and stays silent about which came first, which is the honest answer.</p>
 *
 * <p>Pure and taking the policy rather than the site id, so the rule is checkable without a server:
 * the one thing that can go wrong here is reading `defaultLocale` without `declared`.</p>
 */
export function declaredDefaultLocale(
  policy: { defaultLocale?: string; declared?: boolean } | null | undefined,
): string | null {
  if (!policy?.declared) return null
  const locale = policy.defaultLocale?.trim()
  return locale ? locale : null
}

/**
 * SH1059 — how a row is named to anything that is not looking at it: the title, and the locale
 * when there is one. The checkbox, the actions trigger and the title all say this, so two
 * translations of one page are two different things to a screen reader, as they are on screen.
 *
 * <p>SH1125 — and which of them is the original, when the site has declared one. `defaultWord` is
 * passed in already translated: this function is pure so `browser-locale.test.mjs` can run it without
 * a browser, and a hard-coded "default" would be an English word in a Portuguese console — the locale
 * tag itself is not translated because a tag is a tag, and a sentence about it is not.</p>
 */
export function rowLabel(item: BrowserRow, defaultWord?: string): string {
  if (!item.locale) return item.name
  const marked = item.isDefaultLocale && defaultWord ? `${item.locale}, ${defaultWord}` : item.locale
  return `${item.name} (${marked})`
}

/** One step of the browser's path: the site, then its folders down to the one on screen. */
export interface BreadcrumbPathItem {
  id: string
  name: string
  type: string
  /** The site's root folder — `/path` says so, and the address grammar does not count it. */
  root?: boolean
}

/**
 * SH1184 — the browser's path from `GET /v2/object/{id}/path` (`ShCdaPath`).
 *
 * <p>The breadcrumb opens at the site's root folder, and a blank site's root folder is created
 * with the site's own name (`ShSiteWriteService.createBlank`), so drawing the site and then every
 * folder read `sh8844001/sh8844001/menu`. A root folder that repeats the site's name is the same
 * place said twice and is left out; one with a name of its own ("Home") is a real step and stays,
 * marked `root` so the address built from these crumbs can still skip it.</p>
 */
export function breadcrumbFrom(data: {
  site?: { id: string; name: string } | null
  breadcrumb?: readonly { id: string; name: string; root?: boolean }[] | null
} | null | undefined): BreadcrumbPathItem[] {
  const items: BreadcrumbPathItem[] = []
  const site = data?.site
  if (site) items.push({ id: site.id, name: site.name, type: "SITE" })
  for (const crumb of data?.breadcrumb ?? []) {
    const root = crumb.root === true
    if (root && site && crumb.name?.toLowerCase() === site.name?.toLowerCase()) continue
    items.push({ id: crumb.id, name: crumb.name, type: "FOLDER", root })
  }
  return items
}

/**
 * SH1275 — the type a folder's "New …" button offers.
 *
 * <p>It offered the last type created anywhere, else the first one the instance knew, so every
 * Quilhara folder said "Novo Guide", including one holding three Trails and nothing else. The folder
 * says what belongs in it: the type most of its posts already have (the server's facet counts when
 * the listing carries them, since those cover every page of it, else the rows on screen), then the
 * type last created in this folder, then the site's declared page type (SH1283), then the last one
 * created anywhere, then the first non-system type. System types (File, layouts, themes) are never
 * the suggestion: a folder of images is filled by uploading, not by a post form.</p>
 */
export function suggestedPostType<T extends { name: string; system?: number | boolean | null }>(
  postTypes: readonly T[],
  folder: {
    counts?: Readonly<Record<string, number>> | null
    posts?: readonly { postType?: string | null }[] | null
  },
  lastHere: string | null,
  lastAnywhere: string | null,
  sitePageType: string | null = null,
): T | null {
  const authorable = postTypes.filter((type) => !type.system)
  const byName = (name: string | null | undefined) => authorable.find((type) => type.name === name)
  const counts: Record<string, number> = { ...(folder.counts ?? {}) }
  if (!folder.counts) {
    for (const post of folder.posts ?? []) {
      if (post.postType) counts[post.postType] = (counts[post.postType] ?? 0) + 1
    }
  }
  const common = Object.entries(counts)
    .filter(([name, count]) => count > 0 && byName(name))
    .sort(([a, x], [b, y]) => y - x || a.localeCompare(b))[0]?.[0]
  return byName(common) ?? byName(lastHere) ?? byName(sitePageType) ?? byName(lastAnywhere)
    ?? authorable[0] ?? postTypes[0] ?? null
}

/** The key the type last created in one folder is kept under (SH1275). */
export const lastPostTypeKey = (folderId: string) => `sh-last-post-type:${folderId}`

/**
 * SH1274 — the folder a site's listing is standing in for, when it is the only thing there.
 *
 * <p>A site holds content only through its root folder (SH128), so its own listing is one row, a
 * folder usually named like the site: a curator opened `quilhara` and was shown `quilhara`. When the
 * listing is exactly one root folder and no posts, that folder's contents are what the site page
 * means, and this names it so the page can open there. A site with two root folders, or a listing
 * that is a folder's, answers null and is shown as it is.</p>
 */
export function soleRootFolder(listing: {
  folders?: readonly { id: string; root?: boolean }[] | null
  posts?: readonly unknown[] | null
} | null | undefined): string | null {
  const folders = listing?.folders ?? []
  if (folders.length !== 1 || (listing?.posts?.length ?? 0) > 0) return null
  return folders[0].root === true ? folders[0].id : null
}

/**
 * SH1192 — a nested entity's location, as the hero's trail (VDS184): the site list first, then
 * each ancestor the path names, the last being the entity's parent.
 *
 * <p>A folder's own path ends with the folder itself, which is the page's title rather than a step
 * of the trail, so the folder browser passes `ownPath: true` and the last item is left out. A post
 * passes its parent folder's path whole. One step, which is what a site's root listing has, renders
 * as the arrow back link to the site list.</p>
 */
export function heroTrail(
  path: readonly BreadcrumbPathItem[],
  sites: { to: string; label: string },
  objectRoute: (id: string) => string,
  { ownPath }: { ownPath: boolean },
): { to: string; label: string }[] {
  const ancestors = ownPath ? path.slice(0, -1) : path
  return [sites, ...ancestors.map((item) => ({ to: objectRoute(item.id), label: item.name }))]
}

/**
 * SH1027/SH1145 — "search this folder", as the search page's own address.
 *
 * <p>The browser's box and the console's search are different questions, and this is the wider one:
 * it hands the search page a `folder:` address it can scope by, which is what makes its "this
 * folder" and "this folder and below" options reachable at all — that page has no folder of its own
 * to offer.</p>
 *
 * <p>A path from the breadcrumb, not an id: the search takes the vocabulary's own addressing (SH79),
 * and an id in that parameter would be a fourth spelling of where content is. Null before the
 * breadcrumb has landed, because there is no honest address until it has.</p>
 *
 * <p>The route is required (SH1187): its old default, a bare `/search`, is no route the router
 * declares, and the button opened a 404.</p>
 */
export function searchHereFor(
  siteName: string | null,
  breadcrumbPath: readonly { name: string; type: string; root?: boolean }[],
  searchRoute: string,
): string | null {
  if (!siteName) return null
  // SH1184 — the root folder is the site's own place, and the grammar spells it as the site: it is
  // not a segment of the chain (`ShAddressResolver.addressOf` stops below it).
  const chain = breadcrumbPath.filter((crumb) => crumb.type === "FOLDER" && !crumb.root).map((crumb) => crumb.name)
  return `${searchRoute}?${new URLSearchParams({
    folder: `folder:${siteName}/${chain.join("/")}`,
    subtree: "true",
  })}`
}

/**
 * SH1188 — the router state "search in this folder" carries: the browser page it was opened from,
 * so the search page's back link returns there rather than to the site list.
 */
export function searchBackState(location: { pathname: string; search: string }): { from: string } {
  return { from: `${location.pathname}${location.search}` }
}

/**
 * SH1188 — where the search page's back link goes: the page that opened it when that page said so,
 * else `fallback`. Only an in-app path is taken, so a state nobody set can send the curator nowhere odd.
 */
export function searchBackTo(state: unknown, fallback: string): string {
  const from = (state as { from?: unknown } | null)?.from
  return typeof from === "string" && from.startsWith("/") && !from.startsWith("//") ? from : fallback
}

/**
 * SH1145 — "3 days ago", or nothing at all for a date the row does not carry or cannot parse.
 * SH1203 — in the console's language, which `lib/relative-date.ts` takes from the caller.
 */
export function relativeDate(dateStr: string | undefined, language?: string): string {
  return relativeDateIn(dateStr, language)
}
