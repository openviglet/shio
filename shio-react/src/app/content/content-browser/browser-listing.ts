/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH1145 — what the listing IS: the envelope, its rows, and the constants that shape a request.
 *
 * <p>Types and frozen values only, so this imports nothing from React and can be read by the hook,
 * the page and a test without any of them pulling the others in. SH1026 moved what each view
 * *draws* into this directory; this is the half that says what they draw.</p>
 */
import type { ShPostType } from "@/models/post-type/post-type"

import type { ShContentFilters } from "@/lib/content-query"
import type { BrowserRow } from "./browser-view"
import type { FacetCounts } from "@/lib/filter-bar"

/**
 * `GET /v2/object/{id}/list` (SH120) — the CDA listing envelope, with rows whose shared
 * fields carry the CDA's names. It used to be `{ shFolders, shPosts, shSite, breadcrumb }`
 * of raw JPA entities; the site and the breadcrumb now come from `/path`, which is the one
 * endpoint that answers "where am I".
 *
 * SH165: the posts are one page of the folder's content — `folders` is still the whole set.
 */
export interface ObjectListResponse {
  folderId?: string
  folders: ConsoleFolderRow[]
  posts: ConsolePostRow[]
  page: number
  size: number
  totalPosts: number
  /** SH1025 — how many child folders the folder holds, whether or not they were paged. */
  totalFolders: number
  /** SH1024 — the filtered posts counted by facet; asked for on the first page only. */
  facets?: FacetCounts
}

/** A folder row: the CDA's `{id, name, root}` plus the console's ordering axis. */
export interface ConsoleFolderRow {
  id: string
  name: string
  root?: boolean
  date?: string
  position?: number
}

/** A post row: the CDA post summary plus the draft axis a delivery API cannot express. */
export interface ConsolePostRow {
  id: string
  title?: string
  summary?: string
  furl?: string
  postType?: string
  locale?: string
  date?: string
  modifiedDate?: string
  postTypeTitle?: string
  published?: boolean
  publishStatus?: string
  state?: string
  /** SH1026 — the byte path of the image its card shows. */
  thumbnail?: string
}

/** SH1184 — declared beside `breadcrumbFrom`, which builds it. */
export type { BreadcrumbPathItem } from "./browser-view"

/** SH1026 — the row every view draws; it and its label moved beside the renderers. */
export type ObjectChild = BrowserRow

/** Posts per request. The folder's subfolders come whole regardless (SH165). */
export const PAGE_SIZE = 50

/**
 * How many post ids "select everything" will pull in before it stops and says so (SH237).
 *
 * A selection is a list of ids, so selecting everything means holding every id — and an unbounded
 * folder is exactly the response SH165 removed. The cap is high enough that no realistic curator
 * gesture hits it and low enough that hitting it costs a warning rather than the tab.
 */
export const SELECT_ALL_CAP = 1000

/**
 * The browser's sort selector → the endpoint's `sort` (SH165).
 *
 * The order used to be applied here, over the whole folder, which was right while the whole
 * folder was in the browser. With the posts paged it has to be the server's, or "sort by date"
 * would order the fifty rows that happen to be loaded and quietly leave the rest where they
 * were. `order` is manual position, which only a folder has — so for the posts underneath it
 * both `order` and `name` mean title.
 */
export const POST_SORT: Record<string, string> = { order: "title", name: "title", date: "date" }

/**
 * SH703 — one frozen empty set, at module scope on purpose.
 *
 * A `new Set()` per render would be a new identity every time, so every `useMemo` and every child
 * keyed on the selection would recompute on every render of a page that renders a lot.
 */
export const EMPTY_SELECTION: Set<string> = new Set<string>()

/** SH1046 — the same argument for the catalogue while it loads: one identity, not one per render. */
export const EMPTY_POST_TYPES: ShPostType[] = []

/** SH1021 — the three verbs a selection can take. */
export type BulkOp = "PUBLISH" | "UNPUBLISH" | "DELETE"

/**
 * SH1023 — every key this list owns, and what each one means when the URL says nothing.
 *
 * The filter names are `ShContentQuery`'s (SH1022), which is the whole point: the address is the
 * request. `sort` and `view` are the console's own — `sort` because the browser's three options
 * map onto the listing's axes, `view` because a grid and a list are the same rows.
 */
/** What the "more filters" badge does not count: the bar shows its own chips for these. */
export const MORE_IGNORES = ["sort", "view", "q", "type", "state", "locale", "modifiedSince", "modifiedBefore"] as const

// SH1203 — the table is the default view: a folder is records a curator scans by type, state and date.
export const BROWSER_LIST = {
  q: "", sort: "order", view: "spreadsheet",
  type: "", state: "", locale: "", missingLocale: "", modifiedSince: "", modifiedBefore: "",
  author: "", modifier: "", agent: "", subtree: "",
}

/** The list state, as the filter shape the request builder and the popover both read. */
export function browserFilters(value: Record<keyof typeof BROWSER_LIST, string>): ShContentFilters {
  return {
    q: value.q, type: value.type, state: value.state, locale: value.locale,
    // SH1060 — the coverage question, asked of a folder: which pages here have no pt-br yet.
    missingLocale: value.missingLocale,
    modifiedSince: value.modifiedSince, modifiedBefore: value.modifiedBefore,
    author: value.author, modifier: value.modifier, agent: value.agent, subtree: value.subtree,
  }
}
