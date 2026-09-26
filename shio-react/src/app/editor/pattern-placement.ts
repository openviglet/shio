/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH1108 — placing a pushed pattern from the rail, as the document `shio patterns apply` sends.
 *
 * `placementDocument` in `cli/src/patterns-store.mjs` is the definition, and this is its mirror for
 * a browser bundle that cannot import the CLI. The two are held equal by `pattern-placement.test.mjs`
 * on the same inputs, which is what "the array the rail writes is the array the CLI writes" means:
 * one document shape, sent to one `POST /agent/apply`, so the server has one thing to agree with.
 *
 * The rail fills a pattern from its **declared defaults only**. A placeholder with no default is a
 * value only the person placing it knows, and the rail has no form for it yet, so such a pattern is
 * offered with the reason and the CLI line that places it, rather than placed with braces in it.
 */

export type PatternSection = { type: string; data?: Record<string, unknown> }

export type Pattern = {
  formatVersion: number
  name: string
  title?: string
  from?: string
  params?: Record<string, { default?: unknown; description?: string } | undefined>
  sections: PatternSection[]
}

/** The page as `/agent/read` answers it. */
export type PlacementPage = { address: string; type: string; data?: Record<string, unknown> }

const PLACEHOLDER = /\{\{\s*([A-Za-z0-9_.-]+)\s*}}/g
const DEFAULT_SECTIONS_FOLDER = "/sections"

/** The placeholders a pattern mentions that no declared default fills, in first-seen order. */
export function unfilled(pattern: Pattern): string[] {
  const defaults = defaultsOf(pattern)
  const found: string[] = []
  walk(pattern.sections, (text) => {
    for (const [, name] of text.matchAll(PLACEHOLDER)) {
      if (defaults[name] === undefined && !found.includes(name)) found.push(name)
    }
  })
  return found
}

/** The pattern with every placeholder filled from its defaults; call `unfilled` first. */
export function fillFromDefaults(pattern: Pattern): Pattern {
  const defaults = defaultsOf(pattern)
  return {
    ...pattern,
    sections: pattern.sections.map((section) => ({
      type: section.type,
      data: substitute(section.data ?? {}, defaults) as Record<string, unknown>,
    })),
  }
}

/** The document that places `pattern` into `page`; `after` is sections to skip, absent the end. */
export function placementDocument(
  { pattern, page, instance, after }: { pattern: Pattern; page: PlacementPage; instance: string; after?: number },
) {
  const site = siteOf(page.address)
  const held = referencesOf(page.data?.sections)
  const folder = folderOf(held[0]) ?? DEFAULT_SECTIONS_FOLDER
  const base = folder.startsWith("/") ? folder.replace(/\/$/, "") : DEFAULT_SECTIONS_FOLDER
  const urls = pattern.sections.map((_, index) => `${base}/${instance}-${index + 1}`)
  const where = after === undefined ? held.length : Math.max(0, Math.min(after, held.length))
  const next = [...held]
  next.splice(where, 0, ...urls)
  return {
    urls,
    where,
    document: {
      site,
      folders: [{ path: folder }],
      posts: [
        ...pattern.sections.map((section, index) => ({
          url: urls[index], type: section.type, folder, data: section.data,
        })),
        { url: urlOf(page.address, site), type: page.type, data: { ...page.data, sections: next } },
      ],
    },
  }
}

/**
 * The `--as` the rail passes: the pattern's name, or the first `name-N` this page does not already
 * place. The CLI leaves that choice to the caller because re-applying is idempotent there; a curator
 * pressing Insert twice means two blocks, and the same name twice would splice the same posts in
 * again.
 */
export function freeInstance(name: string, page: PlacementPage): string {
  const held = referencesOf(page.data?.sections)
  const taken = (instance: string) => held.some((url) => url.endsWith(`/${instance}-1`))
  if (!taken(name)) return name
  let n = 2
  while (taken(`${name}-${n}`)) n += 1
  return `${name}-${n}`
}

function defaultsOf(pattern: Pattern): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  for (const [name, spec] of Object.entries(pattern.params ?? {})) {
    if (spec && typeof spec === "object" && spec.default !== undefined) out[name] = spec.default
  }
  return out
}

function referencesOf(value: unknown): string[] {
  if (Array.isArray(value)) return value.filter((item): item is string => typeof item === "string" && !!item)
  return typeof value === "string" && value ? [value] : []
}

function folderOf(url: string | undefined): string | null {
  if (typeof url !== "string" || !url.startsWith("/")) return null
  const cut = url.lastIndexOf("/")
  return cut <= 0 ? "/" : url.slice(0, cut)
}

function siteOf(address: string): string {
  const rest = String(address).replace(/^post:/, "")
  const cut = rest.indexOf("/")
  return cut < 0 ? rest : rest.slice(0, cut)
}

function urlOf(address: string, site: string): string {
  return String(address).replace(/^post:/, "").slice(site.length) || "/"
}

function substitute(value: unknown, values: Record<string, unknown>): unknown {
  if (typeof value === "string") {
    return value.replace(PLACEHOLDER, (whole, name: string) =>
      (values[name] === undefined ? whole : String(values[name])))
  }
  if (Array.isArray(value)) return value.map((item) => substitute(item, values))
  if (value && typeof value === "object") {
    const out: Record<string, unknown> = {}
    for (const key of Object.keys(value).sort()) out[key] = substitute((value as Record<string, unknown>)[key], values)
    return out
  }
  return value
}

function walk(value: unknown, onText: (text: string) => void) {
  if (typeof value === "string") onText(value)
  else if (Array.isArray(value)) value.forEach((item) => walk(item, onText))
  else if (value && typeof value === "object") Object.values(value).forEach((item) => walk(item, onText))
}
