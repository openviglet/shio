/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH985's comparison, out of the dialog file (SH1136): a module that exports a component may export
 * only components for fast refresh to work, and these are pure functions of two states that the post
 * editor calls and the dialog never does.
 */
export interface PostConflictField {
  /** The field's name, as `data` keys it. */
  name: string
  /** What the form shows it as, for a reader — never the raw object. */
  mine: string
  /** What is stored now. */
  theirs: string
  /** The label the form uses, when the schema has one. */
  label: string
}

/** A value a person can read: an empty field says so rather than rendering as nothing. */
export function readable(value: unknown): string {
  if (value === null || value === undefined || value === "") return ""
  if (typeof value === "string") return value
  if (typeof value === "number" || typeof value === "boolean") return String(value)
  try {
    return JSON.stringify(value)
  } catch {
    return String(value)
  }
}

/** Which fields two states disagree about, compared the way a save would overwrite them. */
export function conflictsBetween(
  mine: Record<string, unknown>,
  theirs: Record<string, unknown>,
  labelOf: (name: string) => string = (name) => name
): PostConflictField[] {
  const names = new Set([...Object.keys(mine ?? {}), ...Object.keys(theirs ?? {})])
  const found: PostConflictField[] = []
  for (const name of [...names].sort()) {
    const left = readable(mine?.[name])
    const right = readable(theirs?.[name])
    if (left !== right) found.push({ name, mine: left, theirs: right, label: labelOf(name) })
  }
  return found
}
