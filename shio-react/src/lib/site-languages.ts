/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import type { ShSiteLocaleDeclaration, ShSiteLocalePolicy } from "@/models/site/site"

/**
 * SH1056 — the rules a site's language policy is edited by, pure so a test can run them.
 *
 * The server validates a policy as one value — a default outside the enabled set is not a default,
 * and a fallback naming a locale the site does not offer is a redirect to nothing — and answers a
 * teaching 400 for each. These rules keep the form from ever composing one of those, so the curator
 * meets the refusal only for what a form cannot know (a tag the instance has since dropped).
 */
export type LanguagesDraft = {
  /** Whether the site declares a policy at all. Off, it reports what its content is written in. */
  declare: boolean
  enabled: string[]
  defaultLocale: string
  fallback: Record<string, string>
  /** SH1281 — what the site's switcher calls a locale; an absent key keeps the language's name. */
  labels: Record<string, string>
}

/** The draft a form starts from: the policy the site reports, declared or not. */
export function draftOf(policy: ShSiteLocalePolicy): LanguagesDraft {
  return {
    declare: policy.declared,
    enabled: [...policy.enabled],
    defaultLocale: policy.defaultLocale,
    fallback: { ...policy.fallback },
    labels: { ...(policy.labels ?? {}) },
  }
}

/** SH1281 — sets what the switcher calls one locale, or clears it with a blank label. */
export function setLabel(draft: LanguagesDraft, tag: string, label: string): LanguagesDraft {
  const labels = { ...draft.labels }
  if (label.trim()) labels[tag] = label
  else delete labels[tag]
  return prune({ ...draft, labels })
}

/**
 * Enables or disables one locale. A newly enabled one goes last, because the order is the order a
 * switcher lists them in and that is a decision, not an alphabet.
 */
export function toggleLocale(draft: LanguagesDraft, tag: string, on: boolean): LanguagesDraft {
  const enabled = on
    ? (draft.enabled.includes(tag) ? draft.enabled : [...draft.enabled, tag])
    : draft.enabled.filter((each) => each !== tag)
  return prune({ ...draft, enabled })
}

/** Points a locale's missing pages at another enabled locale, or back at the default with `""`. */
export function setFallback(draft: LanguagesDraft, from: string, to: string): LanguagesDraft {
  const fallback = { ...draft.fallback }
  if (to) fallback[from] = to
  else delete fallback[from]
  return prune({ ...draft, fallback })
}

/**
 * What survives an edit: a default that is still enabled (else the first one that is), and only
 * the fallback rules whose both ends are still offered. A rule starting at the default is dropped
 * too — the default is where every chain already ends, so a rule from it is a loop in waiting.
 */
export function prune(draft: LanguagesDraft): LanguagesDraft {
  const defaultLocale = draft.enabled.includes(draft.defaultLocale)
    ? draft.defaultLocale
    : (draft.enabled[0] ?? "")
  const fallback: Record<string, string> = {}
  for (const [from, to] of Object.entries(draft.fallback)) {
    if (from !== defaultLocale && from !== to
        && draft.enabled.includes(from) && draft.enabled.includes(to)) {
      fallback[from] = to
    }
  }
  // SH1281 — a label for a locale the site no longer offers is refused by the server.
  const labels: Record<string, string> = {}
  for (const [tag, label] of Object.entries(draft.labels ?? {})) {
    if (draft.enabled.includes(tag) && label.trim()) labels[tag] = label
  }
  return { ...draft, defaultLocale, fallback, labels }
}

/**
 * The body `PUT /v2/site/{id}/locales` takes — the document `site.upsert` takes too. `labels` only
 * when there are any (SH1281): the policy is replaced whole, so leaving them out would erase them.
 */
export function declarationOf(draft: LanguagesDraft): ShSiteLocaleDeclaration {
  const declaration: ShSiteLocaleDeclaration = {
    default: draft.defaultLocale, enabled: [...draft.enabled], fallback: { ...draft.fallback },
  }
  if (Object.keys(draft.labels ?? {}).length) declaration.labels = { ...draft.labels }
  return declaration
}

/**
 * The locales the translations menu may offer (SH53's menu, narrowed by SH1056).
 *
 * A declared policy is the list, in its order: the site said which languages it publishes, and
 * offering the other forty is offering to write pages nothing will ever link to. An undeclared site
 * gets the whole catalogue, deliberately — narrowing to the derived set would stop a single-language
 * site from ever adding its first translation.
 */
export function offeredLocales<T extends { initials: string }>(
  catalogue: T[], policy: ShSiteLocalePolicy | null,
): T[] {
  if (!policy?.declared) return catalogue
  const byTag = new Map(catalogue.map((locale) => [locale.initials, locale]))
  return policy.enabled.flatMap((tag) => {
    const known = byTag.get(tag)
    return known ? [known] : []
  })
}
