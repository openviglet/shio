import { defineConfig } from "vite";
import { resolve } from "path";

export default defineConfig({
  build: {
    lib: {
      entry: resolve(__dirname, "src/shio-editor-cors.ts"),
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
    minify: "esbuild",
  },
});
