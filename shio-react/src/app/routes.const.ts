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

export const ROUTES = {
  ROOT: "/",
  LOGIN: "/login",
  LOGOUT: "/logout",
  CONSOLE: scoped(consolePath),
  HOME: scoped(`${consolePath}/home`),
  CONTENT_ROOT: scoped(`${consolePath}/content`),
  SITE_NEW: scoped(`${consolePath}/content/site/new`),
  SITE_IMPORT: scoped(`${consolePath}/content/site/import`),
  POST_TYPE_ROOT: scoped(`${consolePath}/content/post-type`),
  POST_TYPE_NEW: scoped(`${consolePath}/content/post-type/new`),
  SEARCH: scoped(`${consolePath}/content/search`),
  TRASH: scoped(`${consolePath}/content/trash`),
  FILES: scoped(`${consolePath}/content/files`),
  ADMIN_ROOT: scoped(`${consolePath}/admin`),
  ADMIN_USERS: scoped(`${consolePath}/admin/users`),
  ADMIN_GROUPS: scoped(`${consolePath}/admin/groups`),
  ADMIN_ROLES: scoped(`${consolePath}/admin/roles`),
  GRAPHQL_ROOT: scoped(`${consolePath}/graphql`),
  SETUP: "/setup",
  ADMIN_TOKENS: scoped(`${consolePath}/admin/tokens`),
  ADMIN_TENANTS: scoped(`${consolePath}/admin/tenants`),
  ADMIN_AUTH_PROVIDERS: scoped(`${consolePath}/admin/auth-providers`),
  ADMIN_EXCHANGE_PROVIDERS: scoped(`${consolePath}/admin/exchange-providers`),
  ADMIN_EMAIL_CONFIG: scoped(`${consolePath}/admin/email-config`),
  ADMIN_ACTIVITY: scoped(`${consolePath}/admin/activity`),
  ADMIN_REVIEW: scoped(`${consolePath}/admin/review`),
  ADMIN_WEBHOOKS: scoped(`${consolePath}/admin/webhooks`),
  ADMIN_MARKETPLACE: scoped(`${consolePath}/admin/marketplace`),
  UNIVERSAL_EDITOR: scoped(`${consolePath}/universal-editor`),
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

// Route patterns (with `:tenant` placeholder) used by React Router for matching.
export const ROUTE_PATTERNS = {
  CONSOLE: `/:tenant${consolePath}`,
  ADMIN_ROOT: `/:tenant${consolePath}/admin`,
  GRAPHQL_ROOT: `/:tenant${consolePath}/graphql`,
  UNIVERSAL_EDITOR: `/:tenant${consolePath}/universal-editor`,
};

// Bare console path (no tenant) — matched to redirect through TenantGateway,
// so old bookmarks / external redirects to /console still resolve a tenant.
export const CONSOLE_GATEWAY_PATTERN = `${consolePath}/*`;
