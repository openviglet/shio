/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH1028 — how the palette ranks the actions it offers.
 *
 * ## Why the product ranks at all
 *
 * `BentoCommandPalette` takes a product group and **does not reorder it** (VDS123), for a reason
 * worth keeping: the product asked a source that understands the query, and a substring match in
 * the component would only undo that ranking. The content half is ranked by the server, which
 * knows what matches. The actions half has no server, so the ranking is here — and being here
 * means it is a function of two strings and a little state, which is a thing that can be asserted
 * without rendering anything.
 *
 * ## Three signals, in this order
 *
 * **Where you are** outranks everything. "Publish this page" is a different offer on a post than
 * in a folder listing, and a palette that ranked it the same in both is one a curator learns to
 * scroll past. **How well it matches** comes next — a prefix of the label beats a subsequence of
 * it, which is what makes `pub` land on Publish rather than on "Republish everything". **What you
 * used last** breaks the remaining ties, because a console has a handful of verbs somebody runs
 * all day and no amount of cleverness beats remembering them.
 *
 * ## Subsequence, not fuzzy-with-a-score
 *
 * `pbl` matches "Publish" the way every palette does, and the score is the *span* it needed: a
 * tight match scores better than a scattered one. Nothing here weights character classes or
 * camel-case boundaries. Those make a matcher feel clever on the examples it was tuned on and
 * unpredictable everywhere else, and a palette is a thing people build muscle memory against.
 */

/** One thing the palette can offer, before it is scored. */
export interface ShCommandCandidate {
  id: string
  label: string
  /** Other words a reader might type for it — "new" for Create, "check" for Verify. */
  aliases?: readonly string[]
  /** True when this action is about the page the reader is on. */
  contextual?: boolean
}

/** A scored candidate: higher is better, and `null` means it did not match at all. */
export function score(
  candidate: ShCommandCandidate,
  query: string,
  recent: readonly string[] = [],
): number | null {
  const term = query.trim().toLowerCase()
  const words = [candidate.label, ...(candidate.aliases ?? [])]
  const best = words
    .map((word) => match(word.toLowerCase(), term))
    .filter((value): value is number => value !== null)
    .sort((a, b) => b - a)[0]
  if (best === undefined) {
    return null
  }
  const position = recent.indexOf(candidate.id)
  // Recency is worth at most one place, which is what "breaks a tie" means: it must not float a
  // poor match above a good one just because somebody ran it this morning.
  const recency = position < 0 ? 0 : Math.max(0, 5 - position)
  return (candidate.contextual ? 1000 : 0) + best + recency
}

/**
 * How well one word matches a term: 100 for a prefix, less as the match spreads out.
 *
 * An empty term matches everything at the same score, which is what makes an empty palette show
 * the whole list in its declared order rather than in an arbitrary one.
 */
function match(word: string, term: string): number | null {
  if (!term) {
    return 50
  }
  if (word.startsWith(term)) {
    return 100
  }
  const at = word.indexOf(term)
  if (at >= 0) {
    // A whole-word hit anywhere beats any subsequence: "publish" in "Unpublish page" is a better
    // answer than p-u-b scattered across "Pull back".
    return 90 - Math.min(at, 20)
  }
  let index = 0
  let first = -1
  let last = -1
  for (let position = 0; position < word.length && index < term.length; position += 1) {
    if (word[position] === term[index]) {
      if (first < 0) first = position
      last = position
      index += 1
    }
  }
  if (index < term.length) {
    return null
  }
  const span = last - first + 1
  return Math.max(1, 60 - (span - term.length))
}

/** The candidates that match, best first, with ties broken by declaration order. */
export function rank<T extends ShCommandCandidate>(
  candidates: readonly T[],
  query: string,
  recent: readonly string[] = [],
): T[] {
  return candidates
    .map((candidate, index) => ({ candidate, index, score: score(candidate, query, recent) }))
    .filter((entry): entry is { candidate: T; index: number; score: number } => entry.score !== null)
    .sort((a, b) => b.score - a.score || a.index - b.index)
    .map((entry) => entry.candidate)
}

/** The id moved to the front of the recency list, capped — what a selection records. */
export function remember(recent: readonly string[], id: string, cap = 8): string[] {
  return [id, ...recent.filter((each) => each !== id)].slice(0, cap)
}
