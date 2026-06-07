import { defineConfig } from "vite";
import { resolve } from "path";

// Zero runtime dependencies → nothing is marked external; the bundle is
// self-contained for every target (Node, the browser, edge, bundlers). Mirrors
// @viglet/shio-client's build.
export default defineConfig({
  build: {
    lib: {
      entry: resolve(__dirname, "src/index.ts"),
      name: "ShioModel",
      formats: ["es", "umd"],
      fileName: (format) =>
        format === "umd" ? "shio-model.umd.cjs" : "shio-model.js",
    },
    outDir: "dist",
    emptyOutDir: true,
  },
});
