export type ShSite = {
  id: string;
  name: string;
  description: string;
  url: string;
  furl: string;
  postTypeLayout: string;
  searchablePostTypes: string;
  /** SH1283 — the type a new page in this site starts as; null while the site names none. */
  pageType?: string | null;
  /**
   * SH1299 — send a visitor turned away from a members-only page to sign in, rather than the 404.
   * Null and false are both off.
   */
  membersLogin?: boolean | null;
  owner: string;
  date: string;
  /** SH1227 — present only on a site in the trash: when it went in. */
  deletedDate?: string;
  /**
   * SH1217 — present on rows of `GET /v2/site`, the listing, and absent on a single-site read. A
   * page live with a pending draft counts under both.
   */
  pages?: { published: number; draft: number };
  /** SH1217 — the site's newest change; absent when nothing was recorded against it. */
  lastChange?: { at: string; actor: string; agent: boolean } | null;
  /**
   * SH1218 — the stored picture of the published home, taken at appearance `digest`; absent until
   * one is stored, which the grid shows as the site's initials.
   */
  thumbnail?: { url: string; digest: string; at: string } | null;
};

/**
 * SH1056 — the languages a site is in, as `GET /v2/site/{id}/locales` reports them. The same four
 * fields the delivery API's `ShioSiteLocales` carries (SH1123).
 */
export type ShSiteLocalePolicy = {
  defaultLocale: string;
  enabled: string[];
  fallback: Record<string, string>;
  /** False while nobody has chosen: `enabled` is then the languages the content is already in. */
  declared: boolean;
  /** SH1281 — what the site's switcher calls each locale; absent or empty when it never said. */
  labels?: Record<string, string>;
};

/**
 * SH1131 — a site's translation coverage: a row per page, a cell per language.
 *
 * A cell carries both identifiers because the two readers address a page differently — the console
 * opens `/content/post/{id}` and an agent writes to `post:<site>/<url>`.
 */
export type ShSiteCoverage = {
  site: string;
  locales: string[];
  rows: ShCoverageRow[];
  totals: Record<string, ShCoverageCount>;
  page: number;
  size: number;
  total: number;
};

export type ShCoverageRow = {
  address: string;
  id: string;
  title?: string;
  cells: Record<string, ShCoverageCell>;
};

export type ShCoverageCell = {
  /** `current`, `stale` (SH1058) or `missing`. */
  state: "current" | "stale" | "missing";
  address?: string;
  id?: string;
  /** SH1062 — the translation carries an open request to the connected agent. Absent otherwise. */
  requested?: boolean;
};

export type ShCoverageCount = { current: number; stale: number; missing: number };

/**
 * SH1063 — search indexing: whether it is on, where it sends, and which sites route nowhere.
 *
 * A site with published pages and no `searchSite` reaches no index and raises no error, which is
 * the row the card exists to show.
 */
export type ShIndexStatus = {
  enabled: boolean;
  target?: string | null;
  defaultSite?: string | null;
  sites: { site: string; searchSite?: string | null; published: number }[];
  failures: number;
};

/** SH1056 — what a declaration sends: the document `site.upsert` takes under `data.locales`. */
export type ShSiteLocaleDeclaration = {
  default: string;
  enabled: string[];
  fallback: Record<string, string>;
  /** SH1281 — sent only when set, keyed by enabled locales. */
  labels?: Record<string, string>;
};

/** SH1135 — one note the instance remembers, as `GET /v2/agent/memory` answers it. */
export type ShSiteMemoryNote = {
  scope?: string | null;
  key: string;
  note: string;
  author?: string | null;
  updated?: string | null;
};

/**
 * SH1079 — what `POST /v2/import` answers: the site, what arrived in it, and what the import could
 * not do. `code` is one of `post-skipped`, `post-type-unresolved` or `file-bytes-missing` (SH1134).
 */
export type ShImportOutcome = {
  site: ShSite;
  /** `files` counts the File posts whose bytes were restored (SH1134). */
  arrived: { sites: number; folders: number; posts: number; postTypesCreated: number; files: number };
  problems: { code: string; subject: string | null; message: string }[];
};
