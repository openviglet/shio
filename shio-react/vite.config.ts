import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { vigletBootLoader } from "@viglet/viglet-design-system/vite";
import path from "node:path";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [
    vigletBootLoader({
      title: "Viglet Shio CMS",
      subtitle: "Content Management System",
      color: "#FF6347",
      colorDark: "#e0341b",
      prefix: "shio",
      storageKey: "shio-ui-theme",
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
      "@": path.resolve(__dirname, "./src"),
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
    include: ["graphiql", "@graphiql/react", "@graphiql/toolkit"],
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
