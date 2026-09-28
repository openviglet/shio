const consolePath: string = import.meta.env.VITE_CONSOLE_CONTEXT_PATH;

const RESERVED_TOP_SEGMENTS = new Set([
  "",
  "login",
  "logout",
  "setup",
  "api",
  "graphql",
  "graphiql",
  "preview",
  "assets",
  "content",
  "console",
  /*
   * SH887 — the bento proving page is a top-level route, and every unreserved first segment is
   * read as a tenant. Without this entry `/bento-vocabulary` resolves TENANT_SEGMENT to
   * "/bento-vocabulary" and every ROUTES.* the page links to is prefixed with it, so the hero's
   * back link and the specimen tiles all point into a tenant that does not exist. Reserved
   * rather than nested under `console`, because the page must not mount inside the console
   * shell's SidebarProvider — the whole reason it is top-level.
   */
  "bento-vocabulary",
  /*
   * SH888 — the parallel bento route tree, for the same reason and one more: it is a sibling of
   * `console`, not a child, because the two shells cannot nest. Reserved here so `/bento/...`
   * is a route rather than a tenant named "bento".
   */
  "bento",
]);

function readTenantSegment(): string {
  if (typeof window === "undefined") return "";
  const first = window.location.pathname.split("/").filter(Boolean)[0] ?? "";
  return RESERVED_TOP_SEGMENTS.has(first) ? "" : `/${first}`;
}

export const TENANT_SEGMENT = readTenantSegment();

const scoped = (path: string) => `${TENANT_SEGMENT}${path}`;

/**
 * SH909 — where the console lives, and it is `/bento`.
 *
 * Every constant below used to be `scoped(consolePath)`-prefixed, and re-pointing this one line
 * moves all of them: the 24 call sites that build a path from `ROUTES.CONSOLE`, the nav
 * declaration, and every `navigate()` in the console follow without being touched. That is the
 * whole reason they were constants.
 *
 * **The old prefix keeps working** — `LEGACY_CONSOLE` below is matched by a redirect route, so a
 * bookmark, a link in an old e-mail and an external redirect all still land where they meant to.
 * §LV.12's instruction was to redirect rather than drop, because the reversal nobody claims is the
 * one that becomes a permanent second product.
 *
 * ## One thing the switch drops, deliberately
 *
 * The console path is **tenant-prefixed** (`/acme/console/...`) and bento is not. That prefix was
 * never how the backend resolved a tenant: `ShVigletTenantResolver` reads the session (set by
 * `POST /tenant/{name}/switch`), a CDA token's own tenant, or an OIDC `workspace_id` claim, and
 * never the URL. So the prefix is cosmetic — it kept generated links inside whatever prefix you
 * arrived with — and dropping it changes which tenant nothing resolves to. **SH931** carries
 * tenant-prefixed bento URLs for the part that is a real loss: sharing a link that names its
 * tenant.
 */
const CONSOLE_BASE = "/bento";

export const ROUTES = {
  ROOT: "/",
  LOGIN: "/login",
  LOGOUT: "/logout",
  CONSOLE: CONSOLE_BASE,
  /** The bento shell's index *is* the home page (SH898), so there is no `/home` under it. */
  HOME: CONSOLE_BASE,
  CONTENT_ROOT: `${CONSOLE_BASE}/content`,
  SITE_NEW: `${CONSOLE_BASE}/content/site/new`,
  SITE_IMPORT: `${CONSOLE_BASE}/content/site/import`,
  POST_TYPE_ROOT: `${CONSOLE_BASE}/content/post-type`,
  POST_TYPE_NEW: `${CONSOLE_BASE}/content/post-type/new`,
  SEARCH: `${CONSOLE_BASE}/content/search`,
  TRASH: `${CONSOLE_BASE}/content/trash`,
  /** SH1066 — a site's form submissions, and where its form sends them. */
  FORMS: `${CONSOLE_BASE}/content/forms`,
  /** SH991 — what goes live or comes down, in the order it will happen. */
  SCHEDULED: `${CONSOLE_BASE}/content/scheduled`,
  FILES: `${CONSOLE_BASE}/content/files`,
  ADMIN_ROOT: `${CONSOLE_BASE}/admin`,
  ADMIN_USERS: `${CONSOLE_BASE}/admin/users`,
  ADMIN_GROUPS: `${CONSOLE_BASE}/admin/groups`,
  ADMIN_ROLES: `${CONSOLE_BASE}/admin/roles`,
  GRAPHQL_ROOT: `${CONSOLE_BASE}/graphql`,
  SETUP: "/setup",
  ADMIN_TOKENS: `${CONSOLE_BASE}/admin/tokens`,
  ADMIN_TENANTS: `${CONSOLE_BASE}/admin/tenants`,
  ADMIN_AUTH_PROVIDERS: `${CONSOLE_BASE}/admin/auth-providers`,
  ADMIN_EXCHANGE_PROVIDERS: `${CONSOLE_BASE}/admin/exchange-providers`,
  ADMIN_EMAIL_CONFIG: `${CONSOLE_BASE}/admin/email-config`,
  ADMIN_ACTIVITY: `${CONSOLE_BASE}/admin/activity`,
  ADMIN_REVIEW: `${CONSOLE_BASE}/admin/review`,
  ADMIN_WEBHOOKS: `${CONSOLE_BASE}/admin/webhooks`,
  ADMIN_MARKETPLACE: `${CONSOLE_BASE}/admin/marketplace`,
  UNIVERSAL_EDITOR: `${CONSOLE_BASE}/universal-editor`,
  /** SH909 — the old prefix, kept for the redirect and for nothing else. */
  LEGACY_CONSOLE: scoped(consolePath),
  /** SH887 — unscoped on purpose: a harness with no tenant, mounted outside the console shell. */
  BENTO_VOCABULARY: "/bento-vocabulary",
  /**
   * SH888 — the parallel bento route tree's root. Unscoped like the console's own `CONSOLE`
   * would be if it were not tenant-prefixed; tenancy under bento arrives with the surfaces
   * that need it (SH905), not with the shell.
   */
  BENTO: "/bento",
  /**
   * SH899 — the bento content surfaces. Unscoped like BENTO itself; tenancy under bento arrives
   * with the surfaces that need it (SH905), not with the ones that do not.
   */
  BENTO_CONTENT: "/bento/content",
  /**
   * SH1193 — a rail section's hub: every surface of the section, grouped, one click from the rail.
   * `/bento/areas/content` and `/bento/areas/administration`; the section id is the segment.
   */
  BENTO_AREAS: "/bento/areas",
  BENTO_SITE_NEW: "/bento/content/site/new",
  BENTO_SITE_IMPORT: "/bento/content/site/import",
  /**
   * SH904 — the identity surfaces. Unscoped for the same reason as the content ones: tenancy
   * under bento arrives with SH905, which is the slice that actually needs it.
   */
  BENTO_ADMIN: "/bento/admin",
  BENTO_GROUPS: "/bento/admin/groups",
  BENTO_GROUP_NEW: "/bento/admin/groups/new",
  BENTO_ROLES: "/bento/admin/roles",
  BENTO_ROLE_NEW: "/bento/admin/roles/new",
  BENTO_USERS: "/bento/admin/users",
  BENTO_USER_NEW: "/bento/admin/users/new",
  /**
   * SH905 — the infrastructure surfaces. Still unscoped, and the tenant list is the reason to say
   * so out loud: it is the one page here that is *about* tenancy, and it is a platform-admin
   * surface that lists tenants rather than a surface that lives inside one. Prefixing it would be
   * the wrong shape even once bento gets tenant scoping.
   */
  BENTO_TOKENS: "/bento/admin/tokens",
  BENTO_TENANTS: "/bento/admin/tenants",
  BENTO_AUTH_PROVIDERS: "/bento/admin/auth-providers",
  BENTO_EXCHANGE_PROVIDERS: "/bento/admin/exchange-providers",
  BENTO_WEBHOOKS: "/bento/admin/webhooks",
  BENTO_EMAIL_CONFIG: "/bento/admin/email-config",
};

/**
 * SH909 — the two patterns the legacy redirect matches, bare and tenant-prefixed.
 *
 * These replace `ROUTE_PATTERNS` and `CONSOLE_GATEWAY_PATTERN`, which were **imported by
 * nothing**: a tenant gateway that was designed, declared and never mounted. Left in place they
 * would have read, to the next person, as the mechanism that makes the redirect below work.
 */
export const LEGACY_CONSOLE_PATTERNS = [`${consolePath}/*`, `/:tenant${consolePath}/*`];

/** The path the legacy console prefix is stripped down to — `/console/content` → `/bento/content`. */
export function bentoPathFor(pathname: string): string {
  const segments = pathname.split("/").filter(Boolean);
  const consoleSegment = consolePath.replace(/^\//, "");
  const at = segments.indexOf(consoleSegment);
  // Everything after `<tenant?>/console`. A tenant prefix is dropped rather than carried: bento is
  // unscoped, and the tenant a request runs in comes from the session and never from the URL.
  const rest = at === -1 ? segments : segments.slice(at + 1);
  // `/console` and `/console/home` are the same page under bento, and it is the shell's index.
  const tail = rest[0] === "home" ? rest.slice(1) : rest;
  return tail.length === 0 ? ROUTES.HOME : `${ROUTES.CONSOLE}/${tail.join("/")}`;
}
