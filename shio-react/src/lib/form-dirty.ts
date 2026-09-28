/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH1196 — whether a form differs from what was loaded, which is what the save bar's `dirty` cue
 * says out loud. No page of the infrastructure editors said it, so leaving one with an unsaved
 * change was silent.
 *
 * <p>The baseline is the record as the form seeded it, not local state kept beside the form: a
 * second copy of the record would be one more thing to reset in render, which the console's lint
 * already forbids. `undefined` means nothing has loaded yet, and nothing loaded is not a change.</p>
 */
export function isDirty<T>(current: T, baseline: T | undefined): boolean {
  if (baseline === undefined) return false
  return JSON.stringify(current) !== JSON.stringify(baseline)
}
