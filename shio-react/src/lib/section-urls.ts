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
