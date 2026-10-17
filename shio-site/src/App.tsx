import { useEffect } from "react";
import { LandingPage } from "@/pages/landing";
import { ClaudeCodePage } from "@/pages/claude-code";
import { ConsolePage } from "@/pages/console";
import { ComparePage } from "@/pages/compare";
import { ReplicatePage } from "@/pages/replicate";
import { CuratorPage } from "@/pages/curator";
import { FeaturePage } from "@/pages/feature-page";
import { FEATURE_DETAILS, FEATURE_SLUGS } from "@/lib/feature-content";

/**
 * Routing is one table the prerender and the client both read: no react-router
 * and no SSG framework, so the bleeding-edge Vite this workspace tracks has
 * nothing framework-shaped to break. Navigation is plain `<a href>` full loads,
 * because every route is prerendered to its own static HTML anyway.
 *
 * A route's path is declared once, here, and `entry-server.tsx` attaches the
 * title and description to the same string. Adding a page is a row plus an
 * entry in that table, and `scripts/prerender.mjs` writes the file.
 */
export const ROUTES: Record<string, () => React.JSX.Element> = {
  "/": LandingPage,
  "/console": ConsolePage,
  "/claude-code": ClaudeCodePage,
  "/compare": ComparePage,
  "/replicate": ReplicatePage,
  "/curator": CuratorPage,
  // One depth page per pillar, from one record each, so a new pillar is an
  // entry in feature-content.ts and a row in entry-server.tsx, and the build
  // refuses if you write only one of the two.
  ...Object.fromEntries(
    FEATURE_SLUGS.map((slug) => [
      `/features/${slug}`,
      () => <FeaturePage detail={FEATURE_DETAILS[slug]} />,
    ])
  ),
};

/**
 * Resolve a pathname to a page, tolerating the trailing slash a server adds.
 *
 * Trimmed by index rather than by `/\/+$/`: an anchored repeated group over a
 * string the URL bar supplies backtracks super-linearly, and `/////…` is free
 * for anyone to type. Same rule the CLI's tag scanner is written under.
 */
export function matchRoute(path: string): (() => React.JSX.Element) | null {
  let end = path.length;
  while (end > 1 && path[end - 1] === "/") end -= 1;
  return ROUTES[path.slice(0, end)] ?? null;
}

export default function App({ path = "/" }: Readonly<{ path?: string }>) {
  // A cross-page link to a home anchor ("/#files") full-loads "/" and then
  // relies on the browser's native hash scroll, but the target section mounts
  // AFTER React does (and does not exist at all before hydration in dev), so the
  // native jump finds nothing and stays at the top. Re-run the scroll once the
  // element is present, plus on hashchange for same-page anchor clicks.
  useEffect(() => {
    const scrollToHash = (smooth: boolean) => {
      const { hash } = window.location;
      if (hash.length < 2) return;
      const id = decodeURIComponent(hash.slice(1));
      let tries = 0;
      const tick = () => {
        const el = document.getElementById(id);
        if (el) {
          el.scrollIntoView({ behavior: smooth ? "smooth" : "auto", block: "start" });
        } else if (tries++ < 30) {
          requestAnimationFrame(tick);
        }
      };
      tick();
    };
    scrollToHash(false);
    const onHashChange = () => scrollToHash(true);
    window.addEventListener("hashchange", onHashChange);
    return () => window.removeEventListener("hashchange", onHashChange);
  }, []);

  const Page = matchRoute(path) ?? LandingPage;
  return <Page />;
}
