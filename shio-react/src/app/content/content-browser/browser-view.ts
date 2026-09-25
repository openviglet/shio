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
}

/**
 * SH1059 — how a row is named to anything that is not looking at it: the title, and the locale
 * when there is one. The checkbox, the actions trigger and the title all say this, so two
 * translations of one page are two different things to a screen reader, as they are on screen.
 */
export function rowLabel(item: BrowserRow): string {
  return item.locale ? `${item.name} (${item.locale})` : item.name
}
