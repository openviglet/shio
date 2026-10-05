/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH1220 — which shape the sites list opens in, and where a reader's own choice is kept.
 *
 * At the counts most instances hold, a site is recognised by its home page faster than by its
 * name, so the list opens as a grid of cards. Past thirty, the cards stop fitting a screen and the
 * reader is comparing rather than picking, so it opens as rows (SH1194's reason). A reader who picks
 * one keeps it: per person and per browser, the way the content browser keeps its column layout,
 * and never as state another curator sees.
 */
export type SitesView = "grid" | "list"

/** The largest number of sites the list opens as a grid. */
export const GRID_UP_TO = 30

export function defaultSitesView(count: number): SitesView {
  return count <= GRID_UP_TO ? "grid" : "list"
}

export function sitesViewKey(username: string | undefined): string {
  return `shio.sites.view.${username ?? "anonymous"}`
}

/** A stored choice, or undefined for anything that is not one. */
export function readSitesView(raw: string | null | undefined): SitesView | undefined {
  return raw === "grid" || raw === "list" ? raw : undefined
}

/** A name's initials, for a card with no picture: two letters, upper-case. */
export function initialsOf(name: string): string {
  const words = name.trim().split(/[\s._-]+/).filter(Boolean)
  const letters = words.length > 1 ? words[0][0] + words[1][0] : (words[0] ?? "?").slice(0, 2)
  return letters.toUpperCase()
}
