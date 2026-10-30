import type { ShSite } from "@/models/site/site"

/**
 * SH184 — where a person can look at a site. The client half of the server's
 * `ShSiteFrontEnd`.
 *
 * Three console surfaces asked this question and each answered it inline: the site list's
 * **View Site**, the object browser's **View Site**, and the Universal Editor's iframe. All
 * three read `site.url` directly and all three gave up when it was empty — a toast in two
 * places and a sentence in the third.
 *
 * Empty is the *normal* state of a site an agent just built (`site.upsert` creates a blank
 * one), so the product's three ways of looking at a page were unavailable at exactly the
 * moment there was something new to see. SH183 removed the reason: Shio renders the page
 * itself at `/preview/**`.
 *
 * It is one function because it is one rule, and because a rule copied into three call sites
 * is a rule that will be right in two of them — the lesson SH245 is filed for on the server
 * side. The fallback deliberately mirrors `ShSiteFrontEnd.of` so the console and the API
 * cannot come to disagree about where a site is visible.
 */
export type SiteDestination = {
  /** The URL to open or embed. Never empty. */
  url: string
  /** `false` when this is Shio's own renderer rather than a deployed front end. */
  external: boolean
}

/**
 * SH1304 — where a *visitor* sees a site, which `siteDestination` does not answer: that one falls
 * back to the authenticated, bannered, draft-preferred preview. A deployed front end is where the
 * published site lives; without one it is `/sites/<furl>/`, the only route that serves PUBLISHED
 * alone and enforces the members-only gate. Mirrors `ShSiteFrontEnd.publishedUrl` on the server.
 */
export function publishedDestination(site: ShSite): SiteDestination {
  if (site.url?.trim()) return siteDestination(site)
  return { url: `/sites/${encodeURIComponent(site.furl || previewKey(site))}/`, external: false }
}

/** The `/preview/{siteKey}` route accepts an id, a furl or a name (SH181). */
function previewKey(site: ShSite): string {
  return site.name || site.id || ""
}

/**
 * @param site the site to look at
 * @param path an optional page path within it; defaults to the site root
 */
export function siteDestination(site: ShSite, path = "/"): SiteDestination {
  const configured = site.url?.trim()
  if (configured) {
    const base = configured.endsWith("/") ? configured.slice(0, -1) : configured
    return { url: base + (path.startsWith("/") ? path : `/${path}`), external: true }
  }
  const base = `/preview/${encodeURIComponent(previewKey(site))}`
  return { url: base + (path.startsWith("/") ? path : `/${path}`), external: false }
}
