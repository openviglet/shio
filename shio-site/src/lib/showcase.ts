import MATRIX from "../../../docs/specs/SH1250-showcase-matrix.json";
import PUBLISHED from "./marketplace-screenshots.generated.json";
import { withBase } from "./base";

/**
 * SH1258: the showcase's pages and what each one demonstrates, read from the matrix SH1250
 * wrote, never retyped. `cli/test/showcase-matrix.test.mjs` holds that matrix to the product
 * (every manifest feature, every MCP tool, every curator surface), so a page listed here is a
 * page the package carries and a feature listed under it is one that page shows.
 */

/** The catalogue id the package is published under (SH643's prefix plus the package name). */
export const SHOWCASE_ID = `shio-blueprint-${MATRIX.site}`;

/** The site name the install commands apply it to. */
export const SHOWCASE_SITE = MATRIX.site;

/**
 * The screenshots the catalogue entry publishes, as this site serves them. Captured by
 * `scripts/showcase-screenshots.mjs` from an applied showcase and listed by
 * `scripts/marketplace.mjs`, so these are the files the console shows before an install.
 * The name says the page: `2-trail.jpg` is the trail detail.
 */
export const SHOWCASE_SCREENSHOTS: { src: string; name: string }[] = (
  (PUBLISHED as Record<string, string[]>)[SHOWCASE_ID] ?? []
).map((file) => ({
  src: withBase(`/marketplace/${SHOWCASE_ID}/${file}`),
  name: file.replace(/^\d+-/, "").replace(/\.[a-z]+$/, ""),
}));

/** The languages every showcase page exists in. */
export const SHOWCASE_LOCALES: readonly string[] = MATRIX.locales;

type Mapping = Record<string, { page?: string; exclude?: string }>;

export interface ShowcasePage {
  path: string;
  summary: string;
  /** The anchor on `/showcase/` this page's row carries. */
  anchor: string;
  features: string[];
  tools: string[];
  surfaces: string[];
}

/** `/tour/agent` → `page-tour-agent`, `/` → `page-home`. */
export function anchorOf(path: string): string {
  const slug = path.split("/").filter(Boolean).join("-");
  return `page-${slug || "home"}`;
}

function onPage(mapping: Mapping, path: string): string[] {
  return Object.entries(mapping)
    .filter(([, target]) => target.page === path)
    .map(([key]) => key)
    .sort((a, b) => a.localeCompare(b));
}

const PAGES: ShowcasePage[] = Object.entries(MATRIX.pages as Record<string, string>).map(
  ([path, summary]) => ({
    path,
    summary,
    anchor: anchorOf(path),
    features: onPage(MATRIX.features as Mapping, path),
    tools: onPage(MATRIX.mcpTools as Mapping, path),
    surfaces: onPage(MATRIX.curatorSurfaces as Mapping, path),
  }),
);

/** The tour: the pages under `/tour`, which explain how the site was built. */
export const TOUR_PAGES = PAGES.filter((p) => p.path === "/tour" || p.path.startsWith("/tour/"));

/** The site itself: every other page, the magazine a visitor reads. */
export const SITE_PAGES = PAGES.filter((p) => !TOUR_PAGES.includes(p));

/**
 * The showcase page that demonstrates a manifest `features` key, for the link a depth page
 * carries. Thrown rather than skipped: a key the matrix does not map, or maps to an exclusion,
 * would otherwise drop the link silently, and a depth page whose "see it running" link vanished
 * reads exactly like one that never had it.
 */
export function showcasePageFor(feature: string): ShowcasePage {
  const target = (MATRIX.features as Mapping)[feature];
  const page = target?.page && PAGES.find((p) => p.path === target.page);
  if (!page) {
    throw new Error(
      `showcase: feature '${feature}' has no page in docs/specs/SH1250-showcase-matrix.json`
        + (target?.exclude ? ` (excluded: ${target.exclude})` : ""),
    );
  }
  return page;
}
