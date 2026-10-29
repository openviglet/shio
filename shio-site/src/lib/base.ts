/**
 * The site is published under a path, not at a host's root: https://openviglet.github.io/shio/.
 * `base` in vite.config.ts is the one place that says so, and Vite hands it to both builds (the
 * client and the SSR one the prerender runs) as `BASE_URL`, so a link written here prerenders and
 * hydrates to the same string.
 *
 * Every internal link is written root-anchored ("/console/", "/#laws") and goes through this,
 * because a bare "/console/" on that host is GitHub's 404, not this site's page.
 */
export const BASE = import.meta.env.BASE_URL;

/** `"/console/"` → `"/shio/console/"`. Anything not root-anchored (a URL, a "#hash") passes through. */
export function withBase(path: string): string {
  return path.startsWith("/") ? `${BASE}${path.slice(1)}` : path;
}
