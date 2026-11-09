/**
 * SH1392 — the page the Universal Editor opens on, named in its address.
 *
 * `/bento/universal-editor/<site>?path=<friendly url>`: the part of `post:<site>/<path>` after the
 * site, so a page reaches the editor without a link drawn on another one (a draft nothing links to,
 * most of all), and the address follows the frame, so a reload or a shared link keeps the page.
 */
import { pageTypesOf, pathOf, type PageSite } from "./site-pages.ts"

/** The query parameter that names the page. */
export const EDITOR_PATH_PARAM = "path"

/** `?path=` as the path to frame: the home when absent, always one leading slash. */
export function framePath(requested: string | null): string {
  const rest = (requested ?? "").replace(/^\/+/, "")
  return `/${rest}`
}

/**
 * Where to open a site's page in the editor. `editorRoute` is `ROUTES.UNIVERSAL_EDITOR`, passed in so
 * this module loads outside Vite; `furl` is the page's friendly url, `/` or empty for the home.
 */
export function editorHref(editorRoute: string, siteId: string, furl?: string | null): string {
  const rest = (furl ?? "").replace(/^\/+/, "")
  return `${editorRoute}/${siteId}${rest ? `?${EDITOR_PATH_PARAM}=${encodeURIComponent(rest)}` : ""}`
}

/**
 * A search result as a page the editor can open: its site's id and its path, or null when the
 * address names no site in `sites` or its type is not one that site renders as a page (a section, a
 * menu or a theme has no page of its own to open).
 */
export function pageOnSite(address: string, type: string | undefined,
  sites: readonly (PageSite & { id?: string })[]): { siteId: string; path: string } | null {
  if (!type) return null
  const site = sites.find((each) => address === `post:${each.name}` || address.startsWith(`post:${each.name}/`))
  if (!site?.id || !pageTypesOf(site).has(type)) return null
  return { siteId: site.id, path: pathOf(address, site.name) }
}

/**
 * The editor's own address once the frame has moved to `reported`, or null when the frame left the
 * site (another origin, or a path outside the site's base), which names no page here.
 */
export function followedAddress(reported: string, siteBase: string, current: string): string | null {
  if (!URL.canParse(reported, current) || !URL.canParse(siteBase, current)) return null
  const at = new URL(reported, current)
  const base = new URL(siteBase, current)
  const prefix = base.pathname.replace(/\/+$/, "")
  if (at.origin !== base.origin || (at.pathname !== prefix && !at.pathname.startsWith(`${prefix}/`))) {
    return null
  }
  const rest = decodeURIComponent(at.pathname.slice(prefix.length)).replace(/^\/+/, "")
  const next = new URL(current)
  if (rest) next.searchParams.set(EDITOR_PATH_PARAM, rest)
  else next.searchParams.delete(EDITOR_PATH_PARAM)
  return next.toString()
}
