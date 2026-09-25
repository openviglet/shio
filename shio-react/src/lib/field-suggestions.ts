/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH1019 — a suggestion the curator chose to apply, on its way from the dock to the form.
 *
 * <p>The dock and the post editor are different subtrees, and the curator may press "apply" from
 * any page. So the action leaves the value here, keyed by post, and navigates to the post; the
 * editor takes it when it is (or becomes) the page for that post, and writes it into the form as
 * an unsaved edit, so it goes through the same dirty state, validation and save as typing it.
 * Nothing here writes to the server.</p>
 */
export interface FieldSuggestion {
  postId: string
  field: string
  value: string
}

let pending: FieldSuggestion[] = []
const listeners = new Set<() => void>()

export function requestApply(suggestion: FieldSuggestion) {
  pending = [...pending.filter((each) => !(each.postId === suggestion.postId && each.field === suggestion.field)),
    suggestion]
  for (const listener of listeners) listener()
}

/** Take this post's waiting suggestions, once: a second reader of the same post gets none. */
export function takeFor(postId: string): FieldSuggestion[] {
  const mine = pending.filter((each) => each.postId === postId)
  if (mine.length) pending = pending.filter((each) => each.postId !== postId)
  return mine
}

export function onSuggestion(listener: () => void): () => void {
  listeners.add(listener)
  return () => { listeners.delete(listener) }
}
