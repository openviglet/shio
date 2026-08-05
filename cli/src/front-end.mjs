/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH442 — **a slug is not a front end.**
 *
 * A site's URL field holds **three** states, not two: absent, absolute, and a *slug*, which is the
 * default for any site created without a public URL. Two readers were testing it for truthiness, and
 * a slug is truthy:
 *
 * - `snapshot`/`audit` took the front-end branch and handed the slug to a browser as an absolute
 *   address. Measured: `snapshot --page post:Viglet2/ --against <source>` failed with playwright's
 *   own `Cannot navigate to invalid URL`, call log `navigating to "viglet2/"` — no scheme, no host.
 *   `Bootstrap` holds `https://getbootstrap.com/` and worked; `Cafeteria` holds `cafeteria` and did
 *   not. `--preview` made the same run work, which is what located it.
 * - `report` composed `cafeteria/blog/hello` as a preview link and printed it for a human to click:
 *   the same wrong predicate, one file over, producing a broken link presented as a working one
 *   rather than the honest "this site has no front end".
 *
 * Its own module because that is the shape of the defect — two answers to one question, in two files
 * that otherwise share nothing. Pure and dependency-free, so neither of them gains a dependency for
 * a regex; `context.mjs` would have been the other home and it reaches for `node:fs`.
 */

/**
 * Whether a site's URL field names something a browser or a reader can actually open.
 *
 * <p>The same test `resolveUrl` has always applied to its *target*, which is the point: the target
 * and the site's URL end up in the same place, and only one of them was being checked.</p>
 */
export function isFrontEnd(url) {
  return /^https?:\/\//.test(String(url ?? "").trim());
}
