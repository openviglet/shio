import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import path from "path";
import { defineConfig } from "vite";

// Deployed under a path, https://openviglet.github.io/shio/, so every asset and every internal
// link carries it: links through withBase() (src/lib/base.ts), which reads this as BASE_URL.
export default defineConfig({
  base: "/shio/",
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      "@": path.resolve(import.meta.dirname, "./src"),
    },
    dedupe: ["react", "react-dom"],
  },
});
