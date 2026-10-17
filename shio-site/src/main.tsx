import React from "react";
import { createRoot, hydrateRoot } from "react-dom/client";
import App from "./App.tsx";
import { ThemeProvider } from "@/components/theme-provider";
import "./index.css";

// The route is derived from the URL: the same trivial rule the server used.
const container = document.getElementById("root")!;
const tree = (
  <React.StrictMode>
    <ThemeProvider defaultTheme="system" storageKey="shio-site-theme">
      <App path={globalThis.location.pathname} />
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
