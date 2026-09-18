/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useCallback, useEffect, useRef, useState } from "react"
import { useSearchParams } from "react-router-dom"

import {
  clearedParams, listState, narrowedBy, nextParams, type ListDefaults,
} from "@/lib/list-state"

/**
 * SH1023 — one list's search, sort, filters and view, held in the URL.
 *
 * ## What this replaces, everywhere it is used
 *
 * A `useState` per control, plus a debounced mirror of the text one, plus a `queryKey` string
 * joining them so the spinner belongs to the right answer. All four were written out per page,
 * and none of them was in the address — so the view on screen had no URL and the URL produced no
 * particular view. That is P5 broken where a person notices it: a curator who narrows a folder to
 * "drafts Ana changed this week" cannot send that to anybody, and an agent handed the link opens
 * the unfiltered folder.
 *
 * ## The two things a caller still has to decide
 *
 * **Which keys are text.** A `<Select>` commits when it changes; an `<Input>` commits when the
 * typing stops. A text key's `draft` leads its committed value by the debounce, which is what the
 * input binds to, and the URL write **replaces** rather than pushes — otherwise Back walks the
 * search term one character at a time and is useless for what Back is for.
 *
 * **What each key's default is.** A default is absent from the URL, so the default is also the
 * answer to "what does this address mean when it says nothing".
 *
 * ## What it deliberately does not do
 *
 * It does not fetch. The state is the input to whatever the page already does, and a hook that
 * owned the request as well would have to know about paging, cursors and which service to call —
 * which is six different answers across six lists, and the reason this is a hook and not a
 * component.
 */
export interface ListState<K extends string> {
  /** The committed value of each key: what the URL says, and what the server should be asked. */
  value: Record<K, string>
  /** What a text input shows. Equal to `value` except within one debounce of a keystroke. */
  draft: Record<K, string>
  /** One key, committed. Text keys are debounced and replace; the rest commit now and push. */
  set: (key: K, value: string) => void
  /** Several at once — one history entry for a change that moves more than one control. */
  patch: (changes: Partial<Record<K, string>>) => void
  /** Every declared key back to its default, leaving the rest of the URL alone. */
  clear: () => void
  /** How many keys are narrowing the list, ignoring the ones a badge should not count. */
  narrowed: (ignoring?: readonly K[]) => number
  /** The identity of the current query — what a page compares against what finished loading. */
  key: string
}

export function useListState<K extends string>(
  defaults: ListDefaults<K>,
  options: { text?: readonly NoInfer<K>[]; delay?: number } = {},
): ListState<K> {
  const { text = [] as readonly K[], delay = 300 } = options
  const [search, setSearch] = useSearchParams()
  const value = listState(defaults, search)

  // The draft exists only for text keys, and it is seeded from the URL so arriving at a link with
  // `?q=pricing` shows "pricing" in the box rather than an empty one above matching rows.
  const [draft, setDraft] = useState<Record<K, string>>(value)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const committed = JSON.stringify(value)

  useEffect(() => {
    // Keyed on the committed values rather than on the params object: Back and a programmatic
    // write both land here and must re-seed the box, while a render that merely touched the URL
    // must not — re-seeding mid-keystroke deletes what is being typed.
    setDraft(JSON.parse(committed) as Record<K, string>)
  }, [committed])

  useEffect(() => () => {
    if (timer.current) clearTimeout(timer.current)
  }, [])

  const write = useCallback((changes: Partial<Record<K, string>>, replace: boolean) => {
    // The updater form, because a debounced write lands one delay after the render that scheduled
    // it: closing over `search` would resurrect whatever the URL held when the key was pressed,
    // and a filter set while typing would be undone by the search term committing.
    setSearch((current) => nextParams(defaults, current, changes), { replace })
    // `defaults` is a literal at every call site, so it is stable in practice and unstable by
    // identity; listing it would re-create this callback on every render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [setSearch])

  const set = useCallback((key: K, next: string) => {
    if (!text.includes(key)) {
      write({ [key]: next } as Partial<Record<K, string>>, false)
      return
    }
    setDraft((prev) => ({ ...prev, [key]: next }))
    if (timer.current) clearTimeout(timer.current)
    timer.current = setTimeout(() => write({ [key]: next } as Partial<Record<K, string>>, true), delay)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [delay, write])

  const patch = useCallback((changes: Partial<Record<K, string>>) => write(changes, false), [write])

  const clear = useCallback(() => {
    if (timer.current) clearTimeout(timer.current)
    setSearch((current) => clearedParams(defaults, current), { replace: false })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [setSearch])

  const narrowed = useCallback(
    (ignoring: readonly K[] = []) => narrowedBy(defaults, search, ignoring),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [committed],
  )

  return { value, draft, set, patch, clear, narrowed, key: committed }
}
