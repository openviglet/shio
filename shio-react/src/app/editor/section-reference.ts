/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH1101 — how a duplicated section's copy is named in the Page's array, in the original entry's
 * own spelling.
 *
 * A path entry (`/sections/hero`) becomes the same path with the copy's friendly URL as its last
 * segment, because the copy is made in the original's folder; anything else becomes the copy's id.
 * Keeping the neighbour's spelling is what keeps the file projection's lines alike, and both
 * spellings resolve (SH181 tries the id first). Pure, so `section-reference.test.mjs` holds it.
 */
export function copyReference(original: unknown, copy: { id: string; furl?: string }): string {
  if (typeof original === "string" && original.startsWith("/") && copy.furl) {
    return original.replace(/[^/]*$/, copy.furl)
  }
  return copy.id
}
