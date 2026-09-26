/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH1026 — the content browser's three views, as data. Pure, so `browser-views.test.mjs` reads it
 * without a browser; the renderers are `browser-views.tsx`.
 */
export type BrowserView = "list" | "grid" | "spreadsheet"

const VIEWS: readonly BrowserView[] = ["list", "grid", "spreadsheet"]

/** What the URL's `view` says, as a view. An unknown word is the default, not a blank page. */
export function browserView(word: string): BrowserView {
  return (VIEWS as readonly string[]).includes(word) ? word as BrowserView : "list"
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
 * SH1059 — how a row is named to anything that is not looking at it: the title, and the locale
 * when there is one. The checkbox, the actions trigger and the title all say this, so two
 * translations of one page are two different things to a screen reader, as they are on screen.
 */
export function rowLabel(item: BrowserRow): string {
  return item.locale ? `${item.name} (${item.locale})` : item.name
}
