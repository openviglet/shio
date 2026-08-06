import { renderToString } from "react-dom/server";
import App from "./App.tsx";
import { ThemeProvider } from "@/components/theme-provider";

export interface PrerenderRoute {
  /** URL path (also the output directory under dist/). */
  path: string;
  title: string;
  description: string;
}

/** Every route the build prerenders to its own static HTML file. */
export const routes: PrerenderRoute[] = [
  {
    path: "/",
    title: "Viglet Shio — the CMS whose operator is Claude Code",
    description:
      "An open-source, self-hosted headless CMS designed for a coding agent: MCP-native, one call replaces a discovery session, content lives as files, every 4xx carries the fix, and every write lands as a draft a human approves.",
  },
];

/** Render a route's app shell to an HTML string for the prerender step. */
export function render(path: string): string {
  return renderToString(
    <ThemeProvider defaultTheme="system" storageKey="shio-site-theme">
      <App path={path} />
    </ThemeProvider>
  );
}
