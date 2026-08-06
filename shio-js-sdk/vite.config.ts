import { defineConfig } from "vite";
import { resolve } from "path";

export default defineConfig({
  build: {
    lib: {
      entry: resolve(import.meta.dirname, "src/shio-editor-cors.ts"),
      name: "ShioEditorCors",
      formats: ["es", "umd", "iife"],
      fileName: (format) => {
        if (format === "iife") return "shio-editor-cors.iife.js";
        if (format === "umd") return "shio-editor-cors.umd.cjs";
        return "shio-editor-cors.js";
      },
    },
    outDir: "dist",
    emptyOutDir: true,
    // vite 8 no longer bundles esbuild; use the default (oxc) minifier, like the
    // sibling lib packages (shio-react-sdk / shio-client).
    minify: true,
  },
});
