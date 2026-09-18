/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH1023 — a list's state, read out of and written back into the URL.
 *
 * ## Why the logic is here and not in the hook
 *
 * Everything below is a function of a `URLSearchParams` and a set of defaults, which means it can
 * be asserted without rendering anything. The React half is thirty lines of `useSearchParams` over
 * these, and the property worth guarding — **the same address produces the same view** — is a
 * round trip: read what was written and get back what you set.
 *
 * ## Three rules, each of which cost something before it was written down
 *
 * **A default is absent, not written.** `?sort=title&view=list&type=&state=` is the same view as
 * `/content/folder/x` and reads like a filtered one. A URL that names what is not narrowing
 * anything cannot be skimmed, and the whole point of putting the state there is that a person
 * reads it before pasting it to somebody.
 *
 * **A key this list does not own is left alone.** The console carries params that are not list
 * state — a tenant prefix is in the path, but a `tab` or a `from` is not — and a hook that wrote
 * the whole query string would drop them on the first keystroke.
 *
 * **The order is the declaration's.** Two callers that set the same filters in a different order
 * would otherwise produce two URLs for one view, which is P5 for something a person compares by
 * eye: "is this the link I sent you" is answered by looking.
 */

/** What a list declares: each key it owns, and the value that means "not narrowing". */
export type ListDefaults<K extends string> = Record<K, string>

/** The committed value of every declared key, defaults filled in. */
export function listState<K extends string>(
  defaults: ListDefaults<K>,
  search: URLSearchParams,
): Record<K, string> {
  const state = {} as Record<K, string>
  for (const key of Object.keys(defaults) as K[]) {
    const value = search.get(key)
    state[key] = value === null || value === "" ? defaults[key] : value
  }
  return state
}

/**
 * The next query string, with one key changed.
 *
 * <p>Ordered by the declaration and then by whatever else the URL carried, so the address for a
 * view does not depend on the order the curator touched the controls in.</p>
 */
export function nextParams<K extends string>(
  defaults: ListDefaults<K>,
  search: URLSearchParams,
  changes: Partial<Record<K, string>>,
): URLSearchParams {
  const merged = { ...listState(defaults, search), ...changes }
  const next = new URLSearchParams()
  for (const key of Object.keys(defaults) as K[]) {
    const value = merged[key]
    if (typeof value === "string" && value !== "" && value !== defaults[key]) {
      next.set(key, value)
    }
  }
  for (const [key, value] of search.entries()) {
    if (!(key in defaults) && !next.has(key)) {
      next.append(key, value)
    }
  }
  return next
}

/** Every declared key dropped, and nothing else touched — what a "clear filters" button does. */
export function clearedParams<K extends string>(
  defaults: ListDefaults<K>,
  search: URLSearchParams,
): URLSearchParams {
  const cleared = {} as Partial<Record<K, string>>
  for (const key of Object.keys(defaults) as K[]) {
    cleared[key] = defaults[key]
  }
  return nextParams(defaults, search, cleared)
}

/** How many declared keys are narrowing the list — what a toolbar badge counts. */
export function narrowedBy<K extends string>(
  defaults: ListDefaults<K>,
  search: URLSearchParams,
  ignoring: readonly K[] = [],
): number {
  const state = listState(defaults, search)
  return (Object.keys(defaults) as K[])
    .filter((key) => !ignoring.includes(key))
    .filter((key) => state[key] !== defaults[key]).length
}
