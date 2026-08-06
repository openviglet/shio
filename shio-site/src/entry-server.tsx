import { renderToString } from "react-dom/server";
import App, { ROUTES } from "./App.tsx";
import { ThemeProvider } from "@/components/theme-provider";

export interface PrerenderRoute {
  /** URL path (also the output directory under dist/). */
  path: string;
  title: string;
  description: string;
}

/**
 * Every route the build prerenders to its own static HTML file.
 *
 * The paths must be the keys of `ROUTES` in App.tsx — `routesAreDeclared` below
 * asserts it at prerender time, because the failure it prevents is silent: a
 * route in this table with no page renders the landing page under another
 * title, and a page missing from it never gets a file at all.
 */
export const routes: PrerenderRoute[] = [
  {
    path: "/",
    title: "Viglet Shio — the CMS whose operator is Claude Code",
    description:
      "An open-source, self-hosted headless CMS designed for a coding agent: MCP-native, one call replaces a discovery session, content lives as files, every 4xx carries the fix, and every write lands as a draft a human approves.",
  },
  {
    path: "/claude-code",
    title: "Drive Shio from Claude Code — the plugin, the MCP bridge | Viglet Shio",
    description:
      "Hand a Shio CMS instance to Claude Code in one command: the plugin (MCP server, skills, slash commands, AGENTS.md fragment) or a bare stdio bridge, the three calls a session opens with, and the AGENT token scope — which writes on the agent surface only and is deliberately not a superset of a write key.",
  },
  {
    path: "/compare",
    title: "Shio vs Contentful, Sanity, Strapi and WordPress | Viglet Shio",
    description:
      "An honest comparison on the axis where headless CMSs actually differ: agent-operability. Seventeen checkable questions grouped by the design law each comes from, plus what Contentful, Sanity, Strapi and WordPress are genuinely better at and when to pick them instead.",
  },
  {
    path: "/replicate",
    title: "Replicate an existing site into an editable one | Viglet Shio",
    description:
      "Point shio clone at a site that already exists and get one a curator can edit: capture under robots.txt writing no content, a proposal that groups blocks by signature rather than by page, a conversion into drafts, and arithmetic at the end that says whether anything was lost. Fidelity keeps the source appearance; authorable gives editable fields.",
  },
  {
    path: "/curator",
    title: "The human half — what stops the agent | Viglet Shio",
    description:
      "An agent wrote nine pages last night and nobody has seen them yet. The curator half of Shio: the review queue an agent token cannot reach, expiring preview links, inline editing on the rendered page, draft-published diff and restore, reversible deletes, and the six gates that are on by default rather than configurable.",
  },
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
    throw new Error(`entry-server: routes disagree with App.tsx — ${parts.join("; ")}`);
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
