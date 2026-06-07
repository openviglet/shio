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
  PREVIEW: scoped(`${consolePath}/preview`),
  ADMIN_ROOT: scoped(`${consolePath}/admin`),
  ADMIN_USERS: scoped(`${consolePath}/admin/users`),
  ADMIN_GROUPS: scoped(`${consolePath}/admin/groups`),
  ADMIN_ROLES: scoped(`${consolePath}/admin/roles`),
  GRAPHQL_ROOT: scoped(`${consolePath}/graphql`),
  SETUP: "/setup",
  ADMIN_TOKENS: scoped(`${consolePath}/admin/tokens`),
  ADMIN_TENANTS: scoped(`${consolePath}/admin/tenants`),
  UNIVERSAL_EDITOR: scoped(`${consolePath}/universal-editor`),
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
