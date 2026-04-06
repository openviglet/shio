import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import path from "node:path";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: {
    emptyOutDir: true,
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes("node_modules")) {
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
