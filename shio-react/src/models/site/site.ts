export type ShSite = {
  id: string;
  name: string;
  description: string;
  url: string;
  furl: string;
  postTypeLayout: string;
  searchablePostTypes: string;
  owner: string;
  date: string;
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
};

/** SH1056 — what a declaration sends: the document `site.upsert` takes under `data.locales`. */
export type ShSiteLocaleDeclaration = {
  default: string;
  enabled: string[];
  fallback: Record<string, string>;
};
