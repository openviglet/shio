/**
 * The package marketplace (SH646, SH679).
 *
 * Mirrors `ShMarketplaceItem` and `ShMarketplaceCatalog.Catalogue` on the server. `params` stays
 * `unknown` rather than a JSON-Schema type: the console reads exactly two things out of it — the
 * property names and each one's description and default — and typing the whole of JSON Schema to
 * read three fields is a type nobody can keep true.
 */
export interface ShMarketplaceItem {
  id: string
  title: string | null
  description: string | null
  version: string | null
  author: string | null
  category: string | null
  tags: string[]
  icon: string | null
  downloadUrl: string | null
  readmeUrl: string | null
  params: ShMarketplaceParams | null
  /** SH151/SH152 opt-ins the package declares it needs. Shown as a consent, not a footnote. */
  requires: string[]
  /** It shipped with this instance. */
  builtIn: boolean
  /** The registry already holds a package under this name. */
  installed: boolean
}

/** Only the parts of the package's JSON Schema the install form reads. */
export interface ShMarketplaceParams {
  properties?: Record<string, ShMarketplaceParam>
  required?: string[]
}

export interface ShMarketplaceParam {
  type?: string
  description?: string
  default?: string | number | boolean
}

/**
 * Where the listing's entries came from.
 *
 * Rendered on the page rather than swallowed: with no catalogue configured the answer is the
 * packages that shipped with the instance and nothing else, which looks identical to a catalogue
 * that returned nothing.
 */
export interface ShMarketplaceSource {
  url: string | null
  /** `builtin-only` · `remote` · `unreachable` */
  state: string
  reason: string | null
}

export interface ShMarketplaceCatalogue {
  items: ShMarketplaceItem[]
  source: ShMarketplaceSource
}

/** What an install did, or would do. */
export interface ShMarketplaceInstalled {
  id: string
  version: string | null
  origin: string
  installed: boolean
  loaded: number
}

/** SH1251 — what the dialog sends to `POST /api/v2/marketplace/{id}/setup`. */
export interface ShMarketplaceSetupRequest {
  site: string
  params: Record<string, string | number | boolean>
  createSite: boolean
  mergePostTypes: boolean
  dryRun: boolean
}

/** The parts of `ShBlueprintApplied` the dialog shows. */
export interface ShMarketplaceApplied {
  dryRun: boolean
  blueprint: string
  version?: string | null
  site: string
  postTypes?: { name: string; action: string; missing?: string[] }[]
  content?: { ops: number; changed: number; results?: { action: string; address: string }[] }
  createdSite?: string | null
  /** SH1252 — the images the package writes into the site. */
  assets?: { path: string; action: string; bytes: number }[]
}

/** The install half, absent when the package was already here, and the apply or its plan. */
export interface ShMarketplaceSetupResult {
  install: ShMarketplaceInstalled | null
  apply: ShMarketplaceApplied
}

export const MARKETPLACE_BUILTIN_ONLY = "builtin-only"
export const MARKETPLACE_REMOTE = "remote"
export const MARKETPLACE_UNREACHABLE = "unreachable"
