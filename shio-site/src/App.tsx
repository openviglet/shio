import { useEffect } from "react";
import { LandingPage } from "@/pages/landing";

/**
 * Routing is one regular expression the prerender and the client both read —
 * no react-router, no SSG framework. Navigation between pages uses plain
 * `<a href>` full loads, because every route is prerendered to its own static
 * HTML anyway. SH539 adds the first pages that make this branch do work.
 */
export default function App({ path = "/" }: Readonly<{ path?: string }>) {
  // A cross-page link to a home anchor ("/#files") full-loads "/" and then
  // relies on the browser's native hash scroll — but the target section mounts
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

  void path;
  return <LandingPage />;
}
