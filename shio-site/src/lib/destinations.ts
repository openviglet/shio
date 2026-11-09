/**
 * SH1371: where each job a visitor has goes, one map for every surface that links out: the
 * marketing nav and footer, the calls to action, the docs area's header and footer, and the
 * checked links in `public/llms.txt`.
 *
 * The docs area had already sent Get started to the installation guide and Changelog to the hub's
 * release notes, while the marketing site sent the first to the docs overview and the second to
 * the GitHub task ledger, so the same word led to two places depending on the page. Each entry is
 * named for its job, and the jobs stay separate: release notes are for someone running Viglet
 * Shio, while the roadmap and the shipped ledger are development records.
 *
 * Paths are relative to the site's base, so each surface applies its own base; external URLs are
 * absolute. Install and release history belong to the hub (viglet.org/shio/download/ and
 * /shio/release-notes/). This site links to them rather than keeping a second copy.
 */
export const DESTINATIONS = {
  /** Read about it: the documentation overview. */
  docs: { path: "/docs/" },
  /** Get started: the supported setup path, with prerequisites and a first-success check. */
  install: { path: "/docs/installation-guide/" },
  /** Try it by hand: the console tour. */
  explore: { path: "/console/" },
  /** Download a release: the hub's page. */
  download: { url: "https://www.viglet.org/shio/download/" },
  /** What changed in each release, for someone running it. */
  releaseNotes: { url: "https://www.viglet.org/shio/release-notes/" },
  /** The same release notes as a feed this site serves. */
  releaseFeed: { path: "/changelog.json" },
  /** The source. */
  source: { url: "https://github.com/openviglet/shio" },
  /** What is being built next: a development record, not release notes. */
  roadmap: { url: "https://github.com/openviglet/shio/blob/HEAD/docs/ROADMAP.md" },
  /** Every task that shipped: the development ledger. */
  ledger: { url: "https://github.com/openviglet/shio/blob/HEAD/docs/CHANGELOG.md" },
  /** Back to the portfolio. */
  portfolio: { url: "https://www.viglet.org" },
} as const;

export type Destination = keyof typeof DESTINATIONS;

/** The href for a destination, with `base` (the site's base, ending in `/`) applied to a path. */
export function hrefOf(destination: Destination, base = "/"): string {
  const entry: { path?: string; url?: string } = DESTINATIONS[destination];
  if (entry.url) return entry.url;
  return base.replace(/\/$/u, "") + entry.path;
}
