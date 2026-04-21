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
