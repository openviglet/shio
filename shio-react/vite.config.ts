import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { vigletBootLoader } from "@viglet/viglet-design-system/vite";
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import path from "node:path";
import { defineConfig, type Plugin } from "vite";
import { BUILD_INFO, sourceDigest } from "./scripts/console-build-info.mjs";
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

/**
 * SH1112 — the console's budget travels with the console.
 *
 * `console-size-budget.json` holds the eager ceilings the build is gated on, and it used to live
 * only in this checkout, so an instance serving the build could report what it costs but not what it
 * was allowed to cost. Emitted beside `index.html` in the static root, where `ShConsoleCost` reads
 * both, so a running instance answers the number and the line it is measured against together.
 */
function shioConsoleBudget(): Plugin {
  return {
    name: "shio-console-budget",
    apply: "build",
    generateBundle() {
      const budget = JSON.parse(readFileSync(path.resolve(import.meta.dirname, "console-size-budget.json"), "utf8"));
      // The ceilings only: the file's notes are the repository's history, not the build's.
      this.emitFile({
        type: "asset",
        fileName: "console-size-budget.json",
        source: `${JSON.stringify({ eager: budget.eager, chunks: budget.chunks }, null, 2)}
`,
      });
    },
  };
}

/**
 * SH1126 — the build says which tree it came from, so a size verdict is about the change under test.
 *
 * SH1043's comparison runs against whatever build sits in the static root and `pnpm test` never
 * builds one, so the gate measured a build of *some* tree with nothing saying which. It went wrong in
 * both directions on consecutive tasks: one passed against a build that predated its own change, the
 * next failed the headroom check only because a build made outside the session happened to include
 * it. `build-info.json` beside `index.html` is the same identity SH460 gave the instance, and
 * `console-size-budget.test.mjs` compares its digest with the tree it runs in.
 *
 * The digest comes from `scripts/console-build-info.mjs` — one function, used here and by the test,
 * because a build that hashed differently from the reader would report a mismatch on every run.
 */
function shioBuildInfo(): Plugin {
  return {
    name: "shio-build-info",
    apply: "build",
    generateBundle() {
      const root = import.meta.dirname;
      this.emitFile({
        type: "asset",
        fileName: BUILD_INFO,
        source: `${JSON.stringify({
          // The comparison. Over file contents, so it matches a dirty tree exactly when the build was
          // made from it — a commit cannot do that, and refusing a dirty tree would refuse every
          // build anybody makes while working.
          sourceDigest: sourceDigest(root),
          // For a person: what a reader wants when the digests differ and they are working out which
          // build they have. Absent outside a checkout, which is not a failure — a build from a
          // tarball has no commit and is still measurable.
          commit: headCommit(root),
          builtAt: new Date().toISOString(),
        }, null, 2)}\n`,
      });
    },
  };
}

/** HEAD, or `null` where there is no git — a release tarball builds fine and has no commit. */
function headCommit(root: string): string | null {
  try {
    return execFileSync("git", ["rev-parse", "HEAD"], { cwd: root, encoding: "utf8" }).trim();
  } catch {
    return null;
  }
}

export default defineConfig({
  plugins: [
    shioThemeSeed(),
    shioConsoleBudget(),
    shioBuildInfo(),
    vigletBootLoader({
      title: "Viglet Shio",
      subtitle: "Agent-ready CMS",
      color: "#7C3AED",
      colorDark: "#6D28D9",
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
      // Pages the console opens in a new tab: without these the dev server answers
      // with the SPA shell and the React router renders its 404.
      "/preview": "http://localhost:2710",
      "/sites": "http://localhost:2710",
      "/store": "http://localhost:2710",
      // SH1201 — the media library's thumbnails and every byte URL the console builds. Without it
      // the dev server answers the SPA shell with a 200, so no request fails and every image breaks.
      "/file_source": "http://localhost:2710",
    },
  },
});
