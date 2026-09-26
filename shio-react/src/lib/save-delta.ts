/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH1109 — what a save in the visual editor changed, said in one line instead of the word *saved*.
 *
 * The console takes the page's two render digests (`GET /agent/render`: `digest` over structure,
 * `appearanceDigest` over what draws it) before the write and again after. Two hashes side by side
 * say only that something differs, which a curator who pressed Save already knew; the **delta** is
 * the deliverable. It names the fields the change log wrote, says whether the structure and the
 * appearance moved, and flags the one case this editor is most likely to cause by accident: an edit
 * of text alone that moved the appearance, which is a reflow nobody meant.
 *
 * Measured before it was spent: one digest of the marketing package's composed home page is a
 * median of about 41 ms on the server, so the pair is taken on every save rather than on request.
 */

/** The two digests `/agent/render` answers for a page. */
export type PageDigests = { digest?: string | null; appearanceDigest?: string | null }

/** One entry of the editor's change log, reduced to what a delta reads. */
export type SavedChange = { postId: string; prop: string; type: string; label?: string }

export type SaveDelta = {
  /** Field labels, in the order the log holds them, each once. */
  fields: string[]
  /** Whether the structural digest moved; null when either side could not be read. */
  structureMoved: boolean | null
  /** Whether the appearance digest moved; null when either side could not be read. */
  appearanceMoved: boolean | null
  /** Every change was typed text: no attribute, no widget panel. */
  textOnly: boolean
  /** Text alone was edited and the appearance moved anyway — the reflow to look at. */
  unexpected: boolean
  /**
   * The log in the change-set shape the agent path answers with, so what a curator did reads like
   * what an agent did: one row per post, the fields it wrote.
   */
  changeSet: { changed: { id: string; fields: string[] }[] }
}

export function saveDelta(
  { changes, before, after }: { changes: SavedChange[]; before: PageDigests | null; after: PageDigests | null },
): SaveDelta {
  const fields = [...new Set(changes.map((change) => change.label || change.prop))]
  const structureMoved = moved(before?.digest, after?.digest)
  const appearanceMoved = moved(before?.appearanceDigest, after?.appearanceDigest)
  const textOnly = changes.length > 0 && changes.every((change) => change.type === "text")
  const byPost = new Map<string, string[]>()
  for (const change of changes) {
    const held = byPost.get(change.postId) ?? []
    if (!held.includes(change.prop)) held.push(change.prop)
    byPost.set(change.postId, held)
  }
  return {
    fields,
    structureMoved,
    appearanceMoved,
    textOnly,
    unexpected: textOnly && appearanceMoved === true,
    changeSet: { changed: [...byPost].map(([id, props]) => ({ id, fields: props })) },
  }
}

function moved(before: string | null | undefined, after: string | null | undefined): boolean | null {
  if (!before || !after) return null
  return before !== after
}
