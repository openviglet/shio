import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import path from "path";
import { defineConfig } from "vite";

// Deployed at the root of https://shio.viglet.org/. Internal links still go through withBase()
// (src/lib/base.ts), which reads this as BASE_URL, so a future move under a path is one line here.
export default defineConfig({
  base: "/",
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      "@": path.resolve(import.meta.dirname, "./src"),
    },
    dedupe: ["react", "react-dom"],
  },
});
