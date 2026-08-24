import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { vigletBootLoader } from "@viglet/viglet-design-system/vite";
import path from "node:path";
import { defineConfig, type Plugin } from "vite";
import { SHIO_THEME_STORAGE_KEY, shioThemeSeedScript } from "./src/theme";

/**
 * SH918 — write the console's default theme into storage before anything reads it.
 *
 * `head-prepend` and not `head`: the boot loader injects its own `<script>` into `<head>` and
 * decides the splash's colour from `localStorage[SHIO_THEME_STORAGE_KEY]`, falling back to
 * `prefers-color-scheme` when nothing is stored. This has to have run by then or the fallback
 * is what decides, and on a light-preference machine the splash and the app disagree — see
 * `src/theme.ts` for the whole shape of it.
 *
 * The plugin has no `defaultTheme` option, so storage is where the two are taught to agree.
 * `shio-theme-seed.test.mjs` executes both scripts, in this order, against a real DOM.
 */
function shioThemeSeed(): Plugin {
  return {
    name: "shio-theme-seed",
    transformIndexHtml() {
      return [{ tag: "script", children: shioThemeSeedScript(), injectTo: "head-prepend" }];
    },
  };
}

export default defineConfig({
  plugins: [
    shioThemeSeed(),
    vigletBootLoader({
      title: "Viglet Shio CMS",
      subtitle: "Content Management System",
      color: "#FF6347",
      colorDark: "#e0341b",
      prefix: "shio",
      storageKey: SHIO_THEME_STORAGE_KEY,
      testFlag: "__SHIO_LOADING_TEST__",
    }),
    react(),
    tailwindcss(),
  ],
  build: {
    emptyOutDir: true,
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes("node_modules")) {
            // GraphiQL + Monaco + graphql are only reached via the lazily-loaded
            // GraphQL Explorer page. Keep them in their own chunk so the generic
            // vendor-lib rule below doesn't pull this multi-MB editor into the
            // eager bundle (it would, since it matches every node_modules id).
            if (
              id.includes("graphiql") ||
              id.includes("@graphiql") ||
              id.includes("monaco") ||
              id.includes("node_modules/graphql/") ||
              id.includes("codemirror")
            ) {
              return "vendor-graphiql";
            }
            if (id.includes("@radix-ui")) return "vendor-ui";
            return "vendor-lib";
          }
        },
      },
    },
    outDir: "../shio-app/src/main/resources/public/",
  },
  resolve: {
    alias: {
      "@": path.resolve(import.meta.dirname, "./src"),
    },
  },
  optimizeDeps: {
    // GraphiQL is reached only via a dynamic import (lazy route), so Vite's dep
    // scanner doesn't pre-bundle it — its CJS deps (react-compiler-runtime's `c`,
    // nullthrows' default, …) then get served raw and ESM interop fails at runtime.
    // Force pre-bundling of the GraphiQL tree (incl. its Monaco/CJS deps) so the
    // optimizer fixes that interop. The Monaco `?worker` imports are kept in app
    // source (src/lib/monaco-workers.ts), not graphiql's dep entry, so the
    // pre-bundler never tries to load a `?worker` query (which it can't).
    include: ["graphiql", "@graphiql/toolkit"],
  },
  server: {
    proxy: {
      "/api": "http://localhost:2710",
      "/logout": "http://localhost:2710",
      "/csrf": "http://localhost:2710",
      "/graphiql": "http://localhost:2710",
      "/graphql": "http://localhost:2710",
      "/oauth2": "http://localhost:2710",
      "/login/oauth2": "http://localhost:2710",
    },
  },
});
