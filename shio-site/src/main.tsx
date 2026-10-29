import React from "react";
import { createRoot, hydrateRoot } from "react-dom/client";
import App from "./App.tsx";
import { ThemeProvider } from "@/components/theme-provider";
import { BASE } from "@/lib/base";
import "./index.css";

// The route is derived from the URL: the same trivial rule the server used, once the
// path the site is published under ("/shio/") is taken off.
const { pathname } = globalThis.location;
const path = pathname.startsWith(BASE) ? `/${pathname.slice(BASE.length)}` : pathname;
const container = document.getElementById("root")!;
const tree = (
  <React.StrictMode>
    <ThemeProvider defaultTheme="system" storageKey="shio-site-theme">
      <App path={path} />
    </ThemeProvider>
  </React.StrictMode>
);

// In production the route's HTML was prerendered (scripts/prerender.mjs) →
// hydrate it. In dev there is no prerender (empty #root) → mount fresh, which
// avoids a spurious hydration-mismatch warning.
if (container.hasChildNodes()) {
  hydrateRoot(container, tree);
} else {
  createRoot(container).render(tree);
}
