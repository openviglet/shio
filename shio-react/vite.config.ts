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
    // SH1044 — there is deliberately no `manualChunks` here, and deleting it is the fix.
    //
    // There used to be one, and its own comment said what it was for: keep GraphiQL, Monaco and
    // CodeMirror out of the eager bundle, "since [the vendor-lib rule] matches every node_modules
    // id". It did the opposite. A manual chunk is a chunk the bundler must place, and under
    // rolldown a named vendor chunk becomes a static dependency of the entry — so `index.html`
    // preloaded `vendor-graphiql` on every console route, for a page whose own chunk is 3 KB.
    //
    // MEASURED, because the first attempt at this task was the reordering §LXXII.2 proposed —
    // give CodeMirror its own chunk ahead of the GraphiQL rule — and it moved the total by 4 KB:
    //
    //   manual chunks, codemirror folded into vendor-graphiql   7298.3 KB raw   2148.6 KB gzip
    //   manual chunks, codemirror given its own vendor chunk    7293.9 KB raw   2146.2 KB gzip
    //   no manual chunks at all                                 1506.4 KB raw    403.5 KB gzip
    //
    // The bundler's own splitting puts each of them behind the dynamic import that reaches it:
    // Monaco (2.8 MB) and the GraphiQL page (678 KB) behind the lazy route, CodeMirror (573 KB)
    // behind the lazy Ace widget. Hand-grouping was also what made the SH1017 CodeMirror version
    // conflict read as a GraphiQL fault, because the dead editor was in a chunk named for it
    // (codemirror-singleton.test.mjs).
    //
    // `console-size-budget.json` is what keeps this honest now: a rule stating an intention was
    // believed for as long as nothing measured it.
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
