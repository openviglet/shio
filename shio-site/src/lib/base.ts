/**
 * The path the site is published under. Today that is the root of https://shio.viglet.org/, so
 * BASE is "/"; it was "/shio/" while the site lived at openviglet.github.io/shio (2026-10-29 to
 * 2026-11-05). `base` in vite.config.ts is the one place that says so, and Vite hands it to both
 * builds (the client and the SSR one the prerender runs) as `BASE_URL`, so a link written here
 * prerenders and hydrates to the same string.
 *
 * Every internal link is written root-anchored ("/console/", "/#laws") and goes through this, so
 * moving the site under a path again is a change to `base` and nothing else.
 */
export const BASE = import.meta.env.BASE_URL;

/** `"/console/"` → `${BASE}console/`. Anything not root-anchored (a URL, a "#hash") passes through. */
export function withBase(path: string): string {
  return path.startsWith("/") ? `${BASE}${path.slice(1)}` : path;
}
