/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH1024 — where a saved view opens: its path, and its query when it has one.
 *
 * A view is a list's URL under a name (SH1023 put every filter in the address), so opening it is
 * navigating to exactly what was saved. Pure, so `filter-bar.test.mjs` checks it without a browser.
 */
export function viewHref(view: { path: string; query?: string | null }): string {
  const query = (view.query ?? "").replace(/^\?/, "")
  return query ? `${view.path}?${query}` : view.path
}
