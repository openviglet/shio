/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useEffect } from "react"
import { onSuggestion, takeFor } from "@/lib/field-suggestions"

/**
 * SH1019 — write a suggestion the curator applied from the dock into this post's form.
 *
 * <p>Into the form and nowhere else: it becomes an unsaved edit, so the page's dirty state, its
 * validation, autosave and the save button all treat it exactly as if the curator had typed it.
 * Taken once the post has loaded too, because "apply" navigates here and the value may be waiting
 * when the page arrives; taken before, the load would overwrite it.</p>
 */
export function useAppliedSuggestions(postId: string | undefined, loaded: boolean,
  setData: (update: (current: Record<string, unknown>) => Record<string, unknown>) => void) {
  useEffect(() => {
    // After the post has loaded, or the load would overwrite the edit with the stored value.
    if (!postId || !loaded) return
    const apply = () => {
      const mine = takeFor(postId)
      if (mine.length === 0) return
      setData((current) => {
        const next = { ...current }
        for (const each of mine) next[each.field] = each.value
        return next
      })
    }
    // Deferred, not called in the effect body: the value is data arriving, not render state.
    void Promise.resolve().then(apply)
    return onSuggestion(apply)
  }, [postId, loaded, setData])
}
