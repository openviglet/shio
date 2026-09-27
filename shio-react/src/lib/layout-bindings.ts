/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH1167 — the post-types a site renders as pages, read the way the server reads them.
 *
 * `ShPageRenderService.bindingsOf` is the whole rule: the site's `postTypeLayout`, parsed as a JSON
 * object, keeps only its string values. Blank, not JSON, or not an object binds nothing, and a
 * non-string value (the retired array form) is skipped rather than half-read. The live preview asks
 * this before asking the server to render, because a type with no binding is refused with a 400 the
 * browser logs as an error, on every editor of a menu, a section or a theme.
 */
export function boundPostTypes(raw: string | null | undefined): Set<string> {
  if (!raw?.trim()) return new Set()
  let parsed: unknown
  try {
    parsed = JSON.parse(raw)
  } catch {
    // The server records this as an incident and binds nothing; a preview has nothing to add.
    return new Set()
  }
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return new Set()
  return new Set(Object.entries(parsed as Record<string, unknown>)
    .filter(([, layout]) => typeof layout === "string")
    .map(([type]) => type))
}

/**
 * Whether the preview may ask the server to render this post: `undefined` while the answer is not
 * in yet (so nothing is requested), and `true` whenever the answer cannot be had — no site to read,
 * or the read failed — because a missing answer must not hide a preview that would have worked.
 */
export function isRenderable({ siteId, failed, layouts, typeName }: {
  siteId?: string
  failed: boolean
  layouts?: string | null
  typeName?: string
}): boolean | undefined {
  // No type means no post yet, and "no site" read before the post arrives would be a guess that
  // fires the very render this exists to hold back.
  if (!typeName) return undefined
  if (!siteId || failed) return true
  if (layouts === undefined) return undefined
  return boundPostTypes(layouts).has(typeName)
}
