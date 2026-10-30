/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/** The two names a post type has: what a reader is shown, and the key the API and the files use. */
export interface NamedType {
  name: string
  title?: string | null
}

/** How a type is named to a reader: its title, or its name when it has none. */
export function labelOf(type: NamedType): string {
  return type.title?.trim() || type.name
}

/**
 * SH1310 — the API name, only when it says something the label does not.
 *
 * Most types are titled with their own name, so the list drew "InfoPage" under "InfoPage" on nearly
 * every row: half its text a copy, and every row twice the height its content needed. The key is
 * worth showing where it differs ("How this was built" is `BuildNote`), because that is the word an
 * agent and a file use for it.
 */
export function distinctKey(type: NamedType): string | undefined {
  return labelOf(type) === type.name ? undefined : type.name
}
