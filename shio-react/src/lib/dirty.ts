/**
 * SH1011 — is there unsaved work on this page?
 *
 * The editor holds its data in `useState` and never compared it with the post it loaded, so nothing
 * on the page knew the difference between "opened and read" and "typed into and not saved". The
 * design system has rendered an **Unsaved changes** cue on `dirty` since it shipped; the page that
 * needed it most passed only `titleMissing`.
 *
 * ## Why a canonical serialization and not a deep compare
 *
 * A widget hands back a new object on every keystroke, so reference equality says dirty always. A
 * field-by-field compare says it too, for a reason that is harder to see: a form re-serializes the
 * document it was given, and `{a, b}` and `{b, a}` are the same document with different key order.
 * Comparing sorted, stably-serialized text answers the question actually being asked — *does this
 * differ from what was loaded* — and it is the same rule `shio pull` uses to decide whether a file
 * changed (P5).
 */

/** Recursively sorted, so key order is never mistaken for a change. */
function canonicalize(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(canonicalize)
  if (value && typeof value === "object") {
    const entries = Object.entries(value as Record<string, unknown>)
      .filter(([, item]) => item !== undefined)
      .sort(([a], [b]) => a.localeCompare(b))
    return Object.fromEntries(entries.map(([key, item]) => [key, canonicalize(item)]))
  }
  // `null` and `""` are both "nothing was typed here" as far as a form is concerned, and a widget
  // that clears its input hands back one or the other depending on the widget. Folding them is what
  // stops an untouched page from reading as dirty the moment a widget mounts.
  return value === null ? "" : value
}

/** The document as one comparable string. Exported because a test asserts the rule, not the result. */
export function canonicalJson(value: unknown): string {
  return JSON.stringify(canonicalize(value ?? {}))
}

/** Whether `current` differs from `loaded` in anything but key order. */
export function isDirty(loaded: unknown, current: unknown): boolean {
  return canonicalJson(loaded) !== canonicalJson(current)
}
