import { renderToString } from "react-dom/server";
import App, { ROUTES } from "./App.tsx";
import { ThemeProvider } from "@/components/theme-provider";
import { FEATURE_DETAILS, FEATURE_SLUGS } from "@/lib/feature-content";

export interface PrerenderRoute {
  /** URL path (also the output directory under dist/). */
  path: string;
  title: string;
  description: string;
}

/**
 * Every route the build prerenders to its own static HTML file.
 *
 * The paths must be the keys of `ROUTES` in App.tsx; `routesAreDeclared` below
 * asserts it at prerender time, because the failure it prevents is silent: a
 * route in this table with no page renders the landing page under another
 * title, and a page missing from it never gets a file at all.
 */
export const routes: PrerenderRoute[] = [
  {
    path: "/",
    title: "Viglet Shio: a complete CMS your agent can drive too",
    description:
      "An open-source, self-hosted, agent-ready CMS with four ways in: a full React console needing no agent at all, an MCP and CLI surface for coding agents, content projected to files for a repository workflow, and REST plus GraphQL delivery with TypeScript SDKs. Same content, same drafts, same publish button.",
  },
  {
    path: "/console",
    title: "Shio without an agent: the CMS in the browser | Viglet Shio",
    description:
      "The traditional path, in full: create a site, build the content model with fifteen field widgets, write pages in a form, upload media, preview and edit on the rendered page, publish or schedule, and manage users, roles and tokens. All in the console. Plus the four things that live only on the command line, and what changes if you attach an agent later.",
  },
  {
    path: "/claude-code",
    title: "A CMS for Claude Code: the plugin and the MCP server | Viglet Shio",
    description:
      "An agent-ready CMS that Claude Code or any MCP client drives. Hand a Viglet Shio instance to Claude Code in one command: the plugin (MCP server, skills, slash commands, AGENTS.md fragment) or a bare stdio bridge, the three calls a session opens with, and the AGENT token scope, which writes on the agent surface only and is deliberately not a superset of a write key.",
  },
  {
    path: "/compare",
    title: "Shio vs Contentful, Sanity, Strapi and WordPress | Viglet Shio",
    description:
      "Viglet Shio against Contentful, Sanity, Strapi and WordPress on the axis where they differ: agent-operability. Eighteen checkable questions grouped by the design law each comes from, plus what Contentful, Sanity, Strapi and WordPress do better and when to pick them instead.",
  },
  {
    path: "/replicate",
    title: "Replicate an existing site into an editable one | Viglet Shio",
    description:
      "Point shio clone at a site that already exists and get one a curator can edit: capture under robots.txt writing no content, a proposal that groups blocks by signature rather than by page, a conversion into drafts, and arithmetic at the end that says whether anything was lost. Fidelity keeps the source appearance; authorable gives editable fields.",
  },
  {
    path: "/curator",
    title: "The curator’s half: what stops the agent | Viglet Shio",
    description:
      "An agent wrote nine pages last night and nobody has seen them yet. The curator half of Viglet Shio: the review queue an agent token cannot reach, expiring preview links, inline editing on the rendered page, draft-published diff and restore, reversible deletes, and the six gates that are on by default rather than configurable.",
  },
  // The depth pages carry their own metadata, so the record is the single
  // source for the route, the title and the description alike.
  ...FEATURE_SLUGS.map((slug) => ({
    path: `/features/${slug}`,
    title: FEATURE_DETAILS[slug].metaTitle,
    description: FEATURE_DETAILS[slug].metaDescription,
  })),
];

/**
 * The two tables agree, in both directions. Thrown at import time, so the
 * prerender fails before it writes anything rather than after.
 */
export function routesAreDeclared(): void {
  const declared = new Set(Object.keys(ROUTES));
  const prerendered = new Set(routes.map((r) => r.path));
  const missing = [...declared].filter((p) => !prerendered.has(p));
  const orphan = [...prerendered].filter((p) => !declared.has(p));
  if (missing.length || orphan.length) {
    const parts = [];
    if (missing.length) parts.push(`no prerender entry for ${missing.join(", ")}`);
    if (orphan.length) parts.push(`no page for ${orphan.join(", ")}`);
    throw new Error(`entry-server: routes disagree with App.tsx: ${parts.join("; ")}`);
  }
}

routesAreDeclared();

/** Render a route's app shell to an HTML string for the prerender step. */
export function render(path: string): string {
  return renderToString(
    <ThemeProvider defaultTheme="system" storageKey="shio-site-theme">
      <App path={path} />
    </ThemeProvider>
  );
}
